package com.cheku.app.fit

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class BleSnapshot(val heartRate: Int?, val cadence: Int?, val power: Int?, val connected: Int)

class BleSensorManager(private val context: Context) {
    companion object {
        val HEART_RATE_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val CSC_SERVICE: UUID = UUID.fromString("00001816-0000-1000-8000-00805f9b34fb")
        val POWER_SERVICE: UUID = UUID.fromString("00001818-0000-1000-8000-00805f9b34fb")
        private val HR_MEASUREMENT = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        private val CSC_MEASUREMENT = UUID.fromString("00002a5b-0000-1000-8000-00805f9b34fb")
        private val POWER_MEASUREMENT = UUID.fromString("00002a63-0000-1000-8000-00805f9b34fb")
        private val CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private const val MAX_DISCOVERY_ATTEMPTS = 4
        private const val MAX_WRITE_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 500L
    }
    private val bluetooth = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val gatts = ConcurrentHashMap<String, BluetoothGatt>()
    // 显式声明为 MutableSet 而不是用 `ConcurrentHashMap.newKeySet()`：
    // 后者返回的 KeySetView 类型是 API 24 才有的，minSdk 23 下 lint 会报 NewApi。
    // newKeySet 内部就是 ConcurrentHashMap 的 KeySet 视图，这里显式包一层保持同样的并发语义。
    private val connectedAddresses: MutableSet<String> = ConcurrentHashMap<String, Boolean>().keys
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var heartRate: Pair<Int, Long>? = null
    @Volatile private var cadence: Pair<Int, Long>? = null
    @Volatile private var power: Pair<Int, Long>? = null
    private var lastCrankRevs: Int? = null
    private var lastCrankTime: Int? = null

    /** Descriptor writes must be issued one at a time */
    private data class DescriptorWrite(
        val gatt: BluetoothGatt,
        val descriptor: BluetoothGattDescriptor,
        val value: ByteArray,
        var attempts: Int = 0
    )

    private val pendingWrites = ArrayDeque<DescriptorWrite>()
    private val discoveryAttempts = mutableMapOf<String, Int>()

    fun snapshot(now: Long = System.currentTimeMillis()) = BleSnapshot(
        heartRate?.takeIf { now - it.second <= 5_000 }?.first,
        cadence?.takeIf { now - it.second <= 3_000 }?.first,
        power?.takeIf { now - it.second <= 3_000 }?.first,
        connectedAddresses.size
    )

    @SuppressLint("MissingPermission")
    fun scan(onResult: (BluetoothDevice) -> Unit): (() -> Unit)? {
        if (!hasPermission()) return null
        val scanner = bluetooth?.bluetoothLeScanner ?: return null
        val filters = listOf(HEART_RATE_SERVICE, CSC_SERVICE, POWER_SERVICE).map {
            ScanFilter.Builder().setServiceUuid(ParcelUuid(it)).build()
        }
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) = onResult(result.device)
        }
        scanner.startScan(filters, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
        return { if (hasPermission()) scanner.stopScan(callback) }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        if (!hasPermission() || gatts.containsKey(device.address)) return
        gatts[device.address] = device.connectGatt(context, true, callback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun connectConfigured() {
        if (!hasPermission()) return
        val addresses = context.getSharedPreferences("fit_ble", Context.MODE_PRIVATE).getStringSet("addresses", emptySet()).orEmpty()
        addresses.forEach { runCatching { bluetooth?.getRemoteDevice(it) }.getOrNull()?.let(::connect) }
    }

    @SuppressLint("MissingPermission")
    fun close() {
        // Drop anything still queued so a closing manager cannot keep writing descriptors.
        pendingWrites.clear()
        discoveryAttempts.clear()
        connectedAddresses.clear()
        gatts.values.forEach { runCatching { it.close() } }
        gatts.clear()
    }

    private val callback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectedAddresses.add(gatt.device.address)
                discoveryAttempts[gatt.device.address] = 0
                requestServices(gatt)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED && hasPermission()) {
                connectedAddresses.remove(gatt.device.address)
                runCatching { gatt.connect() }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                requestServices(gatt)
                return
            }
            discoveryAttempts.remove(gatt.device.address)
            // Drop only this device's stale entries: clearing the whole queue used to wipe
            // the subscriptions another sensor had already queued.
            pendingWrites.retainAll { it.gatt !== gatt }

            // Subscribe one descriptor at a time. GATT permits a single outstanding
            // operation, so the old code issued three writes back to back and silently
            // lost every subscription after the first.
            listOf(HR_MEASUREMENT, CSC_MEASUREMENT, POWER_MEASUREMENT).forEach { uuid ->
                val characteristic = gatt.services.asSequence()
                    .mapNotNull { it.getCharacteristic(uuid) }
                    .firstOrNull() ?: return@forEach
                runCatching { gatt.setCharacteristicNotification(characteristic, true) }
                val descriptor = characteristic.getDescriptor(CCCD) ?: return@forEach
                // Send the CCCD value the characteristic actually advertises. Straps that
                // expose heart rate as INDICATE reject the NOTIFY value, which failed the
                // whole subscription and left the heart-rate chart with nothing to draw.
                val properties = characteristic.properties
                val value = when {
                    properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 ->
                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0 ->
                        BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                    else -> return@forEach
                }
                pendingWrites.addLast(DescriptorWrite(gatt, descriptor, value))
            }
            writeNextDescriptor()
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            writeNextDescriptor()
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) = parse(characteristic.uuid, characteristic.value)
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) = parse(characteristic.uuid, value)
    }

    /** Service discovery, with retries */
    @SuppressLint("MissingPermission")
    private fun requestServices(gatt: BluetoothGatt) {
        if (!hasPermission()) return
        val address = gatt.device.address
        val attempt = discoveryAttempts[address] ?: 0
        if (attempt > MAX_DISCOVERY_ATTEMPTS) return
        discoveryAttempts[address] = attempt + 1
        val requested = runCatching { gatt.discoverServices() }.getOrDefault(false)
        if (!requested) handler.postDelayed({ requestServices(gatt) }, RETRY_DELAY_MS)
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun writeNextDescriptor() {
        val next = pendingWrites.removeFirstOrNull() ?: return
        if (!hasPermission()) return

        val queued: Boolean = if (Build.VERSION.SDK_INT >= 33) {
            runCatching {
                next.gatt.writeDescriptor(next.descriptor, next.value) == BluetoothStatusCodes.SUCCESS
            }.getOrDefault(false)
        } else {
            runCatching {
                next.descriptor.value = next.value
                next.gatt.writeDescriptor(next.descriptor)
            }.getOrDefault(false)
        }

        if (queued) return
        // A rejected write means the stack was busy. Retry briefly, but give up after a few
        // tries so a permanently busy stack cannot spin this loop forever.
        next.attempts += 1
        if (next.attempts <= MAX_WRITE_ATTEMPTS) {
            pendingWrites.addFirst(next)
            handler.postDelayed({ writeNextDescriptor() }, RETRY_DELAY_MS)
        }
    }

    private fun parse(uuid: UUID, data: ByteArray) {
        val now = System.currentTimeMillis()
        when (uuid) {
            HR_MEASUREMENT -> if (data.size >= 2) {
                val wide = data[0].toInt() and 1 != 0
                heartRate = (if (wide && data.size >= 3) u16(data, 1) else data[1].toInt() and 0xff) to now
            }
            POWER_MEASUREMENT -> if (data.size >= 4) power = s16(data, 2).coerceAtLeast(0) to now
            CSC_MEASUREMENT -> {
                val flags = data.firstOrNull()?.toInt()?.and(0xff) ?: return
                var offset = 1
                if (flags and 1 != 0) offset += 6
                if (flags and 2 != 0 && data.size >= offset + 4) {
                    val revs = u16(data, offset); val time = u16(data, offset + 2)
                    val previousRevs = lastCrankRevs; val previousTime = lastCrankTime
                    if (previousRevs != null && previousTime != null) {
                        val dr = (revs - previousRevs) and 0xffff; val dt = (time - previousTime) and 0xffff
                        if (dt > 0) cadence = (dr * 60.0 * 1024.0 / dt).toInt().coerceIn(0, 255) to now
                    }
                    lastCrankRevs = revs; lastCrankTime = time
                }
            }
        }
    }
    private fun u16(b: ByteArray, o: Int) = (b[o].toInt() and 0xff) or ((b[o + 1].toInt() and 0xff) shl 8)
    private fun s16(b: ByteArray, o: Int) = u16(b, o).toShort().toInt()
    private fun hasPermission(): Boolean = Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
}

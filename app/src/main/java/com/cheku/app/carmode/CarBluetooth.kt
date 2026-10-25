package com.cheku.app.carmode

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** 一个已配对的蓝牙设备 */
internal class CarBluetoothChoice(
    val address: String,
    val name: String
)

/** 是否持有读蓝牙所需的权限 */
internal fun canReadBondedBluetooth(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

internal fun bondedCarBluetoothDevices(context: Context): List<CarBluetoothChoice> {
    if (!canReadBondedBluetooth(context)) return emptyList()
    return runCatching {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return@runCatching emptyList()
        if (!adapter.isEnabled) return@runCatching emptyList()
        adapter.bondedDevices.orEmpty()
            .map { device ->
                val address = device.address ?: ""
                CarBluetoothChoice(address, device.name?.takeIf { it.isNotBlank() } ?: address)
            }
            .filter { it.address.isNotBlank() }
            .sortedBy { it.name }
    }.getOrDefault(emptyList())
}

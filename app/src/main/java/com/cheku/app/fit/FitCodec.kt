package com.cheku.app.fit

import com.cheku.app.data.*
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant

data class FitValidation(val valid: Boolean, val message: String, val recordCount: Int)

object FitCodec {
    private const val FIT_EPOCH_OFFSET = 631065600L
    private data class Field(val number: Int, val size: Int, val baseType: Int)

    fun encode(activity: FitActivityEntity, source: List<FitSampleEntity>, sourceEvents: List<FitTimerEventEntity>, trimStart: Long? = null, trimEnd: Long? = null): ByteArray {
        val samples = source.filter { (trimStart == null || it.timestamp >= trimStart) && (trimEnd == null || it.timestamp <= trimEnd) }.sortedBy { it.timestamp }
        require(samples.size >= 2) { "至少需要两条有效采样" }
        val data = ByteArrayOutputStream()
        val fileFields = listOf(Field(0,1,0x00), Field(1,2,0x84), Field(2,2,0x84), Field(3,4,0x8C), Field(4,4,0x86))
        definition(data, 0, 0, fileFields)
        data.write(0); data.write(4); u16(data, 255); u16(data, 1); u32(data, stableSerial(activity.id)); u32(data, fitTime(samples.first().timestamp))
        val eventFields = listOf(Field(253,4,0x86), Field(0,1,0x00), Field(1,1,0x00))
        definition(data, 1, 21, eventFields)
        event(data, samples.first().timestamp, 0)
        val recordFields = listOf(
            Field(253,4,0x86), Field(0,4,0x85), Field(1,4,0x85), Field(2,2,0x84),
            Field(3,1,0x02), Field(4,1,0x02), Field(5,4,0x86), Field(6,2,0x84),
            Field(7,2,0x84), Field(73,4,0x86), Field(78,4,0x86)
        )
        definition(data, 2, 20, recordFields)
        val baseDistance = samples.first().distanceMeters
        val events = sourceEvents.filter { it.timestamp > samples.first().timestamp && it.timestamp < samples.last().timestamp }.sortedBy { it.timestamp }
        var eventIndex = 0
        samples.forEach { sample ->
            while (eventIndex < events.size && events[eventIndex].timestamp <= sample.timestamp) {
                val e = events[eventIndex++]
                event(data, e.timestamp, if (e.type == FitTimerEventType.Start || e.type == FitTimerEventType.Resume) 0 else 1)
            }
            data.write(2)
            u32(data, fitTime(sample.timestamp))
            s32(data, degreesToSemicircles(sample.latitude)); s32(data, degreesToSemicircles(sample.longitude))
            nullableU16(data, sample.altitudeMeters?.let { ((it + 500.0) * 5.0).toInt() })
            nullableU8(data, sample.heartRateBpm); nullableU8(data, sample.cadenceRpm)
            u32(data, ((sample.distanceMeters - baseDistance).coerceAtLeast(0.0) * 100.0).toLong())
            u16(data, (sample.speedMps.coerceAtLeast(0.0) * 1000.0).toInt().coerceIn(0, 65534))
            nullableU16(data, sample.powerWatts)
            u32(data, (sample.speedMps.coerceAtLeast(0.0) * 1000.0).toLong())
            nullableU32(data, sample.altitudeMeters?.let { ((it + 500.0) * 5.0).toLong() })
        }
        event(data, samples.last().timestamp, 1)
        val payload = data.toByteArray()
        val header = ByteArrayOutputStream().apply {
            write(14); write(0x20); u16(this, 2180); u32(this, payload.size.toLong()); write(".FIT".toByteArray())
        }.toByteArray()
        val headerCrc = crc(header)
        val completeHeader = header + byteArrayOf((headerCrc and 0xff).toByte(), (headerCrc ushr 8).toByte())
        val withoutFileCrc = completeHeader + payload
        val fileCrc = crc(withoutFileCrc)
        return withoutFileCrc + byteArrayOf((fileCrc and 0xff).toByte(), (fileCrc ushr 8).toByte())
    }

    fun validate(bytes: ByteArray): FitValidation {
        if (bytes.size < 18 || bytes[0].toInt() and 0xff !in 12..14) return FitValidation(false, "FIT 文件头无效", 0)
        if (String(bytes.copyOfRange(8, 12)) != ".FIT") return FitValidation(false, "缺少 FIT 标识", 0)
        val headerSize = bytes[0].toInt() and 0xff
        val dataSize = ByteBuffer.wrap(bytes, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
        if (headerSize + dataSize + 2 != bytes.size) return FitValidation(false, "FIT 数据长度不匹配", 0)
        val storedCrc = (bytes[bytes.size-2].toInt() and 0xff) or ((bytes.last().toInt() and 0xff) shl 8)
        if (crc(bytes.copyOf(bytes.size - 2)) != storedCrc) return FitValidation(false, "FIT CRC 校验失败", 0)
        return FitValidation(true, "标准 Activity FIT", -1)
    }

    private fun definition(out: ByteArrayOutputStream, local: Int, global: Int, fields: List<Field>) {
        out.write(0x40 or local); out.write(0); out.write(0); u16(out, global); out.write(fields.size)
        fields.forEach { out.write(it.number); out.write(it.size); out.write(it.baseType) }
    }
    private fun event(out: ByteArrayOutputStream, millis: Long, type: Int) { out.write(1); u32(out, fitTime(millis)); out.write(0); out.write(type) }
    private fun fitTime(ms: Long) = (Instant.ofEpochMilli(ms).epochSecond - FIT_EPOCH_OFFSET).coerceAtLeast(0)
    private fun degreesToSemicircles(v: Double) = (v * 2147483648.0 / 180.0).toLong().coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
    private fun stableSerial(id: String) = id.hashCode().toLong().and(0xffffffffL).takeIf { it != 0L } ?: 1L
    private fun nullableU8(o: ByteArrayOutputStream, v: Int?) = o.write(v?.coerceIn(0,254) ?: 0xff)
    private fun nullableU16(o: ByteArrayOutputStream, v: Int?) { if (v == null) { o.write(0xff); o.write(0xff) } else u16(o, v.coerceIn(0,65534)) }
    private fun nullableU32(o: ByteArrayOutputStream, v: Long?) { if (v == null) u32(o, 0xffffffffL) else u32(o, v.coerceIn(0,0xfffffffeL)) }
    private fun u16(o: ByteArrayOutputStream, v: Int) { o.write(v and 0xff); o.write(v ushr 8 and 0xff) }
    private fun s32(o: ByteArrayOutputStream, v: Int) = u32(o, v.toLong().and(0xffffffffL))
    private fun u32(o: ByteArrayOutputStream, v: Long) { repeat(4) { o.write((v ushr (it*8) and 0xff).toInt()) } }
    private fun crc(bytes: ByteArray): Int {
        val table = intArrayOf(0x0000,0xCC01,0xD801,0x1400,0xF001,0x3C00,0x2800,0xE401,0xA001,0x6C00,0x7800,0xB401,0x5000,0x9C01,0x8801,0x4400)
        var crc = 0
        bytes.forEach { b -> var tmp = table[crc and 0xF]; crc = (crc ushr 4) and 0x0FFF; crc = crc xor tmp xor table[b.toInt() and 0xF]; tmp = table[crc and 0xF]; crc = (crc ushr 4) and 0x0FFF; crc = crc xor tmp xor table[(b.toInt() ushr 4) and 0xF] }
        return crc and 0xffff
    }
}

package com.cheku.app

import com.cheku.app.data.EnergyRecordType
import java.io.File
import jxl.Workbook
import jxl.write.Label
import jxl.write.Number
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XiongFuelImporterTest {
    @Test
    fun parsesFuelAndChargeRowsWithAliasesAndDerivedValues() {
        val bytes = workbookBytes {
            addCell(Label(0, 0, "说明"))
            listOf("日期", "里程", "充能类型", "充能量", "单价", "金额").forEachIndexed { column, value ->
                addCell(Label(column, 1, value))
            }
            addCell(Label(0, 2, "2026-08-01 08:30"))
            addCell(Number(1, 2, 12000.0))
            addCell(Label(2, 2, "加油"))
            addCell(Number(3, 2, 40.0))
            addCell(Number(4, 2, 7.5))
            addCell(Label(0, 3, "2026/08/02"))
            addCell(Number(1, 3, 12100.0))
            addCell(Label(2, 3, "充电"))
            addCell(Number(4, 3, 1.2))
            addCell(Number(5, 3, 60.0))
        }

        val result = XiongFuelImporter.parse(bytes)

        assertEquals(2, result.recognized)
        assertEquals(2, result.rows.size)
        assertEquals(EnergyRecordType.Fuel, result.rows[0].input.type)
        assertEquals(300.0, result.rows[0].input.paidAmount, 0.001)
        assertEquals("未知", result.rows[0].input.fuelGrade)
        assertEquals(EnergyRecordType.Charge, result.rows[1].input.type)
        assertEquals(50.0, result.rows[1].input.amount, 0.001)
        assertEquals("第三方导入", result.rows[1].input.chargeMethod)
        assertEquals(100, result.rows[1].input.chargeLimitSocSnapshot)
        assertTrue(result.rows.all { it.createdAt > 0L })
    }

    @Test(expected = IllegalStateException::class)
    fun rejectsWorkbookWithoutRequiredColumns() {
        XiongFuelImporter.parse(workbookBytes { addCell(Label(0, 0, "日期")) })
    }

    private fun workbookBytes(block: jxl.write.WritableSheet.() -> Unit): ByteArray {
        val file = File.createTempFile("xiong-import", ".xls")
        try {
            val workbook = Workbook.createWorkbook(file)
            workbook.createSheet("油耗", 0).block()
            workbook.write()
            workbook.close()
            return file.readBytes()
        } finally {
            file.delete()
        }
    }
}

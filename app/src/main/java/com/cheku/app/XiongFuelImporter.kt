package com.cheku.app

import com.cheku.app.data.EnergyRecordInput
import com.cheku.app.data.EnergyRecordType
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Locale
import jxl.DateCell
import jxl.Sheet
import jxl.Workbook

data class XiongFuelRow(val input: EnergyRecordInput, val createdAt: Long)
data class XiongFuelParseResult(val rows: List<XiongFuelRow>, val recognized: Int)

object XiongFuelImporter {
    fun parse(bytes: ByteArray): XiongFuelParseResult {
        val workbook = Workbook.getWorkbook(ByteArrayInputStream(bytes))
        return try {
            val sheet = workbook.getSheet(0)
            val header = (0 until sheet.rows).firstOrNull { row ->
                (0 until sheet.columns).any { sheet.getCell(it, row).contents.trim().contains("日期") }
            } ?: error("未找到小熊油耗表头")
            val dateCol = sheet.column(header, "日期时间", "日期")
            val mileageCol = sheet.column(header, "总里程", "里程")
            val typeCol = sheet.column(header, "充能类型（加油/充电）", "充能类型")
            val amountCol = sheet.column(header, "充能量")
            val priceCol = sheet.column(header, "机显单价", "单价")
            val costCol = sheet.column(header, "机显金额", "金额")
            var recognized = 0
            val rows = (header + 1 until sheet.rows).mapNotNull { row ->
                val type = when (val text = sheet.getCell(typeCol, row).contents.trim()) {
                    else -> when {
                        text.contains("充电") -> EnergyRecordType.Charge
                        text.contains("加油") -> EnergyRecordType.Fuel
                        else -> return@mapNotNull null
                    }
                }
                recognized++
                val mileage = sheet.number(row, mileageCol) ?: return@mapNotNull null
                val quantity = sheet.number(row, amountCol) ?: 0.0
                val price = sheet.number(row, priceCol) ?: 0.0
                val enteredCost = sheet.number(row, costCol) ?: 0.0
                val cost = if (enteredCost > 0.0) enteredCost else quantity * price
                val resolvedQuantity = if (quantity > 0.0) quantity else if (price > 0.0) cost / price else 0.0
                val resolvedPrice = if (price > 0.0) price else if (resolvedQuantity > 0.0) cost / resolvedQuantity else 0.0
                if (mileage < 0.0 || resolvedQuantity <= 0.0 || cost <= 0.0) return@mapNotNull null
                val createdAt = sheet.date(row, dateCol) ?: return@mapNotNull null
                XiongFuelRow(EnergyRecordInput(
                    type = type, mileage = mileage, amount = resolvedQuantity, orderAmount = cost, paidAmount = cost,
                    isFull = true, missedPrevious = false,
                    fuelGrade = if (type == EnergyRecordType.Fuel) "未知" else "",
                    unitPrice = if (type == EnergyRecordType.Fuel) resolvedPrice else 0.0,
                    chargeMethod = if (type == EnergyRecordType.Charge) "第三方导入" else "",
                    electricityPrice = if (type == EnergyRecordType.Charge) resolvedPrice else 0.0,
                    chargeLimitSocSnapshot = if (type == EnergyRecordType.Charge) 100 else null,
                    note = "小熊油耗导入"
                ), createdAt)
            }
            XiongFuelParseResult(rows, recognized)
        } finally { workbook.close() }
    }

    private fun Sheet.column(header: Int, vararg names: String): Int =
        (0 until columns).firstOrNull { getCell(it, header).contents.trim() in names }
            ?: error("缺少“${names.joinToString("/")}”列")

    private fun Sheet.number(row: Int, column: Int): Double? =
        Regex("-?\\d+(?:\\.\\d+)?").find(getCell(column, row).contents.replace(",", ""))?.value?.toDoubleOrNull()

    private fun Sheet.date(row: Int, column: Int): Long? {
        val cell = getCell(column, row)
        if (cell is DateCell) return cell.date?.time
        return listOf("yyyy-MM-dd HH:mm", "yyyy-MM-dd", "yyyy/MM/dd HH:mm", "yyyy/MM/dd").firstNotNullOfOrNull { pattern ->
            runCatching { SimpleDateFormat(pattern, Locale.CHINA).apply { isLenient = false }.parse(cell.contents.trim())?.time }.getOrNull()
        }
    }
}

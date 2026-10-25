package com.cheku.app

import java.io.InputStream
import jxl.Cell
import jxl.Sheet
import jxl.Workbook
import java.io.ByteArrayInputStream

object XlsSheetReader {
    internal const val MAX_XLS_BYTES = 32 * 1024 * 1024

    /** 工作表数量上限 */
    internal const val MAX_SHEETS = 64

    /** 单表行数上限 */
    internal const val MAX_ROWS_PER_SHEET = 200_000

    /** 单表列数上限 */
    internal const val MAX_COLUMNS_PER_SHEET = 256

    /** 单个单元格字符上限 */
    internal const val MAX_CELL_CHARS = 32_768

    /** 一张工作表 */
    data class SheetData(
        val name: String,
        val headers: List<String>,
        val rows: List<List<String>>
    ) {
        /** 按表头名取列号 */
        fun columnOf(header: String): Int = headers.indexOf(header)

        /** 取某行某列 */
        fun cell(row: List<String>, header: String): String {
            val index = columnOf(header)
            return if (index in row.indices) row[index] else ""
        }
    }

    fun read(bytes: ByteArray): List<SheetData> {
        // 先看体积再交给 jxl：jxl 一旦接手就会开始分配，之后再说"太大了"已经晚了。
        require(bytes.size <= MAX_XLS_BYTES) {
            "单个数据卷过大（${bytes.size} 字节 > $MAX_XLS_BYTES）：拒绝解析，避免内存耗尽"
        }
        return ByteArrayInputStream(bytes).use { read(it) }
    }

    fun read(input: InputStream): List<SheetData> {
        val workbook = Workbook.getWorkbook(input)
        return try {
            val sheets = workbook.sheets
            require(sheets.size <= MAX_SHEETS) {
                "工作簿包含 ${sheets.size} 张表，超过上限 $MAX_SHEETS：拒绝解析"
            }
            sheets.map { sheet -> readSheet(sheet) }
        } finally {
            workbook.close()
        }
    }

    private fun readSheet(sheet: Sheet): SheetData {
        val name = sheet.name
        if (sheet.rows <= 0 || sheet.columns <= 0) return SheetData(name, emptyList(), emptyList())
        require(sheet.rows - 1 <= MAX_ROWS_PER_SHEET) {
            "工作表「$name」声明了 ${sheet.rows} 行，超过上限 $MAX_ROWS_PER_SHEET"
        }
        require(sheet.columns <= MAX_COLUMNS_PER_SHEET) {
            "工作表「$name」声明了 ${sheet.columns} 列，超过上限 $MAX_COLUMNS_PER_SHEET"
        }

        val headers = (0 until sheet.columns).map { column ->
            sheet.getCell(column, 0).safeContents()
        }
        // 末尾连续的空表头去掉（jxl 的 columns 可能比实际写的多）
        val width = headers.indexOfLast { it.isNotBlank() } + 1
        if (width <= 0) return SheetData(name, emptyList(), emptyList())
        val head = headers.subList(0, width)

        val rows = ArrayList<List<String>>(maxOf(0, sheet.rows - 1))
        for (r in 1 until sheet.rows) {
            val row = (0 until width).map { column -> sheet.getCell(column, r).safeContents() }
            if (row.all { it.isBlank() }) continue
            rows += row
        }
        return SheetData(name, head, rows)
    }

    private fun Cell.safeContents(): String =
        runCatching { contents }.getOrDefault("").trim().take(MAX_CELL_CHARS)

    fun SheetData.rowValues(row: List<String>): Map<String, String> =
        headers.withIndex().associate { (index, header) ->
            header to (if (index in row.indices) row[index] else "")
        }

    /** 供映射层做外键检查用 */
    fun SheetData.distinctOf(header: String): Set<String> {
        val index = columnOf(header)
        if (index < 0) return emptySet()
        return rows.mapNotNull { row -> row.getOrNull(index)?.takeIf { it.isNotBlank() } }.toSet()
    }
}

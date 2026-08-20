package com.hackerli.jizhang.data

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ExportProgress(val completedImages: Int, val totalImages: Int)

object FullExporter {
    fun write(
        output: OutputStream,
        expenses: List<Expense>,
        tags: List<QuickTag>,
        zoneId: ZoneId = ZoneId.systemDefault(),
        onProgress: (ExportProgress) -> Unit = {},
    ) {
        val photoNames = expenses.associate { expense ->
            expense.id to expense.photos.mapIndexed { index, photo ->
                photo.path to "bill-${expense.id}-${index + 1}.${File(photo.path).extension.ifBlank { "jpg" }}"
            }
        }
        val tagImageNames = tags.mapNotNull { tag ->
            tag.imagePath?.let { path ->
                tag.id to (path to "tag-${tag.id}.${File(path).extension.ifBlank { "jpg" }}")
            }
        }.toMap()
        photoNames.values.flatten().forEach { (path, _) ->
            val file = File(path)
            require(file.isFile && file.canRead() && file.length() > 0L) {
                "照片文件不存在或无法读取：${file.name}"
            }
        }
        tagImageNames.values.forEach { (path, _) ->
            val file = File(path)
            require(file.isFile && file.canRead() && file.length() > 0L) {
                "标签图片不存在或无法读取：${file.name}"
            }
        }
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("记得记账单.xlsx"))
            zip.write(buildWorkbook(expenses, tags, zoneId, photoNames, tagImageNames))
            zip.closeEntry()

            val total = expenses.sumOf { it.photos.size } + tagImageNames.size
            var completed = 0
            expenses.forEach { expense ->
                photoNames.getValue(expense.id).forEach { (path, name) ->
                    val file = File(path)
                    zip.putNextEntry(ZipEntry("photos/$name"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    completed++
                    onProgress(ExportProgress(completed, total))
                }
            }
            tags.forEach { tag ->
                tagImageNames[tag.id]?.let { (path, name) ->
                    zip.putNextEntry(ZipEntry("tag-icons/$name"))
                    File(path).inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    completed++
                    onProgress(ExportProgress(completed, total))
                }
            }
        }
    }

    internal fun buildWorkbook(
        expenses: List<Expense>,
        tags: List<QuickTag>,
        zoneId: ZoneId,
        photoNames: Map<Long, List<Pair<String, String>>> = expenses.associate { expense ->
            expense.id to expense.photos.mapIndexed { index, photo ->
                photo.path to "bill-${expense.id}-${index + 1}.${File(photo.path).extension.ifBlank { "jpg" }}"
            }
        },
        tagImageNames: Map<Long, Pair<String, String>> = tags.mapNotNull { tag ->
            tag.imagePath?.let { path ->
                tag.id to (path to "tag-${tag.id}.${File(path).extension.ifBlank { "jpg" }}")
            }
        }.toMap(),
    ): ByteArray {
        val sheets = listOf(
            Sheet("账单", billRows(expenses, zoneId)),
            Sheet("退款", refundRows(expenses, zoneId)),
            Sheet("照片", photoRows(expenses, photoNames)),
            Sheet("标签", tagRows(tags, tagImageNames)),
        )
        return ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                zip.xml("[Content_Types].xml", contentTypes(sheets.size))
                zip.xml("_rels/.rels", rootRelationships())
                zip.xml("xl/workbook.xml", workbook(sheets))
                zip.xml("xl/_rels/workbook.xml.rels", workbookRelationships(sheets.size))
                zip.xml("xl/styles.xml", styles())
                sheets.forEachIndexed { index, sheet ->
                    zip.xml("xl/worksheets/sheet${index + 1}.xml", worksheet(sheet.rows, zoneId))
                }
            }
        }.toByteArray()
    }

    private fun billRows(expenses: List<Expense>, zoneId: ZoneId): List<List<Cell>> = buildList {
        add(
            listOf(
                "账单ID", "消费时间", "时区", "原消费金额", "累计退款金额", "实际金额", "退款状态",
                "标签", "备注", "地点名称", "纬度", "经度", "定位精度（米）", "照片数量",
            ).map(Cell::Text),
        )
        expenses.sortedByDescending { it.occurredAt }.forEach { expense ->
            add(
                listOf(
                    Cell.Number(expense.id.toDouble()),
                    Cell.Date(expense.occurredAt),
                    Cell.Text(zoneId.id),
                    Cell.Number(expense.amountCents / 100.0),
                    Cell.Number(expense.refundedAmountCents / 100.0),
                    Cell.Number(expense.actualAmountCents / 100.0),
                    Cell.Text(
                        when (expense.refundStatus) {
                            RefundStatus.NONE -> "无"
                            RefundStatus.PARTIAL -> "部分退款"
                            RefundStatus.FULL -> "全额退款"
                        },
                    ),
                    Cell.Text(expense.tagName),
                    Cell.Text(expense.note),
                    Cell.Text(expense.locationLabel),
                    Cell.Number(expense.latitude),
                    Cell.Number(expense.longitude),
                    Cell.Number(expense.locationAccuracyMeters.toDouble()),
                    Cell.Number(expense.photos.size.toDouble()),
                ),
            )
        }
    }

    private fun refundRows(expenses: List<Expense>, zoneId: ZoneId): List<List<Cell>> = buildList {
        add(listOf("退款ID", "账单ID", "退款时间", "时区", "退款金额").map(Cell::Text))
        expenses.flatMap { it.refunds }.sortedByDescending { it.occurredAt }.forEach { refund ->
            add(
                listOf(
                    Cell.Number(refund.id.toDouble()),
                    Cell.Number(refund.expenseId.toDouble()),
                    Cell.Date(refund.occurredAt),
                    Cell.Text(zoneId.id),
                    Cell.Number(refund.amountCents / 100.0),
                ),
            )
        }
    }

    private fun photoRows(
        expenses: List<Expense>,
        photoNames: Map<Long, List<Pair<String, String>>>,
    ): List<List<Cell>> = buildList {
        add(listOf("账单ID", "照片文件名", "照片顺序").map(Cell::Text))
        expenses.forEach { expense ->
            photoNames.getValue(expense.id).forEachIndexed { index, (_, name) ->
                add(listOf(Cell.Number(expense.id.toDouble()), Cell.Text(name), Cell.Number((index + 1).toDouble())))
            }
        }
    }

    private fun tagRows(
        tags: List<QuickTag>,
        tagImageNames: Map<Long, Pair<String, String>>,
    ): List<List<Cell>> = buildList {
        add(listOf("标签ID", "标签名称", "图标类型", "Emoji", "标签图片文件名", "颜色", "固定排序", "是否停用").map(Cell::Text))
        tags.sortedWith(compareBy<QuickTag> { it.isArchived }.thenBy { it.sortOrder }).forEach { tag ->
            add(
                listOf(
                    Cell.Number(tag.id.toDouble()),
                    Cell.Text(tag.name),
                    Cell.Text(if (tag.imagePath == null) "Emoji" else "图片"),
                    Cell.Text(tag.emoji),
                    Cell.Text(tagImageNames[tag.id]?.second.orEmpty()),
                    Cell.Text(String.format(Locale.US, "#%08X", tag.colorArgb)),
                    Cell.Number(tag.sortOrder.toDouble()),
                    Cell.Text(if (tag.isArchived) "是" else "否"),
                ),
            )
        }
    }

    private fun worksheet(rows: List<List<Cell>>, zoneId: ZoneId): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>")
        rows.forEachIndexed { rowIndex, cells ->
            append("<row r=\"${rowIndex + 1}\">")
            cells.forEachIndexed { columnIndex, cell ->
                val ref = "${columnName(columnIndex)}${rowIndex + 1}"
                when (cell) {
                    is Cell.Text -> append("<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${xml(cell.value)}</t></is></c>")
                    is Cell.Number -> append("<c r=\"$ref\"><v>${cell.value}</v></c>")
                    is Cell.Date -> append("<c r=\"$ref\" s=\"1\"><v>${excelDate(cell.epochMillis, zoneId)}</v></c>")
                }
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    private fun excelDate(epochMillis: Long, zoneId: ZoneId): Double {
        val origin = LocalDateTime.of(1899, 12, 30, 0, 0)
        val local = Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDateTime()
        return Duration.between(origin, local).toMillis() / 86_400_000.0
    }

    private fun contentTypes(sheetCount: Int): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
        append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
        append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
        append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>")
        repeat(sheetCount) { index ->
            append("<Override PartName=\"/xl/worksheets/sheet${index + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
        }
        append("</Types>")
    }

    private fun rootRelationships() = """<?xml version="1.0" encoding="UTF-8"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
        </Relationships>""".trimIndent()

    private fun workbook(sheets: List<Sheet>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        append("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>")
        sheets.forEachIndexed { index, sheet ->
            append("<sheet name=\"${xml(sheet.name)}\" sheetId=\"${index + 1}\" r:id=\"rId${index + 1}\"/>")
        }
        append("</sheets></workbook>")
    }

    private fun workbookRelationships(sheetCount: Int): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
        repeat(sheetCount) { index ->
            append("<Relationship Id=\"rId${index + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet${index + 1}.xml\"/>")
        }
        append("<Relationship Id=\"rId${sheetCount + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>")
        append("</Relationships>")
    }

    private fun styles() = """<?xml version="1.0" encoding="UTF-8"?>
        <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
          <numFmts count="1"><numFmt numFmtId="164" formatCode="yyyy-mm-dd hh:mm:ss"/></numFmts>
          <fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>
          <fills count="1"><fill><patternFill patternType="none"/></fill></fills>
          <borders count="1"><border/></borders>
          <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
          <cellXfs count="2">
            <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
            <xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
          </cellXfs>
        </styleSheet>""".trimIndent()

    private fun columnName(index: Int): String {
        var value = index + 1
        val result = StringBuilder()
        while (value > 0) {
            result.append(('A'.code + (value - 1) % 26).toChar())
            value = (value - 1) / 26
        }
        return result.reverse().toString()
    }

    private fun xml(value: String): String = sanitizeXml(value)
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun sanitizeXml(value: String): String = buildString(value.length) {
        var index = 0
        while (index < value.length) {
            val codePoint = value.codePointAt(index)
            if (
                codePoint == 0x9 || codePoint == 0xA || codePoint == 0xD ||
                codePoint in 0x20..0xD7FF || codePoint in 0xE000..0xFFFD ||
                codePoint in 0x10000..0x10FFFF
            ) {
                appendCodePoint(codePoint)
            }
            index += Character.charCount(codePoint)
        }
    }

    private fun ZipOutputStream.xml(path: String, body: String) {
        putNextEntry(ZipEntry(path))
        write(body.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private data class Sheet(val name: String, val rows: List<List<Cell>>)
    private sealed interface Cell {
        data class Text(val value: String) : Cell
        data class Number(val value: Double) : Cell
        data class Date(val epochMillis: Long) : Cell
    }
}

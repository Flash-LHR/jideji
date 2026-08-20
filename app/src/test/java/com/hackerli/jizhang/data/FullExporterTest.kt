package com.hackerli.jizhang.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.ZoneId
import java.util.zip.ZipInputStream

class FullExporterTest {
    @Test
    fun fullExportContainsWorkbookAndAllImages() {
        val photo = File.createTempFile("jideji-photo", ".jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
            deleteOnExit()
        }
        val tagImage = File.createTempFile("jideji-tag", ".jpg").apply {
            writeBytes(byteArrayOf(5, 6, 7, 8))
            deleteOnExit()
        }
        val expense = expense(photo).copy(
            refunds = listOf(Refund(2, 1, 300, 1_700_000_100_000)),
        )
        val output = ByteArrayOutputStream()

        FullExporter.write(
            output = output,
            expenses = listOf(expense),
            tags = listOf(QuickTag(7, "餐饮", "🍜", 0, 0, imagePath = tagImage.absolutePath)),
            zoneId = ZoneId.of("Asia/Shanghai"),
        )

        val entries = unzip(output.toByteArray())
        assertTrue("记得记账单.xlsx" in entries)
        assertEquals(photo.readBytes().toList(), entries.getValue("photos/bill-1-1.jpg").toList())
        assertEquals(tagImage.readBytes().toList(), entries.getValue("tag-icons/tag-7.jpg").toList())

        val workbookEntries = unzip(entries.getValue("记得记账单.xlsx"))
        val workbook = workbookEntries.getValue("xl/workbook.xml").decodeToString()
        assertTrue(workbook.contains("账单"))
        assertTrue(workbook.contains("退款"))
        assertTrue(workbook.contains("照片"))
        assertTrue(workbook.contains("标签"))
        val billSheet = workbookEntries.getValue("xl/worksheets/sheet1.xml").decodeToString()
        assertTrue(billSheet.contains("Asia/Shanghai"))
        assertTrue(billSheet.contains("部分退款"))
        val tagSheet = workbookEntries.getValue("xl/worksheets/sheet4.xml").decodeToString()
        assertTrue(tagSheet.contains("图标类型"))
        assertTrue(tagSheet.contains("tag-7.jpg"))
    }

    @Test
    fun refusesExportBeforeWritingWhenPhotoIsMissing() {
        val missingPhoto = File(System.getProperty("java.io.tmpdir"), "missing-${System.nanoTime()}.jpg")
        val output = ByteArrayOutputStream()

        assertThrows(IllegalArgumentException::class.java) {
            FullExporter.write(
                output = output,
                expenses = listOf(expense(missingPhoto)),
                tags = emptyList(),
            )
        }
        assertEquals(0, output.size())
    }

    @Test
    fun removesInvalidXmlControlCharactersAndKeepsEmoji() {
        val workbook = FullExporter.buildWorkbook(
            expenses = listOf(expense(File("unused")).copy(note = "正常\u0001备注🙂")),
            tags = emptyList(),
            zoneId = ZoneId.of("Asia/Shanghai"),
        )
        val billSheet = unzip(workbook).getValue("xl/worksheets/sheet1.xml").decodeToString()

        assertFalse(billSheet.contains('\u0001'))
        assertTrue(billSheet.contains("正常备注🙂"))
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> = buildMap {
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                put(entry.name, zip.readBytes())
                zip.closeEntry()
            }
        }
    }

    private fun expense(photo: File) = Expense(
        id = 1,
        amountCents = 1_200,
        tagId = 7,
        tagName = "餐饮",
        tagEmoji = "🍜",
        tagColorArgb = 0,
        occurredAt = 1_700_000_000_000,
        note = "有逗号,还有\"引号\"和 & 符号",
        latitude = 31.2,
        longitude = 121.4,
        locationAccuracyMeters = 10f,
        locationLabel = "上海",
        photos = listOf(ExpensePhoto(1, 1, photo.absolutePath, 0)),
    )
}

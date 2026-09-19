package org.nexoraofficial.console.data

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A REAL .xlsx, WRITTEN BY HAND.
 *
 * An xlsx file is a zip of XML parts, nothing more, so the app writes one
 * itself rather than carrying a spreadsheet library. Apache POI would add
 * something like ten megabytes to an app that is fifteen; this is two hundred
 * lines and produces a file Excel, LibreOffice and Google Sheets all open.
 *
 * Text is written as an inline string, which costs a few bytes more than the
 * shared-strings table and removes a whole part and its bookkeeping. Numbers
 * are written as numbers, so a column of transactions can be summed in Excel
 * instead of being a column of text that looks like numbers.
 */
object Xlsx {

    sealed interface Cell {
        data class Text(val value: String) : Cell
        data class Number(val value: Double) : Cell
    }

    fun text(v: String?): Cell = Cell.Text(v ?: "")
    fun num(v: Int): Cell = Cell.Number(v.toDouble())

    data class Sheet(
        val name: String,
        val header: List<String>,
        val rows: List<List<Cell>>
    )

    fun build(sheets: List<Sheet>): ByteArray {
        require(sheets.isNotEmpty()) { "a workbook needs at least one sheet" }
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.put("[Content_Types].xml", contentTypes(sheets.size))
            zip.put("_rels/.rels", rootRels())
            zip.put("xl/workbook.xml", workbook(sheets))
            zip.put("xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
            zip.put("xl/styles.xml", styles())
            sheets.forEachIndexed { i, s ->
                zip.put("xl/worksheets/sheet${i + 1}.xml", sheet(s))
            }
        }
        return out.toByteArray()
    }

    /* ---- the parts ---- */

    private fun contentTypes(sheetCount: Int): String = buildString {
        append(HEAD)
        append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
        append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
        append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
        append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>")
        for (i in 1..sheetCount) {
            append("<Override PartName=\"/xl/worksheets/sheet$i.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
        }
        append("</Types>")
    }

    private fun rootRels(): String =
        HEAD +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
            "</Relationships>"

    private fun workbook(sheets: List<Sheet>): String = buildString {
        append(HEAD)
        append("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" ")
        append("xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>")
        sheets.forEachIndexed { i, s ->
            append("<sheet name=\"${esc(sheetName(s.name))}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>")
        }
        append("</sheets></workbook>")
    }

    private fun workbookRels(sheetCount: Int): String = buildString {
        append(HEAD)
        append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
        for (i in 1..sheetCount) {
            append("<Relationship Id=\"rId$i\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet$i.xml\"/>")
        }
        append("<Relationship Id=\"rId${sheetCount + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>")
        append("</Relationships>")
    }

    /** Two cell formats: 0 plain, 1 bold — the header row. */
    private fun styles(): String =
        HEAD +
            "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
            "<fonts count=\"2\">" +
            "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
            "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
            "</fonts>" +
            "<fills count=\"2\">" +
            "<fill><patternFill patternType=\"none\"/></fill>" +
            "<fill><patternFill patternType=\"gray125\"/></fill>" +
            "</fills>" +
            "<borders count=\"1\"><border/></borders>" +
            "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
            "<cellXfs count=\"2\">" +
            "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
            "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>" +
            "</cellXfs>" +
            "</styleSheet>"

    private fun sheet(s: Sheet): String = buildString {
        append(HEAD)
        append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")

        /* A width per column so nothing opens as a row of #### or a squashed
           name. Measured from the header and the first hundred rows. */
        append("<cols>")
        s.header.indices.forEach { i ->
            var w = s.header.getOrNull(i)?.length ?: 8
            s.rows.take(100).forEach { r ->
                val len = when (val c = r.getOrNull(i)) {
                    is Cell.Text -> c.value.length
                    is Cell.Number -> c.value.toLong().toString().length
                    null -> 0
                }
                if (len > w) w = len
            }
            append("<col min=\"${i + 1}\" max=\"${i + 1}\" width=\"${(w + 3).coerceIn(9, 46)}\" customWidth=\"1\"/>")
        }
        append("</cols>")

        append("<sheetData>")
        append("<row r=\"1\">")
        s.header.forEachIndexed { i, h ->
            append("<c r=\"${col(i)}1\" t=\"inlineStr\" s=\"1\"><is><t>${esc(h)}</t></is></c>")
        }
        append("</row>")

        s.rows.forEachIndexed { r, row ->
            val n = r + 2
            append("<row r=\"$n\">")
            row.forEachIndexed { i, cell ->
                val ref = "${col(i)}$n"
                when (cell) {
                    is Cell.Text ->
                        if (cell.value.isNotEmpty()) {
                            append("<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${esc(cell.value)}</t></is></c>")
                        }
                    is Cell.Number ->
                        append("<c r=\"$ref\"><v>${trim(cell.value)}</v></c>")
                }
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    /* ---- helpers ---- */

    private const val HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"

    private fun ZipOutputStream.put(name: String, body: String) {
        putNextEntry(ZipEntry(name))
        write(body.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    /** A, B … Z, AA, AB … */
    private fun col(index: Int): String {
        var i = index
        val sb = StringBuilder()
        while (true) {
            sb.insert(0, ('A' + i % 26))
            i = i / 26 - 1
            if (i < 0) break
        }
        return sb.toString()
    }

    /** Excel refuses a sheet name over 31 characters or carrying : \ / ? * [ ] */
    private fun sheetName(raw: String): String {
        val cleaned = raw.replace(Regex("[\\\\/?*\\[\\]:]"), " ").trim()
        return if (cleaned.length > 31) cleaned.take(31) else cleaned.ifEmpty { "Sheet" }
    }

    private fun trim(d: Double): String =
        if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()

    private fun esc(s: String): String {
        val sb = StringBuilder(s.length + 16)
        for (ch in s) {
            when {
                ch == '&' -> sb.append("&amp;")
                ch == '<' -> sb.append("&lt;")
                ch == '>' -> sb.append("&gt;")
                ch == '"' -> sb.append("&quot;")
                ch == '\'' -> sb.append("&apos;")
                /* Control characters are illegal in XML 1.0 and would make the
                   whole file unreadable, so they are dropped rather than risked. */
                ch.code < 0x20 && ch != '\t' && ch != '\n' && ch != '\r' -> Unit
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}

package com.easyledger.app.data.export

import com.easyledger.app.domain.settlement.Settlement
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object XlsxExporter {
    private fun xml(value: String): String = buildString {
        value.forEach { c -> when (c) { '&' -> append("&amp;"); '<' -> append("&lt;"); '>' -> append("&gt;"); '"' -> append("&quot;"); else -> if (c == '\n' || c == '\r' || c == '\t' || c.code >= 32) append(c) } }
    }
    fun write(doc: Settlement, output: OutputStream) {
        ZipOutputStream(output).use { zip ->
            fun part(name: String, text: String) { zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray(Charsets.UTF_8)); zip.closeEntry() }
            part("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>""")
            part("_rels/.rels", """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            part("xl/workbook.xml", """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="工作结算单" sheetId="1" r:id="rId1"/></sheets><definedNames><definedName name="_xlnm.Print_Titles" localSheetId="0">'工作结算单'!${'$'}1:${'$'}3</definedName></definedNames></workbook>""")
            part("xl/_rels/workbook.xml.rels", """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
            part("xl/styles.xml", """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="2"><font><sz val="12"/><name val="Microsoft YaHei"/></font><font><b/><sz val="14"/><name val="Microsoft YaHei"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="2"><border/><border><left style="thin"/><right style="thin"/><top style="thin"/><bottom style="thin"/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="3"><xf numFmtId="49" fontId="0" fillId="0" borderId="1" xfId="0" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf><xf numFmtId="0" fontId="1" fillId="0" borderId="1" xfId="0" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf></cellXfs></styleSheet>""")
            fun row(index: Int, cells: List<String>, height: Int, style: Int = 0): String = "<row r=\"$index\" ht=\"$height\" customHeight=\"1\">" + cells.mapIndexed { c, text ->
                "<c r=\"${('A'.code + c).toChar()}$index\" s=\"$style\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${xml(text)}</t></is></c>"
            }.joinToString("") + "</row>"
            val body = buildString {
                append(row(1, listOf("${doc.title} 工作结算单"), 32, 1)); append(row(2, listOf(doc.period), 28, 1)); val headerWidths=listOf(10,10,14,7,5,7,9,15)
                val headerLines=doc.columns.mapIndexed { i,text -> (text.length+headerWidths[i]-1)/headerWidths[i] }.max().coerceAtLeast(1)
                append(row(3, doc.columns, maxOf(30, headerLines*20+10), 2))
                doc.rows.forEachIndexed { i, r ->
                    // Estimate Chinese glyph width and explicit lines; never use spaces for alignment.
                    val widths = listOf(10, 10, 14, 7, 5, 7, 9, 15)
                    val lines = r.cells.mapIndexed { c, value -> value.split('\n').sumOf { maxOf(1, (it.length + widths[c] - 1) / widths[c]) } }.maxOrNull() ?: 1
                    require(lines <= 20) { "一条记录内容超过 Excel 单行可显示高度，请缩短备注或使用 PDF／PNG 导出" }
                    append(row(i + 4, r.cells, maxOf(28, lines * 20)))
                }
                append(row(doc.rows.size + 4, listOf("合计", "", "", "", "", "", doc.total, ""), 32, 2))
                append(row(doc.rows.size + 5, listOf("负责人签字："), 50, 1))
            }
            val widths = listOf(15, 20, 23, 14, 10, 14, 16, 30)
            part("xl/worksheets/sheet1.xml", """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetPr><pageSetUpPr fitToPage="1"/></sheetPr><sheetViews><sheetView workbookViewId="0"><pane ySplit="3" topLeftCell="A4" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews><cols>${widths.mapIndexed { i, w -> "<col min=\"${i+1}\" max=\"${i+1}\" width=\"$w\" customWidth=\"1\"/>" }.joinToString("")}</cols><sheetData>$body</sheetData><mergeCells count="3"><mergeCell ref="A1:H1"/><mergeCell ref="A2:H2"/><mergeCell ref="A${doc.rows.size+5}:H${doc.rows.size+5}"/></mergeCells><printOptions horizontalCentered="1"/><pageMargins left="0.25" right="0.25" top="0.4" bottom="0.4" header="0.2" footer="0.2"/><pageSetup paperSize="9" orientation="landscape" fitToWidth="1" fitToHeight="0"/></worksheet>""")
        }
    }
}

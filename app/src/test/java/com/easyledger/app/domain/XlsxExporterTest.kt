package com.easyledger.app.domain
import com.easyledger.app.data.export.XlsxExporter
import com.easyledger.app.domain.settlement.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class XlsxExporterTest {
    @Test fun xlsxUsesLineBreaksStylesAndExactAmountsWithoutFormulas() {
        val doc=Settlement("项目 & <施工>","2026-10-14",defaultColumnNames,listOf(SettlementRow(listOf("2026-10-14","项目","07:00–11:00\n13:00–17:00\n18:00–20:00","10","小时","20.00","200.00","=1+1"))),"200.00")
        val output=ByteArrayOutputStream();XlsxExporter.write(doc,output)
        val entries=mutableMapOf<String,String>()
        ZipInputStream(output.toByteArray().inputStream()).use { zip -> var entry=zip.nextEntry;while(entry!=null){entries[entry.name]=zip.readBytes().toString(Charsets.UTF_8);entry=zip.nextEntry} }
        assertTrue(entries.keys.containsAll(listOf("[Content_Types].xml","xl/workbook.xml","xl/styles.xml","xl/worksheets/sheet1.xml")))
        entries.values.forEach { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it.byteInputStream()) }
        val sheet=entries.getValue("xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("07:00–11:00\n13:00–17:00\n18:00–20:00"))
        assertTrue(sheet.contains("ht=\"60\""));assertTrue(sheet.contains("200.00"));assertFalse(sheet.contains("<f>"));assertTrue(sheet.contains("=1+1"))
        assertTrue(entries.getValue("xl/styles.xml").contains("wrapText=\"1\""))
    }
}

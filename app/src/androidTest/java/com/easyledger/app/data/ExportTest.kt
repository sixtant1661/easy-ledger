package com.easyledger.app.data

import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.easyledger.app.data.export.PageExporter
import com.easyledger.app.domain.settlement.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

@RunWith(AndroidJUnit4::class)
class ExportTest {
    @Test fun multiPagePdfAndPngAreReadableAndPageCountsMatch() {
        val rows=List(40){ SettlementRow(listOf("2026-10-14","合成工程","07:00–11:00\n13:00–17:00\n18:00–20:00","10","小时","25.00","250.00","仅合成测试")) }
        val doc=Settlement("合成工程","2026-10-14 至 2026-10-14",defaultColumnNames,rows,"10000.00")
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val file=File(context.cacheDir,"test-${UUID.randomUUID()}.pdf")
        try {
            file.outputStream().use { PageExporter.pdf(doc,it) }
            val pdfPages=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use { it.pageCount } }
            assertTrue(pdfPages>1)
            val png=ByteArrayOutputStream();PageExporter.pngZip(doc,png)
            var count=0
            ZipInputStream(png.toByteArray().inputStream()).use { zip ->
                var entry=zip.nextEntry
                while(entry!=null) { val bytes=zip.readBytes();val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size);assertNotNull(bitmap);assertEquals(1120,bitmap.width);bitmap.recycle();count++;entry=zip.nextEntry }
            }
            assertEquals(pdfPages,count)
        }finally{file.delete()}
    }
}

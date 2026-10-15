package com.easyledger.app.data.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.graphics.createBitmap
import com.easyledger.app.domain.settlement.Settlement
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** PDF and PNG use the same measured rows and drawing code; the source is an immutable settlement. */
object PageExporter {
    private const val WIDTH = 1120
    private const val HEIGHT = 790
    private val widths = listOf(110, 140, 160, 95, 65, 95, 110, 305)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f; color = Color.rgb(17, 24, 39); typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
    private data class Block(val cells: List<List<String>>, val lines: Int)
    private fun wrap(text: String, width: Int): List<String> = text.split('\n').flatMap { line ->
        if (line.isEmpty()) listOf("") else buildList {
            var offset = 0
            while (offset < line.length) {
                var count = paint.breakText(line, offset, line.length, true, (width - 14).toFloat(), null).coerceAtLeast(1)
                // Do not split UTF-16 surrogate pairs across lines.
                if (offset + count < line.length && Character.isHighSurrogate(line[offset + count - 1]) && count > 1) count--
                add(line.substring(offset, offset + count)); offset += count
            }
        }
    }
    private fun headerHeight(doc: Settlement) = doc.columns.mapIndexed { i,v -> wrap(v,widths[i]).size }.max().coerceAtLeast(1) * 24 + 12
    private fun titleLines(doc: Settlement) = wrap("${doc.title} 工作结算单", WIDTH-40).also { require(it.size <= 8) { "项目名称过长，无法完整分页，请缩短显示名称后导出" } }
    private fun headerY(doc: Settlement) = titleLines(doc).size * 24 + 56
    private fun available(doc: Settlement) = (HEIGHT - headerY(doc) - headerHeight(doc) - 92).also { require(it >= 100) { "列名过长，请缩短后导出" } }
    private fun pages(doc: Settlement): List<List<Block>> {
        val capacity = available(doc)
        val maxLines = ((capacity-12)/24).coerceAtLeast(1)
        val blocks = doc.rows.flatMap { row ->
            val cells = row.cells.mapIndexed { i, value -> wrap(value, widths[i]) }
            val max = cells.maxOfOrNull { it.size } ?: 1
            (0 until max step maxLines).map { offset -> Block(cells.map { it.drop(offset).take(maxLines) }, minOf(maxLines, max - offset)) }
        }
        val result = mutableListOf<List<Block>>(); var current = mutableListOf<Block>(); var used = 0
        blocks.forEach { block ->
            val h = maxOf(34, block.lines * 24 + 12)
            if (used + h > capacity && current.isNotEmpty()) { result.add(current); current = mutableListOf(); used = 0 }
            current.add(block); used += h
        }
        result.add(current)
        return result
    }
    private fun draw(canvas: Canvas, doc: Settlement, rows: List<Block>, index: Int, count: Int) {
        canvas.drawColor(Color.WHITE)
        paint.color = Color.rgb(17, 24, 39); paint.textSize = 18f
        titleLines(doc).forEachIndexed { i, line -> canvas.drawText(line, 20f, 28f+i*24, paint) }
        canvas.drawText("${doc.period} · 第 ${index+1}/$count 页", 20f, (headerY(doc)-14).toFloat(), paint)
        var y = headerY(doc).toFloat()
        fun row(cells: List<List<String>>, height: Int, header: Boolean = false) {
            var x = 20f
            if (header) canvas.drawRect(x, y, (WIDTH-20).toFloat(), y+height, Paint().apply { color = Color.rgb(239,246,255) })
            cells.forEachIndexed { column, lines ->
                val border = Paint().apply { color = Color.rgb(156,163,175); style = Paint.Style.STROKE; strokeWidth = 1f }
                canvas.drawRect(x, y, x + widths[column], y + height, border)
                lines.forEachIndexed { line, text -> canvas.drawText(text, if(column in listOf(3,5,6)) x+widths[column]-7-paint.measureText(text) else x+7, y + 24 + line * 24, paint) }
                x += widths[column]
            }
            y += height
        }
        row(doc.columns.mapIndexed { i, t -> wrap(t, widths[i]) }, headerHeight(doc), true)
        rows.forEach { row(it.cells, maxOf(34, it.lines * 24 + 12)) }
        if (index == count - 1) {
            canvas.drawText("合计：${doc.total} 元", 20f, y + 30, paint)
            canvas.drawText("负责人签字：", 20f, y + 66, paint)
        }
    }
    @Synchronized fun pdf(doc: Settlement, output: OutputStream) {
        val pages = pages(doc)
        val pdf = PdfDocument()
        try {
            pages.forEachIndexed { i, rows ->
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(842, 595, i+1).create())
                page.canvas.scale(842f/WIDTH,595f/HEIGHT)
                draw(page.canvas, doc, rows, i, pages.size); pdf.finishPage(page)
            }
            pdf.writeTo(output)
        } finally { pdf.close() }
    }
    @Synchronized fun pngFiles(doc: Settlement, directory: java.io.File, baseName: String): List<java.io.File> {
        val all=pages(doc)
        return all.mapIndexed { i,rows ->
            val file=java.io.File(directory, if(all.size==1) "$baseName.png" else "${baseName}_第${i+1}页.png")
            val bitmap=createBitmap(WIDTH,HEIGHT,Bitmap.Config.ARGB_8888)
            try { draw(Canvas(bitmap),doc,rows,i,all.size);file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) { "图片生成失败" } };file }
            finally {bitmap.recycle()}
        }
    }
    @Synchronized fun pngZip(doc: Settlement, output: OutputStream) {
        val pages = pages(doc)
        ZipOutputStream(output).use { zip ->
            pages.forEachIndexed { i, rows ->
                val bitmap = createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
                try {
                    draw(Canvas(bitmap), doc, rows, i, pages.size)
                    zip.putNextEntry(ZipEntry("结算单-${i+1}.png"))
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, zip)) { "图片生成失败" }
                    zip.closeEntry()
                } finally { bitmap.recycle() }
            }
        }
    }
}

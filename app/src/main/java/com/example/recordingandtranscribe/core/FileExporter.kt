package com.example.recordingandtranscribe.core

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object FileExporter {

    fun exportToTxt(context: Context, file: File, metadata: RecordingMetadata): File? {
        val exportFile = File(context.cacheDir, "${file.nameWithoutExtension}.txt")
        val content = buildString {
            appendLine("Transcript for ${file.name}")
            appendLine("=" * 20)
            appendLine(metadata.transcript ?: "No transcript available.")
            if (metadata.summary != null) {
                appendLine("\nSummary")
                appendLine("-" * 10)
                appendLine(metadata.summary)
            }
            if (metadata.actionItems.isNotEmpty()) {
                appendLine("\nAction Items")
                appendLine("-" * 10)
                metadata.actionItems.forEach { appendLine("- $it") }
            }
            if (metadata.tags.isNotEmpty()) {
                appendLine("\nTags")
                appendLine("-" * 10)
                appendLine(metadata.tags.joinToString(", "))
            }
            if (metadata.keywords.isNotEmpty()) {
                appendLine("\nKeywords")
                appendLine("-" * 10)
                appendLine(metadata.keywords.joinToString(", "))
            }
            if (metadata.emotionAnalysis != null) {
                appendLine("\nEmotion Analysis")
                appendLine("-" * 10)
                appendLine(metadata.emotionAnalysis)
            }
        }
        exportFile.writeText(content)
        return exportFile
    }

    fun exportToPdf(context: Context, file: File, metadata: RecordingMetadata): File? {
        val pdfDocument = PdfDocument()
        var pageNum = 1
        var pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNum).create() // A4 size
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        val paint = Paint().apply {
            textSize = 12f
            color = Color.BLACK
        }

        var y = 50f
        canvas.drawText("Transcript: ${file.name}", 50f, y, paint)
        y += 30f

        fun drawTextWrapped(text: String, isBold: Boolean = false) {
            paint.isFakeBoldText = isBold
            var remainingText = text
            val maxWidth = 495f // 595 width - 50 margin * 2
            
            if (remainingText.isEmpty()) {
                y += 20f
                return
            }
            
            while (remainingText.isNotEmpty()) {
                val count = paint.breakText(remainingText, true, maxWidth, null)
                val chunk = remainingText.substring(0, count)
                remainingText = remainingText.substring(count)
                
                if (y > 780f) {
                    pdfDocument.finishPage(page)
                    pageNum++
                    pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNum).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    y = 50f
                }
                canvas.drawText(chunk, 50f, y, paint)
                y += 20f
            }
            paint.isFakeBoldText = false
        }

        val transcriptLines = metadata.transcript?.lines() ?: listOf("No transcript")
        transcriptLines.forEach { drawTextWrapped(it) }

        if (metadata.summary != null) {
            y += 20f
            drawTextWrapped("Summary", isBold = true)
            drawTextWrapped("----------")
            metadata.summary.lines().forEach { drawTextWrapped(it) }
        }

        if (metadata.actionItems.isNotEmpty()) {
            y += 20f
            drawTextWrapped("Action Items", isBold = true)
            drawTextWrapped("----------")
            metadata.actionItems.forEach { drawTextWrapped("- $it") }
        }

        if (metadata.tags.isNotEmpty()) {
            y += 20f
            drawTextWrapped("Tags", isBold = true)
            drawTextWrapped("----------")
            drawTextWrapped(metadata.tags.joinToString(", "))
        }

        if (metadata.keywords.isNotEmpty()) {
            y += 20f
            drawTextWrapped("Keywords", isBold = true)
            drawTextWrapped("----------")
            drawTextWrapped(metadata.keywords.joinToString(", "))
        }

        if (metadata.emotionAnalysis != null) {
            y += 20f
            drawTextWrapped("Emotion Analysis", isBold = true)
            drawTextWrapped("----------")
            metadata.emotionAnalysis.lines().forEach { drawTextWrapped(it) }
        }

        pdfDocument.finishPage(page)
        
        val pdfFile = File(context.cacheDir, "${file.nameWithoutExtension}.pdf")
        FileOutputStream(pdfFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()
        
        return pdfFile
    }

    fun shareFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (file.name.endsWith(".pdf")) "application/pdf" else "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "Share via").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}

private operator fun String.times(n: Int): String = this.repeat(n)

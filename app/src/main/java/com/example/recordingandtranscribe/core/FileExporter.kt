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
        }
        exportFile.writeText(content)
        return exportFile
    }

    fun exportToPdf(context: Context, file: File, metadata: RecordingMetadata): File? {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint().apply {
            textSize = 12f
            color = Color.BLACK
        }

        var y = 50f
        canvas.drawText("Transcript: ${file.name}", 50f, y, paint)
        y += 30f

        val transcriptLines = metadata.transcript?.lines() ?: listOf("No transcript")
        transcriptLines.forEach { line ->
            // Simple line wrap check (Max 80 chars approx)
            line.chunked(80).forEach { chunk ->
                if (y > 780f) return@forEach // Simple page break skip (just one page for demo)
                canvas.drawText(chunk, 50f, y, paint)
                y += 20f
            }
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
        context.startActivity(Intent.createChooser(intent, "Share via"))
    }
}

private operator fun String.times(n: Int): String = this.repeat(n)

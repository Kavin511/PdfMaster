package com.pdfmaster.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * PDF overlay editor built on PDFBox-Android (com.tom_roush.pdfbox).
 *
 * IMPORTANT: this was previously implemented on OpenPDF (com.lowagie), which references
 * `java.awt.*` — classes that do not exist on Android — so every operation threw
 * `NoClassDefFoundError: java.awt.Color` at `PdfStamper.<init>` and wrote a 0-byte file.
 * PDFBox-Android is fully Android-native. All edits are stamped onto the ORIGINAL pages via
 * append-mode content streams, so existing vector text / search / accessibility are preserved.
 *
 * Coordinate convention for the data classes below: top-left origin, PDF points. PDFBox uses a
 * bottom-left origin, so we flip Y here (pdfY = pageHeight - y - height).
 */
object OpenPdfEditor {

    private const val TAG = "OpenPdfEditor"

    data class TextEdit(
        val pageIndex: Int,
        val x: Float,
        val y: Float,
        val text: String,
        val fontSize: Float = 12f,
        val fontColor: Int = AndroidColor.BLACK,
        val isBold: Boolean = false,
        val isItalic: Boolean = false,
    )

    data class WhiteoutRect(
        val pageIndex: Int,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
    )

    data class ImageOverlay(
        val pageIndex: Int,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val bitmap: Bitmap,
    )

    data class HighlightRect(
        val pageIndex: Int,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val color: Int = AndroidColor.YELLOW,
        val opacity: Float = 0.35f,
    )

    private fun ensureInit(context: Context) {
        // Idempotent; required before PDFBox font/stream operations on Android.
        runCatching { PDFBoxResourceLoader.init(context.applicationContext) }
    }

    /**
     * Apply overlay edits onto [sourceUri] and write to [outputFile]. Writes to a temp file and
     * only moves it into place after a fully successful save, so a failure can never leave a
     * 0-byte or partially-written output. Returns false on any failure.
     */
    suspend fun applyEdits(
        context: Context,
        sourceUri: Uri,
        outputFile: File,
        textEdits: List<TextEdit> = emptyList(),
        whiteouts: List<WhiteoutRect> = emptyList(),
        images: List<ImageOverlay> = emptyList(),
        highlights: List<HighlightRect> = emptyList(),
    ): Boolean = withContext(Dispatchers.IO) {
        ensureInit(context)
        val tmp = File(outputFile.parentFile, "${outputFile.name}.tmp")
        var document: PDDocument? = null
        try {
            val bytes = context.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
                ?: return@withContext false
            document = PDDocument.load(bytes)
            val pageCount = document.numberOfPages

            for (pageIndex in 0 until pageCount) {
                val hasWork = whiteouts.any { it.pageIndex == pageIndex } ||
                    highlights.any { it.pageIndex == pageIndex } ||
                    textEdits.any { it.pageIndex == pageIndex } ||
                    images.any { it.pageIndex == pageIndex }
                if (!hasWork) continue

                val page = document.getPage(pageIndex)
                val pageHeight = page.mediaBox.height
                PDPageContentStream(
                    document, page, PDPageContentStream.AppendMode.APPEND, true, true,
                ).use { cs ->
                    whiteouts.filter { it.pageIndex == pageIndex }.forEach { drawWhiteout(cs, it, pageHeight) }
                    highlights.filter { it.pageIndex == pageIndex }.forEach { drawHighlight(cs, it, pageHeight) }
                    images.filter { it.pageIndex == pageIndex }.forEach { drawImage(document, cs, it, pageHeight) }
                    textEdits.filter { it.pageIndex == pageIndex }.forEach { drawText(cs, it, pageHeight) }
                }
            }

            tmp.parentFile?.mkdirs()
            document.save(tmp)
            document.close()
            document = null

            if (tmp.length() == 0L) {
                tmp.delete()
                Log.e(TAG, "applyEdits produced an empty file")
                return@withContext false
            }
            val moved = tmp.renameTo(outputFile) || runCatching {
                tmp.copyTo(outputFile, overwrite = true); tmp.delete()
            }.isSuccess
            if (!moved) {
                tmp.delete()
                return@withContext false
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "applyEdits failed", e)
            false
        } finally {
            runCatching { document?.close() }
            if (tmp.exists()) tmp.delete()
        }
    }

    /**
     * Stamps a diagonal watermark across every page (free-tier output). Returns false on failure.
     */
    suspend fun addWatermark(
        context: Context,
        sourceUri: Uri,
        outputFile: File,
        text: String = "Made with PdfMaster",
    ): Boolean = withContext(Dispatchers.IO) {
        ensureInit(context)
        val tmp = File(outputFile.parentFile, "${outputFile.name}.tmp")
        var document: PDDocument? = null
        try {
            val bytes = context.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
                ?: return@withContext false
            document = PDDocument.load(bytes)

            for (pageIndex in 0 until document.numberOfPages) {
                val page = document.getPage(pageIndex)
                val w = page.mediaBox.width
                val h = page.mediaBox.height
                PDPageContentStream(
                    document, page, PDPageContentStream.AppendMode.APPEND, true, true,
                ).use { cs ->
                    val gs = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 0.18f }
                    cs.setGraphicsStateParameters(gs)
                    cs.setNonStrokingColor(120f / 255f, 120f / 255f, 120f / 255f)
                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA_BOLD, 42f)
                    // Rotate 45° about the page centre.
                    val rad = Math.toRadians(45.0)
                    val cos = Math.cos(rad).toFloat()
                    val sin = Math.sin(rad).toFloat()
                    cs.setTextMatrix(
                        com.tom_roush.pdfbox.util.Matrix(cos, sin, -sin, cos, w / 2f - 150f, h / 2f),
                    )
                    cs.showText(text)
                    cs.endText()
                }
            }

            tmp.parentFile?.mkdirs()
            document.save(tmp)
            document.close()
            document = null

            if (tmp.length() == 0L) {
                tmp.delete(); return@withContext false
            }
            val moved = tmp.renameTo(outputFile) || runCatching {
                tmp.copyTo(outputFile, overwrite = true); tmp.delete()
            }.isSuccess
            if (!moved) { tmp.delete(); return@withContext false }
            true
        } catch (e: Exception) {
            Log.e(TAG, "addWatermark failed", e)
            false
        } finally {
            runCatching { document?.close() }
            if (tmp.exists()) tmp.delete()
        }
    }

    private fun drawWhiteout(cs: PDPageContentStream, w: WhiteoutRect, pageHeight: Float) {
        cs.setNonStrokingColor(1f, 1f, 1f)
        cs.addRect(w.x, pageHeight - w.y - w.height, w.width, w.height)
        cs.fill()
    }

    private fun drawHighlight(cs: PDPageContentStream, hl: HighlightRect, pageHeight: Float) {
        cs.saveGraphicsState()
        val gs = PDExtendedGraphicsState().apply {
            nonStrokingAlphaConstant = hl.opacity.coerceIn(0.05f, 1f)
        }
        cs.setGraphicsStateParameters(gs)
        cs.setNonStrokingColor(
            AndroidColor.red(hl.color) / 255f,
            AndroidColor.green(hl.color) / 255f,
            AndroidColor.blue(hl.color) / 255f,
        )
        cs.addRect(hl.x, pageHeight - hl.y - hl.height, hl.width, hl.height)
        cs.fill()
        cs.restoreGraphicsState()
    }

    private fun drawText(cs: PDPageContentStream, t: TextEdit, pageHeight: Float) {
        val font = when {
            t.isBold && t.isItalic -> PDType1Font.HELVETICA_BOLD_OBLIQUE
            t.isBold -> PDType1Font.HELVETICA_BOLD
            t.isItalic -> PDType1Font.HELVETICA_OBLIQUE
            else -> PDType1Font.HELVETICA
        }
        cs.beginText()
        cs.setFont(font, t.fontSize)
        cs.setNonStrokingColor(
            AndroidColor.red(t.fontColor) / 255f,
            AndroidColor.green(t.fontColor) / 255f,
            AndroidColor.blue(t.fontColor) / 255f,
        )
        cs.newLineAtOffset(t.x, pageHeight - t.y - t.fontSize)
        // Standard-14 fonts only encode WinAnsi; strip anything they can't render.
        cs.showText(t.text.filter { it.code in 32..255 })
        cs.endText()
    }

    private fun drawImage(
        doc: PDDocument,
        cs: PDPageContentStream,
        img: ImageOverlay,
        pageHeight: Float,
    ) {
        // LosslessFactory handles ARGB_8888 with alpha (transparent signature) natively.
        val pdImage = LosslessFactory.createFromImage(doc, img.bitmap)
        cs.drawImage(pdImage, img.x, pageHeight - img.y - img.height, img.width, img.height)
    }

    /** Convenience: whiteout an area then write replacement text. */
    suspend fun replaceText(
        context: Context,
        sourceUri: Uri,
        outputFile: File,
        pageIndex: Int,
        originalX: Float,
        originalY: Float,
        originalWidth: Float,
        originalHeight: Float,
        newText: String,
        fontSize: Float = 12f,
        fontColor: Int = AndroidColor.BLACK,
    ): Boolean = applyEdits(
        context = context,
        sourceUri = sourceUri,
        outputFile = outputFile,
        whiteouts = listOf(WhiteoutRect(pageIndex, originalX, originalY, originalWidth, originalHeight)),
        textEdits = listOf(TextEdit(pageIndex, originalX, originalY, newText, fontSize, fontColor)),
    )

    suspend fun addText(
        context: Context,
        sourceUri: Uri,
        outputFile: File,
        pageIndex: Int,
        x: Float,
        y: Float,
        text: String,
        fontSize: Float = 12f,
        fontColor: Int = AndroidColor.BLACK,
    ): Boolean = applyEdits(
        context = context,
        sourceUri = sourceUri,
        outputFile = outputFile,
        textEdits = listOf(TextEdit(pageIndex, x, y, text, fontSize, fontColor)),
    )

    /** Page dimensions in PDF points (72pt = 1in), or null on failure. */
    suspend fun getPageDimensions(
        context: Context,
        uri: Uri,
        pageIndex: Int,
    ): Pair<Float, Float>? = withContext(Dispatchers.IO) {
        ensureInit(context)
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext null
            PDDocument.load(bytes).use { doc ->
                if (pageIndex !in 0 until doc.numberOfPages) return@withContext null
                val box = doc.getPage(pageIndex).mediaBox
                Pair(box.width, box.height)
            }
        } catch (e: Exception) {
            Log.e(TAG, "getPageDimensions failed", e)
            null
        }
    }

    suspend fun getPageCount(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        ensureInit(context)
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext 0
            PDDocument.load(bytes).use { it.numberOfPages }
        } catch (e: Exception) {
            0
        }
    }
}

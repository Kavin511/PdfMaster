package com.pdfmaster.app.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.lowagie.text.Document
import com.lowagie.text.Element
import com.lowagie.text.Font
import com.lowagie.text.Image
import com.lowagie.text.Rectangle
import com.lowagie.text.pdf.BaseFont
import com.lowagie.text.pdf.PdfContentByte
import com.lowagie.text.pdf.PdfReader
import com.lowagie.text.pdf.PdfStamper
import com.lowagie.text.pdf.PdfWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * OpenPDF-based PDF editor for real PDF manipulation
 * - Add/replace text at precise positions
 * - Whiteout (redact) existing content
 * - Add images and drawings
 */
object OpenPdfEditor {

    /**
     * Text edit operation
     */
    data class TextEdit(
        val pageIndex: Int,  // 0-based
        val x: Float,
        val y: Float,
        val text: String,
        val fontSize: Float = 12f,
        val fontColor: Int = android.graphics.Color.BLACK,
        val isBold: Boolean = false,
        val isItalic: Boolean = false
    )

    /**
     * Whiteout operation (cover existing content)
     */
    data class WhiteoutRect(
        val pageIndex: Int,  // 0-based
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float
    )

    /**
     * Image/drawing operation
     */
    data class ImageOverlay(
        val pageIndex: Int,  // 0-based
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val bitmap: Bitmap
    )

    /**
     * Highlight operation — a semi-transparent colored rectangle drawn over content.
     */
    data class HighlightRect(
        val pageIndex: Int,  // 0-based
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val color: Int = android.graphics.Color.YELLOW,
        val opacity: Float = 0.35f
    )

    /**
     * Apply edits to a PDF and save to output file
     * Uses OpenPDF's PdfStamper for precise content placement
     */
    suspend fun applyEdits(
        context: Context,
        sourceUri: Uri,
        outputFile: File,
        textEdits: List<TextEdit> = emptyList(),
        whiteouts: List<WhiteoutRect> = emptyList(),
        images: List<ImageOverlay> = emptyList(),
        highlights: List<HighlightRect> = emptyList()
    ): Boolean = withContext(Dispatchers.IO) {
        var reader: PdfReader? = null
        var stamper: PdfStamper? = null

        try {
            // Read source PDF
            val inputStream = context.contentResolver.openInputStream(sourceUri)
                ?: return@withContext false
            val pdfBytes = inputStream.readBytes()
            inputStream.close()

            reader = PdfReader(pdfBytes)
            stamper = PdfStamper(reader, FileOutputStream(outputFile))

            val totalPages = reader.numberOfPages

            // Process each page
            for (pageNum in 1..totalPages) {
                val pageIndex = pageNum - 1
                val pageSize = reader.getPageSize(pageNum)
                val pageHeight = pageSize.height

                // Get content layer (over = on top of existing content)
                val overContent = stamper.getOverContent(pageNum)
                // Get under content (under = below existing content, for whiteout)
                val underContent = stamper.getUnderContent(pageNum)

                // Apply whiteouts first (draw white rectangles over existing content)
                whiteouts.filter { it.pageIndex == pageIndex }.forEach { whiteout ->
                    applyWhiteout(overContent, whiteout, pageHeight)
                }

                // Apply highlights (semi-transparent colored rectangles over content)
                highlights.filter { it.pageIndex == pageIndex }.forEach { highlight ->
                    applyHighlight(overContent, highlight, pageHeight)
                }

                // Apply text edits
                textEdits.filter { it.pageIndex == pageIndex }.forEach { textEdit ->
                    applyTextEdit(overContent, textEdit, pageHeight)
                }

                // Apply image overlays
                images.filter { it.pageIndex == pageIndex }.forEach { imageOverlay ->
                    applyImageOverlay(overContent, imageOverlay, pageHeight)
                }
            }

            stamper.close()
            reader.close()
            true

        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            try {
                stamper?.close()
                reader?.close()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
    }

    /**
     * Stamps a diagonal watermark across every page (free-tier output).
     * Premium removes this by simply not calling it. Returns false on failure so the
     * caller can fall back to the un-watermarked source.
     */
    suspend fun addWatermark(
        context: Context,
        sourceUri: Uri,
        outputFile: File,
        text: String = "Made with PdfMaster",
    ): Boolean = withContext(Dispatchers.IO) {
        var reader: PdfReader? = null
        var stamper: PdfStamper? = null
        try {
            val pdfBytes = context.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
                ?: return@withContext false

            reader = PdfReader(pdfBytes)
            stamper = PdfStamper(reader, FileOutputStream(outputFile))

            val font = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED)
            for (pageNum in 1..reader.numberOfPages) {
                val pageSize = reader.getPageSize(pageNum)
                val over = stamper.getOverContent(pageNum)
                over.saveState()
                // Light grey, semi-transparent diagonal stamp through the page centre.
                val gs = com.lowagie.text.pdf.PdfGState().apply { setFillOpacity(0.18f) }
                over.setGState(gs)
                over.beginText()
                over.setFontAndSize(font, 42f)
                over.setRGBColorFill(120, 120, 120)
                over.showTextAligned(
                    Element.ALIGN_CENTER,
                    text,
                    pageSize.width / 2f,
                    pageSize.height / 2f,
                    45f,
                )
                over.endText()
                over.restoreState()
            }

            stamper.close()
            reader.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            try {
                stamper?.close()
                reader?.close()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Whiteout with background color for seamless blending
     */
    data class WhiteoutRectWithColor(
        val pageIndex: Int,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val backgroundColor: Int = android.graphics.Color.WHITE  // Sampled background color
    )

    /**
     * Apply whiteout with matched background color for seamless look
     */
    private fun applyWhiteout(content: PdfContentByte, whiteout: WhiteoutRect, pageHeight: Float) {
        content.saveState()

        // Use white as default - ideally this should be sampled from the PDF
        content.setRGBColorFill(255, 255, 255)
        content.setRGBColorStroke(255, 255, 255)

        // PDF coordinates are from bottom-left, need to flip Y
        val pdfY = pageHeight - whiteout.y - whiteout.height

        // Draw filled rectangle
        content.rectangle(whiteout.x, pdfY, whiteout.width, whiteout.height)
        content.fill()

        content.restoreState()
    }

    /**
     * Apply a semi-transparent highlight rectangle over existing content.
     */
    private fun applyHighlight(content: PdfContentByte, highlight: HighlightRect, pageHeight: Float) {
        content.saveState()

        val gs = com.lowagie.text.pdf.PdfGState().apply {
            setFillOpacity(highlight.opacity.coerceIn(0.05f, 1f))
        }
        content.setGState(gs)

        val r = android.graphics.Color.red(highlight.color)
        val g = android.graphics.Color.green(highlight.color)
        val b = android.graphics.Color.blue(highlight.color)
        content.setRGBColorFill(r, g, b)

        // PDF coordinates are from bottom-left, need to flip Y
        val pdfY = pageHeight - highlight.y - highlight.height
        content.rectangle(highlight.x, pdfY, highlight.width, highlight.height)
        content.fill()

        content.restoreState()
    }

    /**
     * Apply whiteout with specific background color
     */
    private fun applyWhiteoutWithColor(content: PdfContentByte, x: Float, y: Float, width: Float, height: Float, pageHeight: Float, bgColor: Int) {
        content.saveState()

        val r = android.graphics.Color.red(bgColor)
        val g = android.graphics.Color.green(bgColor)
        val b = android.graphics.Color.blue(bgColor)
        content.setRGBColorFill(r, g, b)

        // PDF coordinates are from bottom-left, need to flip Y
        val pdfY = pageHeight - y - height

        content.rectangle(x, pdfY, width, height)
        content.fill()

        content.restoreState()
    }

    /**
     * Apply text at specified position
     */
    private fun applyTextEdit(content: PdfContentByte, textEdit: TextEdit, pageHeight: Float) {
        content.saveState()

        try {
            // Create font based on style
            val fontName = when {
                textEdit.isBold && textEdit.isItalic -> BaseFont.HELVETICA_BOLDOBLIQUE
                textEdit.isBold -> BaseFont.HELVETICA_BOLD
                textEdit.isItalic -> BaseFont.HELVETICA_OBLIQUE
                else -> BaseFont.HELVETICA
            }

            val baseFont = BaseFont.createFont(fontName, BaseFont.CP1252, BaseFont.NOT_EMBEDDED)

            // Set text properties
            content.beginText()
            content.setFontAndSize(baseFont, textEdit.fontSize)

            // Convert Android color to RGB
            val r = android.graphics.Color.red(textEdit.fontColor)
            val g = android.graphics.Color.green(textEdit.fontColor)
            val b = android.graphics.Color.blue(textEdit.fontColor)
            content.setRGBColorFill(r, g, b)

            // PDF coordinates are from bottom-left, need to flip Y
            // Also adjust for baseline (text is drawn from baseline, not top)
            val pdfY = pageHeight - textEdit.y - textEdit.fontSize

            content.setTextMatrix(textEdit.x, pdfY)
            content.showText(textEdit.text)
            content.endText()

        } catch (e: Exception) {
            e.printStackTrace()
        }

        content.restoreState()
    }

    /**
     * Apply image overlay at specified position
     */
    private fun applyImageOverlay(content: PdfContentByte, imageOverlay: ImageOverlay, pageHeight: Float) {
        try {
            // Convert Bitmap to bytes
            val stream = ByteArrayOutputStream()
            imageOverlay.bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            val imageBytes = stream.toByteArray()

            val image = Image.getInstance(imageBytes)

            // Scale image to specified dimensions
            image.scaleAbsolute(imageOverlay.width, imageOverlay.height)

            // PDF coordinates are from bottom-left
            val pdfY = pageHeight - imageOverlay.y - imageOverlay.height
            image.setAbsolutePosition(imageOverlay.x, pdfY)

            content.addImage(image)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Replace text at a specific location
     * This whiteouts the area first, then adds new text
     */
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
        fontColor: Int = android.graphics.Color.BLACK
    ): Boolean {
        // Create whiteout for original text area
        val whiteout = WhiteoutRect(
            pageIndex = pageIndex,
            x = originalX,
            y = originalY,
            width = originalWidth,
            height = originalHeight
        )

        // Create text edit for new text
        val textEdit = TextEdit(
            pageIndex = pageIndex,
            x = originalX,
            y = originalY,
            text = newText,
            fontSize = fontSize,
            fontColor = fontColor
        )

        return applyEdits(
            context = context,
            sourceUri = sourceUri,
            outputFile = outputFile,
            textEdits = listOf(textEdit),
            whiteouts = listOf(whiteout)
        )
    }

    /**
     * Add text annotation to PDF
     */
    suspend fun addText(
        context: Context,
        sourceUri: Uri,
        outputFile: File,
        pageIndex: Int,
        x: Float,
        y: Float,
        text: String,
        fontSize: Float = 12f,
        fontColor: Int = android.graphics.Color.BLACK
    ): Boolean {
        val textEdit = TextEdit(
            pageIndex = pageIndex,
            x = x,
            y = y,
            text = text,
            fontSize = fontSize,
            fontColor = fontColor
        )

        return applyEdits(
            context = context,
            sourceUri = sourceUri,
            outputFile = outputFile,
            textEdits = listOf(textEdit)
        )
    }

    /**
     * Get PDF page dimensions (in PDF points, 72 points = 1 inch)
     */
    suspend fun getPageDimensions(
        context: Context,
        uri: Uri,
        pageIndex: Int
    ): Pair<Float, Float>? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext null
            val pdfBytes = inputStream.readBytes()
            inputStream.close()

            val reader = PdfReader(pdfBytes)
            if (pageIndex >= reader.numberOfPages) {
                reader.close()
                return@withContext null
            }

            val pageSize = reader.getPageSize(pageIndex + 1)  // 1-based
            val result = Pair(pageSize.width, pageSize.height)
            reader.close()
            result

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Get total page count
     */
    suspend fun getPageCount(
        context: Context,
        uri: Uri
    ): Int = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext 0
            val pdfBytes = inputStream.readBytes()
            inputStream.close()

            val reader = PdfReader(pdfBytes)
            val count = reader.numberOfPages
            reader.close()
            count

        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }
}

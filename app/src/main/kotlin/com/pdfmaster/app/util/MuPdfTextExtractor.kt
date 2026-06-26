package com.pdfmaster.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.Page
import com.artifex.mupdf.fitz.Rect
import com.artifex.mupdf.fitz.StructuredText
import com.artifex.mupdf.fitz.android.AndroidDrawDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * MuPDF-based text extractor for accurate text detection with precise positions
 * MuPDF provides much more accurate text extraction than PDFBox
 */
object MuPdfTextExtractor {

    /**
     * Extract text blocks with precise positions using MuPDF
     */
    suspend fun extractTextBlocks(
        context: Context,
        uri: Uri,
        pageIndex: Int
    ): List<ExtractedTextBlock> = withContext(Dispatchers.IO) {
        var document: Document? = null
        var tempFile: File? = null

        try {
            // Copy URI content to temp file (MuPDF needs file path)
            tempFile = copyUriToTempFile(context, uri)
            if (tempFile == null) return@withContext emptyList()

            document = Document.openDocument(tempFile.absolutePath)
            if (pageIndex >= document.countPages()) {
                return@withContext emptyList()
            }

            val page = document.loadPage(pageIndex)
            val textBlocks = extractTextFromPage(page, pageIndex)

            page.destroy()
            textBlocks

        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        } finally {
            document?.destroy()
            tempFile?.delete()
        }
    }

    /**
     * Extract text from a single page with position information
     */
    private fun extractTextFromPage(page: Page, pageIndex: Int): List<ExtractedTextBlock> {
        val textBlocks = mutableListOf<ExtractedTextBlock>()

        try {
            val structuredText = page.toStructuredText()

            // Walk through text blocks
            for (block in structuredText.blocks) {
                if (block.lines == null) continue

                for (line in block.lines) {
                    if (line.chars == null || line.chars.isEmpty()) continue

                    val text = StringBuilder()
                    var minX = Float.MAX_VALUE
                    var minY = Float.MAX_VALUE
                    var maxX = Float.MIN_VALUE
                    var maxY = Float.MIN_VALUE
                    var totalFontSize = 0f
                    var charCount = 0

                    for (char in line.chars) {
                        text.append(char.c.toChar())

                        val quad = char.quad
                        if (quad != null) {
                            // Get bounding box from quad
                            minX = minOf(minX, quad.ul_x, quad.ll_x)
                            minY = minOf(minY, quad.ul_y, quad.ur_y)
                            maxX = maxOf(maxX, quad.ur_x, quad.lr_x)
                            maxY = maxOf(maxY, quad.ll_y, quad.lr_y)
                        }

                        // Estimate font size from character height
                        if (char.quad != null) {
                            totalFontSize += kotlin.math.abs(char.quad.ll_y - char.quad.ul_y)
                            charCount++
                        }
                    }

                    val lineText = text.toString().trim()
                    if (lineText.isNotEmpty() && minX != Float.MAX_VALUE) {
                        val avgFontSize = if (charCount > 0) totalFontSize / charCount else 12f

                        textBlocks.add(
                            ExtractedTextBlock(
                                text = lineText,
                                x = minX,
                                y = minY,
                                width = maxX - minX,
                                height = maxY - minY,
                                fontSize = avgFontSize.coerceIn(8f, 72f),
                                pageIndex = pageIndex,
                                fontColor = android.graphics.Color.BLACK,
                                isBold = false,
                                isItalic = false
                            )
                        )
                    }
                }
            }

            structuredText.destroy()

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return textBlocks
    }

    /**
     * Render a page to bitmap using MuPDF (higher quality than PdfRenderer)
     */
    suspend fun renderPage(
        context: Context,
        uri: Uri,
        pageIndex: Int,
        targetWidth: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        var document: Document? = null
        var tempFile: File? = null

        try {
            tempFile = copyUriToTempFile(context, uri)
            if (tempFile == null) return@withContext null

            document = Document.openDocument(tempFile.absolutePath)
            if (pageIndex >= document.countPages()) {
                return@withContext null
            }

            val page = document.loadPage(pageIndex)
            val bounds = page.bounds

            // Calculate scale to fit target width
            val scale = targetWidth.toFloat() / bounds.x1
            val matrix = Matrix(scale, scale)

            val scaledWidth = (bounds.x1 * scale).toInt()
            val scaledHeight = (bounds.y1 * scale).toInt()

            val bitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
            val device = AndroidDrawDevice(bitmap, 0, 0, 0, 0, scaledWidth, scaledHeight)

            page.run(device, matrix, null)
            device.close()
            page.destroy()

            bitmap

        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            document?.destroy()
            tempFile?.delete()
        }
    }

    /**
     * Get page dimensions using MuPDF
     */
    suspend fun getPageDimensions(
        context: Context,
        uri: Uri,
        pageIndex: Int
    ): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        var document: Document? = null
        var tempFile: File? = null

        try {
            tempFile = copyUriToTempFile(context, uri)
            if (tempFile == null) return@withContext null

            document = Document.openDocument(tempFile.absolutePath)
            if (pageIndex >= document.countPages()) {
                return@withContext null
            }

            val page = document.loadPage(pageIndex)
            val bounds = page.bounds
            page.destroy()

            Pair(bounds.x1.toInt(), bounds.y1.toInt())

        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            document?.destroy()
            tempFile?.delete()
        }
    }

    /**
     * Get page count using MuPDF
     */
    suspend fun getPageCount(
        context: Context,
        uri: Uri
    ): Int = withContext(Dispatchers.IO) {
        var document: Document? = null
        var tempFile: File? = null

        try {
            tempFile = copyUriToTempFile(context, uri)
            if (tempFile == null) return@withContext 0

            document = Document.openDocument(tempFile.absolutePath)
            document.countPages()

        } catch (e: Exception) {
            e.printStackTrace()
            0
        } finally {
            document?.destroy()
            tempFile?.delete()
        }
    }

    /**
     * Search for text in a page and return matching positions
     */
    suspend fun searchText(
        context: Context,
        uri: Uri,
        pageIndex: Int,
        searchQuery: String
    ): List<RectF> = withContext(Dispatchers.IO) {
        var document: Document? = null
        var tempFile: File? = null

        try {
            tempFile = copyUriToTempFile(context, uri)
            if (tempFile == null) return@withContext emptyList()

            document = Document.openDocument(tempFile.absolutePath)
            if (pageIndex >= document.countPages()) {
                return@withContext emptyList()
            }

            val page = document.loadPage(pageIndex)
            val hits = page.search(searchQuery)
            page.destroy()

            hits?.flatMap { quads ->
                quads.map { quad ->
                    RectF(
                        minOf(quad.ul_x, quad.ll_x),
                        minOf(quad.ul_y, quad.ur_y),
                        maxOf(quad.ur_x, quad.lr_x),
                        maxOf(quad.ll_y, quad.lr_y)
                    )
                }
            } ?: emptyList()

        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        } finally {
            document?.destroy()
            tempFile?.delete()
        }
    }

    private fun copyUriToTempFile(context: Context, uri: Uri): File? {
        return try {
            val tempFile = File.createTempFile("mupdf_", ".pdf", context.cacheDir)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

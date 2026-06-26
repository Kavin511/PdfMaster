package com.pdfmaster.app.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * OCR utilities for extracting text from scanned/image-based PDFs
 * Uses ML Kit Text Recognition for accurate text detection
 */
object OcrUtils {

    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Perform OCR on a bitmap and return extracted text blocks with positions
     */
    suspend fun performOcr(bitmap: Bitmap): List<ExtractedTextBlock> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            val inputImage = InputImage.fromBitmap(bitmap, 0)

            textRecognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    val textBlocks = mutableListOf<ExtractedTextBlock>()

                    for (block in visionText.textBlocks) {
                        for (line in block.lines) {
                            val boundingBox = line.boundingBox ?: continue
                            val text = line.text

                            if (text.isNotBlank()) {
                                // Estimate font size based on line height
                                val fontSize = boundingBox.height().toFloat() * 0.8f

                                textBlocks.add(
                                    ExtractedTextBlock(
                                        text = text,
                                        x = boundingBox.left.toFloat(),
                                        y = boundingBox.top.toFloat(),
                                        width = boundingBox.width().toFloat(),
                                        height = boundingBox.height().toFloat(),
                                        fontSize = fontSize.coerceIn(8f, 72f),
                                        pageIndex = 0 // Will be set by caller
                                    )
                                )
                            }
                        }
                    }

                    continuation.resume(textBlocks)
                }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                    continuation.resume(emptyList())
                }
        }
    }

    /**
     * Extract text from a PDF page using OCR
     * First renders the page as bitmap, then runs OCR
     */
    suspend fun extractTextWithOcr(
        context: Context,
        uri: Uri,
        pageIndex: Int
    ): List<ExtractedTextBlock> = withContext(Dispatchers.IO) {
        try {
            // Render page at high resolution for better OCR accuracy
            val width = context.resources.displayMetrics.widthPixels * 2
            val bitmap = PdfUtils.renderPage(context, uri, pageIndex, width, 100)
                ?: return@withContext emptyList()

            val blocks = performOcr(bitmap)

            // Scale coordinates back to original PDF dimensions
            val originalDims = PdfUtils.getPageDimensions(context, uri, pageIndex)
            val scaleX = if (originalDims != null) originalDims.first.toFloat() / bitmap.width else 0.5f
            val scaleY = if (originalDims != null) originalDims.second.toFloat() / bitmap.height else 0.5f

            bitmap.recycle()

            // Adjust coordinates and page index
            blocks.map { block ->
                block.copy(
                    x = block.x * scaleX,
                    y = block.y * scaleY,
                    width = block.width * scaleX,
                    height = block.height * scaleY,
                    fontSize = block.fontSize * scaleY,
                    pageIndex = pageIndex
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Extract text with OCR fallback
     * First tries PDFBox text extraction, falls back to OCR if no text found
     */
    suspend fun extractTextWithOcrFallback(
        context: Context,
        uri: Uri,
        pageIndex: Int
    ): Pair<List<ExtractedTextBlock>, Boolean> = withContext(Dispatchers.IO) {
        // First try PDFBox extraction
        val pdfBoxBlocks = PdfUtils.extractTextBlocks(context, uri, pageIndex)

        if (pdfBoxBlocks.isNotEmpty()) {
            // Has extractable text, return it
            Pair(pdfBoxBlocks, false) // false = not from OCR
        } else {
            // No extractable text, try OCR
            val ocrBlocks = extractTextWithOcr(context, uri, pageIndex)
            Pair(ocrBlocks, true) // true = from OCR
        }
    }

    /**
     * Check if a PDF page needs OCR (no extractable text)
     */
    suspend fun pageNeedsOcr(
        context: Context,
        uri: Uri,
        pageIndex: Int
    ): Boolean = withContext(Dispatchers.IO) {
        val blocks = PdfUtils.extractTextBlocks(context, uri, pageIndex)
        blocks.isEmpty()
    }
}

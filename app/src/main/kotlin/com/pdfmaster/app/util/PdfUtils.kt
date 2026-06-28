package com.pdfmaster.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Represents an extracted text block from a PDF with position information
 */
data class ExtractedTextBlock(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val fontSize: Float,
    val fontColor: Int = Color.BLACK,
    val fontName: String? = null,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val pageIndex: Int,
    val isModified: Boolean = false,
    val isDeleted: Boolean = false
)

/**
 * Represents a modification to a text block
 */
data class TextModification(
    val blockId: String,
    val originalBlock: ExtractedTextBlock?,
    val newText: String?,  // null means deleted
    val newX: Float,
    val newY: Float
)

/**
 * Result of PDF validation
 */
sealed class PdfValidationResult {
    data class Valid(val pageCount: Int) : PdfValidationResult()
    data class Encrypted(val message: String = "This PDF is password protected") : PdfValidationResult()
    data class Corrupted(val message: String = "This PDF file is corrupted or invalid") : PdfValidationResult()
    data class TooLarge(val message: String = "This PDF is too large to process") : PdfValidationResult()
    data class Error(val message: String) : PdfValidationResult()
}

object PdfUtils {

    // Maximum file size for processing (100MB)
    private const val MAX_FILE_SIZE = 100 * 1024 * 1024L

    /**
     * Validate a PDF file and return its status
     */
    suspend fun validatePdf(context: Context, uri: Uri): PdfValidationResult = withContext(Dispatchers.IO) {
        try {
            // Check file size
            val fileSize = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
            if (fileSize > MAX_FILE_SIZE) {
                return@withContext PdfValidationResult.TooLarge("PDF is too large (${fileSize / 1024 / 1024}MB). Maximum is 100MB.")
            }

            // Try to open with PdfRenderer
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                try {
                    PdfRenderer(pfd).use { renderer ->
                        PdfValidationResult.Valid(renderer.pageCount)
                    }
                } catch (e: SecurityException) {
                    PdfValidationResult.Encrypted()
                }
            } ?: PdfValidationResult.Error("Cannot open file")
        } catch (e: SecurityException) {
            PdfValidationResult.Encrypted()
        } catch (e: Exception) {
            when {
                e.message?.contains("password", ignoreCase = true) == true -> PdfValidationResult.Encrypted()
                e.message?.contains("encrypt", ignoreCase = true) == true -> PdfValidationResult.Encrypted()
                else -> PdfValidationResult.Corrupted(e.message ?: "Unknown error")
            }
        }
    }

    suspend fun getPageCount(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    renderer.pageCount
                }
            } ?: 0
        } catch (e: Exception) {
            0
        }
    }

    suspend fun renderPage(
        context: Context,
        uri: Uri,
        pageNumber: Int,
        width: Int,
        quality: Int = 100
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (pageNumber >= renderer.pageCount) return@withContext null

                    renderer.openPage(pageNumber).use { page ->
                        val aspectRatio = page.height.toFloat() / page.width.toFloat()
                        val height = (width * aspectRatio).toInt()

                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)

                        page.render(
                            bitmap,
                            null,
                            null,
                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                        )

                        bitmap
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun renderThumbnail(
        context: Context,
        uri: Uri,
        pageNumber: Int = 0,
        width: Int = 200
    ): Bitmap? = renderPage(context, uri, pageNumber, width, 80)

    suspend fun imagesToPdf(
        context: Context,
        imageUris: List<Uri>,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        val document = PdfDocument()
        var pagesAdded = 0
        Log.d(TAG, "imagesToPdf: starting with ${imageUris.size} image(s) -> ${outputFile.absolutePath}")
        try {
            imageUris.forEachIndexed { index, uri ->
                ensureActive() // honor cancellation (e.g. user backs out mid-convert)

                // Each image is handled independently: a single unreadable/undecodable image
                // (e.g. a transient SAF read error, a revoked grant, or a corrupt file) is
                // logged and skipped rather than aborting the whole batch. Without this, one
                // bad URI threw out of the loop and failed every other (valid) image too.
                try {
                    // 1) Read just the dimensions — no full-resolution allocation yet.
                    val bounds = android.graphics.BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    val boundsStream = context.contentResolver.openInputStream(uri)
                    if (boundsStream == null) {
                        Log.w(TAG, "imagesToPdf: openInputStream returned null for $uri — skipping")
                        return@forEachIndexed
                    }
                    boundsStream.use {
                        android.graphics.BitmapFactory.decodeStream(it, null, bounds)
                    }
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                        Log.w(TAG, "imagesToPdf: undecodable bounds (${bounds.outWidth}x${bounds.outHeight}, " +
                            "mime=${bounds.outMimeType}) for $uri — skipping")
                        return@forEachIndexed
                    }

                    // 2) Downsample so the longest side <= MAX_IMAGE_DIMEN. A 12MP phone photo
                    //    would otherwise decode to ~48MB ARGB_8888 and OOM after a few pages.
                    val opts = android.graphics.BitmapFactory.Options().apply {
                        inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_IMAGE_DIMEN)
                    }
                    val bitmap = context.contentResolver.openInputStream(uri)?.use {
                        android.graphics.BitmapFactory.decodeStream(it, null, opts)
                    }
                    if (bitmap == null) {
                        Log.w(TAG, "imagesToPdf: decodeStream returned null for $uri " +
                            "(inSampleSize=${opts.inSampleSize}) — skipping")
                        return@forEachIndexed
                    }

                    try {
                        val pageInfo = PdfDocument.PageInfo.Builder(
                            bitmap.width,
                            bitmap.height,
                            index + 1
                        ).create()
                        val page = document.startPage(pageInfo)
                        page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                        document.finishPage(page)
                        pagesAdded++
                        Log.d(TAG, "imagesToPdf: added page ${index + 1} (${bitmap.width}x${bitmap.height})")
                    } finally {
                        bitmap.recycle() // recycle even if finishPage throws
                    }
                } catch (ce: CancellationException) {
                    throw ce // never swallow coroutine cancellation
                } catch (e: Exception) {
                    Log.w(TAG, "imagesToPdf: failed to process image #${index + 1} ($uri) — skipping", e)
                }
            }

            // Don't write an empty PDF if every image failed to decode.
            if (pagesAdded == 0) {
                Log.e(TAG, "imagesToPdf: no pages added (all ${imageUris.size} image(s) failed to decode)")
                return@withContext false
            }

            FileOutputStream(outputFile).use { outputStream ->
                document.writeTo(outputStream)
            }
            Log.d(TAG, "imagesToPdf: wrote $pagesAdded page(s), ${outputFile.length()} bytes")
            true
        } catch (e: Exception) {
            Log.e(TAG, "imagesToPdf failed (pagesAdded=$pagesAdded, output=${outputFile.absolutePath})", e)
            false
        } finally {
            document.close() // always close, including on the exception path
        }
    }

    /** Largest allowed bitmap edge for image->PDF pages (~A4 at 300dpi). */
    private const val MAX_IMAGE_DIMEN = 2500

    /** Smallest power-of-two sample size that keeps both dimensions within [maxDimen]. */
    private fun calculateInSampleSize(width: Int, height: Int, maxDimen: Int): Int {
        var inSample = 1
        while (width / inSample > maxDimen || height / inSample > maxDimen) {
            inSample *= 2
        }
        return inSample
    }

    suspend fun getPageDimensions(
        context: Context,
        uri: Uri,
        pageNumber: Int
    ): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (pageNumber >= renderer.pageCount) return@withContext null

                    renderer.openPage(pageNumber).use { page ->
                        Pair(page.width, page.height)
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun isValidPdf(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    renderer.pageCount > 0
                }
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Extract specific pages from a PDF to create a new document
     */
    suspend fun extractPages(
        context: Context,
        sourceUri: Uri,
        pageIndices: List<Int>,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        // Copy the selected source pages (in the given order; duplicates allowed) into a new
        // document via PDFBox page-tree import. This preserves vector text/search instead of
        // rasterizing each page to a bitmap. Source stays open until the target is saved.
        PDFBoxResourceLoader.init(context.applicationContext)
        var src: PDDocument? = null
        var target: PDDocument? = null
        try {
            val bytes = context.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
                ?: return@withContext false
            src = PDDocument.load(bytes)
            val pageCount = src.numberOfPages
            target = PDDocument()
            pageIndices.forEach { index ->
                if (index in 0 until pageCount) target!!.importPage(src!!.getPage(index))
            }
            if (target.numberOfPages == 0) return@withContext false
            saveAtomic(target, outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "extractPages failed", e)
            false
        } finally {
            runCatching { target?.close() }
            runCatching { src?.close() }
        }
    }

    /**
     * Merge multiple PDFs into a single document
     */
    suspend fun mergePdfs(
        context: Context,
        sourceUris: List<Uri>,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        // Concatenate sources by importing their page trees into one target document (preserves
        // vector text). Unreadable/encrypted sources are skipped rather than silently producing a
        // blank page. All sources stay open until the target is saved.
        PDFBoxResourceLoader.init(context.applicationContext)
        val sources = mutableListOf<PDDocument>()
        var target: PDDocument? = null
        try {
            target = PDDocument()
            sourceUris.forEach { uri ->
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@forEach
                val src = runCatching { PDDocument.load(bytes) }.getOrNull() ?: run {
                    Log.w(TAG, "mergePdfs: skipping unreadable source $uri")
                    return@forEach
                }
                sources += src
                for (i in 0 until src.numberOfPages) target!!.importPage(src.getPage(i))
            }
            if (target.numberOfPages == 0) return@withContext false
            saveAtomic(target, outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "mergePdfs failed", e)
            false
        } finally {
            runCatching { target?.close() }
            sources.forEach { runCatching { it.close() } }
        }
    }

    /**
     * Compress a PDF by optimizing images and removing unnecessary data
     * Uses PDFBox for proper PDF compression
     */
    suspend fun compressPdf(
        context: Context,
        sourceUri: Uri,
        outputFile: File,
        quality: CompressionQuality
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            PDFBoxResourceLoader.init(context.applicationContext)

            // Copy source to temp file
            val tempFile = File(context.cacheDir, "temp_compress_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            val document = PDDocument.load(tempFile)

            // Image quality based on compression level
            val imageQuality = when (quality) {
                CompressionQuality.LOW -> 0.3f
                CompressionQuality.MEDIUM -> 0.5f
                CompressionQuality.HIGH -> 0.7f
            }

            val maxImageDimension = when (quality) {
                CompressionQuality.LOW -> 800
                CompressionQuality.MEDIUM -> 1200
                CompressionQuality.HIGH -> 1600
            }

            // Process each page to compress images
            for (page in document.pages) {
                val resources = page.resources
                val xObjectNames = resources.xObjectNames?.toList() ?: continue

                for (name in xObjectNames) {
                    try {
                        val xObject = resources.getXObject(name)
                        if (xObject is com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject) {
                            val image = xObject.image
                            if (image != null && (image.width > maxImageDimension || image.height > maxImageDimension)) {
                                // Scale down large images
                                val scale = minOf(
                                    maxImageDimension.toFloat() / image.width,
                                    maxImageDimension.toFloat() / image.height
                                )
                                val newWidth = (image.width * scale).toInt()
                                val newHeight = (image.height * scale).toInt()

                                val scaledBitmap = Bitmap.createScaledBitmap(image, newWidth, newHeight, true)

                                // Compress to JPEG
                                val baos = java.io.ByteArrayOutputStream()
                                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, (imageQuality * 100).toInt(), baos)

                                // Create new image XObject
                                val newImage = com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory.createFromByteArray(
                                    document,
                                    baos.toByteArray()
                                )

                                // Replace the image
                                resources.put(name, newImage)

                                scaledBitmap.recycle()
                                baos.close()
                            }
                        }
                    } catch (e: Exception) {
                        // Skip problematic images
                        continue
                    }
                }
            }

            // Remove metadata to reduce size
            document.documentInformation.author = null
            document.documentInformation.creator = null
            document.documentInformation.producer = null
            document.documentInformation.subject = null
            document.documentInformation.keywords = null

            // Save with compression
            document.save(outputFile)
            document.close()

            tempFile.delete()

            // Verify the output is smaller, otherwise try alternative approach
            val originalSize = context.contentResolver.openInputStream(sourceUri)?.use { it.available().toLong() } ?: 0L
            val compressedSize = outputFile.length()

            if (compressedSize >= originalSize) {
                // Fallback: just copy and strip metadata
                return@withContext compressPdfFallback(context, sourceUri, outputFile)
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            // Try fallback method
            compressPdfFallback(context, sourceUri, outputFile)
        }
    }

    /**
     * Fallback compression - strips unnecessary data without re-encoding
     */
    private suspend fun compressPdfFallback(
        context: Context,
        sourceUri: Uri,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            PDFBoxResourceLoader.init(context.applicationContext)

            val tempFile = File(context.cacheDir, "temp_compress_fb_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            val document = PDDocument.load(tempFile)

            // Remove all metadata
            document.documentInformation.author = null
            document.documentInformation.creator = null
            document.documentInformation.producer = null
            document.documentInformation.subject = null
            document.documentInformation.keywords = null
            document.documentInformation.title = null

            // Remove document catalog metadata
            document.documentCatalog.metadata = null

            // Save
            document.save(outputFile)
            document.close()
            tempFile.delete()

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Reorder pages in a PDF
     */
    suspend fun reorderPages(
        context: Context,
        sourceUri: Uri,
        newOrder: List<Int>,
        outputFile: File
    ): Boolean = extractPages(context, sourceUri, newOrder, outputFile)

    /**
     * Delete pages from a PDF
     */
    suspend fun deletePages(
        context: Context,
        sourceUri: Uri,
        pagesToDelete: Set<Int>,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val pageCount = getPageCount(context, sourceUri)
            val pagesToKeep = (0 until pageCount).filter { it !in pagesToDelete }
            extractPages(context, sourceUri, pagesToKeep, outputFile)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Rotate specific pages in a PDF
     */
    suspend fun rotatePages(
        context: Context,
        sourceUri: Uri,
        pageRotations: Map<Int, Float>,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        // Set the /Rotate entry on the affected pages (additive on top of any existing rotation)
        // rather than rasterizing. Rotation is normalized to [0,360). Vector content untouched.
        PDFBoxResourceLoader.init(context.applicationContext)
        var doc: PDDocument? = null
        try {
            val bytes = context.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
                ?: return@withContext false
            doc = PDDocument.load(bytes)
            val pageCount = doc.numberOfPages
            pageRotations.forEach { (index, degrees) ->
                if (index in 0 until pageCount && degrees != 0f) {
                    val page = doc!!.getPage(index)
                    page.rotation = (((page.rotation + degrees.toInt()) % 360) + 360) % 360
                }
            }
            saveAtomic(doc, outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "rotatePages failed", e)
            false
        } finally {
            runCatching { doc?.close() }
        }
    }

    /**
     * Assemble an output PDF from a page plan in a single PDFBox pass — supporting reorder,
     * delete, duplicate, blank-page insertion, and per-output-page rotation together.
     *
     * @param pagePlan one entry per output page: a source page index to import, or -1 for a new
     *   blank page (sized to the source's first page). Out-of-range indices are skipped.
     * @param rotations rotation (degrees, additive, normalized) keyed by OUTPUT position
     *   (index into [pagePlan]).
     */
    suspend fun buildDocument(
        context: Context,
        sourceUri: Uri,
        pagePlan: List<Int>,
        rotations: Map<Int, Float>,
        outputFile: File,
    ): Boolean = withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context.applicationContext)
        var src: PDDocument? = null
        var target: PDDocument? = null
        try {
            val bytes = context.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
                ?: return@withContext false
            src = PDDocument.load(bytes)
            val pageCount = src.numberOfPages
            if (pageCount == 0) return@withContext false
            target = PDDocument()
            val blankSize = src.getPage(0).mediaBox.let { PDRectangle(it.width, it.height) }

            pagePlan.forEachIndexed { outPos, srcIndex ->
                val page: PDPage? = when {
                    srcIndex == -1 -> PDPage(blankSize).also { target!!.addPage(it) }
                    srcIndex in 0 until pageCount -> target!!.importPage(src!!.getPage(srcIndex))
                    else -> null
                }
                val deg = rotations[outPos]
                if (page != null && deg != null && deg != 0f) {
                    page.rotation = (((page.rotation + deg.toInt()) % 360) + 360) % 360
                }
            }
            if (target.numberOfPages == 0) return@withContext false
            saveAtomic(target, outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "buildDocument failed", e)
            false
        } finally {
            runCatching { target?.close() }
            runCatching { src?.close() }
        }
    }

    /**
     * Save [doc] to [outputFile] atomically: write to a sibling temp file and only move it into
     * place after a fully-successful save, so a failure can never leave a 0-byte/partial output.
     */
    private fun saveAtomic(doc: PDDocument, outputFile: File): Boolean {
        val tmp = File(outputFile.parentFile, "${outputFile.name}.tmp")
        return try {
            tmp.parentFile?.mkdirs()
            doc.save(tmp)
            if (tmp.length() == 0L) {
                tmp.delete()
                false
            } else {
                val moved = tmp.renameTo(outputFile) || runCatching {
                    tmp.copyTo(outputFile, overwrite = true); tmp.delete()
                }.isSuccess
                if (!moved) tmp.delete()
                moved
            }
        } catch (e: Exception) {
            Log.e(TAG, "saveAtomic failed", e)
            if (tmp.exists()) tmp.delete()
            false
        }
    }

    private const val TAG = "PdfUtils"

    private fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
        val matrix = android.graphics.Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /**
     * Check if PDF is password protected
     */
    fun isPasswordProtected(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { _ ->
                    false
                }
            } ?: false
        } catch (e: SecurityException) {
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Add an overlay image (like a signature) to a specific page
     */
    suspend fun addOverlayToPage(
        context: Context,
        sourceUri: Uri,
        overlayBitmap: Bitmap,
        pageIndex: Int,
        xPosition: Float,
        yPosition: Float,
        overlayWidth: Int,
        overlayHeight: Int,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val pageCount = getPageCount(context, sourceUri)
            val document = PdfDocument()
            val width = context.resources.displayMetrics.widthPixels

            for (i in 0 until pageCount) {
                val pageBitmap = renderPage(context, sourceUri, i, width, 100) ?: continue

                val pageInfo = PdfDocument.PageInfo.Builder(
                    pageBitmap.width,
                    pageBitmap.height,
                    i + 1
                ).create()

                val page = document.startPage(pageInfo)
                val canvas = page.canvas

                // Draw the original page
                canvas.drawBitmap(pageBitmap, 0f, 0f, null)

                // If this is the target page, draw the overlay
                if (i == pageIndex) {
                    val scaledOverlay = Bitmap.createScaledBitmap(
                        overlayBitmap,
                        overlayWidth,
                        overlayHeight,
                        true
                    )
                    canvas.drawBitmap(scaledOverlay, xPosition, yPosition, null)
                    scaledOverlay.recycle()
                }

                document.finishPage(page)
                pageBitmap.recycle()
            }

            FileOutputStream(outputFile).use { out ->
                document.writeTo(out)
            }
            document.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Add text annotation to a PDF page
     */
    suspend fun addTextAnnotation(
        context: Context,
        sourceUri: Uri,
        pageIndex: Int,
        text: String,
        xPosition: Float,
        yPosition: Float,
        textSize: Float,
        textColor: Int,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val pageCount = getPageCount(context, sourceUri)
            val document = PdfDocument()
            val width = context.resources.displayMetrics.widthPixels

            val paint = android.graphics.Paint().apply {
                color = textColor
                this.textSize = textSize
                isAntiAlias = true
            }

            for (i in 0 until pageCount) {
                val pageBitmap = renderPage(context, sourceUri, i, width, 100) ?: continue

                val pageInfo = PdfDocument.PageInfo.Builder(
                    pageBitmap.width,
                    pageBitmap.height,
                    i + 1
                ).create()

                val page = document.startPage(pageInfo)
                val canvas = page.canvas

                canvas.drawBitmap(pageBitmap, 0f, 0f, null)

                if (i == pageIndex) {
                    canvas.drawText(text, xPosition, yPosition, paint)
                }

                document.finishPage(page)
                pageBitmap.recycle()
            }

            FileOutputStream(outputFile).use { out ->
                document.writeTo(out)
            }
            document.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Add highlight annotation to PDF page
     */
    suspend fun addHighlightAnnotation(
        context: Context,
        sourceUri: Uri,
        pageIndex: Int,
        highlightRect: android.graphics.RectF,
        highlightColor: Int,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val pageCount = getPageCount(context, sourceUri)
            val document = PdfDocument()
            val width = context.resources.displayMetrics.widthPixels

            val paint = android.graphics.Paint().apply {
                color = highlightColor
                alpha = 100
                style = android.graphics.Paint.Style.FILL
            }

            for (i in 0 until pageCount) {
                val pageBitmap = renderPage(context, sourceUri, i, width, 100) ?: continue

                val pageInfo = PdfDocument.PageInfo.Builder(
                    pageBitmap.width,
                    pageBitmap.height,
                    i + 1
                ).create()

                val page = document.startPage(pageInfo)
                val canvas = page.canvas

                canvas.drawBitmap(pageBitmap, 0f, 0f, null)

                if (i == pageIndex) {
                    canvas.drawRect(highlightRect, paint)
                }

                document.finishPage(page)
                pageBitmap.recycle()
            }

            FileOutputStream(outputFile).use { out ->
                document.writeTo(out)
            }
            document.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    enum class CompressionQuality {
        LOW, MEDIUM, HIGH
    }

    /**
     * Initialize PDFBox resources - call this once at app startup
     */
    fun initPdfBox(context: Context) {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    /**
     * Encrypt a PDF with a password using PDFBox
     * @param ownerPassword The owner password (for full access)
     * @param userPassword The user password (for opening the document)
     */
    suspend fun encryptPdf(
        context: Context,
        sourceUri: Uri,
        outputFile: File,
        ownerPassword: String,
        userPassword: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Ensure PDFBox is initialized
            PDFBoxResourceLoader.init(context.applicationContext)

            // Copy source to temp file for PDFBox to read
            val tempFile = File(context.cacheDir, "temp_encrypt_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Load the document with PDFBox
            val document = PDDocument.load(tempFile)

            // Set up encryption
            val accessPermission = AccessPermission().apply {
                // Allow printing
                setCanPrint(true)
                // Allow copying content
                setCanExtractContent(true)
                // Allow modifications
                setCanModify(true)
                // Allow annotations
                setCanModifyAnnotations(true)
                // Allow form filling
                setCanFillInForm(true)
            }

            // Create protection policy with 128-bit AES encryption
            val protectionPolicy = StandardProtectionPolicy(
                ownerPassword,
                userPassword,
                accessPermission
            ).apply {
                encryptionKeyLength = 128
            }

            // Apply encryption
            document.protect(protectionPolicy)

            // Save the encrypted document
            document.save(outputFile)
            document.close()

            // Clean up temp file
            tempFile.delete()

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Decrypt/unlock a password-protected PDF
     * Returns the path to the decrypted file if successful, null otherwise
     */
    suspend fun decryptPdf(
        context: Context,
        sourceUri: Uri,
        password: String,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Ensure PDFBox is initialized
            PDFBoxResourceLoader.init(context.applicationContext)

            // Copy source to temp file for PDFBox to read
            val tempFile = File(context.cacheDir, "temp_decrypt_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Try to load with the provided password
            val document = PDDocument.load(tempFile, password)

            // Check if document was actually encrypted and is now decrypted
            if (document.isEncrypted) {
                // Remove encryption by setting it to null
                document.setAllSecurityToBeRemoved(true)
            }

            // Save the decrypted document
            document.save(outputFile)
            document.close()

            // Clean up temp file
            tempFile.delete()

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Check if a PDF is password protected using PDFBox
     */
    suspend fun checkPasswordProtection(
        context: Context,
        uri: Uri
    ): PasswordProtectionStatus = withContext(Dispatchers.IO) {
        try {
            // Ensure PDFBox is initialized
            PDFBoxResourceLoader.init(context.applicationContext)

            // Copy source to temp file for PDFBox to read
            val tempFile = File(context.cacheDir, "temp_check_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            try {
                // Try to load without password
                val document = PDDocument.load(tempFile)
                val isEncrypted = document.isEncrypted
                document.close()
                tempFile.delete()

                if (isEncrypted) {
                    PasswordProtectionStatus.PROTECTED
                } else {
                    PasswordProtectionStatus.NOT_PROTECTED
                }
            } catch (e: Exception) {
                tempFile.delete()
                // If we get an exception, it's likely password protected
                PasswordProtectionStatus.PROTECTED
            }
        } catch (e: Exception) {
            PasswordProtectionStatus.ERROR
        }
    }

    /**
     * Verify if a password is correct for a protected PDF
     */
    suspend fun verifyPassword(
        context: Context,
        uri: Uri,
        password: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Ensure PDFBox is initialized
            PDFBoxResourceLoader.init(context.applicationContext)

            // Copy source to temp file for PDFBox to read
            val tempFile = File(context.cacheDir, "temp_verify_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            try {
                val document = PDDocument.load(tempFile, password)
                document.close()
                tempFile.delete()
                true
            } catch (e: Exception) {
                tempFile.delete()
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    enum class PasswordProtectionStatus {
        NOT_PROTECTED,
        PROTECTED,
        ERROR
    }

    // ============================================================
    // Text Extraction for Figma-like Editing
    // ============================================================

    /**
     * Extract text blocks from a PDF page with position information
     * Uses PDFBox's PDFTextStripper with custom text position tracking
     */
    suspend fun extractTextBlocks(
        context: Context,
        uri: Uri,
        pageIndex: Int
    ): List<ExtractedTextBlock> = withContext(Dispatchers.IO) {
        try {
            PDFBoxResourceLoader.init(context.applicationContext)

            val tempFile = File(context.cacheDir, "temp_extract_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            val textBlocks = mutableListOf<ExtractedTextBlock>()
            val document = PDDocument.load(tempFile)

            try {
                val pageCount = document.numberOfPages
                if (pageIndex >= pageCount) {
                    document.close()
                    tempFile.delete()
                    return@withContext emptyList()
                }

                val page = document.getPage(pageIndex)
                val pageHeight = page.mediaBox.height

                // Custom text stripper to capture text positions
                val stripper = object : PDFTextStripper() {
                    private val currentLineText = StringBuilder()
                    private var lineStartX = 0f
                    private var lineStartY = 0f
                    private var lineEndX = 0f
                    private var lineFontSize = 12f
                    private var lineHeight = 0f
                    private var lineFontName: String? = null
                    private var lineIsBold = false
                    private var lineIsItalic = false

                    init {
                        setSortByPosition(true)
                        startPage = pageIndex + 1
                        endPage = pageIndex + 1
                    }

                    override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
                        if (textPositions.isEmpty()) return

                        for (textPos in textPositions) {
                            val char = textPos.unicode
                            if (char.isNullOrEmpty()) continue

                            // Convert PDF coordinates (origin bottom-left) to screen coordinates (origin top-left)
                            val x = textPos.xDirAdj
                            val y = pageHeight - textPos.yDirAdj

                            if (currentLineText.isEmpty()) {
                                // Start new line
                                lineStartX = x
                                lineStartY = y
                                lineFontSize = textPos.fontSize
                                lineHeight = textPos.height

                                // Extract font info
                                try {
                                    val font = textPos.font
                                    lineFontName = font?.name
                                    val fontNameLower = lineFontName?.lowercase() ?: ""
                                    lineIsBold = fontNameLower.contains("bold")
                                    lineIsItalic = fontNameLower.contains("italic") || fontNameLower.contains("oblique")
                                } catch (e: Exception) {
                                    // Ignore font extraction errors
                                }
                            }

                            currentLineText.append(char)
                            lineEndX = x + textPos.width

                            // Update line height if this character is taller
                            if (textPos.height > lineHeight) {
                                lineHeight = textPos.height
                            }
                        }
                    }

                    override fun writeLineSeparator() {
                        flushLine()
                        super.writeLineSeparator()
                    }

                    override fun endDocument(document: PDDocument) {
                        flushLine()
                        super.endDocument(document)
                    }

                    private fun flushLine() {
                        val text = currentLineText.toString().trim()
                        if (text.isNotEmpty()) {
                            val width = (lineEndX - lineStartX).coerceAtLeast(10f)
                            val height = lineHeight.coerceAtLeast(lineFontSize)

                            textBlocks.add(
                                ExtractedTextBlock(
                                    id = UUID.randomUUID().toString(),
                                    text = text,
                                    x = lineStartX,
                                    y = lineStartY - height, // Adjust y to top of text
                                    width = width,
                                    height = height,
                                    fontSize = lineFontSize,
                                    fontName = lineFontName,
                                    isBold = lineIsBold,
                                    isItalic = lineIsItalic,
                                    pageIndex = pageIndex
                                )
                            )
                        }
                        currentLineText.clear()
                        lineFontName = null
                        lineIsBold = false
                        lineIsItalic = false
                    }
                }

                stripper.getText(document)

                document.close()
                tempFile.delete()

                // Merge nearby text blocks that are on the same line
                mergeAdjacentBlocks(textBlocks)
            } catch (e: Exception) {
                document.close()
                tempFile.delete()
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Merge text blocks that are adjacent and on the same line
     */
    private fun mergeAdjacentBlocks(blocks: MutableList<ExtractedTextBlock>): List<ExtractedTextBlock> {
        if (blocks.size <= 1) return blocks

        val merged = mutableListOf<ExtractedTextBlock>()
        val sorted = blocks.sortedWith(compareBy({ it.y }, { it.x }))

        var current: ExtractedTextBlock? = null

        for (block in sorted) {
            if (current == null) {
                current = block
            } else {
                // Check if blocks are on same line (within tolerance) and close horizontally
                val sameLine = kotlin.math.abs(current.y - block.y) < current.height * 0.5f
                val closeHorizontally = block.x - (current.x + current.width) < current.fontSize * 2

                if (sameLine && closeHorizontally) {
                    // Merge blocks
                    current = current.copy(
                        text = current.text + " " + block.text,
                        width = (block.x + block.width) - current.x,
                        height = maxOf(current.height, block.height),
                        fontSize = maxOf(current.fontSize, block.fontSize)
                    )
                } else {
                    merged.add(current)
                    current = block
                }
            }
        }

        current?.let { merged.add(it) }
        return merged
    }

    /**
     * Check if a PDF is scanned (image-based with no extractable text)
     * Returns true if the PDF appears to be scanned and needs OCR
     */
    suspend fun isScannedPdf(
        context: Context,
        uri: Uri
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            PDFBoxResourceLoader.init(context.applicationContext)

            val tempFile = File(context.cacheDir, "temp_check_scan_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            val document = PDDocument.load(tempFile)

            try {
                // Try to extract text from first few pages
                val stripper = PDFTextStripper().apply {
                    startPage = 1
                    endPage = minOf(3, document.numberOfPages)
                }

                val text = stripper.getText(document)
                document.close()
                tempFile.delete()

                // If we got very little text, it's likely a scanned PDF
                val meaningfulText = text.replace("\\s+".toRegex(), "").length
                meaningfulText < 50 // Less than 50 characters = likely scanned
            } catch (e: Exception) {
                document.close()
                tempFile.delete()
                true // Assume scanned if extraction fails
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Create a modified PDF with text changes
     * This renders each page as a bitmap, applies modifications, and saves as new PDF
     */
    suspend fun createModifiedPdf(
        context: Context,
        sourceUri: Uri,
        modifications: Map<Int, List<ExtractedTextBlock>>, // pageIndex -> modified blocks
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val pageCount = getPageCount(context, sourceUri)
            val document = PdfDocument()
            val width = context.resources.displayMetrics.widthPixels

            for (i in 0 until pageCount) {
                val pageBitmap = renderPage(context, sourceUri, i, width, 100) ?: continue

                // Calculate scale factors for this page
                val dims = getPageDimensions(context, sourceUri, i)
                val scaleX = if (dims != null) pageBitmap.width.toFloat() / dims.first else 1f
                val scaleY = if (dims != null) pageBitmap.height.toFloat() / dims.second else 1f

                // Create a mutable copy to draw on
                val mutableBitmap = pageBitmap.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(mutableBitmap)

                // Get modifications for this page
                val pageModifications = modifications[i] ?: emptyList()

                for (block in pageModifications) {
                    if (block.isDeleted) {
                        // White out the deleted block
                        val paint = Paint().apply {
                            color = Color.WHITE
                            style = Paint.Style.FILL
                        }
                        val rect = RectF(
                            block.x * scaleX,
                            block.y * scaleY,
                            (block.x + block.width) * scaleX,
                            (block.y + block.height) * scaleY
                        )
                        canvas.drawRect(rect, paint)
                    } else if (block.isModified || block.text.isNotEmpty()) {
                        // White out original area first
                        val whitePaint = Paint().apply {
                            color = Color.WHITE
                            style = Paint.Style.FILL
                        }
                        val rect = RectF(
                            block.x * scaleX,
                            block.y * scaleY,
                            (block.x + block.width) * scaleX,
                            (block.y + block.height) * scaleY
                        )
                        canvas.drawRect(rect, whitePaint)

                        // Draw new text
                        val textPaint = Paint().apply {
                            color = Color.BLACK
                            textSize = block.fontSize * scaleY
                            isAntiAlias = true
                        }

                        // Draw text at the block position
                        // Text is drawn from baseline, so we need to offset
                        val textY = (block.y + block.height) * scaleY - (block.fontSize * 0.2f * scaleY)
                        canvas.drawText(
                            block.text,
                            block.x * scaleX,
                            textY,
                            textPaint
                        )
                    }
                }

                // Create PDF page from modified bitmap
                val pageInfo = PdfDocument.PageInfo.Builder(
                    mutableBitmap.width,
                    mutableBitmap.height,
                    i + 1
                ).create()

                val page = document.startPage(pageInfo)
                page.canvas.drawBitmap(mutableBitmap, 0f, 0f, null)
                document.finishPage(page)

                pageBitmap.recycle()
                mutableBitmap.recycle()
            }

            FileOutputStream(outputFile).use { out ->
                document.writeTo(out)
            }
            document.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

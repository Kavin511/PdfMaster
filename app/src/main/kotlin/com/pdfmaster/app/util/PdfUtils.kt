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
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
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
        try {
            val document = PdfDocument()

            imageUris.forEachIndexed { index, uri ->
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                    if (bitmap != null) {
                        val pageInfo = PdfDocument.PageInfo.Builder(
                            bitmap.width,
                            bitmap.height,
                            index + 1
                        ).create()

                        val page = document.startPage(pageInfo)
                        val canvas = page.canvas
                        canvas.drawBitmap(bitmap, 0f, 0f, null)
                        document.finishPage(page)

                        bitmap.recycle()
                    }
                }
            }

            FileOutputStream(outputFile).use { outputStream ->
                document.writeTo(outputStream)
            }

            document.close()
            true
        } catch (e: Exception) {
            false
        }
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
        try {
            val width = context.resources.displayMetrics.widthPixels
            val document = PdfDocument()

            pageIndices.forEachIndexed { newIndex, originalIndex ->
                val bitmap = renderPage(context, sourceUri, originalIndex, width, 100)
                if (bitmap != null) {
                    val pageInfo = PdfDocument.PageInfo.Builder(
                        bitmap.width,
                        bitmap.height,
                        newIndex + 1
                    ).create()

                    val page = document.startPage(pageInfo)
                    page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    document.finishPage(page)
                    bitmap.recycle()
                }
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
     * Merge multiple PDFs into a single document
     */
    suspend fun mergePdfs(
        context: Context,
        sourceUris: List<Uri>,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val document = PdfDocument()
            var pageNumber = 1
            val width = context.resources.displayMetrics.widthPixels

            sourceUris.forEach { uri ->
                val pageCount = getPageCount(context, uri)
                for (i in 0 until pageCount) {
                    val bitmap = renderPage(context, uri, i, width, 100)
                    if (bitmap != null) {
                        val pageInfo = PdfDocument.PageInfo.Builder(
                            bitmap.width,
                            bitmap.height,
                            pageNumber++
                        ).create()

                        val page = document.startPage(pageInfo)
                        page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                        document.finishPage(page)
                        bitmap.recycle()
                    }
                }
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
        try {
            val pageCount = getPageCount(context, sourceUri)
            val document = PdfDocument()
            val width = context.resources.displayMetrics.widthPixels

            for (i in 0 until pageCount) {
                var bitmap = renderPage(context, sourceUri, i, width, 100)
                if (bitmap != null) {
                    val rotation = pageRotations[i]
                    if (rotation != null && rotation != 0f) {
                        bitmap = rotateBitmap(bitmap, rotation)
                    }

                    val pageInfo = PdfDocument.PageInfo.Builder(
                        bitmap.width,
                        bitmap.height,
                        i + 1
                    ).create()

                    val page = document.startPage(pageInfo)
                    page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    document.finishPage(page)
                    bitmap.recycle()
                }
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

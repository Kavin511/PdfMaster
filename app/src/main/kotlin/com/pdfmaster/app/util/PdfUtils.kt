package com.pdfmaster.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PdfUtils {

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
     * Compress a PDF by reducing rendering quality
     */
    suspend fun compressPdf(
        context: Context,
        sourceUri: Uri,
        outputFile: File,
        quality: CompressionQuality
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val scaleFactor = when (quality) {
                CompressionQuality.LOW -> 0.5f
                CompressionQuality.MEDIUM -> 0.7f
                CompressionQuality.HIGH -> 0.85f
            }

            val pageCount = getPageCount(context, sourceUri)
            val document = PdfDocument()
            val baseWidth = (context.resources.displayMetrics.widthPixels * scaleFactor).toInt()

            for (i in 0 until pageCount) {
                val bitmap = renderPage(context, sourceUri, i, baseWidth, 80)
                if (bitmap != null) {
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
}

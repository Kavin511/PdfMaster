package com.pdfmaster.app.util

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object FileUtils {

    fun getFileName(context: Context, uri: Uri): String {
        var name = "document.pdf"

        when (uri.scheme) {
            ContentResolver.SCHEME_CONTENT -> {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) {
                            name = cursor.getString(nameIndex)
                        }
                    }
                }
            }
            ContentResolver.SCHEME_FILE -> {
                name = uri.lastPathSegment ?: name
            }
        }

        return name
    }

    fun getFileSize(context: Context, uri: Uri): Long {
        var size = 0L

        when (uri.scheme) {
            ContentResolver.SCHEME_CONTENT -> {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex >= 0) {
                            size = cursor.getLong(sizeIndex)
                        }
                    }
                }
            }
            ContentResolver.SCHEME_FILE -> {
                uri.path?.let { path ->
                    size = File(path).length()
                }
            }
        }

        return size
    }

    fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> String.format("%.1f MB", size / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0))
        }
    }

    fun formatDate(date: Date): String {
        val now = Calendar.getInstance()
        val dateCalendar = Calendar.getInstance().apply { time = date }

        return when {
            isSameDay(now, dateCalendar) -> {
                SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
            }
            isYesterday(now, dateCalendar) -> "Yesterday"
            isSameYear(now, dateCalendar) -> {
                SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
            }
            else -> {
                SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date)
            }
        }
    }

    private fun isSameDay(c1: Calendar, c2: Calendar): Boolean {
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
                c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
    }

    private fun isYesterday(now: Calendar, date: Calendar): Boolean {
        val yesterday = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }
        return isSameDay(yesterday, date)
    }

    private fun isSameYear(c1: Calendar, c2: Calendar): Boolean {
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR)
    }

    fun generateOutputFileName(prefix: String = "PDF", extension: String = "pdf"): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "${prefix}_$timestamp.$extension"
    }

    fun getOutputDirectory(context: Context): File {
        // Use public Documents/PdfMaster folder for user accessibility
        val publicDocsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val dir = File(publicDocsDir, "PdfMaster")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        // Fallback to app-specific directory if public folder is not writable
        return if (dir.canWrite()) {
            dir
        } else {
            val fallbackDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "PdfMaster")
            if (!fallbackDir.exists()) {
                fallbackDir.mkdirs()
            }
            fallbackDir
        }
    }

    fun getTempDirectory(context: Context): File {
        val dir = File(context.cacheDir, "temp")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun clearTempDirectory(context: Context): Boolean {
        return try {
            getTempDirectory(context).deleteRecursively()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun copyUriToFile(context: Context, uri: Uri, outputFile: File): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(outputFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getMimeType(context: Context, uri: Uri): String? {
        return context.contentResolver.getType(uri)
    }

    fun isPdfFile(context: Context, uri: Uri): Boolean {
        val mimeType = getMimeType(context, uri)
        return mimeType == "application/pdf" ||
               getFileName(context, uri).lowercase().endsWith(".pdf")
    }

    fun isImageFile(context: Context, uri: Uri): Boolean {
        val mimeType = getMimeType(context, uri)
        return mimeType?.startsWith("image/") == true
    }

    fun loadBitmapFromUri(context: Context, uri: Uri): android.graphics.Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                android.graphics.BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun saveBitmapToFile(bitmap: android.graphics.Bitmap, file: File, quality: Int = 90): Boolean {
        return try {
            FileOutputStream(file).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, quality, out)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun createTempFile(context: Context, prefix: String, suffix: String): File {
        val tempDir = getTempDirectory(context)
        return File.createTempFile(prefix, suffix, tempDir)
    }

    fun getDocumentsDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "PdfMaster")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getSignaturesDir(context: Context): File {
        val dir = File(context.filesDir, "signatures")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getScansDir(context: Context): File {
        val dir = File(getDocumentsDir(context), "Scans")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getMergedDir(context: Context): File {
        val dir = File(getDocumentsDir(context), "Merged")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getCompressedDir(context: Context): File {
        val dir = File(getDocumentsDir(context), "Compressed")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getConvertedDir(context: Context): File {
        val dir = File(getDocumentsDir(context), "Converted")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getSplitDir(context: Context): File {
        val dir = File(getDocumentsDir(context), "Split")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun calculateDirectorySize(directory: File): Long {
        var size = 0L
        if (directory.isDirectory) {
            directory.listFiles()?.forEach { file ->
                size += if (file.isDirectory) {
                    calculateDirectorySize(file)
                } else {
                    file.length()
                }
            }
        }
        return size
    }

    fun getCacheSize(context: Context): Long {
        return calculateDirectorySize(context.cacheDir)
    }
}

package com.pdfmaster.app.domain.repository

import android.graphics.Bitmap
import android.net.Uri
import com.pdfmaster.app.domain.model.*
import kotlinx.coroutines.flow.Flow

interface PdfRepository {

    // Document Operations
    suspend fun openPdf(uri: Uri): Result<PdfDocument>
    suspend fun savePdf(document: PdfDocument, outputUri: Uri): Result<Uri>
    suspend fun getPageCount(uri: Uri): Result<Int>
    suspend fun getMetadata(uri: Uri): Result<PdfMetadata>

    // Page Rendering
    suspend fun renderPage(uri: Uri, pageNumber: Int, width: Int, quality: Int = 100): Result<Bitmap>
    suspend fun renderThumbnail(uri: Uri, pageNumber: Int, width: Int = 200): Result<Bitmap>

    // Page Operations
    suspend fun deletePage(uri: Uri, pageNumber: Int, outputUri: Uri): Result<Uri>
    suspend fun deletePages(uri: Uri, pageNumbers: List<Int>, outputUri: Uri): Result<Uri>
    suspend fun reorderPages(uri: Uri, newOrder: List<Int>, outputUri: Uri): Result<Uri>
    suspend fun rotatePage(uri: Uri, pageNumber: Int, degrees: Int, outputUri: Uri): Result<Uri>
    suspend fun rotatePages(uri: Uri, rotations: Map<Int, Int>, outputUri: Uri): Result<Uri>
    suspend fun addBlankPage(uri: Uri, afterPage: Int, outputUri: Uri): Result<Uri>
    suspend fun duplicatePage(uri: Uri, pageNumber: Int, outputUri: Uri): Result<Uri>

    // PDF Tools
    suspend fun mergePdfs(uris: List<Uri>, outputUri: Uri): Result<Uri>
    suspend fun splitPdf(uri: Uri, ranges: List<IntRange>, outputDir: Uri): Result<List<Uri>>
    suspend fun extractPages(uri: Uri, pageNumbers: List<Int>, outputUri: Uri): Result<Uri>
    suspend fun compressPdf(uri: Uri, quality: CompressionQuality, outputUri: Uri): Result<Uri>

    // Conversion
    suspend fun imagesToPdf(imageUris: List<Uri>, outputUri: Uri): Result<Uri>
    suspend fun pdfToImages(uri: Uri, outputDir: Uri, format: ImageFormat = ImageFormat.PNG): Result<List<Uri>>
    suspend fun pdfToText(uri: Uri): Result<String>

    // Security
    suspend fun encryptPdf(uri: Uri, password: String, outputUri: Uri): Result<Uri>
    suspend fun decryptPdf(uri: Uri, password: String, outputUri: Uri): Result<Uri>
    suspend fun isPasswordProtected(uri: Uri): Result<Boolean>
    suspend fun validatePassword(uri: Uri, password: String): Result<Boolean>

    // Text Operations
    suspend fun extractText(uri: Uri, pageNumber: Int): Result<List<TextBlock>>
    suspend fun searchText(uri: Uri, query: String): Result<List<SearchResult>>
}

enum class CompressionQuality(val value: Int) {
    HIGH(90),      // Minimal compression, best quality
    MEDIUM(70),    // Balanced compression
    LOW(50)        // Maximum compression, smaller size
}

enum class ImageFormat {
    PNG,
    JPEG,
    WEBP
}

data class SearchResult(
    val pageNumber: Int,
    val text: String,
    val bounds: List<androidx.compose.ui.geometry.Rect>,
    val context: String
)

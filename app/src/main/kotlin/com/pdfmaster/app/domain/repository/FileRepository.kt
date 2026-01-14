package com.pdfmaster.app.domain.repository

import android.net.Uri
import com.pdfmaster.app.domain.model.PdfDocument
import kotlinx.coroutines.flow.Flow

interface FileRepository {

    // File Discovery
    fun getAllPdfFiles(): Flow<List<PdfDocument>>
    fun getRecentFiles(limit: Int = 20): Flow<List<PdfDocument>>
    fun getFavoriteFiles(): Flow<List<PdfDocument>>
    fun searchFiles(query: String): Flow<List<PdfDocument>>

    // File Operations
    suspend fun getFileInfo(uri: Uri): Result<PdfDocument>
    suspend fun deleteFile(uri: Uri): Result<Boolean>
    suspend fun renameFile(uri: Uri, newName: String): Result<Uri>
    suspend fun copyFile(uri: Uri, destinationUri: Uri): Result<Uri>
    suspend fun moveFile(uri: Uri, destinationUri: Uri): Result<Uri>
    suspend fun shareFile(uri: Uri): Result<Uri>

    // Favorites
    suspend fun addToFavorites(documentId: Long): Result<Boolean>
    suspend fun removeFromFavorites(documentId: Long): Result<Boolean>
    suspend fun toggleFavorite(documentId: Long): Result<Boolean>

    // Recent Files
    suspend fun addToRecent(document: PdfDocument): Result<Boolean>
    suspend fun clearRecentFiles(): Result<Boolean>

    // Thumbnail Cache
    suspend fun getThumbnail(uri: Uri, pageNumber: Int = 0): Result<String?>
    suspend fun saveThumbnail(uri: Uri, pageNumber: Int, thumbnailPath: String): Result<Boolean>
    suspend fun clearThumbnailCache(): Result<Boolean>

    // Storage
    suspend fun getOutputUri(fileName: String): Uri
    suspend fun getTempUri(fileName: String): Uri
    suspend fun clearTempFiles(): Result<Boolean>
    suspend fun getAvailableStorage(): Long
}

package com.pdfmaster.app.data.repository

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import androidx.core.net.toUri
import com.pdfmaster.app.data.local.database.PdfDao
import com.pdfmaster.app.data.local.entity.RecentFileEntity
import com.pdfmaster.app.domain.model.PdfDocument
import com.pdfmaster.app.domain.repository.FileRepository
import com.pdfmaster.app.util.FileUtils
import com.pdfmaster.app.util.PdfUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pdfDao: PdfDao
) : FileRepository {

    override fun getAllPdfFiles(): Flow<List<PdfDocument>> = flow {
        // Get PDF files from common directories
        val pdfFiles = mutableListOf<PdfDocument>()

        val directories = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            FileUtils.getDocumentsDir(context)
        )

        directories.forEach { dir ->
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.filter { it.extension.lowercase() == "pdf" }?.forEach { file ->
                    pdfFiles.add(
                        PdfDocument(
                            id = file.absolutePath.hashCode().toLong(),
                            uri = file.toUri().toString(),
                            name = file.name,
                            pageCount = 0, // Will be loaded lazily
                            size = file.length(),
                            modifiedAt = Date(file.lastModified())
                        )
                    )
                }
            }
        }

        emit(pdfFiles.sortedByDescending { it.modifiedAt })
    }.flowOn(Dispatchers.IO)

    override fun getRecentFiles(limit: Int): Flow<List<PdfDocument>> {
        return pdfDao.getRecentFiles(limit).map { entities ->
            entities.mapNotNull { entity ->
                try {
                    val uri = Uri.parse(entity.uri)
                    val pageCount = PdfUtils.getPageCount(context, uri)
                    PdfDocument(
                        id = entity.id,
                        uri = entity.uri,
                        name = entity.name,
                        pageCount = pageCount,
                        size = entity.size,
                        modifiedAt = Date(entity.lastOpenedAt),
                        isFavorite = entity.isFavorite
                    )
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    override fun getFavoriteFiles(): Flow<List<PdfDocument>> {
        return pdfDao.getFavoriteFiles().map { entities ->
            entities.mapNotNull { entity ->
                try {
                    PdfDocument(
                        id = entity.id,
                        uri = entity.uri,
                        name = entity.name,
                        pageCount = 0,
                        size = entity.size,
                        modifiedAt = Date(entity.lastOpenedAt),
                        isFavorite = true
                    )
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    override fun searchFiles(query: String): Flow<List<PdfDocument>> = flow {
        val results = mutableListOf<PdfDocument>()
        val lowercaseQuery = query.lowercase()

        val directories = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            FileUtils.getDocumentsDir(context)
        )

        directories.forEach { dir ->
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()
                    ?.filter { it.extension.lowercase() == "pdf" && it.name.lowercase().contains(lowercaseQuery) }
                    ?.forEach { file ->
                        results.add(
                            PdfDocument(
                                id = file.absolutePath.hashCode().toLong(),
                                uri = file.toUri().toString(),
                                name = file.name,
                                pageCount = 0,
                                size = file.length(),
                                modifiedAt = Date(file.lastModified())
                            )
                        )
                    }
            }
        }

        emit(results)
    }.flowOn(Dispatchers.IO)

    override suspend fun getFileInfo(uri: Uri): Result<PdfDocument> = withContext(Dispatchers.IO) {
        try {
            val fileName = FileUtils.getFileName(context, uri)
            val fileSize = FileUtils.getFileSize(context, uri)
            val pageCount = PdfUtils.getPageCount(context, uri)

            Result.success(
                PdfDocument(
                    id = uri.hashCode().toLong(),
                    uri = uri.toString(),
                    name = fileName,
                    pageCount = pageCount,
                    size = fileSize,
                    modifiedAt = Date()
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteFile(uri: Uri): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val file = File(uri.path ?: return@withContext Result.failure(Exception("Invalid path")))
            val deleted = file.delete()
            Result.success(deleted)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun renameFile(uri: Uri, newName: String): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val file = File(uri.path ?: return@withContext Result.failure(Exception("Invalid path")))
            val newFile = File(file.parent, newName)
            val success = file.renameTo(newFile)
            if (success) {
                Result.success(newFile.toUri())
            } else {
                Result.failure(Exception("Failed to rename file"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun copyFile(uri: Uri, destinationUri: Uri): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val destFile = File(destinationUri.path ?: return@withContext Result.failure(Exception("Invalid path")))
            val success = FileUtils.copyUriToFile(context, uri, destFile)
            if (success) {
                Result.success(destFile.toUri())
            } else {
                Result.failure(Exception("Failed to copy file"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun moveFile(uri: Uri, destinationUri: Uri): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val copyResult = copyFile(uri, destinationUri)
            if (copyResult.isSuccess) {
                deleteFile(uri)
                copyResult
            } else {
                copyResult
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun shareFile(uri: Uri): Result<Uri> {
        return Result.success(uri)
    }

    override suspend fun addToFavorites(documentId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            pdfDao.setFavorite(documentId, true)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeFromFavorites(documentId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            pdfDao.setFavorite(documentId, false)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun toggleFavorite(documentId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val current = pdfDao.getRecentFileById(documentId)
            val newValue = !(current?.isFavorite ?: false)
            pdfDao.setFavorite(documentId, newValue)
            Result.success(newValue)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun addToRecent(document: PdfDocument): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val entity = RecentFileEntity(
                id = document.id,
                uri = document.uri,
                name = document.name,
                size = document.size,
                lastOpenedAt = System.currentTimeMillis(),
                isFavorite = document.isFavorite,
                thumbnailPath = null
            )
            pdfDao.insertRecentFile(entity)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun clearRecentFiles(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            pdfDao.deleteAllRecentFiles()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getThumbnail(uri: Uri, pageNumber: Int): Result<String?> = withContext(Dispatchers.IO) {
        try {
            val id = uri.hashCode().toLong()
            val entity = pdfDao.getRecentFileById(id)
            Result.success(entity?.thumbnailPath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun saveThumbnail(uri: Uri, pageNumber: Int, thumbnailPath: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val id = uri.hashCode().toLong()
            pdfDao.updateThumbnailPath(id, thumbnailPath)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun clearThumbnailCache(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val cacheDir = File(context.cacheDir, "thumbnails")
            cacheDir.deleteRecursively()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getOutputUri(fileName: String): Uri {
        val outputFile = File(FileUtils.getDocumentsDir(context), fileName)
        return outputFile.toUri()
    }

    override suspend fun getTempUri(fileName: String): Uri {
        val tempFile = File(FileUtils.getTempDirectory(context), fileName)
        return tempFile.toUri()
    }

    override suspend fun clearTempFiles(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            FileUtils.clearTempDirectory(context)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAvailableStorage(): Long = withContext(Dispatchers.IO) {
        try {
            val stat = StatFs(Environment.getExternalStorageDirectory().path)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            0L
        }
    }
}

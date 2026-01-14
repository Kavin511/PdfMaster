package com.pdfmaster.app.data.local.database

import androidx.room.*
import com.pdfmaster.app.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfDao {

    // PDF Documents
    @Query("SELECT * FROM pdf_documents ORDER BY modifiedAt DESC")
    fun getAllDocuments(): Flow<List<PdfEntity>>

    @Query("SELECT * FROM pdf_documents WHERE isFavorite = 1 ORDER BY name ASC")
    fun getFavoriteDocuments(): Flow<List<PdfEntity>>

    @Query("SELECT * FROM pdf_documents WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchDocuments(query: String): Flow<List<PdfEntity>>

    @Query("SELECT * FROM pdf_documents WHERE uri = :uri LIMIT 1")
    suspend fun getDocumentByUri(uri: String): PdfEntity?

    @Query("SELECT * FROM pdf_documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentById(id: Long): PdfEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: PdfEntity): Long

    @Update
    suspend fun updateDocument(document: PdfEntity)

    @Delete
    suspend fun deleteDocument(document: PdfEntity)

    @Query("DELETE FROM pdf_documents WHERE uri = :uri")
    suspend fun deleteDocumentByUri(uri: String)

    @Query("UPDATE pdf_documents SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavoriteStatus(id: Long, isFavorite: Boolean)

    @Query("UPDATE pdf_documents SET lastOpened = :timestamp WHERE id = :id")
    suspend fun updateLastOpened(id: Long, timestamp: Long)

    // Recent Files
    @Query("SELECT * FROM recent_files ORDER BY lastOpenedAt DESC LIMIT :limit")
    fun getRecentFiles(limit: Int): Flow<List<RecentFileEntity>>

    @Query("SELECT * FROM recent_files WHERE isFavorite = 1 ORDER BY name ASC")
    fun getFavoriteFiles(): Flow<List<RecentFileEntity>>

    @Query("SELECT * FROM recent_files WHERE id = :id LIMIT 1")
    suspend fun getRecentFileById(id: Long): RecentFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentFile(file: RecentFileEntity)

    @Query("UPDATE recent_files SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE recent_files SET thumbnailPath = :path WHERE id = :id")
    suspend fun updateThumbnailPath(id: Long, path: String)

    @Query("DELETE FROM recent_files")
    suspend fun deleteAllRecentFiles()

    @Query("DELETE FROM recent_files WHERE uri = :uri")
    suspend fun removeFromRecent(uri: String)

    // Keep only the last N recent files
    @Query("DELETE FROM recent_files WHERE uri NOT IN (SELECT uri FROM recent_files ORDER BY lastOpenedAt DESC LIMIT :keepCount)")
    suspend fun trimRecentFiles(keepCount: Int = 50)

    // Signatures
    @Query("SELECT * FROM signatures ORDER BY createdAt DESC")
    fun getAllSignatures(): Flow<List<SignatureEntity>>

    @Query("SELECT * FROM signatures WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultSignature(): SignatureEntity?

    @Query("SELECT * FROM signatures WHERE id = :id LIMIT 1")
    suspend fun getSignatureById(id: String): SignatureEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSignature(signature: SignatureEntity)

    @Delete
    suspend fun deleteSignature(signature: SignatureEntity)

    @Query("UPDATE signatures SET isDefault = 0")
    suspend fun clearDefaultSignature()

    @Query("UPDATE signatures SET isDefault = 1 WHERE id = :id")
    suspend fun setDefaultSignature(id: String)

    // Annotations
    @Query("SELECT * FROM annotations WHERE documentUri = :documentUri ORDER BY pageNumber, createdAt")
    fun getAnnotationsForDocument(documentUri: String): Flow<List<AnnotationEntity>>

    @Query("SELECT * FROM annotations WHERE documentUri = :documentUri AND pageNumber = :pageNumber ORDER BY createdAt")
    fun getAnnotationsForPage(documentUri: String, pageNumber: Int): Flow<List<AnnotationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotation(annotation: AnnotationEntity)

    @Update
    suspend fun updateAnnotation(annotation: AnnotationEntity)

    @Delete
    suspend fun deleteAnnotation(annotation: AnnotationEntity)

    @Query("DELETE FROM annotations WHERE documentUri = :documentUri")
    suspend fun deleteAnnotationsForDocument(documentUri: String)

    @Query("DELETE FROM annotations WHERE documentUri = :documentUri AND pageNumber = :pageNumber")
    suspend fun deleteAnnotationsForPage(documentUri: String, pageNumber: Int)
}

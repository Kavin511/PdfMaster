package com.pdfmaster.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.pdfmaster.app.data.local.database.Converters

@Entity(tableName = "pdf_documents")
@TypeConverters(Converters::class)
data class PdfEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uri: String,
    val name: String,
    val path: String,
    val size: Long,
    val pageCount: Int = 0,
    val lastOpened: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val isPasswordProtected: Boolean = false,
    val thumbnailPath: String? = null,
    val tags: String = "" // Comma separated tags
)

@Entity(tableName = "recent_files")
data class RecentFileEntity(
    @PrimaryKey
    val id: Long = 0,
    val uri: String,
    val name: String,
    val path: String = "",
    val size: Long,
    val pageCount: Int = 0,
    val openedAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val thumbnailPath: String? = null
)

@Entity(tableName = "signatures")
data class SignatureEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val type: String,
    val data: String, // JSON serialized
    val createdAt: Long = System.currentTimeMillis(),
    val isDefault: Boolean = false
)

@Entity(tableName = "annotations")
data class AnnotationEntity(
    @PrimaryKey
    val id: String,
    val documentUri: String,
    val pageNumber: Int,
    val type: String,
    val data: String, // JSON serialized
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis()
)

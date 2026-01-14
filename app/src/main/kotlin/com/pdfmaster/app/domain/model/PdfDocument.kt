package com.pdfmaster.app.domain.model

import android.net.Uri
import java.util.Date

data class PdfDocument(
    val id: Long = 0,
    val uri: String,
    val name: String,
    val path: String? = null,
    val size: Long,
    val pageCount: Int = 0,
    val lastOpened: Date? = null,
    val createdAt: Date = Date(),
    val modifiedAt: Date = Date(),
    val isFavorite: Boolean = false,
    val isPasswordProtected: Boolean = false,
    val thumbnailPath: String? = null,
    val tags: List<String> = emptyList()
) {
    val parsedUri: Uri get() = Uri.parse(uri)

    val sizeFormatted: String
        get() = when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> String.format("%.1f MB", size / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0))
        }

    val extension: String
        get() = name.substringAfterLast('.', "pdf").lowercase()
}

data class PdfPage(
    val pageNumber: Int,
    val width: Int,
    val height: Int,
    val rotation: Int = 0,
    val thumbnailPath: String? = null
)

data class PdfMetadata(
    val title: String? = null,
    val author: String? = null,
    val subject: String? = null,
    val keywords: String? = null,
    val creator: String? = null,
    val producer: String? = null,
    val creationDate: Date? = null,
    val modificationDate: Date? = null,
    val pageCount: Int = 0,
    val pageWidth: Int = 0,
    val pageHeight: Int = 0,
    val fileSize: Long = 0,
    val isEncrypted: Boolean = false
)

package com.pdfmaster.app.presentation.ui.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdfmaster.app.data.local.database.PdfDao
import com.pdfmaster.app.data.local.entity.PdfEntity
import com.pdfmaster.app.data.local.entity.RecentFileEntity
import com.pdfmaster.app.data.local.preferences.UserPreferences
import com.pdfmaster.app.domain.model.PdfDocument
import com.pdfmaster.app.domain.repository.SortOrder
import com.pdfmaster.app.domain.repository.ViewMode
import com.pdfmaster.app.util.FileUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

data class HomeUiState(
    val allFiles: List<PdfDocument> = emptyList(),
    val recentFiles: List<PdfDocument> = emptyList(),
    val favoriteFiles: List<PdfDocument> = emptyList(),
    val searchResults: List<PdfDocument> = emptyList(),
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val isLoading: Boolean = true,
    val isGridView: Boolean = false,
    val sortOrder: SortOrder = SortOrder.DATE_NEWEST,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val pdfDao: PdfDao,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadPreferences()
        loadFiles()
    }

    private fun loadPreferences() {
        viewModelScope.launch {
            userPreferences.getDefaultViewMode().collect { viewMode ->
                _uiState.update { it.copy(isGridView = viewMode == ViewMode.GRID) }
            }
        }

        viewModelScope.launch {
            userPreferences.getDefaultSortOrder().collect { sortOrder ->
                _uiState.update { it.copy(sortOrder = sortOrder) }
            }
        }
    }

    private fun loadFiles() {
        // Load all files
        viewModelScope.launch {
            pdfDao.getAllDocuments().collect { entities ->
                val documents = entities.map { it.toPdfDocument() }
                val sorted = sortDocuments(documents, _uiState.value.sortOrder)
                _uiState.update { it.copy(allFiles = sorted, isLoading = false) }
            }
        }

        // Load recent files
        viewModelScope.launch {
            pdfDao.getRecentFiles(20).collect { entities ->
                val documents = entities.map { it.toPdfDocument() }
                _uiState.update { it.copy(recentFiles = documents) }
            }
        }

        // Load favorites
        viewModelScope.launch {
            pdfDao.getFavoriteDocuments().collect { entities ->
                val documents = entities.map { it.toPdfDocument() }
                _uiState.update { it.copy(favoriteFiles = documents) }
            }
        }
    }

    fun toggleViewMode() {
        viewModelScope.launch {
            val newMode = if (_uiState.value.isGridView) ViewMode.LIST else ViewMode.GRID
            userPreferences.setDefaultViewMode(newMode)
            _uiState.update { it.copy(isGridView = !it.isGridView) }
        }
    }

    fun setSortOrder(order: SortOrder) {
        viewModelScope.launch {
            userPreferences.setDefaultSortOrder(order)
            _uiState.update { state ->
                state.copy(
                    sortOrder = order,
                    allFiles = sortDocuments(state.allFiles, order)
                )
            }
        }
    }

    fun setSearchActive(active: Boolean) {
        _uiState.update { it.copy(isSearchActive = active, searchQuery = if (!active) "" else it.searchQuery, searchResults = emptyList()) }
    }

    fun search(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }
        viewModelScope.launch {
            pdfDao.searchDocuments(query).collect { entities ->
                val documents = entities.map { it.toPdfDocument() }
                _uiState.update { it.copy(searchResults = documents) }
            }
        }
    }

    fun toggleFavorite(document: PdfDocument) {
        viewModelScope.launch {
            pdfDao.updateFavoriteStatus(document.id, !document.isFavorite)
        }
    }

    fun deleteFile(document: PdfDocument) {
        viewModelScope.launch {
            pdfDao.deleteDocumentByUri(document.uri)
            pdfDao.removeFromRecent(document.uri)
        }
    }

    fun shareFile(context: Context, document: PdfDocument) {
        try {
            val uri = document.parsedUri

            // Copy to cache for reliable sharing
            val fileName = document.name.ifEmpty { "document.pdf" }
            val cacheFile = java.io.File(context.cacheDir, "share_$fileName")

            context.contentResolver.openInputStream(uri)?.use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw Exception("Cannot read file")

            val shareUri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                cacheFile
            )

            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(android.content.Intent.EXTRA_STREAM, shareUri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Share PDF"))
        } catch (e: Exception) {
            e.printStackTrace()
            _uiState.update { it.copy(error = "Failed to share: ${e.message}") }
        }
    }

    fun addToRecent(context: Context, uri: Uri) {
        viewModelScope.launch {
            val fileName = FileUtils.getFileName(context, uri)
            val fileSize = FileUtils.getFileSize(context, uri)
            val currentTime = System.currentTimeMillis()

            val recentFile = RecentFileEntity(
                id = uri.hashCode().toLong(),
                uri = uri.toString(),
                name = fileName,
                path = uri.path ?: "",
                size = fileSize,
                pageCount = 0,
                openedAt = currentTime,
                lastOpenedAt = currentTime
            )

            pdfDao.insertRecentFile(recentFile)
            pdfDao.trimRecentFiles(50)
        }
    }

    private fun sortDocuments(documents: List<PdfDocument>, order: SortOrder): List<PdfDocument> {
        return when (order) {
            SortOrder.NAME_ASC -> documents.sortedBy { it.name.lowercase() }
            SortOrder.NAME_DESC -> documents.sortedByDescending { it.name.lowercase() }
            SortOrder.DATE_NEWEST -> documents.sortedByDescending { it.modifiedAt }
            SortOrder.DATE_OLDEST -> documents.sortedBy { it.modifiedAt }
            SortOrder.SIZE_LARGEST -> documents.sortedByDescending { it.size }
            SortOrder.SIZE_SMALLEST -> documents.sortedBy { it.size }
        }
    }

    private fun PdfEntity.toPdfDocument(): PdfDocument {
        return PdfDocument(
            id = id,
            uri = uri,
            name = name,
            path = path,
            size = size,
            pageCount = pageCount,
            lastOpened = lastOpened?.let { Date(it) },
            createdAt = Date(createdAt),
            modifiedAt = Date(modifiedAt),
            isFavorite = isFavorite,
            isPasswordProtected = isPasswordProtected,
            thumbnailPath = thumbnailPath,
            tags = if (tags.isEmpty()) emptyList() else tags.split(",")
        )
    }

    private fun RecentFileEntity.toPdfDocument(): PdfDocument {
        return PdfDocument(
            id = id,
            uri = uri,
            name = name,
            path = path,
            size = size,
            pageCount = pageCount,
            lastOpened = Date(openedAt),
            createdAt = Date(openedAt),
            modifiedAt = Date(openedAt),
            isFavorite = isFavorite,
            thumbnailPath = thumbnailPath
        )
    }
}

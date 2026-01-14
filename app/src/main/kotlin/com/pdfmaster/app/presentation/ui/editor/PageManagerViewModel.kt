package com.pdfmaster.app.presentation.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdfmaster.app.util.FileUtils
import com.pdfmaster.app.util.PdfUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class PageManagerUiState(
    val uri: Uri? = null,
    val pageCount: Int = 0,
    val pages: List<Bitmap?> = emptyList(),
    val pageRotations: Map<Int, Int> = emptyMap(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val hasChanges: Boolean = false
)

@HiltViewModel
class PageManagerViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(PageManagerUiState())
    val uiState: StateFlow<PageManagerUiState> = _uiState.asStateFlow()

    private var originalPageOrder: List<Int> = emptyList()
    private var currentPageOrder: MutableList<Int> = mutableListOf()
    private var deletedPages: MutableSet<Int> = mutableSetOf()

    fun loadPdf(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, uri = uri) }

            try {
                val pageCount = PdfUtils.getPageCount(context, uri)

                if (pageCount == 0) {
                    _uiState.update { it.copy(isLoading = false, error = "Could not open PDF") }
                    return@launch
                }

                originalPageOrder = (0 until pageCount).toList()
                currentPageOrder = originalPageOrder.toMutableList()

                val initialPages = List(pageCount) { null as Bitmap? }

                _uiState.update {
                    it.copy(
                        pageCount = pageCount,
                        pages = initialPages,
                        isLoading = false
                    )
                }

                loadThumbnailsAsync(context, uri, pageCount)

            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun loadThumbnailsAsync(context: Context, uri: Uri, pageCount: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            for (pageIndex in 0 until pageCount) {
                val bitmap = PdfUtils.renderThumbnail(context, uri, pageIndex, 300)

                withContext(Dispatchers.Main) {
                    _uiState.update { state ->
                        val updatedPages = state.pages.toMutableList()
                        if (pageIndex < updatedPages.size) {
                            updatedPages[pageIndex] = bitmap
                        }
                        state.copy(pages = updatedPages)
                    }
                }
            }
        }
    }

    fun reorderPage(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return

        val item = currentPageOrder.removeAt(fromIndex)
        currentPageOrder.add(toIndex, item)

        _uiState.update { state ->
            val pages = state.pages.toMutableList()
            val movedPage = pages.removeAt(fromIndex)
            pages.add(toIndex, movedPage)

            state.copy(
                pages = pages,
                hasChanges = currentPageOrder != originalPageOrder || deletedPages.isNotEmpty()
            )
        }
    }

    fun rotatePage(pageIndex: Int) {
        _uiState.update { state ->
            val currentRotation = state.pageRotations[pageIndex] ?: 0
            val newRotation = (currentRotation + 90) % 360

            state.copy(
                pageRotations = state.pageRotations + (pageIndex to newRotation),
                hasChanges = true
            )
        }
    }

    fun deletePage(pageIndex: Int) {
        if (_uiState.value.pageCount <= 1) return

        val originalIndex = currentPageOrder[pageIndex]
        currentPageOrder.removeAt(pageIndex)
        deletedPages.add(originalIndex)

        _uiState.update { state ->
            val pages = state.pages.toMutableList()
            pages.removeAt(pageIndex)

            state.copy(
                pages = pages,
                pageCount = state.pageCount - 1,
                hasChanges = true
            )
        }
    }

    fun deletePages(pageIndices: List<Int>) {
        val sortedIndices = pageIndices.sortedDescending()
        sortedIndices.forEach { deletePage(it) }
    }

    fun duplicatePage(pageIndex: Int) {
        _uiState.update { state ->
            val pages = state.pages.toMutableList()
            val duplicatedPage = pages[pageIndex]
            pages.add(pageIndex + 1, duplicatedPage)

            currentPageOrder.add(pageIndex + 1, currentPageOrder[pageIndex])

            state.copy(
                pages = pages,
                pageCount = state.pageCount + 1,
                hasChanges = true
            )
        }
    }

    fun addBlankPage() {
        _uiState.update { state ->
            val pages = state.pages.toMutableList()
            pages.add(null) // Blank page placeholder

            currentPageOrder.add(-1) // -1 indicates new blank page

            state.copy(
                pages = pages,
                pageCount = state.pageCount + 1,
                hasChanges = true
            )
        }
    }

    fun save(context: Context, onComplete: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }

            try {
                val sourceUri = _uiState.value.uri ?: throw Exception("No PDF loaded")
                val outputDir = FileUtils.getOutputDirectory(context)
                val outputFile = File(outputDir, FileUtils.generateOutputFileName("Edited"))

                // Get the page indices to keep (non-deleted pages in current order)
                // currentPageOrder contains original indices, filter out -1 (blank pages) for now
                val pageIndicesToKeep = currentPageOrder.filter { it >= 0 }

                // Get rotation map (convert to Float for PdfUtils)
                val rotations = _uiState.value.pageRotations.mapValues { it.value.toFloat() }

                val success = if (rotations.isNotEmpty()) {
                    // If there are rotations, we need to handle them
                    // First extract/reorder pages, then rotate
                    val tempFile = File(outputDir, "temp_${System.currentTimeMillis()}.pdf")

                    val extractSuccess = PdfUtils.extractPages(context, sourceUri, pageIndicesToKeep, tempFile)
                    if (extractSuccess) {
                        val rotateSuccess = PdfUtils.rotatePages(
                            context,
                            Uri.fromFile(tempFile),
                            rotations,
                            outputFile
                        )
                        tempFile.delete()
                        rotateSuccess
                    } else {
                        false
                    }
                } else {
                    // No rotations, just extract/reorder pages
                    PdfUtils.extractPages(context, sourceUri, pageIndicesToKeep, outputFile)
                }

                if (success) {
                    val outputUri = Uri.fromFile(outputFile).toString()

                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            hasChanges = false,
                            uri = Uri.fromFile(outputFile)
                        )
                    }

                    // Reset tracking state
                    originalPageOrder = (0 until pageIndicesToKeep.size).toList()
                    currentPageOrder = originalPageOrder.toMutableList()
                    deletedPages.clear()

                    onComplete?.invoke(outputUri)
                } else {
                    throw Exception("Failed to save PDF")
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = "Failed to save: ${e.message}"
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

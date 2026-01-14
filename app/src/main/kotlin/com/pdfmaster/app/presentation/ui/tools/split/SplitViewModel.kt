package com.pdfmaster.app.presentation.ui.tools.split

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdfmaster.app.util.FileUtils
import com.pdfmaster.app.util.PdfUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class SplitUiState(
    val uri: Uri? = null,
    val fileName: String = "",
    val pageCount: Int = 0,
    val pages: List<Bitmap?> = emptyList(),
    val selectedPages: Set<Int> = emptySet(),
    val isLoading: Boolean = false,
    val isSplitting: Boolean = false,
    val outputUri: String? = null,
    val error: String? = null
)

@HiltViewModel
class SplitViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(SplitUiState())
    val uiState: StateFlow<SplitUiState> = _uiState.asStateFlow()

    fun loadPdf(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, uri = uri, error = null) }
            try {
                val pageCount = PdfUtils.getPageCount(context, uri)
                val fileName = FileUtils.getFileName(context, uri)
                _uiState.update {
                    it.copy(
                        pageCount = pageCount,
                        fileName = fileName,
                        pages = List(pageCount) { null },
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to load PDF: ${e.message}")
                }
            }
        }
    }

    fun loadThumbnail(context: Context, pageIndex: Int) {
        viewModelScope.launch {
            val uri = _uiState.value.uri ?: return@launch
            val thumbnail = PdfUtils.renderThumbnail(context, uri, pageIndex, 200)
            _uiState.update { state ->
                val updatedPages = state.pages.toMutableList()
                if (pageIndex < updatedPages.size) {
                    updatedPages[pageIndex] = thumbnail
                }
                state.copy(pages = updatedPages)
            }
        }
    }

    fun togglePageSelection(pageIndex: Int) {
        _uiState.update { state ->
            val selected = if (state.selectedPages.contains(pageIndex)) {
                state.selectedPages - pageIndex
            } else {
                state.selectedPages + pageIndex
            }
            state.copy(selectedPages = selected)
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            state.copy(selectedPages = (0 until state.pageCount).toSet())
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedPages = emptySet()) }
    }

    fun extractPages(context: Context, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            val uri = _uiState.value.uri ?: return@launch
            val selectedPages = _uiState.value.selectedPages

            if (selectedPages.isEmpty()) {
                _uiState.update { it.copy(error = "Please select at least one page") }
                return@launch
            }

            _uiState.update { it.copy(isSplitting = true, error = null) }

            try {
                val outputDir = FileUtils.getOutputDirectory(context)
                val baseName = _uiState.value.fileName.removeSuffix(".pdf")
                val outputFile = File(outputDir, FileUtils.generateOutputFileName("${baseName}_split"))

                val pageIndices = selectedPages.sorted()
                val success = PdfUtils.extractPages(context, uri, pageIndices, outputFile)

                if (success) {
                    val outputUri = Uri.fromFile(outputFile).toString()
                    _uiState.update { it.copy(isSplitting = false, outputUri = outputUri) }
                    onComplete(outputUri)
                } else {
                    throw Exception("Failed to extract pages")
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSplitting = false, error = "Failed to split PDF: ${e.message}")
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun reset() {
        _uiState.update { SplitUiState() }
    }
}

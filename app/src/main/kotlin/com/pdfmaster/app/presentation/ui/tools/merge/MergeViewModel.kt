package com.pdfmaster.app.presentation.ui.tools.merge

import android.content.Context
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

data class MergeUiState(
    val files: List<MergeFileItem> = emptyList(),
    val isMerging: Boolean = false,
    val progress: Int = 0,
    val error: String? = null
)

@HiltViewModel
class MergeViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(MergeUiState())
    val uiState: StateFlow<MergeUiState> = _uiState.asStateFlow()

    fun addFile(file: MergeFileItem) {
        _uiState.update { it.copy(files = it.files + file) }
    }

    fun removeFile(index: Int) {
        _uiState.update {
            it.copy(files = it.files.filterIndexed { i, _ -> i != index })
        }
    }

    fun reorderFiles(fromIndex: Int, toIndex: Int) {
        _uiState.update { state ->
            val files = state.files.toMutableList()
            val item = files.removeAt(fromIndex)
            files.add(toIndex, item)
            state.copy(files = files)
        }
    }

    fun mergePdfs(context: Context, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isMerging = true, error = null, progress = 0) }

            try {
                if (_uiState.value.files.isEmpty()) {
                    throw IllegalStateException("No files to merge")
                }

                val outputDir = FileUtils.getOutputDirectory(context)
                val outputFile = File(outputDir, FileUtils.generateOutputFileName("Merged"))

                val sourceUris: List<Uri> = _uiState.value.files.map { it.uri }

                val success = PdfUtils.mergePdfs(context, sourceUris, outputFile)

                if (success) {
                    _uiState.update { it.copy(isMerging = false, progress = 100) }
                    onComplete(Uri.fromFile(outputFile).toString())
                } else {
                    throw Exception("Merge operation failed")
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isMerging = false, progress = 0, error = "Failed to merge: ${e.message}")
                }
            }
        }
    }

    fun clearFiles() {
        _uiState.update { it.copy(files = emptyList()) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

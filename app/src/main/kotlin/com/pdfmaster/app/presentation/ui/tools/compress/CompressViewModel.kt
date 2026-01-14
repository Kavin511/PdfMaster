package com.pdfmaster.app.presentation.ui.tools.compress

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdfmaster.app.domain.repository.CompressionQuality
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

data class CompressUiState(
    val uri: Uri? = null,
    val fileName: String = "",
    val originalSize: Long = 0,
    val compressedSize: Long = 0,
    val selectedQuality: CompressionQuality = CompressionQuality.MEDIUM,
    val isCompressing: Boolean = false,
    val isComplete: Boolean = false,
    val outputUri: String? = null,
    val error: String? = null
)

@HiltViewModel
class CompressViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(CompressUiState())
    val uiState: StateFlow<CompressUiState> = _uiState.asStateFlow()

    fun loadPdf(context: Context, uri: Uri) {
        val name = FileUtils.getFileName(context, uri)
        val size = FileUtils.getFileSize(context, uri)
        _uiState.update { it.copy(uri = uri, fileName = name, originalSize = size, error = null) }
    }

    fun setQuality(quality: CompressionQuality) {
        _uiState.update { it.copy(selectedQuality = quality) }
    }

    fun compress(context: Context, onComplete: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            val uri = _uiState.value.uri ?: return@launch

            _uiState.update { it.copy(isCompressing = true, error = null) }

            try {
                val outputDir = FileUtils.getOutputDirectory(context)
                val baseName = _uiState.value.fileName.removeSuffix(".pdf")
                val outputFile = File(outputDir, FileUtils.generateOutputFileName("${baseName}_compressed"))

                val pdfUtilsQuality = when (_uiState.value.selectedQuality) {
                    CompressionQuality.HIGH -> PdfUtils.CompressionQuality.HIGH
                    CompressionQuality.MEDIUM -> PdfUtils.CompressionQuality.MEDIUM
                    CompressionQuality.LOW -> PdfUtils.CompressionQuality.LOW
                }

                val success = PdfUtils.compressPdf(context, uri, outputFile, pdfUtilsQuality)

                if (success) {
                    val compressedSize = outputFile.length()
                    val outputUri = Uri.fromFile(outputFile).toString()
                    _uiState.update {
                        it.copy(
                            isCompressing = false,
                            isComplete = true,
                            compressedSize = compressedSize,
                            outputUri = outputUri
                        )
                    }
                    onComplete?.invoke(outputUri)
                } else {
                    throw Exception("Compression failed")
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isCompressing = false, error = "Failed to compress: ${e.message}")
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun reset() {
        _uiState.update { CompressUiState() }
    }
}

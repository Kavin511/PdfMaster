package com.pdfmaster.app.presentation.ui.scanner

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdfmaster.app.data.local.database.PdfDao
import com.pdfmaster.app.data.local.entity.PdfEntity
import com.pdfmaster.app.util.FileUtils
import com.pdfmaster.app.util.PdfUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScannerUiState(
    val scannedPages: List<Uri> = emptyList(),
    val isProcessing: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val pdfDao: PdfDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    fun onScanComplete(context: Context, pdfUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }

            try {
                val fileName = FileUtils.getFileName(context, pdfUri)
                val fileSize = FileUtils.getFileSize(context, pdfUri)
                val pageCount = PdfUtils.getPageCount(context, pdfUri)

                // Save to database
                val pdfEntity = PdfEntity(
                    uri = pdfUri.toString(),
                    name = fileName,
                    path = pdfUri.path ?: "",
                    size = fileSize,
                    pageCount = pageCount,
                    createdAt = System.currentTimeMillis(),
                    modifiedAt = System.currentTimeMillis()
                )

                pdfDao.insertDocument(pdfEntity)

                _uiState.update { it.copy(isProcessing = false) }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        error = "Failed to save scanned document: ${e.message}"
                    )
                }
            }
        }
    }

    fun addPage(uri: Uri) {
        _uiState.update { it.copy(scannedPages = it.scannedPages + uri) }
    }

    fun removePage(index: Int) {
        _uiState.update {
            it.copy(scannedPages = it.scannedPages.filterIndexed { i, _ -> i != index })
        }
    }

    fun clearPages() {
        _uiState.update { it.copy(scannedPages = emptyList()) }
    }
}

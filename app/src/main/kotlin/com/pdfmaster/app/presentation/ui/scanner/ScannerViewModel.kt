package com.pdfmaster.app.presentation.ui.scanner

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdfmaster.app.analytics.Analytics
import com.pdfmaster.app.analytics.AnalyticsEvent
import com.pdfmaster.app.billing.FeatureGate
import com.pdfmaster.app.data.local.database.PdfDao
import com.pdfmaster.app.data.local.entity.PdfEntity
import com.pdfmaster.app.util.FileUtils
import com.pdfmaster.app.util.OpenPdfEditor
import com.pdfmaster.app.util.PdfUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ScannerUiState(
    val scannedPages: List<Uri> = emptyList(),
    val isProcessing: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val pdfDao: PdfDao,
    private val featureGate: FeatureGate,
    private val analytics: Analytics,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    fun onScanComplete(context: Context, pdfUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }

            try {
                // Free tier: stamp a watermark on the scanned output. Premium keeps it clean.
                val watermarked = featureGate.shouldWatermark()
                val savedUri = if (watermarked) {
                    val out = File(
                        FileUtils.getOutputDirectory(context),
                        FileUtils.generateOutputFileName("Scan"),
                    )
                    if (OpenPdfEditor.addWatermark(context, pdfUri, out)) {
                        Uri.fromFile(out)
                    } else {
                        // Never hand a free user the clean, un-watermarked file when
                        // watermarking fails — that's an entitlement bypass. Fail instead.
                        _uiState.update {
                            it.copy(
                                isProcessing = false,
                                error = "Couldn't finish processing the scan. Please try again.",
                            )
                        }
                        return@launch
                    }
                } else {
                    pdfUri
                }

                val fileName = FileUtils.getFileName(context, savedUri)
                val fileSize = FileUtils.getFileSize(context, savedUri)
                val pageCount = PdfUtils.getPageCount(context, savedUri)

                analytics.track(AnalyticsEvent.ScanCompleted(pageCount = pageCount, watermarked = watermarked))

                // Save to database
                val pdfEntity = PdfEntity(
                    uri = savedUri.toString(),
                    name = fileName,
                    path = savedUri.path ?: "",
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

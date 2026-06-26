package com.pdfmaster.app.presentation.ui.tools.compress

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdfmaster.app.analytics.Analytics
import com.pdfmaster.app.analytics.AnalyticsEvent
import com.pdfmaster.app.analytics.Tool
import com.pdfmaster.app.billing.DailyLimitedFeature
import com.pdfmaster.app.billing.FeatureGate
import com.pdfmaster.app.billing.GateResult
import com.pdfmaster.app.billing.PremiumPrompt
import com.pdfmaster.app.domain.repository.CompressionQuality
import com.pdfmaster.app.util.FileUtils
import com.pdfmaster.app.util.PdfUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
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
    val error: String? = null,
    val premiumPrompt: PremiumPrompt? = null
)

@HiltViewModel
class CompressViewModel @Inject constructor(
    private val featureGate: FeatureGate,
    private val analytics: Analytics,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CompressUiState())
    val uiState: StateFlow<CompressUiState> = _uiState.asStateFlow()

    init {
        analytics.track(AnalyticsEvent.ToolOpened(Tool.COMPRESS))
    }

    val isPremium: StateFlow<Boolean> = featureGate.isPremium

    /** Free compressions left today (large number for premium users). */
    val remainingToday: StateFlow<Int> =
        featureGate.remainingDaily(DailyLimitedFeature.COMPRESS)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                DailyLimitedFeature.COMPRESS.freeDailyLimit,
            )

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

            // Free tier: limited compressions per day.
            val gate = featureGate.consumeDaily(DailyLimitedFeature.COMPRESS)
            if (gate is GateResult.Blocked) {
                _uiState.update { it.copy(premiumPrompt = gate.prompt) }
                return@launch
            }

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
                    analytics.track(
                        AnalyticsEvent.ToolCompleted(
                            Tool.COMPRESS,
                            mapOf(
                                "original_size" to _uiState.value.originalSize,
                                "compressed_size" to compressedSize,
                                "quality" to _uiState.value.selectedQuality.name,
                            ),
                        )
                    )
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
                analytics.track(AnalyticsEvent.ToolFailed(Tool.COMPRESS, e.message))
                _uiState.update {
                    it.copy(isCompressing = false, error = "Failed to compress: ${e.message}")
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearPremiumPrompt() {
        _uiState.update { it.copy(premiumPrompt = null) }
    }

    fun reset() {
        _uiState.update { CompressUiState() }
    }
}

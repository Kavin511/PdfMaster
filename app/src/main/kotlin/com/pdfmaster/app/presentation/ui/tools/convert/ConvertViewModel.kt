package com.pdfmaster.app.presentation.ui.tools.convert

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdfmaster.app.analytics.Analytics
import com.pdfmaster.app.analytics.AnalyticsEvent
import com.pdfmaster.app.analytics.Param
import com.pdfmaster.app.analytics.Tool
import com.pdfmaster.app.billing.FeatureGate
import com.pdfmaster.app.billing.FreeTierLimits
import com.pdfmaster.app.billing.GateResult
import com.pdfmaster.app.billing.PremiumPrompt
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

data class ConvertUiState(
    val selectedImages: List<Uri> = emptyList(),
    val isConverting: Boolean = false,
    val outputUri: String? = null,
    val error: String? = null,
    val premiumPrompt: PremiumPrompt? = null,
)

@HiltViewModel
class ConvertViewModel @Inject constructor(
    private val featureGate: FeatureGate,
    private val analytics: Analytics,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConvertUiState())
    val uiState: StateFlow<ConvertUiState> = _uiState.asStateFlow()

    init {
        analytics.track(AnalyticsEvent.ToolOpened(Tool.IMAGES_TO_PDF))
    }

    /** Whether the user is premium, for the free-tier hint in the UI. */
    val isPremium: StateFlow<Boolean> = featureGate.isPremium

    /** Free-tier per-PDF image cap, exposed so the UI can show the limit. */
    val freeImageLimit: Int = FreeTierLimits.IMAGES_TO_PDF_MAX

    fun setImages(uris: List<Uri>) {
        _uiState.update { it.copy(selectedImages = uris, error = null) }
    }

    fun addImages(uris: List<Uri>) {
        _uiState.update { it.copy(selectedImages = it.selectedImages + uris, error = null) }
    }

    fun removeImage(index: Int) {
        _uiState.update {
            it.copy(selectedImages = it.selectedImages.filterIndexed { i, _ -> i != index })
        }
    }

    fun convert(context: Context, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            val images = _uiState.value.selectedImages
            if (images.isEmpty()) return@launch

            // Free tier: cap the number of images per PDF.
            val gate = featureGate.requireCount(
                count = images.size,
                freeLimit = FreeTierLimits.IMAGES_TO_PDF_MAX,
                noun = "images",
            )
            if (gate is GateResult.Blocked) {
                _uiState.update { it.copy(premiumPrompt = gate.prompt) }
                return@launch
            }

            _uiState.update { it.copy(isConverting = true, error = null) }
            try {
                val outputDir = FileUtils.getOutputDirectory(context)
                val outputFile = File(outputDir, FileUtils.generateOutputFileName("Images_to_PDF"))

                val success = PdfUtils.imagesToPdf(context, images, outputFile)
                if (success) {
                    analytics.track(
                        AnalyticsEvent.ToolCompleted(
                            Tool.IMAGES_TO_PDF,
                            mapOf(Param.IMAGE_COUNT to images.size),
                        )
                    )
                    val outputUri = Uri.fromFile(outputFile).toString()
                    _uiState.update { it.copy(isConverting = false, outputUri = outputUri) }
                    onComplete(outputUri)
                } else {
                    analytics.track(AnalyticsEvent.ToolFailed(Tool.IMAGES_TO_PDF, "conversion_failed"))
                    _uiState.update {
                        it.copy(isConverting = false, error = "Failed to convert images to PDF")
                    }
                }
            } catch (e: Exception) {
                analytics.track(AnalyticsEvent.ToolFailed(Tool.IMAGES_TO_PDF, e.message))
                _uiState.update { it.copy(isConverting = false, error = "Error: ${e.message}") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearPremiumPrompt() {
        _uiState.update { it.copy(premiumPrompt = null) }
    }
}

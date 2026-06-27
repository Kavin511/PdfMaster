package com.pdfmaster.app.presentation.ui.form

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdfmaster.app.billing.FeatureGate
import com.pdfmaster.app.billing.GateResult
import com.pdfmaster.app.billing.PremiumFeature
import com.pdfmaster.app.billing.PremiumPrompt
import com.pdfmaster.app.domain.model.FormField
import com.pdfmaster.app.util.FileUtils
import com.pdfmaster.app.util.OpenPdfEditor
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
import java.util.UUID
import javax.inject.Inject
import kotlin.math.roundToInt

data class FormFillingUiState(
    val uri: Uri? = null,
    val fileName: String = "",
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val pages: List<Bitmap?> = emptyList(),
    val formFields: List<FormField> = emptyList(),
    val selectedField: FormField? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val isAddingField: Boolean = false,
    val addingFieldType: FieldType = FieldType.TEXT,
    val premiumPrompt: PremiumPrompt? = null
)

enum class FieldType {
    TEXT, CHECKBOX, DATE, SIGNATURE
}

@HiltViewModel
class FormFillingViewModel @Inject constructor(
    private val featureGate: FeatureGate,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FormFillingUiState())
    val uiState: StateFlow<FormFillingUiState> = _uiState.asStateFlow()

    fun clearPremiumPrompt() {
        _uiState.update { it.copy(premiumPrompt = null) }
    }

    fun loadPdf(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, uri = uri) }

            try {
                val fileName = FileUtils.getFileName(context, uri)
                val pageCount = PdfUtils.getPageCount(context, uri)

                if (pageCount == 0) {
                    _uiState.update { it.copy(isLoading = false, error = "Could not open PDF") }
                    return@launch
                }

                _uiState.update {
                    it.copy(
                        fileName = fileName,
                        pageCount = pageCount,
                        pages = List(pageCount) { null },
                        isLoading = false
                    )
                }

                loadPagesAsync(context, uri, pageCount)
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun loadPagesAsync(context: Context, uri: Uri, pageCount: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val width = context.resources.displayMetrics.widthPixels

            for (i in 0 until pageCount) {
                val bitmap = PdfUtils.renderPage(context, uri, i, width, 100)

                withContext(Dispatchers.Main) {
                    _uiState.update { state ->
                        val pages = state.pages.toMutableList()
                        if (i < pages.size) pages[i] = bitmap
                        state.copy(pages = pages)
                    }
                }
            }
        }
    }

    fun setCurrentPage(page: Int) {
        _uiState.update { it.copy(currentPage = page) }
    }

    fun startAddingField(type: FieldType) {
        _uiState.update { it.copy(isAddingField = true, addingFieldType = type) }
    }

    fun cancelAddingField() {
        _uiState.update { it.copy(isAddingField = false) }
    }

    fun addFieldAtPosition(position: Offset, pageWidth: Float, pageHeight: Float) {
        val currentPage = _uiState.value.currentPage
        val fieldType = _uiState.value.addingFieldType

        // Create a reasonable sized field based on type
        val fieldWidth = when (fieldType) {
            FieldType.TEXT -> 200f
            FieldType.CHECKBOX -> 24f
            FieldType.DATE -> 150f
            FieldType.SIGNATURE -> 200f
        }
        val fieldHeight = when (fieldType) {
            FieldType.TEXT -> 40f
            FieldType.CHECKBOX -> 24f
            FieldType.DATE -> 40f
            FieldType.SIGNATURE -> 80f
        }

        val bounds = Rect(
            left = position.x,
            top = position.y,
            right = (position.x + fieldWidth).coerceAtMost(pageWidth),
            bottom = (position.y + fieldHeight).coerceAtMost(pageHeight)
        )

        val newField: FormField = when (fieldType) {
            FieldType.TEXT -> FormField.TextField(
                pageIndex = currentPage,
                bounds = bounds,
                placeholder = "Enter text..."
            )
            FieldType.CHECKBOX -> FormField.CheckBox(
                pageIndex = currentPage,
                bounds = bounds
            )
            FieldType.DATE -> FormField.DateField(
                pageIndex = currentPage,
                bounds = bounds
            )
            FieldType.SIGNATURE -> FormField.Signature(
                pageIndex = currentPage,
                bounds = bounds
            )
        }

        _uiState.update { state ->
            state.copy(
                formFields = state.formFields + newField,
                isAddingField = false,
                selectedField = newField
            )
        }
    }

    fun selectField(field: FormField?) {
        _uiState.update { it.copy(selectedField = field) }
    }

    fun updateFieldValue(fieldId: String, value: String) {
        _uiState.update { state ->
            val updatedFields = state.formFields.map { field ->
                if (field.id == fieldId) {
                    when (field) {
                        is FormField.TextField -> field.copy(value = value)
                        is FormField.CheckBox -> field.copy(
                            value = value,
                            isChecked = value == "true"
                        )
                        is FormField.DateField -> field.copy(value = value)
                        is FormField.Signature -> field.copy(value = value)
                        is FormField.RadioButton -> field.copy(value = value)
                        is FormField.Dropdown -> field.copy(value = value)
                    }
                } else field
            }
            state.copy(formFields = updatedFields)
        }
    }

    fun toggleCheckbox(fieldId: String) {
        _uiState.update { state ->
            val updatedFields = state.formFields.map { field ->
                if (field.id == fieldId && field is FormField.CheckBox) {
                    field.copy(isChecked = !field.isChecked, value = (!field.isChecked).toString())
                } else field
            }
            state.copy(formFields = updatedFields)
        }
    }

    fun deleteField(fieldId: String) {
        _uiState.update { state ->
            state.copy(
                formFields = state.formFields.filter { it.id != fieldId },
                selectedField = if (state.selectedField?.id == fieldId) null else state.selectedField
            )
        }
    }

    fun moveField(fieldId: String, newPosition: Offset) {
        _uiState.update { state ->
            val updatedFields = state.formFields.map { field ->
                if (field.id == fieldId) {
                    val width = field.bounds.width
                    val height = field.bounds.height
                    val newBounds = Rect(
                        left = newPosition.x,
                        top = newPosition.y,
                        right = newPosition.x + width,
                        bottom = newPosition.y + height
                    )
                    when (field) {
                        is FormField.TextField -> field.copy(bounds = newBounds)
                        is FormField.CheckBox -> field.copy(bounds = newBounds)
                        is FormField.DateField -> field.copy(bounds = newBounds)
                        is FormField.Signature -> field.copy(bounds = newBounds)
                        is FormField.RadioButton -> field.copy(bounds = newBounds)
                        is FormField.Dropdown -> field.copy(bounds = newBounds)
                    }
                } else field
            }
            state.copy(formFields = updatedFields)
        }
    }

    fun savePdf(context: Context, onComplete: (Uri?) -> Unit) {
        val sourceUri = _uiState.value.uri ?: return

        // Saving a filled form is premium-only.
        when (val gate = featureGate.require(PremiumFeature.FORM_FILLING)) {
            is GateResult.Allowed -> Unit
            is GateResult.Blocked -> {
                _uiState.update { it.copy(premiumPrompt = gate.prompt) }
                return
            }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }

            try {
                val outputDir = FileUtils.getOutputDirectory(context)
                val outputFile = File(outputDir, FileUtils.generateOutputFileName("FormFilled"))

                val success = withContext(Dispatchers.IO) {
                    renderFormFieldsToPdf(context, sourceUri, outputFile)
                }

                if (success) {
                    _uiState.update { it.copy(isSaving = false) }
                    onComplete(outputFile.toUri())
                } else {
                    _uiState.update { it.copy(isSaving = false, error = "Failed to save form") }
                    onComplete(null)
                }

            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message) }
                onComplete(null)
            }
        }
    }

    private suspend fun renderFormFieldsToPdf(context: Context, sourceUri: Uri, outputFile: File): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val formFields = _uiState.value.formFields
                if (formFields.isEmpty()) return@withContext false
                val width = context.resources.displayMetrics.widthPixels

                // Render filled fields onto a TRANSPARENT overlay per page, then stamp onto the
                // ORIGINAL pdf via OpenPdfEditor (PDFBox) so the page's vector text is preserved
                // instead of being flattened to an image.
                val overlays = mutableListOf<OpenPdfEditor.ImageOverlay>()
                val bitmaps = mutableListOf<Bitmap>()
                try {
                    formFields.map { it.pageIndex }.distinct().sorted().forEach { pageIndex ->
                        val dims = OpenPdfEditor.getPageDimensions(context, sourceUri, pageIndex)
                            ?: return@forEach
                        val (pdfW, pdfH) = dims
                        val bmpH = (width * pdfH / pdfW).roundToInt().coerceAtLeast(1)
                        val overlay = Bitmap.createBitmap(width, bmpH, Bitmap.Config.ARGB_8888)
                        bitmaps += overlay
                        val canvas = Canvas(overlay) // transparent background
                        formFields.filter { it.pageIndex == pageIndex }
                            .forEach { drawFormField(canvas, it) }
                        overlays += OpenPdfEditor.ImageOverlay(
                            pageIndex = pageIndex,
                            x = 0f, y = 0f, width = pdfW, height = pdfH,
                            bitmap = overlay,
                        )
                    }
                    if (overlays.isEmpty()) return@withContext false
                    OpenPdfEditor.applyEdits(context, sourceUri, outputFile, images = overlays)
                } finally {
                    bitmaps.forEach { it.recycle() }
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun drawFormField(canvas: Canvas, field: FormField) {
        val paint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
        }

        when (field) {
            is FormField.TextField -> {
                if (field.value.isNotEmpty()) {
                    paint.textSize = 32f
                    canvas.drawText(
                        field.value,
                        field.bounds.left + 8f,
                        field.bounds.top + field.bounds.height * 0.7f,
                        paint
                    )
                }
            }
            is FormField.CheckBox -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f
                val rect = RectF(
                    field.bounds.left,
                    field.bounds.top,
                    field.bounds.right,
                    field.bounds.bottom
                )
                canvas.drawRect(rect, paint)

                if (field.isChecked) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 3f
                    // Draw checkmark
                    val startX = field.bounds.left + field.bounds.width * 0.2f
                    val startY = field.bounds.top + field.bounds.height * 0.5f
                    val midX = field.bounds.left + field.bounds.width * 0.4f
                    val midY = field.bounds.top + field.bounds.height * 0.8f
                    val endX = field.bounds.left + field.bounds.width * 0.85f
                    val endY = field.bounds.top + field.bounds.height * 0.2f
                    canvas.drawLine(startX, startY, midX, midY, paint)
                    canvas.drawLine(midX, midY, endX, endY, paint)
                }
            }
            is FormField.DateField -> {
                if (field.value.isNotEmpty()) {
                    paint.textSize = 28f
                    canvas.drawText(
                        field.value,
                        field.bounds.left + 8f,
                        field.bounds.top + field.bounds.height * 0.7f,
                        paint
                    )
                }
            }
            is FormField.Signature -> {
                if (field.value.isNotEmpty()) {
                    paint.textSize = 24f
                    paint.color = Color.BLUE
                    canvas.drawText(
                        "Signed",
                        field.bounds.left + 8f,
                        field.bounds.top + field.bounds.height * 0.6f,
                        paint
                    )
                }
            }
            else -> {}
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

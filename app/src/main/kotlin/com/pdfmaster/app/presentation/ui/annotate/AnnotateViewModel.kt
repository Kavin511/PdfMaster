package com.pdfmaster.app.presentation.ui.annotate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdfmaster.app.billing.FeatureGate
import com.pdfmaster.app.billing.GateResult
import com.pdfmaster.app.billing.PremiumFeature
import com.pdfmaster.app.billing.PremiumPrompt
import com.pdfmaster.app.domain.model.AnnotationTool
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
import javax.inject.Inject
import kotlin.math.roundToInt

data class AnnotationData(
    val pageIndex: Int,
    val tool: AnnotationTool,
    val color: Color,
    val points: List<Offset> = emptyList(),
    val rect: RectF? = null,
    val text: String? = null
)

data class AnnotateUiState(
    val uri: Uri? = null,
    val pageCount: Int = 0,
    val pages: List<Bitmap?> = emptyList(),
    val selectedTool: AnnotationTool = AnnotationTool.NONE,
    val selectedColor: Color = Color(0xFFFEF08A),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val canUndo: Boolean = false,
    val drawingPaths: Map<Int, List<Offset>> = emptyMap(),
    val annotations: List<AnnotationData> = emptyList(),
    val outputUri: String? = null,
    val error: String? = null,
    val premiumPrompt: PremiumPrompt? = null
)

@HiltViewModel
class AnnotateViewModel @Inject constructor(
    private val featureGate: FeatureGate,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AnnotateUiState())
    val uiState: StateFlow<AnnotateUiState> = _uiState.asStateFlow()

    private val undoStack = mutableListOf<AnnotationData>()

    fun clearPremiumPrompt() {
        _uiState.update { it.copy(premiumPrompt = null) }
    }

    fun loadPdf(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, uri = uri) }
            val pageCount = PdfUtils.getPageCount(context, uri)
            _uiState.update { it.copy(pageCount = pageCount, pages = List(pageCount) { null }, isLoading = false) }
            loadPagesAsync(context, uri, pageCount)
        }
    }

    private fun loadPagesAsync(context: Context, uri: Uri, pageCount: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val width = context.resources.displayMetrics.widthPixels
            for (i in 0 until pageCount) {
                val bitmap = PdfUtils.renderPage(context, uri, i, width)
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

    fun selectTool(tool: AnnotationTool) {
        _uiState.update { it.copy(selectedTool = if (it.selectedTool == tool) AnnotationTool.NONE else tool) }
    }

    fun selectColor(color: Color) {
        _uiState.update { it.copy(selectedColor = color) }
    }

    fun addDrawingPoint(pageIndex: Int, point: Offset) {
        _uiState.update { state ->
            val paths = state.drawingPaths.toMutableMap()
            val currentPath = paths[pageIndex]?.toMutableList() ?: mutableListOf()
            currentPath.add(point)
            paths[pageIndex] = currentPath
            state.copy(drawingPaths = paths, canUndo = true)
        }
    }

    fun finishStroke(pageIndex: Int) {
        val state = _uiState.value
        val points = state.drawingPaths[pageIndex] ?: return

        if (points.isNotEmpty()) {
            val annotation = AnnotationData(
                pageIndex = pageIndex,
                tool = state.selectedTool,
                color = state.selectedColor,
                points = points.toList()
            )
            undoStack.add(annotation)
            _uiState.update {
                it.copy(
                    annotations = it.annotations + annotation,
                    drawingPaths = it.drawingPaths - pageIndex,
                    canUndo = true
                )
            }
        }
    }

    fun addHighlight(pageIndex: Int, rect: RectF) {
        val annotation = AnnotationData(
            pageIndex = pageIndex,
            tool = AnnotationTool.HIGHLIGHT,
            color = _uiState.value.selectedColor,
            rect = rect
        )
        undoStack.add(annotation)
        _uiState.update {
            it.copy(annotations = it.annotations + annotation, canUndo = true)
        }
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val lastAnnotation = undoStack.removeLastOrNull()
            _uiState.update { state ->
                state.copy(
                    annotations = state.annotations.dropLast(1),
                    canUndo = undoStack.isNotEmpty()
                )
            }
        }
    }

    fun save(context: Context, onComplete: ((String) -> Unit)? = null) {
        // Saving annotations into the PDF is premium-only.
        when (val gate = featureGate.require(PremiumFeature.ANNOTATE)) {
            is GateResult.Allowed -> Unit
            is GateResult.Blocked -> {
                _uiState.update { it.copy(premiumPrompt = gate.prompt) }
                return
            }
        }

        viewModelScope.launch {
            val sourceUri = _uiState.value.uri ?: return@launch
            _uiState.update { it.copy(isSaving = true, error = null) }

            try {
                val outputDir = FileUtils.getOutputDirectory(context)
                val outputFile = File(outputDir, FileUtils.generateOutputFileName("Annotated"))

                val success = withContext(Dispatchers.IO) {
                    renderAnnotatedPdf(context, sourceUri, outputFile)
                }

                if (success) {
                    val outputUri = Uri.fromFile(outputFile).toString()
                    _uiState.update { it.copy(isSaving = false, outputUri = outputUri) }
                    onComplete?.invoke(outputUri)
                } else {
                    _uiState.update { it.copy(isSaving = false, error = "Failed to save") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = "Error: ${e.message}") }
            }
        }
    }

    private suspend fun renderAnnotatedPdf(context: Context, sourceUri: Uri, outputFile: File): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val annotations = _uiState.value.annotations
                if (annotations.isEmpty()) return@withContext false
                val width = context.resources.displayMetrics.widthPixels

                // Draw annotations onto a TRANSPARENT overlay bitmap per annotated page (same
                // pixel space the gestures were captured in), then stamp those overlays onto the
                // ORIGINAL pdf via OpenPdfEditor (PDFBox). This preserves the page's vector text
                // instead of flattening every page to an image.
                val overlays = mutableListOf<OpenPdfEditor.ImageOverlay>()
                val bitmaps = mutableListOf<Bitmap>()
                try {
                    annotations.map { it.pageIndex }.distinct().sorted().forEach { pageIndex ->
                        val dims = OpenPdfEditor.getPageDimensions(context, sourceUri, pageIndex)
                            ?: return@forEach
                        val (pdfW, pdfH) = dims
                        val bmpH = (width * pdfH / pdfW).roundToInt().coerceAtLeast(1)
                        val overlay = Bitmap.createBitmap(width, bmpH, Bitmap.Config.ARGB_8888)
                        bitmaps += overlay
                        val canvas = Canvas(overlay) // transparent background
                        annotations.filter { it.pageIndex == pageIndex }
                            .forEach { drawAnnotation(canvas, it) }
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

    private fun drawAnnotation(canvas: Canvas, annotation: AnnotationData) {
        val paint = Paint().apply {
            color = annotation.color.toArgb()
            isAntiAlias = true
        }

        when (annotation.tool) {
            AnnotationTool.HIGHLIGHT -> {
                annotation.rect?.let {
                    paint.alpha = 100
                    paint.style = Paint.Style.FILL
                    canvas.drawRect(it, paint)
                }
            }
            AnnotationTool.UNDERLINE -> {
                annotation.rect?.let {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 3f
                    canvas.drawLine(it.left, it.bottom, it.right, it.bottom, paint)
                }
            }
            AnnotationTool.STRIKETHROUGH -> {
                annotation.rect?.let {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 2f
                    val centerY = (it.top + it.bottom) / 2
                    canvas.drawLine(it.left, centerY, it.right, centerY, paint)
                }
            }
            AnnotationTool.FREEHAND -> {
                if (annotation.points.size > 1) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 5f
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.strokeJoin = Paint.Join.ROUND

                    val path = Path()
                    path.moveTo(annotation.points.first().x, annotation.points.first().y)
                    annotation.points.drop(1).forEach { path.lineTo(it.x, it.y) }
                    canvas.drawPath(path, paint)
                }
            }
            AnnotationTool.TEXT -> {
                annotation.text?.let { text ->
                    paint.textSize = 40f
                    annotation.points.firstOrNull()?.let { pos ->
                        canvas.drawText(text, pos.x, pos.y, paint)
                    }
                }
            }
            AnnotationTool.SHAPE_RECTANGLE -> {
                annotation.rect?.let {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 3f
                    canvas.drawRect(it, paint)
                }
            }
            else -> {}
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

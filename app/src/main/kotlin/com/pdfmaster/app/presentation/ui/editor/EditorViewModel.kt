package com.pdfmaster.app.presentation.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdfmaster.app.analytics.Analytics
import com.pdfmaster.app.analytics.AnalyticsEvent
import com.pdfmaster.app.billing.FeatureGate
import com.pdfmaster.app.billing.GateResult
import com.pdfmaster.app.billing.PremiumFeature
import com.pdfmaster.app.billing.PremiumPrompt
import com.pdfmaster.app.util.ExtractedTextBlock
import com.pdfmaster.app.util.FileUtils
import com.pdfmaster.app.util.MuPdfTextExtractor
import com.pdfmaster.app.util.OcrUtils
import com.pdfmaster.app.util.OpenPdfEditor
import com.pdfmaster.app.util.PdfUtils
import com.pdfmaster.app.util.PdfValidationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject

// Editor tools
enum class EditorTool {
    SELECT,
    TEXT,
    DRAW,
    WHITEOUT,
    HIGHLIGHT,
    IMAGE,
    SIGNATURE
}

// Element types that can be added to PDF
sealed class PdfElement {
    abstract val id: String
    abstract val pageIndex: Int
    abstract var x: Float
    abstract var y: Float

    data class TextElement(
        override val id: String = UUID.randomUUID().toString(),
        override val pageIndex: Int,
        override var x: Float,
        override var y: Float,
        var text: String,
        var fontSize: Float = 16f,
        var color: Int = android.graphics.Color.BLACK,
        var fontBold: Boolean = false,
        var fontItalic: Boolean = false,
        val isFromPdf: Boolean = false,  // True if editing existing PDF text
        val originalBlockId: String? = null  // Reference to original ExtractedTextBlock
    ) : PdfElement()

    data class ImageElement(
        override val id: String = UUID.randomUUID().toString(),
        override val pageIndex: Int,
        override var x: Float,
        override var y: Float,
        var width: Float,
        var height: Float,
        var bitmap: Bitmap
    ) : PdfElement()

    data class DrawingElement(
        override val id: String = UUID.randomUUID().toString(),
        override val pageIndex: Int,
        override var x: Float = 0f,
        override var y: Float = 0f,
        var points: List<Offset>,
        var strokeWidth: Float = 3f,
        var color: Int = android.graphics.Color.BLACK
    ) : PdfElement()

    data class HighlightElement(
        override val id: String = UUID.randomUUID().toString(),
        override val pageIndex: Int,
        override var x: Float,
        override var y: Float,
        var width: Float,
        var height: Float,
        var color: Int = android.graphics.Color.YELLOW
    ) : PdfElement()

    data class WhiteoutElement(
        override val id: String = UUID.randomUUID().toString(),
        override val pageIndex: Int,
        override var x: Float,
        override var y: Float,
        var width: Float,
        var height: Float
    ) : PdfElement()
}

data class EditorUiState(
    val uri: Uri? = null,
    val fileName: String = "",
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val pageBitmap: Bitmap? = null,
    val pageDimensions: Pair<Int, Int>? = null,
    val bitmapScale: Float = 1f,  // Scale factor from PDF coords to bitmap coords
    val elements: List<PdfElement> = emptyList(),
    val extractedTextBlocks: List<ExtractedTextBlock> = emptyList(),  // Text from PDF
    val selectedElementId: String? = null,
    val selectedTextBlockId: String? = null,  // Selected existing PDF text
    val currentTool: EditorTool = EditorTool.SELECT,
    val currentColor: Color = Color.Black,
    val currentFontSize: Float = 16f,
    val currentStrokeWidth: Float = 3f,
    val isLoading: Boolean = true,
    val isExtracting: Boolean = false,
    val isSaving: Boolean = false,
    val hasChanges: Boolean = false,
    val saveSuccess: Boolean = false,
    val savedFilePath: String? = null,
    val error: String? = null,
    // Drawing state
    val isDrawing: Boolean = false,
    val currentDrawingPoints: List<Offset> = emptyList(),
    // Rectangle drag state (for whiteout/highlight)
    val isDraggingRect: Boolean = false,
    val rectStartPoint: Offset? = null,
    val rectEndPoint: Offset? = null,
    // Premium gating
    val premiumPrompt: PremiumPrompt? = null
)

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val featureGate: FeatureGate,
    private val analytics: Analytics,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    /**
     * Text editing is premium. Returns true if allowed; otherwise raises the paywall prompt.
     * Gated at every text mutation entry point so free users can still view/annotate but
     * can't add, edit, or delete PDF text.
     */
    private fun ensureTextEditingAllowed(): Boolean {
        return when (val gate = featureGate.require(PremiumFeature.TEXT_EDITING)) {
            is GateResult.Allowed -> {
                analytics.track(AnalyticsEvent.EditorTextEdited)
                true
            }
            is GateResult.Blocked -> {
                _uiState.update { it.copy(premiumPrompt = gate.prompt) }
                false
            }
        }
    }

    fun clearPremiumPrompt() {
        _uiState.update { it.copy(premiumPrompt = null) }
    }

    private val undoStack = mutableListOf<List<PdfElement>>()
    private val redoStack = mutableListOf<List<PdfElement>>()

    // Store elements per page
    private val allPagesElements = mutableMapOf<Int, MutableList<PdfElement>>()

    // Store extracted text blocks per page
    private val allPagesExtractedText = mutableMapOf<Int, List<ExtractedTextBlock>>()

    // Track which original text blocks have been modified/deleted
    private val modifiedTextBlockIds = mutableSetOf<String>()

    fun loadPdf(context: Context, uri: Uri, title: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val fileName = title ?: FileUtils.getFileName(context, uri)

                // Validate PDF first
                when (val validation = PdfUtils.validatePdf(context, uri)) {
                    is PdfValidationResult.Valid -> {
                        _uiState.update {
                            it.copy(
                                uri = uri,
                                fileName = fileName,
                                pageCount = validation.pageCount,
                                isLoading = false
                            )
                        }
                        // Load first page
                        loadPage(context, 0)
                    }
                    is PdfValidationResult.Encrypted -> {
                        _uiState.update {
                            it.copy(isLoading = false, error = validation.message)
                        }
                    }
                    is PdfValidationResult.Corrupted -> {
                        _uiState.update {
                            it.copy(isLoading = false, error = validation.message)
                        }
                    }
                    is PdfValidationResult.TooLarge -> {
                        _uiState.update {
                            it.copy(isLoading = false, error = validation.message)
                        }
                    }
                    is PdfValidationResult.Error -> {
                        _uiState.update {
                            it.copy(isLoading = false, error = validation.message)
                        }
                    }
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to load PDF: ${e.message}")
                }
            }
        }
    }

    fun loadPage(context: Context, pageIndex: Int) {
        viewModelScope.launch {
            val uri = _uiState.value.uri ?: return@launch

            _uiState.update { it.copy(isLoading = true, currentPage = pageIndex) }

            try {
                val screenWidth = context.resources.displayMetrics.widthPixels
                val bitmap = withContext(Dispatchers.IO) {
                    PdfUtils.renderPage(context, uri, pageIndex, screenWidth)
                }
                val dimensions = withContext(Dispatchers.IO) {
                    PdfUtils.getPageDimensions(context, uri, pageIndex)
                }

                // Calculate scale factor
                val scale = if (dimensions != null && bitmap != null) {
                    bitmap.width.toFloat() / dimensions.first
                } else 1f

                // Get elements for this page
                val pageElements = allPagesElements.getOrPut(pageIndex) { mutableListOf() }

                _uiState.update {
                    it.copy(
                        pageBitmap = bitmap,
                        pageDimensions = dimensions,
                        bitmapScale = scale,
                        elements = pageElements.toList(),
                        selectedElementId = null,
                        selectedTextBlockId = null,
                        isLoading = false
                    )
                }

                // Extract text blocks in background
                extractTextBlocks(context, uri, pageIndex)

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to load page: ${e.message}")
                }
            }
        }
    }

    private fun extractTextBlocks(context: Context, uri: Uri, pageIndex: Int) {
        viewModelScope.launch {
            // Check if already extracted
            if (allPagesExtractedText.containsKey(pageIndex)) {
                _uiState.update {
                    it.copy(extractedTextBlocks = allPagesExtractedText[pageIndex] ?: emptyList())
                }
                return@launch
            }

            _uiState.update { it.copy(isExtracting = true) }

            try {
                // Use MuPDF for accurate text extraction (much better than PDFBox)
                var textBlocks = withContext(Dispatchers.IO) {
                    MuPdfTextExtractor.extractTextBlocks(context, uri, pageIndex)
                }

                // If MuPDF didn't find text, fall back to OCR (for scanned PDFs).
                // OCR is a premium feature, so gate it before running.
                if (textBlocks.isEmpty()) {
                    when (val gate = featureGate.require(PremiumFeature.OCR)) {
                        is GateResult.Allowed -> {
                            textBlocks = withContext(Dispatchers.IO) {
                                OcrUtils.extractTextWithOcr(context, uri, pageIndex)
                            }
                        }
                        is GateResult.Blocked -> {
                            _uiState.update {
                                it.copy(isExtracting = false, premiumPrompt = gate.prompt)
                            }
                            return@launch
                        }
                    }
                }

                allPagesExtractedText[pageIndex] = textBlocks

                _uiState.update {
                    it.copy(
                        extractedTextBlocks = textBlocks,
                        isExtracting = false
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(isExtracting = false) }
            }
        }
    }

    fun setTool(tool: EditorTool) {
        _uiState.update {
            it.copy(
                currentTool = tool,
                selectedElementId = null,
                selectedTextBlockId = null
            )
        }
    }

    fun setColor(color: Color) {
        _uiState.update { it.copy(currentColor = color) }
    }

    fun setFontSize(size: Float) {
        _uiState.update { it.copy(currentFontSize = size) }
    }

    fun setStrokeWidth(width: Float) {
        _uiState.update { it.copy(currentStrokeWidth = width) }
    }

    /**
     * Handle tap on canvas - check if tapping on existing PDF text
     * Returns the tapped text block if found, null otherwise
     */
    fun findTextBlockAtPosition(x: Float, y: Float): ExtractedTextBlock? {
        val scale = _uiState.value.bitmapScale
        val textBlocks = _uiState.value.extractedTextBlocks

        // Convert tap position to PDF coordinates
        val pdfX = x / scale
        val pdfY = y / scale

        // Find text block at position with generous tolerance for better UX
        // Sort by area (smallest first) to prefer more precise matches
        val tolerance = 15f
        return textBlocks
            .filter { block ->
                // Skip if already modified/deleted
                !modifiedTextBlockIds.contains(block.id) &&
                pdfX >= block.x - tolerance &&
                pdfX <= block.x + block.width + tolerance &&
                pdfY >= block.y - tolerance &&
                pdfY <= block.y + block.height + tolerance
            }
            .minByOrNull { it.width * it.height }  // Prefer smaller/more precise matches
    }

    /**
     * Select an existing PDF text block for editing
     */
    fun selectTextBlock(blockId: String?) {
        _uiState.update {
            it.copy(
                selectedTextBlockId = blockId,
                selectedElementId = null
            )
        }
    }

    /**
     * Edit existing PDF text - marks as modified and creates replacement text
     * Whiteout is only applied during save, not during live editing for seamless UX
     */
    fun editExistingText(blockId: String, newText: String) {
        if (!ensureTextEditingAllowed()) return
        val textBlock = _uiState.value.extractedTextBlocks.find { it.id == blockId } ?: return

        if (newText.isBlank()) {
            // Delete the text block
            deleteExistingText(blockId)
            return
        }

        // If text hasn't changed, do nothing
        if (newText == textBlock.text) {
            return
        }

        saveToUndoStack()

        // Mark original as modified (whiteout will be applied during save)
        modifiedTextBlockIds.add(blockId)

        val scale = _uiState.value.bitmapScale
        val pageIndex = _uiState.value.currentPage

        // Create replacement text element with same properties
        // NO whiteout during editing - it will be applied when saving
        val element = PdfElement.TextElement(
            pageIndex = pageIndex,
            x = textBlock.x * scale,
            y = textBlock.y * scale,
            text = newText,
            fontSize = textBlock.fontSize * scale,
            color = textBlock.fontColor,
            fontBold = textBlock.isBold,
            fontItalic = textBlock.isItalic,
            isFromPdf = true,
            originalBlockId = blockId
        )

        allPagesElements.getOrPut(pageIndex) { mutableListOf() }.add(element)

        _uiState.update {
            it.copy(
                elements = allPagesElements[pageIndex]?.toList() ?: emptyList(),
                selectedElementId = element.id,
                selectedTextBlockId = null,
                hasChanges = true
            )
        }
    }

    /**
     * Delete existing PDF text
     */
    fun deleteExistingText(blockId: String) {
        if (!ensureTextEditingAllowed()) return
        val textBlock = _uiState.value.extractedTextBlocks.find { it.id == blockId } ?: return

        saveToUndoStack()

        // Mark as modified (deleted)
        modifiedTextBlockIds.add(blockId)

        // Add whiteout element to cover the text
        val element = PdfElement.WhiteoutElement(
            pageIndex = _uiState.value.currentPage,
            x = textBlock.x * _uiState.value.bitmapScale,
            y = textBlock.y * _uiState.value.bitmapScale,
            width = textBlock.width * _uiState.value.bitmapScale,
            height = textBlock.height * _uiState.value.bitmapScale
        )

        addElement(element)
        _uiState.update { it.copy(selectedTextBlockId = null, hasChanges = true) }
    }

    /**
     * Check if a text block has been modified
     */
    fun isTextBlockModified(blockId: String): Boolean {
        return modifiedTextBlockIds.contains(blockId)
    }

    /**
     * Add new text element - matches font of nearby text if possible
     */
    fun addTextElement(x: Float, y: Float, text: String) {
        if (text.isBlank()) return
        if (!ensureTextEditingAllowed()) return
        saveToUndoStack()

        // Try to find nearby text to match font
        val nearbyBlock = findNearbyTextBlock(x, y)
        val scale = _uiState.value.bitmapScale

        val element = PdfElement.TextElement(
            pageIndex = _uiState.value.currentPage,
            x = x,
            y = y,
            text = text,
            fontSize = nearbyBlock?.let { it.fontSize * scale } ?: _uiState.value.currentFontSize,
            color = nearbyBlock?.fontColor ?: _uiState.value.currentColor.toArgb(),
            fontBold = nearbyBlock?.isBold ?: false,
            fontItalic = nearbyBlock?.isItalic ?: false
        )

        addElement(element)
    }

    /**
     * Find nearest text block to match font properties
     */
    private fun findNearbyTextBlock(x: Float, y: Float): ExtractedTextBlock? {
        val scale = _uiState.value.bitmapScale
        val pdfX = x / scale
        val pdfY = y / scale

        return _uiState.value.extractedTextBlocks
            .minByOrNull { block ->
                val centerX = block.x + block.width / 2
                val centerY = block.y + block.height / 2
                val dx = pdfX - centerX
                val dy = pdfY - centerY
                dx * dx + dy * dy  // Distance squared
            }
    }

    // Add image/signature at position
    fun addImageElement(x: Float, y: Float, bitmap: Bitmap, width: Float, height: Float) {
        saveToUndoStack()

        val element = PdfElement.ImageElement(
            pageIndex = _uiState.value.currentPage,
            x = x,
            y = y,
            width = width,
            height = height,
            bitmap = bitmap
        )

        addElement(element)
    }

    // Start drawing
    fun startDrawing(point: Offset) {
        _uiState.update {
            it.copy(
                isDrawing = true,
                currentDrawingPoints = listOf(point)
            )
        }
    }

    // Continue drawing
    fun continueDrawing(point: Offset) {
        _uiState.update {
            it.copy(currentDrawingPoints = it.currentDrawingPoints + point)
        }
    }

    // End drawing
    fun endDrawing() {
        val points = _uiState.value.currentDrawingPoints
        if (points.size > 1) {
            saveToUndoStack()

            val element = PdfElement.DrawingElement(
                pageIndex = _uiState.value.currentPage,
                points = points,
                strokeWidth = _uiState.value.currentStrokeWidth,
                color = _uiState.value.currentColor.toArgb()
            )

            addElement(element)
        }

        _uiState.update {
            it.copy(isDrawing = false, currentDrawingPoints = emptyList())
        }
    }

    // Add highlight rectangle
    fun addHighlight(x: Float, y: Float, width: Float, height: Float) {
        saveToUndoStack()

        val element = PdfElement.HighlightElement(
            pageIndex = _uiState.value.currentPage,
            x = x,
            y = y,
            width = width,
            height = height,
            color = _uiState.value.currentColor.copy(alpha = 0.4f).toArgb()
        )

        addElement(element)
    }

    // Add whiteout rectangle
    fun addWhiteout(x: Float, y: Float, width: Float, height: Float) {
        saveToUndoStack()

        val element = PdfElement.WhiteoutElement(
            pageIndex = _uiState.value.currentPage,
            x = x,
            y = y,
            width = width,
            height = height
        )

        addElement(element)
    }

    // Rectangle drag functions
    fun startRectDrag(point: Offset) {
        _uiState.update {
            it.copy(
                isDraggingRect = true,
                rectStartPoint = point,
                rectEndPoint = point
            )
        }
    }

    fun continueRectDrag(point: Offset) {
        _uiState.update {
            it.copy(rectEndPoint = point)
        }
    }

    fun endRectDrag() {
        val startPoint = _uiState.value.rectStartPoint
        val endPoint = _uiState.value.rectEndPoint

        if (startPoint != null && endPoint != null) {
            val x = minOf(startPoint.x, endPoint.x)
            val y = minOf(startPoint.y, endPoint.y)
            val width = kotlin.math.abs(endPoint.x - startPoint.x)
            val height = kotlin.math.abs(endPoint.y - startPoint.y)

            if (width > 10 && height > 10) {
                when (_uiState.value.currentTool) {
                    EditorTool.WHITEOUT -> addWhiteout(x, y, width, height)
                    EditorTool.HIGHLIGHT -> addHighlight(x, y, width, height)
                    else -> {}
                }
            }
        }

        _uiState.update {
            it.copy(
                isDraggingRect = false,
                rectStartPoint = null,
                rectEndPoint = null
            )
        }
    }

    private fun addElement(element: PdfElement) {
        val pageIndex = _uiState.value.currentPage
        allPagesElements.getOrPut(pageIndex) { mutableListOf() }.add(element)

        _uiState.update {
            it.copy(
                elements = allPagesElements[pageIndex]?.toList() ?: emptyList(),
                selectedElementId = element.id,
                hasChanges = true
            )
        }
    }

    fun selectElement(elementId: String?) {
        _uiState.update {
            it.copy(
                selectedElementId = elementId,
                selectedTextBlockId = null
            )
        }
    }

    fun moveElement(elementId: String, newX: Float, newY: Float) {
        saveToUndoStack()

        val pageIndex = _uiState.value.currentPage
        val elements = allPagesElements[pageIndex] ?: return

        elements.find { it.id == elementId }?.let { element ->
            element.x = newX
            element.y = newY
        }

        _uiState.update {
            it.copy(
                elements = elements.toList(),
                hasChanges = true
            )
        }
    }

    fun updateTextElement(elementId: String, newText: String) {
        saveToUndoStack()

        val pageIndex = _uiState.value.currentPage
        val elements = allPagesElements[pageIndex] ?: return

        elements.filterIsInstance<PdfElement.TextElement>()
            .find { it.id == elementId }?.let { element ->
                element.text = newText
            }

        _uiState.update {
            it.copy(
                elements = elements.toList(),
                hasChanges = true
            )
        }
    }

    fun deleteSelectedElement() {
        val selectedId = _uiState.value.selectedElementId ?: return
        saveToUndoStack()

        val pageIndex = _uiState.value.currentPage
        allPagesElements[pageIndex]?.removeAll { it.id == selectedId }

        _uiState.update {
            it.copy(
                elements = allPagesElements[pageIndex]?.toList() ?: emptyList(),
                selectedElementId = null,
                hasChanges = true
            )
        }
    }

    fun undo() {
        // Pop first with removeLastOrNull() so a double-tap within one frame can't throw
        // NoSuchElementException (the isNotEmpty() check was not race-safe).
        val previousState = undoStack.removeLastOrNull() ?: return
        val pageIndex = _uiState.value.currentPage
        redoStack.add(allPagesElements[pageIndex]?.toList() ?: emptyList())
        allPagesElements[pageIndex] = previousState.toMutableList()

        _uiState.update {
            it.copy(
                elements = previousState,
                hasChanges = allPagesElements.values.any { it.isNotEmpty() }
            )
        }
    }

    fun redo() {
        val nextState = redoStack.removeLastOrNull() ?: return
        val pageIndex = _uiState.value.currentPage
        undoStack.add(allPagesElements[pageIndex]?.toList() ?: emptyList())
        allPagesElements[pageIndex] = nextState.toMutableList()

        _uiState.update {
            it.copy(
                elements = nextState,
                hasChanges = true
            )
        }
    }

    private fun saveToUndoStack() {
        val pageIndex = _uiState.value.currentPage
        undoStack.add(allPagesElements[pageIndex]?.toList() ?: emptyList())
        redoStack.clear()
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun savePdf(context: Context, replaceOriginal: Boolean = false, onComplete: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            val sourceUri = _uiState.value.uri
            if (sourceUri == null) {
                _uiState.update { it.copy(error = "No PDF loaded to save") }
                return@launch
            }

            _uiState.update { it.copy(isSaving = true, error = null) }

            try {
                val outputFile: File

                if (replaceOriginal && sourceUri.scheme == "file") {
                    // Replace original file
                    val originalFile = File(sourceUri.path!!)
                    // Create temp file first, then replace
                    val tempFile = File(originalFile.parent, "temp_${originalFile.name}")

                    val success = withContext(Dispatchers.IO) {
                        renderPdfWithElements(context, sourceUri, tempFile)
                    }

                    if (success && tempFile.exists() && tempFile.length() > 0) {
                        // Replace the original ONLY after the temp is confirmed good, and
                        // never delete the original first (a failed rename used to wipe the
                        // user's file). Same dir => renameTo is atomic; copyTo is the fallback.
                        val replaced = withContext(Dispatchers.IO) {
                            if (tempFile.renameTo(originalFile)) {
                                true
                            } else {
                                runCatching {
                                    tempFile.copyTo(originalFile, overwrite = true)
                                    tempFile.delete()
                                }.isSuccess
                            }
                        }
                        if (!replaced) {
                            tempFile.delete()
                            _uiState.update {
                                it.copy(isSaving = false, error = "Failed to save PDF")
                            }
                            return@launch
                        }
                        outputFile = originalFile
                    } else {
                        tempFile.delete()
                        _uiState.update {
                            it.copy(isSaving = false, error = "Failed to save PDF")
                        }
                        return@launch
                    }
                } else {
                    // Save as new file
                    val outputDir = FileUtils.getOutputDirectory(context)
                    if (!outputDir.exists() && !outputDir.mkdirs()) {
                        throw Exception("Cannot create output directory")
                    }

                    outputFile = File(outputDir, FileUtils.generateOutputFileName("Edited"))

                    val success = withContext(Dispatchers.IO) {
                        renderPdfWithElements(context, sourceUri, outputFile)
                    }

                    if (!success || !outputFile.exists() || outputFile.length() == 0L) {
                        _uiState.update {
                            it.copy(isSaving = false, error = "Failed to save PDF - file not created")
                        }
                        return@launch
                    }
                }

                // Clear modified text block tracking for saved file
                modifiedTextBlockIds.clear()
                allPagesExtractedText.clear()

                analytics.track(AnalyticsEvent.EditorSaved(replaceOriginal = replaceOriginal))

                _uiState.update {
                    it.copy(
                        isSaving = false,
                        hasChanges = false,
                        uri = Uri.fromFile(outputFile),
                        saveSuccess = true,
                        savedFilePath = outputFile.absolutePath
                    )
                }

                undoStack.clear()
                redoStack.clear()
                allPagesElements.clear()

                onComplete?.invoke(Uri.fromFile(outputFile).toString())

            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update {
                    it.copy(isSaving = false, error = "Failed to save: ${e.message}")
                }
            }
        }
    }

    private suspend fun renderPdfWithElements(
        context: Context,
        sourceUri: Uri,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Collect all edits to apply via OpenPdfEditor (PDFBox-backed)
            val textEdits = mutableListOf<OpenPdfEditor.TextEdit>()
            val whiteouts = mutableListOf<OpenPdfEditor.WhiteoutRect>()
            val images = mutableListOf<OpenPdfEditor.ImageOverlay>()
            val highlights = mutableListOf<OpenPdfEditor.HighlightRect>()

            val pageCount = PdfUtils.getPageCount(context, sourceUri)

            for (pageIndex in 0 until pageCount) {
                // Get PDF page dimensions for coordinate conversion
                val pdfDims = OpenPdfEditor.getPageDimensions(context, sourceUri, pageIndex)
                    ?: continue
                val bitmapDims = _uiState.value.pageDimensions
                val bitmapScale = _uiState.value.bitmapScale

                // Whiteout modified original text blocks
                val extractedBlocks = allPagesExtractedText[pageIndex] ?: emptyList()
                for (block in extractedBlocks) {
                    if (modifiedTextBlockIds.contains(block.id)) {
                        whiteouts.add(
                            OpenPdfEditor.WhiteoutRect(
                                pageIndex = pageIndex,
                                x = block.x,
                                y = block.y,
                                width = block.width,
                                height = block.height
                            )
                        )
                    }
                }

                // Convert UI elements to OpenPdfEditor operations
                val pageElements = allPagesElements[pageIndex] ?: emptyList()
                for (element in pageElements) {
                    when (element) {
                        is PdfElement.TextElement -> {
                            // Convert from bitmap coords to PDF coords
                            textEdits.add(
                                OpenPdfEditor.TextEdit(
                                    pageIndex = pageIndex,
                                    x = element.x / bitmapScale,
                                    y = element.y / bitmapScale,
                                    text = element.text,
                                    fontSize = element.fontSize / bitmapScale,
                                    fontColor = element.color,
                                    isBold = element.fontBold,
                                    isItalic = element.fontItalic
                                )
                            )
                        }
                        is PdfElement.WhiteoutElement -> {
                            whiteouts.add(
                                OpenPdfEditor.WhiteoutRect(
                                    pageIndex = pageIndex,
                                    x = element.x / bitmapScale,
                                    y = element.y / bitmapScale,
                                    width = element.width / bitmapScale,
                                    height = element.height / bitmapScale
                                )
                            )
                        }
                        is PdfElement.ImageElement -> {
                            images.add(
                                OpenPdfEditor.ImageOverlay(
                                    pageIndex = pageIndex,
                                    x = element.x / bitmapScale,
                                    y = element.y / bitmapScale,
                                    width = element.width / bitmapScale,
                                    height = element.height / bitmapScale,
                                    bitmap = element.bitmap
                                )
                            )
                        }
                        is PdfElement.HighlightElement -> {
                            highlights.add(
                                OpenPdfEditor.HighlightRect(
                                    pageIndex = pageIndex,
                                    x = element.x / bitmapScale,
                                    y = element.y / bitmapScale,
                                    width = element.width / bitmapScale,
                                    height = element.height / bitmapScale,
                                    color = element.color,
                                )
                            )
                        }
                        is PdfElement.DrawingElement -> {
                            // Drawings need to be converted to PDF paths
                            // For now, render to bitmap and add as image
                            val drawingBitmap = renderDrawingToBitmap(element, bitmapScale)
                            if (drawingBitmap != null) {
                                images.add(
                                    OpenPdfEditor.ImageOverlay(
                                        pageIndex = pageIndex,
                                        x = 0f,
                                        y = 0f,
                                        width = pdfDims.first,
                                        height = pdfDims.second,
                                        bitmap = drawingBitmap
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Apply all edits via OpenPdfEditor (PDFBox-backed)
            OpenPdfEditor.applyEdits(
                context = context,
                sourceUri = sourceUri,
                outputFile = outputFile,
                textEdits = textEdits,
                whiteouts = whiteouts,
                images = images,
                highlights = highlights
            )

        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Render drawing element to a transparent bitmap
     */
    private fun renderDrawingToBitmap(element: PdfElement.DrawingElement, bitmapScale: Float): Bitmap? {
        if (element.points.size < 2) return null

        try {
            // Calculate bounds
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var maxY = Float.MIN_VALUE

            element.points.forEach { point ->
                minX = minOf(minX, point.x)
                minY = minOf(minY, point.y)
                maxX = maxOf(maxX, point.x)
                maxY = maxOf(maxY, point.y)
            }

            val padding = element.strokeWidth * 2
            val width = ((maxX - minX + padding * 2) / bitmapScale).toInt().coerceAtLeast(1)
            val height = ((maxY - minY + padding * 2) / bitmapScale).toInt().coerceAtLeast(1)

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val paint = Paint().apply {
                color = element.color
                strokeWidth = element.strokeWidth / bitmapScale
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                isAntiAlias = true
            }

            val path = Path()
            element.points.forEachIndexed { index, point ->
                val x = (point.x - minX + padding) / bitmapScale
                val y = (point.y - minY + padding) / bitmapScale
                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }
            canvas.drawPath(path, paint)

            return bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun drawElement(canvas: Canvas, element: PdfElement, scaleX: Float, scaleY: Float) {
        when (element) {
            is PdfElement.TextElement -> {
                val paint = Paint().apply {
                    color = element.color
                    textSize = element.fontSize * scaleY
                    isAntiAlias = true
                    typeface = when {
                        element.fontBold && element.fontItalic -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
                        element.fontBold -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        element.fontItalic -> Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                        else -> Typeface.DEFAULT
                    }
                }
                canvas.drawText(
                    element.text,
                    element.x * scaleX,
                    element.y * scaleY + element.fontSize * scaleY,
                    paint
                )
            }

            is PdfElement.ImageElement -> {
                val scaledBitmap = Bitmap.createScaledBitmap(
                    element.bitmap,
                    (element.width * scaleX).toInt(),
                    (element.height * scaleY).toInt(),
                    true
                )
                canvas.drawBitmap(
                    scaledBitmap,
                    element.x * scaleX,
                    element.y * scaleY,
                    null
                )
                scaledBitmap.recycle()
            }

            is PdfElement.DrawingElement -> {
                val paint = Paint().apply {
                    color = element.color
                    strokeWidth = element.strokeWidth * scaleY
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    isAntiAlias = true
                }

                val path = Path()
                element.points.forEachIndexed { index, point ->
                    if (index == 0) {
                        path.moveTo(point.x * scaleX, point.y * scaleY)
                    } else {
                        path.lineTo(point.x * scaleX, point.y * scaleY)
                    }
                }
                canvas.drawPath(path, paint)
            }

            is PdfElement.HighlightElement -> {
                val paint = Paint().apply {
                    color = element.color
                    style = Paint.Style.FILL
                }
                canvas.drawRect(
                    RectF(
                        element.x * scaleX,
                        element.y * scaleY,
                        (element.x + element.width) * scaleX,
                        (element.y + element.height) * scaleY
                    ),
                    paint
                )
            }

            is PdfElement.WhiteoutElement -> {
                val paint = Paint().apply {
                    color = android.graphics.Color.WHITE
                    style = Paint.Style.FILL
                }
                canvas.drawRect(
                    RectF(
                        element.x * scaleX,
                        element.y * scaleY,
                        (element.x + element.width) * scaleX,
                        (element.y + element.height) * scaleY
                    ),
                    paint
                )
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearSaveSuccess() {
        _uiState.update { it.copy(saveSuccess = false, savedFilePath = null) }
    }
}

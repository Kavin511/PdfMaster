package com.pdfmaster.app.presentation.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdfmaster.app.util.FileUtils
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
import java.io.FileOutputStream
import javax.inject.Inject

data class TextEdit(
    val id: String,
    val pageNumber: Int,
    val text: String,
    val x: Float,
    val y: Float,
    val fontSize: Float,
    val color: Int
)

data class EditorUiState(
    val uri: Uri? = null,
    val fileName: String = "",
    val pageCount: Int = 0,
    val pages: List<Bitmap?> = emptyList(),
    val selectedPage: Int = 0,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val hasChanges: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val textEdits: List<TextEdit> = emptyList()
)

@HiltViewModel
class EditorViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val undoStack = mutableListOf<List<TextEdit>>()
    private val redoStack = mutableListOf<List<TextEdit>>()

    fun loadPdf(context: Context, uri: Uri, title: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val fileName = title ?: FileUtils.getFileName(context, uri)
                val pageCount = PdfUtils.getPageCount(context, uri)

                if (pageCount == 0) {
                    _uiState.update {
                        it.copy(isLoading = false, error = "Could not open PDF file")
                    }
                    return@launch
                }

                val initialPages = List(pageCount) { null as Bitmap? }

                _uiState.update {
                    it.copy(
                        uri = uri,
                        fileName = fileName,
                        pageCount = pageCount,
                        pages = initialPages,
                        isLoading = false
                    )
                }

                loadPagesAsync(context, uri, pageCount)

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to load PDF: ${e.message}")
                }
            }
        }
    }

    private fun loadPagesAsync(context: Context, uri: Uri, pageCount: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val screenWidth = context.resources.displayMetrics.widthPixels

            for (pageIndex in 0 until pageCount) {
                val bitmap = PdfUtils.renderPage(context, uri, pageIndex, screenWidth)

                withContext(Dispatchers.Main) {
                    _uiState.update { state ->
                        val updatedPages = state.pages.toMutableList()
                        if (pageIndex < updatedPages.size) {
                            updatedPages[pageIndex] = bitmap
                        }
                        state.copy(pages = updatedPages)
                    }
                }
            }
        }
    }

    fun selectPage(pageIndex: Int) {
        _uiState.update { it.copy(selectedPage = pageIndex) }
    }

    fun addText(text: String, fontSize: Float, color: Color) {
        val currentState = _uiState.value

        saveToUndoStack()

        val newEdit = TextEdit(
            id = System.currentTimeMillis().toString(),
            pageNumber = currentState.selectedPage,
            text = text,
            x = 100f,
            y = 100f,
            fontSize = fontSize,
            color = color.toArgb()
        )

        _uiState.update {
            it.copy(
                textEdits = it.textEdits + newEdit,
                hasChanges = true,
                canUndo = true
            )
        }
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            redoStack.add(_uiState.value.textEdits)
            val previousState = undoStack.removeLast()
            _uiState.update {
                it.copy(
                    textEdits = previousState,
                    hasChanges = previousState.isNotEmpty(),
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = true
                )
            }
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            undoStack.add(_uiState.value.textEdits)
            val nextState = redoStack.removeLast()
            _uiState.update {
                it.copy(
                    textEdits = nextState,
                    hasChanges = true,
                    canUndo = true,
                    canRedo = redoStack.isNotEmpty()
                )
            }
        }
    }

    private fun saveToUndoStack() {
        undoStack.add(_uiState.value.textEdits)
        redoStack.clear()
    }

    fun save(context: Context, onComplete: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            val sourceUri = _uiState.value.uri ?: return@launch
            _uiState.update { it.copy(isSaving = true, error = null) }

            try {
                val outputDir = FileUtils.getOutputDirectory(context)
                val outputFile = File(outputDir, FileUtils.generateOutputFileName("Edited"))

                val success = withContext(Dispatchers.IO) {
                    renderTextEditsToPdf(context, sourceUri, outputFile)
                }

                if (success) {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            hasChanges = false,
                            uri = Uri.fromFile(outputFile)
                        )
                    }

                    // Clear undo/redo stacks after save
                    undoStack.clear()
                    redoStack.clear()
                    _uiState.update { it.copy(canUndo = false, canRedo = false) }

                    onComplete?.invoke(Uri.fromFile(outputFile).toString())
                } else {
                    _uiState.update {
                        it.copy(isSaving = false, error = "Failed to save PDF")
                    }
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = "Failed to save: ${e.message}"
                    )
                }
            }
        }
    }

    private suspend fun renderTextEditsToPdf(context: Context, sourceUri: Uri, outputFile: File): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val pageCount = _uiState.value.pageCount
                val document = PdfDocument()
                val width = context.resources.displayMetrics.widthPixels
                val textEdits = _uiState.value.textEdits

                for (i in 0 until pageCount) {
                    val pageBitmap = PdfUtils.renderPage(context, sourceUri, i, width) ?: continue

                    val pageInfo = PdfDocument.PageInfo.Builder(
                        pageBitmap.width,
                        pageBitmap.height,
                        i + 1
                    ).create()

                    val page = document.startPage(pageInfo)
                    val canvas = page.canvas

                    // Draw original page
                    canvas.drawBitmap(pageBitmap, 0f, 0f, null)

                    // Draw text edits for this page
                    val pageEdits = textEdits.filter { it.pageNumber == i }
                    for (edit in pageEdits) {
                        drawTextEdit(canvas, edit)
                    }

                    document.finishPage(page)
                    pageBitmap.recycle()
                }

                FileOutputStream(outputFile).use { out ->
                    document.writeTo(out)
                }
                document.close()
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun drawTextEdit(canvas: Canvas, edit: TextEdit) {
        val paint = Paint().apply {
            color = edit.color
            textSize = edit.fontSize
            isAntiAlias = true
        }
        canvas.drawText(edit.text, edit.x, edit.y, paint)
    }
}

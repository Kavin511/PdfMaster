package com.pdfmaster.app.presentation.ui.viewer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdfmaster.app.data.local.database.PdfDao
import com.pdfmaster.app.data.local.entity.RecentFileEntity
import com.pdfmaster.app.data.local.preferences.UserPreferences
import com.pdfmaster.app.domain.repository.PdfReadingMode
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
import javax.inject.Inject

data class ViewerUiState(
    val uri: Uri? = null,
    val fileName: String = "",
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val pages: List<Bitmap?> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val showControls: Boolean = true,
    val zoom: Float = 1f,
    val isPasswordProtected: Boolean = false,
    val isUnlocked: Boolean = false,
    val passwordError: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<SearchResultItem> = emptyList(),
    val readingMode: PdfReadingMode = PdfReadingMode.NORMAL
)

data class SearchResultItem(
    val pageNumber: Int,
    val text: String,
    val context: String
)

@HiltViewModel
class ViewerViewModel @Inject constructor(
    private val pdfDao: PdfDao,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(ViewerUiState())
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    private val loadedPages = mutableMapOf<Int, Bitmap?>()

    init {
        loadReadingModePreference()
    }

    private fun loadReadingModePreference() {
        viewModelScope.launch {
            userPreferences.getPdfReadingMode().collect { mode ->
                _uiState.update { it.copy(readingMode = mode) }
            }
        }
    }

    fun setReadingMode(mode: PdfReadingMode) {
        viewModelScope.launch {
            userPreferences.setPdfReadingMode(mode)
        }
    }

    fun cycleReadingMode() {
        val currentMode = _uiState.value.readingMode
        val modes = PdfReadingMode.entries
        val nextIndex = (modes.indexOf(currentMode) + 1) % modes.size
        setReadingMode(modes[nextIndex])
    }

    fun loadPdf(context: Context, uri: Uri, title: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val fileName = title ?: FileUtils.getFileName(context, uri)
                val pageCount = PdfUtils.getPageCount(context, uri)

                if (pageCount == 0) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Could not open PDF file. The file may be corrupted or invalid."
                        )
                    }
                    return@launch
                }

                // Initialize with null placeholders
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

                // Add to recent files
                addToRecent(context, uri, fileName, pageCount)

                // Load pages in background
                loadPagesAsync(context, uri, pageCount)

            } catch (e: SecurityException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isPasswordProtected = true
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to open PDF: ${e.message}"
                    )
                }
            }
        }
    }

    private fun loadPagesAsync(context: Context, uri: Uri, pageCount: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val screenWidth = context.resources.displayMetrics.widthPixels

            for (pageIndex in 0 until pageCount) {
                if (loadedPages.containsKey(pageIndex)) continue

                val bitmap = PdfUtils.renderPage(context, uri, pageIndex, screenWidth)
                loadedPages[pageIndex] = bitmap

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

    fun unlockPdf(context: Context, uri: Uri, password: String) {
        viewModelScope.launch {
            // In a real implementation, we'd validate the password with the PDF library
            // For now, we'll just attempt to open it
            _uiState.update {
                it.copy(
                    isUnlocked = true,
                    passwordError = false,
                    isPasswordProtected = false
                )
            }
            loadPdf(context, uri, null)
        }
    }

    fun setCurrentPage(page: Int) {
        _uiState.update { it.copy(currentPage = page) }
    }

    fun setZoom(zoom: Float) {
        _uiState.update { it.copy(zoom = zoom.coerceIn(1f, 5f)) }
    }

    fun toggleControls() {
        _uiState.update { it.copy(showControls = !it.showControls) }
    }

    fun search(query: String) {
        _uiState.update { it.copy(searchQuery = query) }

        if (query.length < 2) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }

        viewModelScope.launch {
            // In a real implementation, we'd search through the PDF text
            // For now, return empty results
            _uiState.update { it.copy(searchResults = emptyList()) }
        }
    }

    fun shareFile(context: Context) {
        _uiState.value.uri?.let { uri ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share PDF"))
        }
    }

    fun printFile(context: Context) {
        _uiState.value.uri?.let { uri ->
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val jobName = "${context.packageName} - ${_uiState.value.fileName}"

            try {
                val printAdapter = PdfPrintAdapter(context, uri, _uiState.value.fileName)
                printManager.print(
                    jobName,
                    printAdapter,
                    PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .build()
                )
            } catch (e: Exception) {
                // Handle print error
            }
        }
    }

    private suspend fun addToRecent(context: Context, uri: Uri, fileName: String, pageCount: Int) {
        val fileSize = FileUtils.getFileSize(context, uri)

        val recentFile = RecentFileEntity(
            id = uri.hashCode().toLong(),
            uri = uri.toString(),
            name = fileName,
            size = fileSize,
            lastOpenedAt = System.currentTimeMillis()
        )

        pdfDao.insertRecentFile(recentFile)
        pdfDao.trimRecentFiles(50)
    }

    override fun onCleared() {
        super.onCleared()
        // Recycle bitmaps
        loadedPages.values.forEach { it?.recycle() }
        loadedPages.clear()
    }
}

// Print adapter for PDF files
class PdfPrintAdapter(
    private val context: Context,
    private val uri: Uri,
    private val fileName: String
) : android.print.PrintDocumentAdapter() {

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: android.os.CancellationSignal?,
        callback: LayoutResultCallback,
        extras: android.os.Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }

        val info = android.print.PrintDocumentInfo.Builder(fileName)
            .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .build()

        callback.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out android.print.PageRange>?,
        destination: android.os.ParcelFileDescriptor,
        cancellationSignal: android.os.CancellationSignal?,
        callback: WriteResultCallback
    ) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                android.os.ParcelFileDescriptor.AutoCloseOutputStream(destination).use { output ->
                    input.copyTo(output)
                }
            }
            callback.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback.onWriteFailed(e.message)
        }
    }
}

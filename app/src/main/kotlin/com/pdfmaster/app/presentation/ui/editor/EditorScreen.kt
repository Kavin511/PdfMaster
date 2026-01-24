package com.pdfmaster.app.presentation.ui.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdfmaster.app.presentation.theme.*
import com.pdfmaster.app.util.ExtractedTextBlock
import kotlin.math.roundToInt

// Flag to control whether to use ComPDFKit or the custom editor
// TODO: Set to true once ComPDFKit imports are fixed
private const val USE_COMPDFKIT = false

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    uri: String,
    title: String?,
    onNavigateBack: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    // Launch ComPDFKit editor if enabled
    if (USE_COMPDFKIT) {
        var hasLaunched by remember { mutableStateOf(false) }

        LaunchedEffect(uri) {
            if (!hasLaunched) {
                hasLaunched = true
                val intent = ComPdfKitEditorActivity.createIntent(context, Uri.parse(uri))
                context.startActivity(intent)
                // Navigate back after launching the activity
                onNavigateBack()
            }
        }

        // Show a loading state while launching
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    // ============ Legacy Custom Editor (kept as fallback) ============
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showTextDialog by remember { mutableStateOf(false) }
    var textInputPosition by remember { mutableStateOf(Offset.Zero) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var showSaveOptionsDialog by remember { mutableStateOf(false) }
    var editingTextId by remember { mutableStateOf<String?>(null) }
    var editingPdfTextBlockId by remember { mutableStateOf<String?>(null) }  // For editing existing PDF text

    // Inline editing state for PDFfiller-style editing
    var inlineEditingBlock by remember { mutableStateOf<ExtractedTextBlock?>(null) }
    var inlineEditingText by remember { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }

    // Show save success message with file path
    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            val path = uiState.savedFilePath?.substringAfterLast("/") ?: "PdfMaster folder"
            snackbarHostState.showSnackbar(
                message = "Saved to: $path",
                duration = SnackbarDuration.Long
            )
            viewModel.clearSaveSuccess()
        }
    }

    // Show error message
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Long
            )
            viewModel.clearError()
        }
    }

    // Load PDF on first composition
    LaunchedEffect(uri) {
        viewModel.loadPdf(context, Uri.parse(uri), title)
    }

    // Handle back press
    val handleBack = {
        if (uiState.hasChanges) {
            showUnsavedDialog = true
        } else {
            onNavigateBack()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.fileName.ifEmpty { "Edit PDF" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        if (uiState.hasChanges) {
                            Text(
                                text = "Unsaved changes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { handleBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = viewModel.canUndo()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = viewModel.canRedo()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }
                    IconButton(
                        onClick = { showSaveOptionsDialog = true },
                        enabled = uiState.hasChanges && !uiState.isSaving
                    ) {
                        Icon(Icons.Outlined.Save, contentDescription = "Save")
                    }
                }
            )
        },
        bottomBar = {
            Column {
                // Main toolbar
                EditorToolbar(
                    currentTool = uiState.currentTool,
                    currentColor = uiState.currentColor,
                    onToolSelected = { viewModel.setTool(it) },
                    onColorClick = { showColorPicker = true }
                )

                // Page navigation
                if (uiState.pageCount > 1) {
                    PageNavigationBar(
                        currentPage = uiState.currentPage,
                        pageCount = uiState.pageCount,
                        onPreviousPage = {
                            if (uiState.currentPage > 0) {
                                viewModel.loadPage(context, uiState.currentPage - 1)
                            }
                        },
                        onNextPage = {
                            if (uiState.currentPage < uiState.pageCount - 1) {
                                viewModel.loadPage(context, uiState.currentPage + 1)
                            }
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF404040))
        ) {
            when {
                uiState.isLoading -> {
                    LoadingContent()
                }
                uiState.error != null -> {
                    ErrorContent(
                        error = uiState.error!!,
                        onRetry = { viewModel.loadPdf(context, Uri.parse(uri), title) }
                    )
                }
                else -> {
                    // PDF Canvas
                    PdfEditorCanvas(
                        uiState = uiState,
                        inlineEditingBlock = inlineEditingBlock,
                        inlineEditingText = inlineEditingText,
                        onInlineTextChange = { inlineEditingText = it },
                        onInlineEditComplete = { block, newText ->
                            if (newText.isNotBlank() && newText != block.text) {
                                viewModel.editExistingText(block.id, newText)
                            }
                            inlineEditingBlock = null
                            inlineEditingText = ""
                        },
                        onInlineEditCancel = {
                            inlineEditingBlock = null
                            inlineEditingText = ""
                        },
                        onCanvasTap = { position ->
                            // If inline editing is active, complete it first
                            if (inlineEditingBlock != null) {
                                val block = inlineEditingBlock!!
                                if (inlineEditingText.isNotBlank() && inlineEditingText != block.text) {
                                    viewModel.editExistingText(block.id, inlineEditingText)
                                }
                                inlineEditingBlock = null
                                inlineEditingText = ""
                                return@PdfEditorCanvas
                            }

                            when (uiState.currentTool) {
                                EditorTool.TEXT -> {
                                    // First check if tapping on existing PDF text
                                    val existingTextBlock = viewModel.findTextBlockAtPosition(position.x, position.y)
                                    if (existingTextBlock != null) {
                                        // Start inline editing
                                        inlineEditingBlock = existingTextBlock
                                        inlineEditingText = existingTextBlock.text
                                    } else {
                                        // Add new text (use dialog for new text)
                                        textInputPosition = position
                                        editingTextId = null
                                        editingPdfTextBlockId = null
                                        showTextDialog = true
                                    }
                                }
                                EditorTool.SELECT -> {
                                    // First check user-added elements
                                    val tappedElement = findElementAtPosition(uiState.elements, position)
                                    if (tappedElement != null) {
                                        viewModel.selectElement(tappedElement.id)
                                    } else {
                                        // Check if tapped on existing PDF text - start inline editing
                                        val existingTextBlock = viewModel.findTextBlockAtPosition(position.x, position.y)
                                        if (existingTextBlock != null) {
                                            inlineEditingBlock = existingTextBlock
                                            inlineEditingText = existingTextBlock.text
                                        } else {
                                            viewModel.selectElement(null)
                                            viewModel.selectTextBlock(null)
                                        }
                                    }
                                }
                                else -> {}
                            }
                        },
                        onDrawStart = { position ->
                            viewModel.startDrawing(position)
                        },
                        onDrawMove = { position ->
                            viewModel.continueDrawing(position)
                        },
                        onDrawEnd = {
                            viewModel.endDrawing()
                        },
                        onRectStart = { position ->
                            viewModel.startRectDrag(position)
                        },
                        onRectMove = { position ->
                            viewModel.continueRectDrag(position)
                        },
                        onRectEnd = {
                            viewModel.endRectDrag()
                        },
                        onElementDrag = { elementId, newX, newY ->
                            viewModel.moveElement(elementId, newX, newY)
                        }
                    )
                }
            }

            // Selection actions bar for user-added elements (floating overlay)
            AnimatedVisibility(
                visible = uiState.selectedElementId != null,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(24.dp)),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    tonalElevation = 4.dp,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Edit text (only for text elements)
                        val selectedElement = uiState.elements.find { it.id == uiState.selectedElementId }
                        if (selectedElement is PdfElement.TextElement) {
                            FilledTonalIconButton(
                                onClick = {
                                    editingTextId = selectedElement.id
                                    showTextDialog = true
                                }
                            ) {
                                Icon(Icons.Default.Edit, "Edit")
                            }
                        }

                        FilledTonalIconButton(
                            onClick = { viewModel.deleteSelectedElement() },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }

                        FilledTonalIconButton(
                            onClick = { viewModel.selectElement(null) }
                        ) {
                            Icon(Icons.Default.Close, "Deselect")
                        }
                    }
                }
            }

            // Selection actions bar for existing PDF text (floating overlay)
            AnimatedVisibility(
                visible = uiState.selectedTextBlockId != null,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(24.dp)),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 4.dp,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Edit existing PDF text
                        FilledTonalIconButton(
                            onClick = {
                                editingPdfTextBlockId = uiState.selectedTextBlockId
                                editingTextId = null
                                showTextDialog = true
                            }
                        ) {
                            Icon(Icons.Default.Edit, "Edit")
                        }

                        // Delete existing PDF text
                        FilledTonalIconButton(
                            onClick = {
                                uiState.selectedTextBlockId?.let { viewModel.deleteExistingText(it) }
                            },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }

                        FilledTonalIconButton(
                            onClick = { viewModel.selectTextBlock(null) }
                        ) {
                            Icon(Icons.Default.Close, "Deselect")
                        }
                    }
                }
            }

            // Saving overlay
            if (uiState.isSaving) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Saving PDF...")
                        }
                    }
                }
            }
        }
    }

    // Text Input Dialog
    if (showTextDialog) {
        val initialText = when {
            editingTextId != null -> {
                (uiState.elements.find { it.id == editingTextId } as? PdfElement.TextElement)?.text ?: ""
            }
            editingPdfTextBlockId != null -> {
                uiState.extractedTextBlocks.find { it.id == editingPdfTextBlockId }?.text ?: ""
            }
            else -> ""
        }

        TextInputDialog(
            initialText = initialText,
            onConfirm = { text ->
                when {
                    editingTextId != null -> {
                        // Editing user-added text element
                        viewModel.updateTextElement(editingTextId!!, text)
                    }
                    editingPdfTextBlockId != null -> {
                        // Editing existing PDF text
                        viewModel.editExistingText(editingPdfTextBlockId!!, text)
                    }
                    else -> {
                        // Adding new text
                        viewModel.addTextElement(textInputPosition.x, textInputPosition.y, text)
                    }
                }
                showTextDialog = false
                editingTextId = null
                editingPdfTextBlockId = null
            },
            onDismiss = {
                showTextDialog = false
                editingTextId = null
                editingPdfTextBlockId = null
            }
        )
    }

    // Color Picker Dialog
    if (showColorPicker) {
        ColorPickerDialog(
            currentColor = uiState.currentColor,
            onColorSelected = {
                viewModel.setColor(it)
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false }
        )
    }

    // Unsaved Changes Dialog
    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text("Unsaved Changes") },
            text = { Text("You have unsaved changes. Do you want to save before leaving?") },
            confirmButton = {
                Button(
                    onClick = {
                        showUnsavedDialog = false
                        showSaveOptionsDialog = true
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showUnsavedDialog = false }) {
                        Text("Cancel")
                    }
                    TextButton(onClick = {
                        showUnsavedDialog = false
                        onNavigateBack()
                    }) {
                        Text("Discard")
                    }
                }
            }
        )
    }

    // Save Options Dialog
    if (showSaveOptionsDialog) {
        val canReplaceOriginal = uiState.uri?.scheme == "file"

        AlertDialog(
            onDismissRequest = { showSaveOptionsDialog = false },
            title = { Text("Save PDF") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("How would you like to save?")

                    if (!canReplaceOriginal) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Note: Original file cannot be replaced (external source)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Save as new file option
                    Button(
                        onClick = {
                            showSaveOptionsDialog = false
                            viewModel.savePdf(context, replaceOriginal = false)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save as New File")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Replace original option
                    OutlinedButton(
                        onClick = {
                            showSaveOptionsDialog = false
                            viewModel.savePdf(context, replaceOriginal = true)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = canReplaceOriginal
                    ) {
                        Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Replace Original")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSaveOptionsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PdfEditorCanvas(
    uiState: EditorUiState,
    inlineEditingBlock: ExtractedTextBlock?,
    inlineEditingText: String,
    onInlineTextChange: (String) -> Unit,
    onInlineEditComplete: (ExtractedTextBlock, String) -> Unit,
    onInlineEditCancel: () -> Unit,
    onCanvasTap: (Offset) -> Unit,
    onDrawStart: (Offset) -> Unit,
    onDrawMove: (Offset) -> Unit,
    onDrawEnd: () -> Unit,
    onRectStart: (Offset) -> Unit,
    onRectMove: (Offset) -> Unit,
    onRectEnd: () -> Unit,
    onElementDrag: (String, Float, Float) -> Unit
) {
    val bitmap = uiState.pageBitmap ?: return
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Reset zoom when page changes
    LaunchedEffect(uiState.currentPage) {
        scale = 1f
        offset = Offset.Zero
    }

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        if (uiState.currentTool == EditorTool.SELECT || uiState.currentTool == EditorTool.TEXT) {
            scale = (scale * zoomChange).coerceIn(0.5f, 4f)
            offset += panChange
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .transformable(state = transformableState),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
                .pointerInput(uiState.currentTool, inlineEditingBlock) {
                    when (uiState.currentTool) {
                        EditorTool.DRAW -> {
                            detectDragGestures(
                                onDragStart = { onDrawStart(it) },
                                onDrag = { change, _ ->
                                    change.consume()
                                    onDrawMove(change.position)
                                },
                                onDragEnd = { onDrawEnd() },
                                onDragCancel = { onDrawEnd() }
                            )
                        }
                        EditorTool.WHITEOUT, EditorTool.HIGHLIGHT -> {
                            detectDragGestures(
                                onDragStart = { onRectStart(it) },
                                onDrag = { change, _ ->
                                    change.consume()
                                    onRectMove(change.position)
                                },
                                onDragEnd = { onRectEnd() },
                                onDragCancel = { onRectEnd() }
                            )
                        }
                        else -> {}
                    }
                }
                .pointerInput(uiState.currentTool, inlineEditingBlock) {
                    if (uiState.currentTool == EditorTool.SELECT || uiState.currentTool == EditorTool.TEXT) {
                        detectTapGestures(
                            onTap = { tapOffset ->
                                onCanvasTap(tapOffset)
                            }
                        )
                    }
                }
        ) {
            // PDF Background
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "PDF Page",
                modifier = Modifier.fillMaxWidth()
            )

            // Draw elements overlay
            Canvas(modifier = Modifier.matchParentSize()) {
                // Draw whiteout elements first (they should be below other elements)
                uiState.elements.filterIsInstance<PdfElement.WhiteoutElement>().forEach { element ->
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(element.x, element.y),
                        size = androidx.compose.ui.geometry.Size(element.width, element.height)
                    )
                }

                // Draw highlights
                uiState.elements.filterIsInstance<PdfElement.HighlightElement>().forEach { element ->
                    drawRect(
                        color = Color(element.color),
                        topLeft = Offset(element.x, element.y),
                        size = androidx.compose.ui.geometry.Size(element.width, element.height)
                    )
                }

                // Draw existing drawing elements
                uiState.elements.filterIsInstance<PdfElement.DrawingElement>().forEach { element ->
                    if (element.points.size > 1) {
                        val path = Path()
                        element.points.forEachIndexed { index, point ->
                            if (index == 0) {
                                path.moveTo(point.x, point.y)
                            } else {
                                path.lineTo(point.x, point.y)
                            }
                        }
                        drawPath(
                            path = path,
                            color = Color(element.color),
                            style = Stroke(
                                width = element.strokeWidth,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }

                // Draw current drawing path
                if (uiState.isDrawing && uiState.currentDrawingPoints.size > 1) {
                    val path = Path()
                    uiState.currentDrawingPoints.forEachIndexed { index, point ->
                        if (index == 0) {
                            path.moveTo(point.x, point.y)
                        } else {
                            path.lineTo(point.x, point.y)
                        }
                    }
                    drawPath(
                        path = path,
                        color = uiState.currentColor,
                        style = Stroke(
                            width = uiState.currentStrokeWidth,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                // Highlight selected PDF text block
                uiState.selectedTextBlockId?.let { selectedId ->
                    uiState.extractedTextBlocks.find { it.id == selectedId }?.let { block ->
                        val scale = uiState.bitmapScale
                        drawRect(
                            color = Color(0xFF2196F3).copy(alpha = 0.2f),
                            topLeft = Offset(block.x * scale, block.y * scale),
                            size = androidx.compose.ui.geometry.Size(block.width * scale, block.height * scale)
                        )
                        drawRect(
                            color = Color(0xFF2196F3),
                            topLeft = Offset(block.x * scale, block.y * scale),
                            size = androidx.compose.ui.geometry.Size(block.width * scale, block.height * scale),
                            style = Stroke(width = 3f)
                        )
                    }
                }

                // Draw current rectangle being dragged (for whiteout/highlight preview)
                if (uiState.isDraggingRect && uiState.rectStartPoint != null && uiState.rectEndPoint != null) {
                    val startPoint = uiState.rectStartPoint!!
                    val endPoint = uiState.rectEndPoint!!
                    val x = minOf(startPoint.x, endPoint.x)
                    val y = minOf(startPoint.y, endPoint.y)
                    val width = kotlin.math.abs(endPoint.x - startPoint.x)
                    val height = kotlin.math.abs(endPoint.y - startPoint.y)

                    val rectColor = when (uiState.currentTool) {
                        EditorTool.WHITEOUT -> Color.White
                        EditorTool.HIGHLIGHT -> uiState.currentColor.copy(alpha = 0.4f)
                        else -> Color.Gray.copy(alpha = 0.3f)
                    }

                    drawRect(
                        color = rectColor,
                        topLeft = Offset(x, y),
                        size = androidx.compose.ui.geometry.Size(width, height)
                    )

                    // Draw border for visibility
                    drawRect(
                        color = Color.Gray,
                        topLeft = Offset(x, y),
                        size = androidx.compose.ui.geometry.Size(width, height),
                        style = Stroke(width = 2f)
                    )
                }
            }

            // Text elements (as composables for interactivity)
            uiState.elements.filterIsInstance<PdfElement.TextElement>().forEach { element ->
                DraggableTextElement(
                    element = element,
                    isSelected = element.id == uiState.selectedElementId,
                    onDragEnd = { newX, newY -> onElementDrag(element.id, newX, newY) }
                )
            }

            // Image elements
            uiState.elements.filterIsInstance<PdfElement.ImageElement>().forEach { element ->
                DraggableImageElement(
                    element = element,
                    isSelected = element.id == uiState.selectedElementId,
                    onDragEnd = { newX, newY -> onElementDrag(element.id, newX, newY) }
                )
            }

            // Inline text editor (PDFfiller-style)
            if (inlineEditingBlock != null) {
                InlineTextEditor(
                    block = inlineEditingBlock,
                    text = inlineEditingText,
                    bitmapScale = uiState.bitmapScale,
                    onTextChange = onInlineTextChange,
                    onEditComplete = { onInlineEditComplete(inlineEditingBlock, inlineEditingText) },
                    onCancel = onInlineEditCancel
                )
            }
        }
    }

    // Tool hint
    if (uiState.elements.isEmpty() && !uiState.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                ),
                modifier = Modifier.padding(32.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        when (uiState.currentTool) {
                            EditorTool.TEXT -> Icons.Outlined.TextFields
                            EditorTool.DRAW -> Icons.Outlined.Draw
                            EditorTool.WHITEOUT -> Icons.Outlined.FormatColorReset
                            EditorTool.HIGHLIGHT -> Icons.Outlined.Highlight
                            else -> Icons.Outlined.TouchApp
                        },
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = when (uiState.currentTool) {
                            EditorTool.TEXT -> "Tap anywhere to add text"
                            EditorTool.DRAW -> "Draw on the document"
                            EditorTool.WHITEOUT -> "Drag to cover existing text"
                            EditorTool.HIGHLIGHT -> "Drag to highlight text"
                            EditorTool.SELECT -> "Select a tool to start editing"
                            else -> "Select a tool to start"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun DraggableTextElement(
    element: PdfElement.TextElement,
    isSelected: Boolean,
    onDragEnd: (Float, Float) -> Unit
) {
    var offsetX by remember(element.id, element.x) { mutableFloatStateOf(element.x) }
    var offsetY by remember(element.id, element.y) { mutableFloatStateOf(element.y) }

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .then(
                if (isSelected) {
                    Modifier
                        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            RoundedCornerShape(4.dp)
                        )
                } else Modifier
            )
            .pointerInput(element.id) {
                detectDragGestures(
                    onDragEnd = { onDragEnd(offsetX, offsetY) },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                )
            }
            .padding(4.dp)
    ) {
        Text(
            text = element.text,
            fontSize = element.fontSize.sp,
            color = Color(element.color),
            fontWeight = if (element.fontBold) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun DraggableImageElement(
    element: PdfElement.ImageElement,
    isSelected: Boolean,
    onDragEnd: (Float, Float) -> Unit
) {
    var offsetX by remember(element.id, element.x) { mutableFloatStateOf(element.x) }
    var offsetY by remember(element.id, element.y) { mutableFloatStateOf(element.y) }

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .size(element.width.dp, element.height.dp)
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                } else Modifier
            )
            .pointerInput(element.id) {
                detectDragGestures(
                    onDragEnd = { onDragEnd(offsetX, offsetY) },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                )
            }
    ) {
        Image(
            bitmap = element.bitmap.asImageBitmap(),
            contentDescription = "Image",
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun findElementAtPosition(elements: List<PdfElement>, position: Offset): PdfElement? {
    return elements.lastOrNull { element ->
        when (element) {
            is PdfElement.TextElement -> {
                val width = element.text.length * element.fontSize * 0.6f
                val height = element.fontSize * 1.5f
                position.x >= element.x && position.x <= element.x + width &&
                        position.y >= element.y && position.y <= element.y + height
            }
            is PdfElement.ImageElement -> {
                position.x >= element.x && position.x <= element.x + element.width &&
                        position.y >= element.y && position.y <= element.y + element.height
            }
            is PdfElement.HighlightElement -> {
                position.x >= element.x && position.x <= element.x + element.width &&
                        position.y >= element.y && position.y <= element.y + element.height
            }
            is PdfElement.WhiteoutElement -> {
                position.x >= element.x && position.x <= element.x + element.width &&
                        position.y >= element.y && position.y <= element.y + element.height
            }
            is PdfElement.DrawingElement -> false // Drawings are not selectable by tap
        }
    }
}

@Composable
private fun EditorToolbar(
    currentTool: EditorTool,
    currentColor: Color,
    onToolSelected: (EditorTool) -> Unit,
    onColorClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolButton(
                icon = Icons.Outlined.TouchApp,
                label = "Select",
                isSelected = currentTool == EditorTool.SELECT,
                onClick = { onToolSelected(EditorTool.SELECT) }
            )

            ToolButton(
                icon = Icons.Outlined.TextFields,
                label = "Text",
                isSelected = currentTool == EditorTool.TEXT,
                onClick = { onToolSelected(EditorTool.TEXT) }
            )

            ToolButton(
                icon = Icons.Outlined.Draw,
                label = "Draw",
                isSelected = currentTool == EditorTool.DRAW,
                onClick = { onToolSelected(EditorTool.DRAW) }
            )

            ToolButton(
                icon = Icons.Outlined.FormatColorReset,
                label = "Whiteout",
                isSelected = currentTool == EditorTool.WHITEOUT,
                onClick = { onToolSelected(EditorTool.WHITEOUT) }
            )

            ToolButton(
                icon = Icons.Outlined.Highlight,
                label = "Highlight",
                isSelected = currentTool == EditorTool.HIGHLIGHT,
                onClick = { onToolSelected(EditorTool.HIGHLIGHT) }
            )

            Spacer(modifier = Modifier.weight(1f))

            // Color picker button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(currentColor)
                    .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { onColorClick() }
            )
        }
    }
}

@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            tint = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PageNavigationBar(
    currentPage: Int,
    pageCount: Int,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPreviousPage,
                enabled = currentPage > 0
            ) {
                Icon(Icons.Default.ChevronLeft, "Previous")
            }

            Text(
                text = "Page ${currentPage + 1} of $pageCount",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            IconButton(
                onClick = onNextPage,
                enabled = currentPage < pageCount - 1
            ) {
                Icon(Icons.Default.ChevronRight, "Next")
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Loading PDF...", color = Color.White)
        }
    }
}

@Composable
private fun ErrorContent(error: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                Icons.Outlined.Error,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                error,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextInputDialog(
    initialText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialText.isEmpty()) "Add Text" else "Edit Text") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Enter text...") },
                minLines = 2,
                maxLines = 5
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank()
            ) {
                Text(if (initialText.isEmpty()) "Add" else "Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ColorPickerDialog(
    currentColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = listOf(
        Color.Black,
        Color.Red,
        Color.Blue,
        Color(0xFF2563EB),
        Color(0xFF10B981),
        Color(0xFFF59E0B),
        Color(0xFF8B5CF6),
        Color(0xFFEC4899),
        Color.Yellow,
        Color.Cyan
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Color") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    colors.take(5).forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (currentColor == color) {
                                        Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                    } else Modifier
                                )
                                .clickable { onColorSelected(color) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    colors.drop(5).forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (currentColor == color) {
                                        Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                    } else Modifier
                                )
                                .clickable { onColorSelected(color) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun InlineTextEditor(
    block: ExtractedTextBlock,
    text: String,
    bitmapScale: Float,
    onTextChange: (String) -> Unit,
    onEditComplete: () -> Unit,
    onCancel: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current

    // Calculate position in bitmap coordinates (pixels)
    val x = (block.x * bitmapScale).roundToInt()
    val y = (block.y * bitmapScale).roundToInt()
    val widthPx = (block.width * bitmapScale).coerceAtLeast(150f)
    val heightPx = (block.height * bitmapScale).coerceAtLeast(40f)

    // Convert pixel values to dp for Compose
    val widthDp = with(density) { widthPx.toDp() }
    val heightDp = with(density) { heightPx.toDp() }
    val fontSize = (block.fontSize * bitmapScale * 0.8f).coerceIn(10f, 32f)

    // Request focus when the editor appears
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(x - 4, y - 4) }  // Slight offset to cover original text
            .width(widthDp + 80.dp)  // Extra width for buttons
            .heightIn(min = heightDp + 8.dp)
    ) {
        // White background to cover original text + edit border
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(4.dp),
            color = Color.White,
            shadowElevation = 2.dp,
            border = BorderStroke(2.dp, Color(0xFF2196F3))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    textStyle = TextStyle(
                        fontSize = fontSize.sp,
                        color = Color(block.fontColor),
                        fontWeight = if (block.isBold) FontWeight.Bold else FontWeight.Normal
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(Color(0xFF2196F3))
                )

                // Done button
                IconButton(
                    onClick = {
                        keyboardController?.hide()
                        onEditComplete()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Done",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Cancel button
                IconButton(
                    onClick = {
                        keyboardController?.hide()
                        onCancel()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = Color(0xFFF44336),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

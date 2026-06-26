package com.pdfmaster.app.presentation.ui.annotate

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdfmaster.app.domain.model.AnnotationTool
import com.pdfmaster.app.presentation.theme.*
import com.pdfmaster.app.presentation.ui.premium.PremiumGateDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotateScreen(
    uri: String,
    onNavigateBack: () -> Unit,
    onSaveComplete: ((String) -> Unit)? = null,
    onNavigateToPremium: () -> Unit = {},
    viewModel: AnnotateViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uri) { viewModel.loadPdf(context, Uri.parse(uri)) }

    PremiumGateDialog(
        prompt = uiState.premiumPrompt,
        onDismiss = viewModel::clearPremiumPrompt,
        onUpgrade = { viewModel.clearPremiumPrompt(); onNavigateToPremium() },
    )

    // Handle save completion
    LaunchedEffect(uiState.outputUri) {
        uiState.outputUri?.let {
            onSaveComplete?.invoke(it)
            onNavigateBack()
        }
    }

    // Handle errors
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val annotationColors = listOf(
        Color(0xFFFEF08A), Color(0xFFBBF7D0), Color(0xFFBFDBFE),
        Color(0xFFFBCFE8), Color(0xFFFED7AA), Color.Red, Color.Black
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Annotate") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.undo() }, enabled = uiState.canUndo) {
                        Icon(Icons.Filled.Undo, "Undo")
                    }
                    IconButton(
                        onClick = { viewModel.save(context) },
                        enabled = !uiState.isSaving && uiState.annotations.isNotEmpty()
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        } else {
                            Icon(Icons.Outlined.Save, "Save")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column {
                    // Tool selection
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item {
                            AnnotationToolButton(Icons.Outlined.Highlight, "Highlight", uiState.selectedTool == AnnotationTool.HIGHLIGHT) { viewModel.selectTool(AnnotationTool.HIGHLIGHT) }
                        }
                        item {
                            AnnotationToolButton(Icons.Outlined.FormatUnderlined, "Underline", uiState.selectedTool == AnnotationTool.UNDERLINE) { viewModel.selectTool(AnnotationTool.UNDERLINE) }
                        }
                        item {
                            AnnotationToolButton(Icons.Outlined.StrikethroughS, "Strike", uiState.selectedTool == AnnotationTool.STRIKETHROUGH) { viewModel.selectTool(AnnotationTool.STRIKETHROUGH) }
                        }
                        item {
                            AnnotationToolButton(Icons.Outlined.Draw, "Draw", uiState.selectedTool == AnnotationTool.FREEHAND) { viewModel.selectTool(AnnotationTool.FREEHAND) }
                        }
                        item {
                            AnnotationToolButton(Icons.Outlined.TextFields, "Text", uiState.selectedTool == AnnotationTool.TEXT) { viewModel.selectTool(AnnotationTool.TEXT) }
                        }
                        item {
                            AnnotationToolButton(Icons.Outlined.Rectangle, "Shape", uiState.selectedTool == AnnotationTool.SHAPE_RECTANGLE) { viewModel.selectTool(AnnotationTool.SHAPE_RECTANGLE) }
                        }
                    }
                    // Color selection
                    if (uiState.selectedTool != AnnotationTool.NONE) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).navigationBarsPadding(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(annotationColors.size) { index ->
                                val color = annotationColors[index]
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .then(if (uiState.selectedColor == color) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                                        .clickable { viewModel.selectColor(color) }
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(uiState.pages) { index, bitmap ->
                        Card(
                            modifier = Modifier.fillMaxWidth()
                                // Key on the tool so the detector rebinds when it changes
                                // (pointerInput(Unit) captured a stale tool). onDragEnd
                                // commits the stroke via finishStroke() — without it the
                                // freehand path accumulated points but never saved anything.
                                .pointerInput(uiState.selectedTool) {
                                    if (uiState.selectedTool == AnnotationTool.FREEHAND) {
                                        detectDragGestures(
                                            onDrag = { change, _ ->
                                                viewModel.addDrawingPoint(index, change.position)
                                            },
                                            onDragEnd = { viewModel.finishStroke(index) },
                                        )
                                    }
                                }
                        ) {
                            if (bitmap != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Page ${index + 1}",
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnnotationToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) }
    )
}

package com.pdfmaster.app.presentation.ui.editor

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdfmaster.app.presentation.theme.*
import com.pdfmaster.app.presentation.ui.editor.components.*

enum class EditorTool {
    NONE,
    SELECT,
    TEXT,
    IMAGE,
    DRAW
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    uri: String,
    title: String?,
    onNavigateBack: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    var selectedTool by remember { mutableStateOf(EditorTool.SELECT) }
    var showTextEditor by remember { mutableStateOf(false) }
    var showImagePicker by remember { mutableStateOf(false) }
    var showUnsavedDialog by remember { mutableStateOf(false) }

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
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Edit PDF",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
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
                        enabled = uiState.canUndo
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = uiState.canRedo
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }
                    IconButton(
                        onClick = { viewModel.save(context) },
                        enabled = uiState.hasChanges
                    ) {
                        Icon(Icons.Outlined.Save, contentDescription = "Save")
                    }
                }
            )
        },
        bottomBar = {
            EditorToolbar(
                selectedTool = selectedTool,
                onToolSelected = { tool ->
                    selectedTool = tool
                    when (tool) {
                        EditorTool.TEXT -> showTextEditor = true
                        EditorTool.IMAGE -> showImagePicker = true
                        else -> {}
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsIndexed(uiState.pages) { index, bitmap ->
                            EditorPageCard(
                                bitmap = bitmap,
                                pageNumber = index + 1,
                                isSelected = uiState.selectedPage == index,
                                onPageClick = { viewModel.selectPage(index) },
                                onTextEdit = { offset ->
                                    viewModel.selectPage(index)
                                    showTextEditor = true
                                }
                            )
                        }
                    }
                }
            }

            // Saving indicator
            if (uiState.isSaving) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Saving changes...")
                        }
                    }
                }
            }
        }
    }

    // Text Editor Sheet
    if (showTextEditor) {
        TextEditorSheet(
            onDismiss = { showTextEditor = false },
            onAddText = { text, fontSize, color ->
                viewModel.addText(text, fontSize, color)
                showTextEditor = false
            }
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
                        viewModel.save(context)
                        showUnsavedDialog = false
                        onNavigateBack()
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
}

@Composable
private fun EditorToolbar(
    selectedTool: EditorTool,
    onToolSelected: (EditorTool) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            EditorToolButton(
                icon = Icons.Outlined.TouchApp,
                label = "Select",
                isSelected = selectedTool == EditorTool.SELECT,
                onClick = { onToolSelected(EditorTool.SELECT) }
            )
            EditorToolButton(
                icon = Icons.Outlined.TextFields,
                label = "Text",
                isSelected = selectedTool == EditorTool.TEXT,
                onClick = { onToolSelected(EditorTool.TEXT) }
            )
            EditorToolButton(
                icon = Icons.Outlined.Image,
                label = "Image",
                isSelected = selectedTool == EditorTool.IMAGE,
                onClick = { onToolSelected(EditorTool.IMAGE) }
            )
            EditorToolButton(
                icon = Icons.Outlined.Draw,
                label = "Draw",
                isSelected = selectedTool == EditorTool.DRAW,
                onClick = { onToolSelected(EditorTool.DRAW) }
            )
        }
    }
}

@Composable
private fun EditorToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun EditorPageCard(
    bitmap: Bitmap?,
    pageNumber: Int,
    isSelected: Boolean,
    onPageClick: () -> Unit,
    onTextEdit: (offset: androidx.compose.ui.geometry.Offset) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                } else Modifier
            ),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            onPageClick()
                        },
                        onDoubleTap = { offset ->
                            onTextEdit(offset)
                        }
                    )
                }
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Page $pageNumber",
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.707f)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            }

            // Page number badge
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                shape = RoundedCornerShape(4.dp),
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Text(
                    text = pageNumber.toString(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}


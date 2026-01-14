package com.pdfmaster.app.presentation.ui.editor

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdfmaster.app.presentation.theme.*
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageManagerScreen(
    uri: String,
    onNavigateBack: () -> Unit,
    viewModel: PageManagerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var selectedPages by remember { mutableStateOf(setOf<Int>()) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uri) {
        viewModel.loadPdf(context, Uri.parse(uri))
    }

    // Show error as snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val gridState = rememberLazyGridState()
    val reorderableState = rememberReorderableLazyGridState(gridState) { from, to ->
        viewModel.reorderPage(from.index, to.index)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedPages.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSelectionMode = false
                            selectedPages = emptySet()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                selectedPages = if (selectedPages.size == uiState.pageCount) {
                                    emptySet()
                                } else {
                                    (0 until uiState.pageCount).toSet()
                                }
                            }
                        ) {
                            Icon(
                                if (selectedPages.size == uiState.pageCount) Icons.Default.Deselect else Icons.Outlined.SelectAll,
                                contentDescription = "Select all"
                            )
                        }
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            enabled = selectedPages.isNotEmpty()
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Manage Pages",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${uiState.pageCount} pages",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.addBlankPage() }) {
                            Icon(Icons.Outlined.Add, contentDescription = "Add page")
                        }
                        IconButton(
                            onClick = { viewModel.save(context) { /* Saved successfully */ } },
                            enabled = uiState.hasChanges && !uiState.isSaving
                        ) {
                            Icon(Icons.Outlined.Save, contentDescription = "Save")
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (!isSelectionMode) {
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
                        TextButton(onClick = { isSelectionMode = true }) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Select")
                        }
                        TextButton(onClick = {
                            (0 until uiState.pageCount).forEach { viewModel.rotatePage(it) }
                        }) {
                            Icon(Icons.Outlined.RotateRight, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Rotate All")
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = 120.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(uiState.pages, key = { index, _ -> index }) { index, bitmap ->
                            ReorderableItem(reorderableState, key = index) { isDragging ->
                                val elevation by animateDpAsState(if (isDragging) 8.dp else 2.dp)

                                PageThumbnailCard(
                                    bitmap = bitmap,
                                    pageNumber = index + 1,
                                    isSelected = selectedPages.contains(index),
                                    isSelectionMode = isSelectionMode,
                                    elevation = elevation,
                                    onLongClick = {
                                        if (!isSelectionMode) {
                                            isSelectionMode = true
                                            selectedPages = setOf(index)
                                        }
                                    },
                                    onClick = {
                                        if (isSelectionMode) {
                                            selectedPages = if (selectedPages.contains(index)) {
                                                selectedPages - index
                                            } else {
                                                selectedPages + index
                                            }
                                        }
                                    },
                                    onRotate = { viewModel.rotatePage(index) },
                                    onDelete = { viewModel.deletePage(index) },
                                    onDuplicate = { viewModel.duplicatePage(index) },
                                    modifier = Modifier.draggableHandle()
                                )
                            }
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
                    Card(shape = RoundedCornerShape(16.dp)) {
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

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Pages") },
            text = { Text("Are you sure you want to delete ${selectedPages.size} page(s)?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePages(selectedPages.toList())
                        selectedPages = emptySet()
                        isSelectionMode = false
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageThumbnailCard(
    bitmap: Bitmap?,
    pageNumber: Int,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    elevation: androidx.compose.ui.unit.Dp,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onRotate: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .aspectRatio(0.707f)
            .shadow(elevation, RoundedCornerShape(8.dp))
            .then(
                if (isSelected) {
                    Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                } else Modifier
            ),
        shape = RoundedCornerShape(8.dp),
        onClick = onClick
    ) {
        Box {
            if (bitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Page $pageNumber",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }

            // Selection checkbox
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                )
            }

            // Page number
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(4.dp),
                shape = RoundedCornerShape(4.dp),
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Text(
                    text = pageNumber.toString(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }

            // More options
            if (!isSelectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.4f))
                    ) {
                        Icon(
                            Icons.Outlined.MoreVert,
                            contentDescription = "Options",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rotate") },
                            onClick = { showMenu = false; onRotate() },
                            leadingIcon = { Icon(Icons.Outlined.RotateRight, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate") },
                            onClick = { showMenu = false; onDuplicate() },
                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = { showMenu = false; onDelete() },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null) }
                        )
                    }
                }
            }
        }
    }
}

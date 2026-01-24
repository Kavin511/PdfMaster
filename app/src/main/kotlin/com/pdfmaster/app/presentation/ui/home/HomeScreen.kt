package com.pdfmaster.app.presentation.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdfmaster.app.domain.model.PdfDocument
import com.pdfmaster.app.presentation.theme.*
import com.pdfmaster.app.presentation.ui.home.components.*
import com.pdfmaster.app.util.FileUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenPdf: (String, String?) -> Unit,
    onNavigateToScanner: () -> Unit,
    onNavigateToMerge: () -> Unit,
    onNavigateToSplit: () -> Unit,
    onNavigateToCompress: () -> Unit,
    onNavigateToConvert: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPremium: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val pagerState = rememberPagerState(pageCount = { 3 })
    val tabs = listOf("All Files", "Recent", "Favorites")

    var showSortMenu by remember { mutableStateOf(false) }

    // File picker launcher
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = FileUtils.getFileName(context, it)
            onOpenPdf(it.toString(), fileName)
        }
    }

    // Image picker for conversion
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            onNavigateToConvert()
        }
    }

    Scaffold(
        topBar = {
            if (uiState.isSearchActive) {
                SearchTopBar(
                    query = uiState.searchQuery,
                    onQueryChange = { viewModel.search(it) },
                    onClose = { viewModel.setSearchActive(false) }
                )
            } else {
                HomeTopBar(
                    onSearchClick = { viewModel.setSearchActive(true) },
                    onViewModeToggle = { viewModel.toggleViewMode() },
                    isGridView = uiState.isGridView,
                    onSortClick = { showSortMenu = true },
                    onSettingsClick = onNavigateToSettings,
                    onPremiumClick = onNavigateToPremium
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = PdfMasterShapes.Fab
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Import PDF"
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search Results or Normal Content
            if (uiState.isSearchActive) {
                SearchResultsContent(
                    results = uiState.searchResults,
                    query = uiState.searchQuery,
                    isGridView = uiState.isGridView,
                    onFileClick = { doc -> onOpenPdf(doc.uri, doc.name) },
                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                    onDelete = { viewModel.deleteFile(it) },
                    onShare = { viewModel.shareFile(context, it) }
                )
            } else {
            // Quick Tools Section
            QuickToolsSection(
                onScanClick = onNavigateToScanner,
                onMergeClick = onNavigateToMerge,
                onSplitClick = onNavigateToSplit,
                onCompressClick = onNavigateToCompress,
                onConvertClick = onNavigateToConvert
            )

            // Tabs
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    if (pagerState.currentPage < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                            height = 3.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                divider = {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 0.5.dp
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (pagerState.currentPage == index) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // Pager Content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val files = when (page) {
                    0 -> uiState.allFiles
                    1 -> uiState.recentFiles
                    2 -> uiState.favoriteFiles
                    else -> emptyList()
                }

                if (files.isEmpty()) {
                    EmptyState(
                        page = page,
                        onImportClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                        onScanClick = onNavigateToScanner
                    )
                } else {
                    FileList(
                        files = files,
                        isGridView = uiState.isGridView,
                        onFileClick = { file ->
                            onOpenPdf(file.uri, file.name)
                        },
                        onFavoriteToggle = { file ->
                            viewModel.toggleFavorite(file)
                        },
                        onDeleteFile = { file ->
                            viewModel.deleteFile(file)
                        },
                        onShareFile = { file ->
                            viewModel.shareFile(context, file)
                        }
                    )
                }
            }
        }
        } // end of else (not search)
    }

    // Sort Menu
    if (showSortMenu) {
        SortOptionsMenu(
            currentSort = uiState.sortOrder,
            onSortSelected = { order ->
                viewModel.setSortOrder(order)
                showSortMenu = false
            },
            onDismiss = { showSortMenu = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    onSearchClick: () -> Unit,
    onViewModeToggle: () -> Unit,
    isGridView: Boolean,
    onSortClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onPremiumClick: () -> Unit
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // App Logo/Icon
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Primary, PrimaryLight)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = "PDF Master",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        actions = {
            IconButton(onClick = onSearchClick) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "Search"
                )
            }
            IconButton(onClick = onViewModeToggle) {
                Icon(
                    imageVector = if (isGridView) Icons.Outlined.ViewList else Icons.Outlined.GridView,
                    contentDescription = "Toggle view"
                )
            }
            IconButton(onClick = onSortClick) {
                Icon(
                    imageVector = Icons.Outlined.Sort,
                    contentDescription = "Sort"
                )
            }
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Settings"
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun QuickToolsSection(
    onScanClick: () -> Unit,
    onMergeClick: () -> Unit,
    onSplitClick: () -> Unit,
    onCompressClick: () -> Unit,
    onConvertClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "Quick Tools",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                QuickToolCard(
                    icon = Icons.Outlined.CameraAlt,
                    title = "Scan",
                    backgroundColor = ScanColor,
                    onClick = onScanClick
                )
            }
            item {
                QuickToolCard(
                    icon = Icons.Outlined.CallMerge,
                    title = "Merge",
                    backgroundColor = MergeColor,
                    onClick = onMergeClick
                )
            }
            item {
                QuickToolCard(
                    icon = Icons.Outlined.CallSplit,
                    title = "Split",
                    backgroundColor = SplitColor,
                    onClick = onSplitClick
                )
            }
            item {
                QuickToolCard(
                    icon = Icons.Outlined.Compress,
                    title = "Compress",
                    backgroundColor = CompressColor,
                    onClick = onCompressClick
                )
            }
            item {
                QuickToolCard(
                    icon = Icons.Outlined.SwapHoriz,
                    title = "Convert",
                    backgroundColor = ConvertColor,
                    onClick = onConvertClick
                )
            }
        }
    }
}

@Composable
private fun QuickToolCard(
    icon: ImageVector,
    title: String,
    backgroundColor: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(80.dp)
            .height(90.dp),
        shape = PdfMasterShapes.ToolCard,
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(backgroundColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = backgroundColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun FileList(
    files: List<PdfDocument>,
    isGridView: Boolean,
    onFileClick: (PdfDocument) -> Unit,
    onFavoriteToggle: (PdfDocument) -> Unit,
    onDeleteFile: (PdfDocument) -> Unit,
    onShareFile: (PdfDocument) -> Unit
) {
    if (isGridView) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(files, key = { it.id }) { file ->
                FileGridItem(
                    file = file,
                    onClick = { onFileClick(file) },
                    onFavoriteToggle = { onFavoriteToggle(file) },
                    onDelete = { onDeleteFile(file) },
                    onShare = { onShareFile(file) }
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(files, key = { it.id }) { file ->
                FileListItem(
                    file = file,
                    onClick = { onFileClick(file) },
                    onFavoriteToggle = { onFavoriteToggle(file) },
                    onDelete = { onDeleteFile(file) },
                    onShare = { onShareFile(file) }
                )
            }
        }
    }
}

@Composable
private fun EmptyState(
    page: Int,
    onImportClick: () -> Unit,
    onScanClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val (icon, title, description) = when (page) {
            0 -> Triple(
                Icons.Outlined.FolderOpen,
                "No PDF files found",
                "Import or scan documents to get started"
            )
            1 -> Triple(
                Icons.Outlined.History,
                "No recent files",
                "Files you open will appear here"
            )
            else -> Triple(
                Icons.Outlined.StarOutline,
                "No favorites yet",
                "Star files to add them to favorites"
            )
        }

        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (page == 0) {
            Spacer(modifier = Modifier.height(32.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onScanClick,
                    shape = PdfMasterShapes.Button
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan")
                }

                Button(
                    onClick = onImportClick,
                    shape = PdfMasterShapes.Button
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FileOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import")
                }
            }
        }
    }
}

@Composable
private fun SortOptionsMenu(
    currentSort: com.pdfmaster.app.domain.repository.SortOrder,
    onSortSelected: (com.pdfmaster.app.domain.repository.SortOrder) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sort by") },
        text = {
            Column {
                com.pdfmaster.app.domain.repository.SortOrder.entries.forEach { order ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSortSelected(order) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentSort == order,
                            onClick = { onSortSelected(order) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = when (order) {
                                com.pdfmaster.app.domain.repository.SortOrder.NAME_ASC -> "Name (A-Z)"
                                com.pdfmaster.app.domain.repository.SortOrder.NAME_DESC -> "Name (Z-A)"
                                com.pdfmaster.app.domain.repository.SortOrder.DATE_NEWEST -> "Date (Newest)"
                                com.pdfmaster.app.domain.repository.SortOrder.DATE_OLDEST -> "Date (Oldest)"
                                com.pdfmaster.app.domain.repository.SortOrder.SIZE_LARGEST -> "Size (Largest)"
                                com.pdfmaster.app.domain.repository.SortOrder.SIZE_SMALLEST -> "Size (Smallest)"
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    TopAppBar(
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search PDFs...") },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
            }
        },
        actions = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                }
            }
        }
    )
}

@Composable
private fun SearchResultsContent(
    results: List<PdfDocument>,
    query: String,
    isGridView: Boolean,
    onFileClick: (PdfDocument) -> Unit,
    onToggleFavorite: (PdfDocument) -> Unit,
    onDelete: (PdfDocument) -> Unit,
    onShare: (PdfDocument) -> Unit
) {
    when {
        query.isBlank() -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Search for PDF files",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        results.isEmpty() -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Outlined.SearchOff,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "No results for \"$query\"",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        else -> {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    "${results.size} result${if (results.size != 1) "s" else ""} for \"$query\"",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                FileList(
                    files = results,
                    isGridView = isGridView,
                    onFileClick = onFileClick,
                    onFavoriteToggle = onToggleFavorite,
                    onDeleteFile = onDelete,
                    onShareFile = onShare
                )
            }
        }
    }
}

package com.pdfmaster.app.presentation.ui.viewer

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdfmaster.app.domain.repository.PdfReadingMode
import com.pdfmaster.app.presentation.theme.*
import com.pdfmaster.app.presentation.ui.viewer.components.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    uri: String,
    title: String?,
    onNavigateBack: () -> Unit,
    onNavigateToEditor: (String) -> Unit,
    onNavigateToPageManager: (String) -> Unit,
    onNavigateToAnnotate: (String) -> Unit,
    onNavigateToSign: (String) -> Unit,
    viewModel: ViewerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var showBottomSheet by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }
    var showPageSlider by remember { mutableStateOf(false) }

    // Load PDF on first composition
    LaunchedEffect(uri) {
        viewModel.loadPdf(context, Uri.parse(uri), title)
    }

    // Track current visible page
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { index ->
                viewModel.setCurrentPage(index)
            }
    }

    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = uiState.showControls,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                ViewerTopBar(
                    title = uiState.fileName,
                    readingMode = uiState.readingMode,
                    onNavigateBack = onNavigateBack,
                    onSearchClick = { showSearchBar = true },
                    onReadingModeClick = { viewModel.cycleReadingMode() },
                    onMoreClick = { showBottomSheet = true }
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = uiState.showControls,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
            ) {
                ViewerBottomBar(
                    currentPage = uiState.currentPage + 1,
                    totalPages = uiState.pageCount,
                    onPageClick = { showPageSlider = true },
                    onEditClick = { onNavigateToEditor(uri) },
                    onAnnotateClick = { onNavigateToAnnotate(uri) },
                    onSignClick = { onNavigateToSign(uri) },
                    onPagesClick = { onNavigateToPageManager(uri) }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { viewModel.toggleControls() }
                    )
                }
        ) {
            when {
                uiState.isLoading -> {
                    LoadingState()
                }
                uiState.error != null -> {
                    ErrorState(
                        message = uiState.error!!,
                        onRetry = { viewModel.loadPdf(context, Uri.parse(uri), title) }
                    )
                }
                uiState.isPasswordProtected && !uiState.isUnlocked -> {
                    PasswordPrompt(
                        onSubmit = { password ->
                            viewModel.unlockPdf(context, Uri.parse(uri), password)
                        },
                        isError = uiState.passwordError
                    )
                }
                else -> {
                    PdfPagesList(
                        pages = uiState.pages,
                        listState = listState,
                        zoom = uiState.zoom,
                        readingMode = uiState.readingMode,
                        onZoomChange = { viewModel.setZoom(it) }
                    )
                }
            }

            // Page indicator
            AnimatedVisibility(
                visible = uiState.showControls && uiState.pageCount > 0,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = "${uiState.currentPage + 1} / ${uiState.pageCount}",
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }

    // Search Sheet
    if (showSearchBar) {
        SearchSheet(
            query = uiState.searchQuery,
            results = uiState.searchResults,
            onQueryChange = { viewModel.search(it) },
            onResultClick = { pageNumber ->
                scope.launch {
                    listState.animateScrollToItem(pageNumber)
                }
                showSearchBar = false
            },
            onDismiss = { showSearchBar = false }
        )
    }

    // Page Slider Dialog
    if (showPageSlider) {
        PageSliderDialog(
            currentPage = uiState.currentPage + 1,
            totalPages = uiState.pageCount,
            onPageSelected = { page ->
                scope.launch {
                    listState.animateScrollToItem(page - 1)
                }
                showPageSlider = false
            },
            onDismiss = { showPageSlider = false }
        )
    }

    // More Options Bottom Sheet
    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            shape = PdfMasterShapes.BottomSheet
        ) {
            ViewerOptionsSheet(
                onShareClick = {
                    showBottomSheet = false
                    viewModel.shareFile(context)
                },
                onPrintClick = {
                    showBottomSheet = false
                    viewModel.printFile(context)
                },
                onDetailsClick = {
                    showBottomSheet = false
                    // Show details dialog
                },
                onDismiss = { showBottomSheet = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewerTopBar(
    title: String,
    readingMode: PdfReadingMode,
    onNavigateBack: () -> Unit,
    onSearchClick: () -> Unit,
    onReadingModeClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                maxLines = 1,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
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
            // Reading mode toggle
            IconButton(onClick = onReadingModeClick) {
                Icon(
                    imageVector = when (readingMode) {
                        PdfReadingMode.NORMAL -> Icons.Outlined.LightMode
                        PdfReadingMode.DARK -> Icons.Outlined.DarkMode
                        PdfReadingMode.SEPIA -> Icons.Outlined.WbSunny
                        PdfReadingMode.NIGHT -> Icons.Outlined.NightsStay
                    },
                    contentDescription = "Reading Mode: ${readingMode.name}"
                )
            }
            IconButton(onClick = onMoreClick) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = "More"
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        )
    )
}

@Composable
private fun ViewerBottomBar(
    currentPage: Int,
    totalPages: Int,
    onPageClick: () -> Unit,
    onEditClick: () -> Unit,
    onAnnotateClick: () -> Unit,
    onSignClick: () -> Unit,
    onPagesClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ViewerAction(
                icon = Icons.Outlined.Edit,
                label = "Edit",
                onClick = onEditClick
            )
            ViewerAction(
                icon = Icons.Outlined.Draw,
                label = "Annotate",
                onClick = onAnnotateClick
            )
            ViewerAction(
                icon = Icons.Outlined.Draw,
                label = "Sign",
                onClick = onSignClick
            )
            ViewerAction(
                icon = Icons.Outlined.GridView,
                label = "Pages",
                onClick = onPagesClick
            )
        }
    }
}

@Composable
private fun ViewerAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PdfPagesList(
    pages: List<Bitmap?>,
    listState: LazyListState,
    zoom: Float,
    readingMode: PdfReadingMode,
    onZoomChange: (Float) -> Unit
) {
    var scale by remember { mutableFloatStateOf(zoom) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, gestureZoom, _ ->
                    scale = (scale * gestureZoom).coerceIn(1f, 5f)
                    if (scale > 1f) {
                        offset += pan
                    } else {
                        offset = Offset.Zero
                    }
                    onZoomChange(scale)
                }
            },
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(pages) { index, bitmap ->
            PdfPageItem(
                bitmap = bitmap,
                pageNumber = index + 1,
                scale = scale,
                offset = offset,
                readingMode = readingMode
            )
        }
    }
}

@Composable
private fun PdfPageItem(
    bitmap: Bitmap?,
    pageNumber: Int,
    scale: Float,
    offset: Offset,
    readingMode: PdfReadingMode
) {
    // Color matrix for different reading modes
    val colorMatrix = remember(readingMode) {
        when (readingMode) {
            PdfReadingMode.NORMAL -> null
            PdfReadingMode.DARK -> androidx.compose.ui.graphics.ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            PdfReadingMode.SEPIA -> androidx.compose.ui.graphics.ColorMatrix(
                floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            PdfReadingMode.NIGHT -> androidx.compose.ui.graphics.ColorMatrix(
                floatArrayOf(
                    -0.8f, 0f, 0f, 0f, 200f,
                    0f, -0.8f, 0f, 0f, 200f,
                    0f, 0f, -0.8f, 0f, 200f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        }
    }

    val colorFilter = colorMatrix?.let {
        androidx.compose.ui.graphics.ColorFilter.colorMatrix(it)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
        shape = RoundedCornerShape(4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Page $pageNumber",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth,
                colorFilter = colorFilter
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.707f) // A4 aspect ratio
                    .background(if (readingMode == PdfReadingMode.DARK || readingMode == PdfReadingMode.NIGHT) Color.DarkGray else Color.White),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Loading document...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Error,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun PasswordPrompt(
    onSubmit: (String) -> Unit,
    isError: Boolean
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth(),
            shape = PdfMasterShapes.Dialog
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Password Protected",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Enter the password to open this document",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    isError = isError,
                    supportingText = if (isError) {
                        { Text("Incorrect password") }
                    } else null,
                    visualTransformation = if (passwordVisible) {
                        androidx.compose.ui.text.input.VisualTransformation.None
                    } else {
                        androidx.compose.ui.text.input.PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { onSubmit(password) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = password.isNotEmpty()
                ) {
                    Text("Unlock")
                }
            }
        }
    }
}


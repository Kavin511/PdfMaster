package com.pdfmaster.app.presentation.ui.tools.split

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdfmaster.app.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitScreen(
    preSelectedUri: String? = null,
    onNavigateBack: () -> Unit,
    onSelectPdf: (String) -> Unit,
    onSplitComplete: ((String) -> Unit)? = null,
    onNavigateToPremium: () -> Unit = {},
    viewModel: SplitViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isPremium by viewModel.isPremium.collectAsStateWithLifecycle()
    val remainingToday by viewModel.remainingToday.collectAsStateWithLifecycle()

    com.pdfmaster.app.presentation.ui.premium.PremiumGateDialog(
        prompt = uiState.premiumPrompt,
        onDismiss = viewModel::clearPremiumPrompt,
        onUpgrade = { viewModel.clearPremiumPrompt(); onNavigateToPremium() },
    )

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.loadPdf(context, it) }
    }

    LaunchedEffect(preSelectedUri) {
        preSelectedUri?.let { viewModel.loadPdf(context, Uri.parse(it)) }
    }

    // Handle split completion
    LaunchedEffect(uiState.outputUri) {
        uiState.outputUri?.let { uri ->
            onSplitComplete?.invoke(uri)
            // Don't call onNavigateBack() here - navigation is handled by onSplitComplete
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Split PDF") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.pageCount > 0) {
                        TextButton(onClick = { viewModel.selectAll() }) {
                            Text("Select All")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (uiState.selectedPages.isNotEmpty() && !uiState.isSplitting) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.extractPages(context) { } }
                ) {
                    Icon(Icons.Outlined.ContentCut, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Extract ${uiState.selectedPages.size} Pages")
                }
            }
        },
        bottomBar = {
            if (uiState.pageCount > 0) {
                com.pdfmaster.app.presentation.ui.premium.DailyQuotaHint(
                    isPremium = isPremium,
                    remaining = remainingToday,
                    unitLabel = "splits",
                    onUpgrade = onNavigateToPremium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        },
        snackbarHost = {
            uiState.error?.let { error ->
                Snackbar(
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) { Text("Dismiss") }
                    }
                ) { Text(error) }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.uri == null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Outlined.ContentCut,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text("Split PDF", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Select a PDF to extract specific pages", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(onClick = { filePicker.launch(arrayOf("application/pdf")) }) {
                            Text("Select PDF")
                        }
                    }
                }
                uiState.isLoading || uiState.isSplitting -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(if (uiState.isSplitting) "Extracting pages..." else "Loading...")
                    }
                }
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "Select pages to extract",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(100.dp),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(uiState.pages) { index, _ ->
                                val isSelected = uiState.selectedPages.contains(index)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.togglePageSelection(index) },
                                    label = { Text("Page ${index + 1}") }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

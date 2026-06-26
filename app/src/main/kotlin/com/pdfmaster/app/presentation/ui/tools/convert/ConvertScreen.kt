package com.pdfmaster.app.presentation.ui.tools.convert

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.pdfmaster.app.presentation.ui.premium.PremiumGateDialog
import com.pdfmaster.app.util.FileUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConvertScreen(
    onNavigateBack: () -> Unit,
    onConvertComplete: (String) -> Unit,
    onNavigateToPremium: () -> Unit = {},
    viewModel: ConvertViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isPremium by viewModel.isPremium.collectAsStateWithLifecycle()
    val selectedImages = uiState.selectedImages

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> viewModel.addImages(uris) }

    PremiumGateDialog(
        prompt = uiState.premiumPrompt,
        onDismiss = viewModel::clearPremiumPrompt,
        onUpgrade = { viewModel.clearPremiumPrompt(); onNavigateToPremium() },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Images to PDF") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedImages.isNotEmpty() && !uiState.isConverting) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.convert(context, onConvertComplete) }
                ) {
                    Icon(Icons.Outlined.Transform, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Convert to PDF")
                }
            }
        },
        snackbarHost = {
            uiState.error?.let { errorMessage ->
                Snackbar(
                    action = {
                        TextButton(onClick = viewModel::clearError) { Text("Dismiss") }
                    }
                ) { Text(errorMessage) }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                selectedImages.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Outlined.Image, null, Modifier.size(80.dp), MaterialTheme.colorScheme.primary.copy(0.6f))
                        Spacer(Modifier.height(24.dp))
                        Text("Convert Images to PDF", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text("Select images to combine into a PDF document", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!isPremium) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Free plan: up to ${viewModel.freeImageLimit} images per PDF",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(32.dp))
                        Button(onClick = { imagePicker.launch(arrayOf("image/*")) }) { Text("Select Images") }
                    }
                }
                uiState.isConverting -> {
                    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Converting...")
                    }
                }
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("${selectedImages.size} images selected", style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = { imagePicker.launch(arrayOf("image/*")) }) {
                                Icon(Icons.Outlined.Add, null)
                                Spacer(Modifier.width(4.dp))
                                Text("Add More")
                            }
                        }
                        // Free-tier hint: warn when over the per-PDF image cap.
                        if (!isPremium) {
                            val over = selectedImages.size > viewModel.freeImageLimit
                            AssistChip(
                                onClick = { if (over) onNavigateToPremium() },
                                modifier = Modifier.padding(horizontal = 16.dp),
                                leadingIcon = {
                                    Icon(
                                        if (over) Icons.Outlined.WorkspacePremium else Icons.Outlined.Info,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                                label = {
                                    Text(
                                        if (over) "Over the free limit of ${viewModel.freeImageLimit} — upgrade to convert"
                                        else "Free plan: up to ${viewModel.freeImageLimit} images per PDF"
                                    )
                                },
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(selectedImages) { index, uri ->
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("${index + 1}", fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.width(12.dp))
                                        Text(FileUtils.getFileName(context, uri), modifier = Modifier.weight(1f))
                                        IconButton(onClick = { viewModel.removeImage(index) }) {
                                            Icon(Icons.Outlined.Close, "Remove")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

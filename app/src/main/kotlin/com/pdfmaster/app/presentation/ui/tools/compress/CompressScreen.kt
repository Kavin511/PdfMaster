package com.pdfmaster.app.presentation.ui.tools.compress

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
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
import com.pdfmaster.app.domain.repository.CompressionQuality
import com.pdfmaster.app.presentation.theme.*
import com.pdfmaster.app.util.FileUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressScreen(
    onNavigateBack: () -> Unit,
    onCompressComplete: (String) -> Unit,
    viewModel: CompressViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.loadPdf(context, it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compress PDF") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
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
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                uiState.uri == null -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Outlined.Compress, null, Modifier.size(80.dp), MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                        Spacer(Modifier.height(24.dp))
                        Text("Compress PDF", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text("Reduce file size while maintaining quality", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(32.dp))
                        Button(onClick = { filePicker.launch(arrayOf("application/pdf")) }) { Text("Select PDF") }
                    }
                }
                uiState.isCompressing -> {
                    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Compressing...")
                    }
                }
                uiState.isComplete -> {
                    Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Outlined.CheckCircle, null, Modifier.size(80.dp), MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(24.dp))
                        Text("Compression Complete!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(16.dp))
                        Text("Original: ${FileUtils.formatFileSize(uiState.originalSize)}")
                        Text("Compressed: ${FileUtils.formatFileSize(uiState.compressedSize)}")
                        Text("Saved: ${((uiState.originalSize - uiState.compressedSize) * 100 / uiState.originalSize)}%", color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(32.dp))
                        Button(onClick = { uiState.outputUri?.let { onCompressComplete(it) } }) { Text("Open Compressed PDF") }
                    }
                }
                else -> {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(uiState.fileName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                                Text("Size: ${FileUtils.formatFileSize(uiState.originalSize)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        Text("Compression Level", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(16.dp))
                        CompressionQuality.entries.forEach { quality ->
                            Card(
                                onClick = { viewModel.setQuality(quality) },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (uiState.selectedQuality == quality) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = uiState.selectedQuality == quality, onClick = { viewModel.setQuality(quality) })
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(when(quality) { CompressionQuality.HIGH -> "High Quality"; CompressionQuality.MEDIUM -> "Balanced"; CompressionQuality.LOW -> "Maximum Compression" })
                                        Text(when(quality) { CompressionQuality.HIGH -> "Best quality, larger file"; CompressionQuality.MEDIUM -> "Good balance"; CompressionQuality.LOW -> "Smallest file" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Button(onClick = { viewModel.compress(context) }, modifier = Modifier.fillMaxWidth(), shape = PdfMasterShapes.Button) { Text("Compress PDF") }
                    }
                }
            }
        }
    }
}

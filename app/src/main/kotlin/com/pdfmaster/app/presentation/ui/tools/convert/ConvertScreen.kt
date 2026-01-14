package com.pdfmaster.app.presentation.ui.tools.convert

import android.net.Uri
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
import com.pdfmaster.app.presentation.theme.*
import com.pdfmaster.app.util.FileUtils
import com.pdfmaster.app.util.PdfUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConvertScreen(
    onNavigateBack: () -> Unit,
    onConvertComplete: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedImages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var isConverting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        selectedImages = uris
    }

    LaunchedEffect(isConverting) {
        if (isConverting && selectedImages.isNotEmpty()) {
            try {
                val outputDir = FileUtils.getOutputDirectory(context)
                val outputFile = File(outputDir, FileUtils.generateOutputFileName("Images_to_PDF"))

                val success = withContext(Dispatchers.IO) {
                    PdfUtils.imagesToPdf(context, selectedImages, outputFile)
                }

                if (success) {
                    val outputUri = Uri.fromFile(outputFile).toString()
                    onConvertComplete(outputUri)
                } else {
                    error = "Failed to convert images to PDF"
                    isConverting = false
                }
            } catch (e: Exception) {
                error = "Error: ${e.message}"
                isConverting = false
            }
        }
    }

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
            if (selectedImages.isNotEmpty() && !isConverting) {
                ExtendedFloatingActionButton(onClick = { error = null; isConverting = true }) {
                    Icon(Icons.Outlined.Transform, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Convert to PDF")
                }
            }
        },
        snackbarHost = {
            error?.let { errorMessage ->
                Snackbar(
                    action = {
                        TextButton(onClick = { error = null }) {
                            Text("Dismiss")
                        }
                    }
                ) {
                    Text(errorMessage)
                }
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
                        Spacer(Modifier.height(32.dp))
                        Button(onClick = { imagePicker.launch(arrayOf("image/*")) }) { Text("Select Images") }
                    }
                }
                isConverting -> {
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
                        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(selectedImages) { index, uri ->
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("${index + 1}", fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.width(12.dp))
                                        Text(FileUtils.getFileName(context, uri), modifier = Modifier.weight(1f))
                                        IconButton(onClick = { selectedImages = selectedImages.filterIndexed { i, _ -> i != index } }) {
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

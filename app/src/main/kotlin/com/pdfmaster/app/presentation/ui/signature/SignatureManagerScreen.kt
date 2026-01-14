package com.pdfmaster.app.presentation.ui.signature

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pdfmaster.app.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureManagerScreen(
    onNavigateBack: () -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Signatures") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Outlined.Add, "Add signature")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Outlined.Draw, null, Modifier.size(80.dp), MaterialTheme.colorScheme.primary.copy(0.6f))
            Spacer(Modifier.height(24.dp))
            Text("No Signatures Yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("Create signatures to quickly sign documents", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(32.dp))
            Button(onClick = { showCreateDialog = true }) { Text("Create Signature") }
        }
    }

    if (showCreateDialog) {
        CreateSignatureDialog(onDismiss = { showCreateDialog = false }, onSave = { showCreateDialog = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSignatureScreen(
    uri: String,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Signature") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Tap on the document to place your signature", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateSignatureDialog(
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    var points by remember { mutableStateOf(listOf<Offset>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Draw Your Signature") },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                points = points + change.position
                            }
                        }
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        if (points.size > 1) {
                            val path = Path()
                            path.moveTo(points.first().x, points.first().y)
                            points.drop(1).forEach { path.lineTo(it.x, it.y) }
                            drawPath(path, Color.Black, style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { points = emptyList() }, modifier = Modifier.align(Alignment.End)) {
                    Text("Clear")
                }
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = points.isNotEmpty()) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

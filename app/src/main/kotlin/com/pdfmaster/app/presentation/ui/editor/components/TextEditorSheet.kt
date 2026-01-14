package com.pdfmaster.app.presentation.ui.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pdfmaster.app.presentation.theme.PdfMasterShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorSheet(
    onDismiss: () -> Unit,
    onAddText: (text: String, fontSize: Float, color: Color) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var fontSize by remember { mutableFloatStateOf(16f) }
    var selectedColor by remember { mutableStateOf(Color.Black) }

    val colors = listOf(
        Color.Black,
        Color.Red,
        Color.Blue,
        Color(0xFF2563EB), // Primary blue
        Color(0xFF10B981), // Green
        Color(0xFFF59E0B), // Orange
        Color(0xFF8B5CF6), // Purple
        Color(0xFFEC4899)  // Pink
    )

    val fontSizes = listOf(12f, 14f, 16f, 18f, 20f, 24f, 28f, 32f, 36f, 48f)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = PdfMasterShapes.BottomSheet
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Add Text",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Text Input
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Enter text") },
                minLines = 3,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Font Size
            Text(
                text = "Font Size",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(fontSizes) { size ->
                    FilterChip(
                        selected = fontSize == size,
                        onClick = { fontSize = size },
                        label = { Text("${size.toInt()}") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Color
            Text(
                text = "Color",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(colors) { color ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(color)
                            .then(
                                if (selectedColor == color) {
                                    Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                } else Modifier
                            )
                            .clickable { selectedColor = color }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Preview
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = text.ifEmpty { "Preview" },
                        fontSize = fontSize.sp,
                        color = if (text.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else selectedColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = PdfMasterShapes.Button
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = { onAddText(text, fontSize, selectedColor) },
                    modifier = Modifier.weight(1f),
                    shape = PdfMasterShapes.Button,
                    enabled = text.isNotEmpty()
                ) {
                    Text("Add Text")
                }
            }
        }
    }
}

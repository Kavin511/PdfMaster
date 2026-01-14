package com.pdfmaster.app.presentation.ui.viewer.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.pdfmaster.app.presentation.theme.PdfMasterShapes
import com.pdfmaster.app.presentation.ui.viewer.SearchResultItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSheet(
    query: String,
    results: List<SearchResultItem>,
    onQueryChange: (String) -> Unit,
    onResultClick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
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
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search in document") },
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Outlined.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { }),
                shape = PdfMasterShapes.SearchBar
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (results.isEmpty() && query.length >= 2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No results found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    items(results) { result ->
                        ListItem(
                            headlineContent = { Text(result.text) },
                            supportingContent = { Text("Page ${result.pageNumber}") },
                            modifier = Modifier.clickable { onResultClick(result.pageNumber - 1) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PageSliderDialog(
    currentPage: Int,
    totalPages: Int,
    onPageSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedPage by remember { mutableFloatStateOf(currentPage.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Go to Page") },
        text = {
            Column {
                Text(
                    text = "Page ${selectedPage.toInt()} of $totalPages",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Slider(
                    value = selectedPage,
                    onValueChange = { selectedPage = it },
                    valueRange = 1f..totalPages.toFloat(),
                    steps = totalPages - 2
                )
            }
        },
        confirmButton = {
            Button(onClick = { onPageSelected(selectedPage.toInt()) }) {
                Text("Go")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ViewerOptionsSheet(
    onShareClick: () -> Unit,
    onPrintClick: () -> Unit,
    onDetailsClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
    ) {
        ListItem(
            headlineContent = { Text("Share") },
            leadingContent = { Icon(Icons.Outlined.Share, contentDescription = null) },
            modifier = Modifier.clickable { onShareClick() }
        )
        ListItem(
            headlineContent = { Text("Print") },
            leadingContent = { Icon(Icons.Outlined.Print, contentDescription = null) },
            modifier = Modifier.clickable { onPrintClick() }
        )
        ListItem(
            headlineContent = { Text("Document Info") },
            leadingContent = { Icon(Icons.Outlined.Info, contentDescription = null) },
            modifier = Modifier.clickable { onDetailsClick() }
        )
    }
}

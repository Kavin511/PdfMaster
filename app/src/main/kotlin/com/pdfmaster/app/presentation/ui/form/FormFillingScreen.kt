package com.pdfmaster.app.presentation.ui.form

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdfmaster.app.domain.model.FormField
import com.pdfmaster.app.presentation.theme.*
import com.pdfmaster.app.presentation.ui.premium.PremiumGateDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormFillingScreen(
    uri: String,
    onNavigateBack: () -> Unit,
    onSaveComplete: (String) -> Unit,
    onNavigateToPremium: () -> Unit = {},
    viewModel: FormFillingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    PremiumGateDialog(
        prompt = uiState.premiumPrompt,
        onDismiss = viewModel::clearPremiumPrompt,
        onUpgrade = { viewModel.clearPremiumPrompt(); onNavigateToPremium() },
    )

    var showFieldInput by remember { mutableStateOf(false) }
    var inputFieldId by remember { mutableStateOf<String?>(null) }
    var inputValue by remember { mutableStateOf("") }

    LaunchedEffect(uri) {
        viewModel.loadPdf(context, Uri.parse(uri))
    }

    // Track current page
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { viewModel.setCurrentPage(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fill Form", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (uiState.selectedField != null) {
                        IconButton(onClick = {
                            uiState.selectedField?.let { viewModel.deleteField(it.id) }
                        }) {
                            Icon(Icons.Outlined.Delete, "Delete field")
                        }
                    }
                    IconButton(
                        onClick = {
                            viewModel.savePdf(context) { savedUri ->
                                savedUri?.let { onSaveComplete(it.toString()) }
                            }
                        },
                        enabled = !uiState.isSaving
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Outlined.Save, "Save")
                        }
                    }
                }
            )
        },
        bottomBar = {
            FormToolbar(
                isAddingField = uiState.isAddingField,
                addingFieldType = uiState.addingFieldType,
                onAddText = { viewModel.startAddingField(FieldType.TEXT) },
                onAddCheckbox = { viewModel.startAddingField(FieldType.CHECKBOX) },
                onAddDate = { viewModel.startAddingField(FieldType.DATE) },
                onAddSignature = { viewModel.startAddingField(FieldType.SIGNATURE) },
                onCancel = { viewModel.cancelAddingField() }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Loading document...")
                    }
                }
            } else if (uiState.error != null) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Error, null, Modifier.size(64.dp), MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(16.dp))
                        Text(uiState.error!!)
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(uiState.pages) { index, bitmap ->
                        FormPageItem(
                            bitmap = bitmap,
                            pageIndex = index,
                            formFields = uiState.formFields.filter { it.pageIndex == index },
                            selectedField = uiState.selectedField,
                            isAddingField = uiState.isAddingField,
                            onFieldTap = { position, pageSize ->
                                if (uiState.isAddingField) {
                                    viewModel.addFieldAtPosition(
                                        position,
                                        pageSize.width.toFloat(),
                                        pageSize.height.toFloat()
                                    )
                                }
                            },
                            onFieldSelect = { viewModel.selectField(it) },
                            onFieldMove = { id, pos -> viewModel.moveField(id, pos) },
                            onTextFieldClick = { field ->
                                inputFieldId = field.id
                                inputValue = field.value
                                showFieldInput = true
                            },
                            onCheckboxToggle = { viewModel.toggleCheckbox(it.id) }
                        )
                    }
                }

                // Page indicator
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.7f)
                ) {
                    Text(
                        "${uiState.currentPage + 1} / ${uiState.pageCount}",
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // Adding field hint
                if (uiState.isAddingField) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            "Tap on the page to add ${uiState.addingFieldType.name.lowercase()} field",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }

    // Text input dialog
    if (showFieldInput && inputFieldId != null) {
        AlertDialog(
            onDismissRequest = { showFieldInput = false },
            title = { Text("Enter Value") },
            text = {
                OutlinedTextField(
                    value = inputValue,
                    onValueChange = { inputValue = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Enter text...") }
                )
            },
            confirmButton = {
                Button(onClick = {
                    inputFieldId?.let { viewModel.updateFieldValue(it, inputValue) }
                    showFieldInput = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFieldInput = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun FormToolbar(
    isAddingField: Boolean,
    addingFieldType: FieldType,
    onAddText: () -> Unit,
    onAddCheckbox: () -> Unit,
    onAddDate: () -> Unit,
    onAddSignature: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            if (isAddingField) {
                Button(onClick = onCancel) {
                    Icon(Icons.Outlined.Close, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Cancel")
                }
            } else {
                FormToolButton(
                    icon = Icons.Outlined.TextFields,
                    label = "Text",
                    onClick = onAddText
                )
                FormToolButton(
                    icon = Icons.Outlined.CheckBox,
                    label = "Checkbox",
                    onClick = onAddCheckbox
                )
                FormToolButton(
                    icon = Icons.Outlined.CalendarMonth,
                    label = "Date",
                    onClick = onAddDate
                )
                FormToolButton(
                    icon = Icons.Outlined.Draw,
                    label = "Signature",
                    onClick = onAddSignature
                )
            }
        }
    }
}

@Composable
private fun FormToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(icon, label, Modifier.size(24.dp), MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun FormPageItem(
    bitmap: Bitmap?,
    pageIndex: Int,
    formFields: List<FormField>,
    selectedField: FormField?,
    isAddingField: Boolean,
    onFieldTap: (Offset, IntSize) -> Unit,
    onFieldSelect: (FormField?) -> Unit,
    onFieldMove: (String, Offset) -> Unit,
    onTextFieldClick: (FormField.TextField) -> Unit,
    onCheckboxToggle: (FormField.CheckBox) -> Unit
) {
    var pageSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { pageSize = it }
                .pointerInput(isAddingField) {
                    if (isAddingField) {
                        detectTapGestures { offset ->
                            onFieldTap(offset, pageSize)
                        }
                    }
                }
        ) {
            // PDF Page
            if (bitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Page ${pageIndex + 1}",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.707f)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 2.dp)
                }
            }

            // Form Fields Overlay
            formFields.forEach { field ->
                FormFieldOverlay(
                    field = field,
                    isSelected = selectedField?.id == field.id,
                    pageSize = pageSize,
                    onSelect = { onFieldSelect(field) },
                    onMove = { onFieldMove(field.id, it) },
                    onTextClick = { if (field is FormField.TextField) onTextFieldClick(field) },
                    onCheckboxToggle = { if (field is FormField.CheckBox) onCheckboxToggle(field) }
                )
            }
        }
    }
}

@Composable
private fun FormFieldOverlay(
    field: FormField,
    isSelected: Boolean,
    pageSize: IntSize,
    onSelect: () -> Unit,
    onMove: (Offset) -> Unit,
    onTextClick: () -> Unit,
    onCheckboxToggle: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f)

    Box(
        modifier = Modifier
            .offset(
                x = with(LocalDensity.current) { field.bounds.left.toDp() },
                y = with(LocalDensity.current) { field.bounds.top.toDp() }
            )
            .size(
                width = with(LocalDensity.current) { field.bounds.width.toDp() },
                height = with(LocalDensity.current) { field.bounds.height.toDp() }
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(4.dp)
            )
            .background(
                color = when (field) {
                    is FormField.TextField -> Color.White.copy(alpha = 0.9f)
                    is FormField.CheckBox -> Color.Transparent
                    else -> Color.White.copy(alpha = 0.8f)
                },
                shape = RoundedCornerShape(4.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        onSelect()
                        when (field) {
                            is FormField.TextField -> onTextClick()
                            is FormField.CheckBox -> onCheckboxToggle()
                            else -> {}
                        }
                    }
                )
            }
            .then(
                if (isSelected) {
                    Modifier.pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onMove(
                                Offset(
                                    field.bounds.left + dragAmount.x,
                                    field.bounds.top + dragAmount.y
                                )
                            )
                        }
                    }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        when (field) {
            is FormField.TextField -> {
                Text(
                    text = field.value.ifEmpty { field.placeholder },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (field.value.isEmpty()) Color.Gray else Color.Black
                )
            }
            is FormField.CheckBox -> {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .border(2.dp, Color.Black, RoundedCornerShape(2.dp))
                        .background(if (field.isChecked) MaterialTheme.colorScheme.primary else Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    if (field.isChecked) {
                        Icon(
                            Icons.Filled.Check,
                            null,
                            Modifier.size(16.dp),
                            Color.White
                        )
                    }
                }
            }
            is FormField.DateField -> {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = field.value.ifEmpty { "Select date" },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (field.value.isEmpty()) Color.Gray else Color.Black
                    )
                }
            }
            is FormField.Signature -> {
                if (field.value.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Draw, null, Modifier.size(16.dp), Color.Gray)
                        Spacer(Modifier.width(4.dp))
                        Text("Sign here", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                } else {
                    Text("Signed", style = MaterialTheme.typography.bodySmall)
                }
            }
            else -> {}
        }
    }
}

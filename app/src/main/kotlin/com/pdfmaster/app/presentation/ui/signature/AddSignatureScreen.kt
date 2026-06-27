package com.pdfmaster.app.presentation.ui.signature

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdfmaster.app.billing.PremiumFeature
import com.pdfmaster.app.presentation.theme.*
import com.pdfmaster.app.presentation.ui.premium.PremiumGateDialog
import com.pdfmaster.app.presentation.ui.premium.PremiumGateViewModel
import com.pdfmaster.app.util.FileUtils
import com.pdfmaster.app.util.OpenPdfEditor
import com.pdfmaster.app.util.PdfCoordinateMapper
import com.pdfmaster.app.util.PdfUtils
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSignatureScreen(
    uri: String,
    onNavigateBack: () -> Unit,
    onSignComplete: ((String) -> Unit)? = null,
    onNavigateToPremium: () -> Unit = {},
    gateViewModel: PremiumGateViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val gatePrompt by gateViewModel.prompt.collectAsStateWithLifecycle()
    PremiumGateDialog(
        prompt = gatePrompt,
        onDismiss = gateViewModel::clearPrompt,
        onUpgrade = { gateViewModel.clearPrompt(); onNavigateToPremium() },
    )

    var currentPage by remember { mutableStateOf(0) }
    var pageCount by remember { mutableStateOf(0) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pageSize by remember { mutableStateOf(IntSize.Zero) }

    var showSignatureDialog by remember { mutableStateOf(true) }
    var signatureBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var signaturePosition by remember { mutableStateOf<Offset?>(null) }
    var signatureSize by remember { mutableStateOf(IntSize(200, 100)) }

    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val parsedUri = remember { Uri.parse(uri) }

    // Load PDF page
    LaunchedEffect(uri, currentPage) {
        pageCount = PdfUtils.getPageCount(context, parsedUri)
        val width = context.resources.displayMetrics.widthPixels
        pageBitmap = PdfUtils.renderPage(context, parsedUri, currentPage, width)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Signature") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (signaturePosition != null && signatureBitmap != null) {
                        TextButton(
                            onClick = {
                                // Placing & saving a signature is premium-only.
                                if (!gateViewModel.ensure(PremiumFeature.SIGNATURE)) return@TextButton
                                val bmp = pageBitmap
                                val pos = signaturePosition
                                val sig = signatureBitmap
                                if (bmp == null || pos == null || sig == null) return@TextButton
                                scope.launch {
                                    isSaving = true
                                    try {
                                        // Read the real PDF page size (points) so we can map the
                                        // on-screen placement into PDF space and stamp the signature
                                        // onto the ORIGINAL pdf — preserving its vector text — instead
                                        // of rasterizing every page.
                                        val dims = OpenPdfEditor.getPageDimensions(context, parsedUri, currentPage)
                                        if (dims == null) {
                                            error = "Couldn't read the page size"
                                            isSaving = false
                                            return@launch
                                        }
                                        val mapper = PdfCoordinateMapper(
                                            bitmapWidth = bmp.width,
                                            bitmapHeight = bmp.height,
                                            containerWidth = pageSize.width.toFloat(),
                                            containerHeight = pageSize.height.toFloat(),
                                            pdfWidthPts = dims.first,
                                            pdfHeightPts = dims.second,
                                        )
                                        val pdfPoint = mapper.containerToPdf(pos)
                                        if (pdfPoint == null) {
                                            error = "Place the signature on the page"
                                            isSaving = false
                                            return@launch
                                        }

                                        val outputDir = FileUtils.getOutputDirectory(context)
                                        val outputFile = File(outputDir, FileUtils.generateOutputFileName("Signed"))

                                        val success = OpenPdfEditor.applyEdits(
                                            context = context,
                                            sourceUri = parsedUri,
                                            outputFile = outputFile,
                                            images = listOf(
                                                OpenPdfEditor.ImageOverlay(
                                                    pageIndex = currentPage,
                                                    x = pdfPoint.x,
                                                    y = pdfPoint.y,
                                                    width = mapper.lengthToPdfX(signatureSize.width.toFloat()),
                                                    height = mapper.lengthToPdfY(signatureSize.height.toFloat()),
                                                    bitmap = sig,
                                                )
                                            ),
                                        )

                                        if (success) {
                                            onSignComplete?.invoke(Uri.fromFile(outputFile).toString())
                                            onNavigateBack()
                                        } else {
                                            error = "Failed to save signed PDF"
                                        }
                                    } catch (e: Exception) {
                                        error = "Error: ${e.message}"
                                    } finally {
                                        isSaving = false
                                    }
                                }
                            },
                            enabled = !isSaving
                        ) {
                            Text("Save")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (pageCount > 1) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .navigationBarsPadding(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { if (currentPage > 0) currentPage-- },
                            enabled = currentPage > 0
                        ) {
                            Icon(Icons.Outlined.ChevronLeft, "Previous")
                        }
                        Text("Page ${currentPage + 1} of $pageCount")
                        IconButton(
                            onClick = { if (currentPage < pageCount - 1) currentPage++ },
                            enabled = currentPage < pageCount - 1
                        ) {
                            Icon(Icons.Outlined.ChevronRight, "Next")
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            pageBitmap?.let { bitmap ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .onSizeChanged { pageSize = it }
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                if (signatureBitmap != null) {
                                    signaturePosition = offset
                                }
                            }
                        }
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "PDF Page",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )

                    // Draw signature overlay if placed
                    signaturePosition?.let { pos ->
                        signatureBitmap?.let { sig ->
                            Box(
                                modifier = Modifier
                                    .offset(
                                        x = with(density) { pos.x.toDp() },
                                        y = with(density) { pos.y.toDp() }
                                    )
                                    .size(
                                        width = with(density) { signatureSize.width.toDp() },
                                        height = with(density) { signatureSize.height.toDp() }
                                    )
                                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                    .pointerInput(Unit) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            signaturePosition = Offset(
                                                pos.x + dragAmount.x,
                                                pos.y + dragAmount.y
                                            )
                                        }
                                    }
                            ) {
                                Image(
                                    bitmap = sig.asImageBitmap(),
                                    contentDescription = "Signature",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }
                }
            } ?: Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            // Saving overlay
            if (isSaving) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text("Saving...")
                        }
                    }
                }
            }

            // Instruction banner
            if (signatureBitmap != null && signaturePosition == null) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Text(
                        "Tap on the document to place your signature",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }

    // Signature drawing dialog
    if (showSignatureDialog) {
        SignatureDrawingDialog(
            onDismiss = {
                showSignatureDialog = false
                if (signatureBitmap == null) onNavigateBack()
            },
            onSignatureCreated = { bitmap ->
                signatureBitmap = bitmap
                showSignatureDialog = false
            }
        )
    }

    // Error snackbar
    error?.let {
        LaunchedEffect(it) {
            // Auto dismiss after showing
            kotlinx.coroutines.delay(3000)
            error = null
        }
    }
}

@Composable
private fun SignatureDrawingDialog(
    onDismiss: () -> Unit,
    onSignatureCreated: (Bitmap) -> Unit
) {
    var points by remember { mutableStateOf(listOf<Offset>()) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Draw Your Signature") },
        text = {
            Column {
                Text(
                    "Draw your signature in the box below",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .onSizeChanged { canvasSize = it }
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
                            drawPath(
                                path,
                                Color.Black,
                                style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { points = emptyList() },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Clear")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (points.isNotEmpty() && canvasSize.width > 0 && canvasSize.height > 0) {
                        val bitmap = Bitmap.createBitmap(
                            canvasSize.width,
                            canvasSize.height,
                            Bitmap.Config.ARGB_8888
                        )
                        val canvas = android.graphics.Canvas(bitmap)
                        canvas.drawColor(android.graphics.Color.TRANSPARENT)

                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.BLACK
                            strokeWidth = 4f
                            style = android.graphics.Paint.Style.STROKE
                            strokeCap = android.graphics.Paint.Cap.ROUND
                            strokeJoin = android.graphics.Paint.Join.ROUND
                            isAntiAlias = true
                        }

                        val path = android.graphics.Path()
                        path.moveTo(points.first().x, points.first().y)
                        points.drop(1).forEach { path.lineTo(it.x, it.y) }
                        canvas.drawPath(path, paint)

                        onSignatureCreated(bitmap)
                    }
                },
                enabled = points.isNotEmpty()
            ) {
                Text("Use Signature")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

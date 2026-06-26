package com.pdfmaster.app.presentation.ui.premium

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.pdfmaster.app.presentation.theme.*

enum class PlanType {
    WEEKLY, ANNUAL, LIFETIME
}

/** Walks the ContextWrapper chain to find the hosting Activity (needed to launch billing). */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumScreen(
    onNavigateBack: () -> Unit,
    source: String = "premium_screen",
    viewModel: PremiumViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedPlan by remember { mutableStateOf(PlanType.ANNUAL) }
    val activity = LocalContext.current.findActivity()
    val snackbarHostState = remember { SnackbarHostState() }

    // Report the paywall impression once, tagged with where it was opened from.
    LaunchedEffect(Unit) { viewModel.trackPaywallViewed(source) }

    // Surface transient errors/messages from billing.
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    // On a successful unlock, leave the paywall.
    LaunchedEffect(uiState.purchaseSuccess) {
        if (uiState.purchaseSuccess) onNavigateBack()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Spacer(Modifier.height(16.dp))

                    // Pro badge
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF2563EB),
                                        Color(0xFF7C3AED)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.WorkspacePremium,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = Color.White
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    Text(
                        "PDF Master Pro",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        "Unlock the full power of PDF Master",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(32.dp))
                }
            }

            // Features list
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "What's included",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(16.dp))

                        PremiumFeatureItem(Icons.Outlined.AllInclusive, "Unlimited PDF operations")
                        PremiumFeatureItem(Icons.Outlined.WaterDrop, "No watermark on scans & edits")
                        PremiumFeatureItem(Icons.Outlined.Edit, "Full PDF text editing")
                        PremiumFeatureItem(Icons.Outlined.Scanner, "Unlimited document scanning")
                        PremiumFeatureItem(Icons.Outlined.Compress, "Advanced compression options")
                        PremiumFeatureItem(Icons.Outlined.Draw, "All annotation tools")
                        PremiumFeatureItem(Icons.Outlined.Security, "Password protection")
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            // Plan selection
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        "Choose your plan",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Spacer(Modifier.height(12.dp))

                    // Weekly plan (3-day free trial)
                    PlanCard(
                        title = "Weekly",
                        price = uiState.prices[PlanType.WEEKLY] ?: "$4.99",
                        period = "/week",
                        savings = "3-day free trial",
                        isSelected = selectedPlan == PlanType.WEEKLY,
                        onClick = { selectedPlan = PlanType.WEEKLY }
                    )

                    Spacer(Modifier.height(12.dp))

                    // Annual plan (Best Value)
                    PlanCard(
                        title = "Annual",
                        price = uiState.prices[PlanType.ANNUAL] ?: "$39.99",
                        period = "/year",
                        originalPrice = "$259.48",
                        savings = "Save 84% · $3.33/mo",
                        isSelected = selectedPlan == PlanType.ANNUAL,
                        isBestValue = true,
                        onClick = { selectedPlan = PlanType.ANNUAL }
                    )

                    Spacer(Modifier.height(12.dp))

                    // Lifetime plan (one-time)
                    PlanCard(
                        title = "Lifetime",
                        price = uiState.prices[PlanType.LIFETIME] ?: "$79.99",
                        period = "one-time",
                        isSelected = selectedPlan == PlanType.LIFETIME,
                        onClick = { selectedPlan = PlanType.LIFETIME }
                    )

                    Spacer(Modifier.height(24.dp))
                }
            }

            // Purchase button
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = { activity?.let { viewModel.purchase(it, selectedPlan) } },
                        enabled = !uiState.isLoading && activity != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                "Continue",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    TextButton(onClick = { viewModel.restorePurchases() }) {
                        Text("Restore Purchases")
                    }

                    Spacer(Modifier.height(8.dp))

                    Text(
                        "Cancel anytime. Secure payment via Google Play.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = { viewModel.openTerms() }) {
                            Text("Terms of Service", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(" • ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { viewModel.openPrivacy() }) {
                            Text("Privacy Policy", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun PremiumFeatureItem(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun PlanCard(
    title: String,
    price: String,
    period: String,
    originalPrice: String? = null,
    savings: String? = null,
    isSelected: Boolean,
    isBestValue: Boolean = false,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        if (isSelected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant,
        label = "borderColor"
    )

    val containerColor by animateColorAsState(
        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else MaterialTheme.colorScheme.surface,
        label = "containerColor"
    )

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, borderColor, RoundedCornerShape(16.dp))
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = containerColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Radio button
                RadioButton(
                    selected = isSelected,
                    onClick = onClick,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = MaterialTheme.colorScheme.primary
                    )
                )

                Spacer(Modifier.width(8.dp))

                // Plan info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (originalPrice != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                originalPrice,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textDecoration = TextDecoration.LineThrough
                            )
                            Spacer(Modifier.width(8.dp))
                            if (savings != null) {
                                Text(
                                    savings,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Price
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        price,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        period,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Best value badge
        if (isBestValue) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-12).dp, y = (-8).dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary
            ) {
                Text(
                    "BEST VALUE",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

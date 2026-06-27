package com.pdfmaster.app.presentation.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val backgroundColor: Color,
    val iconColor: Color
)

val onboardingPages = listOf(
    OnboardingPage(
        icon = Icons.Outlined.Description,
        title = "All-in-One PDF Solution",
        description = "View, edit, scan, merge, split, compress, and annotate PDFs - everything you need in one powerful app.",
        backgroundColor = Color(0xFF2563EB),
        iconColor = Color.White
    ),
    OnboardingPage(
        icon = Icons.Outlined.Scanner,
        title = "Scan Documents Instantly",
        description = "Transform your phone into a powerful scanner. Capture documents with automatic edge detection and enhancement.",
        backgroundColor = Color(0xFF059669),
        iconColor = Color.White
    ),
    OnboardingPage(
        icon = Icons.Outlined.Edit,
        title = "Edit Like a Pro",
        description = "Add text, images, signatures, and annotations. Rearrange pages, merge files, and more with intuitive tools.",
        backgroundColor = Color(0xFF7C3AED),
        iconColor = Color.White
    ),
    OnboardingPage(
        icon = Icons.Outlined.DarkMode,
        title = "Comfortable Reading",
        description = "Switch between reading modes including dark mode, sepia, and night mode for comfortable viewing anytime.",
        backgroundColor = Color(0xFFDC2626),
        iconColor = Color.White
    ),
    OnboardingPage(
        icon = Icons.Outlined.Security,
        title = "Secure & Private",
        description = "Your documents stay on your device. Add password protection to sensitive PDFs for extra security.",
        backgroundColor = Color(0xFFD97706),
        iconColor = Color.White
    )
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val pagerState = rememberPagerState(pageCount = { onboardingPages.size })
    val scope = rememberCoroutineScope()

    // GDPR opt-in: ask once, on first run. Analytics stays off unless the user accepts.
    val showAnalyticsConsent by viewModel.showAnalyticsConsent.collectAsStateWithLifecycle()
    if (showAnalyticsConsent) {
        AnalyticsConsentDialog(
            onAllow = { viewModel.respondToAnalyticsConsent(true) },
            onDecline = { viewModel.respondToAnalyticsConsent(false) },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            OnboardingPageContent(onboardingPages[page])
        }

        // Bottom controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Page indicators
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                repeat(onboardingPages.size) { index ->
                    PageIndicator(
                        isSelected = pagerState.currentPage == index,
                        color = onboardingPages[index].backgroundColor
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Skip button
                AnimatedVisibility(
                    visible = pagerState.currentPage < onboardingPages.size - 1,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    TextButton(
                        onClick = {
                            viewModel.completeOnboarding()
                            onComplete()
                        }
                    ) {
                        Text("Skip")
                    }
                }

                // Spacer when skip is hidden
                if (pagerState.currentPage == onboardingPages.size - 1) {
                    Spacer(Modifier.width(1.dp))
                }

                // Next/Get Started button
                Button(
                    onClick = {
                        if (pagerState.currentPage < onboardingPages.size - 1) {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        } else {
                            viewModel.completeOnboarding()
                            onComplete()
                        }
                    },
                    modifier = Modifier.height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = onboardingPages[pagerState.currentPage].backgroundColor
                    )
                ) {
                    Text(
                        text = if (pagerState.currentPage == onboardingPages.size - 1) "Get Started" else "Next",
                        fontWeight = FontWeight.SemiBold
                    )
                    if (pagerState.currentPage < onboardingPages.size - 1) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsConsentDialog(
    onAllow: () -> Unit,
    onDecline: () -> Unit,
) {
    AlertDialog(
        // Dismissing without choosing "Allow" must NOT grant consent.
        onDismissRequest = onDecline,
        icon = { Icon(Icons.Outlined.Analytics, contentDescription = null) },
        title = { Text("Help improve PdfMaster?") },
        text = {
            Text(
                "We'd like to collect anonymous usage analytics (which features are used, " +
                    "crashes) to improve the app. We never collect your documents, file names, " +
                    "or any personal data.\n\nYou can change this anytime in Settings.",
            )
        },
        confirmButton = {
            Button(onClick = onAllow) { Text("Allow") }
        },
        dismissButton = {
            TextButton(onClick = onDecline) { Text("No thanks") }
        },
    )
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Icon with gradient background
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            page.backgroundColor,
                            page.backgroundColor.copy(alpha = 0.7f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = page.iconColor
            )
        }

        Spacer(Modifier.height(48.dp))

        // Title
        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(16.dp))

        // Description
        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 26.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun PageIndicator(
    isSelected: Boolean,
    color: Color
) {
    val width by animateDpAsState(
        targetValue = if (isSelected) 32.dp else 8.dp,
        label = "indicatorWidth"
    )

    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .height(8.dp)
            .width(width)
            .clip(CircleShape)
            .background(
                if (isSelected) color else color.copy(alpha = 0.3f)
            )
    )
}

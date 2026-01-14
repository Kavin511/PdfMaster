package com.pdfmaster.app.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.pdfmaster.app.presentation.ui.home.HomeScreen
import com.pdfmaster.app.presentation.ui.viewer.ViewerScreen
import com.pdfmaster.app.presentation.ui.scanner.ScannerScreen
import com.pdfmaster.app.presentation.ui.editor.EditorScreen
import com.pdfmaster.app.presentation.ui.editor.PageManagerScreen
import com.pdfmaster.app.presentation.ui.tools.merge.MergeScreen
import com.pdfmaster.app.presentation.ui.tools.split.SplitScreen
import com.pdfmaster.app.presentation.ui.tools.compress.CompressScreen
import com.pdfmaster.app.presentation.ui.tools.convert.ConvertScreen
import com.pdfmaster.app.presentation.ui.annotate.AnnotateScreen
import com.pdfmaster.app.presentation.ui.signature.SignatureManagerScreen
import com.pdfmaster.app.presentation.ui.signature.AddSignatureScreen
import com.pdfmaster.app.presentation.ui.settings.SettingsScreen
import com.pdfmaster.app.presentation.ui.premium.PremiumScreen
import com.pdfmaster.app.presentation.ui.form.FormFillingScreen
import com.pdfmaster.app.presentation.ui.onboarding.OnboardingScreen
import com.pdfmaster.app.presentation.ui.password.UnlockPdfScreen
import com.pdfmaster.app.presentation.ui.password.SetPasswordScreen

private const val ANIMATION_DURATION = 300

@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: Screen = Screen.Home,
    onOpenPdf: ((String) -> Unit)? = null
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(ANIMATION_DURATION)
            ) + fadeIn(animationSpec = tween(ANIMATION_DURATION))
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(ANIMATION_DURATION)
            ) + fadeOut(animationSpec = tween(ANIMATION_DURATION))
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(ANIMATION_DURATION)
            ) + fadeIn(animationSpec = tween(ANIMATION_DURATION))
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(ANIMATION_DURATION)
            ) + fadeOut(animationSpec = tween(ANIMATION_DURATION))
        }
    ) {
        // Onboarding
        composable<Screen.Onboarding> {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(Screen.Home) {
                        popUpTo(Screen.Onboarding) { inclusive = true }
                    }
                }
            )
        }

        // Home
        composable<Screen.Home> {
            HomeScreen(
                onOpenPdf = { uri, title ->
                    navController.navigate(Screen.Viewer(uri = uri, title = title))
                },
                onNavigateToScanner = {
                    navController.navigate(Screen.Scanner)
                },
                onNavigateToMerge = {
                    navController.navigate(Screen.Merge)
                },
                onNavigateToSplit = {
                    navController.navigate(Screen.Split)
                },
                onNavigateToCompress = {
                    navController.navigate(Screen.Compress)
                },
                onNavigateToConvert = {
                    navController.navigate(Screen.Convert)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings)
                },
                onNavigateToPremium = {
                    navController.navigate(Screen.Premium)
                }
            )
        }

        // Viewer
        composable<Screen.Viewer> { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.Viewer>()
            ViewerScreen(
                uri = route.uri,
                title = route.title,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEditor = { uri ->
                    navController.navigate(Screen.Editor(uri = uri))
                },
                onNavigateToPageManager = { uri ->
                    navController.navigate(Screen.PageManager(uri = uri))
                },
                onNavigateToAnnotate = { uri ->
                    navController.navigate(Screen.Annotate(uri = uri))
                },
                onNavigateToSign = { uri ->
                    navController.navigate(Screen.AddSignature(uri = uri))
                }
            )
        }

        // Editor
        composable<Screen.Editor> { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.Editor>()
            EditorScreen(
                uri = route.uri,
                title = route.title,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Page Manager
        composable<Screen.PageManager> { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.PageManager>()
            PageManagerScreen(
                uri = route.uri,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Scanner
        composable<Screen.Scanner> {
            ScannerScreen(
                onNavigateBack = { navController.popBackStack() },
                onScanComplete = { uri ->
                    navController.popBackStack()
                    navController.navigate(Screen.Viewer(uri = uri))
                }
            )
        }

        // Merge
        composable<Screen.Merge> {
            MergeScreen(
                onNavigateBack = { navController.popBackStack() },
                onMergeComplete = { uri ->
                    navController.popBackStack()
                    navController.navigate(Screen.Viewer(uri = uri))
                }
            )
        }

        // Split
        composable<Screen.Split> {
            SplitScreen(
                onNavigateBack = { navController.popBackStack() },
                onSelectPdf = { uri ->
                    navController.navigate(Screen.SplitDocument(uri = uri))
                },
                onSplitComplete = { uri ->
                    navController.popBackStack()
                    navController.navigate(Screen.Viewer(uri = uri))
                }
            )
        }

        composable<Screen.SplitDocument> { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.SplitDocument>()
            SplitScreen(
                preSelectedUri = route.uri,
                onNavigateBack = { navController.popBackStack() },
                onSelectPdf = {},
                onSplitComplete = { uri ->
                    navController.popBackStack()
                    navController.navigate(Screen.Viewer(uri = uri))
                }
            )
        }

        // Compress
        composable<Screen.Compress> {
            CompressScreen(
                onNavigateBack = { navController.popBackStack() },
                onCompressComplete = { uri ->
                    navController.popBackStack()
                    navController.navigate(Screen.Viewer(uri = uri))
                }
            )
        }

        // Convert
        composable<Screen.Convert> {
            ConvertScreen(
                onNavigateBack = { navController.popBackStack() },
                onConvertComplete = { uri ->
                    navController.popBackStack()
                    navController.navigate(Screen.Viewer(uri = uri))
                }
            )
        }

        // Annotate
        composable<Screen.Annotate> { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.Annotate>()
            AnnotateScreen(
                uri = route.uri,
                onNavigateBack = { navController.popBackStack() },
                onSaveComplete = { annotatedUri ->
                    navController.popBackStack()
                    navController.navigate(Screen.Viewer(uri = annotatedUri))
                }
            )
        }

        // Signature Manager
        composable<Screen.SignatureManager> {
            SignatureManagerScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Add Signature
        composable<Screen.AddSignature> { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.AddSignature>()
            AddSignatureScreen(
                uri = route.uri,
                onNavigateBack = { navController.popBackStack() },
                onSignComplete = { signedUri ->
                    navController.popBackStack()
                    navController.navigate(Screen.Viewer(uri = signedUri))
                }
            )
        }

        // Settings
        composable<Screen.Settings> {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPremium = {
                    navController.navigate(Screen.Premium)
                }
            )
        }

        // Premium
        composable<Screen.Premium> {
            PremiumScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Form Filling
        composable<Screen.FillForm> { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.FillForm>()
            FormFillingScreen(
                uri = route.uri,
                onNavigateBack = { navController.popBackStack() },
                onSaveComplete = { savedUri ->
                    navController.popBackStack()
                    navController.navigate(Screen.Viewer(uri = savedUri))
                }
            )
        }

        // Password Protection
        composable<Screen.UnlockPdf> { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.UnlockPdf>()
            UnlockPdfScreen(
                uri = route.uri,
                onNavigateBack = { navController.popBackStack() },
                onUnlockSuccess = { unlockedUri ->
                    navController.popBackStack()
                    navController.navigate(Screen.Viewer(uri = unlockedUri))
                }
            )
        }

        composable<Screen.ProtectPdf> { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.ProtectPdf>()
            SetPasswordScreen(
                uri = route.uri,
                onNavigateBack = { navController.popBackStack() },
                onPasswordSet = { protectedUri ->
                    navController.popBackStack()
                    navController.navigate(Screen.Viewer(uri = protectedUri))
                }
            )
        }
    }
}

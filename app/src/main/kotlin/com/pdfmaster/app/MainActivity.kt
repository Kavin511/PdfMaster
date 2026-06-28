package com.pdfmaster.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.pdfmaster.app.data.local.preferences.UserPreferences
import com.pdfmaster.app.domain.repository.ThemeMode
import com.pdfmaster.app.presentation.navigation.NavGraph
import com.pdfmaster.app.presentation.navigation.Screen
import com.pdfmaster.app.presentation.theme.PdfMasterTheme
import com.pdfmaster.app.presentation.ui.onboarding.OnboardingViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var userPreferences: UserPreferences

    private var pendingUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle intent if app was launched with a PDF
        handleIntent(intent)

        setContent {
            // Collect theme settings from preferences
            val themeMode by userPreferences.getThemeMode().collectAsState(initial = ThemeMode.SYSTEM)
            val dynamicColorEnabled by userPreferences.getDynamicColorEnabled().collectAsState(initial = false)

            // Determine if dark theme based on user preference
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            PdfMasterTheme(
                darkTheme = darkTheme,
                dynamicColor = dynamicColorEnabled
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    var initialUri by remember { mutableStateOf(pendingUri) }

                    // Check if first launch
                    val onboardingViewModel: OnboardingViewModel = hiltViewModel()
                    val isFirstLaunch by onboardingViewModel.isFirstLaunch.collectAsState()

                    // Determine start destination
                    val startDestination = if (isFirstLaunch) Screen.Onboarding else Screen.Home

                    // Navigate to viewer if launched with PDF
                    LaunchedEffect(initialUri) {
                        initialUri?.let { uri ->
                            navController.navigate(
                                Screen.Viewer(
                                    uri = uri.toString(),
                                    title = getFileNameFromUri(uri)
                                )
                            )
                            initialUri = null
                            pendingUri = null
                        }
                    }

                    NavGraph(
                        navController = navController,
                        startDestination = startDestination
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_VIEW -> {
                intent.data?.let { uri ->
                    pendingUri = uri
                }
            }
            Intent.ACTION_SEND -> {
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uri ->
                    pendingUri = uri
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.firstOrNull()?.let { uri ->
                    pendingUri = uri
                }
            }
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    cursor.getString(nameIndex)
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
}

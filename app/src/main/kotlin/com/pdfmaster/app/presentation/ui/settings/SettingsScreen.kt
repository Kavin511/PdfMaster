package com.pdfmaster.app.presentation.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdfmaster.app.domain.repository.ThemeMode
import com.pdfmaster.app.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPremium: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showThemeDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // Premium Section
            item {
                Card(
                    onClick = onNavigateToPremium,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.WorkspacePremium, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("PDF Master Pro", fontWeight = FontWeight.SemiBold)
                            Text("Unlock all features", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.7f))
                        }
                        Icon(Icons.Outlined.ChevronRight, null)
                    }
                }
            }

            // Appearance Section
            item { SettingsSectionHeader("Appearance") }
            item {
                SettingsItem(
                    icon = Icons.Outlined.DarkMode,
                    title = "Theme",
                    subtitle = when(uiState.themeMode) { ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark"; ThemeMode.SYSTEM -> "System default" },
                    onClick = { showThemeDialog = true }
                )
            }
            item {
                SettingsSwitch(
                    icon = Icons.Outlined.Palette,
                    title = "Dynamic Colors",
                    subtitle = "Use Material You colors",
                    checked = uiState.dynamicColorEnabled,
                    onCheckedChange = { viewModel.setDynamicColor(it) }
                )
            }

            // Viewer Section
            item { SettingsSectionHeader("Viewer") }
            item {
                SettingsSwitch(
                    icon = Icons.Outlined.LightMode,
                    title = "Keep Screen On",
                    subtitle = "Prevent screen from turning off while viewing",
                    checked = uiState.keepScreenOn,
                    onCheckedChange = { viewModel.setKeepScreenOn(it) }
                )
            }

            // Storage Section
            item { SettingsSectionHeader("Storage") }
            item {
                SettingsItem(
                    icon = Icons.Outlined.CleaningServices,
                    title = "Clear Cache",
                    subtitle = "Free up storage space",
                    onClick = { viewModel.clearCache() }
                )
            }

            // About Section
            item { SettingsSectionHeader("About") }
            item {
                SettingsItem(
                    icon = Icons.Outlined.Info,
                    title = "Version",
                    subtitle = "1.0.0",
                    onClick = {}
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Outlined.Star,
                    title = "Rate App",
                    subtitle = "Love the app? Leave a review!",
                    onClick = { viewModel.openPlayStore() }
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Outlined.Email,
                    title = "Send Feedback",
                    subtitle = "Help us improve",
                    onClick = { viewModel.sendFeedback() }
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Outlined.Policy,
                    title = "Privacy Policy",
                    onClick = { viewModel.openPrivacyPolicy() }
                )
            }
        }
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Theme") },
            text = {
                Column {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { viewModel.setThemeMode(mode); showThemeDialog = false }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = uiState.themeMode == mode, onClick = { viewModel.setThemeMode(mode); showThemeDialog = false })
                            Spacer(Modifier.width(12.dp))
                            Text(when(mode) { ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark"; ThemeMode.SYSTEM -> "System default" })
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showThemeDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { Icon(icon, null) },
        trailingContent = { Icon(Icons.Outlined.ChevronRight, null) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun SettingsSwitch(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) }
    )
}

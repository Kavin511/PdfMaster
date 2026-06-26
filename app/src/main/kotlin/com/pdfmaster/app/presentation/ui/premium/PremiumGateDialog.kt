package com.pdfmaster.app.presentation.ui.premium

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.pdfmaster.app.billing.PremiumPrompt

/**
 * Shared paywall prompt shown whenever a tool hits a free-tier wall.
 * Pass the [PremiumPrompt] from a ViewModel's uiState; renders nothing when null.
 *
 * Usage:
 * ```
 * PremiumGateDialog(
 *     prompt = uiState.premiumPrompt,
 *     onDismiss = viewModel::clearPremiumPrompt,
 *     onUpgrade = { viewModel.clearPremiumPrompt(); onNavigateToPremium() },
 * )
 * ```
 */
@Composable
fun PremiumGateDialog(
    prompt: PremiumPrompt?,
    onDismiss: () -> Unit,
    onUpgrade: () -> Unit,
) {
    if (prompt == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.WorkspacePremium, contentDescription = null) },
        title = { Text(prompt.title) },
        text = { Text(prompt.message) },
        confirmButton = {
            Button(onClick = onUpgrade) { Text("Upgrade") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not now") }
        },
    )
}

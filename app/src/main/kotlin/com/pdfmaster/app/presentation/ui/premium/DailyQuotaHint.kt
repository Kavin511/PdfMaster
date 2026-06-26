package com.pdfmaster.app.presentation.ui.premium

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Small nudge chip showing how many free uses of a daily-limited tool remain today.
 * Renders nothing for premium users. Tapping it routes to the paywall.
 *
 * @param unitLabel plural noun for the action, e.g. "compressions", "splits".
 */
@Composable
fun DailyQuotaHint(
    isPremium: Boolean,
    remaining: Int,
    unitLabel: String,
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isPremium) return

    val depleted = remaining <= 0
    AssistChip(
        onClick = onUpgrade,
        modifier = modifier,
        leadingIcon = {
            Icon(
                imageVector = if (depleted) Icons.Outlined.WorkspacePremium else Icons.Outlined.Bolt,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
        label = {
            Text(
                if (depleted) "No free $unitLabel left today — upgrade for unlimited"
                else "$remaining free $unitLabel left today"
            )
        },
    )
}

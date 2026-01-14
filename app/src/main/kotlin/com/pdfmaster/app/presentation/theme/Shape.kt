package com.pdfmaster.app.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val Shapes = Shapes(
    // Extra small - chips, small buttons
    extraSmall = RoundedCornerShape(4.dp),

    // Small - text fields, small cards
    small = RoundedCornerShape(8.dp),

    // Medium - cards, dialogs
    medium = RoundedCornerShape(12.dp),

    // Large - bottom sheets, large cards
    large = RoundedCornerShape(16.dp),

    // Extra large - modals, full dialogs
    extraLarge = RoundedCornerShape(24.dp)
)

// Custom shapes for specific use cases
object PdfMasterShapes {
    val ToolCard = RoundedCornerShape(16.dp)
    val FileCard = RoundedCornerShape(12.dp)
    val Button = RoundedCornerShape(12.dp)
    val ButtonSmall = RoundedCornerShape(8.dp)
    val Chip = RoundedCornerShape(20.dp)
    val BottomSheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val Dialog = RoundedCornerShape(24.dp)
    val SearchBar = RoundedCornerShape(12.dp)
    val Fab = RoundedCornerShape(16.dp)
    val Thumbnail = RoundedCornerShape(8.dp)
    val TabIndicator = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
}

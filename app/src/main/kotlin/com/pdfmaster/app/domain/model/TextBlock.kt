package com.pdfmaster.app.domain.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color

data class TextBlock(
    val id: String,
    val pageNumber: Int,
    val text: String,
    val bounds: Rect,
    val fontSize: Float,
    val fontFamily: String = "default",
    val fontWeight: Int = 400,
    val fontStyle: FontStyle = FontStyle.NORMAL,
    val color: Color = Color.Black,
    val alignment: TextAlignment = TextAlignment.LEFT,
    val lineHeight: Float = 1.2f,
    val isEditable: Boolean = true
)

enum class FontStyle {
    NORMAL,
    ITALIC
}

enum class TextAlignment {
    LEFT,
    CENTER,
    RIGHT,
    JUSTIFY
}

data class TextEdit(
    val originalText: String,
    val newText: String,
    val textBlock: TextBlock
)

data class TextStyle(
    val fontSize: Float = 14f,
    val fontFamily: String = "default",
    val fontWeight: Int = 400,
    val fontStyle: FontStyle = FontStyle.NORMAL,
    val color: Color = Color.Black,
    val alignment: TextAlignment = TextAlignment.LEFT,
    val lineHeight: Float = 1.2f
)

// Available fonts for PDF editing
object PdfFonts {
    val availableFonts = listOf(
        "Helvetica",
        "Times-Roman",
        "Courier",
        "Arial",
        "Verdana",
        "Georgia",
        "Trebuchet MS"
    )

    val defaultFont = "Helvetica"
}

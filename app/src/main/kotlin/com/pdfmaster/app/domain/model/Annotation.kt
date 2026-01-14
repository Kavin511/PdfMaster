package com.pdfmaster.app.domain.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color

sealed class Annotation {
    abstract val id: String
    abstract val pageNumber: Int
    abstract val createdAt: Long
    abstract val modifiedAt: Long

    data class Highlight(
        override val id: String,
        override val pageNumber: Int,
        val rects: List<Rect>,
        val color: Color = Color(0xFFFEF08A),
        override val createdAt: Long = System.currentTimeMillis(),
        override val modifiedAt: Long = System.currentTimeMillis()
    ) : Annotation()

    data class Underline(
        override val id: String,
        override val pageNumber: Int,
        val rects: List<Rect>,
        val color: Color = Color(0xFF2563EB),
        override val createdAt: Long = System.currentTimeMillis(),
        override val modifiedAt: Long = System.currentTimeMillis()
    ) : Annotation()

    data class Strikethrough(
        override val id: String,
        override val pageNumber: Int,
        val rects: List<Rect>,
        val color: Color = Color(0xFFEF4444),
        override val createdAt: Long = System.currentTimeMillis(),
        override val modifiedAt: Long = System.currentTimeMillis()
    ) : Annotation()

    data class FreehandDrawing(
        override val id: String,
        override val pageNumber: Int,
        val points: List<Offset>,
        val color: Color = Color(0xFF000000),
        val strokeWidth: Float = 3f,
        override val createdAt: Long = System.currentTimeMillis(),
        override val modifiedAt: Long = System.currentTimeMillis()
    ) : Annotation()

    data class TextBox(
        override val id: String,
        override val pageNumber: Int,
        val position: Offset,
        val text: String,
        val fontSize: Float = 14f,
        val fontColor: Color = Color(0xFF000000),
        val backgroundColor: Color = Color.Transparent,
        val width: Float = 200f,
        override val createdAt: Long = System.currentTimeMillis(),
        override val modifiedAt: Long = System.currentTimeMillis()
    ) : Annotation()

    data class Shape(
        override val id: String,
        override val pageNumber: Int,
        val shapeType: ShapeType,
        val bounds: Rect,
        val strokeColor: Color = Color(0xFF2563EB),
        val fillColor: Color = Color.Transparent,
        val strokeWidth: Float = 2f,
        override val createdAt: Long = System.currentTimeMillis(),
        override val modifiedAt: Long = System.currentTimeMillis()
    ) : Annotation()

    data class StickyNote(
        override val id: String,
        override val pageNumber: Int,
        val position: Offset,
        val text: String,
        val color: Color = Color(0xFFFEF08A),
        val isExpanded: Boolean = false,
        override val createdAt: Long = System.currentTimeMillis(),
        override val modifiedAt: Long = System.currentTimeMillis()
    ) : Annotation()

    data class Stamp(
        override val id: String,
        override val pageNumber: Int,
        val stampType: StampType,
        val position: Offset,
        val scale: Float = 1f,
        val rotation: Float = 0f,
        override val createdAt: Long = System.currentTimeMillis(),
        override val modifiedAt: Long = System.currentTimeMillis()
    ) : Annotation()

    data class Redaction(
        override val id: String,
        override val pageNumber: Int,
        val rects: List<Rect>,
        val color: Color = Color.Black,
        override val createdAt: Long = System.currentTimeMillis(),
        override val modifiedAt: Long = System.currentTimeMillis()
    ) : Annotation()
}

enum class ShapeType {
    RECTANGLE,
    CIRCLE,
    OVAL,
    LINE,
    ARROW
}

enum class StampType(val displayName: String) {
    APPROVED("Approved"),
    REJECTED("Rejected"),
    DRAFT("Draft"),
    CONFIDENTIAL("Confidential"),
    FINAL("Final"),
    FOR_REVIEW("For Review"),
    VOID("Void"),
    COMPLETED("Completed")
}

enum class AnnotationTool {
    NONE,
    HIGHLIGHT,
    UNDERLINE,
    STRIKETHROUGH,
    FREEHAND,
    TEXT,
    SHAPE_RECTANGLE,
    SHAPE_CIRCLE,
    SHAPE_LINE,
    SHAPE_ARROW,
    STICKY_NOTE,
    STAMP,
    REDACT,
    ERASER
}

package com.pdfmaster.app.util

import androidx.compose.ui.geometry.Offset

/**
 * Maps gesture coordinates from a Compose container that displays a PDF-page bitmap with
 * [androidx.compose.ui.layout.ContentScale.Fit] into PDF points (top-left origin), for use
 * with [OpenPdfEditor] overlays (which then flip Y to PDF's bottom-left internally).
 *
 * This replaces the old per-screen `scaleX = bitmap.width / containerWidth` math, which
 * assumed the bitmap filled the whole container and used independent X/Y scales — both wrong
 * under `Fit`, which scales uniformly and letterboxes.
 *
 * All math is pure (no Android framework deps beyond [Offset]) so it is unit-testable.
 */
class PdfCoordinateMapper(
    private val bitmapWidth: Int,
    private val bitmapHeight: Int,
    private val containerWidth: Float,
    private val containerHeight: Float,
    private val pdfWidthPts: Float,
    private val pdfHeightPts: Float,
) {
    // Uniform Fit scale and the letterbox offsets of the rendered bitmap inside the container.
    private val dispScale: Float =
        if (bitmapWidth > 0 && bitmapHeight > 0)
            minOf(containerWidth / bitmapWidth, containerHeight / bitmapHeight)
        else 0f
    private val renderedW = bitmapWidth * dispScale
    private val renderedH = bitmapHeight * dispScale
    private val offsetX = (containerWidth - renderedW) / 2f
    private val offsetY = (containerHeight - renderedH) / 2f

    // bitmap px -> PDF points
    private val ptsPerBmpX = if (bitmapWidth > 0) pdfWidthPts / bitmapWidth else 0f
    private val ptsPerBmpY = if (bitmapHeight > 0) pdfHeightPts / bitmapHeight else 0f

    /** True if a container-space point falls on the rendered page (not the letterbox margin). */
    fun isOnPage(p: Offset): Boolean =
        dispScale > 0f &&
            p.x in offsetX..(offsetX + renderedW) &&
            p.y in offsetY..(offsetY + renderedH)

    /** Container-space point -> PDF points (top-left origin), or null if outside the page. */
    fun containerToPdf(p: Offset): Offset? {
        if (!isOnPage(p)) return null
        val bmpX = (p.x - offsetX) / dispScale
        val bmpY = (p.y - offsetY) / dispScale
        return Offset(bmpX * ptsPerBmpX, bmpY * ptsPerBmpY)
    }

    /** Convert a horizontal container-space length to PDF points. */
    fun lengthToPdfX(len: Float): Float = if (dispScale > 0f) (len / dispScale) * ptsPerBmpX else 0f

    /** Convert a vertical container-space length to PDF points. */
    fun lengthToPdfY(len: Float): Float = if (dispScale > 0f) (len / dispScale) * ptsPerBmpY else 0f
}

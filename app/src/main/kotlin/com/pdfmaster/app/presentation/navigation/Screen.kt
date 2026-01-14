package com.pdfmaster.app.presentation.navigation

import kotlinx.serialization.Serializable

sealed interface Screen {

    @Serializable
    data object Onboarding : Screen

    @Serializable
    data object Home : Screen

    @Serializable
    data class Viewer(
        val uri: String,
        val title: String? = null
    ) : Screen

    @Serializable
    data class Editor(
        val uri: String,
        val title: String? = null
    ) : Screen

    @Serializable
    data object Scanner : Screen

    @Serializable
    data class PageManager(
        val uri: String
    ) : Screen

    // Tools
    @Serializable
    data object Merge : Screen

    @Serializable
    data object Split : Screen

    @Serializable
    data class SplitDocument(
        val uri: String
    ) : Screen

    @Serializable
    data object Compress : Screen

    @Serializable
    data class CompressDocument(
        val uri: String
    ) : Screen

    @Serializable
    data object Convert : Screen

    @Serializable
    data class ConvertImages(
        val uris: List<String>
    ) : Screen

    // Annotation
    @Serializable
    data class Annotate(
        val uri: String
    ) : Screen

    // Signature
    @Serializable
    data object SignatureManager : Screen

    @Serializable
    data class AddSignature(
        val uri: String
    ) : Screen

    // Security
    @Serializable
    data class ProtectPdf(
        val uri: String
    ) : Screen

    @Serializable
    data class UnlockPdf(
        val uri: String
    ) : Screen

    // OCR
    @Serializable
    data class OcrDocument(
        val uri: String
    ) : Screen

    // Form Filling
    @Serializable
    data class FillForm(
        val uri: String
    ) : Screen

    // Settings & Others
    @Serializable
    data object Settings : Screen

    @Serializable
    data object Premium : Screen

    @Serializable
    data object About : Screen
}

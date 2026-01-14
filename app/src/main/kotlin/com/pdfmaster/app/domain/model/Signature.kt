package com.pdfmaster.app.domain.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import java.util.Date

data class Signature(
    val id: String,
    val name: String,
    val type: SignatureType,
    val data: SignatureData,
    val createdAt: Date = Date(),
    val isDefault: Boolean = false
)

sealed class SignatureData {
    data class DrawnSignature(
        val paths: List<SignaturePath>,
        val width: Int,
        val height: Int
    ) : SignatureData()

    data class ImageSignature(
        val imagePath: String,
        val width: Int,
        val height: Int
    ) : SignatureData()

    data class TextSignature(
        val text: String,
        val fontFamily: String = "default",
        val fontSize: Float = 32f,
        val color: Color = Color.Black
    ) : SignatureData()
}

data class SignaturePath(
    val points: List<Offset>,
    val color: Color = Color.Black,
    val strokeWidth: Float = 3f
)

enum class SignatureType {
    DRAWN,
    IMAGE,
    TEXT
}

data class SignaturePlacement(
    val signature: Signature,
    val pageNumber: Int,
    val position: Offset,
    val scale: Float = 1f,
    val rotation: Float = 0f
)

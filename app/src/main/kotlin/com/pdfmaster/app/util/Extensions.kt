package com.pdfmaster.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.content.FileProvider
import java.io.File

// Context Extensions
fun Context.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

fun Context.shareFile(file: File, mimeType: String = "application/pdf") {
    val uri = FileProvider.getUriForFile(
        this,
        "${packageName}.fileprovider",
        file
    )
    shareUri(uri, mimeType)
}

fun Context.shareUri(uri: Uri, mimeType: String = "application/pdf") {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(shareIntent, "Share PDF"))
}

fun Context.openUrl(url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    } catch (e: Exception) {
        showToast("Could not open link")
    }
}

fun Context.sendEmail(email: String, subject: String = "", body: String = "") {
    try {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        startActivity(intent)
    } catch (e: Exception) {
        showToast("No email app found")
    }
}

// Modifier Extensions
fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier = composed {
    clickable(
        indication = null,
        interactionSource = remember { MutableInteractionSource() },
        onClick = onClick
    )
}

@Composable
fun Modifier.hapticClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val haptic = LocalHapticFeedback.current
    return this.clickable(enabled = enabled) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onClick()
    }
}

// String Extensions
fun String.capitalizeWords(): String {
    return split(" ").joinToString(" ") { word ->
        word.lowercase().replaceFirstChar { it.uppercase() }
    }
}

fun String.removeExtension(): String {
    return substringBeforeLast(".")
}

fun String.getExtension(): String {
    return substringAfterLast(".", "")
}

// Number Extensions
fun Int.toPageLabel(): String {
    return "Page $this"
}

fun Long.toFileSizeString(): String {
    return FileUtils.formatFileSize(this)
}

// Uri Extensions
fun Uri.getFileName(context: Context): String {
    return FileUtils.getFileName(context, this)
}

fun Uri.getFileSize(context: Context): Long {
    return FileUtils.getFileSize(context, this)
}

// Collection Extensions
fun <T> List<T>.swap(index1: Int, index2: Int): List<T> {
    if (index1 !in indices || index2 !in indices) return this
    return toMutableList().apply {
        val temp = this[index1]
        this[index1] = this[index2]
        this[index2] = temp
    }
}

fun <T> List<T>.move(fromIndex: Int, toIndex: Int): List<T> {
    if (fromIndex !in indices || toIndex !in indices) return this
    return toMutableList().apply {
        val item = removeAt(fromIndex)
        add(toIndex, item)
    }
}

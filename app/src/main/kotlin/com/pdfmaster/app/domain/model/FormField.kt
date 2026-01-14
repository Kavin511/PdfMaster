package com.pdfmaster.app.domain.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import java.util.UUID

sealed class FormField {
    abstract val id: String
    abstract val pageIndex: Int
    abstract val bounds: Rect
    abstract val value: String

    data class TextField(
        override val id: String = UUID.randomUUID().toString(),
        override val pageIndex: Int,
        override val bounds: Rect,
        override val value: String = "",
        val placeholder: String = "",
        val isMultiline: Boolean = false,
        val maxLength: Int? = null
    ) : FormField()

    data class CheckBox(
        override val id: String = UUID.randomUUID().toString(),
        override val pageIndex: Int,
        override val bounds: Rect,
        override val value: String = "false",
        val isChecked: Boolean = false
    ) : FormField()

    data class RadioButton(
        override val id: String = UUID.randomUUID().toString(),
        override val pageIndex: Int,
        override val bounds: Rect,
        override val value: String = "",
        val groupName: String,
        val isSelected: Boolean = false
    ) : FormField()

    data class Dropdown(
        override val id: String = UUID.randomUUID().toString(),
        override val pageIndex: Int,
        override val bounds: Rect,
        override val value: String = "",
        val options: List<String> = emptyList()
    ) : FormField()

    data class Signature(
        override val id: String = UUID.randomUUID().toString(),
        override val pageIndex: Int,
        override val bounds: Rect,
        override val value: String = "",
        val signaturePath: String? = null
    ) : FormField()

    data class DateField(
        override val id: String = UUID.randomUUID().toString(),
        override val pageIndex: Int,
        override val bounds: Rect,
        override val value: String = "",
        val format: String = "MM/dd/yyyy"
    ) : FormField()
}

data class FilledForm(
    val documentUri: String,
    val fields: List<FormField>,
    val completedAt: Long = System.currentTimeMillis()
)

package com.pdfmaster.app.presentation.ui.editor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

/**
 * Activity that hosts ComPDFKit's document fragment for professional PDF editing.
 *
 * Features provided by ComPDFKit:
 * - Text editing (tap to edit existing text)
 * - Add text annotations
 * - Freehand drawing/ink
 * - Highlights, underlines, strikethrough
 * - Stamps and signatures
 * - Form filling
 * - Image insertion
 * - Page management
 * - Search
 * - Thumbnails
 *
 * Note: This activity uses reflection to load ComPDFKit classes to avoid
 * compile-time dependencies on specific SDK versions.
 */
class ComPdfKitEditorActivity : AppCompatActivity() {

    private var documentFragment: Any? = null
    private var tempFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = intent.data
        if (uri == null) {
            Toast.makeText(this, "No PDF file provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Copy URI content to a temp file that ComPDFKit can access
        val file = copyUriToTempFile(uri)
        if (file == null) {
            Toast.makeText(this, "Failed to open PDF file", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        tempFile = file

        // Try to load ComPDFKit fragment using reflection
        if (!initComPdfKitFragment(file.absolutePath)) {
            Toast.makeText(this, "ComPDFKit SDK not available", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Handle back press to save before exiting
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                saveAndFinish()
            }
        })
    }

    private fun initComPdfKitFragment(filePath: String): Boolean {
        return try {
            // Load CPDFDocumentFragment class
            val fragmentClass = Class.forName("com.compdfkit.tools.common.pdf.CPDFDocumentFragment")

            // Load CPDFConfiguration class and create default configuration
            val configClass = Class.forName("com.compdfkit.tools.common.pdf.config.CPDFConfiguration")
            val configBuilderClass = Class.forName("com.compdfkit.tools.common.pdf.config.CPDFConfiguration\$Builder")
            val configBuilder = configBuilderClass.getDeclaredConstructor().newInstance()
            val buildMethod = configBuilderClass.getMethod("build")
            val configuration = buildMethod.invoke(configBuilder)

            // Create fragment using newInstance
            val newInstanceMethod = fragmentClass.getMethod(
                "newInstance",
                String::class.java,
                String::class.java,
                configClass
            )
            val fragment = newInstanceMethod.invoke(null, filePath, null, configuration)
            documentFragment = fragment

            // Add fragment to activity
            val fragmentTransaction = supportFragmentManager.beginTransaction()
            fragmentTransaction.replace(android.R.id.content, fragment as androidx.fragment.app.Fragment, FRAGMENT_TAG)
            fragmentTransaction.commit()

            Log.d(TAG, "ComPDFKit fragment loaded successfully")
            true
        } catch (e: ClassNotFoundException) {
            Log.e(TAG, "ComPDFKit classes not found", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing ComPDFKit fragment", e)
            false
        }
    }

    private fun copyUriToTempFile(uri: Uri): File? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val fileName = getFileNameFromUri(uri) ?: "document.pdf"
            val tempFile = File(cacheDir, "compdfkit_temp_$fileName")

            FileOutputStream(tempFile).use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()

            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Error copying URI to temp file", e)
            null
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    cursor.getString(nameIndex)
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun saveAndFinish() {
        try {
            documentFragment?.let { fragment ->
                // Use reflection to get pdfView and save
                val getPdfViewMethod = fragment.javaClass.getMethod("getPdfView")
                val pdfView = getPdfViewMethod.invoke(fragment)

                if (pdfView != null) {
                    val getPdfDocMethod = pdfView.javaClass.getMethod("getCPdfReaderView")
                    val readerView = getPdfDocMethod.invoke(pdfView)

                    if (readerView != null) {
                        val getPdfDocumentMethod = readerView.javaClass.getMethod("getPDFDocument")
                        val pdfDocument = getPdfDocumentMethod.invoke(readerView)

                        if (pdfDocument != null) {
                            val hasChangesMethod = pdfDocument.javaClass.getMethod("hasChanges")
                            val hasChanges = hasChangesMethod.invoke(pdfDocument) as Boolean

                            if (hasChanges) {
                                val saveMethod = pdfDocument.javaClass.getMethod("save")
                                val success = saveMethod.invoke(pdfDocument) as Boolean
                                if (success) {
                                    copyTempFileBackToUri()
                                    Toast.makeText(this, "Changes saved", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving document", e)
        }
        finish()
    }

    private fun copyTempFileBackToUri() {
        val originalUri = intent.data ?: return
        val tempFile = this.tempFile ?: return

        try {
            // Try to write back to the original URI
            contentResolver.openOutputStream(originalUri)?.use { output ->
                tempFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            // If we can't write to the original, save to PdfMaster folder
            Log.w(TAG, "Cannot write to original URI, saving to app folder", e)
            saveToPdfMasterFolder(tempFile)
        }
    }

    private fun saveToPdfMasterFolder(tempFile: File) {
        try {
            val pdfMasterDir = File(getExternalFilesDir(null), "PdfMaster")
            if (!pdfMasterDir.exists()) {
                pdfMasterDir.mkdirs()
            }

            val fileName = tempFile.name.removePrefix("compdfkit_temp_")
            val destFile = File(pdfMasterDir, "edited_$fileName")

            tempFile.copyTo(destFile, overwrite = true)
            Toast.makeText(this, "Saved to: ${destFile.name}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving to PdfMaster folder", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up temp file
        tempFile?.delete()
    }

    companion object {
        private const val TAG = "ComPdfKitEditor"
        private const val FRAGMENT_TAG = "CPDFDocumentFragment"

        /**
         * Create an intent to launch the ComPDFKit editor
         */
        fun createIntent(context: Context, uri: Uri): Intent {
            return Intent(context, ComPdfKitEditorActivity::class.java).apply {
                data = uri
            }
        }

        /**
         * Create an intent to launch the ComPDFKit editor from a file path
         */
        fun createIntent(context: Context, filePath: String): Intent {
            return Intent(context, ComPdfKitEditorActivity::class.java).apply {
                data = Uri.fromFile(File(filePath))
            }
        }
    }
}

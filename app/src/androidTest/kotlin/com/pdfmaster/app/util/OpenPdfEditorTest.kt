package com.pdfmaster.app.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Regression test for the non-raster save path. Reproduces the signature save:
 * stamp a transparent signature bitmap onto a real PDF via [OpenPdfEditor.applyEdits]
 * and assert the output is a non-empty, valid PDF (the bug produced a 0-byte file).
 */
@RunWith(AndroidJUnit4::class)
class OpenPdfEditorTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    /** Writes the bundled test PDF asset to a temp file and returns its Uri. */
    private fun testPdfUri(): Uri {
        val out = File(context.cacheDir, "signtest_src.pdf")
        // The asset ships in the TEST apk, so read it from the instrumentation context,
        // not the target app context.
        val testContext = InstrumentationRegistry.getInstrumentation().context
        testContext.assets.open("signtest.pdf").use { input ->
            out.outputStream().use { input.copyTo(it) }
        }
        return Uri.fromFile(out)
    }

    /** A transparent signature-like bitmap (ARGB_8888 with alpha), as the UI produces. */
    private fun signatureBitmap(): Bitmap {
        val bmp = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.TRANSPARENT)
        val paint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 6f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        canvas.drawLine(20f, 180f, 380f, 20f, paint)
        canvas.drawLine(20f, 20f, 380f, 180f, paint)
        return bmp
    }

    @Test
    fun imageOverlay_producesNonEmptyPdf() = runBlocking {
        val src = testPdfUri()
        val outFile = File(context.cacheDir, "signed_out.pdf")
        if (outFile.exists()) outFile.delete()

        val ok = OpenPdfEditor.applyEdits(
            context = context,
            sourceUri = src,
            outputFile = outFile,
            images = listOf(
                OpenPdfEditor.ImageOverlay(
                    pageIndex = 0,
                    x = 100f, y = 200f, width = 200f, height = 100f,
                    bitmap = signatureBitmap(),
                )
            ),
        )

        assertTrue("applyEdits returned false", ok)
        assertTrue("output PDF is empty (0 bytes)", outFile.length() > 0L)
        // A valid PDF starts with %PDF-
        val header = outFile.inputStream().use { ByteArray(5).also { b -> it.read(b) } }
        assertTrue("output is not a PDF", String(header) == "%PDF-")

        // The whole point of the non-raster path: the original vector text must survive,
        // not be flattened to an image. Extract text from the signed output and assert it.
        PDFBoxResourceLoader.init(context)
        val extracted = PDDocument.load(outFile).use { PDFTextStripper().getText(it) }
        assertTrue(
            "original text layer was lost (page was rasterized): [$extracted]",
            extracted.contains("selectable after signing"),
        )
    }

    @Test
    fun textOverlay_producesNonEmptyPdf() = runBlocking {
        // Control: the text path is believed to work; confirms the harness + asset are sound.
        val src = testPdfUri()
        val outFile = File(context.cacheDir, "text_out.pdf")
        if (outFile.exists()) outFile.delete()

        val ok = OpenPdfEditor.applyEdits(
            context = context,
            sourceUri = src,
            outputFile = outFile,
            textEdits = listOf(
                OpenPdfEditor.TextEdit(pageIndex = 0, x = 100f, y = 300f, text = "Signed by test"),
            ),
        )

        assertTrue("applyEdits(text) returned false", ok)
        assertTrue("text output PDF is empty", outFile.length() > 0L)
    }
}

package com.pdfmaster.app.util

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Regression tests for the non-raster page-structure ops (merge / split / rotate). They assert
 * the output preserves vector text (extractable) and that page count/order/rotation are correct —
 * impossible if the pages were rasterized.
 */
@RunWith(AndroidJUnit4::class)
class PageOpsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun asset(name: String): Uri {
        val out = File(context.cacheDir, name)
        InstrumentationRegistry.getInstrumentation().context.assets.open(name).use { input ->
            out.outputStream().use { input.copyTo(it) }
        }
        return Uri.fromFile(out)
    }

    /** Text of a single 1-based page from a saved PDF. */
    private fun pageText(file: File, page: Int): String {
        PDFBoxResourceLoader.init(context)
        return PDDocument.load(file).use { doc ->
            PDFTextStripper().apply { startPage = page; endPage = page }.getText(doc)
        }
    }

    private fun pageCount(file: File): Int =
        PDDocument.load(file).use { it.numberOfPages }

    @Test
    fun merge_concatenatesAndPreservesText() = runBlocking {
        val three = asset("three_page.pdf")
        val out = File(context.cacheDir, "merged.pdf").apply { delete() }

        val ok = PdfUtils.mergePdfs(context, listOf(three, three), out)

        assertTrue("mergePdfs returned false", ok)
        assertEquals("merged page count", 6, pageCount(out))
        // First and fourth pages should both be PAGE_ONE (two copies concatenated).
        assertTrue(pageText(out, 1).contains("unique-marker-one"))
        assertTrue(pageText(out, 4).contains("unique-marker-one"))
        assertTrue(pageText(out, 6).contains("unique-marker-three"))
    }

    @Test
    fun extract_keepsSelectedPagesInOrder() = runBlocking {
        val three = asset("three_page.pdf")
        val out = File(context.cacheDir, "extracted.pdf").apply { delete() }

        // Reorder: take page index 2 (PAGE_THREE) then index 0 (PAGE_ONE).
        val ok = PdfUtils.extractPages(context, three, listOf(2, 0), out)

        assertTrue("extractPages returned false", ok)
        assertEquals("extracted page count", 2, pageCount(out))
        assertTrue("page 1 should be PAGE_THREE", pageText(out, 1).contains("unique-marker-three"))
        assertTrue("page 2 should be PAGE_ONE", pageText(out, 2).contains("unique-marker-one"))
    }

    @Test
    fun buildDocument_handlesBlankReorderAndRotation() = runBlocking {
        val three = asset("three_page.pdf")
        val out = File(context.cacheDir, "built.pdf").apply { delete() }

        // Plan: page 2 (PAGE_THREE), a blank page, page 0 (PAGE_ONE). Rotate output position 0 by 90.
        val ok = PdfUtils.buildDocument(
            context = context,
            sourceUri = three,
            pagePlan = listOf(2, -1, 0),
            rotations = mapOf(0 to 90f),
            outputFile = out,
        )

        assertTrue("buildDocument returned false", ok)
        assertEquals("output page count", 3, pageCount(out))
        assertTrue("pos 0 = PAGE_THREE", pageText(out, 1).contains("unique-marker-three"))
        // The inserted blank page has no extractable text.
        assertTrue("pos 1 should be blank", pageText(out, 2).isBlank())
        assertTrue("pos 2 = PAGE_ONE", pageText(out, 3).contains("unique-marker-one"))
        val rot0 = PDDocument.load(out).use { it.getPage(0).rotation }
        assertEquals("pos 0 rotated", 90, rot0)
    }

    @Test
    fun rotate_setsRotationAndPreservesText() = runBlocking {
        val three = asset("three_page.pdf")
        val out = File(context.cacheDir, "rotated.pdf").apply { delete() }

        val ok = PdfUtils.rotatePages(context, three, mapOf(0 to 90f), out)

        assertTrue("rotatePages returned false", ok)
        assertEquals("page count unchanged", 3, pageCount(out))
        val rotation = PDDocument.load(out).use { it.getPage(0).rotation }
        assertEquals("page 0 rotation", 90, rotation)
        // Text still present (not rasterized).
        assertTrue(pageText(out, 1).contains("unique-marker-one"))
    }
}

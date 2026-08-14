package com.verumomnis.forensic

import com.verumomnis.forensic.engine.OcrPolicy
import com.verumomnis.forensic.engine.TextExtractionCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * OCR must be proportional to actual scans: pages with a real text layer are
 * never OCR'd, thin pages without images are never OCR'd (their text layer IS
 * the page), and a repeated upload of the same document is served from cache.
 */
class OcrPolicyTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val thin = 30

    @Test
    fun pagesWithRealTextLayersAreNeverOcrd() {
        // A typical machine-made bundle: emails, letters, invoices.
        val lengths = listOf(1200, 800, 4500, 300)
        val images = listOf(true, true, true, true) // even image-bearing pages skip OCR when text is rich
        assertTrue(OcrPolicy.selectOcrPages(lengths, images, thin).isEmpty())
    }

    @Test
    fun thinPageWithoutImagesIsNotOcrd() {
        // The classic waste case: a page whose entire content is "1".
        val lengths = listOf(1, 1, 1)
        val images = listOf(false, false, false)
        assertTrue(OcrPolicy.selectOcrPages(lengths, images, thin).isEmpty())
    }

    @Test
    fun onlyScannedPagesAreSelected() {
        // 187-page bundle shape: mostly text, a few true scans, a few bare page numbers.
        val lengths = List(187) { idx ->
            when (idx) {
                24, 25, 26, 27, 51, 55, 80, 105 -> 1     // bare page numbers
                10, 42, 99 -> 0                           // true scans (no text layer)
                else -> 900                               // machine-made text
            }
        }
        val images = List(187) { idx -> idx in setOf(10, 42, 99) }
        assertEquals(listOf(10, 42, 99), OcrPolicy.selectOcrPages(lengths, images, thin))
    }

    @Test
    fun unknownImagePresenceFailsOpenToOcr() {
        // Malformed resources must not silently drop a possible scan.
        val selected = OcrPolicy.selectOcrPages(listOf(0, 0), listOf(), thin)
        assertEquals(listOf(0, 1), selected)
    }

    @Test
    fun cacheRoundTripsByContentHash() {
        val cache = TextExtractionCache(temp.newFolder())
        val sha = "ab".repeat(64)
        assertNull(cache.get(sha))
        cache.put(sha, "extracted text")
        assertEquals("extracted text", cache.get(sha))
        // A different document (different hash) misses.
        assertNull(cache.get("cd".repeat(64)))
    }

    @Test
    fun cacheRejectsNonHashKeys() {
        val cache = TextExtractionCache(temp.newFolder())
        cache.put("../evil", "x")
        assertNull(cache.get("../evil"))
        cache.put("short", "x")
        assertNull(cache.get("short"))
    }

    @Test
    fun cachePrunesOldestBeyondCapacity() {
        val root = temp.newFolder()
        val cache = TextExtractionCache(root, maxEntries = 3)
        val keys = (0 until 5).map { it.toString().padStart(2, '0').repeat(64) }
        for ((i, k) in keys.withIndex()) {
            cache.put(k, "doc $i")
            // Distinct mtimes so pruning order is deterministic on coarse filesystems.
            java.io.File(root, "$k.txt").setLastModified(1_000_000L + i * 60_000L)
        }
        val remaining = root.listFiles()!!.map { it.nameWithoutExtension }.toSet()
        assertEquals(3, remaining.size)
        assertTrue(keys[3] in remaining && keys[4] in remaining)
        assertTrue(keys[0] !in remaining)
    }
}

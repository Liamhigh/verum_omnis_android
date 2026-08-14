package com.verumomnis.forensic.engine

import java.io.File

/**
 * Content-addressed cache of extracted PDF text.
 *
 * Keyed by the document's SHA-512, so identity is the content itself: the same
 * evidence bundle uploaded twice — or re-scanned in a new case — reads out of
 * cache in milliseconds instead of re-parsing and re-OCR'ing every page.
 * A changed document changes its hash and simply misses.
 *
 * Cache entries hold extracted TEXT ONLY, in the app's private cache directory
 * — never evidence bytes — and the OS may clear that directory at will; a miss
 * just re-extracts. Bounded by [maxEntries]: oldest entries (by modification
 * time) are pruned first.
 */
class TextExtractionCache(
    private val root: File,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES
) {

    companion object {
        const val DEFAULT_MAX_ENTRIES = 64
        private val SHA512_HEX = Regex("^[0-9a-f]{128}$")
    }

    /** Cached text for [sha512Hex], or null on miss/unreadable entry. */
    fun get(sha512Hex: String): String? {
        val key = sha512Hex.lowercase()
        if (!SHA512_HEX.matches(key)) return null
        val file = File(root, "$key.txt")
        if (!file.isFile) return null
        return runCatching {
            // Touch so pruning treats recently used entries as fresh.
            file.setLastModified(System.currentTimeMillis())
            file.readText()
        }.getOrNull()
    }

    /** Stores [text] for [sha512Hex], then prunes to [maxEntries]. Best-effort. */
    fun put(sha512Hex: String, text: String) {
        val key = sha512Hex.lowercase()
        if (!SHA512_HEX.matches(key) || text.isEmpty()) return
        runCatching {
            root.mkdirs()
            File(root, "$key.txt").writeText(text)
            prune()
        }
    }

    private fun prune() {
        val entries = root.listFiles { f -> f.isFile && f.extension == "txt" } ?: return
        if (entries.size <= maxEntries) return
        entries.sortedBy { it.lastModified() }
            .take(entries.size - maxEntries)
            .forEach { runCatching { it.delete() } }
    }
}

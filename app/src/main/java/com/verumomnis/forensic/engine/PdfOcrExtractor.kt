package com.verumomnis.forensic.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.verumomnis.forensic.crypto.Sha512
import java.io.File

/**
 * Hybrid PDF text extractor with on-device OCR for image-only pages.
 *
 * A page's embedded text layer is used when present (fast, exact). Only pages
 * that have little or no text layer — scanned exhibits, photographed documents —
 * are rendered with Android's PdfRenderer and read with Google ML Kit's
 * on-device text recognition. Nothing leaves the device, preserving the
 * "nothing leaves your device" guarantee, while giving far faster and more
 * accurate OCR than the browser's WebAssembly Tesseract.
 *
 * This mirrors the website's selective OCR (only image-only pages are OCR'd),
 * but uses the platform's native recogniser instead of tesseract.js.
 *
 * Two guards keep OCR work proportional to actual scans ([OcrPolicy]):
 * a thin page must also carry a raster image to be OCR'd (a page whose whole
 * content is a page number is already fully extracted), and extraction is
 * cached by document SHA-512 ([TextExtractionCache]) so re-uploading the same
 * bundle never re-parses or re-OCRs it.
 */
class PdfOcrExtractor(
    private val context: Context,
    /** Per-page text-layer length below which a page is treated as image-only. */
    private val thinPageChars: Int = DEFAULT_THIN_PAGE_CHARS,
    /** Cap on pages to OCR, to bound worst-case time on very large scans. */
    private val maxOcrPages: Int = DEFAULT_MAX_OCR_PAGES,
    /**
     * Content-addressed extraction cache; null disables. The same document
     * (by SHA-512) is parsed and OCR'd once, then served in milliseconds on
     * every later upload or re-scan.
     */
    private val cache: TextExtractionCache? =
        TextExtractionCache(File(context.cacheDir, "vo_text_cache")),
) : PdfTextExtractor {

    companion object {
        /** A page with fewer text-layer chars than this is treated as image-only. */
        const val DEFAULT_THIN_PAGE_CHARS: Int = 30
        /** Worst-case bound on how many image-only pages get OCR'd per document. */
        const val DEFAULT_MAX_OCR_PAGES: Int = 200
        /** Rasterisation resolution for OCR — 200 dpi balances accuracy and memory. */
        private const val RENDER_DPI: Float = 200f
        /** PDF user-space is 72 units/inch; scale = dpi / 72. */
        private const val PDF_POINTS_PER_INCH: Float = 72f
        /** Hard cap on either bitmap dimension so a huge page can't OOM the device. */
        private const val MAX_RENDER_DIMENSION_PX: Int = 2600
    }

    /** Number of image-only pages OCR'd and skipped in the last run (for notes). */
    var lastOcrPageCount: Int = 0
        private set
    var lastSkippedPageCount: Int = 0
        private set

    /** True when the last extraction was served from the content cache. */
    var lastServedFromCache: Boolean = false
        private set

    /**
     * Extract text from a PDF, OCR'ing only image-only pages.
     *
     * BLOCKING: this waits on PDFBox parsing and (via `Tasks.await`) on ML Kit
     * OCR, so it MUST be called off the main thread. All current callers invoke
     * it inside `Dispatchers.IO` coroutines (VerumViewModel, MediaIngestor).
     */
    /**
     * File-based extraction — the path large evidence should take.
     *
     * Nothing is held in memory: PDFBox loads from the file, and [ocrPages]
     * renders directly from it instead of writing a temporary copy. A 150 MB
     * bundle costs roughly its own size on disk and near-constant heap, where
     * the ByteArray path cost several multiples of it.
     */
    override fun extractText(file: File): String = extract(pdf = file, bytes = null)

    override fun extractText(bytes: ByteArray): String = extract(pdf = null, bytes = bytes)

    /**
     * Exactly one of [pdf] / [bytes] is non-null. Kept as a single body so the
     * page-merge logic cannot drift between the two entry points.
     */
    private fun extract(pdf: File?, bytes: ByteArray?): String {
        lastOcrPageCount = 0
        lastSkippedPageCount = 0
        lastServedFromCache = false

        // 0. Content-addressed cache: the same document is extracted once.
        val contentHash = cache?.let {
            runCatching { if (pdf != null) Sha512.hash(pdf) else Sha512.hash(bytes!!) }.getOrNull()
        }
        if (contentHash != null) {
            cache?.get(contentHash)?.let {
                lastServedFromCache = true
                return it
            }
        }

        // 1. Per-page text-layer extraction with PDFBox, noting which pages
        //    actually carry raster images (scans) — a thin page without any
        //    image has nothing OCR could read; its text layer IS the page.
        val pageText: MutableList<String> = mutableListOf()
        val pageHasImages: MutableList<Boolean> = mutableListOf()
        try {
            (if (pdf != null) PDDocument.load(pdf) else PDDocument.load(bytes!!)).use { doc ->
                val stripper = PDFTextStripper()
                val n = doc.numberOfPages
                for (i in 1..n) {
                    stripper.startPage = i
                    stripper.endPage = i
                    pageText.add((stripper.getText(doc) ?: "").trim())
                    pageHasImages.add(pageBearsImage(doc, i - 1))
                }
            }
        } catch (e: Exception) {
            // PDFBox failed entirely; fall back to whole-doc OCR below.
        }

        // Which pages need OCR? Thin text layer AND an actual image to read.
        val needsOcr = OcrPolicy.selectOcrPages(pageText.map { it.length }, pageHasImages, thinPageChars)
        if (pageText.isNotEmpty() && needsOcr.isEmpty()) {
            val text = pageText.joinToString("\n").trim() // machine-readable; no OCR needed
            if (contentHash != null && text.isNotEmpty()) cache?.put(contentHash, text)
            return text
        }

        // 2. OCR the image-only pages (or all pages if PDFBox found nothing).
        val ocrByPage = ocrPages(pdf, bytes, if (pageText.isEmpty()) null else needsOcr.toSet())

        // 3. Merge: text layer where present, OCR where we have it. A thin page
        //    with no OCR result still contributes its text layer — a bare page
        //    number is that page's complete content, not a failure.
        val out = StringBuilder()
        val total = if (pageText.isNotEmpty()) pageText.size else ocrByPage.keys.maxOrNull()?.plus(1) ?: 0
        for (i in 0 until total) {
            val layer = pageText.getOrNull(i) ?: ""
            val ocr = ocrByPage[i]
            when {
                layer.length >= thinPageChars -> out.append(layer)
                ocr != null && ocr.isNotBlank() -> out.append("[OCR] ").append(ocr)
                layer.isNotBlank() -> out.append(layer)
                else -> { /* page genuinely unreadable */ }
            }
            out.append('\n')
        }
        val text = out.toString().trim()
        if (contentHash != null && text.isNotEmpty()) cache?.put(contentHash, text)
        return text
    }

    /**
     * Whether the page's resources include a raster image XObject. Unknown or
     * malformed resources count as image-bearing: over-OCR'ing a broken page is
     * recoverable, skipping a real scan drops evidence.
     */
    private fun pageBearsImage(doc: PDDocument, pageIndex: Int): Boolean = runCatching {
        val resources = doc.getPage(pageIndex).resources ?: return@runCatching false
        resources.xObjectNames.any { name -> resources.isImageXObject(name) }
    }.getOrDefault(true)

    /**
     * Render pages to bitmaps and run ML Kit OCR. If [only] is null, OCR every
     * page (whole document is image-only); otherwise OCR just those indices.
     */
    private fun ocrPages(pdf: File?, bytes: ByteArray?, only: Set<Int>?): Map<Int, String> {
        val result = HashMap<Int, String>()
        // PdfRenderer needs a seekable file descriptor. When the caller already
        // has the PDF on disk — the streaming path — render straight from it;
        // only the in-memory path has to spill a temporary copy.
        val tmp = if (pdf == null) File.createTempFile("vo_ocr_", ".pdf", context.cacheDir) else null
        val source = pdf ?: tmp!!
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            if (tmp != null) tmp.writeBytes(bytes!!)
            pfd = ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            var ocrd = 0
            for (i in 0 until renderer.pageCount) {
                if (only != null && !only.contains(i)) continue
                if (ocrd >= maxOcrPages) { lastSkippedPageCount++; continue }
                try {
                    val text = renderPageAndRecognise(renderer, i, recognizer)
                    if (text.isNotBlank()) { result[i] = text; lastOcrPageCount++ }
                    ocrd++
                } catch (e: Exception) {
                    // per-page failure: leave the page unread, keep going
                }
            }
        } catch (e: Exception) {
            // whole-document render failed; return whatever we have
        } finally {
            try { renderer?.close() } catch (e: Exception) {}
            try { pfd?.close() } catch (e: Exception) {}
            try { recognizer.close() } catch (e: Exception) {}
            // Only the spilled copy is deleted — never the caller's vaulted file.
            try { tmp?.delete() } catch (e: Exception) {}
        }
        return result
    }

    private fun renderPageAndRecognise(renderer: PdfRenderer, index: Int, recognizer: com.google.mlkit.vision.text.TextRecognizer): String {
        val page = renderer.openPage(index)
        try {
            // DPI-based rasterisation (page.width/height are PDF points = 1/72").
            // Capped per side so an oversized page can't allocate an OOM bitmap.
            val scale = RENDER_DPI / PDF_POINTS_PER_INCH
            val w = (page.width * scale).toInt().coerceIn(1, MAX_RENDER_DIMENSION_PX)
            val h = (page.height * scale).toInt().coerceIn(1, MAX_RENDER_DIMENSION_PX)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Canvas(bmp).drawColor(Color.WHITE) // white background: OCR needs dark-on-light
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return try {
                val visionText = Tasks.await(recognizer.process(InputImage.fromBitmap(bmp, 0)))
                visionText.text.trim()
            } finally {
                bmp.recycle()
            }
        } finally {
            page.close()
        }
    }
}

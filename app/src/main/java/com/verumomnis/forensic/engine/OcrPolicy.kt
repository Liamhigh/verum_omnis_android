package com.verumomnis.forensic.engine

/**
 * Decides which PDF pages actually need OCR. Pure logic, kept out of
 * [PdfOcrExtractor] so the policy is unit-testable off-device.
 *
 * A page earns OCR only when BOTH hold:
 *
 *  1. its embedded text layer is thin (under [thinPageChars] characters), and
 *  2. it actually contains a raster image — a scan or photograph.
 *
 * The second condition is what stops the classic waste case: a machine-made
 * page whose entire content is a page number ("1") has a thin text layer, but
 * there is nothing on it for OCR to read — its text layer already IS the page.
 * Running OCR there burns seconds per page and returns the same "1".
 *
 * When image presence cannot be determined (malformed resources), the page is
 * treated as bearing images — over-OCR'ing a broken page is recoverable, while
 * skipping a real scan silently drops evidence.
 */
object OcrPolicy {

    /**
     * Indices (0-based) of the pages OCR should run on.
     *
     * @param pageTextLengths per-page embedded text-layer lengths
     * @param pageHasImages   per-page raster-image presence, aligned with
     *                        [pageTextLengths]; pass true when unknown
     * @param thinPageChars   text-layer length below which a page counts as thin
     */
    fun selectOcrPages(
        pageTextLengths: List<Int>,
        pageHasImages: List<Boolean>,
        thinPageChars: Int
    ): List<Int> =
        pageTextLengths.mapIndexedNotNull { idx, len ->
            val hasImages = pageHasImages.getOrElse(idx) { true }
            if (len < thinPageChars && hasImages) idx else null
        }
}

package com.verumomnis.forensic.engine.llm

import com.verumomnis.forensic.core.Constitution

/** The three on-device models, tying [Constitution]'s hard-coded URLs/hashes to a downloadable spec. */
object ModelCatalog {
    val GEMMA_3 = ModelDownloadManager.ModelSpec(
        id = "gemma3",
        displayName = "Gemma 3",
        url = Constitution.MODEL_GEMMA3_URL,
        sha256 = Constitution.MODEL_GEMMA3_SHA256,
        sizeBytes = Constitution.MODEL_GEMMA3_SIZE_BYTES
    )

    /**
     * Gemma 3 1B — the report writer used where the 4B will not fit.
     *
     * Same id as [GEMMA_3] deliberately: both land at `models/gemma3.gguf` and
     * fill the REPORT_WRITER role, so the rest of the pipeline neither knows nor
     * cares which one is installed. Only one may be present at a time, which is
     * enforced by that shared path.
     */
    val GEMMA_3_1B = ModelDownloadManager.ModelSpec(
        id = "gemma3",
        displayName = "Gemma 3",
        url = Constitution.MODEL_GEMMA3_1B_URL,
        sha256 = Constitution.MODEL_GEMMA3_1B_SHA256,
        sizeBytes = Constitution.MODEL_GEMMA3_1B_SIZE_BYTES
    )

    val PHI_3 = ModelDownloadManager.ModelSpec(
        id = "phi3",
        displayName = "Phi-3",
        url = Constitution.MODEL_PHI3_URL,
        sha256 = Constitution.MODEL_PHI3_SHA256,
        sizeBytes = Constitution.MODEL_PHI3_SIZE_BYTES
    )

    val GEMMA_4 = ModelDownloadManager.ModelSpec(
        id = "gemma4",
        displayName = "Gemma 4",
        url = Constitution.MODEL_GEMMA4_URL,
        sha256 = Constitution.MODEL_GEMMA4_SHA256,
        sizeBytes = Constitution.MODEL_GEMMA4_SIZE_BYTES
    )

    // ── 2026-08 refresh specs (OFFLINE_MODEL_RESEARCH_2026-08.md) ──
    //
    // Each shares its slot's on-disk id, exactly like the GEMMA_3/GEMMA_3_1B
    // pair: the pipeline addresses slots, not model generations. They activate
    // through [preferPinned] only once Constitution carries a real hash for
    // them; until then the legacy verified spec keeps serving the slot.

    /** Gemma 4 E4B — next-gen report writer for the "Gemma 3" slot. */
    val GEMMA_4_E4B = ModelDownloadManager.ModelSpec(
        id = "gemma3",
        displayName = "Gemma 4 E4B",
        url = Constitution.MODEL_GEMMA4_E4B_URL,
        sha256 = Constitution.MODEL_GEMMA4_E4B_SHA256,
        sizeBytes = Constitution.MODEL_GEMMA4_E4B_SIZE_BYTES
    )

    /** Gemma 4 E2B — next-gen small-device report writer for the "Gemma 3" slot. */
    val GEMMA_4_E2B = ModelDownloadManager.ModelSpec(
        id = "gemma3",
        displayName = "Gemma 4 E2B",
        url = Constitution.MODEL_GEMMA4_E2B_URL,
        sha256 = Constitution.MODEL_GEMMA4_E2B_SHA256,
        sizeBytes = Constitution.MODEL_GEMMA4_E2B_SIZE_BYTES
    )

    /** Qwen3.5-4B — next-gen communicator/antithesis for the "Phi-3" slot. */
    val QWEN35_4B = ModelDownloadManager.ModelSpec(
        id = "phi3",
        displayName = "Qwen3.5-4B",
        url = Constitution.MODEL_QWEN35_4B_URL,
        sha256 = Constitution.MODEL_QWEN35_4B_SHA256,
        sizeBytes = Constitution.MODEL_QWEN35_4B_SIZE_BYTES
    )

    /**
     * A spec is pinned when its hash is a real SHA-256 computed from the
     * artifact — 64 hex characters, not [Constitution.PENDING_SHA256]. Only
     * pinned specs may ever be offered for download.
     */
    fun isPinned(spec: ModelDownloadManager.ModelSpec): Boolean =
        spec.sha256.length == 64 && spec.sha256.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }

    private fun preferPinned(
        nextGen: ModelDownloadManager.ModelSpec,
        legacy: ModelDownloadManager.ModelSpec
    ): ModelDownloadManager.ModelSpec = if (isPinned(nextGen)) nextGen else legacy

    fun forName(name: String): ModelDownloadManager.ModelSpec? = when (name) {
        "Gemma 3" -> preferPinned(GEMMA_4_E4B, GEMMA_3)
        "Phi-3" -> preferPinned(QWEN35_4B, PHI_3)
        "Gemma 4" -> GEMMA_4
        else -> null
    }

    /**
     * The report writer sized for this device.
     *
     * Below [SMALL_WRITER_RAM_GB] the 4B-class writer is not a slow option, it
     * is an absent one: a ~2.5 GB file cannot be held by a handset with under
     * two gigabytes free, so choosing it there means no narrative at all. The
     * small variant fits, and a narrative writer that runs beats a better one
     * that does not.
     */
    fun forName(name: String, deviceRamGb: Int): ModelDownloadManager.ModelSpec? =
        if (name == "Gemma 3" && deviceRamGb < SMALL_WRITER_RAM_GB) {
            preferPinned(GEMMA_4_E2B, GEMMA_3_1B)
        } else {
            forName(name)
        }

    /**
     * Every spec that could occupy this model's slot on disk.
     *
     * Slot-sharing specs land at one filename, so "is a model installed?"
     * cannot be answered by one spec alone — a device holding the 1B would look
     * empty when asked about the 4B, and the app would re-download 2.49 GB over
     * a working model. Unpinned specs are harmless here: they can never pass
     * [ModelDownloadManager.isVerified] with a PENDING hash and zero size.
     */
    fun variantsForName(name: String): List<ModelDownloadManager.ModelSpec> = when (name) {
        "Gemma 3" -> listOf(GEMMA_3, GEMMA_3_1B, GEMMA_4_E4B, GEMMA_4_E2B)
        "Phi-3" -> listOf(PHI_3, QWEN35_4B)
        else -> listOfNotNull(forName(name))
    }

    /** Below this much RAM the report writer drops to the small variant. */
    const val SMALL_WRITER_RAM_GB = 6
}

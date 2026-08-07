package com.verumomnis.forensic

import com.verumomnis.forensic.core.Constitution
import com.verumomnis.forensic.engine.llm.ModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The 2026-08 model refresh (OFFLINE_MODEL_RESEARCH_2026-08.md) must fail safe:
 * an unpinned next-gen spec — PENDING hash, zero size — may never be offered
 * for download. The legacy verified spec keeps serving its slot until
 * tools/pin-models.sh output is pasted into Constitution.
 */
class ModelCatalogTest {

    @Test
    fun legacySpecsArePinned() {
        for (spec in listOf(ModelCatalog.GEMMA_3, ModelCatalog.GEMMA_3_1B, ModelCatalog.PHI_3, ModelCatalog.GEMMA_4)) {
            assertTrue("${spec.displayName} must carry a real SHA-256", ModelCatalog.isPinned(spec))
        }
    }

    @Test
    fun pendingSentinelIsNotPinned() {
        assertFalse(ModelCatalog.isPinned(ModelCatalog.GEMMA_4_E4B.copy(sha256 = Constitution.PENDING_SHA256)))
        // A hash-length string that is not hex must not count as pinned either.
        assertFalse(ModelCatalog.isPinned(ModelCatalog.GEMMA_4_E4B.copy(sha256 = "z".repeat(64))))
    }

    @Test
    fun slotsServeOnlyPinnedSpecs() {
        // Whichever generation is selected for a slot, it must be downloadable —
        // i.e. pinned. While the refresh hashes are PENDING this resolves to the
        // legacy specs; once pinned, the same assertions hold for the new ones.
        for (name in listOf("Gemma 3", "Phi-3", "Gemma 4")) {
            val spec = ModelCatalog.forName(name)!!
            assertTrue("$name slot served unpinned ${spec.displayName}", ModelCatalog.isPinned(spec))
        }
        val smallWriter = ModelCatalog.forName("Gemma 3", deviceRamGb = 4)!!
        assertTrue(ModelCatalog.isPinned(smallWriter))
    }

    @Test
    fun refreshSpecsShareTheirSlotsOnDiskId() {
        // Slot addressing (models/<id>.gguf) is what lets a spec swap happen
        // without the rest of the pipeline noticing. Breaking the shared id
        // would strand installed models and trigger multi-GB re-downloads.
        assertEquals(ModelCatalog.GEMMA_3.id, ModelCatalog.GEMMA_4_E4B.id)
        assertEquals(ModelCatalog.GEMMA_3.id, ModelCatalog.GEMMA_4_E2B.id)
        assertEquals(ModelCatalog.PHI_3.id, ModelCatalog.QWEN35_4B.id)
    }

    @Test
    fun variantsCoverEverySpecThatCouldOccupyTheSlot() {
        val writerVariants = ModelCatalog.variantsForName("Gemma 3")
        assertTrue(writerVariants.containsAll(listOf(
            ModelCatalog.GEMMA_3, ModelCatalog.GEMMA_3_1B,
            ModelCatalog.GEMMA_4_E4B, ModelCatalog.GEMMA_4_E2B
        )))
        val commVariants = ModelCatalog.variantsForName("Phi-3")
        assertTrue(commVariants.containsAll(listOf(ModelCatalog.PHI_3, ModelCatalog.QWEN35_4B)))
    }

    @Test
    fun smallRamWriterStaysOnSmallVariant() {
        val small = ModelCatalog.forName("Gemma 3", deviceRamGb = 4)!!
        val large = ModelCatalog.forName("Gemma 3", deviceRamGb = 8)!!
        // While unpinned: 1B vs 4B. After pinning: E2B vs E4B. Either way the
        // small-device selection must never be the multi-GB writer.
        assertTrue(small.sizeBytes < Constitution.MODEL_GEMMA3_SIZE_BYTES)
        assertTrue(ModelCatalog.isPinned(large))
    }
}

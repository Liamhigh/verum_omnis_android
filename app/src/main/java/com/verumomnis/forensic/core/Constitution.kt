package com.verumomnis.forensic.core

/**
 * Verum Omnis Constitution — hard-coded, immutable governance constants.
 *
 * The version itself lives in [VERSION]; do not repeat it in prose here or it
 * drifts from the constant the seals actually record.
 *
 * Per the specification these are COMPILE-TIME CONSTANTS: they are not stored in
 * config files or databases. Changing them requires recompiling from source, and
 * doing so invalidates every existing seal (hash mismatch). They therefore act as
 * the machine-readable ruleset embedded into every cryptographic seal.
 */
object Constitution {
    const val VERSION = "8.0"
    const val FINAL = true

    const val PROFIT_TO_FOUNDATION = 99      // 99% to Verum Foundation
    const val ETHICS_HALT_THRESHOLD = 0.003  // 0.3% bias = halt everything
    const val DEAD_MAN_SWITCH_HOURS = 72     // 72h inactivity = INTERPOL release
    const val BRAIN_COUNT = 9                // Exactly 9 brains
    const val GUARDIAN_COUNCIL_SIZE = 7      // 7 oversight members
    const val COMMISSION_PERCENT = 20        // 20% of recovered fraud
    const val CITIZEN_ACCESS_FREE = true     // Always free for citizens

    // AI Behaviour — Constitutional Prime Directives
    const val TRUTH_OVER_PROBABILITY = true          // Ordinal confidence only
    const val EVIDENCE_BEFORE_NARRATIVE = true       // No anchor = no sentence
    const val MANDATORY_CONTRADICTION_DISCLOSURE = true
    const val DETERMINISM_REQUIRED = true            // No randomness, no Date.now()
    const val CHAIN_OF_CUSTODY_IS_LAW = true
    const val FAILURE_MODE_DISCLOSURE = true
    const val ANTI_COERCION = true
    const val NON_OWNERSHIP = true
    const val ANTI_WAR_DOCTRINE = true               // Article X — supreme hierarchy

    /**
     * Prime Directive 16 (introduced in v6.1) — findings are stated as fact;
     * verdicts belong to the court.
     *
     * A verified finding is a measurement, not an opinion: where Triple
     * Verification confirms a contradiction, tampering or an impossibility, the
     * output says so plainly and is never softened to "might", "possibly" or
     * "appears to". A finding graded INSUFFICIENT is not asserted at all. The one
     * determination reserved to the court is the criminal or civil verdict on a
     * named person, which turns on intent that documents cannot measure.
     */
    const val FINDINGS_STATED_AS_FACT = true

    // Prime Directives 17–20 (new in v8.0, CONSTITUTION.md §1).
    /** PD17 — all AI systems are bound; no opt-out; non-compliance is a Constitutional Breach. */
    const val MANDATORY_AI_GOVERNANCE = true
    /** PD18 — 50/50 Human/AI partnership; neither side can change or override the Constitution. */
    const val PARTNERSHIP_50_50 = true
    /** PD19 — all forensic extraction follows the sealed template structure; no improvisation. */
    const val TEMPLATE_GOVERNED_EXTRACTION = true
    /** PD20 — the Breathalyzer Standard: reports state findings as fact; no probability, no hedging. */
    const val BREATHALYZER_STANDARD = true

    // Sealing
    const val HASH_ALGORITHM = "SHA-512"
    const val PDF_STANDARD = "PDF/A-3B"
    const val ENCRYPTION = "AES-256-GCM"
    const val BLOCKCHAIN_NETWORK = "bitcoin"

    // Signed rule updates (verum-rules worker, rule-format.md v1).
    // The public key is public BY DESIGN (SubjectPublicKeyInfo DER, base64) —
    // it can only verify signatures, never create them. publicKeyId: vo-master-1.
    const val RULE_MANIFEST_URL = "https://verumglobal.foundation/api/v1/rules/manifest"
    const val RULES_PUBLIC_KEY_DER_B64 = "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEA9FQPTWCsFh1qMs/mrOOgZvdjCh8APmlsJlallCm3CmWgMoFAyRHRAauvXWFoBoiaUQGGx7OGtZ6eBCpBlOGLxSnVk0T2hBgd6kxZwj1vHEITw9KmXMjy5qmUY1hd3BO3y4aAfrPKu+6ENSJo7Ax77fvBnHPG1oL8m3724oqU913HYI7Miob+CdL0Oi36oCBKhlw5sCYH+evMPU1PmOqTrmz8zUkDk4osqX8INTIchmk2j3BguMw8sjmKRnrB//t6LPYme4motggMPVMNR3hLJHX+ehCYDUtJLshZq1MPLjTT7aK36gCIPg2ja6BxWfYdx7ZzSFVcL+gapy4pA7VnDrhQ7jb10ojGnofssEbQEi7k9FpswMFegmGNmKEH5TQcKlI4VJvQcZddbhZXYwpfgsL/raEFMChEuzR3A49oIXgBBmi9AdQtdEHpfb2i9/PimxsilhDxa8Pi+8cEQUMbHcPeodfX/IWf+wotnc3VKGoffVL/8+hSU/voPhxfXyOcnbRYkFGeOZhcrE/u4Nh6Vkq6y1+cpVUtrIzOnaeNbNF248ZS7f65IZci8MTeo4nAqkWGmXcZHrZLT7YIvHSyAryYzBNoofm2uTuiTxp8Oiwa2yfU2UMQfg0eGZa0LBHCLbG72pxiVd2TGvdHh3QguO1/zM5NNRtoUnqHfuLBOJECAwEAAQ=="

    // On-device LLM downloads (ON_DEVICE_LLM_ARCHITECTURE.md section 6): URL + SHA-256 are
    // hard-coded so ModelDownloadManager can verify integrity before ever loading a model.
    // GGUF quantized re-uploads (bartowski) rather than Google/Microsoft's raw checkpoints,
    // since llama.cpp inference requires GGUF format. Verified reachable + hashed 2026-07-26.
    const val MODEL_GEMMA3_URL =
        "https://huggingface.co/bartowski/google_gemma-3-4b-it-GGUF/resolve/main/google_gemma-3-4b-it-Q4_K_M.gguf"
    const val MODEL_GEMMA3_SHA256 = "4996030242583a40aa151ff93f49ed787ac8c25e4120c3ae4588b2e2a7d1ae94"
    const val MODEL_GEMMA3_SIZE_BYTES = 2489758112L

    // Gemma 3 1B — the report writer for devices that cannot hold the 4B.
    //
    // At 0.81 GB against the 4B's 2.49 GB this runs on a mid-range handset with
    // room to spare, where the 4B is marginal: on a 5.2 GB device with ~2 GB free
    // the larger file exceeds available memory outright, and llama.cpp only makes
    // it viable at all by memory-mapping.
    //
    // Narration is the job here — the deterministic engine has already extracted
    // the contradictions, so the model turns structured findings into prose
    // rather than reasoning over raw evidence. A 1B is credible at that. It is
    // materially weaker at spotting contradictions the engine missed, which is
    // why G3 candidates stay labelled PENDING VERIFICATION and are never
    // promoted without human sign-off.
    //
    // Downloaded and hashed 2026-08-05.
    const val MODEL_GEMMA3_1B_URL =
        "https://huggingface.co/bartowski/google_gemma-3-1b-it-GGUF/resolve/main/google_gemma-3-1b-it-Q4_K_M.gguf"
    const val MODEL_GEMMA3_1B_SHA256 = "12bf0fff8815d5f73a3c9b586bd8fee8e7b248c935de70dec367679873d0f29d"
    const val MODEL_GEMMA3_1B_SIZE_BYTES = 806058496L

    const val MODEL_PHI3_URL =
        "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf"
    const val MODEL_PHI3_SHA256 = "8a83c7fb9049a9b2e92266fa7ad04933bb53aa1e85136b7b30f1b8000ff2edef"
    const val MODEL_PHI3_SIZE_BYTES = 2393231072L

    const val MODEL_GEMMA4_URL =
        "https://huggingface.co/bartowski/gemma-4-12B-it-GGUF/resolve/main/gemma-4-12B-it-Q4_K_M.gguf"
    const val MODEL_GEMMA4_SHA256 = "d333b368be6cd655563fce18aede26027e208fdb13816d35eb06983ce054044b"
    const val MODEL_GEMMA4_SIZE_BYTES = 7662531872L

    // ── 2026-08 model refresh (OFFLINE_MODEL_RESEARCH_2026-08.md) ──
    //
    // Target set: Gemma 4 E4B/E2B take over the report-writer slot, Qwen3.5-4B
    // takes over the communicator slot — chosen so the triple-verification
    // thesis (Google) and antithesis (Alibaba) legs share no lab, training
    // pipeline, or architecture. The flagship Gemma 4 12B pin above is already
    // current and stays.
    //
    // A hash here is either computed from the downloaded artifact or PENDING —
    // never copied from a webpage, never guessed. Run tools/pin-models.sh on an
    // unrestricted network, verify the printed values, and paste them in. While
    // a spec is PENDING, ModelCatalog keeps serving the legacy verified spec
    // above, so the app never downloads an artifact it cannot verify.
    const val PENDING_SHA256 = "PENDING"
    const val PENDING_SIZE_BYTES = 0L

    const val MODEL_GEMMA4_E4B_URL =
        "https://huggingface.co/unsloth/gemma-4-E4B-it-GGUF/resolve/main/gemma-4-E4B-it-Q4_K_M.gguf"
    const val MODEL_GEMMA4_E4B_SHA256 = PENDING_SHA256
    const val MODEL_GEMMA4_E4B_SIZE_BYTES = PENDING_SIZE_BYTES

    const val MODEL_GEMMA4_E2B_URL =
        "https://huggingface.co/unsloth/gemma-4-E2B-it-GGUF/resolve/main/gemma-4-E2B-it-Q4_K_M.gguf"
    const val MODEL_GEMMA4_E2B_SHA256 = PENDING_SHA256
    const val MODEL_GEMMA4_E2B_SIZE_BYTES = PENDING_SIZE_BYTES

    const val MODEL_QWEN35_4B_URL =
        "https://huggingface.co/unsloth/Qwen3.5-4B-GGUF/resolve/main/Qwen3.5-4B-Q4_K_M.gguf"
    const val MODEL_QWEN35_4B_SHA256 = PENDING_SHA256
    const val MODEL_QWEN35_4B_SIZE_BYTES = PENDING_SIZE_BYTES

    const val NINE_BRAIN_VERSION = "v1.0"
    const val SEALING_PROTOCOL = "verum-omnis-seal v1.0"
    const val TAGLINE = "AI Forensics for Truth"

    /** Machine-readable ruleset embedded into every seal. */
    fun rulesetFingerprint(): String = buildString {
        append("VO-CONSTITUTION|")
        append("v=$VERSION|final=$FINAL|")
        append("profit=$PROFIT_TO_FOUNDATION|ethicsHalt=$ETHICS_HALT_THRESHOLD|")
        append("deadman=$DEAD_MAN_SWITCH_HOURS|brains=$BRAIN_COUNT|")
        append("council=$GUARDIAN_COUNCIL_SIZE|commission=$COMMISSION_PERCENT|")
        append("citizenFree=$CITIZEN_ACCESS_FREE|")
        append("truthOverProb=$TRUTH_OVER_PROBABILITY|evidenceFirst=$EVIDENCE_BEFORE_NARRATIVE|")
        append("contradictionDisclosure=$MANDATORY_CONTRADICTION_DISCLOSURE|determinism=$DETERMINISM_REQUIRED|")
        append("chainOfCustody=$CHAIN_OF_CUSTODY_IS_LAW|antiCoercion=$ANTI_COERCION|antiWar=$ANTI_WAR_DOCTRINE|")
        append("findingsAsFact=$FINDINGS_STATED_AS_FACT|")
        append("aiGovernance=$MANDATORY_AI_GOVERNANCE|partnership=$PARTNERSHIP_50_50|")
        append("templateExtraction=$TEMPLATE_GOVERNED_EXTRACTION|breathalyzer=$BREATHALYZER_STANDARD|")
        append("hash=$HASH_ALGORITHM|pdf=$PDF_STANDARD|enc=$ENCRYPTION|chain=$BLOCKCHAIN_NETWORK")
    }
}

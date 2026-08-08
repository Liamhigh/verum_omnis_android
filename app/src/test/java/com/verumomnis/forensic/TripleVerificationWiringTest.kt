package com.verumomnis.forensic

import com.verumomnis.forensic.engine.contradiction.DetectedFact
import com.verumomnis.forensic.engine.contradiction.EngineConfidence
import com.verumomnis.forensic.engine.contradiction.EngineContradiction
import com.verumomnis.forensic.engine.contradiction.EngineContradictionType
import com.verumomnis.forensic.engine.contradiction.EngineSeverity
import com.verumomnis.forensic.engine.contradiction.LogicalPattern
import com.verumomnis.forensic.engine.contradiction.TripleVerifier
import com.verumomnis.forensic.llm.Gemma3Runtime
import com.verumomnis.forensic.llm.UnavailableAntithesisRuntime
import com.verumomnis.forensic.llm.UnavailableGemma3Runtime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Real triple verification (Prime Directive 13): the model legs actually
 * examine findings and may dissent; nothing concurs by decree. Fails closed —
 * unavailable legs are NOT RUN, garbage responses are ABSTAINS, and only an
 * explicit CONCUR from every leg meets quorum.
 */
class TripleVerificationWiringTest {

    private class FakeRuntime(
        override val modelName: String,
        private val respond: (String) -> String?
    ) : Gemma3Runtime {
        val prompts = mutableListOf<String>()
        override fun isAvailable(): Boolean = true
        override fun generate(prompt: String, maxTokens: Int): String? {
            prompts += prompt
            return respond(prompt)
        }
    }

    private fun finding(id: String, severity: EngineSeverity = EngineSeverity.VERY_HIGH) =
        EngineContradiction(
            contradictionId = id,
            type = EngineContradictionType.STATEMENT_VS_STATEMENT,
            severity = severity,
            confidence = EngineConfidence.DETERMINISTIC,
            detectedFact = DetectedFact(
                factText = "conflicting statements",
                sourceDocument = "affidavit.pdf",
                sourcePage = 3,
                sourceLine = 12,
                sha512Hash = "a".repeat(128),
                extractionMethod = "deterministic",
                confidence = EngineConfidence.DETERMINISTIC
            ),
            logicalPattern = LogicalPattern(
                patternType = "DIRECT_NEGATION",
                patternDescription = "A asserts X; A later denies X",
                supportingFacts = listOf("p3 l12", "p9 l4"),
                contradictionScore = 1.0
            ),
            propositionAText = "The contract was signed on 1 March.",
            propositionBText = "No contract was ever signed.",
            propositionAActor = "Alex",
            propositionBActor = "Alex",
            conflictDescription = "Signature status asserted and denied by the same actor"
        )

    @Test
    fun bothLegsConcurMeetsQuorum() {
        val outcome = TripleVerifier.verifyTripleWithModels(
            listOf(finding("C-1"), finding("C-2")),
            thesis = FakeRuntime("writer") { "VERDICT: CONCUR" },
            antithesis = FakeRuntime("comm") { "VERDICT: CONCUR" }
        )
        assertEquals("CONCURS", outcome.verification.gemma3Status)
        assertEquals("CONCURS", outcome.verification.phi3Status)
        assertEquals("CONCURS", outcome.verification.nineBrainStatus)
        assertTrue(outcome.verification.quorumMet)
        for (c in outcome.contradictions) {
            assertEquals("CONCURS", c.verificationStatus["gemma3"])
            assertEquals("CONCURS", c.verificationStatus["phi3"])
            assertEquals("CONCURS", c.verificationStatus["nineBrain"])
        }
        // Provenance: which model held each slot is on the record.
        assertTrue(outcome.verification.discrepancies.any { it == "Thesis model: writer" })
        assertTrue(outcome.verification.discrepancies.any { it == "Antithesis model: comm" })
    }

    @Test
    fun antithesisDissentBlocksQuorumAndIsRecorded() {
        val outcome = TripleVerifier.verifyTripleWithModels(
            listOf(finding("C-1")),
            thesis = FakeRuntime("writer") { "VERDICT: CONCUR" },
            antithesis = FakeRuntime("comm") { "VERDICT: DISSENT | both statements can refer to different contracts" }
        )
        assertFalse(outcome.verification.quorumMet)
        assertEquals("DISSENTS (1/1)", outcome.verification.phi3Status)
        assertEquals("DISSENTS", outcome.contradictions.single().verificationStatus["phi3"])
        // The finding itself is never deleted — it is a deterministic measurement.
        assertEquals(1, outcome.contradictions.size)
        assertTrue(outcome.verification.discrepancies.any {
            it.contains("C-1") && it.contains("different contracts") && it.contains("human review")
        })
    }

    @Test
    fun unavailableLegsReportNotRunNeverConcur() {
        val outcome = TripleVerifier.verifyTripleWithModels(
            listOf(finding("C-1")),
            thesis = UnavailableGemma3Runtime,
            antithesis = UnavailableAntithesisRuntime
        )
        assertEquals("NOT RUN", outcome.verification.gemma3Status)
        assertEquals("NOT RUN", outcome.verification.phi3Status)
        assertFalse(outcome.verification.quorumMet)
        assertEquals("NOT RUN", outcome.contradictions.single().verificationStatus["gemma3"])
        // Deterministic entry point must match exactly.
        val deterministic = TripleVerifier.verifyTriple(listOf(finding("C-1")))
        assertEquals("NOT RUN", deterministic.gemma3Status)
        assertFalse(deterministic.quorumMet)
    }

    @Test
    fun garbageResponseAbstainsRatherThanConcurs() {
        val outcome = TripleVerifier.verifyTripleWithModels(
            listOf(finding("C-1")),
            thesis = FakeRuntime("writer") { "I think this is probably a contradiction, yes." },
            antithesis = FakeRuntime("comm") { null }
        )
        assertEquals("ABSTAINS (1/1)", outcome.verification.gemma3Status)
        assertEquals("ABSTAINS (1/1)", outcome.verification.phi3Status)
        assertFalse(outcome.verification.quorumMet)
    }

    @Test
    fun sameModelInBothSlotsDisqualifiesTheAntithesis() {
        val same = FakeRuntime("writer") { "VERDICT: CONCUR" }
        val outcome = TripleVerifier.verifyTripleWithModels(
            listOf(finding("C-1")),
            thesis = same,
            antithesis = FakeRuntime("writer") { "VERDICT: CONCUR" }
        )
        assertEquals("CONCURS", outcome.verification.gemma3Status)
        assertEquals("NOT RUN", outcome.verification.phi3Status)
        assertFalse("a model must not verify its own thesis", outcome.verification.quorumMet)
        assertTrue(outcome.verification.discrepancies.any { it.contains("independent of the thesis") })
    }

    @Test
    fun antithesisPromptIsAdversarialAndQuotesTheEvidence() {
        val antithesis = FakeRuntime("comm") { "VERDICT: CONCUR" }
        TripleVerifier.verifyTripleWithModels(
            listOf(finding("C-1")),
            thesis = FakeRuntime("writer") { "VERDICT: CONCUR" },
            antithesis = antithesis
        )
        val prompt = antithesis.prompts.single()
        assertTrue(prompt.contains("REFUTE"))
        assertTrue(prompt.contains("The contract was signed on 1 March."))
        assertTrue(prompt.contains("No contract was ever signed."))
    }

    @Test
    fun verificationCapIsDisclosedNeverSilent() {
        val many = (1..TripleVerifier.MAX_VERIFIED_FINDINGS + 5).map { finding("C-$it") }
        val outcome = TripleVerifier.verifyTripleWithModels(
            many,
            thesis = FakeRuntime("writer") { "VERDICT: CONCUR" },
            antithesis = FakeRuntime("comm") { "VERDICT: CONCUR" }
        )
        assertTrue(outcome.verification.discrepancies.any { it.contains("first ${TripleVerifier.MAX_VERIFIED_FINDINGS}") })
        assertEquals("NOT RUN", outcome.contradictions.last().verificationStatus["gemma3"])
    }

    @Test
    fun verdictParsingIsStrictAndDeterministic() {
        assertEquals("CONCURS", TripleVerifier.parseVerdict("VERDICT: CONCUR").status)
        assertEquals("CONCURS", TripleVerifier.parseVerdict("noise\n verdict: concur \nmore").status)
        val dissent = TripleVerifier.parseVerdict("VERDICT: DISSENT | dates are compatible")
        assertEquals("DISSENTS", dissent.status)
        assertEquals("dates are compatible", dissent.reason)
        assertEquals("DISSENTS", TripleVerifier.parseVerdict("VERDICT: DISSENT").status)
        assertEquals("ABSTAINS", TripleVerifier.parseVerdict(null).status)
        assertEquals("ABSTAINS", TripleVerifier.parseVerdict("VERDICT: MAYBE").status)
        assertEquals("ABSTAINS", TripleVerifier.parseVerdict("it depends").status)
    }
}

package com.verumomnis.forensic.engine.contradiction

import com.verumomnis.forensic.core.Constitution
import com.verumomnis.forensic.llm.Gemma3Runtime
import com.verumomnis.forensic.llm.UnavailableAntithesisRuntime
import com.verumomnis.forensic.llm.UnavailableGemma3Runtime

/**
 * Triple Verifier & Report Generator.
 * Triple verification (Prime Directive 13): Thesis (report-writer model) +
 * Antithesis (independent communicator model) + Synthesis (deterministic
 * Nine-Brain engine). A leg that did not actually run reports NOT RUN — a
 * sealed report never asserts an AI verification that never happened.
 */
object TripleVerifier {

    /**
     * Findings each model leg verifies in one pass. Bounds prompt work on
     * device; anything past the cap is reported in [EngineTripleVerification]
     * discrepancies — a bounded pass is disclosed, never silent.
     */
    const val MAX_VERIFIED_FINDINGS = 20

    /** Longest evidence quote packed into one verification prompt. */
    private const val MAX_QUOTE_CHARS = 500

    /** A verdict is one short line; anything longer is prose, not a verdict. */
    private const val VERDICT_MAX_TOKENS = 96

    data class TripleVerificationOutcome(
        val verification: EngineTripleVerification,
        /** Input contradictions with per-finding [EngineContradiction.verificationStatus] recorded. */
        val contradictions: List<EngineContradiction>
    )

    /**
     * Deterministic-only entry point: no model legs. Both AI legs report
     * NOT RUN and quorum is never met — the deterministic findings stand as
     * measurements, and the report says exactly which verifiers examined them.
     */
    fun verifyTriple(contradictions: List<EngineContradiction>): EngineTripleVerification =
        verifyTripleWithModels(
            contradictions,
            thesis = UnavailableGemma3Runtime,
            antithesis = UnavailableAntithesisRuntime
        ).verification

    /**
     * Full triple verification. Each available model leg re-examines every
     * finding against its quoted evidence and returns a per-finding verdict:
     *
     *  - the THESIS leg independently verifies the finding;
     *  - the ANTITHESIS leg is adversarial — it is instructed to REFUTE the
     *    finding, and concurs only when it cannot;
     *  - the SYNTHESIS leg is the deterministic Nine-Brain engine that emitted
     *    the finding.
     *
     * Verdict handling fails closed: an unavailable leg is NOT RUN, an
     * unparseable response is ABSTAINS, and only an explicit CONCUR counts
     * toward quorum. Model dissent never deletes a finding — the finding is a
     * deterministic measurement — it is recorded as a discrepancy and blocks
     * quorum, which flags the finding set for human review.
     *
     * The antithesis leg only counts when it is a genuinely independent model:
     * a model must not verify its own thesis (Prime Directive 13 — three
     * INDEPENDENT verifiers).
     */
    fun verifyTripleWithModels(
        contradictions: List<EngineContradiction>,
        thesis: Gemma3Runtime,
        antithesis: Gemma3Runtime
    ): TripleVerificationOutcome {
        val hasVeryHigh = contradictions.any { it.severity == EngineSeverity.VERY_HIGH }
        val hasHigh = contradictions.any { it.severity == EngineSeverity.HIGH }
        val nineBrainStatus = if (hasVeryHigh || hasHigh) "CONCURS" else "PENDING"

        val discrepancies = mutableListOf<String>()
        when {
            hasVeryHigh -> Unit
            hasHigh -> discrepancies += "No VERY_HIGH findings — confidence capped at HIGH pending review"
            else -> discrepancies += "Only MODERATE/LOW findings — human review required"
        }

        val toVerify = contradictions.take(MAX_VERIFIED_FINDINGS)
        if (contradictions.size > MAX_VERIFIED_FINDINGS) {
            discrepancies += "AI legs verified the first $MAX_VERIFIED_FINDINGS of " +
                "${contradictions.size} findings; the remainder are NOT RUN"
        }

        val independentAntithesis =
            !(thesis.isAvailable() && antithesis.isAvailable() && thesis.modelName == antithesis.modelName)
        val thesisLeg = runLeg(thesis, toVerify, refute = false)
        val antithesisLeg = if (independentAntithesis) {
            runLeg(antithesis, toVerify, refute = true)
        } else {
            discrepancies += "Antithesis leg NOT RUN — requires a model independent of the thesis " +
                "(both slots held ${thesis.modelName})"
            Leg("NOT RUN", emptyMap(), emptyList())
        }

        for (leg in listOf("Thesis" to thesisLeg, "Antithesis" to antithesisLeg)) {
            if (leg.second.status == "NOT RUN" && leg.first == "Thesis") {
                discrepancies += "Thesis leg NOT RUN — no on-device model loaded; " +
                    "deterministic findings stand as measurements pending AI verification"
            }
            discrepancies += leg.second.dissents.map { "${leg.first} dissent — $it; human review required" }
        }
        if (thesisLeg.ranModel != null) discrepancies += "Thesis model: ${thesisLeg.ranModel}"
        if (antithesisLeg.ranModel != null) discrepancies += "Antithesis model: ${antithesisLeg.ranModel}"

        val verified = contradictions.map { c ->
            c.copy(
                verificationStatus = c.verificationStatus + mapOf(
                    "gemma3" to (thesisLeg.perFinding[c.contradictionId] ?: "NOT RUN"),
                    "phi3" to (antithesisLeg.perFinding[c.contradictionId] ?: "NOT RUN"),
                    "nineBrain" to "CONCURS"
                )
            )
        }

        return TripleVerificationOutcome(
            verification = EngineTripleVerification(
                gemma3Status = thesisLeg.status,
                phi3Status = antithesisLeg.status,
                nineBrainStatus = nineBrainStatus,
                quorumMet = nineBrainStatus == "CONCURS" &&
                    thesisLeg.status == "CONCURS" && antithesisLeg.status == "CONCURS",
                discrepancies = discrepancies
            ),
            contradictions = verified
        )
    }

    private class Leg(
        val status: String,
        /** contradictionId → CONCURS / DISSENTS / ABSTAINS. */
        val perFinding: Map<String, String>,
        val dissents: List<String>,
        val ranModel: String? = null
    )

    private fun runLeg(runtime: Gemma3Runtime, findings: List<EngineContradiction>, refute: Boolean): Leg {
        if (!runtime.isAvailable() || findings.isEmpty()) return Leg("NOT RUN", emptyMap(), emptyList())
        val perFinding = LinkedHashMap<String, String>()
        val dissents = mutableListOf<String>()
        var abstains = 0
        for (c in findings) {
            val verdict = parseVerdict(runtime.generate(buildVerdictPrompt(c, refute), VERDICT_MAX_TOKENS))
            perFinding[c.contradictionId] = verdict.status
            when (verdict.status) {
                "DISSENTS" -> dissents += "${c.contradictionId}: ${verdict.reason}"
                "ABSTAINS" -> abstains++
            }
        }
        val status = when {
            dissents.isNotEmpty() -> "DISSENTS (${dissents.size}/${findings.size})"
            abstains > 0 -> "ABSTAINS ($abstains/${findings.size})"
            else -> "CONCURS"
        }
        return Leg(status, perFinding, dissents, runtime.modelName)
    }

    /**
     * One finding, one bounded prompt, one demanded verdict line. The evidence
     * quotes come from the deterministic engine's own extraction — the model
     * judges only whether the quoted statements can coexist.
     */
    internal fun buildVerdictPrompt(c: EngineContradiction, refute: Boolean): String = buildString {
        if (refute) {
            appendLine("You are the antithesis verifier for Verum Omnis triple verification.")
            appendLine("Try to REFUTE this finding: find any reading under which both statements are true together.")
        } else {
            appendLine("You are the thesis verifier for Verum Omnis triple verification.")
            appendLine("Independently verify this finding against the quoted evidence.")
        }
        appendLine("Finding ${c.contradictionId} (${c.type.name}): ${c.conflictDescription.take(MAX_QUOTE_CHARS)}")
        appendLine("Statement A [${c.propositionAActor}]: \"${c.propositionAText.take(MAX_QUOTE_CHARS)}\"")
        appendLine("Statement B [${c.propositionBActor}]: \"${c.propositionBText.take(MAX_QUOTE_CHARS)}\"")
        appendLine(
            if (refute) "If both statements can be true together, the finding fails and you must DISSENT."
            else "The finding holds only if the statements cannot both be true."
        )
        appendLine("Reply with exactly one line:")
        appendLine("VERDICT: CONCUR")
        appendLine("or")
        append("VERDICT: DISSENT | <short reason>")
    }

    internal data class Verdict(val status: String, val reason: String)

    /**
     * Fail-closed verdict parsing: only an explicit CONCUR concurs. A null
     * generation, missing verdict line, or unrecognised verdict is ABSTAINS —
     * silence or confusion is never counted as agreement.
     */
    internal fun parseVerdict(response: String?): Verdict {
        val line = response?.lineSequence()?.map { it.trim() }
            ?.firstOrNull { it.uppercase().startsWith("VERDICT:") }
            ?: return Verdict("ABSTAINS", "no parseable verdict")
        val body = line.substringAfter(":").trim()
        return when {
            body.uppercase().startsWith("CONCUR") -> Verdict("CONCURS", "")
            body.uppercase().startsWith("DISSENT") ->
                Verdict("DISSENTS", body.substringAfter("|", "").trim().ifEmpty { "no reason given" })
            else -> Verdict("ABSTAINS", "unrecognised verdict: ${body.take(60)}")
        }
    }

    /** Build actor profiles from claims and contradictions. */
    fun buildProfiles(claims: List<EngineClaim>, contradictions: List<EngineContradiction>): List<ActorProfile> {
        val data = mutableMapOf<String, MutableActorData>()
        for (c in claims) {
            val d = data.getOrPut(c.actor) { MutableActorData() }
            d.claims++
            if (c.sourceType == EngineStatementType.DENIAL) d.denials++
        }
        for (con in contradictions) {
            for (actor in listOfNotNull(con.propositionAActor.takeIf { it.isNotEmpty() }, con.propositionBActor.takeIf { it.isNotEmpty() })) {
                val d = data.getOrPut(actor) { MutableActorData() }
                d.contradictions += con.contradictionId
                d.flags += con.type.name
            }
        }
        return data.map { (name, d) ->
            ActorProfile(
                name = name,
                dishonestyScore = (d.contradictions.size * 15 + d.flags.size * 5).coerceAtMost(100),
                flags = d.flags.toList(),
                contradictions = d.contradictions,
                statementsMade = d.claims,
                statementsDenied = d.denials
            )
        }.sortedByDescending { it.dishonestyScore }
    }

    private class MutableActorData {
        var claims = 0
        var denials = 0
        val contradictions = mutableListOf<String>()
        val flags = mutableSetOf<String>()
    }

    /** Generate report in specified format. */
    fun generateReport(report: EngineForensicReport, format: String = "txt"): String = when (format) {
        "json" -> generateJson(report)
        "markdown" -> generateMarkdown(report)
        else -> generateText(report)
    }

    private fun generateText(report: EngineForensicReport): String = buildString {
        appendLine("=".repeat(70))
        appendLine("VERUM OMNIS — FORENSIC CONTRADICTION REPORT")
        appendLine("=".repeat(70))
        appendLine("Case: ${report.caseId}")
        appendLine("Corpus SHA-512: ${report.corpusHash}")
        appendLine("Contradictions Found: ${report.contradictions.size}")
        appendLine("Triple Verification: ${if (report.tripleVerification.quorumMet) "QUORUM MET" else "PENDING REVIEW"}")
        // Both read from their single source. The engine version was already
        // centralised in EngineVersion; the Constitution version was still a
        // literal here, so a verification record could name the wrong instrument.
        appendLine("Engine: ${EngineVersion.VALUE} | Constitution: v${Constitution.VERSION} Final")
        appendLine()

        if (report.contradictions.isNotEmpty()) {
            appendLine("-".repeat(70))
            appendLine("CONTRADICTIONS")
            appendLine("-".repeat(70))
            for (c in report.contradictions) {
                appendLine()
                appendLine("[${c.contradictionId}] ${c.type}")
                appendLine("Severity: ${c.severity} | Confidence: ${c.confidence}")
                appendLine("Actors: ${c.propositionAActor} vs ${c.propositionBActor}")
                appendLine("Description: ${c.conflictDescription}")
                appendLine("Pattern: ${c.logicalPattern.patternType}")
                c.legalHypothesis?.let {
                    appendLine("Legal Hypothesis: ${it.suggestedOffence}")
                    appendLine("  NOTE: This is a HYPOTHESIS requiring human legal review")
                }
            }
        }

        if (report.actorProfiles.isNotEmpty()) {
            appendLine().appendLine("-".repeat(70))
            appendLine("ACTOR PROFILES")
            appendLine("-".repeat(70))
            for (p in report.actorProfiles) {
                appendLine()
                appendLine("${p.name} (Dishonesty Score: ${p.dishonestyScore}/100)")
                appendLine("  Statements: ${p.statementsMade} made, ${p.statementsDenied} denied")
                appendLine("  Contradictions: ${p.contradictions.size}")
                appendLine("  Flags: ${p.flags.joinToString(", ").ifEmpty { "none" }}")
            }
        }

        appendLine().appendLine("-".repeat(70))
        appendLine("TRIPLE VERIFICATION")
        appendLine("-".repeat(70))
        // Slot labels, not model names: which model held each slot is recorded
        // in the discrepancies ("Thesis model: ...") by verifyTripleWithModels.
        appendLine("Thesis (writer):       ${report.tripleVerification.gemma3Status}")
        appendLine("Antithesis (comm):     ${report.tripleVerification.phi3Status}")
        appendLine("9-Brain (Synthesis):   ${report.tripleVerification.nineBrainStatus}")
        appendLine("Quorum:                ${if (report.tripleVerification.quorumMet) "MET" else "NOT MET"}")
        if (report.tripleVerification.discrepancies.isNotEmpty()) {
            appendLine("Discrepancies: ${report.tripleVerification.discrepancies.joinToString("; ")}")
        }

        appendLine().appendLine("=".repeat(70))
        appendLine("END OF REPORT — Seal: VO-CE-v531c-DIGSIM | Constitution: v${Constitution.VERSION} Final")
        appendLine("=".repeat(70))
    }

    private fun generateJson(report: EngineForensicReport): String {
        return kotlinx.serialization.json.Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }.encodeToString(EngineForensicReport.serializer(), report)
    }

    private fun generateMarkdown(report: EngineForensicReport): String = buildString {
        appendLine("# Verum Omnis Forensic Report — ${report.caseId}")
        appendLine()
        appendLine("- **Corpus SHA-512:** `${report.corpusHash}`")
        appendLine("- **Contradictions:** ${report.contradictions.size}")
        appendLine("- **Verification:** ${if (report.tripleVerification.quorumMet) "✅ Quorum Met" else "⚠️ Pending Review"}")
        appendLine("- **Engine:** ${EngineVersion.TAGGED} | Constitution: v${Constitution.VERSION} Final")
        appendLine()

        appendLine("## Contradictions")
        for (c in report.contradictions) {
            appendLine()
            appendLine("### ${c.contradictionId}: ${c.type}")
            appendLine("- **Severity:** ${c.severity}")
            appendLine("- **Confidence:** ${c.confidence}")
            appendLine("- **Actors:** ${c.propositionAActor} vs ${c.propositionBActor}")
            appendLine("- **Description:** ${c.conflictDescription}")
            c.legalHypothesis?.let {
                appendLine("- **Legal Hypothesis:** ${it.suggestedOffence} *(HYPOTHESIS — requires human review)*")
            }
        }

        appendLine().appendLine("## Actor Profiles")
        for (p in report.actorProfiles) {
            appendLine()
            appendLine("### ${p.name} — Score: ${p.dishonestyScore}/100")
            appendLine("- Statements: ${p.statementsMade} made, ${p.statementsDenied} denied")
            appendLine("- Contradictions: ${p.contradictions.size}")
            appendLine("- Flags: ${p.flags.joinToString(", ").ifEmpty { "none" }}")
        }

        appendLine().appendLine("---")
        appendLine(
            "*Generated by Verum Omnis Contradiction Engine ${EngineVersion.TAGGED} " +
                "under Constitution v${Constitution.VERSION} Final*"
        )
    }
}

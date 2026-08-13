package com.verumomnis.forensic.seal

import com.verumomnis.forensic.blockchain.BlockchainService
import com.verumomnis.forensic.blockchain.OpenTimestampsService
import com.verumomnis.forensic.model.OtsStatus
import com.verumomnis.forensic.vault.EvidenceVault

/**
 * Completes the OpenTimestamps anchor for seals that are still pending.
 *
 * VO-DSS-1.2 §3 submits a digest to the calendars, which returns a *pending*
 * proof — the Bitcoin attestation follows an hour or two later. Turning that
 * pending proof into a confirmed one requires a second call to the calendars'
 * upgrade endpoint.
 *
 * That call existed on [OpenTimestampsService] and was never made from anywhere
 * in the app. Every seal therefore stayed PENDING forever, which meant the
 * platform's central promise — anchored to Bitcoin, independently verifiable by
 * anyone — was not true of any document it had ever produced. This class is the
 * missing step.
 *
 * It is deliberately conservative: a proof is replaced on disk only when the
 * calendars return an upgraded one, and CONFIRMED is only ever reported when the
 * upgraded proof actually carries a Bitcoin attestation. A failed or offline
 * upgrade leaves the pending proof exactly as it was, so nothing is lost by
 * trying and nothing is claimed by failing.
 */
class AnchorUpgrader(
    private val vault: EvidenceVault,
    private val service: BlockchainService = OpenTimestampsService
) {

    companion object {
        /**
         * Marker stored in place of a proof when a seal was created with the
         * calendars unreachable: `UNANCHORED:<sha512hex>`. The colon keeps it
         * unmistakable for Base64 proof data. On the next [upgrade] run the
         * digest is submitted fresh — the "stored locally for later
         * submission" promise, made real.
         */
        const val UNANCHORED_PREFIX = "UNANCHORED:"
    }

    data class Outcome(
        val shortcode: String,
        val status: OtsStatus,
        val message: String,
        /** True when this run moved the seal from pending to confirmed. */
        val newlyConfirmed: Boolean
    )

    /**
     * Attempts to upgrade one stored proof.
     *
     * @return null when no proof is stored for [shortcode] — nothing to upgrade.
     */
    fun upgrade(shortcode: String): Outcome? {
        val stored = vault.loadOtsProof(shortcode) ?: return null

        // A seal created offline has no proof yet, only its queued digest —
        // submit it now instead of trying to upgrade nothing.
        if (stored.startsWith(UNANCHORED_PREFIX)) return anchorQueuedDigest(shortcode, stored)

        // Already confirmed? Don't touch the calendars again; a confirmed proof
        // is final and re-submitting would only risk replacing it with a worse one.
        val before = runCatching { service.verifyLocal(stored) }.getOrNull()
        if (before?.status == OtsStatus.CONFIRMED) {
            return Outcome(shortcode, OtsStatus.CONFIRMED, "Bitcoin attestation already present.", false)
        }

        val result = runCatching { service.upgrade(stored) }.getOrNull()
            ?: return Outcome(
                shortcode,
                OtsStatus.PENDING,
                "Calendars unreachable — seal remains pending Bitcoin confirmation.",
                false
            )

        // Nullable: the calendars may accept the request without returning a
        // proof, in which case there is nothing new to persist.
        val upgradedProof = result.otsProofBase64.orEmpty()
        if (upgradedProof.isNotBlank() && upgradedProof != stored) {
            // Persist whatever the calendars returned, even when still pending:
            // an upgraded pending proof is strictly closer to confirmation than
            // the original, and keeping it shortens the next attempt.
            runCatching { vault.storeOtsProof(shortcode, upgradedProof) }
        }

        return Outcome(
            shortcode = shortcode,
            status = result.status,
            message = result.message,
            newlyConfirmed = result.status == OtsStatus.CONFIRMED
        )
    }

    /**
     * Upgrades every pending proof in the vault.
     *
     * Called on launch and on demand, so a seal created yesterday reaches
     * confirmed state without the user knowing an upgrade step exists.
     */
    fun upgradeAllPending(): List<Outcome> =
        vault.listOtsShortcodes().mapNotNull { upgrade(it) }

    /**
     * Submits a digest that was queued while offline. The marker is replaced
     * by the real pending proof only when a calendar actually accepts the
     * digest; a failed submission keeps the marker so nothing is lost and the
     * next run retries.
     */
    private fun anchorQueuedDigest(shortcode: String, marker: String): Outcome {
        val sha512 = marker.removePrefix(UNANCHORED_PREFIX).trim()
        val result = runCatching { service.anchor(sha512) }.getOrNull()
        val proof = result?.otsProofBase64
        return if (result != null && proof != null &&
            (result.status == OtsStatus.PENDING || result.status == OtsStatus.CONFIRMED)
        ) {
            runCatching { vault.storeOtsProof(shortcode, proof) }
            Outcome(
                shortcode = shortcode,
                status = result.status,
                message = "Queued offline seal submitted — ${result.message}",
                newlyConfirmed = result.status == OtsStatus.CONFIRMED
            )
        } else {
            Outcome(
                shortcode = shortcode,
                status = OtsStatus.OFFLINE,
                message = "Offline seal still unanchored — calendars unreachable; will retry.",
                newlyConfirmed = false
            )
        }
    }
}

package com.verumomnis.forensic

import com.verumomnis.forensic.blockchain.BlockchainService
import com.verumomnis.forensic.blockchain.OpenTimestampsService
import com.verumomnis.forensic.model.OtsAnchorResult
import com.verumomnis.forensic.model.OtsStatus
import com.verumomnis.forensic.model.OtsVerifyResult
import com.verumomnis.forensic.seal.AnchorUpgrader
import com.verumomnis.forensic.vault.EvidenceVault
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Bitcoin confirmation handling: the attested block height is parsed from the
 * proof itself, confirmation depth is measured (never asserted), and a seal
 * created offline is queued and submitted on a later run instead of being
 * silently forgotten.
 */
class OtsConfirmationTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun vault() = EvidenceVault(temp.newFolder()).also { it.initialize() }

    // -- proof fixtures --------------------------------------------------------

    private val digest = ByteArray(32) { 0x11 }

    /** OTS varint: little-endian base-128, MSB = continuation. */
    private fun varint(value: Long): ByteArray {
        var v = value
        val out = mutableListOf<Byte>()
        while (true) {
            val b = (v and 0x7f).toInt()
            v = v ushr 7
            if (v == 0L) { out += b.toByte(); return out.toByteArray() }
            out += (b or 0x80).toByte()
        }
    }

    private fun bitcoinAttestation(height: Long): ByteArray {
        val tag = OpenTimestampsService.hexToBytes("0588960d73d71901")
        val payload = varint(height)
        return tag + varint(payload.size.toLong()) + payload
    }

    private fun confirmedProof(height: Long): ByteArray =
        OpenTimestampsService.buildProof(digest, bitcoinAttestation(height))

    private fun pendingProof(): ByteArray =
        OpenTimestampsService.buildProof(
            digest,
            OpenTimestampsService.hexToBytes("83dfe30d2ef90c8e") + varint(0)
        )

    // -- attested height & confirmations ---------------------------------------

    @Test
    fun attestedHeightIsParsedFromTheProof() {
        // 850000 needs a 3-byte varint — exercises the multi-byte path.
        val res = OpenTimestampsService.verify(confirmedProof(850_000), checkBitcoin = false)
        assertEquals(OtsStatus.CONFIRMED, res.status)
        assertTrue(res.bitcoinAttested)
        assertEquals(850_000L, res.attestedBlockHeight)
        // Offline verification measures nothing it cannot see.
        assertNull(res.confirmations)
        assertTrue(res.message.contains("block 850000"))
    }

    @Test
    fun pendingProofHasNoHeightAndNoConfirmations() {
        val res = OpenTimestampsService.verify(pendingProof(), checkBitcoin = false)
        assertEquals(OtsStatus.PENDING, res.status)
        assertNull(res.attestedBlockHeight)
        assertNull(res.confirmations)
    }

    @Test
    fun malformedAttestationYieldsNullHeightNeverAWrongOne() {
        // Bitcoin tag present but truncated before a complete varint payload.
        val truncated = OpenTimestampsService.buildProof(
            digest,
            OpenTimestampsService.hexToBytes("0588960d73d71901") + byteArrayOf(0x82.toByte())
        )
        val res = OpenTimestampsService.verify(truncated, checkBitcoin = false)
        assertEquals(OtsStatus.CONFIRMED, res.status) // tag is present
        assertNull("a malformed height must be null, not garbage", res.attestedBlockHeight)
    }

    @Test
    fun verifyLocalNeverTouchesTheNetworkPath() {
        val b64 = java.util.Base64.getEncoder().encodeToString(confirmedProof(900_000))
        val res = OpenTimestampsService.verifyLocal(b64)
        assertEquals(900_000L, res.attestedBlockHeight)
        assertNull(res.bitcoinTipHeight)
    }

    // -- offline queue lifecycle ------------------------------------------------

    private val sha512 = "ab".repeat(64)

    private class ScriptedService(
        private val anchorResult: (String) -> OtsAnchorResult?
    ) : BlockchainService {
        val anchored = mutableListOf<String>()
        override fun anchor(sha512Hex: String): OtsAnchorResult {
            anchored += sha512Hex
            return anchorResult(sha512Hex) ?: throw java.io.IOException("offline")
        }
        override fun verify(otsProofBase64: String): OtsVerifyResult = verifyLocal(otsProofBase64)
        override fun verifyLocal(otsProofBase64: String): OtsVerifyResult =
            OtsVerifyResult(OtsStatus.PENDING, pending = true, bitcoinAttested = false)
        override fun upgrade(otsProofBase64: String): OtsAnchorResult =
            throw java.io.IOException("upgrade should not be called for a queued digest")
    }

    private fun pendingAnchor(sha: String) = OtsAnchorResult(
        status = OtsStatus.PENDING,
        sha512 = sha,
        sha256Digest = "d",
        calendarUrls = listOf("https://fake.calendar"),
        otsProofBase64 = "cGVuZGluZw==",
        otsProofFile = "seal.ots",
        submittedAt = "2026-08-07T00:00:00Z",
        message = "Pending Bitcoin attestation via 1 calendar(s)."
    )

    @Test
    fun queuedOfflineSealIsSubmittedOnNextRun() {
        val v = vault()
        v.storeOtsProof("QQQQ0001", AnchorUpgrader.UNANCHORED_PREFIX + sha512)
        val service = ScriptedService { pendingAnchor(it) }

        val outcome = AnchorUpgrader(v, service).upgrade("QQQQ0001")!!

        assertEquals(listOf(sha512), service.anchored)
        assertEquals(OtsStatus.PENDING, outcome.status)
        assertFalse(outcome.newlyConfirmed)
        // The marker is replaced by the real pending proof.
        assertEquals("cGVuZGluZw==", v.loadOtsProof("QQQQ0001"))
    }

    @Test
    fun stillOfflineKeepsTheMarkerSoNothingIsLost() {
        val v = vault()
        val marker = AnchorUpgrader.UNANCHORED_PREFIX + sha512
        v.storeOtsProof("QQQQ0002", marker)
        val service = ScriptedService { null } // anchor throws

        val outcome = AnchorUpgrader(v, service).upgrade("QQQQ0002")!!

        assertEquals(OtsStatus.OFFLINE, outcome.status)
        assertEquals("the queued digest must survive a failed retry", marker, v.loadOtsProof("QQQQ0002"))
    }

    @Test
    fun upgradeAllPendingCoversQueuedDigestsToo() {
        val v = vault()
        v.storeOtsProof("QQQQ0003", AnchorUpgrader.UNANCHORED_PREFIX + sha512)
        val service = ScriptedService { pendingAnchor(it) }

        val outcomes = AnchorUpgrader(v, service).upgradeAllPending()

        assertEquals(1, outcomes.size)
        assertEquals("QQQQ0003", outcomes.single().shortcode)
        assertEquals(listOf(sha512), service.anchored)
    }
}

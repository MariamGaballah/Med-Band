package com.example

import com.example.hardware.NfcMedicalManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NfcPayloadTest {

    @Test
    fun `buildTokenUri creates opaque medband pointer without medical PII`() {
        val uri = NfcMedicalManager.buildTokenUri("user_12345", "tok_abcdef987654")
        assertEquals("medband://profile/user_12345/tok_abcdef987654", uri)
        assertFalse(uri.contains("blood"))
        assertFalse(uri.contains("allerg"))
    }

    @Test
    fun `parseTokenUri validates strict medband scheme and safe ID characters`() {
        val parsed = NfcMedicalManager.parseTokenUri("medband://profile/uid_abc_123/tok_xyz-789", "04AABBCC")
        assertNotNull(parsed)
        assertEquals("uid_abc_123", parsed!!.ownerUid)
        assertEquals("tok_xyz-789", parsed.tokenId)
        assertEquals("04AABBCC", parsed.tagUidHex)

        assertNull(NfcMedicalManager.parseTokenUri("https://example.com/profile/uid/tok"))
        assertNull(NfcMedicalManager.parseTokenUri("medband://profile/onlyone"))
        assertNull(NfcMedicalManager.parseTokenUri("medband://profile/bad\$uid/tok_123"))
        assertNull(NfcMedicalManager.parseTokenUri("medband://profile/uid_123/extra/part"))
    }
}

package com.pulscen.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class CryptoBoxTest {

    @Test
    fun encryptsAndDecryptsWithTheSamePassword() {
        val payload = "{\"email\":\"user@example.com\",\"products\":[]}"
        val salt = CryptoBox.newSalt()
        val key = CryptoBox.deriveKey("correct horse", salt)
        val encrypted = CryptoBox.encrypt(payload, key, salt)

        assertFalse(String(encrypted, Charsets.UTF_8).contains("user@example.com"))
        assertEquals(payload, CryptoBox.decrypt(encrypted, key))
        assertEquals(payload, CryptoBox.decryptWithPassword(encrypted, "correct horse"))
    }

    @Test
    fun wrongPasswordDoesNotDecrypt() {
        val salt = CryptoBox.newSalt()
        val key = CryptoBox.deriveKey("right", salt)
        val encrypted = CryptoBox.encrypt("secret", key, salt)

        assertNull(CryptoBox.decryptWithPassword(encrypted, "wrong"))
    }

    @Test
    fun envelopeContainsOnlyPublicKdfParameters() {
        val salt = CryptoBox.newSalt()
        val key = CryptoBox.deriveKey("pass", salt)
        val text = String(CryptoBox.encrypt("hello", key, salt), Charsets.UTF_8)

        assertTrue(text.contains(CryptoBox.FORMAT))
        assertTrue(text.contains("\"iterations\":${CryptoBox.ITERATIONS}"))
        assertFalse(text.contains("hello"))
    }

    @Test
    fun uidIsDeterministicAndHidesEmail() {
        val first = CryptoBox.uid(" User@Example.com ")
        val second = CryptoBox.uid("user@example.com")
        assertEquals(first, second)
        assertEquals(64, first.length)
        assertFalse(first.contains("user"))
        assertNotEquals(first, CryptoBox.uid("other@example.com"))
    }

    @Test
    fun saltIsRandom() {
        assertNotEquals(CryptoBox.newSalt(), CryptoBox.newSalt())
        assertEquals(16, Base64.getDecoder().decode(CryptoBox.newSalt()).size)
    }

    @Test
    fun validatesEmail() {
        assertTrue(CryptoBox.isValidEmail("a.b+c@example.ru"))
        assertFalse(CryptoBox.isValidEmail("not-an-email"))
        assertFalse(CryptoBox.isValidEmail("a@b"))
    }

    @Test
    fun rejectsCorruptedEnvelope() {
        val salt = CryptoBox.newSalt()
        val key = CryptoBox.deriveKey("pass", salt)
        val encrypted = CryptoBox.encrypt("hello", key, salt)
        // Портим именно шифротекст, оставляя конверт корректным JSON.
        val envelope = CryptoBox.parseEnvelope(encrypted)!!
        val data = Base64.getDecoder().decode(envelope.data)
        data[0] = (data[0].toInt() xor 0x01).toByte()
        val corrupted = """{"format":"${CryptoBox.FORMAT}","kdf":"${CryptoBox.KDF}",""" +
            """"iterations":${envelope.iterations},"salt":"${envelope.salt}",""" +
            """"iv":"${envelope.iv}","data":"${Base64.getEncoder().encodeToString(data)}"}"""
        assertNull(CryptoBox.decrypt(corrupted.toByteArray(Charsets.UTF_8), key))
        assertNotNull(CryptoBox.parseEnvelope(corrupted.toByteArray(Charsets.UTF_8)))
    }
}

package com.example.tuner.parental

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {

    @Test
    fun `valid pins are exactly four digits`() {
        assertTrue(PinHasher.isValidPin("0000"))
        assertTrue(PinHasher.isValidPin("4821"))
        assertFalse(PinHasher.isValidPin("123"))
        assertFalse(PinHasher.isValidPin("12345"))
        assertFalse(PinHasher.isValidPin("12a4"))
    }

    @Test
    fun `verify accepts the right pin and rejects others`() {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash("4821", salt)
        assertTrue(PinHasher.verify("4821", salt, hash))
        assertFalse(PinHasher.verify("4822", salt, hash))
    }

    @Test
    fun `same pin with different salts gives different hashes`() {
        val a = PinHasher.hash("4821", PinHasher.newSalt())
        val b = PinHasher.hash("4821", PinHasher.newSalt())
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun `salt is 16 bytes`() {
        assertEquals(16, PinHasher.newSalt().size)
    }

    @Test
    fun `hex round trips`() {
        val bytes = byteArrayOf(0, 1, 127, -128, -1)
        assertEquals("00017f80ff", PinHasher.toHex(bytes))
        assertArrayEquals(bytes, PinHasher.fromHex("00017f80ff"))
    }
}

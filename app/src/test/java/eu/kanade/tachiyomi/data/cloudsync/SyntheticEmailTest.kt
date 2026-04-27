package eu.kanade.tachiyomi.data.cloudsync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyntheticEmailTest {

    @Test
    fun `produces deterministic email for normalised username`() {
        assertEquals("yechika@${SyntheticEmail.DOMAIN}", SyntheticEmail.from("yechika"))
    }

    @Test
    fun `lowercases the username before composing the email`() {
        assertEquals("yechika@${SyntheticEmail.DOMAIN}", SyntheticEmail.from("YeChiKa"))
    }

    @Test
    fun `uses the reserved invalid TLD so mail can never be sent`() {
        assertTrue(SyntheticEmail.DOMAIN.endsWith(".invalid"))
    }

    @Test
    fun `recognises an email it produced`() {
        val email = SyntheticEmail.from("yechika")
        assertTrue(SyntheticEmail.isSynthetic(email))
    }

    @Test
    fun `does not flag a real email as synthetic`() {
        assertFalse(SyntheticEmail.isSynthetic("user@example.com"))
    }

    @Test
    fun `rejects an invalid username`() {
        assertThrows(IllegalArgumentException::class.java) {
            SyntheticEmail.from("hi!")
        }
    }
}

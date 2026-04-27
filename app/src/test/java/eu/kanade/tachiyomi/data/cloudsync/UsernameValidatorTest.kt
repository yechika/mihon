package eu.kanade.tachiyomi.data.cloudsync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UsernameValidatorTest {

    @Test
    fun `accepts a simple lowercase ascii name`() {
        val result = UsernameValidator.validate("yechika")
        assertInstanceOf(UsernameValidator.Result.Valid::class.java, result)
        assertEquals("yechika", (result as UsernameValidator.Result.Valid).normalised)
    }

    @Test
    fun `lowercases mixed case input`() {
        val result = UsernameValidator.validate("YeChiKa")
        assertInstanceOf(UsernameValidator.Result.Valid::class.java, result)
        assertEquals("yechika", (result as UsernameValidator.Result.Valid).normalised)
    }

    @Test
    fun `accepts hyphens digits and underscores`() {
        assertTrue(UsernameValidator.isValid("user_42"))
        assertTrue(UsernameValidator.isValid("a-b-c"))
        assertTrue(UsernameValidator.isValid("123abc"))
    }

    @Test
    fun `rejects empty input`() {
        assertEquals(UsernameValidator.Result.Empty, UsernameValidator.validate(""))
        assertEquals(UsernameValidator.Result.Empty, UsernameValidator.validate("   "))
    }

    @Test
    fun `rejects too short input`() {
        assertEquals(UsernameValidator.Result.TooShort, UsernameValidator.validate("ab"))
    }

    @Test
    fun `rejects too long input`() {
        val tooLong = "a".repeat(UsernameValidator.MAX_LENGTH + 1)
        assertEquals(UsernameValidator.Result.TooLong, UsernameValidator.validate(tooLong))
    }

    @Test
    fun `rejects illegal characters`() {
        assertEquals(UsernameValidator.Result.IllegalCharacters, UsernameValidator.validate("hello world"))
        assertEquals(UsernameValidator.Result.IllegalCharacters, UsernameValidator.validate("hi!"))
        assertEquals(UsernameValidator.Result.IllegalCharacters, UsernameValidator.validate("user@host"))
        assertEquals(UsernameValidator.Result.IllegalCharacters, UsernameValidator.validate("dot.name"))
    }

    @Test
    fun `accepts boundary lengths`() {
        val minOk = "a".repeat(UsernameValidator.MIN_LENGTH)
        val maxOk = "a".repeat(UsernameValidator.MAX_LENGTH)
        assertTrue(UsernameValidator.isValid(minOk))
        assertTrue(UsernameValidator.isValid(maxOk))
    }

    @Test
    fun `trims surrounding whitespace before validating`() {
        val result = UsernameValidator.validate("   yechika   ")
        assertInstanceOf(UsernameValidator.Result.Valid::class.java, result)
        assertEquals("yechika", (result as UsernameValidator.Result.Valid).normalised)
    }
}

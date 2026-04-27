package eu.kanade.tachiyomi.data.cloudsync

object UsernameValidator {

    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 24

    private val ALLOWED = Regex("^[a-z0-9_-]+$")

    sealed interface Result {
        data class Valid(val normalised: String) : Result
        data object TooShort : Result
        data object TooLong : Result
        data object IllegalCharacters : Result
        data object Empty : Result
    }

    fun validate(raw: String): Result {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return Result.Empty
        val lower = trimmed.lowercase()
        if (lower.length < MIN_LENGTH) return Result.TooShort
        if (lower.length > MAX_LENGTH) return Result.TooLong
        if (!ALLOWED.matches(lower)) return Result.IllegalCharacters
        return Result.Valid(lower)
    }

    fun isValid(raw: String): Boolean = validate(raw) is Result.Valid
}

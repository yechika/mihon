package eu.kanade.tachiyomi.data.cloudsync

object SyntheticEmail {

    const val DOMAIN = "accounts.mihon-cloud.invalid"

    fun from(username: String): String {
        val validation = UsernameValidator.validate(username)
        require(validation is UsernameValidator.Result.Valid) {
            "Cannot synthesise an email from an invalid username: $username"
        }
        return "${validation.normalised}@$DOMAIN"
    }

    fun isSynthetic(email: String): Boolean = email.endsWith("@$DOMAIN")
}

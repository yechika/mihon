package eu.kanade.tachiyomi.data.cloudsync

sealed interface AccountState {

    data object SignedOut : AccountState

    data class SignedIn(
        val uid: String,
        val username: String,
        val hasRecoveryEmail: Boolean,
    ) : AccountState

    data object Unavailable : AccountState
}

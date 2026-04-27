package eu.kanade.tachiyomi.data.cloudsync

sealed interface SignUpDecision {
    data object StartFresh : SignUpDecision
    data class AskUser(val localSummary: LibrarySummary) : SignUpDecision
}

sealed interface SignInDecision {
    data object NoAction : SignInDecision
    data object RestoreCloud : SignInDecision
    data object UploadLocal : SignInDecision
    data class AskUser(
        val localSummary: LibrarySummary,
        val cloudMetadata: SnapshotMetadata,
    ) : SignInDecision
}

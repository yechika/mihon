package eu.kanade.tachiyomi.data.cloudsync

import kotlinx.coroutines.flow.StateFlow

interface AccountManager {

    val state: StateFlow<AccountState>

    suspend fun signUp(username: String, password: String, email: String?): SignUpResult

    suspend fun signIn(usernameOrEmail: String, password: String): SignInResult

    suspend fun signOut()

    suspend fun requestPasswordReset(usernameOrEmail: String): PasswordResetResult

    suspend fun addRecoveryEmail(email: String): AddEmailResult

    suspend fun deleteAccount(password: String): DeleteAccountResult
}

sealed interface SignUpResult {
    data class Success(val uid: String, val username: String) : SignUpResult
    data object UsernameTaken : SignUpResult
    data object UsernameInvalid : SignUpResult
    data object PasswordTooShort : SignUpResult
    data object PasswordTooLong : SignUpResult
    data object EmailInvalid : SignUpResult
    data object Unavailable : SignUpResult
    data class NetworkError(val message: String) : SignUpResult
}

sealed interface SignInResult {
    data class Success(val uid: String, val username: String) : SignInResult
    data object IncorrectCredentials : SignInResult
    data object Unavailable : SignInResult
    data class NetworkError(val message: String) : SignInResult
}

sealed interface PasswordResetResult {
    data object Sent : PasswordResetResult
    data object Unavailable : PasswordResetResult
    data class NetworkError(val message: String) : PasswordResetResult
}

sealed interface AddEmailResult {
    data object Success : AddEmailResult
    data object EmailInvalid : AddEmailResult
    data object NotSignedIn : AddEmailResult
    data class NetworkError(val message: String) : AddEmailResult
}

sealed interface DeleteAccountResult {
    data object Success : DeleteAccountResult
    data object IncorrectPassword : DeleteAccountResult
    data object NotSignedIn : DeleteAccountResult
    data class NetworkError(val message: String) : DeleteAccountResult
}

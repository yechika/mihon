package eu.kanade.tachiyomi.data.cloudsync.noop

import eu.kanade.tachiyomi.data.cloudsync.AccountManager
import eu.kanade.tachiyomi.data.cloudsync.AccountState
import eu.kanade.tachiyomi.data.cloudsync.AddEmailResult
import eu.kanade.tachiyomi.data.cloudsync.DeleteAccountResult
import eu.kanade.tachiyomi.data.cloudsync.PasswordResetResult
import eu.kanade.tachiyomi.data.cloudsync.SignInResult
import eu.kanade.tachiyomi.data.cloudsync.SignUpResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class NoopAccountManager : AccountManager {

    override val state: StateFlow<AccountState> = MutableStateFlow(AccountState.Unavailable)

    override suspend fun signUp(username: String, password: String, email: String?): SignUpResult =
        SignUpResult.Unavailable

    override suspend fun signIn(usernameOrEmail: String, password: String): SignInResult =
        SignInResult.Unavailable

    override suspend fun signOut() = Unit

    override suspend fun requestPasswordReset(usernameOrEmail: String): PasswordResetResult =
        PasswordResetResult.Unavailable

    override suspend fun addRecoveryEmail(email: String): AddEmailResult =
        AddEmailResult.NotSignedIn

    override suspend fun deleteAccount(password: String): DeleteAccountResult =
        DeleteAccountResult.NotSignedIn
}

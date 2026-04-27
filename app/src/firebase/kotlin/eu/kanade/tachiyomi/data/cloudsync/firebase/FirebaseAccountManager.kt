package eu.kanade.tachiyomi.data.cloudsync.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import eu.kanade.tachiyomi.data.cloudsync.AccountManager
import eu.kanade.tachiyomi.data.cloudsync.AccountState
import eu.kanade.tachiyomi.data.cloudsync.AddEmailResult
import eu.kanade.tachiyomi.data.cloudsync.DeleteAccountResult
import eu.kanade.tachiyomi.data.cloudsync.PasswordResetResult
import eu.kanade.tachiyomi.data.cloudsync.SignInResult
import eu.kanade.tachiyomi.data.cloudsync.SignUpResult
import eu.kanade.tachiyomi.data.cloudsync.SyntheticEmail
import eu.kanade.tachiyomi.data.cloudsync.UsernameValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

class FirebaseAccountManager(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : AccountManager {

    private val _state = MutableStateFlow<AccountState>(AccountState.SignedOut)
    override val state: StateFlow<AccountState> = _state.asStateFlow()

    init {
        auth.addAuthStateListener { instance ->
            val user = instance.currentUser
            _state.value = if (user == null) {
                AccountState.SignedOut
            } else {
                AccountState.SignedIn(
                    uid = user.uid,
                    username = extractUsernameFromUser(user),
                    hasRecoveryEmail = userHasRecoveryEmail(user),
                )
            }
        }
    }

    override suspend fun signUp(username: String, password: String, email: String?): SignUpResult {
        val validation = UsernameValidator.validate(username)
        if (validation !is UsernameValidator.Result.Valid) {
            return SignUpResult.UsernameInvalid
        }
        val normalised = validation.normalised
        if (password.length < MIN_PASSWORD_LENGTH) return SignUpResult.PasswordTooShort
        if (password.length > MAX_PASSWORD_LENGTH) return SignUpResult.PasswordTooLong

        val realEmail = email?.takeIf { it.isNotBlank() }
        if (realEmail != null && !looksLikeEmail(realEmail)) return SignUpResult.EmailInvalid

        val usernameRef = firestore.collection(FirestoreCloudSyncStorage.COLLECTION_USERNAMES)
            .document(normalised)
        val existing = runCatching { usernameRef.get().await() }
            .getOrElse { return SignUpResult.NetworkError(it.message ?: "fetch failed") }
        if (existing.exists()) return SignUpResult.UsernameTaken

        val syntheticEmail = SyntheticEmail.from(normalised)

        val authResult = runCatching { auth.createUserWithEmailAndPassword(syntheticEmail, password).await() }
            .getOrElse { error ->
                return when (error) {
                    is FirebaseAuthUserCollisionException -> SignUpResult.UsernameTaken
                    is FirebaseAuthInvalidCredentialsException -> SignUpResult.PasswordTooShort
                    else -> SignUpResult.NetworkError(error.message ?: "sign-up failed")
                }
            }
        val uid = authResult.user?.uid
            ?: return SignUpResult.NetworkError("auth returned no uid")

        // Reserve the username + write profile. If either fails, roll back the auth user so we
        // don't leak orphaned auth records.
        val rollbackOnFailure: suspend (Throwable) -> SignUpResult = { error ->
            runCatching { auth.currentUser?.delete()?.await() }
            SignUpResult.NetworkError(error.message ?: "post-signup write failed")
        }

        runCatching {
            usernameRef.set(
                mapOf(
                    "uid" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
        }.onFailure { return rollbackOnFailure(it) }

        runCatching {
            firestore.collection(FirestoreCloudSyncStorage.COLLECTION_USERS)
                .document(uid)
                .collection(FirestoreCloudSyncStorage.SUBCOLLECTION_PROFILE)
                .document(DOC_PROFILE)
                .set(
                    mapOf(
                        "username" to normalised,
                        "recoveryEmail" to realEmail,
                        "createdAt" to FieldValue.serverTimestamp(),
                    ),
                )
                .await()
        }.onFailure { return rollbackOnFailure(it) }

        return SignUpResult.Success(uid, normalised)
    }

    override suspend fun signIn(usernameOrEmail: String, password: String): SignInResult {
        val email = if (looksLikeEmail(usernameOrEmail)) {
            usernameOrEmail
        } else {
            val validation = UsernameValidator.validate(usernameOrEmail)
            if (validation !is UsernameValidator.Result.Valid) return SignInResult.IncorrectCredentials

            // Look up the synthetic email via the username reservation. If absent, treat as
            // unknown to avoid disclosing existence.
            val resolved = resolveUsernameToEmail(validation.normalised)
                ?: SyntheticEmail.from(validation.normalised)
            resolved
        }

        val result = runCatching { auth.signInWithEmailAndPassword(email, password).await() }
            .getOrElse { error ->
                return when (error) {
                    is FirebaseAuthInvalidCredentialsException -> SignInResult.IncorrectCredentials
                    else -> SignInResult.NetworkError(error.message ?: "sign-in failed")
                }
            }
        val user = result.user ?: return SignInResult.IncorrectCredentials
        return SignInResult.Success(user.uid, extractUsernameFromUser(user))
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    override suspend fun requestPasswordReset(usernameOrEmail: String): PasswordResetResult {
        // Always succeed silently to avoid disclosing account existence. Internally try to
        // resolve to a real recovery email via the user's profile doc. If we can't find one
        // (synthetic-only account), do nothing but still return Sent.
        runCatching {
            val recoveryEmail = lookupRecoveryEmail(usernameOrEmail)
            if (recoveryEmail != null) {
                auth.sendPasswordResetEmail(recoveryEmail).await()
            }
        }
        return PasswordResetResult.Sent
    }

    override suspend fun addRecoveryEmail(email: String): AddEmailResult {
        if (!looksLikeEmail(email)) return AddEmailResult.EmailInvalid
        val user = auth.currentUser ?: return AddEmailResult.NotSignedIn
        return runCatching {
            firestore.collection(FirestoreCloudSyncStorage.COLLECTION_USERS)
                .document(user.uid)
                .collection(FirestoreCloudSyncStorage.SUBCOLLECTION_PROFILE)
                .document(DOC_PROFILE)
                .set(mapOf("recoveryEmail" to email), com.google.firebase.firestore.SetOptions.merge())
                .await()
            AddEmailResult.Success
        }.getOrElse { AddEmailResult.NetworkError(it.message ?: "write failed") }
    }

    override suspend fun deleteAccount(password: String): DeleteAccountResult {
        val user = auth.currentUser ?: return DeleteAccountResult.NotSignedIn

        val syntheticEmail = user.email ?: return DeleteAccountResult.NotSignedIn
        // Re-authenticate to satisfy Firebase's recent-login requirement for delete().
        val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(syntheticEmail, password)
        runCatching { user.reauthenticate(credential).await() }
            .getOrElse {
                return when (it) {
                    is FirebaseAuthInvalidCredentialsException -> DeleteAccountResult.IncorrectPassword
                    else -> DeleteAccountResult.NetworkError(it.message ?: "reauth failed")
                }
            }

        return runCatching {
            // Delete user doc tree first so Firestore rules still recognise the user as owner.
            FirestoreCloudSyncStorage(firestore).deleteAll(user.uid)
            user.delete().await()
            DeleteAccountResult.Success
        }.getOrElse { DeleteAccountResult.NetworkError(it.message ?: "delete failed") }
    }

    private suspend fun resolveUsernameToEmail(username: String): String? {
        val doc = runCatching {
            firestore.collection(FirestoreCloudSyncStorage.COLLECTION_USERNAMES)
                .document(username)
                .get()
                .await()
        }.getOrNull() ?: return null
        if (!doc.exists()) return null
        // The reservation only stores uid + createdAt. Synthesise the email from the username
        // — username is the lookup key so this is deterministic.
        return SyntheticEmail.from(username)
    }

    private suspend fun lookupRecoveryEmail(usernameOrEmail: String): String? {
        if (looksLikeEmail(usernameOrEmail) && !SyntheticEmail.isSynthetic(usernameOrEmail)) {
            return usernameOrEmail
        }
        val username = if (looksLikeEmail(usernameOrEmail)) {
            usernameOrEmail.substringBefore('@')
        } else {
            usernameOrEmail
        }
        val validation = UsernameValidator.validate(username)
        if (validation !is UsernameValidator.Result.Valid) return null

        val reservation = runCatching {
            firestore.collection(FirestoreCloudSyncStorage.COLLECTION_USERNAMES)
                .document(validation.normalised)
                .get()
                .await()
        }.getOrNull() ?: return null
        val uid = reservation.getString("uid") ?: return null

        val profile = runCatching {
            firestore.collection(FirestoreCloudSyncStorage.COLLECTION_USERS)
                .document(uid)
                .collection(FirestoreCloudSyncStorage.SUBCOLLECTION_PROFILE)
                .document(DOC_PROFILE)
                .get()
                .await()
        }.getOrNull() ?: return null
        val recovery = profile.getString("recoveryEmail")
        return recovery?.takeIf { it.isNotBlank() }
    }

    private fun extractUsernameFromUser(user: FirebaseUser): String {
        val email = user.email ?: return user.uid
        return if (SyntheticEmail.isSynthetic(email)) email.substringBefore('@') else email
    }

    private fun userHasRecoveryEmail(user: FirebaseUser): Boolean {
        // Best-effort sync read: the auth user's email is the synthetic one we generated. The
        // recovery email lives in Firestore, which we can't read synchronously here. The UI
        // re-checks via [addRecoveryEmail] / profile reads when needed, so this is allowed to
        // be initially false and refreshed later.
        return !SyntheticEmail.isSynthetic(user.email.orEmpty())
    }

    private fun looksLikeEmail(value: String): Boolean {
        val trimmed = value.trim()
        return trimmed.contains('@') && trimmed.indexOf('@') > 0 && trimmed.indexOf('@') < trimmed.length - 1
    }

    companion object {
        const val MIN_PASSWORD_LENGTH = 8
        const val MAX_PASSWORD_LENGTH = 64
        const val DOC_PROFILE = "profile"
    }
}

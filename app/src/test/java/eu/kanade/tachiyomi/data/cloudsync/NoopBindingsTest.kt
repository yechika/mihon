package eu.kanade.tachiyomi.data.cloudsync

import eu.kanade.tachiyomi.data.cloudsync.noop.NoopAccountManager
import eu.kanade.tachiyomi.data.cloudsync.noop.NoopCloudSyncStorage
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class NoopBindingsTest {

    @Test
    fun `NoopAccountManager always reports Unavailable`() {
        val manager = NoopAccountManager()
        assertEquals(AccountState.Unavailable, manager.state.value)
    }

    @Test
    fun `NoopAccountManager rejects signUp`() = runTest {
        assertEquals(SignUpResult.Unavailable, NoopAccountManager().signUp("user", "password1", null))
    }

    @Test
    fun `NoopAccountManager rejects signIn`() = runTest {
        assertEquals(SignInResult.Unavailable, NoopAccountManager().signIn("user", "password1"))
    }

    @Test
    fun `NoopCloudSyncStorage throws on every read or write`() = runTest {
        val storage = NoopCloudSyncStorage()
        assertThrows(UnsupportedOperationException::class.java) {
            kotlinx.coroutines.runBlocking { storage.fetchSnapshotMetadata("uid") }
        }
        assertThrows(UnsupportedOperationException::class.java) {
            kotlinx.coroutines.runBlocking {
                storage.writeSnapshot(
                    "uid",
                    SnapshotEnvelope(byteArrayOf(0), 0L, 1, 0, "x"),
                )
            }
        }
    }
}

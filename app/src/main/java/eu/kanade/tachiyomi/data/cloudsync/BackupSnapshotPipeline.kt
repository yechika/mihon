package eu.kanade.tachiyomi.data.cloudsync

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.backup.create.BackupCreator
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.restore.BackupRestorer
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import okio.buffer
import okio.gzip
import okio.sink
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.manga.interactor.GetFavorites
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * Adapter wiring [SnapshotProducer] to the existing [BackupCreator] so cloud-sync uploads carry
 * the same protobuf shape as a local backup file.
 */
class BackupSnapshotProducer(
    private val context: Context,
    private val getFavorites: GetFavorites = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
) : SnapshotProducer {

    override suspend fun produce(): SnapshotProducer.ProducedSnapshot {
        val creator = BackupCreator(context, isAutoBackup = false)
        val options = BackupOptions()
        val backup = creator.buildBackupModel(options)
        val payload = creator.encodeBackupBytes(options)
        return SnapshotProducer.ProducedSnapshot(
            payload = payload,
            schemaVersion = SCHEMA_VERSION,
            mangaCount = backup.backupManga.size,
        )
    }

    override suspend fun summarise(): LibrarySummary {
        val favourites = getFavorites.await()
        val categories: List<Category> = getCategories.await().filter { !it.isSystemCategory }
        return LibrarySummary(
            mangaCount = favourites.size,
            customCategoryCount = categories.size,
            lastEditedAt = 0L,
        )
    }

    companion object {
        const val SCHEMA_VERSION: Int = 2
    }
}

/**
 * Adapter wiring [SnapshotConsumer] to the existing [BackupRestorer] by writing the in-memory
 * payload to a gzipped temp file in the cache dir and replaying the standard restore path. This
 * deliberately reuses the local-file restore code instead of opening a parallel restore path.
 */
class BackupSnapshotConsumer(
    private val context: Context,
) : SnapshotConsumer {

    override suspend fun apply(payload: ByteArray, schemaVersion: Int) {
        val tempFile = writeAsGzippedTempFile(payload)
        val uri: Uri = tempFile.toUri()
        try {
            val notifier = BackupNotifier(context)
            BackupRestorer(context, notifier, isSync = true)
                .restore(uri, RestoreOptions())
        } finally {
            tempFile.delete()
        }
    }

    private fun writeAsGzippedTempFile(payload: ByteArray): File {
        val cacheDir = context.cacheDir
        val tempFile = File.createTempFile("cloud-sync-restore-", ".proto.gz", cacheDir)
        tempFile.outputStream().sink().gzip().buffer().use { it.write(payload) }
        return tempFile
    }
}

package tachiyomi.domain.storage.service

import android.content.Context
import androidx.core.net.toUri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.util.storage.DiskUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn

class StorageManager(
    private val context: Context,
    storagePreferences: StoragePreferences,
) {

    private val scope = CoroutineScope(Dispatchers.IO)

    private var baseDir: UniFile? = getBaseDir(storagePreferences.baseStorageDirectory.get())

    private var downloadsDir: UniFile? = null
    private var backupsDir: UniFile? = null
    private var localSourceDir: UniFile? = null

    private val _changes: Channel<Unit> = Channel(Channel.UNLIMITED)
    val changes = _changes.receiveAsFlow()
        .shareIn(scope, SharingStarted.Lazily, 1)

    init {
        storagePreferences.baseStorageDirectory.changes()
            .drop(1)
            .distinctUntilChanged()
            .onEach { uri ->
                baseDir = getBaseDir(uri)
                downloadsDir = null
                backupsDir = null
                localSourceDir = null
                baseDir?.let { parent ->
                    backupsDir = parent.createDirectory(AUTOMATIC_BACKUPS_PATH)
                    localSourceDir = parent.createDirectory(LOCAL_SOURCE_PATH)
                    downloadsDir = parent.createDirectory(DOWNLOADS_PATH).also {
                        DiskUtil.createNoMediaFile(it, context)
                    }
                }
                _changes.send(Unit)
            }
            .launchIn(scope)
    }

    private fun getBaseDir(uri: String): UniFile? {
        return UniFile.fromUri(context, uri.toUri())
            .takeIf { it?.exists() == true }
    }

    @Synchronized
    fun getAutomaticBackupsDirectory(): UniFile? {
        return backupsDir ?: if (baseDir?.name == AUTOMATIC_BACKUPS_PATH) {
            baseDir
        } else {
            baseDir?.createDirectory(AUTOMATIC_BACKUPS_PATH)
        }?.also { backupsDir = it }
    }

    @Synchronized
    fun getDownloadsDirectory(): UniFile? {
        return downloadsDir ?: baseDir?.createDirectory(DOWNLOADS_PATH).also { downloadsDir = it }
    }

    @Synchronized
    fun getLocalSourceDirectory(): UniFile? {
        return localSourceDir ?: baseDir?.createDirectory(LOCAL_SOURCE_PATH).also { localSourceDir = it }
    }
}

private const val AUTOMATIC_BACKUPS_PATH = "autobackup"
private const val DOWNLOADS_PATH = "downloads"
private const val LOCAL_SOURCE_PATH = "local"

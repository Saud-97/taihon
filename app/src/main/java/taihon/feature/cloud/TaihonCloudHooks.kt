package taihon.feature.cloud

import android.content.Context
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import tachiyomi.domain.storage.service.StorageManager
import taihon.domain.preferences.CloudPreferences
import taihon.feature.cloud.backup.CloudBackupMirrorWorker
import taihon.feature.cloud.ui.GoogleDriveBrowserScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

fun CloudService(context: Context): CloudService {
    val googleCloudClientId = context.getString(R.string.default_web_client_id)
    return CloudService(context, googleCloudClientId)
}

object TaihonCloudHooks {

    fun onManualBackupClick(navigator: Navigator, options: BackupOptions) {
        navigator.push(
            GoogleDriveBrowserScreen(
                mode = GoogleDriveBrowserScreen.Mode.SAVE_FILE,
                backupOptions = options,
            ),
        )
    }

    fun init(context: Context) {
        if (!BuildConfig.CLOUD_INCLUDED) return

        val cloudPreferences = Injekt.get<CloudPreferences>()
        val cloudService = Injekt.get<CloudService>()
        if (cloudPreferences.cloudEnabled.get() && cloudPreferences.cloudAutoMirror.get() &&
            cloudService.isAvailable()
        ) {
            CloudBackupMirrorWorker.schedule(context)
        }
    }

    fun onBackupCreated(context: Context, location: String) {
        if (!BuildConfig.CLOUD_INCLUDED) return

        val cloudPreferences = Injekt.get<CloudPreferences>()
        val cloudService = Injekt.get<CloudService>()
        val isFileInAutoDir = location.contains(StorageManager.AUTOMATIC_BACKUPS_PATH)

        if (cloudPreferences.cloudAutoMirror.get() &&
            cloudService.isAvailable() &&
            cloudService.accountState.value != null &&
            isFileInAutoDir
        ) {
            CloudBackupMirrorWorker.schedule(context, forceRestart = true)
        }
    }
}

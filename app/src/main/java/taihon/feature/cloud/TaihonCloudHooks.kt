package taihon.feature.cloud

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import mihon.app.di.appGraph
import taihon.feature.cloud.backup.CloudBackupMirrorWorker
import taihon.feature.cloud.ui.GoogleDriveBrowserScreen

@Suppress("SimplifyBooleanWithConstants")
object TaihonCloudHooks {

    fun onManualBackupClick(navigator: Navigator, options: BackupOptions) {
        navigator.push(
            GoogleDriveBrowserScreen(
                mode = GoogleDriveBrowserScreen.Mode.SAVE_FILE,
                backupOptions = options.asBooleanArray(),
            ),
        )
    }

    fun init(context: Context) {
        if (!BuildConfig.CLOUD_INCLUDED || !context.isMainProcess()) return

        val cloudPreferences = context.appGraph.taihonPreferences.cloud
        val cloudService = context.appGraph.cloudService
        if (cloudPreferences.cloudEnabled.get() && cloudPreferences.cloudAutoMirror.get() &&
            cloudService.isAvailable()
        ) {
            CloudBackupMirrorWorker.schedule(context)
        }
    }

    fun onBackupCreated(context: Context, location: String) {
        if (!BuildConfig.CLOUD_INCLUDED || !context.isMainProcess()) return

        val cloudPreferences = context.appGraph.taihonPreferences.cloud
        val cloudService = context.appGraph.cloudService
        val isFileInAutoDir = location.contains("autobackup")

        if (cloudPreferences.cloudAutoMirror.get() &&
            cloudService.isAvailable() &&
            cloudService.accountState.value != null &&
            isFileInAutoDir
        ) {
            CloudBackupMirrorWorker.schedule(context, forceRestart = true)
        }
    }

    private fun Context.isMainProcess(): Boolean {
        val processName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val pid = android.os.Process.myPid()
            am?.runningAppProcesses?.find { it.pid == pid }?.processName
        }
        return packageName == processName
    }
}

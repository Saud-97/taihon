package taihon.feature.cloud.backup

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.data.backup.create.BackupCreator
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.i18n.MR
import taihon.domain.preferences.CloudPreferences
import taihon.domain.preferences.TaihonPreferences
import taihon.feature.cloud.CloudService
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

class CloudBackupMirrorWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val taihonPreferences = Injekt.get<TaihonPreferences>()
        val cloudPreferences = Injekt.get<CloudPreferences>()
        val cloudService = Injekt.get<CloudService>()

        if (!cloudService.isAvailable()) {
            logcat(LogPriority.DEBUG) { "Cloud service not available, skipping mirror" }
            return Result.failure()
        }

        if (cloudService.accountState.value == null && cloudPreferences.cloudEnabled.get()) {
            try {
                cloudService.updateAccount(null)
            } catch (e: Exception) {
                logcat(LogPriority.DEBUG, e) { "Failed to silent update account before mirror" }
            }
        }

        if (cloudService.accountState.value == null) {
            if (cloudPreferences.cloudEnabled.get()) {
                logcat(LogPriority.WARN) { "Account missing during cloud mirror; notifying user" }
                showErrorNotification(
                    applicationContext.stringResource(
                        MR.strings.login_title,
                        applicationContext.stringResource(MR.strings.label_google_drive),
                    ),
                )
            }
            return Result.failure()
        }

        val storageManager = Injekt.get<StorageManager>()
        val localAutoBackupDir = storageManager.getAutomaticBackupsDirectory() ?: run {
            logcat(LogPriority.ERROR) { "Automatic backups directory is not configured" }
            return Result.failure()
        }

        return try {
            if (!localAutoBackupDir.exists()) {
                logcat(LogPriority.WARN) {
                    "Local automatic backups directory does not exist: ${localAutoBackupDir.filePath}"
                }
                return Result.failure()
            }

            val allFiles = localAutoBackupDir.listFiles() ?: emptyArray()
            val localFiles = allFiles.filter { it.name?.endsWith(".tachibk") == true }

            val autoBackupFolderId = cloudPreferences.cloudStorageAutoBackupId.get().takeIf { it.isNotEmpty() }
            val cloudFiles = cloudService.listFiles(autoBackupFolderId)
            val cloudFileNames = cloudFiles.map { it.name }.toSet()

            val missingLocalFiles = localFiles.filter { it.name != null && it.name !in cloudFileNames }

            if (missingLocalFiles.isNotEmpty()) {
                logcat(LogPriority.INFO) { "Found ${missingLocalFiles.size} missing backups pending mirror to cloud" }
                var successCount = 0
                var failedCount = 0
                var aborted = false
                val totalCount = missingLocalFiles.size

                for ((index, localFile) in missingLocalFiles.sortedBy { it.name }.withIndex()) {
                    if (isStopped) break

                    logcat(LogPriority.DEBUG) { "Mirroring backup ${index + 1}/$totalCount: ${localFile.name}" }
                    val tempFile =
                        File(applicationContext.cacheDir, "mirror_${System.currentTimeMillis()}_${localFile.name}")
                    try {
                        tempFile.parentFile?.mkdirs()

                        val bytesCopied = localFile.openInputStream().use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }

                        if (bytesCopied == 0L) {
                            logcat(LogPriority.WARN) { "Skipping mirror of empty file: ${localFile.name}" }
                            continue
                        }

                        val result = cloudService.uploadFile(tempFile, localFile.name!!)
                        if (result == null) {
                            if (!cloudPreferences.cloudEnabled.get()) {
                                logcat(LogPriority.ERROR) { "Mirror aborted: Account became unavailable" }
                                aborted = true
                                break
                            }
                            logcat(LogPriority.ERROR) { "Failed to upload mirror file to cloud: ${localFile.name}" }
                            failedCount++
                        } else {
                            logcat(LogPriority.DEBUG) { "Successfully mirrored file to cloud: ${localFile.name}" }
                            successCount++
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException || isStopped) throw e
                        if (!cloudPreferences.cloudEnabled.get()) {
                            logcat(LogPriority.ERROR, e) { "Mirror aborted: Service disabled (likely auth error)" }
                            aborted = true
                            break
                        }
                        logcat(LogPriority.ERROR, e) { "Error during mirror of ${localFile.name}" }
                        failedCount++
                    } finally {
                        tempFile.delete()
                    }
                }

                logcat(LogPriority.INFO) { "Cloud mirror finished. Success: $successCount, Failed: $failedCount" }

                if (aborted) return Result.failure()

                if (failedCount > 0 && !isStopped) {
                    showErrorNotification(applicationContext.stringResource(MR.strings.cloud_backup_error))
                    return Result.retry()
                }
            }

            val backupRetention = taihonPreferences.backupRetention.get()
            val autoBackupRegex = BackupCreator.FILENAME_REGEX

            val cloudBackups = cloudService.listFiles(autoBackupFolderId)
                .filter { autoBackupRegex.matches(it.name) }
                .sortedByDescending { it.modifiedTime }

            if (cloudBackups.size > backupRetention) {
                val filesToPrune = cloudBackups.drop(backupRetention)
                logcat(LogPriority.INFO) { "Pruning ${filesToPrune.size} old backups from cloud" }
                filesToPrune.forEach {
                    try {
                        cloudService.deleteFile(it.id)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        if (!cloudPreferences.cloudEnabled.get()) throw e
                        logcat(LogPriority.ERROR, e) { "Failed to prune cloud backup: ${it.name}" }
                    }
                }
            }

            Result.success()
        } catch (e: Exception) {
            if (e is CancellationException || isStopped || !cloudPreferences.cloudEnabled.get()) {
                if (!cloudPreferences.cloudEnabled.get()) {
                    logcat(LogPriority.ERROR, e) { "Cloud mirror sync aborted due to account becoming unavailable" }
                    showErrorNotification(
                        applicationContext.stringResource(
                            MR.strings.login_title,
                            applicationContext.stringResource(MR.strings.label_google_drive),
                        ),
                    )
                }
                Result.failure()
            } else {
                logcat(LogPriority.ERROR, e) { "Failed during cloud mirror sync" }
                showErrorNotification(applicationContext.stringResource(MR.strings.cloud_backup_error))
                Result.retry()
            }
        } finally {
            applicationContext.cancelNotification(Notifications.ID_CLOUD_MIRROR_PROGRESS)
        }
    }

    private fun showErrorNotification(message: String) {
        val notification = applicationContext.notificationBuilder(Notifications.CHANNEL_BACKUP_RESTORE_COMPLETE) {
            setSmallIcon(android.R.drawable.stat_notify_error)
            setContentTitle(applicationContext.stringResource(MR.strings.cloud_backup_error))
            setContentText(message)
            setPriority(NotificationCompat.PRIORITY_HIGH)
        }.build()
        applicationContext.notify(Notifications.ID_CLOUD_MIRROR_ERROR, notification)
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = applicationContext.notificationBuilder(Notifications.CHANNEL_BACKUP_RESTORE_PROGRESS) {
            setSmallIcon(android.R.drawable.stat_notify_sync)
            setContentTitle(applicationContext.stringResource(MR.strings.cloud_backups_label))
            setContentText(applicationContext.stringResource(MR.strings.channel_progress))
            setOngoing(true)
            setOnlyAlertOnce(true)
            setPriority(NotificationCompat.PRIORITY_MIN)
        }.build()

        return ForegroundInfo(
            Notifications.ID_CLOUD_MIRROR_PROGRESS,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    companion object {
        private const val TAG = "CloudBackupMirror"

        fun schedule(context: Context, forceRestart: Boolean = false) {
            val preferences = Injekt.get<LibraryPreferences>()
            val restrictions = preferences.autoUpdateDeviceRestrictions.get()

            val networkType = if (LibraryPreferences.DEVICE_NETWORK_NOT_METERED in restrictions) {
                NetworkType.UNMETERED
            } else {
                NetworkType.CONNECTED
            }

            val networkRequest = NetworkRequest.Builder().apply {
                removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                if (LibraryPreferences.DEVICE_ONLY_ON_WIFI in restrictions) {
                    addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                }
                if (LibraryPreferences.DEVICE_NETWORK_NOT_METERED in restrictions) {
                    addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                }
            }
                .build()

            val constraints = Constraints.Builder()
                .setRequiredNetworkRequest(networkRequest, networkType)
                .build()

            val request = OneTimeWorkRequestBuilder<CloudBackupMirrorWorker>()
                .setConstraints(constraints)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    TAG,
                    if (forceRestart) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                    request,
                )
        }

        fun stop(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(TAG)
        }
    }
}

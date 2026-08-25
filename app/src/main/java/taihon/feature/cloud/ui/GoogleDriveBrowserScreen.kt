package taihon.feature.cloud.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.hippo.unifile.UniFile
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.screen.SettingsDataScreen
import eu.kanade.presentation.more.settings.screen.data.RestoreBackupScreen
import eu.kanade.presentation.util.relativeTimeSpanString
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.backup.create.BackupCreator
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.isOnline
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import taihon.domain.preferences.CloudPreferences
import taihon.feature.cloud.CloudFile
import taihon.feature.cloud.CloudService
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.net.UnknownHostException

class GoogleDriveBrowserScreen(
    private val parentId: String? = null,
    private val relativePath: String? = null,
    private val mode: Mode,
    private val backupOptions: BackupOptions? = null,
) : Screen {

    override val key: ScreenKey =
        "GoogleDriveBrowserScreen:${parentId ?: "root"}:${mode.name}"

    enum class Mode {
        PICK_FOLDER,
        PICK_FILE,
        SAVE_FILE,
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val cloudService = remember { Injekt.get<CloudService>() }
        val cloudPreferences = remember { Injekt.get<CloudPreferences>() }

        val rootLabel = context.stringResource(MR.strings.label_google_drive)

        val storageRootId by cloudPreferences.cloudStorageRootId.collectAsState()
        val storageRootNamePref by cloudPreferences.cloudStorageRootName.collectAsState()
        val storageRootName = storageRootNamePref.ifEmpty { CloudService.ROOT_FOLDER }
        val autoBackupId by cloudPreferences.cloudStorageAutoBackupId.collectAsState()

        LaunchedEffect(Unit) {
            if (parentId == null) {
                val savedId = cloudPreferences.cloudStorageLocationId.get().takeIf { it.isNotEmpty() }
                    ?: storageRootId.takeIf { it.isNotEmpty() }

                if (savedId != null) {
                    val savedPath = cloudPreferences.cloudStorageLocationPath.get()
                    navigator.replace(
                        GoogleDriveBrowserScreen(
                            parentId = savedId,
                            relativePath = savedPath,
                            mode = mode,
                            backupOptions = backupOptions,
                        ),
                    )
                }
            }
        }

        val parentName = when {
            parentId == null -> rootLabel
            parentId == storageRootId -> storageRootName
            else -> relativePath?.substringAfterLast("/", relativePath) ?: ""
        }

        val displaySubtitle = buildString {
            append(rootLabel)
            if (parentId != null) {
                append('/')
                append(storageRootName)
                if (parentId != storageRootId && !relativePath.isNullOrEmpty()) {
                    append('/')
                    append(relativePath)
                }
            }
        }

        var cloudFiles by remember { mutableStateOf<List<CloudFile>>(emptyList()) }
        var isLoading by remember { mutableStateOf(!cloudService.isCached(parentId)) }
        var showCreateFolderDialog by remember { mutableStateOf(false) }
        var grandParentId by remember { mutableStateOf<String?>(null) }
        var saveFileName by remember { mutableStateOf(BackupCreator.getFilename()) }
        var progressTitle by remember { mutableStateOf<String?>(null) }
        var fileToDelete by remember { mutableStateOf<CloudFile?>(null) }

        val refresh = {
            scope.launch {
                val wasCached = cloudService.isCached(parentId)
                if (!wasCached) {
                    isLoading = true
                }
                try {
                    cloudFiles = cloudService.listFiles(parentId)

                    if (cloudFiles.isEmpty() && !wasCached) {
                        if (!context.isOnline()) {
                            context.toast(MR.strings.exception_offline)
                        } else {
                            cloudService.refreshCloudList(parentId)
                            cloudFiles = cloudService.listFiles(parentId)
                        }
                    }

                    if (parentId != null && parentId != storageRootId) {
                        val file = cloudService.getFile(parentId)
                        grandParentId = file?.parents?.firstOrNull()
                    } else {
                        grandParentId = null
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    logcat(LogPriority.ERROR, e)
                    if (e is UnknownHostException || !context.isOnline()) {
                        context.toast(MR.strings.exception_offline)
                    } else {
                        context.toast(MR.strings.unknown_error)
                    }
                } finally {
                    isLoading = false
                }
            }
        }

        LaunchedEffect(parentId, storageRootId) {
            refresh()
        }

        if (showCreateFolderDialog) {
            var folderName by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showCreateFolderDialog = false },
                title = { Text(stringResource(MR.strings.action_create)) },
                text = {
                    OutlinedTextField(
                        value = folderName,
                        onValueChange = { folderName = it },
                        label = { Text(stringResource(MR.strings.name)) },
                        singleLine = true,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (!context.isOnline()) {
                                context.toast(MR.strings.exception_offline)
                                return@TextButton
                            }
                            showCreateFolderDialog = false
                            scope.launch {
                                val result = cloudService.createFolder(folderName, parentId)
                                if (result != null) {
                                    refresh()
                                } else {
                                    context.toast(MR.strings.unknown_error)
                                }
                            }
                        },
                    ) {
                        Text(stringResource(MR.strings.action_create))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateFolderDialog = false }) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }

        fileToDelete?.let { file ->
            AlertDialog(
                onDismissRequest = { fileToDelete = null },
                title = { Text(stringResource(MR.strings.action_delete)) },
                text = {
                    Text(
                        stringResource(
                            if (file.isFolder) {
                                MR.strings.delete_folder_confirmation
                            } else {
                                MR.strings.delete_file_confirmation
                            },
                            file.name,
                        ),
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (!context.isOnline()) {
                                context.toast(MR.strings.exception_offline)
                                return@TextButton
                            }
                            val id = file.id
                            fileToDelete = null
                            scope.launch {
                                progressTitle = context.stringResource(MR.strings.deleting)
                                try {
                                    cloudService.deleteFile(id)
                                    refresh()
                                } catch (e: Exception) {
                                    logcat(LogPriority.ERROR, e)
                                    context.toast(MR.strings.unknown_error)
                                } finally {
                                    progressTitle = null
                                }
                            }
                        },
                    ) {
                        Text(stringResource(MR.strings.action_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { fileToDelete = null }) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }

        val currentProgressTitle = progressTitle
        if (currentProgressTitle != null) {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                title = { Text(currentProgressTitle) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.padding(vertical = 16.dp))
                    }
                },
            )
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = parentName.ifEmpty { rootLabel },
                    subtitle = displaySubtitle,
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        IconButton(
                            onClick = { showCreateFolderDialog = true },
                            enabled = progressTitle == null,
                        ) {
                            Icon(Icons.Outlined.CreateNewFolder, contentDescription = null)
                        }
                    },
                )
            },
            bottomBar = {
                if (mode == Mode.PICK_FOLDER || mode == Mode.SAVE_FILE) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(8.dp),
                    ) {
                        if (mode == Mode.PICK_FOLDER) {
                            Button(
                                onClick = {
                                    if (!context.isOnline()) {
                                        context.toast(MR.strings.exception_offline)
                                        return@Button
                                    }
                                    scope.launch {
                                        progressTitle = context.stringResource(MR.strings.loading)
                                        val newAutoBackupId =
                                            cloudService.updateStorageLocation(parentId ?: storageRootId)
                                        if (newAutoBackupId != null) {
                                            cloudPreferences.cloudStorageLocationPath.set(relativePath ?: "")
                                            navigator.popUntil { it is SettingsDataScreen }
                                        } else {
                                            context.toast(MR.strings.unknown_error)
                                        }
                                        progressTitle = null
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = progressTitle == null,
                            ) {
                                Text(stringResource(MR.strings.onboarding_storage_action_select))
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                OutlinedTextField(
                                    value = saveFileName,
                                    onValueChange = { saveFileName = it },
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp),
                                    singleLine = true,
                                    enabled = progressTitle == null,
                                )
                                Button(
                                    enabled = progressTitle == null,
                                    onClick = {
                                        if (!context.isOnline()) {
                                            context.toast(MR.strings.exception_offline)
                                            return@Button
                                        }
                                        scope.launch {
                                            progressTitle = context.stringResource(MR.strings.creating_backup)
                                            showNotification(context, progressTitle!!)
                                            val tempFile = File(
                                                context.cacheDir,
                                                "manual_cloud_backup_${System.currentTimeMillis()}.tachibk",
                                            )
                                            try {
                                                withContext(Dispatchers.IO) {
                                                    if (!tempFile.exists()) tempFile.createNewFile()

                                                    val finalName =
                                                        if (saveFileName.endsWith(".tachibk")) {
                                                            saveFileName
                                                        } else {
                                                            "$saveFileName.tachibk"
                                                        }
                                                    val uniFile = UniFile.fromFile(tempFile)!!

                                                    BackupCreator(context, false).backup(
                                                        uniFile.uri,
                                                        backupOptions ?: BackupOptions(),
                                                    )
                                                    cloudService.uploadFile(tempFile, finalName, parentId)
                                                }
                                                context.toast(MR.strings.backup_created)
                                                navigator.popUntil { it is SettingsDataScreen }
                                            } catch (e: Exception) {
                                                if (e is CancellationException) throw e
                                                logcat(LogPriority.ERROR, e)
                                                context.toast(MR.strings.creating_backup_error)
                                                tempFile.delete()
                                            } finally {
                                                progressTitle = null
                                                context.cancelNotification(Notifications.ID_CLOUD_MIRROR_PROGRESS)
                                            }
                                        }
                                    },
                                ) {
                                    Text(stringResource(MR.strings.action_save))
                                }
                            }
                        }
                    }
                }
            },
        ) { contentPadding ->
            if (isLoading) {
                Text(
                    text = stringResource(MR.strings.loading),
                    modifier = Modifier
                        .padding(contentPadding)
                        .padding(16.dp),
                )
            } else {
                FastScrollLazyColumn(
                    modifier = Modifier.padding(contentPadding),
                ) {
                    if (parentId != null && parentId != storageRootId) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = progressTitle == null) {
                                        val items = navigator.items
                                        val parentInStack = items.getOrNull(items.size - 2) as? GoogleDriveBrowserScreen
                                        if (parentInStack != null &&
                                            (
                                                parentInStack.parentId == grandParentId ||
                                                    (parentInStack.parentId == null && grandParentId == "root")
                                                )
                                        ) {
                                            navigator.pop()
                                        } else {
                                            navigator.push(
                                                GoogleDriveBrowserScreen(
                                                    parentId = grandParentId,
                                                    relativePath = relativePath?.substringBeforeLast("/", ""),
                                                    mode = mode,
                                                    backupOptions = backupOptions,
                                                ),
                                            )
                                        }
                                    }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Outlined.ArrowUpward,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 16.dp),
                                )
                                Text(
                                    stringResource(MR.strings.action_bar_up_description),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            HorizontalDivider()
                        }
                    }

                    if (cloudFiles.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = stringResource(MR.strings.no_results_found),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    } else {
                        items(
                            count = cloudFiles.size,
                            key = { cloudFiles[it].id },
                        ) { index ->
                            val item = cloudFiles[index]
                            CloudFileItem(
                                file = item,
                                onClick = {
                                    if (progressTitle != null) return@CloudFileItem
                                    if (item.isFolder) {
                                        val newRelativePath = if (relativePath.isNullOrEmpty()) {
                                            item.name
                                        } else {
                                            "$relativePath/${item.name}"
                                        }
                                        navigator.push(
                                            GoogleDriveBrowserScreen(
                                                parentId = item.id,
                                                relativePath = newRelativePath,
                                                mode = mode,
                                                backupOptions = backupOptions,
                                            ),
                                        )
                                    } else if (mode == Mode.PICK_FILE && item.name.endsWith(".tachibk")) {
                                        if (!context.isOnline()) {
                                            context.toast(MR.strings.exception_offline)
                                            return@CloudFileItem
                                        }
                                        scope.launch {
                                            progressTitle =
                                                context.stringResource(MR.strings.downloading_with_progress, 0)
                                                    .substringBefore(" (")
                                            showNotification(context, progressTitle!!)
                                            val tempFile = File(
                                                context.cacheDir,
                                                "temp_restore_${System.currentTimeMillis()}.tachibk",
                                            )
                                            try {
                                                if (!tempFile.exists()) tempFile.createNewFile()
                                                withContext(Dispatchers.IO) {
                                                    cloudService.downloadFile(item.id, tempFile)
                                                }

                                                if (tempFile.exists() && tempFile.length() > 0) {
                                                    val uri = UniFile.fromFile(tempFile)!!.uri.toString()
                                                    navigator.popUntil { it is SettingsDataScreen }
                                                    navigator.push(RestoreBackupScreen(uri))
                                                } else {
                                                    throw Exception("Downloaded file is empty")
                                                }
                                            } catch (e: Exception) {
                                                if (e is CancellationException) throw e
                                                logcat(LogPriority.ERROR, e)
                                                val errorMsg = context.stringResource(MR.strings.restoring_backup_error)
                                                context.toast(errorMsg)
                                                BackupNotifier(context).showRestoreError(errorMsg)
                                                tempFile.delete()
                                            } finally {
                                                progressTitle = null
                                                context.cancelNotification(Notifications.ID_CLOUD_MIRROR_PROGRESS)
                                            }
                                        }
                                    }
                                },
                                onLongClick = {
                                    if (progressTitle == null) {
                                        // Don't allow deleting the magic automatic backups folder if we are currently looking at it
                                        if (item.id != autoBackupId) {
                                            fileToDelete = item
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun CloudFileItem(
        file: CloudFile,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (file.isFolder) Icons.Outlined.Folder else Icons.Outlined.Description,
                contentDescription = null,
                modifier = Modifier.padding(end = 16.dp),
            )
            Column {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!file.isFolder) {
                        Text(
                            text = android.text.format.Formatter.formatFileSize(
                                LocalContext.current,
                                file.size,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = " • ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = relativeTimeSpanString(file.modifiedTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    private fun showNotification(context: Context, title: String) {
        val notification = context.notificationBuilder(Notifications.CHANNEL_BACKUP_RESTORE_PROGRESS) {
            setSmallIcon(R.drawable.ic_refresh_24dp)
            setContentTitle(title)
            setContentText(context.stringResource(MR.strings.channel_progress))
            setOngoing(true)
            setOnlyAlertOnce(true)
            setPriority(NotificationCompat.PRIORITY_MIN)
        }.build()

        try {
            context.notify(Notifications.ID_CLOUD_MIRROR_PROGRESS, notification)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }
}

package taihon.feature.cloud.ui

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.data.CreateBackupScreen
import eu.kanade.presentation.more.settings.widget.BasePreferenceWidget
import eu.kanade.presentation.more.settings.widget.PrefsHorizontalPadding
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import taihon.domain.preferences.CloudPreferences
import taihon.feature.cloud.CloudService
import taihon.feature.cloud.backup.CloudBackupMirrorWorker
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.presentation.core.util.collectAsState as collectAsPreferenceState

object TaihonCloudSettingsHooks {

    @Composable
    fun getGroup(): Preference.PreferenceGroup {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val cloudPreferences = Injekt.get<CloudPreferences>()
        val cloudService = remember { Injekt.get<CloudService>() }
        val scope = rememberCoroutineScope()
        val account by cloudService.accountState.collectAsState()
        val isCloudEnabled by cloudPreferences.cloudEnabled.collectAsPreferenceState()

        var isInitializing by remember { mutableStateOf(false) }

        if (isInitializing) {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                title = { Text(stringResource(MR.strings.logging_in)) },
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

        LaunchedEffect(Unit) {
            if (isCloudEnabled) {
                try {
                    cloudService.invalidateCache()
                    cloudService.updateAccount(null)
                    cloudService.getCloudStorage(forceDeepCheck = true)
                } catch (_: Exception) {
                    // Fail silently, error is likely auth-related and handled by service
                }
            }
        }

        val cloudPath by cloudPreferences.cloudStorageLocationPath.collectAsPreferenceState()
        val cloudStorageRootName by cloudPreferences.cloudStorageRootName.collectAsPreferenceState()
        val cloudItems = mutableListOf<Preference.PreferenceItem<out Any, out Any>>()

        if (!cloudService.isAvailable()) {
            cloudItems.add(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.label_google_drive),
                    subtitle = stringResource(MR.strings.cloud_backup_not_supported),
                    onClick = { context.toast(MR.strings.cloud_backup_not_supported) },
                    icon = Icons.Outlined.Cloud,
                ),
            )
        } else {
            cloudItems.add(
                Preference.PreferenceItem.SwitchPreference(
                    preference = cloudPreferences.cloudEnabled,
                    title = if (account != null) {
                        stringResource(MR.strings.label_google_drive)
                    } else {
                        stringResource(MR.strings.login_title, stringResource(MR.strings.label_google_drive))
                    },
                    subtitle = if (account != null) {
                        context.stringResource(MR.strings.login_success) +
                            ": ${account!!.displayName} (${account!!.email})"
                    } else {
                        stringResource(MR.strings.login)
                    },
                    icon = Icons.Outlined.Cloud,
                    onValueChanged = { enabled ->
                        if (enabled && account == null) {
                            scope.launch {
                                try {
                                    val activity = context as? Activity ?: return@launch
                                    cloudService.signIn(activity)
                                    if (cloudService.accountState.value != null) {
                                        isInitializing = true
                                        try {
                                            if (cloudService.getCloudStorage(forceDeepCheck = true) == null) {
                                                context.toast(MR.strings.unknown_error)
                                                cloudService.signOut()
                                            } else {
                                                cloudPreferences.cloudEnabled.set(true)
                                            }
                                        } finally {
                                            isInitializing = false
                                        }
                                    }
                                } catch (e: Exception) {
                                    context.toast(e.message)
                                }
                            }
                            false
                        } else if (!enabled && account != null) {
                            cloudService.signOut()
                            true
                        } else {
                            true
                        }
                    },
                ),
            )

            if (isCloudEnabled && account != null) {
                cloudItems.add(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = cloudPreferences.cloudAutoMirror,
                        title = stringResource(MR.strings.pref_auto_mirror_to_cloud),
                        subtitle = stringResource(MR.strings.pref_auto_mirror_to_cloud_summary),
                        onValueChanged = { enabled ->
                            if (enabled) {
                                CloudBackupMirrorWorker.schedule(context, forceRestart = true)
                            } else {
                                CloudBackupMirrorWorker.stop(context)
                            }
                            true
                        },
                    ),
                )
                cloudItems.add(
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(MR.strings.pref_storage_location),
                        subtitle = buildString {
                            append(context.stringResource(MR.strings.label_google_drive))
                            val rootName = cloudStorageRootName.ifEmpty { CloudService.ROOT_FOLDER }
                            append('/')
                            append(rootName)
                            if (cloudPath.isNotEmpty()) {
                                append('/')
                                append(cloudPath)
                            }
                        },
                        onClick = {
                            navigator.push(
                                GoogleDriveBrowserScreen(
                                    mode = GoogleDriveBrowserScreen.Mode.PICK_FOLDER,
                                ),
                            )
                        },
                    ),
                )
                cloudItems.add(
                    getBackupActionPreference(
                        titleRes = MR.strings.label_backup,
                        onCreateClick = {
                            navigator.push(
                                CreateBackupScreen(isCloudBackup = true),
                            )
                        },
                        onRestoreClick = {
                            navigator.push(
                                GoogleDriveBrowserScreen(
                                    mode = GoogleDriveBrowserScreen.Mode.PICK_FILE,
                                ),
                            )
                        },
                    ),
                )
            }
        }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.cloud_backups_label),
            badge = ImageVector.vectorResource(R.drawable.ic_taihon),
            preferenceItems = cloudItems,
        )
    }

    @Composable
    private fun getBackupActionPreference(
        titleRes: StringResource,
        onCreateClick: () -> Unit,
        onRestoreClick: () -> Unit,
    ): Preference.PreferenceItem.CustomPreference {
        return Preference.PreferenceItem.CustomPreference(
            title = stringResource(titleRes),
        ) {
            BasePreferenceWidget(
                subcomponent = {
                    MultiChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(intrinsicSize = IntrinsicSize.Min)
                            .padding(horizontal = PrefsHorizontalPadding),
                    ) {
                        SegmentedButton(
                            modifier = Modifier.fillMaxHeight(),
                            checked = false,
                            onCheckedChange = { onCreateClick() },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                        ) {
                            Text(stringResource(MR.strings.pref_create_backup))
                        }
                        SegmentedButton(
                            modifier = Modifier.fillMaxHeight(),
                            checked = false,
                            onCheckedChange = { onRestoreClick() },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                        ) {
                            Text(stringResource(MR.strings.pref_restore_backup))
                        }
                    }
                },
            )
        }
    }
}

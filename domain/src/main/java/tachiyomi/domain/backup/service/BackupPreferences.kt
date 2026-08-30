package tachiyomi.domain.backup.service

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import taihon.domain.preferences.TaihonPreferences

class BackupPreferences(
    preferenceStore: PreferenceStore,
    val taihon: TaihonPreferences,
) {

    val backupInterval: Preference<Int> = preferenceStore.getInt("backup_interval", 12)

    val lastAutoBackupTimestamp: Preference<Long> = preferenceStore.getLong(
        Preference.appStateKey("last_auto_backup_timestamp"),
        0L,
    )
}

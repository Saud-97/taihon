package taihon.domain.preferences

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class CloudPreferences(
    preferenceStore: PreferenceStore,
) {
    val cloudEnabled: Preference<Boolean> = preferenceStore.getBoolean(
        Preference.appStateKey("cloud_enabled"),
        false,
    )

    val cloudAutoMirror: Preference<Boolean> = preferenceStore.getBoolean(
        Preference.appStateKey("cloud_auto_mirror"),
        false,
    )

    val cloudStorageRootId: Preference<String> = preferenceStore.getString(
        Preference.appStateKey("cloud_storage_root_id"),
        "",
    )

    val cloudStorageRootName: Preference<String> = preferenceStore.getString(
        Preference.appStateKey("cloud_storage_root_name"),
        "",
    )

    val cloudStorageAutoBackupId: Preference<String> = preferenceStore.getString(
        Preference.appStateKey("cloud_storage_auto_backup_id"),
        "",
    )

    val cloudStorageLocationId: Preference<String> = preferenceStore.getString(
        Preference.appStateKey("cloud_storage_location_id"),
        "",
    )

    val cloudStorageLocationPath: Preference<String> = preferenceStore.getString(
        Preference.appStateKey("cloud_storage_location_path"),
        "",
    )

    val cloudAccountEmail: Preference<String> = preferenceStore.getString(
        Preference.appStateKey("cloud_account_email"),
        "",
    )

    val cloudAccountName: Preference<String> = preferenceStore.getString(
        Preference.appStateKey("cloud_account_name"),
        "",
    )
}

package taihon.domain.preferences

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class CloudPreferences(
    preferenceStore: PreferenceStore,
) {
    val cloudEnabled: Preference<Boolean> = preferenceStore.getBoolean(
        Preference.appStateKey(PREF_CLOUD_ENABLED),
        false,
    )

    val cloudAutoMirror: Preference<Boolean> = preferenceStore.getBoolean(
        Preference.appStateKey(PREF_CLOUD_AUTO_MIRROR),
        false,
    )

    val cloudStorageRootId: Preference<String> = preferenceStore.getString(
        Preference.appStateKey(PREF_CLOUD_STORAGE_ROOT_ID),
        "",
    )

    val cloudStorageRootName: Preference<String> = preferenceStore.getString(
        Preference.appStateKey(PREF_CLOUD_STORAGE_ROOT_NAME),
        "",
    )

    val cloudStorageAutoBackupId: Preference<String> = preferenceStore.getString(
        Preference.appStateKey(PREF_CLOUD_STORAGE_AUTO_BACKUP_ID),
        "",
    )

    val cloudStorageLocationId: Preference<String> = preferenceStore.getString(
        Preference.appStateKey(PREF_CLOUD_STORAGE_LOCATION_ID),
        "",
    )

    val cloudStorageLocationPath: Preference<String> = preferenceStore.getString(
        Preference.appStateKey(PREF_CLOUD_STORAGE_LOCATION_PATH),
        "",
    )

    val cloudAccountEmail: Preference<String> = preferenceStore.getString(
        Preference.appStateKey(PREF_CLOUD_ACCOUNT_EMAIL),
        "",
    )

    val cloudAccountName: Preference<String> = preferenceStore.getString(
        Preference.appStateKey(PREF_CLOUD_ACCOUNT_NAME),
        "",
    )

    companion object {
        const val PREF_CLOUD_ENABLED = "cloud_enabled"
        const val PREF_CLOUD_AUTO_MIRROR = "cloud_auto_mirror"
        const val PREF_CLOUD_STORAGE_ROOT_ID = "cloud_storage_root_id"
        const val PREF_CLOUD_STORAGE_ROOT_NAME = "cloud_storage_root_name"
        const val PREF_CLOUD_STORAGE_AUTO_BACKUP_ID = "cloud_storage_auto_backup_id"
        const val PREF_CLOUD_STORAGE_LOCATION_ID = "cloud_storage_location_id"
        const val PREF_CLOUD_STORAGE_LOCATION_PATH = "cloud_storage_location_path"
        const val PREF_CLOUD_ACCOUNT_EMAIL = "cloud_account_email"
        const val PREF_CLOUD_ACCOUNT_NAME = "cloud_account_name"
    }
}

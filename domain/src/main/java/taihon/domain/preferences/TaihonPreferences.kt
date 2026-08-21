package taihon.domain.preferences

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.getEnum

class TaihonPreferences(
    private val preferenceStore: PreferenceStore,
) {

    val allowHardwareBitmapForCovers: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_allow_hardware_bitmap_covers",
        true,
    )

    val hideExtensionUpdatesCount: Preference<Boolean> = preferenceStore.getBoolean("ext_hide_updates_count", false)

    val smartApostropheNormalization: Preference<Boolean> = preferenceStore.getBoolean(
        "smart_apostrophe_normalization",
        true,
    )

    val smartApostropheNormalizationExceptions: Preference<Set<String>> = preferenceStore.getStringSet(
        "smart_apostrophe_normalization_exceptions",
        emptySet(),
    )

    val globalSearchEnrichResults: Preference<Boolean> = preferenceStore.getBoolean(
        "global_search_enrich_results",
        true,
    )

    fun filterSource(id: Long): Preference<TriState> = preferenceStore.getEnum(
        "pref_filter_library_source_${id}_v2",
        TriState.DISABLED,
    )

    val filterOrphanedSources: Preference<TriState> = preferenceStore.getEnum(
        "pref_filter_library_orphaned_sources_v2",
        TriState.DISABLED,
    )

    val sourceInstalledBadge: Preference<Boolean> = preferenceStore.getBoolean("display_source_installed_badge", false)

    val sourceOrphanedBadge: Preference<Boolean> = preferenceStore.getBoolean("display_source_orphaned_badge", true)

    val resumeLastSeenPage: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_resume_last_seen_page",
        true,
    )

    val pageTransitionDistance: Preference<Int> = preferenceStore.getInt("pref_page_transition_distance", 0)

    val pageTransitionSpeed: Preference<Int> = preferenceStore.getInt("pref_page_transition_speed", 0)

    val parallelChapterLimit: Preference<Int> = preferenceStore.getInt("download_parallel_chapter_limit", 2)

    val backupRetention: Preference<Int> = preferenceStore.getInt("backup_retention", 12)
}

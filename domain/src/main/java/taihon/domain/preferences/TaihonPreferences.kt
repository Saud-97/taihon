package taihon.domain.preferences

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.getEnum

@Inject
@SingleIn(AppScope::class)
class TaihonPreferences(
    private val preferenceStore: PreferenceStore,
) {
    val cloud = CloudPreferences(preferenceStore)

    // App Preferences

    val useSoftwareRenderingForCovers: Preference<Boolean> = preferenceStore.getBoolean(
        PREF_USE_SOFTWARE_RENDERING_FOR_COVERS,
        false,
    )

    // Library Preferences

    val resumeLastSeenPage: Preference<Boolean> = preferenceStore.getBoolean(
        PREF_RESUME_LAST_SEEN_PAGE,
        true,
    )

    fun filterSource(id: Long): Preference<TriState> = preferenceStore.getEnum(
        "pref_filter_library_source_${id}_v2",
        TriState.DISABLED,
    )

    val filterOrphanedSources: Preference<TriState> = preferenceStore.getEnum(
        PREF_FILTER_ORPHANED_SOURCES,
        TriState.DISABLED,
    )

    val sourceInstalledBadge: Preference<Boolean> = preferenceStore.getBoolean(PREF_SOURCE_INSTALLED_BADGE, false)

    val sourceOrphanedBadge: Preference<Boolean> = preferenceStore.getBoolean(PREF_SOURCE_ORPHANED_BADGE, true)

    // Reader Preferences

    val pageTransitionDistance: Preference<Int> = preferenceStore.getInt(PREF_PAGE_TRANSITION_DISTANCE, 0)

    val pageTransitionSpeed: Preference<Int> = preferenceStore.getInt(PREF_PAGE_TRANSITION_SPEED, 0)

    // Source Preferences

    val hideExtensionUpdatesCount: Preference<Boolean> = preferenceStore.getBoolean(PREF_HIDE_EXT_UPDATES_COUNT, false)

    val smartApostropheNormalization: Preference<Boolean> = preferenceStore.getBoolean(
        PREF_SMART_APOSTROPHE_NORMALIZATION,
        true,
    )

    val smartApostropheNormalizationExceptions: Preference<Set<String>> = preferenceStore.getStringSet(
        PREF_SMART_APOSTROPHE_NORMALIZATION_EXCEPTIONS,
        emptySet(),
    )

    val globalSearchEnrichResults: Preference<Boolean> = preferenceStore.getBoolean(
        PREF_GLOBAL_SEARCH_ENRICH_RESULTS,
        true,
    )

    // Download Preferences

    val prefetchUpcomingChapters: Preference<Boolean> = preferenceStore.getBoolean(
        PREF_PREFETCH_UPCOMING_CHAPTERS,
        true,
    )

    // Backup Preferences

    val backupRetention: Preference<Int> = preferenceStore.getInt(PREF_BACKUP_RETENTION, 12)

    companion object {
        const val PREF_USE_SOFTWARE_RENDERING_FOR_COVERS = "pref_use_software_rendering_for_covers"

        const val PREF_RESUME_LAST_SEEN_PAGE = "pref_resume_last_seen_page"
        const val PREF_FILTER_ORPHANED_SOURCES = "pref_filter_library_orphaned_sources_v2"
        const val PREF_SOURCE_INSTALLED_BADGE = "display_source_installed_badge"
        const val PREF_SOURCE_ORPHANED_BADGE = "display_source_orphaned_badge"

        const val PREF_PAGE_TRANSITION_DISTANCE = "pref_page_transition_distance"
        const val PREF_PAGE_TRANSITION_SPEED = "pref_page_transition_speed"

        const val PREF_HIDE_EXT_UPDATES_COUNT = "ext_hide_updates_count"
        const val PREF_SMART_APOSTROPHE_NORMALIZATION = "smart_apostrophe_normalization"
        const val PREF_SMART_APOSTROPHE_NORMALIZATION_EXCEPTIONS = "smart_apostrophe_normalization_exceptions"
        const val PREF_GLOBAL_SEARCH_ENRICH_RESULTS = "global_search_enrich_results"

        const val PREF_PREFETCH_UPCOMING_CHAPTERS = "prefetch_upcoming_chapters"

        const val PREF_BACKUP_RETENTION = "backup_retention"
    }
}

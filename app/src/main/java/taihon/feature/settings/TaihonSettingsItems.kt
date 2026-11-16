package taihon.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import mihon.app.di.appGraph
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import taihon.feature.ui.components.taihonBadge

@Composable
fun getTaihonAdvancedLibraryItems(): List<Preference.PreferenceItem<out Any, out Any>> {
    val context = LocalContext.current
    val taihonPreferences = remember { context.appGraph.taihonPreferences }
    return listOf(
        Preference.PreferenceItem.SwitchPreference(
            preference = taihonPreferences.useSoftwareRenderingForCovers,
            title = stringResource(MR.strings.pref_use_software_rendering_for_covers),
            subtitle = stringResource(MR.strings.pref_use_software_rendering_for_covers_summary),
            badge = taihonBadge(),
        ),
    )
}

@Composable
fun getTaihonExtensionItems(): List<Preference.PreferenceItem<out Any, out Any>> {
    val context = LocalContext.current
    val taihonPreferences = remember { context.appGraph.taihonPreferences }
    return listOf(
        Preference.PreferenceItem.SwitchPreference(
            preference = taihonPreferences.hideExtensionUpdatesCount,
            title = stringResource(MR.strings.pref_extension_update_hide_browse_badge),
            badge = taihonBadge(),
        ),
    )
}

@Composable
fun getTaihonBrowseGroups(): List<Preference.PreferenceGroup> {
    val context = LocalContext.current
    val taihonPreferences = remember { context.appGraph.taihonPreferences }
    val extensionManager = remember { context.appGraph.extensionManager }

    val installedExtensions by extensionManager.loadedExtensionsFlow.collectAsState(emptyList<Extension.Installed>())
    val smartApostropheNormalization by taihonPreferences.smartApostropheNormalization.collectAsState()

    return listOf(
        Preference.PreferenceGroup(
            title = stringResource(MR.strings.action_global_search),
            badge = taihonBadge(),
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = taihonPreferences.smartApostropheNormalization,
                    title = stringResource(MR.strings.pref_smart_apostrophe_normalization),
                    subtitle = stringResource(MR.strings.pref_smart_apostrophe_normalization_summary),
                ),
                Preference.PreferenceItem.MultiSelectListPreference(
                    preference = taihonPreferences.smartApostropheNormalizationExceptions,
                    entries = installedExtensions.associate { it.pkgName to it.name },
                    title = stringResource(MR.strings.pref_smart_apostrophe_normalization_exceptions),
                    subtitle = stringResource(MR.strings.exclude),
                    visible = smartApostropheNormalization,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = taihonPreferences.globalSearchEnrichResults,
                    title = stringResource(MR.strings.pref_global_search_enrich_results),
                    subtitle = stringResource(MR.strings.pref_global_search_enrich_results_summary),
                ),
            ),
        ),
    )
}

@Composable
fun getTaihonBackupGroupItems(): List<Preference.PreferenceItem<out Any, out Any>> {
    val context = LocalContext.current
    val taihonPreferences = remember { context.appGraph.taihonPreferences }
    val backupPreferences = remember { context.appGraph.backupPreferences }

    val backupInterval by backupPreferences.backupInterval.collectAsState()

    return listOf(
        Preference.PreferenceItem.SliderPreference(
            preference = taihonPreferences.backupRetention,
            valueRange = 4..100,
            title = stringResource(MR.strings.pref_backup_retention),
            subtitle = stringResource(MR.strings.pref_backup_retention_info),
            badge = taihonBadge(),
            steps = 0,
            visible = backupInterval > 0,
        ),
    )
}

@Composable
fun getTaihonDownloadItems(): List<Preference.PreferenceItem<out Any, out Any>> {
    val context = LocalContext.current
    val taihonPreferences = remember { context.appGraph.taihonPreferences }
    return listOf(
        Preference.PreferenceItem.SwitchPreference(
            preference = taihonPreferences.prefetchUpcomingChapters,
            title = stringResource(MR.strings.pref_prefetch_upcoming_chapters),
            subtitle = stringResource(MR.strings.pref_prefetch_upcoming_chapters_summary),
            badge = taihonBadge(),
        ),
    )
}

@Composable
fun getTaihonLibraryBehaviorItems(): List<Preference.PreferenceItem<out Any, out Any>> {
    val context = LocalContext.current
    val taihonPreferences = remember { context.appGraph.taihonPreferences }
    return listOf(
        Preference.PreferenceItem.SwitchPreference(
            preference = taihonPreferences.resumeLastSeenPage,
            title = stringResource(MR.strings.pref_resume_last_seen_page),
            subtitle = stringResource(MR.strings.pref_resume_last_seen_page_summary),
            badge = taihonBadge(),
        ),
    )
}

@Composable
fun getTaihonReaderItems(): List<Preference.PreferenceItem<out Any, out Any>> {
    val context = LocalContext.current
    val taihonPref = remember { context.appGraph.taihonPreferences }
    val readerPref = remember { context.appGraph.readerPreferences }

    return listOf(
        Preference.PreferenceItem.SliderPreference(
            preference = taihonPref.pageTransitionSpeed,
            valueRange = 0..1000 step 50,
            title = stringResource(MR.strings.pref_page_transition_speed),
            valueText = {
                if (it == 0) {
                    stringResource(MR.strings.label_default)
                } else {
                    stringResource(MR.strings.pref_flash_duration_summary, it)
                }
            },
            onValueChanged = {
                if (it > 0) readerPref.pageTransitions.set(true)
                true
            },
            badge = taihonBadge(),
        ),
        Preference.PreferenceItem.SliderPreference(
            preference = taihonPref.pageTransitionDistance,
            valueRange = 0..100 step 5,
            title = stringResource(MR.strings.pref_page_transition_distance),
            valueText = {
                if (it == 0 || it == 75) {
                    stringResource(MR.strings.label_default)
                } else {
                    "$it%"
                }
            },
            badge = taihonBadge(),
        ),
    )
}

@Composable
fun getTaihonWebtoonPaddingItem(): Preference.PreferenceItem.SliderPreference {
    val context = LocalContext.current
    val readerPreferences = remember { context.appGraph.readerPreferences }
    val numberFormat = remember { java.text.NumberFormat.getPercentInstance() }

    return Preference.PreferenceItem.SliderPreference(
        preference = readerPreferences.webtoonSidePadding,
        valueRange = ReaderPreferences.WEBTOON_PADDING_MIN..ReaderPreferences.WEBTOON_PADDING_MAX,
        title = stringResource(MR.strings.pref_webtoon_side_padding),
        valueText = { numberFormat.format(it / 100f) },
        badge = taihonBadge(),
    )
}

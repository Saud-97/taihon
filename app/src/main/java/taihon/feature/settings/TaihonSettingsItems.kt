package taihon.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import tachiyomi.domain.backup.service.BackupPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import taihon.domain.preferences.TaihonPreferences
import taihon.feature.ui.components.taihonBadge
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun getTaihonAdvancedLibraryItems(): List<Preference.PreferenceItem<out Any, out Any>> {
    val taihonPreferences = remember { Injekt.get<TaihonPreferences>() }
    return listOf(
        Preference.PreferenceItem.SwitchPreference(
            preference = taihonPreferences.allowHardwareBitmapForCovers,
            title = stringResource(MR.strings.pref_allow_hardware_bitmap_covers),
            subtitle = stringResource(MR.strings.pref_allow_hardware_bitmap_covers_summary),
            badge = taihonBadge(),
        ),
    )
}

@Composable
fun getTaihonExtensionItems(): List<Preference.PreferenceItem<out Any, out Any>> {
    val taihonPreferences = remember { Injekt.get<TaihonPreferences>() }
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
    val taihonPreferences = remember { Injekt.get<TaihonPreferences>() }
    val extensionManager = remember { Injekt.get<ExtensionManager>() }

    val installedExtensions by extensionManager.installedExtensionsFlow.collectAsState(emptyList<Extension.Installed>())
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
                    enabled = smartApostropheNormalization,
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
    val taihonPreferences = remember { Injekt.get<TaihonPreferences>() }
    val backupPreferences = remember { Injekt.get<BackupPreferences>() }

    val backupRetention by taihonPreferences.backupRetention.collectAsState()
    val backupInterval by backupPreferences.backupInterval.collectAsState()

    return listOf(
        Preference.PreferenceItem.SliderPreference(
            value = backupRetention,
            valueRange = 4..100,
            title = stringResource(MR.strings.pref_backup_retention),
            subtitle = stringResource(MR.strings.pref_backup_retention_info),
            onValueChanged = { taihonPreferences.backupRetention.set(it) },
            badge = taihonBadge(),
            steps = 0,
            enabled = backupInterval > 0,
        ),
    )
}

@Composable
fun getTaihonDownloadItems(): List<Preference.PreferenceItem<out Any, out Any>> {
    val taihonPreferences = remember { Injekt.get<TaihonPreferences>() }
    val parallelChapterLimit by taihonPreferences.parallelChapterLimit.collectAsState()

    return listOf(
        Preference.PreferenceItem.SliderPreference(
            value = parallelChapterLimit,
            valueRange = 1..5,
            title = stringResource(MR.strings.pref_download_concurrent_chapters),
            subtitle = stringResource(MR.strings.pref_download_concurrent_chapters_summary),
            onValueChanged = { taihonPreferences.parallelChapterLimit.set(it) },
            badge = taihonBadge(),
        ),
    )
}

@Composable
fun getTaihonLibraryBehaviorItems(): List<Preference.PreferenceItem<out Any, out Any>> {
    val taihonPreferences = remember { Injekt.get<TaihonPreferences>() }
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
    val taihonPref = remember { Injekt.get<TaihonPreferences>() }
    val readerPref = remember { Injekt.get<ReaderPreferences>() }

    val pageTransitionDistance by taihonPref.pageTransitionDistance.collectAsState()
    val pageTransitionSpeed by taihonPref.pageTransitionSpeed.collectAsState()

    return listOf(
        Preference.PreferenceItem.SliderPreference(
            value = if (pageTransitionSpeed == 0) 0 else (1050 - pageTransitionSpeed) / 50,
            valueRange = 0..20,
            steps = 19,
            title = stringResource(MR.strings.pref_page_transition_speed),
            valueString = if (pageTransitionSpeed == 0) {
                stringResource(MR.strings.label_default)
            } else {
                stringResource(MR.strings.pref_flash_duration_summary, pageTransitionSpeed)
            },
            onValueChanged = { sliderValue ->
                val newSpeed = if (sliderValue == 0) 0 else 1050 - sliderValue * 50
                taihonPref.pageTransitionSpeed.set(newSpeed)
                if (newSpeed > 0) {
                    readerPref.pageTransitions.set(true)
                }
            },
            badge = taihonBadge(),
        ),
        Preference.PreferenceItem.SliderPreference(
            value = when (pageTransitionDistance) {
                0 -> 0
                else -> (pageTransitionDistance - 5) / 5
            },
            valueRange = 0..19,
            steps = 18,
            title = stringResource(MR.strings.pref_page_transition_distance),
            valueString = if (pageTransitionDistance == 0 || pageTransitionDistance == 75) {
                stringResource(MR.strings.label_default)
            } else {
                "$pageTransitionDistance%"
            },
            onValueChanged = { sliderValue ->
                val newDistance = if (sliderValue == 0) 0 else sliderValue * 5 + 5
                taihonPref.pageTransitionDistance.set(newDistance)
            },
            badge = taihonBadge(),
        ),
    )
}

@Composable
fun getTaihonWebtoonPaddingItem(): Preference.PreferenceItem<Int, Unit> {
    val readerPreferences = remember { Injekt.get<ReaderPreferences>() }
    val numberFormat = remember { java.text.NumberFormat.getPercentInstance() }

    val webtoonSidePadding by readerPreferences.webtoonSidePadding.collectAsState()

    return Preference.PreferenceItem.SliderPreference(
        value = webtoonSidePadding,
        valueRange = ReaderPreferences.WEBTOON_PADDING_MIN..ReaderPreferences.WEBTOON_PADDING_MAX,
        title = stringResource(MR.strings.pref_webtoon_side_padding),
        valueString = numberFormat.format(webtoonSidePadding / 100f),
        onValueChanged = { readerPreferences.webtoonSidePadding.set(it) },
        badge = taihonBadge(),
    )
}

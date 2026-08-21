package taihon.feature.reader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import tachiyomi.core.common.preference.Preference
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.components.SliderItem
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import taihon.feature.ui.components.taihonBadge

@Composable
fun TaihonReaderSettingsGroup(
    pageTransitionsPref: Preference<Boolean>,
    pageTransitionSpeedPref: Preference<Int>,
    pageTransitionDistancePref: Preference<Int>,
) {
    val pageTransitions by pageTransitionsPref.collectAsState()
    val pageTransitionSpeed by pageTransitionSpeedPref.collectAsState()
    val pageTransitionDistance by pageTransitionDistancePref.collectAsState()

    LaunchedEffect(pageTransitions) {
        if (!pageTransitions) {
            pageTransitionSpeedPref.set(0)
        }
    }

    CheckboxItem(
        label = stringResource(MR.strings.pref_page_transitions),
        pref = pageTransitionsPref,
    )

    SliderItem(
        label = stringResource(MR.strings.pref_page_transition_speed),
        value = if (pageTransitionSpeed == 0) 0 else (1050 - pageTransitionSpeed) / 50,
        valueRange = 0..20,
        steps = 19,
        valueString = if (pageTransitionSpeed == 0) {
            stringResource(MR.strings.label_default)
        } else {
            stringResource(MR.strings.pref_flash_duration_summary, pageTransitionSpeed)
        },
        onChange = { sliderValue ->
            val newSpeed = if (sliderValue == 0) 0 else 1050 - sliderValue * 50
            pageTransitionSpeedPref.set(newSpeed)
            if (newSpeed > 0 && !pageTransitions) {
                pageTransitionsPref.set(true)
            }
        },
        pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        badge = taihonBadge(),
    )

    SliderItem(
        label = stringResource(MR.strings.pref_page_transition_distance),
        value = when (pageTransitionDistance) {
            0 -> 0
            else -> (pageTransitionDistance - 5) / 5
        },
        valueRange = 0..19,
        steps = 18,
        valueString = if (pageTransitionDistance == 0 || pageTransitionDistance == 75) {
            stringResource(MR.strings.label_default)
        } else {
            "$pageTransitionDistance%"
        },
        onChange = { sliderValue ->
            val newDistance = if (sliderValue == 0) 0 else sliderValue * 5 + 5
            pageTransitionDistancePref.set(newDistance)
        },
        pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        badge = taihonBadge(),
    )
}

package taihon.feature.browse.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import eu.kanade.presentation.util.formatChapterNumber
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.Badge
import tachiyomi.presentation.core.components.BadgeGroup
import tachiyomi.presentation.core.i18n.stringResource
import taihon.feature.ui.components.TaihonLoadingBadge

/**
 * Renders Taihon-specific badges for search results, including chapter counts
 * and loading status.
 */
@Composable
fun TaihonSearchBadgeOverlay(
    chapterCount: Int?,
    isLoading: Boolean,
) {
    if (isLoading) {
        if (chapterCount != null && chapterCount > 0) {
            BadgeGroup {
                Badge(text = "$chapterCount")
                TaihonLoadingBadge()
            }
        } else {
            TaihonLoadingBadge()
        }
    } else if (chapterCount != null && chapterCount > 0) {
        Badge(text = "$chapterCount")
    }
}

/**
 * Returns the formatted latest chapter label for the manga cover.
 */
@Composable
fun rememberTaihonSearchCoverText(latestChapter: Double?): String? {
    val formattedLatestChapter = remember(latestChapter) {
        latestChapter?.let(::formatChapterNumber)
    }
    return formattedLatestChapter?.let {
        stringResource(MR.strings.migrationListScreen_latestChapterLabel, it)
    }
}

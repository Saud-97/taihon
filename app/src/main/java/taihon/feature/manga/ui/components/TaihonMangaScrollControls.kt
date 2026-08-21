package taihon.feature.manga.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

class TaihonMangaScrollState(
    val isFarAbove: State<Boolean>,
    val isFarBelow: State<Boolean>,
    val isScrolledDown: State<Boolean>,
    val isNotAtBottom: State<Boolean>,
)

@Composable
fun rememberTaihonMangaScrollState(
    chapterListState: LazyListState,
    currentlyReadingIndex: Int,
): TaihonMangaScrollState {
    return remember(chapterListState, currentlyReadingIndex) {
        TaihonMangaScrollState(
            isFarAbove = derivedStateOf {
                currentlyReadingIndex != -1 && currentlyReadingIndex < chapterListState.firstVisibleItemIndex
            },
            isFarBelow = derivedStateOf {
                val lastVisible = chapterListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                currentlyReadingIndex != -1 && currentlyReadingIndex > lastVisible
            },
            isScrolledDown = derivedStateOf { chapterListState.firstVisibleItemIndex > 0 },
            isNotAtBottom = derivedStateOf {
                val lastVisible = chapterListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisible < chapterListState.layoutInfo.totalItemsCount - 1
            },
        )
    }
}

@Composable
fun BoxScope.TaihonMangaScrollControls(
    chapterListState: LazyListState,
    currentlyReadingIndex: Int,
    isAnySelected: Boolean,
    topPadding: Dp,
    centerOffset: Int,
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberTaihonMangaScrollState(chapterListState, currentlyReadingIndex)

    val isScrolledDown by scrollState.isScrolledDown
    val isNotAtBottom by scrollState.isNotAtBottom
    val isFarAbove by scrollState.isFarAbove
    val isFarBelow by scrollState.isFarBelow

    AnimatedVisibility(
        visible = isScrolledDown && !isAnySelected,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = topPadding + 16.dp),
    ) {
        SuggestionChip(
            onClick = {
                scope.launch {
                    if (isFarAbove) {
                        chapterListState.animateScrollToItem(currentlyReadingIndex, centerOffset)
                    } else {
                        chapterListState.animateScrollToItem(0)
                    }
                }
            },
            label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(text = stringResource(MR.strings.action_move_to_top))
                }
            },
        )
    }

    AnimatedVisibility(
        visible = isNotAtBottom && !isAnySelected,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 16.dp),
    ) {
        SuggestionChip(
            onClick = {
                scope.launch {
                    if (isFarBelow) {
                        chapterListState.animateScrollToItem(currentlyReadingIndex, centerOffset)
                    } else {
                        chapterListState.animateScrollToItem(
                            chapterListState.layoutInfo.totalItemsCount - 1,
                        )
                    }
                }
            },
            label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(text = stringResource(MR.strings.action_move_to_bottom))
                }
            },
        )
    }
}

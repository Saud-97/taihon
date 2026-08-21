package taihon.feature.reader

import androidx.recyclerview.widget.WebtoonLayoutManager
import androidx.viewpager.widget.ViewPager
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonViewer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import taihon.domain.preferences.TaihonPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Taihon-specific extensions for PagerViewer to handle custom scroll speeds.
 */
fun PagerViewer.setupTaihonScroller(scope: CoroutineScope) {
    val pager = this.pager
    val activity = this.activity
    var originalScroller: android.widget.Scroller? = null

    try {
        val scrollerField = ViewPager::class.java.getDeclaredField("mScroller")
        scrollerField.isAccessible = true
        originalScroller = scrollerField.get(pager) as? android.widget.Scroller
    } catch (_: Exception) {
    }

    fun setNextTransitionSpeed(duration: Int) {
        try {
            val scrollerField = ViewPager::class.java.getDeclaredField("mScroller")
            scrollerField.isAccessible = true
            val scroller = if (duration > 0) {
                CustomScroller(activity, duration)
            } else {
                originalScroller
            }
            scrollerField.set(pager, scroller)
        } catch (_: Exception) {
            // ignore
        }
    }

    Injekt.get<TaihonPreferences>().pageTransitionSpeed.changes()
        .onEach { setNextTransitionSpeed(it) }
        .launchIn(scope)
}

/**
 * Taihon-specific smooth scroll logic for WebtoonViewer.
 */
fun WebtoonViewer.performTaihonSmoothScroll(distance: Int) {
    if (config.pageTransitionDuration == 0) {
        recycler.smoothScrollBy(0, distance)
        return
    }
    android.animation.ValueAnimator.ofInt(0, distance).apply {
        duration = config.pageTransitionDuration.toLong()
        interpolator = android.view.animation.DecelerateInterpolator()
        var lastValue = 0
        addUpdateListener {
            val currentValue = it.animatedValue as Int
            recycler.scrollBy(0, currentValue - lastValue)
            lastValue = currentValue
        }
        start()
    }
}

/**
 * Taihon-specific configuration for WebtoonViewer.
 */
fun WebtoonViewer.setupTaihonWebtoonConfig(
    scope: CoroutineScope,
    layoutManager: WebtoonLayoutManager,
    scrollDistance: () -> Int,
) {
    Injekt.get<TaihonPreferences>().pageTransitionDistance.changes()
        .onEach { layoutManager.extraLayoutSpace = scrollDistance() }
        .launchIn(scope)
}

private class CustomScroller(context: android.content.Context, private val scrollDuration: Int) :
    android.widget.Scroller(context, android.view.animation.AccelerateDecelerateInterpolator()) {
    override fun startScroll(startX: Int, startY: Int, dx: Int, dy: Int, duration: Int) {
        super.startScroll(startX, startY, dx, dy, scrollDuration)
    }

    override fun startScroll(startX: Int, startY: Int, dx: Int, dy: Int) {
        super.startScroll(startX, startY, dx, dy, scrollDuration)
    }
}

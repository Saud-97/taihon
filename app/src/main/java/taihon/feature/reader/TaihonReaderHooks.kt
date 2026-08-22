package taihon.feature.reader

import taihon.domain.preferences.TaihonPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date
import kotlin.time.Clock

object TaihonReaderHooks {
    private val taihonPreferences: TaihonPreferences by lazy { Injekt.get() }

    fun shouldResumeLastSeenPage(): Boolean = taihonPreferences.resumeLastSeenPage.get()

    fun getReaderStartTime(): Long = Clock.System.now().toEpochMilliseconds()

    fun shouldSkipHistoryUpdate(readerStartTime: Long): Boolean {
        val now = Date().time
        return now - readerStartTime < 1000
    }
}

package taihon.feature.reader

import java.util.Date
import kotlin.time.Clock

object TaihonReaderHooks {

    fun getReaderStartTime(): Long = Clock.System.now().toEpochMilliseconds()

    fun shouldSkipHistoryUpdate(readerStartTime: Long): Boolean {
        val now = Date().time
        return now - readerStartTime < 1000
    }
}

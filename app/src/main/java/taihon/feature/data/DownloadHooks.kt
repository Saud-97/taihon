package taihon.feature.data

import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder

fun List<UniFile>.sortedByTaihonName(): List<UniFile> {
    return this.sortedWith(compareBy(String::compareToCaseInsensitiveNaturalOrder) { it.name.orEmpty() })
}

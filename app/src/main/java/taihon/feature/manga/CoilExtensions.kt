package taihon.feature.manga

import android.graphics.Bitmap
import coil3.request.ImageRequest
import coil3.request.allowHardware
import taihon.domain.preferences.TaihonPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

fun ImageRequest.Builder.applyTaihonHardwareBitmapPreference(): ImageRequest.Builder {
    val allowHardware = Injekt.get<TaihonPreferences>().allowHardwareBitmapForCovers.get()
    return this.allowHardware(allowHardware)
}

fun getTaihonHardwareBitmapConfig(): Bitmap.Config {
    val allowHardware = Injekt.get<TaihonPreferences>().allowHardwareBitmapForCovers.get()
    return if (allowHardware) Bitmap.Config.HARDWARE else Bitmap.Config.ARGB_8888
}

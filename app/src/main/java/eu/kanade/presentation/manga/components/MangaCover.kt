package eu.kanade.presentation.manga.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import eu.kanade.domain.base.BasePreferences
import eu.kanade.presentation.util.rememberResourceBitmapPainter
import eu.kanade.tachiyomi.R
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

enum class MangaCover(val ratio: Float) {
    Square(1f / 1f),
    Book(2f / 3f),
    ;

    @Composable
    operator fun invoke(
        data: Any?,
        modifier: Modifier = Modifier,
        contentDescription: String = "",
        shape: Shape = MaterialTheme.shapes.extraSmall,
        onClick: (() -> Unit)? = null,
    ) {
        val context = LocalContext.current
        val basePreferences = remember { Injekt.get<BasePreferences>() }
        val allowHardware by basePreferences.allowHardwareBitmapForCovers.collectAsState()

        val model = remember(data, allowHardware) {
            if (data is ImageRequest) {
                data.newBuilder().allowHardware(allowHardware).build()
            } else {
                ImageRequest.Builder(context)
                    .data(data)
                    .allowHardware(allowHardware)
                    .build()
            }
        }

        AsyncImage(
            model = model,
            placeholder = ColorPainter(CoverPlaceholderColor),
            error = rememberResourceBitmapPainter(id = R.drawable.cover_error),
            contentDescription = contentDescription,
            modifier = modifier
                .aspectRatio(ratio)
                .clip(shape)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            role = Role.Button,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                ),
            contentScale = ContentScale.Crop,
        )
    }
}

private val CoverPlaceholderColor = Color(0x1F888888)

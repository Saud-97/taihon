package taihon.feature.browse.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

object SourcesTaihonHooks {
    @Composable
    fun ColumnScope.SourceOptionsButtons(
        onClickSettings: (() -> Unit)?,
        onClickUninstall: (() -> Unit)?,
    ) {
        onClickSettings?.let {
            Text(
                text = stringResource(MR.strings.action_settings),
                modifier = Modifier
                    .clickable(onClick = it)
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            )
        }
        onClickUninstall?.let {
            Text(
                text = stringResource(MR.strings.ext_uninstall),
                modifier = Modifier
                    .clickable(onClick = it)
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            )
        }
    }
}

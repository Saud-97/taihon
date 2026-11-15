package taihon.feature.browse.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.tachiyomi.ui.browse.extension.details.ExtensionDetailsScreen
import eu.kanade.tachiyomi.ui.browse.extension.details.SourcePreferencesScreen
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import taihon.feature.browse.TaihonSourcesHelper

object TaihonSourcesHooks {
    @Composable
    fun SourceOptionsButtons(
        onClickSettings: (() -> Unit)? = null,
        onClickUninstall: (() -> Unit)? = null,
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

    @Composable
    fun rememberSettingsAction(navigator: Navigator, onClose: () -> Unit): (() -> Unit)? {
        val taihonDialog by TaihonSourcesHelper.getDialogInfo()
        val info = taihonDialog ?: return null
        if (!info.canOpenSettings) return null

        return {
            info.extensionPkgName?.let {
                navigator.push(ExtensionDetailsScreen(it))
            }
            if (info.hasCustomSettings) {
                navigator.push(SourcePreferencesScreen(info.source.id))
            }
            onClose()
        }
    }

    @Composable
    fun rememberUninstallAction(onClose: () -> Unit): (() -> Unit)? {
        val scope = rememberCoroutineScope()
        val taihonDialog by TaihonSourcesHelper.getDialogInfo()
        val info = taihonDialog ?: return null
        if (!info.canUninstall) return null

        return {
            scope.launch {
                TaihonSourcesHelper.uninstallExtension(info.source)
                onClose()
            }
        }
    }
}

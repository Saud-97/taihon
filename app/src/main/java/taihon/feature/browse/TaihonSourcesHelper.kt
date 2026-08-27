package taihon.feature.browse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.ConfigurableSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import tachiyomi.domain.source.model.Source
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object TaihonSourcesHelper {

    private val dialogInfo = MutableStateFlow<DialogInfo?>(null)

    data class DialogInfo(
        val source: Source,
        val canUninstall: Boolean,
        val canOpenSettings: Boolean,
        val hasCustomSettings: Boolean,
        val extensionPkgName: String?,
    )

    fun showDialog(
        source: Source,
    ) {
        val extensionManager = Injekt.get<ExtensionManager>()
        val sourceManager = Injekt.get<SourceManager>()
        val extension = extensionManager.installedExtensionsFlow.value
            .find { ext -> ext.sources.any { it.id == source.id } }
        val hasCustomSettings = sourceManager.get(source.id) is ConfigurableSource
        val canUninstall = extension != null
        val canOpenSettings = hasCustomSettings || extension != null

        dialogInfo.update {
            DialogInfo(
                source = source,
                canUninstall = canUninstall,
                canOpenSettings = canOpenSettings,
                hasCustomSettings = hasCustomSettings,
                extensionPkgName = extension?.pkgName,
            )
        }
    }

    @Composable
    fun getDialogInfo(): State<DialogInfo?> {
        return dialogInfo.collectAsState()
    }

    fun closeDialog() {
        dialogInfo.update { null }
    }

    fun uninstallExtension(
        source: Source,
    ) {
        val extensionManager = Injekt.get<ExtensionManager>()
        val extension = extensionManager.installedExtensionsFlow.value
            .find { ext -> ext.sources.any { it.id == source.id } }
            ?: return
        extensionManager.uninstallExtension(extension)
    }
}

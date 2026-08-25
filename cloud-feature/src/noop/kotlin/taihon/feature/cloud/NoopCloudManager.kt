package taihon.feature.cloud

import android.app.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class NoopCloudManager : CloudService {
    override val accountState: StateFlow<CloudAccount?> = MutableStateFlow(null)

    override fun isAvailable(): Boolean = false
    override suspend fun signIn(activity: Activity) = Unit
    override fun signOut() = Unit
    override suspend fun updateAccount(activity: Activity?) = Unit

    override suspend fun uploadFile(localFile: File, fileName: String, parentId: String?): String? = null
    override suspend fun downloadFile(fileId: String, localFile: File) = Unit
    override suspend fun deleteFile(fileId: String) = Unit
    override suspend fun listFiles(parentId: String?): List<CloudFile> = emptyList()
    override suspend fun getFile(fileId: String): CloudFile? = null
    override suspend fun createFolder(name: String, parentId: String?): String? = null

    override suspend fun getCloudStorage(forceDeepCheck: Boolean): CloudStorageInfo? = null
    override suspend fun getRootDisplayName(rootId: String?): String = CloudService.ROOT_FOLDER
    override suspend fun refreshCloudList(parentId: String?) = Unit
    override suspend fun setCloudStorageRoot(rootId: String): CloudStorageInfo? = null
    override suspend fun updateStorageLocation(folderId: String): String? = null

    override fun isCached(parentId: String?): Boolean = false
    override fun invalidateCache() = Unit
}

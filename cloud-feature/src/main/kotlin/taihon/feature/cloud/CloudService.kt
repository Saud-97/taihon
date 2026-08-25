package taihon.feature.cloud

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow
import java.io.File

interface CloudService {
    val accountState: StateFlow<CloudAccount?>

    fun isAvailable(): Boolean
    suspend fun signIn(activity: Activity)
    fun signOut()
    suspend fun updateAccount(activity: Activity? = null)

    suspend fun uploadFile(localFile: File, fileName: String, parentId: String? = null): String?
    suspend fun downloadFile(fileId: String, localFile: File)
    suspend fun deleteFile(fileId: String)
    suspend fun listFiles(parentId: String? = null): List<CloudFile>
    suspend fun getFile(fileId: String): CloudFile?
    suspend fun createFolder(name: String, parentId: String? = null): String?

    suspend fun getCloudStorage(forceDeepCheck: Boolean = false): CloudStorageInfo?
    suspend fun getRootDisplayName(rootId: String?): String
    suspend fun refreshCloudList(parentId: String?)
    suspend fun setCloudStorageRoot(rootId: String): CloudStorageInfo?
    suspend fun updateStorageLocation(folderId: String): String?

    fun isCached(parentId: String?): Boolean
    fun invalidateCache()

    companion object {
        const val ROOT_FOLDER = "Taihon"
        const val MIME_TYPE_FOLDER = "application/vnd.google-apps.folder"
    }
}

data class CloudStorageInfo(
    val rootId: String,
    val autoBackupFolderId: String,
)

data class CloudAccount(
    val email: String,
    val displayName: String?,
    val rawAccount: Any?,
)

data class CloudFile(
    val id: String,
    val name: String,
    val size: Long,
    val modifiedTime: Long,
    val mimeType: String? = null,
    val parents: List<String>? = null,
) {
    val isFolder: Boolean
        get() = mimeType == CloudService.MIME_TYPE_FOLDER
}

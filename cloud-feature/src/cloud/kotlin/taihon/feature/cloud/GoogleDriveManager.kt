package taihon.feature.cloud

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.googleapis.services.AbstractGoogleClientRequest
import com.google.api.client.http.FileContent
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import taihon.domain.preferences.TaihonPreferences
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class GoogleDriveManager(
    private val context: Context,
    taihonPreferences: TaihonPreferences,
) : CloudService {

    private val cloudClientId = context.getString(R.string.default_web_client_id)
    private val cloudPreferences = taihonPreferences.cloud
    private val credentialManager = CredentialManager.create(context)
    private val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Main + kotlinx.coroutines.SupervisorJob())

    private var driveService: Drive? = null
    private var resolvedStorage: CloudStorageInfo? = null
    private val rootNameCache = mutableMapOf<String, String>()
    private val listCache = mutableMapOf<String?, List<CloudFile>>()

    private val storageMutex = Mutex()
    private val marker = Marker(context.packageName)

    private val _accountState = MutableStateFlow<CloudAccount?>(null)
    override val accountState: StateFlow<CloudAccount?> = _accountState.asStateFlow()

    init {
        if (cloudClientId.isEmpty()) {
            logcat(LogPriority.ERROR) { "Google Cloud Client ID is missing! Cloud features will not work." }
        }

        if (cloudPreferences.cloudEnabled.get()) {
            val cachedEmail = cloudPreferences.cloudAccountEmail.get()
            val cachedName = cloudPreferences.cloudAccountName.get()
            if (cachedEmail.isNotEmpty()) {
                _accountState.update {
                    CloudAccount(
                        email = cachedEmail,
                        displayName = cachedName.takeIf { it.isNotEmpty() },
                        rawAccount = null,
                    )
                }
            }

            scope.launch {
                updateAccount()
            }
        }
    }

    // CloudService Implementation

    override fun isAvailable() = true

    override suspend fun signIn(activity: Activity) {
        try {
            val googleIdOption = buildGoogleIdOption(filterByAuthorizedAccounts = false)
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val result = credentialManager.getCredential(activity, request)

            val credential = toGoogleIdTokenCredential(result.credential)
                ?: throw IllegalStateException("Credential Manager returned non-Google credential")

            driveService = null
            authorizeDrive(activity)

            cloudPreferences.cloudAccountEmail.set(credential.id)
            cloudPreferences.cloudAccountName.set(credential.displayName ?: "")
            _accountState.update {
                CloudAccount(
                    email = credential.id,
                    displayName = credential.displayName,
                    rawAccount = credential,
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            when (e) {
                is NoCredentialException -> {
                    logcat(LogPriority.DEBUG, e) {
                        "No Google account available for sign-in"
                    }
                    context.toast("Please add a Google account to your device and try again", Toast.LENGTH_LONG)
                }

                else -> {
                    val message = when (e) {
                        is IllegalStateException ->
                            "Google Sign-In configuration error"

                        is GetCredentialException ->
                            "Failed to sign in with Credential Manager"

                        else ->
                            "Unexpected error during sign-in"
                    }
                    logcat(LogPriority.ERROR, e) { message }
                }
            }

            markAccountUnavailable()
        }
    }

    override fun signOut() {
        scope.launch {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to clear credential state" }
            }
        }
        invalidateCache()
        cloudPreferences.cloudStorageRootId.set("")
        cloudPreferences.cloudStorageRootName.set("")
        cloudPreferences.cloudStorageAutoBackupId.set("")
        cloudPreferences.cloudStorageLocationId.set("")
        cloudPreferences.cloudStorageLocationPath.set("")
        cloudPreferences.cloudAccountEmail.set("")
        cloudPreferences.cloudAccountName.set("")
        cloudPreferences.cloudEnabled.set(false)
        cloudPreferences.cloudAutoMirror.set(false)
        driveService = null
        _accountState.update { null }
    }

    override suspend fun updateAccount(activity: Activity?) {
        // Skip if already fully resolved and not forced via activity
        if (activity == null && accountState.value?.rawAccount != null) return
        if (!cloudPreferences.cloudEnabled.get()) return

        // If we're doing a background silent update and already have cached details,
        // we can rely on GoogleAccountCredential to refresh tokens as needed.
        // We only need to call GetCredentialRequest if we specifically want to
        // refresh the rawAccount object or forced via Activity UI.
        if (activity == null && accountState.value?.email?.isNotEmpty() == true) {
            return
        }

        try {
            val googleIdOption = buildGoogleIdOption(filterByAuthorizedAccounts = true)
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val credential = toGoogleIdTokenCredential(getCredentialWithReauth(request, activity))
                ?: throw IllegalStateException("Credential Manager returned non-Google credential")

            cloudPreferences.cloudAccountEmail.set(credential.id)
            cloudPreferences.cloudAccountName.set(credential.displayName ?: "")
            _accountState.update {
                CloudAccount(
                    email = credential.id,
                    displayName = credential.displayName,
                    rawAccount = credential,
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e

            val isSilent = activity == null
            if (isSilent && e is GetCredentialException) {
                logcat(LogPriority.DEBUG, e) { "Silent background account refresh failed" }
            } else {
                val message = when (e) {
                    is IllegalStateException -> "Google Sign-In configuration error"
                    is GetCredentialException -> "Failed to refresh Google account"
                    else -> "Unexpected error during account update"
                }
                logcat(LogPriority.ERROR, e) { message }
                if (!isSilent) markAccountUnavailable()
            }
        }
    }

    override suspend fun uploadFile(
        localFile: java.io.File,
        fileName: String,
        parentId: String?,
    ): String? = withDrive { drive ->
        if (!localFile.exists() || localFile.length() == 0L) {
            logcat(LogPriority.ERROR) { "Attempted to upload empty or non-existent file: ${localFile.absolutePath}" }
            return@withDrive null
        }
        val storage = getCloudStorageInternal(drive, forceDeepCheck = false) ?: return@withDrive null
        val folderId = parentId ?: storage.autoBackupFolderId

        val metadata = File().apply {
            name = fileName
            parents = listOf(folderId)
        }

        val content = FileContent("application/octet-stream", localFile)
        val createRequest = drive.files().create(metadata, content).setFields("id")
        createRequest.mediaHttpUploader?.apply {
            chunkSize = UPLOAD_CHUNK_SIZE
        }
        val file = createRequest.executeWithAuth()
        listCache.remove(folderId)
        file.id
    }

    override suspend fun downloadFile(fileId: String, localFile: java.io.File) {
        withDrive { drive ->
            localFile.outputStream().use { output ->
                val getRequest = drive.files()[fileId]
                getRequest.mediaHttpDownloader?.apply {
                    chunkSize = DOWNLOAD_CHUNK_SIZE
                }
                getRequest.executeMediaAndDownloadTo(output)
            }
            if (localFile.length() == 0L) {
                throw Exception("Downloaded file is empty")
            }
        }
    }

    override suspend fun deleteFile(fileId: String) {
        withDrive { drive ->
            drive.files().delete(fileId).executeWithAuth()
            listCache.clear()
        }
    }

    override suspend fun listFiles(parentId: String?): List<CloudFile> = withDrive { drive ->
        val effectiveParentId = parentId ?: cloudPreferences.cloudStorageRootId.get().ifEmpty { null }
        listCache[effectiveParentId]?.let { return@withDrive it }

        val files = listFilesInternal(drive, effectiveParentId)
            .filterNot { it.isValidMarker() }
            .toCloudFiles()
        listCache[effectiveParentId] = files
        files
    } ?: emptyList()

    override suspend fun getFile(fileId: String): CloudFile? = withDrive { drive ->
        drive.files().get(fileId)
            .setFields("id, name, parents, modifiedTime, size, mimeType")
            .executeWithAuth().toCloudEntry()
    }

    override suspend fun createFolder(name: String, parentId: String?): String? = withDrive { drive ->
        val effectiveParentId = parentId ?: getCloudStorageInternal(drive, forceDeepCheck = false)?.rootId
        createFolderInternal(drive, name, effectiveParentId)
    }

    override suspend fun getCloudStorage(forceDeepCheck: Boolean): CloudStorageInfo? = withDrive { drive ->
        if (forceDeepCheck) {
            storageMutex.withLock { resolvedStorage = null }
        }
        resolvedStorage ?: getCloudStorageInternal(drive, forceDeepCheck)
    }

    override suspend fun getRootDisplayName(rootId: String?): String = withContext(Dispatchers.IO) {
        if (rootId.isNullOrEmpty()) return@withContext CloudService.ROOT_FOLDER
        rootNameCache[rootId]?.let { return@withContext it }

        val storedRootId = cloudPreferences.cloudStorageRootId.get()
        if (storedRootId == rootId) {
            val prefName = cloudPreferences.cloudStorageRootName.get()
            if (prefName.isNotEmpty()) {
                rootNameCache[rootId] = prefName
                return@withContext prefName
            }
        }

        val drive = getDriveService() ?: return@withContext CloudService.ROOT_FOLDER
        val name = getFileInternal(drive, rootId)?.name ?: CloudService.ROOT_FOLDER
        rootNameCache[rootId] = name
        name
    }

    override suspend fun refreshCloudList(parentId: String?) {
        withDrive { drive ->
            val effectiveParentId = parentId ?: cloudPreferences.cloudStorageRootId.get().ifEmpty { null }

            val storedRootId = cloudPreferences.cloudStorageRootId.get()
            if (!effectiveParentId.isNullOrEmpty() && effectiveParentId == storedRootId) {
                refreshRootNameInternal(drive, effectiveParentId)
            }

            val files = listFilesInternal(drive, effectiveParentId).toCloudFiles()
            listCache[effectiveParentId] = files
        }
    }

    override suspend fun setCloudStorageRoot(rootId: String): CloudStorageInfo? = withContext(Dispatchers.IO) {
        val drive = getDriveService() ?: return@withContext null
        val root = getFileInternal(drive, rootId) ?: return@withContext null
        if (root.mimeType != CloudService.MIME_TYPE_FOLDER) return@withContext null
        rootNameCache[rootId] = root.name ?: CloudService.ROOT_FOLDER
        val autoBackupId = findFolder(drive, AUTOMATIC_BACKUPS_PATH, rootId)
            ?: createFolderInternal(drive, AUTOMATIC_BACKUPS_PATH, rootId)
            ?: return@withContext null
        val storage = CloudStorageInfo(rootId, autoBackupId)
        storageMutex.withLock {
            if (ensureMarker(drive, rootId) && isUsableStorage(drive, storage)) persistStorage(drive, storage) else null
        }
    }

    override suspend fun updateStorageLocation(folderId: String): String? = withContext(Dispatchers.IO) {
        val drive = getDriveService() ?: return@withContext null
        val autoBackupId = findFolder(drive, AUTOMATIC_BACKUPS_PATH, folderId)
            ?: createFolderInternal(drive, AUTOMATIC_BACKUPS_PATH, folderId)
            ?: return@withContext null

        cloudPreferences.cloudStorageAutoBackupId.set(autoBackupId)
        cloudPreferences.cloudStorageLocationId.set(folderId)

        storageMutex.withLock {
            resolvedStorage = null
        }

        folderId
    }

    override fun isCached(parentId: String?): Boolean {
        val effectiveParentId = parentId ?: cloudPreferences.cloudStorageRootId.get().ifEmpty { null }
        return listCache.containsKey(effectiveParentId)
    }

    override fun invalidateCache() {
        listCache.clear()
        rootNameCache.clear()
        resolvedStorage = null
    }

    // Auth helpers

    private fun buildGoogleIdOption(filterByAuthorizedAccounts: Boolean): GetGoogleIdOption {
        return GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(filterByAuthorizedAccounts)
            .setAutoSelectEnabled(true)
            .setServerClientId(cloudClientId)
            .build()
    }

    private fun toGoogleIdTokenCredential(credential: Credential): GoogleIdTokenCredential? {
        return when (credential) {
            is GoogleIdTokenCredential -> credential
            is CustomCredential -> {
                if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    null
                } else {
                    try {
                        GoogleIdTokenCredential.createFrom(credential.data)
                    } catch (e: GoogleIdTokenParsingException) {
                        logcat(LogPriority.ERROR, e) { "Failed to parse Google ID credential payload" }
                        null
                    }
                }
            }

            else -> null
        }
    }

    private suspend fun getCredentialWithReauth(
        request: GetCredentialRequest,
        activity: Activity?,
    ): Credential {
        return try {
            credentialManager.getCredential(context, request).credential
        } catch (e: GetCredentialException) {
            if (activity == null) {
                logcat(LogPriority.DEBUG, e) { "Silent background refresh pending UI or transient error" }
                throw e
            }

            if (!cloudPreferences.cloudEnabled.get()) throw e

            logcat(LogPriority.INFO, e) { "Silent account refresh failed, retrying with Activity re-auth" }
            credentialManager.getCredential(activity, request).credential
        }
    }

    private suspend fun authorizeDrive(activity: Activity) {
        val requestedScopes = listOf(Scope(DriveScopes.DRIVE_FILE))
        val authorizationRequest = AuthorizationRequest.builder()
            .setRequestedScopes(requestedScopes)
            .build()

        try {
            val result = Identity.getAuthorizationClient(activity)
                .authorize(authorizationRequest)
                .awaitTask()

            if (result.hasResolution() && result.pendingIntent != null) {
                if (activity !is ComponentActivity) {
                    activity.startIntentSenderForResult(
                        result.pendingIntent!!.intentSender,
                        AUTH_REQUEST_CODE,
                        null,
                        0,
                        0,
                        0,
                    )
                    return
                }

                suspendCancellableCoroutine<Unit> { continuation ->
                    val registry = activity.activityResultRegistry
                    val key = "google_drive_auth_${System.currentTimeMillis()}"
                    var launcher: androidx.activity.result.ActivityResultLauncher<IntentSenderRequest>? = null
                    launcher = registry.register(
                        key,
                        ActivityResultContracts.StartIntentSenderForResult(),
                    ) { activityResult ->
                        launcher?.unregister()
                        if (activityResult.resultCode == Activity.RESULT_OK) {
                            continuation.resume(Unit)
                        } else {
                            continuation.resumeWithException(Exception("Google Drive authorization failed"))
                        }
                    }
                    launcher.launch(IntentSenderRequest.Builder(result.pendingIntent!!.intentSender).build())

                    continuation.invokeOnCancellation {
                        launcher.unregister()
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logcat(LogPriority.ERROR, e) { "Failed to authorize Google Drive" }
            throw e
        }
    }

    private fun markAccountUnavailable() {
        driveService = null
        cloudPreferences.cloudEnabled.set(false)
        cloudPreferences.cloudAutoMirror.set(false)
        _accountState.update { null }
    }

    // Drive API Helpers

    private fun getDriveService(): Drive? {
        val currentAccount = accountState.value ?: return null
        if (driveService != null) return driveService

        val email = currentAccount.email
        if (email.isEmpty()) return null

        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_FILE),
        ).apply {
            // Manually construct the Account object to avoid IllegalArgumentException in legacy SDK
            selectedAccount = android.accounts.Account(email, "com.google")
        }

        val requestInitializer = HttpRequestInitializer { request ->
            credential.initialize(request)
            request.connectTimeout = CONNECT_TIMEOUT_MS
            request.readTimeout = READ_TIMEOUT_MS
        }

        driveService = Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), requestInitializer)
            .setApplicationName(context.stringResource(MR.strings.app_name))
            .build()

        return driveService
    }

    private fun listFilesInternal(drive: Drive, parentId: String?, mimeType: String? = null): List<File> {
        val q = mutableListOf("trashed = false")
        q.add(if (parentId != null) "'$parentId' in parents" else "'root' in parents")
        if (mimeType != null) q.add("mimeType = '$mimeType'")
        return fetchDriveFiles(drive, q.joinToString(" and "))
    }

    private fun fetchDriveFiles(
        drive: Drive,
        query: String,
        fields: String = "nextPageToken, files(id, name, modifiedTime, size, mimeType, appProperties, parents)",
    ): List<File> {
        val files = mutableListOf<File>()
        var pageToken: String? = null
        do {
            val result = drive.files().list()
                .setQ(query)
                .setFields(fields)
                .setPageToken(pageToken)
                .executeWithAuth()
            files.addAll(result.files.orEmpty())
            pageToken = result.nextPageToken
        } while (pageToken != null)
        return files
    }

    // Storage Resolution

    private suspend fun getCloudStorageInternal(drive: Drive, forceDeepCheck: Boolean): CloudStorageInfo? =
        storageMutex.withLock {
            resolvedStorage?.let { return@withLock it }

            val savedStorage = CloudStorageInfo(
                rootId = cloudPreferences.cloudStorageRootId.get(),
                autoBackupFolderId = cloudPreferences.cloudStorageAutoBackupId.get(),
            )

            // Branch 1: Light Verification (Background)
            if (!forceDeepCheck && savedStorage.rootId.isNotEmpty() && savedStorage.autoBackupFolderId.isNotEmpty()) {
                val currentLocationId = cloudPreferences.cloudStorageLocationId.get()
                if (isUsableStorage(drive, savedStorage, currentLocationId.takeIf { it.isNotEmpty() }) &&
                    ensureMarker(drive, savedStorage.rootId)
                ) {
                    resolvedStorage = savedStorage
                    return@withLock savedStorage
                }
            }

            // Branch 2: Deep Verification & Repair (Foreground / Error Fallback)
            val accessibleFolders = try {
                fetchDriveFiles(
                    drive,
                    "mimeType = '${CloudService.MIME_TYPE_FOLDER}' and trashed = false",
                    "nextPageToken, files(id, name, modifiedTime, parents)",
                )
            } catch (e: Exception) {
                if (e is CancellationException || e is UserRecoverableAuthIOException) throw e
                logcat(LogPriority.ERROR, e) { "Failed to list accessible folders for storage resolution" }
                emptyList()
            }

            val accessibleIds = accessibleFolders.map { it.id }.toSet()

            if (savedStorage.rootId.isNotEmpty() && savedStorage.autoBackupFolderId.isNotEmpty()) {
                val currentLocationId = cloudPreferences.cloudStorageLocationId.get()

                if (currentLocationId.isNotEmpty() && currentLocationId in accessibleIds) {
                    val highestId = findHighestAccessibleAncestor(currentLocationId, accessibleFolders)
                    if (highestId != savedStorage.rootId) {
                        if (ensureMarker(drive, highestId)) {
                            val autoBackupId =
                                findFolder(drive, AUTOMATIC_BACKUPS_PATH, currentLocationId)
                                    ?: createFolderInternal(
                                        drive,
                                        AUTOMATIC_BACKUPS_PATH,
                                        currentLocationId,
                                    )
                            if (autoBackupId != null) {
                                val newStorage = CloudStorageInfo(highestId, autoBackupId)
                                if (isUsableStorage(drive, newStorage, currentLocationId)) {
                                    cleanupRedundantMarkers(drive, highestId)
                                    return@withLock persistStorage(drive, newStorage)
                                }
                            }
                        }
                    }
                }

                if (isUsableStorage(drive, savedStorage, currentLocationId.takeIf { it.isNotEmpty() }) &&
                    ensureMarker(drive, savedStorage.rootId)
                ) {
                    resolvedStorage = savedStorage
                    if (!rootNameCache.containsKey(savedStorage.rootId)) {
                        refreshRootNameInternal(drive, savedStorage.rootId)
                    }
                    return@withLock savedStorage
                }
            }

            recoverStorage(drive, accessibleFolders)?.let {
                resolvedStorage = it
                refreshRootNameInternal(drive, it.rootId)
                return@withLock it
            }

            val appName = context.stringResource(MR.strings.app_name)
            val suffix = context.packageName.substringAfterLast("app.taihon", "")
            val baseName = "$appName$suffix"
            val rootId = findFolder(drive, baseName) ?: createFolderInternal(drive, baseName)
            if (rootId != null) {
                val autoBackupId = findFolder(drive, AUTOMATIC_BACKUPS_PATH, rootId)
                    ?: createFolderInternal(drive, AUTOMATIC_BACKUPS_PATH, rootId)
                if (autoBackupId != null && ensureMarker(drive, rootId)) {
                    val storage = CloudStorageInfo(rootId, autoBackupId)
                    if (isUsableStorage(drive, storage)) {
                        return@withLock persistStorage(drive, storage)
                    }
                }
            }

            logcat(LogPriority.ERROR) { "Unable to initialize cloud storage in $baseName" }
            null
        }

    private fun recoverStorage(drive: Drive, accessibleFolders: List<File>): CloudStorageInfo? {
        val markers = try {
            fetchDriveFiles(
                drive,
                "name = '${marker.fileName}' and trashed = false",
                "nextPageToken, files(id, name, parents, appProperties)",
            )
        } catch (e: Exception) {
            if (e is CancellationException || e is UserRecoverableAuthIOException) throw e
            logcat(LogPriority.ERROR, e) { "Failed to list storage markers" }
            emptyList()
        }

        if (markers.isEmpty() && accessibleFolders.isEmpty()) return null

        val accessibleIds = accessibleFolders.map { it.id }.toSet()

        fun getDepth(folderId: String, visited: Set<String> = emptySet()): Int {
            if (folderId in visited) return 100
            val folder = accessibleFolders.firstOrNull { it.id == folderId } ?: return 0
            val parentId = folder.parents?.firstOrNull() ?: return 0
            if (parentId !in accessibleIds) return 0
            return 1 + getDepth(parentId, visited + folderId)
        }

        val markedFolderIds = markers.filter { it.isValidMarker() }
            .flatMap { it.parents.orEmpty() }.toSet()

        val branchRoots = (markedFolderIds.takeIf { it.isNotEmpty() } ?: accessibleIds).map { id ->
            findHighestAccessibleAncestor(id, accessibleFolders)
        }.distinct().mapNotNull { id -> accessibleFolders.firstOrNull { it.id == id } }

        val finalRoot = branchRoots
            .sortedWith(
                compareBy<File> { getDepth(it.id) }
                    .thenByDescending { it.modifiedTime?.value ?: 0L },
            )
            .firstOrNull() ?: return null

        val rootId = finalRoot.id
        ensureMarker(drive, rootId)
        cleanupRedundantMarkers(drive, rootId)

        val autoBackupId = findFolder(drive, AUTOMATIC_BACKUPS_PATH, rootId)
            ?: createFolderInternal(drive, AUTOMATIC_BACKUPS_PATH, rootId)
        if (autoBackupId != null) {
            val storage = CloudStorageInfo(rootId, autoBackupId)
            if (isUsableStorage(drive, storage)) return persistStorage(drive, storage)
        }

        return null
    }

    private fun findHighestAccessibleAncestor(folderId: String, accessibleFolders: List<File>): String {
        val accessibleIds = accessibleFolders.map { it.id }.toSet()
        var currentId = folderId
        while (true) {
            val folder = accessibleFolders.firstOrNull { it.id == currentId } ?: break
            val parentId = folder.parents?.firstOrNull() ?: break
            if (parentId !in accessibleIds) break
            currentId = parentId
        }
        return currentId
    }

    private fun cleanupRedundantMarkers(drive: Drive, rootId: String) {
        try {
            val markers = drive.files().list()
                .setQ("name = '${marker.fileName}' and trashed = false")
                .setFields("files(id, parents, appProperties)")
                .executeWithAuth().files.orEmpty()

            markers.filter { it.isValidMarker() }.forEach { markerFile ->
                if (!markerFile.parents.orEmpty().contains(rootId)) {
                    try {
                        drive.files().delete(markerFile.id).executeWithAuth()
                    } catch (e: Exception) {
                        if (e is UserRecoverableAuthIOException) throw e
                    }
                }
            }
        } catch (e: UserRecoverableAuthIOException) {
            throw e
        } catch (_: Exception) {
            // Ignore other errors during cleanup
        }
    }

    private fun ensureMarker(drive: Drive, rootId: String): Boolean {
        val existingMarker = listFilesInternal(drive, rootId).firstOrNull { it.isValidMarker() }
        return existingMarker != null || try {
            drive.files().create(
                File().apply {
                    name = marker.fileName
                    mimeType = Marker.MIME_TYPE
                    parents = listOf(rootId)
                    appProperties = mapOf(marker.property to Marker.VALUE)
                },
                com.google.api.client.http.ByteArrayContent(
                    Marker.MIME_TYPE,
                    context.stringResource(MR.strings.cloud_marker_content, context.stringResource(MR.strings.app_name))
                        .toByteArray(),
                ),
            ).setFields("id").executeWithAuth().id != null
        } catch (e: Exception) {
            if (e is CancellationException || e is UserRecoverableAuthIOException) throw e
            logcat(LogPriority.ERROR, e) { "Unable to create cloud storage marker" }
            false
        }
    }

    private fun isUsableStorage(drive: Drive, storage: CloudStorageInfo, expectedParentId: String? = null): Boolean {
        val root = getFileInternal(drive, storage.rootId) ?: return false
        val autoBackup = getFileInternal(drive, storage.autoBackupFolderId) ?: return false
        val parentToCheck = expectedParentId ?: storage.rootId
        return root.mimeType == CloudService.MIME_TYPE_FOLDER &&
            autoBackup.mimeType == CloudService.MIME_TYPE_FOLDER &&
            autoBackup.parents.orEmpty().contains(parentToCheck) &&
            root.trashed != true &&
            autoBackup.trashed != true
    }

    private fun getFileInternal(drive: Drive, fileId: String): File? = try {
        drive.files().get(fileId)
            .setFields("id, name, parents, modifiedTime, size, mimeType, trashed")
            .executeWithAuth()
    } catch (e: Exception) {
        if (e is CancellationException || e is UserRecoverableAuthIOException) throw e
        null
    }

    private fun persistStorage(drive: Drive, storage: CloudStorageInfo): CloudStorageInfo {
        val oldRootId = cloudPreferences.cloudStorageRootId.get()
        val oldLocationId = cloudPreferences.cloudStorageLocationId.get()

        resolvedStorage = storage
        cloudPreferences.cloudStorageRootId.set(storage.rootId)
        val savedName = rootNameCache[storage.rootId] ?: CloudService.ROOT_FOLDER
        cloudPreferences.cloudStorageRootName.set(savedName)
        cloudPreferences.cloudStorageAutoBackupId.set(storage.autoBackupFolderId)

        if (oldRootId != storage.rootId || oldLocationId.isEmpty()) {
            cloudPreferences.cloudStorageLocationId.set(storage.rootId)
            cloudPreferences.cloudStorageLocationPath.set("")
        } else {
            val locationFile = getFileInternal(drive, oldLocationId)
            if (locationFile == null || locationFile.trashed == true) {
                cloudPreferences.cloudStorageLocationId.set(storage.rootId)
                cloudPreferences.cloudStorageLocationPath.set("")
            }
        }
        return storage
    }

    private fun refreshRootNameInternal(drive: Drive, rootId: String) {
        try {
            val rootFile = getFileInternal(drive, rootId)
            val driveName = rootFile?.name ?: CloudService.ROOT_FOLDER
            val prefName = cloudPreferences.cloudStorageRootName.get()
            if (prefName != driveName) {
                cloudPreferences.cloudStorageRootName.set(driveName)
            }
            rootNameCache[rootId] = driveName
        } catch (e: Exception) {
            if (e is CancellationException || e is UserRecoverableAuthIOException) throw e
            logcat(LogPriority.ERROR, e) { "Failed to refresh cloud root name" }
        }
    }

    private fun createFolderInternal(drive: Drive, name: String, parentId: String? = null): String? {
        val metadata = File().apply {
            this.name = name
            mimeType = CloudService.MIME_TYPE_FOLDER
            if (parentId != null) parents = listOf(parentId)
        }
        return try {
            drive.files().create(metadata).setFields("id").executeWithAuth().id.also {
                listCache.remove(parentId)
            }
        } catch (e: Exception) {
            if (e is CancellationException || e is UserRecoverableAuthIOException) throw e
            logcat(LogPriority.ERROR, e) { "Failed to create folder: $name" }
            null
        }
    }

    private fun findFolder(drive: Drive, name: String, parentId: String? = null): String? {
        val escapedName = name.replace("\\", "\\\\").replace("'", "\\'")
        val q = mutableListOf(
            "name = '$escapedName'",
            "mimeType = '${CloudService.MIME_TYPE_FOLDER}'",
            "trashed = false",
        )
        q.add(if (parentId != null) "'$parentId' in parents" else "'root' in parents")
        return try {
            val result = drive.files().list()
                .setQ(q.joinToString(" and "))
                .setFields("files(id)")
                .executeWithAuth()
            result.files?.firstOrNull()?.id
        } catch (e: Exception) {
            if (e is CancellationException || e is UserRecoverableAuthIOException) throw e
            logcat(LogPriority.ERROR, e) { "Failed to find folder: $name" }
            null
        }
    }

    private class Marker(packageName: String) {
        val fileName = ".${packageName.removePrefix("app.")}-marker"
        val property = "${packageName.replace(".", "_")}_cloud_storage_marker_property"

        companion object {
            const val VALUE = "1"
            const val MIME_TYPE = "text/plain"
        }
    }

    // Extension Functions

    private suspend fun <T> withDrive(
        action: suspend (Drive) -> T,
    ): T? = withContext(Dispatchers.IO) {
        val drive = getDriveService() ?: return@withContext null
        try {
            action(drive)
        } catch (e: Exception) {
            when (e) {
                is CancellationException, is UserRecoverableAuthIOException -> throw e
                else -> {
                    logcat(LogPriority.ERROR, e)
                    null
                }
            }
        }
    }

    private fun <T> AbstractGoogleClientRequest<T>.executeWithAuth(): T {
        var attempts = 0
        while (true) {
            try {
                return execute()
            } catch (e: UserRecoverableAuthIOException) {
                markAccountUnavailable()
                throw e
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                val isTransientNetworkError = e is IOException
                if (isTransientNetworkError && attempts < 2) {
                    attempts++
                    logcat(LogPriority.WARN, e) {
                        "Transient network error executing Google Drive request; retrying attempt $attempts/2"
                    }
                    try {
                        Thread.sleep(1000L * attempts)
                    } catch (_: InterruptedException) {
                        throw e
                    }
                } else {
                    throw e
                }
            }
        }
    }

    @Suppress("UsePropertyAccessSyntax")
    private fun File.toCloudEntry() = CloudFile(
        id = id,
        name = name,
        size = getSize() ?: 0L,
        modifiedTime = modifiedTime?.value ?: 0L,
        mimeType = mimeType,
        parents = parents,
    )

    private fun List<File>.toCloudFiles(): List<CloudFile> {
        return this.map { it.toCloudEntry() }
            .sortedWith(
                compareByDescending<CloudFile> { it.isFolder }
                    .thenByDescending { it.modifiedTime }
                    .thenBy { it.name },
            )
    }

    private fun File.isValidMarker(): Boolean {
        return marker.fileName == name && Marker.VALUE == appProperties?.get(marker.property)
    }

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T {
        return suspendCancellableCoroutine { cont ->
            addOnCompleteListener { task ->
                val exception = task.exception
                if (exception != null) {
                    cont.resumeWithException(exception)
                } else if (task.isCanceled) {
                    cont.cancel()
                } else {
                    cont.resume(task.result)
                }
            }
        }
    }

    // endregion

    companion object {
        private const val AUTH_REQUEST_CODE = 1001
        const val AUTOMATIC_BACKUPS_PATH = "autobackup"
        private const val CONNECT_TIMEOUT_MS = 60_000 // 60s connect timeout
        private const val READ_TIMEOUT_MS = 120_000 // 120s read timeout (matches upstream network timeouts)
        private const val UPLOAD_CHUNK_SIZE = 2 * 1024 * 1024 // 2 MiB chunks for reliable uploads
        private const val DOWNLOAD_CHUNK_SIZE = 2 * 1024 * 1024 // 2 MiB chunks for downloads
    }
}

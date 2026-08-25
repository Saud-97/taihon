package taihon.feature.cloud

import android.content.Context

fun CloudService(context: Context, cloudClientId: String): CloudService {
    return GoogleDriveManager(context, cloudClientId = cloudClientId)
}

package fr.vpm.yag.device.model

import android.net.Uri
import java.util.*

class DeviceMedia(
    var id: Long = 0,
    var uri: Uri? = null,
    var path: String? = null,
    var displayName: String? = null,
    var additionDate: Date? = null,
    var bucketId: String? = null,
    var bucketName: String? = null
) : Photo
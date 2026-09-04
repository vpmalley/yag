package fr.vpm.yag.device.model

/**
 * Checks if a photo has a RAW file extension.
 * Supported RAW extensions: .arw, .cr2, .cr3, .nef, .raf, .dng, .rw2, .raw
 */
fun Photo.isRawPhoto(): Boolean {
    val devicePhoto = this as? DevicePhoto
    val path = devicePhoto?.path?.lowercase() ?: return false
    return path.endsWith(".arw", ignoreCase = true) ||
            path.endsWith(".cr2", ignoreCase = true) ||
            path.endsWith(".cr3", ignoreCase = true) ||
            path.endsWith(".nef", ignoreCase = true) ||
            path.endsWith(".raf", ignoreCase = true) ||
            path.endsWith(".dng", ignoreCase = true) ||
            path.endsWith(".rw2", ignoreCase = true) ||
            path.endsWith(".raw", ignoreCase = true)
}

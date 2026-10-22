package fr.vpm.yag.utils

import android.content.Context
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import java.io.FileNotFoundException
import java.io.InputStream

/**
 * Utility class for extracting EXIF metadata from images.
 */
object ExifUtils {

    private const val TAG = "ExifUtils"

    /**
     * Extracts EXIF data from an image URI.
     */
    fun getExifData(context: Context, uri: Uri): ExifData {
        val exifData = ExifData()
        
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                try {
                    val exifInterface = ExifInterface(inputStream)
                    extractExifData(exifInterface, exifData)
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading EXIF from stream", e)
                } finally {
                    inputStream.close()
                }
            } else {
                // Try to get the path from the URI and read directly
                val path = uri.path
                if (path != null) {
                    try {
                        val exifInterface = ExifInterface(path)
                        extractExifData(exifInterface, exifData)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reading EXIF from path: $path", e)
                    }
                }
            }
        } catch (e: FileNotFoundException) {
            Log.e(TAG, "File not found for URI: $uri", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting EXIF data", e)
        }
        
        return exifData
    }

    /**
     * Extracts EXIF data from a file path.
     */
    fun getExifDataFromPath(filePath: String): ExifData {
        val exifData = ExifData()
        
        try {
            val exifInterface = ExifInterface(filePath)
            extractExifData(exifInterface, exifData)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading EXIF from path: $filePath", e)
        }
        
        return exifData
    }

    /**
     * Extracts EXIF data from an ExifInterface.
     */
    private fun extractExifData(exifInterface: ExifInterface, exifData: ExifData) {
        try {
            // Get focal length (actual, not 35mm equivalent)
            val focalLength = exifInterface.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)
            if (focalLength != null) {
                exifData.focalLength = parseFocalLength(focalLength)
                exifData.focalLengthRaw = focalLength
            }

            // Get shutter speed
            val exposureTime = exifInterface.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)
            if (exposureTime != null) {
                exifData.shutterSpeed = parseShutterSpeed(exposureTime)
                exifData.shutterSpeedRaw = exposureTime
            }

            // Get aperture
            val aperture = exifInterface.getAttribute(ExifInterface.TAG_APERTURE)
            if (aperture != null) {
                exifData.aperture = parseAperture(aperture)
                exifData.apertureRaw = aperture
            }

            // Get ISO
            val iso = exifInterface.getAttribute(ExifInterface.TAG_ISO)
            if (iso != null) {
                exifData.iso = parseIso(iso)
                exifData.isoRaw = iso
            }

            // Get date taken - try multiple tags with fallback chain
            var dateTaken: String? = null
            val dateTags = listOf(
                ExifInterface.TAG_DATETIME_ORIGINAL,
                ExifInterface.TAG_DATETIME_DIGITIZED,
                ExifInterface.TAG_DATETIME
            )
            for (tag in dateTags) {
                dateTaken = exifInterface.getAttribute(tag)
                if (dateTaken != null) {
                    exifData.dateTaken = parseDateTaken(dateTaken)
                    exifData.dateTakenRaw = dateTaken
                    break
                }
            }

            // Get camera make and model
            val cameraMake = exifInterface.getAttribute(ExifInterface.TAG_MAKE)
            val cameraModel = exifInterface.getAttribute(ExifInterface.TAG_MODEL)
            val cameraModelParsed = parseCameraModel(cameraMake, cameraModel)
            if (cameraModelParsed != "-") {
                exifData.cameraModel = cameraModelParsed
                exifData.cameraModelRaw = cameraMake ?: cameraModel
            }

            // Get lens model
            val lensMake = exifInterface.getAttribute("LensMake")
            val lensModelTag = exifInterface.getAttribute("LensModel")
            val lensModelParsed = parseLensModel(lensMake, lensModelTag)
            if (lensModelParsed != "-") {
                exifData.lensModel = lensModelParsed
                exifData.lensModelRaw = lensMake ?: lensModelTag
            }

            // Get all EXIF tags - use a predefined list of common tags
            exifData.allTags = mutableMapOf()
            val commonTags = listOf(
                ExifInterface.TAG_FOCAL_LENGTH,
                ExifInterface.TAG_EXPOSURE_TIME,
                ExifInterface.TAG_APERTURE,
                ExifInterface.TAG_ISO,
                ExifInterface.TAG_DATETIME,
                ExifInterface.TAG_DATETIME_ORIGINAL,
                ExifInterface.TAG_DATETIME_DIGITIZED,
                ExifInterface.TAG_MAKE,
                ExifInterface.TAG_MODEL,
                "LensMake",
                "LensModel",
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.TAG_WHITE_BALANCE,
                ExifInterface.TAG_FLASH,
                ExifInterface.TAG_EXPOSURE_MODE,
                ExifInterface.TAG_METERING_MODE,
                ExifInterface.TAG_LIGHT_SOURCE,
                ExifInterface.TAG_COLOR_SPACE,
                ExifInterface.TAG_IMAGE_WIDTH,
                ExifInterface.TAG_IMAGE_LENGTH
            )
            
            commonTags.forEach { tag ->
                try {
                    val value = exifInterface.getAttribute(tag)
                    if (value != null) {
                        exifData.allTags[tag] = value
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Error reading tag: $tag", e)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error extracting EXIF data", e)
        }
    }

    /**
     * Parses focal length from EXIF format (e.g., "4200/100" -> "42.0mm")
     */
    private fun parseFocalLength(focalLength: String): String {
        return try {
            if (focalLength.contains("/")) {
                val parts = focalLength.split("/")
                if (parts.size == 2) {
                    val numerator = parts[0].toDouble()
                    val denominator = parts[1].toDouble()
                    val result = numerator / denominator
                    "${String.format("%.1f", result)}mm"
                } else {
                    focalLength
                }
            } else {
                focalLength
            }
        } catch (e: Exception) {
            focalLength
        }
    }

    /**
     * Parses shutter speed from EXIF format (e.g., "1/250" -> "1/250s")
     */
    private fun parseShutterSpeed(exposureTime: String): String {
        return try {
            if (exposureTime.contains("/")) {
                val parts = exposureTime.split("/")
                if (parts.size == 2) {
                    val numerator = parts[0].toDouble()
                    val denominator = parts[1].toDouble()
                    
                    // If numerator is 1, show as fraction
                    if (numerator == 1.0) {
                        "1/${denominator.toInt()}s"
                    } else {
                        // Calculate decimal value
                        val result = numerator / denominator
                        if (result >= 1.0) {
                            "${String.format("%.1f", result)}s"
                        } else {
                            "1/${Math.round(1.0 / result)}s"
                        }
                    }
                } else {
                    exposureTime
                }
            } else {
                // Handle decimal values
                val value = exposureTime.toDoubleOrNull() ?: 0.0
                if (value >= 1.0) {
                    "${String.format("%.1f", value)}s"
                } else if (value > 0) {
                    "1/${Math.round(1.0 / value)}s"
                } else {
                    exposureTime
                }
            }
        } catch (e: Exception) {
            exposureTime
        }
    }

    /**
     * Parses aperture from EXIF format (e.g., "28/10" -> "f/2.8")
     */
    private fun parseAperture(aperture: String): String {
        return try {
            if (aperture.contains("/")) {
                val parts = aperture.split("/")
                if (parts.size == 2) {
                    val numerator = parts[0].toDouble()
                    val denominator = parts[1].toDouble()
                    val result = numerator / denominator
                    "f/${String.format("%.1f", result)}"
                } else {
                    aperture
                }
            } else {
                "f/$aperture"
            }
        } catch (e: Exception) {
            aperture
        }
    }

    /**
     * Parses ISO value from EXIF format
     */
    private fun parseIso(iso: String): String {
        return try {
            val isoValue = iso.toIntOrNull()
            if (isoValue != null) {
                isoValue.toString()
            } else {
                iso
            }
        } catch (e: Exception) {
            iso
        }
    }

    /**
     * Parses date taken from EXIF format (e.g., "2024:10:15 14:30:45" -> "Oct 15, 2024 14:30")
     * Tries multiple date tags with fallback chain: DATETIME_ORIGINAL -> DATETIME_DIGITIZED -> DATETIME
     */
    private fun parseDateTaken(dateString: String): String {
        return try {
            // EXIF date format: "yyyy:MM:dd HH:mm:ss"
            val parts = dateString.split(":", " ")
            if (parts.size >= 6) {
                val year = parts[0]
                val month = parts[1].toInt()
                val day = parts[2].toInt()
                val hour = parts[3]
                val minute = parts[4]

                val monthNames = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", 
                    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                val monthName = if (month in 1..12) monthNames[month - 1] else "???"

                "$monthName $day, $year $hour:$minute"
            } else {
                dateString
            }
        } catch (e: Exception) {
            dateString
        }
    }

    /**
     * Parses camera model from EXIF format
     * Combines TAG_MAKE and TAG_MODEL if both are available
     */
    private fun parseCameraModel(make: String?, model: String?): String {
        return when {
            make != null && model != null -> "$make $model"
            make != null -> make
            model != null -> model
            else -> "-"
        }
    }

    /**
     * Parses lens model from EXIF format
     * Tries to combine TAG_LENS_MAKE and TAG_LENS_MODEL if both are available
     */
    private fun parseLensModel(lensMake: String?, lensModel: String?): String {
        return when {
            lensMake != null && lensModel != null -> "$lensMake $lensModel"
            lensMake != null -> lensMake
            lensModel != null -> lensModel
            else -> "-"
        }
    }

    /**
     * Gets a list of EXIF tag names that are present in the image.
     */
    fun getExifTagList(exifData: ExifData): List<String> {
        val tags = mutableListOf<String>()
        
        // Add standard tags
        if (exifData.focalLengthRaw != null) tags.add("FocalLength")
        if (exifData.shutterSpeedRaw != null) tags.add("ExposureTime")
        if (exifData.apertureRaw != null) tags.add("Aperture")
        if (exifData.isoRaw != null) tags.add("ISO")
        
        // Add other common tags from allTags
        val commonTags = setOf(
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.TAG_WHITE_BALANCE,
            ExifInterface.TAG_FLASH,
            ExifInterface.TAG_EXPOSURE_MODE,
            ExifInterface.TAG_METERING_MODE,
            ExifInterface.TAG_LIGHT_SOURCE,
            ExifInterface.TAG_COLOR_SPACE
        )
        
        for ((tag, _) in exifData.allTags) {
            if (tag in commonTags && tag !in tags) {
                tags.add(tag.substringAfterLast("TAG_"))
            }
        }
        
        return tags
    }

    /**
     * Data class to hold EXIF information.
     */
    data class ExifData(
        var focalLength: String = "-",
        var focalLengthRaw: String? = null,
        var shutterSpeed: String = "-",
        var shutterSpeedRaw: String? = null,
        var aperture: String = "-",
        var apertureRaw: String? = null,
        var iso: String = "-",
        var isoRaw: String? = null,
        var dateTaken: String = "-",
        var dateTakenRaw: String? = null,
        var cameraModel: String = "-",
        var cameraModelRaw: String? = null,
        var lensModel: String = "-",
        var lensModelRaw: String? = null,
        var allTags: MutableMap<String, String> = mutableMapOf()
    )
}

package ch.rex.photocollagewallpaper.domain

import java.util.Locale

data class ImageCandidate(
    val name: String?,
    val mimeType: String?,
    val isFile: Boolean,
    val canRead: Boolean,
)

object ImageFileFilter {
    private val supportedExtensions = setOf(
        "jpg",
        "jpeg",
        "png",
        "webp",
        "heic",
        "heif",
    )

    private val supportedMimeTypes = setOf(
        "image/jpeg",
        "image/jpg",
        "image/png",
        "image/webp",
        "image/heic",
        "image/heif",
    )

    fun isSupported(
        name: String?,
        mimeType: String?,
    ): Boolean {
        val normalizedMimeType = mimeType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.ROOT)
        if (normalizedMimeType in supportedMimeTypes) {
            return true
        }

        val extension = name
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase(Locale.ROOT)
        return extension in supportedExtensions
    }

    fun keepReadableImages(candidates: List<ImageCandidate>): List<ImageCandidate> =
        candidates.filter { candidate ->
            candidate.isFile &&
                candidate.canRead &&
                isSupported(candidate.name, candidate.mimeType)
        }
}

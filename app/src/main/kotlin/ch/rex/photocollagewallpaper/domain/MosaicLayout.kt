package ch.rex.photocollagewallpaper.domain

/**
 * Asymmetric layouts used by the wallpaper.
 *
 * The enum order is intentionally stable because the selected layout is persisted in
 * DataStore. The visual direction also defines the order in which cells are revealed:
 * the large region first, followed by the smaller regions on the opposite side.
 */
enum class MosaicLayout(
    val photoCount: Int,
) {
    THREE_LARGE_TOP(photoCount = 3),
    THREE_LARGE_BOTTOM(photoCount = 3),
    THREE_LARGE_LEFT(photoCount = 3),
    THREE_LARGE_RIGHT(photoCount = 3),
    ;

    companion object {
        val initial: MosaicLayout = THREE_LARGE_TOP

        fun fromStoredValue(value: String?): MosaicLayout =
            entries.firstOrNull { it.name == value } ?: initial
    }
}

object ProgressiveMosaicPolicy {
    const val PHOTO_COUNT = 3

    fun isComplete(
        layout: MosaicLayout,
        revealedPhotoCount: Int,
    ): Boolean = revealedPhotoCount >= layout.photoCount
}

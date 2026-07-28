package ch.rex.photocollagewallpaper.data

import ch.rex.photocollagewallpaper.domain.MosaicLayout

const val MILLIS_PER_MINUTE = 60L * 1_000L
const val MIN_INTERVAL_MINUTES = 1
const val MAX_INTERVAL_MINUTES = 60
const val DEFAULT_INTERVAL_MINUTES = 5
const val DEFAULT_INTERVAL_MILLIS = DEFAULT_INTERVAL_MINUTES * MILLIS_PER_MINUTE

data class AppSettings(
    val folderUri: String? = null,
    val gapDp: Float = 2f,
    val backgroundArgb: Long = 0xFF000000L,
    val fadeEnabled: Boolean = true,
    val refreshToken: Long = 0L,
    val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
    val lastMosaicLayout: MosaicLayout = MosaicLayout.initial,
    val lastPhotoUris: List<String> = emptyList(),
)

fun normalizeIntervalMillis(value: Long): Long = value.coerceIn(
    minimumValue = MIN_INTERVAL_MINUTES * MILLIS_PER_MINUTE,
    maximumValue = MAX_INTERVAL_MINUTES * MILLIS_PER_MINUTE,
)

fun intervalMinutes(value: Long): Int =
    (normalizeIntervalMillis(value) / MILLIS_PER_MINUTE).toInt()

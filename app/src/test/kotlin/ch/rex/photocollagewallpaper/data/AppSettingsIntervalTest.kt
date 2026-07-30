package ch.rex.photocollagewallpaper.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSettingsIntervalTest {
    @Test
    fun `default interval is five minutes`() {
        assertEquals(5, intervalMinutes(AppSettings().intervalMillis))
    }

    @Test
    fun `interval is clamped between one and sixty minutes`() {
        assertEquals(
            MIN_INTERVAL_MINUTES * MILLIS_PER_MINUTE,
            normalizeIntervalMillis(0L),
        )
        assertEquals(
            MAX_INTERVAL_MINUTES * MILLIS_PER_MINUTE,
            normalizeIntervalMillis(Long.MAX_VALUE),
        )
    }

    @Test
    fun `milliseconds are converted to complete minutes`() {
        assertEquals(17, intervalMinutes(17L * MILLIS_PER_MINUTE))
    }

    @Test
    fun `unknown photo scale mode safely falls back to fill`() {
        assertEquals(PhotoScaleMode.FILL, PhotoScaleMode.fromStoredValue("UNKNOWN"))
    }

    @Test
    fun `photo scale mode restores a known value`() {
        assertEquals(PhotoScaleMode.FIT, PhotoScaleMode.fromStoredValue("FIT"))
    }
}

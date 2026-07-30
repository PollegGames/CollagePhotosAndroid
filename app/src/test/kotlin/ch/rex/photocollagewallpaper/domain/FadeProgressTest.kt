package ch.rex.photocollagewallpaper.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FadeProgressTest {
    @Test
    fun `progress follows elapsed frame time`() {
        assertEquals(
            0.5f,
            FadeProgress.calculate(
                startFrameNanos = 1_000L,
                currentFrameNanos = 1_150L,
                durationNanos = 300L,
            ),
            0.0001f,
        )
    }

    @Test
    fun `progress is clamped before start and after duration`() {
        assertEquals(0f, FadeProgress.calculate(1_000L, 900L, 300L), 0.0001f)
        assertEquals(1f, FadeProgress.calculate(1_000L, 2_000L, 300L), 0.0001f)
    }

    @Test
    fun `invalid duration finishes immediately`() {
        assertEquals(1f, FadeProgress.calculate(1_000L, 1_000L, 0L), 0.0001f)
    }
}

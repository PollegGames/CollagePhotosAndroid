package ch.rex.photocollagewallpaper.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoLayoutPolicyTest {
    @Test
    fun `portrait canvas uses top and bottom layouts`() {
        assertEquals(
            setOf(
                MosaicLayout.THREE_LARGE_TOP,
                MosaicLayout.THREE_LARGE_BOTTOM,
            ),
            PhotoLayoutPolicy.compatibleLayouts(1080, 2400).toSet(),
        )
    }

    @Test
    fun `landscape canvas uses left and right layouts`() {
        assertEquals(
            setOf(
                MosaicLayout.THREE_LARGE_LEFT,
                MosaicLayout.THREE_LARGE_RIGHT,
            ),
            PhotoLayoutPolicy.compatibleLayouts(2400, 1080).toSet(),
        )
    }

    @Test
    fun `square canvas keeps every layout available`() {
        assertEquals(
            MosaicLayout.entries.toSet(),
            PhotoLayoutPolicy.compatibleLayouts(1000, 1000).toSet(),
        )
    }

    @Test
    fun `matching aspect ratios receive the best fit score`() {
        assertEquals(1f, PhotoFitScorer.score(0.75f, 0.75f), 0.0001f)
        assertTrue(
            PhotoFitScorer.score(0.75f, 0.75f) >
                PhotoFitScorer.score(1.8f, 0.75f),
        )
    }

    @Test
    fun `invalid ratios receive a zero score`() {
        assertEquals(0f, PhotoFitScorer.score(0f, 1f), 0.0001f)
        assertEquals(0f, PhotoFitScorer.score(Float.NaN, 1f), 0.0001f)
    }
}

package ch.rex.photocollagewallpaper.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressiveMosaicPolicyTest {
    @Test
    fun `every supported layout contains three photos`() {
        assertTrue(MosaicLayout.entries.all { it.photoCount == ProgressiveMosaicPolicy.PHOTO_COUNT })
        assertEquals(4, MosaicLayout.entries.size)
    }

    @Test
    fun `old mosaic is kept until all three incoming cells are revealed`() {
        MosaicLayout.entries.forEach { layout ->
            assertFalse(ProgressiveMosaicPolicy.isComplete(layout, revealedPhotoCount = 1))
            assertFalse(ProgressiveMosaicPolicy.isComplete(layout, revealedPhotoCount = 2))
            assertTrue(ProgressiveMosaicPolicy.isComplete(layout, revealedPhotoCount = 3))
        }
    }
}

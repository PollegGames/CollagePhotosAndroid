package ch.rex.photocollagewallpaper.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderContentPolicyTest {
    @Test
    fun `empty folder produces no readable image`() {
        assertTrue(ImageFileFilter.keepReadableImages(emptyList()).isEmpty())
    }

    @Test
    fun `directories unreadable files and invalid files are ignored`() {
        val candidates = listOf(
            ImageCandidate("nested", null, isFile = false, canRead = true),
            ImageCandidate("secret.jpg", "image/jpeg", isFile = true, canRead = false),
            ImageCandidate("notes.txt", "text/plain", isFile = true, canRead = true),
            ImageCandidate("valid.png", "image/png", isFile = true, canRead = true),
        )

        val result = ImageFileFilter.keepReadableImages(candidates)

        assertEquals(listOf(candidates.last()), result)
    }

    @Test
    fun `invalid-only folder produces no readable image`() {
        val candidates = listOf(
            ImageCandidate("notes.txt", "text/plain", isFile = true, canRead = true),
            ImageCandidate("movie.mp4", "video/mp4", isFile = true, canRead = true),
            ImageCandidate("folder", null, isFile = false, canRead = true),
        )

        assertTrue(ImageFileFilter.keepReadableImages(candidates).isEmpty())
    }
}

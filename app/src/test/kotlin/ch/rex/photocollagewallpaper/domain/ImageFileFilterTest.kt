package ch.rex.photocollagewallpaper.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageFileFilterTest {
    @Test
    fun `supported extensions are accepted without case sensitivity`() {
        listOf(
            "photo.jpg",
            "photo.JPEG",
            "photo.png",
            "photo.WEBP",
            "photo.heic",
            "photo.HEIF",
        ).forEach { name ->
            assertTrue(name, ImageFileFilter.isSupported(name, null))
        }
    }

    @Test
    fun `supported mime types are accepted when filename has no extension`() {
        listOf(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/heic",
            "image/heif",
        ).forEach { mimeType ->
            assertTrue(mimeType, ImageFileFilter.isSupported("photo", mimeType))
        }
    }

    @Test
    fun `invalid types are rejected`() {
        assertFalse(ImageFileFilter.isSupported("notes.txt", "text/plain"))
        assertFalse(ImageFileFilter.isSupported("video.mp4", "video/mp4"))
        assertFalse(ImageFileFilter.isSupported(null, null))
        assertFalse(ImageFileFilter.isSupported("fake.gif", "image/gif"))
    }
}

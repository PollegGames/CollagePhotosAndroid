package ch.rex.photocollagewallpaper.data

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import ch.rex.photocollagewallpaper.domain.ImageFileFilter
import java.util.concurrent.ConcurrentHashMap

enum class FolderAccessState {
    NO_FOLDER,
    AVAILABLE,
    EMPTY,
    INACCESSIBLE,
}

data class FolderScanResult(
    val folderName: String,
    val imageUris: List<Uri>,
    val accessState: FolderAccessState,
)

class FolderImageRepository(context: Context) {
    private val appContext = context.applicationContext
    private val contentResolver = appContext.contentResolver

    fun scan(
        folderUriValue: String?,
        forceRefresh: Boolean = false,
    ): FolderScanResult {
        if (folderUriValue.isNullOrBlank()) {
            return FolderScanResult(
                folderName = "Aucun dossier sélectionné",
                imageUris = emptyList(),
                accessState = FolderAccessState.NO_FOLDER,
            )
        }

        val folderUri = runCatching { folderUriValue.toUri() }.getOrNull()
            ?: return inaccessibleResult()

        if (!hasPersistedReadPermission(folderUriValue)) {
            return inaccessibleResult()
        }

        if (!forceRefresh) {
            getCached(folderUriValue)?.let { cached ->
                return cached
            }
        }

        // Different WallpaperService engines can ask for the same folder at almost the
        // same time. Only one provider query is allowed to run; the second caller then
        // receives the process-wide cache immediately.
        synchronized(SCAN_LOCK) {
            if (!forceRefresh) {
                getCached(folderUriValue)?.let { cached ->
                    return cached
                }
            }

            val result = scanUncached(folderUri)
            if (
                result.accessState == FolderAccessState.AVAILABLE ||
                result.accessState == FolderAccessState.EMPTY
            ) {
                CACHE[folderUriValue] = CacheEntry(
                    result = result,
                    createdAtMillis = SystemClock.elapsedRealtime(),
                )
            }
            return result
        }
    }

    fun resolveFolderName(folderUriValue: String?): String {
        if (folderUriValue.isNullOrBlank()) {
            return "Aucun dossier sélectionné"
        }
        val folderUri = runCatching { folderUriValue.toUri() }.getOrNull()
            ?: return "Dossier inaccessible"

        getCached(folderUriValue)?.let { cached ->
            return cached.folderName
        }

        return try {
            DocumentFile.fromTreeUri(appContext, folderUri)?.name
                ?: readableUriFallback(folderUri)
        } catch (_: SecurityException) {
            "Dossier inaccessible"
        } catch (_: IllegalArgumentException) {
            "Dossier inaccessible"
        }
    }

    fun hasPersistedReadPermission(folderUriValue: String?): Boolean {
        if (folderUriValue.isNullOrBlank()) {
            return false
        }
        val folderUri = runCatching { folderUriValue.toUri() }.getOrNull() ?: return false
        return contentResolver.persistedUriPermissions.any { permission ->
            permission.uri == folderUri && permission.isReadPermission
        }
    }

    fun invalidate(folderUriValue: String?) {
        folderUriValue?.let(CACHE::remove)
    }

    private fun scanUncached(folderUri: Uri): FolderScanResult {
        return try {
            val root = DocumentFile.fromTreeUri(appContext, folderUri)
                ?: return inaccessibleResult()
            val folderName = root.name
                ?: readableUriFallback(folderUri)

            if (!root.exists() || !root.isDirectory || !root.canRead()) {
                return FolderScanResult(
                    folderName = folderName,
                    imageUris = emptyList(),
                    accessState = FolderAccessState.INACCESSIBLE,
                )
            }

            val imageFiles = queryChildrenEfficiently(folderUri)
                ?: queryChildrenWithDocumentFile(root)

            FolderScanResult(
                folderName = folderName,
                imageUris = imageFiles,
                accessState = if (imageFiles.isEmpty()) {
                    FolderAccessState.EMPTY
                } else {
                    FolderAccessState.AVAILABLE
                },
            )
        } catch (_: SecurityException) {
            inaccessibleResult()
        } catch (_: IllegalArgumentException) {
            inaccessibleResult()
        }
    }

    /**
     * DocumentFile.listFiles() is convenient but reading name/type/readability from every
     * child can result in several provider round trips per file. A single DocumentsContract
     * cursor keeps a 3,000-photo folder responsive while DocumentFile remains responsible
     * for validating the selected tree.
     */
    private fun queryChildrenEfficiently(folderUri: Uri): List<Uri>? {
        return try {
            val parentDocumentId = DocumentsContract.getTreeDocumentId(folderUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                folderUri,
                parentDocumentId,
            )
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            )

            val cursor = contentResolver.query(
                childrenUri,
                projection,
                null,
                null,
                null,
            ) ?: return null

            cursor.use {
                val documentIdColumn = it.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                )
                val displayNameColumn = it.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                )
                val mimeTypeColumn = it.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                )
                buildList {
                    while (it.moveToNext()) {
                        val mimeType = it.getString(mimeTypeColumn)
                        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                            continue
                        }
                        val displayName = it.getString(displayNameColumn)
                        if (!ImageFileFilter.isSupported(displayName, mimeType)) {
                            continue
                        }
                        val documentId = it.getString(documentIdColumn)
                        add(
                            DocumentsContract.buildDocumentUriUsingTree(
                                folderUri,
                                documentId,
                            ),
                        )
                    }
                }
            }
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: UnsupportedOperationException) {
            null
        }
    }

    private fun queryChildrenWithDocumentFile(root: DocumentFile): List<Uri> =
        root.listFiles()
            .asSequence()
            .filter { document ->
                document.isFile &&
                    document.canRead() &&
                    ImageFileFilter.isSupported(document.name, document.type)
            }
            .map { it.uri }
            .toList()

    private fun inaccessibleResult() = FolderScanResult(
        folderName = "Dossier inaccessible",
        imageUris = emptyList(),
        accessState = FolderAccessState.INACCESSIBLE,
    )

    private fun readableUriFallback(folderUri: Uri): String {
        val decoded = Uri.decode(folderUri.lastPathSegment.orEmpty())
        return decoded
            .substringAfterLast(':')
            .substringAfterLast('/')
            .ifBlank { "Dossier de photos" }
    }

    private fun getCached(folderUriValue: String): FolderScanResult? {
        val entry = CACHE[folderUriValue] ?: return null
        val age = SystemClock.elapsedRealtime() - entry.createdAtMillis
        if (age in 0..CACHE_MAX_AGE_MILLIS) {
            return entry.result
        }
        CACHE.remove(folderUriValue, entry)
        return null
    }

    private data class CacheEntry(
        val result: FolderScanResult,
        val createdAtMillis: Long,
    )

    private companion object {
        const val CACHE_MAX_AGE_MILLIS = 30L * 60L * 1_000L
        val CACHE = ConcurrentHashMap<String, CacheEntry>()
        val SCAN_LOCK = Any()
    }
}

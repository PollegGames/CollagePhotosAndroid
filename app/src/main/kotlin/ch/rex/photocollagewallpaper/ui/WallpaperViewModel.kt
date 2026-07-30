package ch.rex.photocollagewallpaper.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ch.rex.photocollagewallpaper.data.FolderAccessState
import ch.rex.photocollagewallpaper.data.FolderImageRepository
import ch.rex.photocollagewallpaper.data.PhotoScaleMode
import ch.rex.photocollagewallpaper.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WallpaperViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val settingsRepository = SettingsRepository(appContext)
    private val folderImageRepository = FolderImageRepository(appContext)

    private val _state = MutableStateFlow(WallpaperUiState())
    val state: StateFlow<WallpaperUiState> = _state.asStateFlow()

    private var inspectedFolderUri: String? = null
    private var folderStatusJob: Job? = null

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _state.update { current -> current.copy(settings = settings) }

                val folderUri = settings.folderUri
                if (folderUri == inspectedFolderUri) {
                    return@collect
                }
                inspectedFolderUri = folderUri
                inspectFolder(folderUri)
            }
        }
    }

    fun selectFolder(uri: Uri) {
        viewModelScope.launch {
            try {
                appContext.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
                val folderUri = uri.toString()
                folderImageRepository.invalidate(folderUri)
                inspectedFolderUri = folderUri
                _state.update { current ->
                    current.copy(
                        folderName = readableUriFallback(uri),
                        folderAccessState = FolderAccessState.AVAILABLE,
                        message = null,
                    )
                }
                settingsRepository.selectFolder(folderUri)
                inspectFolder(folderUri)
            } catch (_: SecurityException) {
                _state.update { current ->
                    current.copy(
                        folderAccessState = FolderAccessState.INACCESSIBLE,
                        message = "Android n’a pas accordé l’accès durable à ce dossier.",
                    )
                }
            }
        }
    }

    fun setGapDp(gapDp: Float) {
        viewModelScope.launch {
            settingsRepository.setGapDp(gapDp)
        }
    }

    fun setBackgroundArgb(backgroundArgb: Long) {
        viewModelScope.launch {
            settingsRepository.setBackgroundArgb(backgroundArgb)
        }
    }

    fun setFadeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setFadeEnabled(enabled)
        }
    }

    fun setPhotoScaleMode(mode: PhotoScaleMode) {
        viewModelScope.launch {
            settingsRepository.setPhotoScaleMode(mode)
        }
    }

    fun setIntervalMinutes(minutes: Int) {
        viewModelScope.launch {
            settingsRepository.setIntervalMinutes(minutes)
        }
    }

    override fun onCleared() {
        folderStatusJob?.cancel()
        super.onCleared()
    }

    private fun inspectFolder(folderUri: String?) {
        folderStatusJob?.cancel()
        if (folderUri.isNullOrBlank()) {
            _state.update { current ->
                current.copy(
                    folderName = "Aucun dossier sélectionné",
                    folderAccessState = FolderAccessState.NO_FOLDER,
                    message = null,
                )
            }
            return
        }

        folderStatusJob = viewModelScope.launch {
            val (folderName, hasAccess) = withContext(Dispatchers.IO) {
                folderImageRepository.resolveFolderName(folderUri) to
                    folderImageRepository.hasPersistedReadPermission(folderUri)
            }
            if (inspectedFolderUri != folderUri) {
                return@launch
            }
            _state.update { current ->
                current.copy(
                    folderName = folderName,
                    folderAccessState = if (hasAccess) {
                        FolderAccessState.AVAILABLE
                    } else {
                        FolderAccessState.INACCESSIBLE
                    },
                    message = if (hasAccess) {
                        null
                    } else {
                        "Le dossier n’est plus accessible. Choisis-le à nouveau."
                    },
                )
            }
        }
    }

    private fun readableUriFallback(uri: Uri): String {
        val decoded = Uri.decode(uri.lastPathSegment.orEmpty())
        return decoded
            .substringAfterLast(':')
            .substringAfterLast('/')
            .ifBlank { "Dossier de photos" }
    }
}

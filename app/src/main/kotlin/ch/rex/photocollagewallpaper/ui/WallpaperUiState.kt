package ch.rex.photocollagewallpaper.ui

import ch.rex.photocollagewallpaper.data.AppSettings
import ch.rex.photocollagewallpaper.data.FolderAccessState

data class WallpaperUiState(
    val settings: AppSettings = AppSettings(),
    val folderName: String = "Aucun dossier sélectionné",
    val folderAccessState: FolderAccessState = FolderAccessState.NO_FOLDER,
    val message: String? = null,
)

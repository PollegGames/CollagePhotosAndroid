package ch.rex.photocollagewallpaper.wallpaper

import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import ch.rex.photocollagewallpaper.data.AppSettings
import ch.rex.photocollagewallpaper.data.FolderImageRepository
import ch.rex.photocollagewallpaper.data.SettingsRepository
import ch.rex.photocollagewallpaper.domain.MosaicCatchUpPolicy
import ch.rex.photocollagewallpaper.domain.ProgressiveMosaicPolicy
import ch.rex.photocollagewallpaper.image.CollageBitmapLoader
import ch.rex.photocollagewallpaper.image.CollageRenderer
import ch.rex.photocollagewallpaper.image.MosaicBitmapSet
import ch.rex.photocollagewallpaper.image.PreparedMosaic
import ch.rex.photocollagewallpaper.image.ScaledBitmapDecoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PhotoCollageWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = CollageEngine()

    private inner class CollageEngine : Engine() {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val settingsRepository = SettingsRepository(applicationContext)
        private val bitmapDecoder = ScaledBitmapDecoder(contentResolver)
        private val collageLoader = CollageBitmapLoader(
            folderImageRepository = FolderImageRepository(applicationContext),
            bitmapDecoder = bitmapDecoder,
        )
        private val renderer = CollageRenderer()

        private var settings = AppSettings()
        private var currentMosaic: MosaicBitmapSet? = null
        private var buildingMosaic: BuildingMosaic? = null
        private var workJob: Job? = null
        private var nextStepJob: Job? = null
        private var surfaceWidth = 0
        private var surfaceHeight = 0
        private var surfaceReady = false
        private var visible = false
        private var settingsLoaded = false
        private var pendingInitialLoad = true
        private var pendingStep = false
        private var lastStepElapsed = 0L
        private var randomGeneration = SystemClock.elapsedRealtimeNanos()
        private var initialRetryCount = 0

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(false)
            setOffsetNotificationsEnabled(false)

            scope.launch {
                settingsRepository.settings.collect { updatedSettings ->
                    val previousSettings = settings
                    val firstSettings = !settingsLoaded
                    settings = updatedSettings
                    settingsLoaded = true

                    val folderChanged = !firstSettings &&
                        previousSettings.folderUri != updatedSettings.folderUri
                    val savedMosaicBecameAvailable =
                        currentMosaic == null &&
                            previousSettings.lastPhotoUris != updatedSettings.lastPhotoUris &&
                            updatedSettings.lastPhotoUris.isNotEmpty()
                    val appearanceChanged =
                        previousSettings.gapDp != updatedSettings.gapDp ||
                            previousSettings.backgroundArgb != updatedSettings.backgroundArgb ||
                            previousSettings.fadeEnabled != updatedSettings.fadeEnabled

                    when {
                        firstSettings -> {
                            initialRetryCount = 0
                            pendingInitialLoad = true
                            requestInitialLoadIfPossible(restartRunning = true)
                        }

                        folderChanged -> {
                            workJob?.cancel()
                            nextStepJob?.cancel()
                            currentMosaic = null
                            buildingMosaic = null
                            lastStepElapsed = 0L
                            initialRetryCount = 0
                            pendingStep = false
                            pendingInitialLoad = true
                            drawCurrentOrBackground()
                            requestInitialLoadIfPossible(restartRunning = true)
                        }

                        savedMosaicBecameAvailable -> {
                            pendingInitialLoad = true
                            requestInitialLoadIfPossible(restartRunning = true)
                        }

                        appearanceChanged -> drawCurrentOrBackground()

                        previousSettings.intervalMillis != updatedSettings.intervalMillis -> {
                            val elapsed = SystemClock.elapsedRealtime() - lastStepElapsed
                            scheduleNextStep(updatedSettings.intervalMillis - elapsed)
                        }
                    }
                }
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            surfaceReady = true
            resumeWorkIfPossible()
        }

        override fun onSurfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int,
        ) {
            super.onSurfaceChanged(holder, format, width, height)
            surfaceReady = true
            surfaceWidth = width
            surfaceHeight = height
            drawCurrentOrBackground()
            resumeWorkIfPossible()
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            drawCurrentOrBackground()
            resumeWorkIfPossible()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            if (workJob?.isActive == true) {
                markInterruptedWorkPending()
            }
            surfaceReady = false
            workJob?.cancel()
            nextStepJob?.cancel()
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            if (!isVisible) {
                if (workJob?.isActive == true) {
                    markInterruptedWorkPending()
                }
                workJob?.cancel()
                nextStepJob?.cancel()
                return
            }
            if (
                currentMosaic == null &&
                !pendingInitialLoad &&
                initialRetryCount >= MAXIMUM_INITIAL_RETRIES
            ) {
                initialRetryCount = 0
                pendingInitialLoad = true
            }
            resumeWorkIfPossible()
        }

        override fun onDestroy() {
            workJob?.cancel()
            nextStepJob?.cancel()
            scope.cancel()
            currentMosaic = null
            buildingMosaic = null
            bitmapDecoder.clear()
            super.onDestroy()
        }

        private fun resumeWorkIfPossible() {
            drawCurrentOrBackground()
            if (!canWork()) {
                return
            }
            if (pendingInitialLoad) {
                requestInitialLoadIfPossible()
                return
            }
            if (currentMosaic == null) {
                return
            }

            val elapsed = SystemClock.elapsedRealtime() - lastStepElapsed
            val remainingCells = buildingMosaic?.let { building ->
                building.prepared.layout.photoCount - building.revealedCellCount
            } ?: ProgressiveMosaicPolicy.PHOTO_COUNT
            val stepsToReveal = MosaicCatchUpPolicy.stepsToReveal(
                elapsedMillis = if (lastStepElapsed == 0L) {
                    settings.intervalMillis
                } else {
                    elapsed
                },
                intervalMillis = settings.intervalMillis,
                remainingCells = remainingCells,
                interruptedStepPending = pendingStep,
            )
            if (stepsToReveal > 0) {
                requestNextPhotosIfPossible(stepsToReveal)
            } else {
                scheduleNextStep(settings.intervalMillis - elapsed)
            }
        }

        private fun requestInitialLoadIfPossible(restartRunning: Boolean = false) {
            if (!canWork()) {
                pendingInitialLoad = true
                return
            }
            if (workJob?.isActive == true && !restartRunning) {
                return
            }

            pendingInitialLoad = false
            pendingStep = false
            val loadSettings = settings
            val width = surfaceWidth
            val height = surfaceHeight
            val gapPixels = gapPixels(loadSettings)
            randomGeneration += 1L
            val seed = loadSettings.refreshToken xor randomGeneration

            workJob?.cancel()
            workJob = scope.launch {
                var savedMosaic: MosaicBitmapSet? = null
                if (loadSettings.lastPhotoUris.isNotEmpty()) {
                    savedMosaic = runLoading {
                        collageLoader.decodeSavedMosaic(
                            imageUriValues = loadSettings.lastPhotoUris,
                            layout = loadSettings.lastMosaicLayout,
                            canvasWidth = width,
                            canvasHeight = height,
                            gapPixels = gapPixels,
                        )
                    }
                    if (isActive && canWork() && savedMosaic?.isComplete == true) {
                        currentMosaic = savedMosaic
                        buildingMosaic = null
                        drawCurrentOrBackground()
                        finishInitialLoad()
                        return@launch
                    }
                }

                val loaded = runLoading {
                    collageLoader.loadComplete(
                        folderUri = loadSettings.folderUri,
                        excludedImageUris = emptySet(),
                        canvasWidth = width,
                        canvasHeight = height,
                        gapPixels = gapPixels,
                        randomSeed = seed,
                    )
                }
                if (!isActive || !canWork()) {
                    pendingInitialLoad = true
                    return@launch
                }

                val loadedMosaic = loaded?.mosaic
                if (loadedMosaic != null) {
                    currentMosaic = loadedMosaic
                    buildingMosaic = null
                    drawCurrentOrBackground()
                    persistCompletedMosaic(loadedMosaic, loadSettings)
                }
                finishInitialLoad()
            }
        }

        private fun finishInitialLoad() {
            if (currentMosaic != null) {
                pendingInitialLoad = false
                initialRetryCount = 0
                lastStepElapsed = SystemClock.elapsedRealtime()
                scheduleNextStep(settings.intervalMillis)
                return
            }

            if (
                settings.folderUri != null &&
                initialRetryCount < MAXIMUM_INITIAL_RETRIES
            ) {
                initialRetryCount += 1
                pendingInitialLoad = true
                scheduleInitialRetry()
            } else {
                pendingInitialLoad = false
            }
        }

        private fun scheduleInitialRetry() {
            nextStepJob?.cancel()
            if (!canWork() || currentMosaic != null) {
                return
            }
            nextStepJob = scope.launch {
                delay(INITIAL_RETRY_DELAY_MILLIS)
                if (isActive && canWork() && currentMosaic == null) {
                    requestInitialLoadIfPossible()
                }
            }
        }

        private fun requestNextPhotosIfPossible(
            requestedStepCount: Int,
            restartRunning: Boolean = false,
        ) {
            val baseMosaic = currentMosaic
            if (baseMosaic == null) {
                pendingInitialLoad = true
                requestInitialLoadIfPossible(restartRunning)
                return
            }
            if (!canWork()) {
                pendingStep = true
                return
            }
            if (workJob?.isActive == true && !restartRunning) {
                return
            }

            pendingStep = false
            val stepSettings = settings
            val width = surfaceWidth
            val height = surfaceHeight
            val gapPixels = gapPixels(stepSettings)
            val safeRequestedStepCount = requestedStepCount.coerceIn(
                minimumValue = 1,
                maximumValue = ProgressiveMosaicPolicy.PHOTO_COUNT,
            )

            workJob?.cancel()
            workJob = scope.launch {
                var activeBuilding = buildingMosaic
                if (activeBuilding == null) {
                    randomGeneration += 1L
                    val preparedResult = runLoading {
                        collageLoader.prepare(
                            folderUri = stepSettings.folderUri,
                            excludedImageUris = baseMosaic.imageUris.filterNotNull().toSet(),
                            randomSeed = stepSettings.refreshToken xor randomGeneration,
                        )
                    }
                    val prepared = preparedResult?.mosaic
                    if (!isActive || !canWork()) {
                        pendingStep = true
                        return@launch
                    }
                    if (prepared == null) {
                        completeStepWithoutChange()
                        return@launch
                    }
                    activeBuilding = BuildingMosaic(prepared)
                    buildingMosaic = activeBuilding
                }

                val stepCountForThisMosaic = minOf(
                    safeRequestedStepCount,
                    activeBuilding.prepared.layout.photoCount -
                        activeBuilding.revealedCellCount,
                )
                if (stepCountForThisMosaic <= 0) {
                    buildingMosaic = null
                    completeStepWithoutChange()
                    return@launch
                }

                repeat(stepCountForThisMosaic) {
                    val cellIndex = activeBuilding.revealedCellCount
                    if (cellIndex !in activeBuilding.prepared.cells.indices) {
                        buildingMosaic = null
                        completeStepWithoutChange()
                        return@launch
                    }

                    val decodedCell = runLoading {
                        collageLoader.decodeCell(
                            preparedMosaic = activeBuilding.prepared,
                            cellIndex = cellIndex,
                            usedImageUris = activeBuilding.imageUris.filterNotNull().toSet(),
                            canvasWidth = width,
                            canvasHeight = height,
                            gapPixels = gapPixels,
                        )
                    }
                    if (!isActive || !canWork()) {
                        pendingStep = true
                        return@launch
                    }
                    if (decodedCell == null) {
                        buildingMosaic = null
                        completeStepWithoutChange()
                        return@launch
                    }

                    val transitionMosaic = activeBuilding.snapshotWith(
                        cellIndex = cellIndex,
                        bitmap = decodedCell.bitmap,
                        imageUri = decodedCell.imageUri,
                    )
                    animateIncomingCell(
                        baseMosaic = baseMosaic,
                        incomingMosaic = transitionMosaic,
                        revealedCellCount = activeBuilding.revealedCellCount,
                        cellIndex = cellIndex,
                        frameSettings = stepSettings,
                    )
                    if (!isActive || !canWork()) {
                        pendingStep = true
                        return@launch
                    }

                    activeBuilding.bitmaps[cellIndex] = decodedCell.bitmap
                    activeBuilding.imageUris[cellIndex] = decodedCell.imageUri
                    activeBuilding.revealedCellCount += 1

                    if (
                        ProgressiveMosaicPolicy.isComplete(
                            layout = activeBuilding.prepared.layout,
                            revealedPhotoCount = activeBuilding.revealedCellCount,
                        )
                    ) {
                        val completedMosaic = activeBuilding.snapshot()
                        currentMosaic = completedMosaic
                        buildingMosaic = null
                        drawCurrentOrBackground()
                        persistCompletedMosaic(completedMosaic, stepSettings)
                    } else {
                        drawCurrentOrBackground()
                    }
                }

                lastStepElapsed = SystemClock.elapsedRealtime()
                scheduleNextStep(stepSettings.intervalMillis)
            }
        }

        private suspend fun animateIncomingCell(
            baseMosaic: MosaicBitmapSet,
            incomingMosaic: MosaicBitmapSet,
            revealedCellCount: Int,
            cellIndex: Int,
            frameSettings: AppSettings,
        ) {
            if (!frameSettings.fadeEnabled) {
                drawFrame(
                    baseMosaic = baseMosaic,
                    incomingMosaic = incomingMosaic,
                    revealedIncomingCells = revealedCellCount,
                    transitioningCellIndex = cellIndex,
                    transitionProgress = 1f,
                    frameSettings = frameSettings,
                )
                return
            }

            repeat(FADE_FRAME_COUNT + 1) { frame ->
                if (!canWork()) {
                    return
                }
                drawFrame(
                    baseMosaic = baseMosaic,
                    incomingMosaic = incomingMosaic,
                    revealedIncomingCells = revealedCellCount,
                    transitioningCellIndex = cellIndex,
                    transitionProgress = frame.toFloat() / FADE_FRAME_COUNT,
                    frameSettings = frameSettings,
                )
                if (frame < FADE_FRAME_COUNT) {
                    delay(FADE_FRAME_DELAY_MILLIS)
                }
            }
        }

        private suspend fun <T> runLoading(block: () -> T): T? =
            try {
                withContext(Dispatchers.IO) { block() }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: RuntimeException) {
                null
            }

        private suspend fun persistCompletedMosaic(
            mosaic: MosaicBitmapSet,
            completedSettings: AppSettings,
        ) {
            val folderUri = completedSettings.folderUri ?: return
            if (!mosaic.isComplete) {
                return
            }
            settingsRepository.saveLastMosaic(
                folderUri = folderUri,
                layout = mosaic.layout,
                photoUris = mosaic.imageUris.filterNotNull().map(Uri::toString),
            )
        }

        private fun completeStepWithoutChange() {
            lastStepElapsed = SystemClock.elapsedRealtime()
            scheduleNextStep(settings.intervalMillis)
        }

        private fun scheduleNextStep(delayMillis: Long) {
            nextStepJob?.cancel()
            if (!canWork() || currentMosaic == null) {
                return
            }
            nextStepJob = scope.launch {
                delay(delayMillis.coerceAtLeast(MINIMUM_SCHEDULE_DELAY_MILLIS))
                if (isActive && canWork()) {
                    pendingStep = true
                    requestNextPhotosIfPossible(requestedStepCount = 1)
                }
            }
        }

        private fun markInterruptedWorkPending() {
            if (currentMosaic == null) {
                pendingInitialLoad = true
            } else {
                pendingStep = true
            }
        }

        private fun drawCurrentOrBackground() {
            drawFrame(
                baseMosaic = currentMosaic,
                incomingMosaic = buildingMosaic?.snapshot(),
                revealedIncomingCells = buildingMosaic?.revealedCellCount ?: 0,
                transitioningCellIndex = null,
                transitionProgress = 1f,
            )
        }

        private fun drawFrame(
            baseMosaic: MosaicBitmapSet?,
            incomingMosaic: MosaicBitmapSet?,
            revealedIncomingCells: Int,
            transitioningCellIndex: Int?,
            transitionProgress: Float,
            frameSettings: AppSettings = settings,
        ) {
            if (!surfaceReady || !surfaceHolder.surface.isValid) {
                return
            }

            var canvas: android.graphics.Canvas? = null
            try {
                canvas = surfaceHolder.lockCanvas()
                canvas?.let { lockedCanvas ->
                    renderer.drawFrame(
                        canvas = lockedCanvas,
                        baseMosaic = baseMosaic,
                        incomingMosaic = incomingMosaic,
                        revealedIncomingCells = revealedIncomingCells,
                        transitioningCellIndex = transitioningCellIndex,
                        transitionProgress = transitionProgress,
                        gapPixels = gapPixels(frameSettings),
                        backgroundArgb = frameSettings.backgroundArgb,
                        showPlaceholder =
                            baseMosaic == null &&
                                incomingMosaic == null &&
                                frameSettings.folderUri != null,
                    )
                }
            } catch (_: IllegalArgumentException) {
                markInterruptedWorkPending()
            } finally {
                canvas?.let { lockedCanvas ->
                    runCatching {
                        surfaceHolder.unlockCanvasAndPost(lockedCanvas)
                    }
                }
            }
        }

        private fun gapPixels(frameSettings: AppSettings): Float =
            frameSettings.gapDp * resources.displayMetrics.density

        private fun canWork(): Boolean =
            settingsLoaded &&
                visible &&
                surfaceReady &&
                surfaceWidth > 0 &&
                surfaceHeight > 0
    }

    private data class BuildingMosaic(
        val prepared: PreparedMosaic,
        val bitmaps: MutableList<Bitmap?> =
            MutableList(prepared.layout.photoCount) { null },
        val imageUris: MutableList<Uri?> =
            MutableList(prepared.layout.photoCount) { null },
        var revealedCellCount: Int = 0,
    ) {
        fun snapshot(): MosaicBitmapSet = MosaicBitmapSet(
            layout = prepared.layout,
            bitmaps = bitmaps.toList(),
            imageUris = imageUris.toList(),
        )

        fun snapshotWith(
            cellIndex: Int,
            bitmap: Bitmap,
            imageUri: Uri,
        ): MosaicBitmapSet {
            val nextBitmaps = bitmaps.toMutableList()
            val nextImageUris = imageUris.toMutableList()
            nextBitmaps[cellIndex] = bitmap
            nextImageUris[cellIndex] = imageUri
            return MosaicBitmapSet(
                layout = prepared.layout,
                bitmaps = nextBitmaps,
                imageUris = nextImageUris,
            )
        }
    }

    private companion object {
        const val FADE_FRAME_COUNT = 8
        const val FADE_FRAME_DELAY_MILLIS = 32L
        const val MINIMUM_SCHEDULE_DELAY_MILLIS = 1_000L
        const val INITIAL_RETRY_DELAY_MILLIS = 5_000L
        const val MAXIMUM_INITIAL_RETRIES = 3
    }
}

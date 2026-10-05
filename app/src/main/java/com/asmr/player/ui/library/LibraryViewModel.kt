package com.asmr.player.ui.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.map
import com.asmr.player.domain.model.LibraryTrackRow
import com.asmr.player.domain.model.TagWithCount
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.domain.model.*
import com.asmr.player.data.local.db.entities.TrackEntity
import com.asmr.player.data.local.db.entities.titleForDisplay
import com.asmr.player.data.local.library.LocalAlbumMergeService
import com.asmr.player.data.local.library.buildOnlineAlbumPath
import com.asmr.player.data.remote.dlsite.DlsiteCloudSyncCandidate
import com.asmr.player.data.remote.dlsite.DlsiteCloudSyncResolveResult
import com.asmr.player.data.remote.dlsite.resolveCloudSyncWorkId
import com.asmr.player.data.download.DownloadDestination
import com.asmr.player.data.download.DownloadDestinationStore
import com.asmr.player.data.repository.DownloadQueueRepository
import com.asmr.player.data.repository.LibraryReadRepository
import com.asmr.player.data.repository.LibraryWriteRepository
import com.asmr.player.data.repository.OnlineContentRepository
import com.asmr.player.data.settings.SettingsRepository
import com.asmr.player.playback.PlayerConnection
import com.asmr.player.util.GlobalSyncState
import com.asmr.player.util.ScanRootsStore
import com.asmr.player.util.MessageManager
import com.asmr.player.util.SyncCoordinator
import com.asmr.player.util.isOnlineTrackPath
import com.asmr.player.util.isScannableLocalDirectoryName
import com.asmr.player.util.isVirtualAlbumPath
import com.asmr.player.util.parseAlbumTags
import com.asmr.player.util.centerCropSquare
import com.asmr.player.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Named
import okhttp3.OkHttpClient
import okhttp3.Request
import com.asmr.player.ui.library.albumdetail.LocalTreeDeletionTarget

sealed class LibraryUiState {
    object Loading : LibraryUiState()
    data class BulkInProgress(val progress: BulkProgress) : LibraryUiState()
    data class Success(val syncingAlbums: Map<Long, SyncStatus> = emptyMap()) : LibraryUiState()
}

sealed class SyncStatus {
    object Idle : SyncStatus()
    object Syncing : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}

enum class BulkPhase {
    ScanningLocal,
    SyncingCloud
}

data class BulkProgress(
    val phase: BulkPhase,
    val current: Int,
    val total: Int,
    val currentAlbumTitle: String = "",
    val currentFile: String = "",
    val startedAtElapsedMs: Long = SystemClock.elapsedRealtime()
) {
    val fraction: Float = if (total <= 0) 0f else (current.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryReadRepository: LibraryReadRepository,
    private val libraryWriteRepository: LibraryWriteRepository,
    private val settingsRepository: SettingsRepository,
    private val downloadDestinationStore: DownloadDestinationStore,
    private val downloadQueueRepository: DownloadQueueRepository,
    private val onlineContentRepository: OnlineContentRepository,
    private val localAlbumMergeService: LocalAlbumMergeService,
    private val syncCoordinator: SyncCoordinator,
    @Named("image") private val imageOkHttpClient: OkHttpClient,
    val messageManager: MessageManager,
    private val playerConnection: PlayerConnection,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private companion object {
        const val TAG = "LibraryViewModel"
    }

    private val scanRootsStore = ScanRootsStore(context)

    /** R3-C1b-ii：任务协调 State Holder（单专辑任务注册表/批量任务门/同步状态/批量进度/云同步选择队列）。 */
    private val taskCoordinator = LibraryTaskCoordinator(messageManager)

    /** R3-C1c：扫描族 State Holder（封面/树缓存/SAF 遍历/字幕/下载目录与文档树扫描/孤儿清理 + 批量入口）。 */
    private val scanHolder = LibraryScanStateHolder(
        context = context,
        readRepository = libraryReadRepository,
        writeRepository = libraryWriteRepository,
        downloadDestinationStore = downloadDestinationStore,
        localAlbumMergeService = localAlbumMergeService,
        playerConnection = playerConnection,
        taskCoordinator = taskCoordinator,
    )

    /** R3-C1b-ii：删除族 State Holder（rescanAlbum/deleteAlbum/deleteAlbumTreeEntry/removeTrackFromAlbum）。 */
    private val deleteHolder = LibraryDeleteStateHolder(
        scope = viewModelScope,
        context = context,
        readRepository = libraryReadRepository,
        writeRepository = libraryWriteRepository,
        downloadQueueRepository = downloadQueueRepository,
        syncCoordinator = syncCoordinator,
        taskCoordinator = taskCoordinator,
        messageManager = messageManager,
        scanHolder = scanHolder,
    )
    private val _scanRoots = MutableStateFlow<Set<String>>(emptySet())
    val scanRoots: StateFlow<List<String>> = _scanRoots
        .map { it.toList().sorted() }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val bulkProgress: StateFlow<BulkProgress?> = taskCoordinator.bulkProgress
    internal val cloudSyncSelectionDialogState: StateFlow<CloudSyncSelectionDialogState?> = taskCoordinator.cloudSyncSelectionQueue.dialogState
    val globalSyncState: StateFlow<GlobalSyncState?> = syncCoordinator.state
    val isGlobalSyncRunning: StateFlow<Boolean> = globalSyncState
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), globalSyncState.value != null)
    private val scanMutex = Mutex()
    private val _expandedTrackAlbumIds = MutableStateFlow<Set<Long>>(emptySet())
    val expandedTrackAlbumIds: StateFlow<Set<Long>> = _expandedTrackAlbumIds.asStateFlow()

    val availableTags: StateFlow<List<TagWithCount>> = libraryReadRepository.observeTagsWithCounts(TagSource.USER)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** R3-C1：过滤/预设族 State Holder（_querySpec 唯一所有者；VM 外壳经 [filterHolder] 只读引用）。 */
    private val filterHolder = LibraryFilterStateHolder(
        scope = viewModelScope,
        readRepository = libraryReadRepository,
        writeRepository = libraryWriteRepository,
        settingsRepository = settingsRepository,
        availableTags = availableTags,
    )
    val querySpec: StateFlow<LibraryQuerySpec> get() = filterHolder.querySpec

    val availableCircles: StateFlow<List<String>> = libraryReadRepository.observeDistinctCircles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val availableCvs: StateFlow<List<String>> = libraryReadRepository.observeDistinctCvs()
        .map { rows ->
            val result = ArrayList<String>(rows.size)
            val seen = HashSet<String>(rows.size)
            rows.forEach { raw ->
                raw.split(',', '，')
                    .asSequence()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .forEach { token ->
                        val normalized = token
                            .replace(" ", "")
                            .replace("　", "")
                            .lowercase()
                        if (normalized.isNotBlank() && seen.add(normalized)) {
                            result.add(token)
                        }
                    }
            }
            result.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val filterPresets: StateFlow<List<LibraryFilterPreset>> = libraryReadRepository.libraryFilterPresets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val hasActiveFilters: StateFlow<Boolean> = filterHolder.querySpec
        .map { it.hasActiveFilters }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val userTagsByAlbumId: StateFlow<Map<Long, List<String>>> = libraryReadRepository.observeAlbumTagsBySource(TagSource.USER)
        .map { rows ->
            rows.associate { row ->
                val tags = row.tagsCsv
                    .orEmpty()
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                row.albumId to tags
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val userTagsByTrackId: StateFlow<Map<Long, List<String>>> = libraryReadRepository.observeTrackTagsBySource(TagSource.USER)
        .map { rows ->
            rows.associate { row ->
                val tags = row.tagsCsv
                    .orEmpty()
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                row.trackId to tags
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** R3-C1：用户标签族 State Holder（VM 外壳保留 UI 转发）。 */
    private val tagHolder = LibraryTagStateHolder(
        scope = viewModelScope,
        readRepository = libraryReadRepository,
        writeRepository = libraryWriteRepository,
        querySpec = filterHolder.querySpec,
        onFiltersRemoved = { filterHolder.removeTagFilters(it) },
        userTagsByAlbumId = userTagsByAlbumId,
    )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _scanRoots.value = runCatching { scanRootsStore.getRoots() }.getOrDefault(emptySet())
            tagHolder.ensureTagTablesInitialized()
            filterHolder.restoreLibraryPreferences()
            scanHolder.backfillLegacyOnlineSavedAlbumRoots()
        }
        viewModelScope.launch {
            val shouldAutoScan = withContext(Dispatchers.IO) {
                val hasAnyAlbum = runCatching { libraryReadRepository.getAllAlbumsOnce().isNotEmpty() }.getOrDefault(false)
                if (hasAnyAlbum) return@withContext false
                val hasRoots = runCatching { scanRootsStore.getRoots().isNotEmpty() }.getOrDefault(false)
                val hasDownloaded = runCatching {
                    when (val destination = downloadDestinationStore.current()) {
                        is DownloadDestination.Default -> {
                            val baseDir = File(destination.root)
                            baseDir.exists() && baseDir.isDirectory &&
                                (baseDir.listFiles()?.any {
                                    it.isDirectory && isScannableLocalDirectoryName(it.name) &&
                                        File(it, ".download_complete").exists()
                                } == true)
                        }
                        is DownloadDestination.DocumentTree -> true
                    }
                }.getOrDefault(false)
                hasRoots || hasDownloaded
            }
            if (!shouldAutoScan) return@launch
            delay(450)
            scanAllRoots()
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val syncingAlbums: StateFlow<Map<Long, SyncStatus>> = taskCoordinator.syncingAlbums

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<LibraryUiState> = taskCoordinator.bulkProgress
        .combine(syncingAlbums) { bulk, syncing ->
            if (bulk != null) LibraryUiState.BulkInProgress(bulk) else LibraryUiState.Success(syncing)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState.Success())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val pagedAlbums = filterHolder.querySpec
        .map { it }
        .distinctUntilChanged()
        .flatMapLatest { spec ->
            Pager(
                config = PagingConfig(pageSize = 40, prefetchDistance = 10, enablePlaceholders = false),
                pagingSourceFactory = { libraryReadRepository.albumsPaged(spec) }
            ).flow
        }
        .map { paging -> paging.map { entity -> entity.toAlbum() } }
        .flowOn(Dispatchers.Default)
        .cachedIn(viewModelScope)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val pagedTrackAlbumHeaders = filterHolder.querySpec
        .map { it }
        .distinctUntilChanged()
        .flatMapLatest { spec ->
            Pager(
                config = PagingConfig(pageSize = 40, prefetchDistance = 10, enablePlaceholders = false),
                pagingSourceFactory = { libraryReadRepository.libraryTrackAlbumHeadersPaged(spec) }
            ).flow
        }
        .cachedIn(viewModelScope)

    private fun AlbumEntity.toAlbum(): Album {
        val baseTags = tags
            .split(",")
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()
        return Album(
            id = id,
            title = titleForDisplay,
            displayTitle = displayTitle,
            path = path,
            localPath = localPath,
            downloadPath = downloadPath,
            circle = circle,
            cv = cv,
            tags = baseTags,
            coverUrl = coverUrl,
            coverPath = coverPath,
            coverThumbPath = coverThumbPath,
            workId = workId,
            rjCode = rjCode.ifBlank { workId },
            audioTrackCount = audioTrackCount,
            audioTotalDuration = audioTotalDuration,
            audioTotalSizeBytes = audioTotalSizeBytes,
            description = description
        )
    }

    suspend fun loadInheritedTagsForAlbum(albumId: Long): List<String> =
        tagHolder.loadInheritedTagsForAlbum(albumId)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val expandedTrackAlbumTracks: StateFlow<Map<Long, List<LibraryTrackRow>>> = _expandedTrackAlbumIds
        .combine(filterHolder.querySpec) { ids, spec -> ids to spec }
        .distinctUntilChanged()
        .flatMapLatest { (ids, spec) ->
            val normalized = ids.asSequence().filter { it > 0L }.distinct().toList()
            if (normalized.isEmpty()) {
                flowOf(emptyMap())
            } else {
                val flows = normalized.map { albumId ->
                    libraryReadRepository.observeLibraryTracksForAlbum(spec, albumId)
                        .map { rows -> albumId to rows }
                }
                combine(flows) { pairs -> pairs.toMap() }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun toggleExpandedTrackAlbum(albumId: Long) {
        if (albumId <= 0L) return
        _expandedTrackAlbumIds.update { albumIds ->
            if (albumId in albumIds) albumIds - albumId else albumIds + albumId
        }
    }

    fun clearExpandedTrackAlbums() {
        _expandedTrackAlbumIds.value = emptySet()
    }

    val libraryViewMode: StateFlow<Int?> = settingsRepository.libraryViewMode
        .map<Int, Int?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setSearchQuery(query: String) = filterHolder.setSearchQuery(query)

    fun setSort(sort: LibrarySort) = filterHolder.setSort(sort)

    fun setSourceFilter(filter: LibrarySourceFilter?) = filterHolder.setSourceFilter(filter)

    fun applyFilters(spec: LibraryQuerySpec) = filterHolder.applyFilters(spec)

    fun toggleTag(tagId: Long) = filterHolder.toggleTag(tagId)

    fun toggleCircle(circle: String) = filterHolder.toggleCircle(circle)

    fun toggleCv(cv: String) = filterHolder.toggleCv(cv)

    fun clearFilters() = filterHolder.clearFilters()

    fun applyPreset(preset: LibraryFilterPreset) = filterHolder.applyPreset(preset)

    fun savePreset(name: String, spec: LibraryQuerySpec = filterHolder.querySpec.value) = filterHolder.savePreset(name, spec)

    fun deletePreset(id: String) = filterHolder.deletePreset(id)

    fun setLibraryViewMode(mode: Int) = filterHolder.setLibraryViewMode(mode)

    fun setUserTagsForAlbum(albumId: Long, tagsCsv: String) = tagHolder.setUserTagsForAlbum(albumId, tagsCsv)

    fun setUserTagsForTrack(trackId: Long, tagsCsv: String) = tagHolder.setUserTagsForTrack(trackId, tagsCsv)

    fun renameUserTag(tagId: Long, newName: String) = tagHolder.renameUserTag(tagId, newName)

    fun deleteUserTag(tagId: Long) = tagHolder.deleteUserTag(tagId)

    // R3-C1b-ii：任务协调函数实现迁入 LibraryTaskCoordinator，VM 保留 UI 转发。

    fun cancelBulkTask() = taskCoordinator.cancelBulkTask()

    fun cancelAlbumTask(albumId: Long) = taskCoordinator.cancelAlbumTask(albumId)

    fun confirmCloudSyncSelection(workno: String) = taskCoordinator.confirmCloudSyncSelection(workno)

    fun cancelCloudSyncSelection() = taskCoordinator.cancelCloudSyncSelection()

    fun ignoreAllCloudSyncSelections() = taskCoordinator.ignoreAllCloudSyncSelections()

    // R3-C1c：扫描底层（封面挑选/树缓存叶/SAF 遍历/字幕匹配/WorkManager 入队）迁入 LibraryScanStateHolder。

    fun addScanRoot(uriString: String): Boolean {
        val existingRoots = scanRootsStore.getRoots()
        
        // 检查重复
        if (existingRoots.contains(uriString)) {
            messageManager.showInfo("扫描目录已存在")
            return false
        }
        
        // 检查嵌套：新目录是否是现有目录的子目录
        val newUri = runCatching { Uri.parse(uriString) }.getOrNull()
        if (newUri != null) {
            for (existingRoot in existingRoots) {
                val existingUri = runCatching { Uri.parse(existingRoot) }.getOrNull() ?: continue
                
                // 检查新目录是否是现有目录的子目录
                if (isSubdirectory(newUri, existingUri)) {
                    messageManager.showInfo("该目录已被包含在现有扫描目录中")
                    return false
                }
                
                // 检查现有目录是否是新目录的子目录
                if (isSubdirectory(existingUri, newUri)) {
                    messageManager.showInfo("该目录包含了现有的扫描目录，请先移除子目录")
                    return false
                }
            }
        }
        
        val added = scanRootsStore.addRoot(uriString)
        _scanRoots.value = runCatching { scanRootsStore.getRoots() }.getOrDefault(emptySet())
        if (added) {
            messageManager.showSuccess("已添加扫描目录")
        }
        return added
    }
    
    private fun isSubdirectory(child: Uri, parent: Uri): Boolean {
        // 如果是相同的 URI scheme 和 authority
        if (child.scheme != parent.scheme || child.authority != parent.authority) {
            return false
        }
        
        // 获取文档树 ID
        val childTreeId = runCatching { 
            DocumentsContract.getTreeDocumentId(child) 
        }.getOrNull() ?: return false
        
        val parentTreeId = runCatching { 
            DocumentsContract.getTreeDocumentId(parent) 
        }.getOrNull() ?: return false
        
        // 检查子目录关系
        return childTreeId.startsWith(parentTreeId) && childTreeId != parentTreeId
    }

    fun removeScanRoot(uriString: String) {
        scanRootsStore.removeRoot(uriString)
        _scanRoots.value = runCatching { scanRootsStore.getRoots() }.getOrDefault(emptySet())
        messageManager.showInfo("已移除扫描目录")
    }

    fun removeScanRootAndDeleteAlbums(uriString: String) {
        viewModelScope.launch(Dispatchers.IO) {
            scanRootsStore.removeRoot(uriString)
            _scanRoots.value = runCatching { scanRootsStore.getRoots() }.getOrDefault(emptySet())

            val allAlbums = libraryReadRepository.getAllAlbumsOnce()
            val affected = allAlbums.filter { entity ->
                entity.path.startsWith(uriString) ||
                    (entity.localPath?.startsWith(uriString) == true) ||
                    entity.coverPath.startsWith(uriString)
            }

            affected.forEach { entity ->
                val downloadPath = entity.downloadPath
                val keepByDownload = !downloadPath.isNullOrBlank() &&
                    !downloadPath.startsWith("content://") &&
                    runCatching { File(downloadPath).exists() }.getOrDefault(false)

                if (!keepByDownload) {
                    val tracks = libraryReadRepository.getTracksForAlbumOnce(entity.id)
                    val hasOnline = isVirtualAlbumPath(entity.path) || tracks.any { isOnlineTrackPath(it.path) }
                    if (!hasOnline) {
                        libraryWriteRepository.deleteAlbumTracksAndSubtitles(entity.id)
                        libraryWriteRepository.deleteAlbumEntity(entity)
                    } else {
                        tracks.filter { it.path.startsWith(uriString) }.forEach { track ->
                            libraryWriteRepository.deleteTrackWithSubtitlesById(track.id)
                        }
                        val updatedPath = if (entity.path.startsWith(uriString)) (buildOnlineAlbumPath(entity) ?: entity.path) else entity.path
                        val updated = entity.copy(
                            path = updatedPath,
                            localPath = entity.localPath?.takeIf { !it.startsWith(uriString) },
                            coverPath = if (entity.coverPath.startsWith(uriString)) "" else entity.coverPath
                        )
                        libraryWriteRepository.updateAlbum(updated)
                        upsertAlbumFtsIndex(updated.id, updated)
                    }
                } else {
                    val tracks = libraryReadRepository.getTracksForAlbumOnce(entity.id)
                    tracks.filter { it.path.startsWith(uriString) }.forEach { track ->
                        libraryWriteRepository.deleteTrackWithSubtitlesById(track.id)
                    }

                    val updated = entity.copy(
                        path = if (entity.path.startsWith(uriString)) downloadPath!! else entity.path,
                        localPath = entity.localPath?.takeIf { !it.startsWith(uriString) },
                        coverPath = if (entity.coverPath.startsWith(uriString)) "" else entity.coverPath
                    )
                    libraryWriteRepository.updateAlbum(updated)
                    upsertAlbumFtsIndex(updated.id, updated)
                }
            }
        }
    }

    fun scanAllRoots() {
        viewModelScope.launch {
            val token = syncCoordinator.tryBegin() ?: run {
                taskCoordinator.showSyncBusy("刷新本地")
                return@launch
            }
            try {
                taskCoordinator.bulkStartMutex.withLock {
                    taskCoordinator.bulkJob = currentCoroutineContext()[Job]
                    try {
                        withContext(Dispatchers.IO) {
                            scanMutex.withLock {
                                currentCoroutineContext().ensureActive()
                                val roots = scanRootsStore.getRoots().toList()
                                val downloadedAlbumCount = runCatching {
                                    when (val destination = downloadDestinationStore.current()) {
                                        is DownloadDestination.Default -> File(destination.root).listFiles()
                                            ?.count {
                                                it.isDirectory && isScannableLocalDirectoryName(it.name) &&
                                                    File(it, ".download_complete").exists()
                                            }
                                            ?: 0
                                        is DownloadDestination.DocumentTree -> {
                                            val uri = Uri.parse(destination.root)
                                            val treeId = DocumentsContract.getTreeDocumentId(uri)
                                            scanHolder.queryChildren(uri, treeId).count { child ->
                                                child.mimeType == DocumentsContract.Document.MIME_TYPE_DIR &&
                                                    isScannableLocalDirectoryName(child.displayName) &&
                                                    scanHolder.queryChildren(uri, child.documentId).any { it.displayName == ".download_complete" }
                                            }
                                        }
                                    }
                                }.getOrDefault(0)

                                var totalAlbums = downloadedAlbumCount
                                roots.forEach { root ->
                                    currentCoroutineContext().ensureActive()
                                    val uri = runCatching { Uri.parse(root) }.getOrNull()
                                    val treeDocId = uri?.let { runCatching { DocumentsContract.getTreeDocumentId(it) }.getOrNull() }
                                    if (uri != null && !treeDocId.isNullOrBlank()) {
                                        totalAlbums += scanHolder.queryChildren(uri, treeDocId).count {
                                            it.mimeType == DocumentsContract.Document.MIME_TYPE_DIR &&
                                                isScannableLocalDirectoryName(it.displayName)
                                        }
                                    }
                                }
                                taskCoordinator.startBulkProgress(phase = BulkPhase.ScanningLocal, total = totalAlbums)

                                var current = 0
                                roots.forEach { root ->
                                    currentCoroutineContext().ensureActive()
                                    scanHolder.scanFromDocumentTree(root) { title ->
                                        current += 1
                                        taskCoordinator.updateBulkAlbumProgress(current = current, currentAlbumTitle = title)
                                    }
                                }
                                scanHolder.scanFromDownloadedDir { title ->
                                    current += 1
                                    taskCoordinator.updateBulkAlbumProgress(current = current, currentAlbumTitle = title)
                                }
                                scanHolder.pruneOrphanedAlbumsByFilesystem()
                            }
                        }
                        messageManager.showSuccess("扫描完成")
                    } catch (e: CancellationException) {
                        messageManager.showInfo("已取消扫描")
                    } catch (e: Exception) {
                        Log.e("LibraryViewModel", "scanAllRoots failed", e)
                        messageManager.showError("扫描失败：${e.message}")
                    } finally {
                        taskCoordinator.finishBulkProgress()
                        if (taskCoordinator.bulkJob == currentCoroutineContext()[Job]) {
                            taskCoordinator.bulkJob = null
                        }
                    }
                }
            } finally {
                syncCoordinator.end(token)
            }
        }
    }

    fun scanCurrentDownloadDestinationAsImport() {
        viewModelScope.launch {
            val token = syncCoordinator.tryBegin() ?: run {
                taskCoordinator.showSyncBusy("扫描目标下载目录")
                return@launch
            }
            try {
                taskCoordinator.bulkStartMutex.withLock {
                    taskCoordinator.bulkJob = currentCoroutineContext()[Job]
                    try {
                        withContext(Dispatchers.IO) {
                            scanMutex.withLock {
                                currentCoroutineContext().ensureActive()
                                val destination = downloadDestinationStore.current()
                                val totalAlbums = when (destination) {
                                    is DownloadDestination.Default -> File(destination.root).listFiles()
                                        ?.count { it.isDirectory && isScannableLocalDirectoryName(it.name) }
                                        ?: 0

                                    is DownloadDestination.DocumentTree -> {
                                        val uri = Uri.parse(destination.root)
                                        val treeId = DocumentsContract.getTreeDocumentId(uri)
                                        scanHolder.queryChildren(uri, treeId).count {
                                            it.mimeType == DocumentsContract.Document.MIME_TYPE_DIR &&
                                                isScannableLocalDirectoryName(it.displayName)
                                        }
                                    }
                                }
                                taskCoordinator.startBulkProgress(phase = BulkPhase.ScanningLocal, total = totalAlbums)
                                var current = 0
                                scanHolder.scanFromDownloadedDir(
                                    onAlbumScanned = { title ->
                                        current += 1
                                        taskCoordinator.updateBulkAlbumProgress(current = current, currentAlbumTitle = title)
                                    },
                                    importAll = true,
                                )
                            }
                        }
                        messageManager.showSuccess("目标下载目录扫描完成")
                    } catch (error: CancellationException) {
                        messageManager.showInfo("已取消目标目录扫描")
                    } catch (error: Exception) {
                        Log.e(TAG, "scanCurrentDownloadDestinationAsImport failed", error)
                        messageManager.showError("目标目录扫描失败：${error.message}")
                    } finally {
                        taskCoordinator.finishBulkProgress()
                        if (taskCoordinator.bulkJob == currentCoroutineContext()[Job]) taskCoordinator.bulkJob = null
                    }
                }
            } finally {
                syncCoordinator.end(token)
            }
        }
    }

    fun scanSingleRoot(uriString: String) {
        if (uriString.isBlank()) return
        viewModelScope.launch {
            val token = syncCoordinator.tryBegin() ?: run {
                taskCoordinator.showSyncBusy("刷新目录")
                return@launch
            }
            try {
                taskCoordinator.bulkStartMutex.withLock {
                    taskCoordinator.bulkJob = currentCoroutineContext()[Job]
                    try {
                        withContext(Dispatchers.IO) {
                            scanMutex.withLock {
                                currentCoroutineContext().ensureActive()
                                val uri = runCatching { Uri.parse(uriString) }.getOrNull()
                                val treeDocId = uri?.let { runCatching { DocumentsContract.getTreeDocumentId(it) }.getOrNull() }
                                val totalAlbums = if (uri != null && !treeDocId.isNullOrBlank()) {
                                    scanHolder.queryChildren(uri, treeDocId).count {
                                        it.mimeType == DocumentsContract.Document.MIME_TYPE_DIR &&
                                            isScannableLocalDirectoryName(it.displayName)
                                    }
                                } else {
                                    0
                                }
                                taskCoordinator.startBulkProgress(phase = BulkPhase.ScanningLocal, total = totalAlbums)
                                var current = 0
                                scanHolder.scanFromDocumentTree(uriString) { title ->
                                    current += 1
                                    taskCoordinator.updateBulkAlbumProgress(current = current, currentAlbumTitle = title)
                                }
                            }
                        }
                        messageManager.showSuccess("目录刷新完成")
                    } catch (e: CancellationException) {
                        messageManager.showInfo("已取消刷新")
                    } catch (e: Exception) {
                        Log.e("LibraryViewModel", "scanSingleRoot failed", e)
                        messageManager.showError("刷新失败：${e.message}")
                    } finally {
                        taskCoordinator.finishBulkProgress()
                        if (taskCoordinator.bulkJob == currentCoroutineContext()[Job]) {
                            taskCoordinator.bulkJob = null
                        }
                    }
                }
            } finally {
                syncCoordinator.end(token)
            }
        }
    }

    fun syncMetadata() {
        viewModelScope.launch {
            val token = syncCoordinator.tryBegin() ?: run {
                taskCoordinator.showSyncBusy("云同步")
                return@launch
            }
            try {
                taskCoordinator.bulkStartMutex.withLock {
                    taskCoordinator.bulkJob = currentCoroutineContext()[Job]
                    try {
                        val albums = withContext(Dispatchers.IO) { libraryReadRepository.getAllAlbumsOnce() }
                        runBatchCloudSync(albums)
                        messageManager.showSuccess("全量同步完成")
                    } catch (e: CancellationException) {
                        messageManager.showInfo("已取消云同步")
                    } catch (e: Exception) {
                        Log.e("LibraryViewModel", "syncMetadata failed", e)
                        messageManager.showError("云同步失败：${e.message}")
                    } finally {
                        taskCoordinator.finishBulkProgress()
                        if (taskCoordinator.bulkJob == currentCoroutineContext()[Job]) {
                            taskCoordinator.bulkJob = null
                        }
                    }
                }
            } finally {
                syncCoordinator.end(token)
            }
        }
    }

    fun syncMetadataForRoot(uriString: String) {
        if (uriString.isBlank()) return
        viewModelScope.launch {
            val token = syncCoordinator.tryBegin() ?: run {
                taskCoordinator.showSyncBusy("云同步")
                return@launch
            }
            try {
                taskCoordinator.bulkStartMutex.withLock {
                    taskCoordinator.bulkJob = currentCoroutineContext()[Job]
                    try {
                        val albums = withContext(Dispatchers.IO) {
                            libraryReadRepository.getAllAlbumsOnce()
                                .filter { entity ->
                                    entity.path.startsWith(uriString) || (entity.localPath?.startsWith(uriString) == true)
                                }
                        }
                        runBatchCloudSync(albums)
                        messageManager.showSuccess("云同步完成")
                    } catch (e: CancellationException) {
                        messageManager.showInfo("已取消云同步")
                    } catch (e: Exception) {
                        Log.e("LibraryViewModel", "syncMetadataForRoot failed", e)
                        messageManager.showError("云同步失败：${e.message}")
                    } finally {
                        taskCoordinator.finishBulkProgress()
                        if (taskCoordinator.bulkJob == currentCoroutineContext()[Job]) {
                            taskCoordinator.bulkJob = null
                        }
                    }
                }
            } finally {
                syncCoordinator.end(token)
            }
        }
    }

    fun syncAlbumMetadata(album: Album) {
        if (!taskCoordinator.tryRegisterAlbumJob(album.id, "云同步")) return
        val job = viewModelScope.launch {
            val ownerJob = currentCoroutineContext()[Job]
            val token = syncCoordinator.tryBegin() ?: run {
                taskCoordinator.showSyncBusy("云同步")
                taskCoordinator.albumJobs.remove(album.id, ownerJob)
                return@launch
            }
            try {
                val entity = withContext(Dispatchers.IO) { libraryReadRepository.getAlbumById(album.id) } ?: return@launch
                withContext(Dispatchers.IO) { syncAlbumMetadataInternal(entity) }
            } catch (e: CancellationException) {
                messageManager.showInfo("已取消云同步")
            } finally {
                taskCoordinator.albumJobs.remove(album.id, ownerJob)
                syncCoordinator.end(token)
            }
        }
        taskCoordinator.albumJobs[album.id] = job
    }

    private suspend fun runBatchCloudSync(albums: List<AlbumEntity>) = coroutineScope {
        taskCoordinator.startBulkProgress(phase = BulkPhase.SyncingCloud, total = albums.size)
        taskCoordinator.cloudSyncSelectionQueue.beginBatchSession()
        try {
        val pendingSelections = mutableListOf<Deferred<Unit>>()
        var current = 0
        albums.forEach { entity ->
            currentCoroutineContext().ensureActive()
            current += 1
            taskCoordinator.updateBulkAlbumProgress(current = current, currentAlbumTitle = entity.title)
            withContext(Dispatchers.IO) {
                syncAlbumMetadataInternal(
                    entity = entity,
                    silent = true,
                    onAmbiguous = { pendingEntity, result ->
                        pendingSelections += this@coroutineScope.async(Dispatchers.IO) {
                            continueSyncAlbumMetadataAfterSelection(
                                entity = pendingEntity,
                                candidates = result.candidates,
                                silent = true
                            )
                        }
                    }
                )
            }
        }
        val pendingCount = taskCoordinator.cloudSyncSelectionQueue.pendingCount()
        if (pendingCount > 0) {
            messageManager.showInfo("主流程已完成，剩余${pendingCount}项待确认")
        }
        pendingSelections.awaitAll()
        } finally {
            taskCoordinator.cloudSyncSelectionQueue.endBatchSession()
        }
    }
    private suspend fun resolveAlbumCloudSync(entity: AlbumEntity): DlsiteCloudSyncResolveResult {
        return onlineContentRepository.resolveManualCloudSync(
            entity = entity,
            baseWorkno = entity.rjCode.ifBlank { entity.workId }.trim().uppercase()
        )
    }

    private suspend fun resolveSelectedAlbumCloudSync(workno: String): DlsiteCloudSyncResolveResult {
        return onlineContentRepository.resolveSelectedManualCloudSync(workno)
    }

    private suspend fun applyResolvedCloudSync(
        entity: AlbumEntity,
        result: DlsiteCloudSyncResolveResult.Success
    ) {
        val resolvedWorkno = result.workno
        val details = result.details
        val updated = entity.copy(
            title = details.title.ifBlank { entity.title },
            circle = details.circle.ifBlank { entity.circle },
            cv = details.cv.ifBlank { entity.cv },
            tags = if (details.tags.isNotEmpty()) details.tags.joinToString(",") else entity.tags,
            coverUrl = details.coverUrl.ifBlank { entity.coverUrl },
            description = details.description.ifBlank { entity.description },
            workId = resolveCloudSyncWorkId(entity.workId, resolvedWorkno),
            rjCode = resolvedWorkno
        )
        libraryWriteRepository.updateAlbum(updated)
        upsertAlbumFtsIndex(updated.id, updated)
        upsertAlbumTagsFromCsv(updated.id, updated.tags, TagSource.AUTO)
        if (updated.coverPath.trim().isBlank() && updated.coverThumbPath.trim().isBlank()) {
            runCatching {
                ensureAlbumCoverSaved(updated.id, updated.coverPath, updated.coverUrl)
            }
        }
    }

    private suspend fun continueSyncAlbumMetadataAfterSelection(
        entity: AlbumEntity,
        candidates: List<DlsiteCloudSyncCandidate>,
        silent: Boolean
    ) {
        try {
            val selectedWorkno = taskCoordinator.cloudSyncSelectionQueue.enqueue(
                albumId = entity.id.takeIf { it > 0L },
                albumTitle = entity.title,
                candidates = candidates
            ).await()
            currentCoroutineContext().ensureActive()
            if (selectedWorkno != null) {
                when (val selectedResult = resolveSelectedAlbumCloudSync(selectedWorkno)) {
                    is DlsiteCloudSyncResolveResult.Success -> {
                        applyResolvedCloudSync(entity, selectedResult)
                        if (!silent) {
                            messageManager.showSuccess("元数据同步成功")
                        }
                    }

                    is DlsiteCloudSyncResolveResult.Ambiguous -> {
                        if (!silent) {
                            messageManager.showError("同步失败：搜索结果不唯一")
                        }
                    }

                    DlsiteCloudSyncResolveResult.NotFound -> {
                        if (!silent) {
                            messageManager.showError("同步失败：未找到专辑信息")
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportSyncAlbumMetadataFailure(entity.id, e, silent)
            return
        } finally {
            taskCoordinator.syncStatus.value -= entity.id
        }
    }

    private suspend fun reportSyncAlbumMetadataFailure(entityId: Long, error: Exception, silent: Boolean) {
        Log.e("LibraryViewModel", "syncAlbumMetadataInternal failed: $entityId", error)
        taskCoordinator.syncStatus.value += (entityId to SyncStatus.Error(error.message ?: "同步失败"))
        if (!silent) {
            messageManager.showError("同步异常：${error.message}")
            delay(3000)
        }
        taskCoordinator.syncStatus.value -= entityId
    }

    private suspend fun syncAlbumMetadataInternal(
        entity: AlbumEntity,
        silent: Boolean = false,
        onAmbiguous: (suspend (AlbumEntity, DlsiteCloudSyncResolveResult.Ambiguous) -> Unit)? = null
    ) {
        val keyword = entity.title.trim()
        val currentWorkno = entity.rjCode.ifBlank { entity.workId }.trim().uppercase()
        if (currentWorkno.isBlank() && keyword.isBlank()) return

        taskCoordinator.syncStatus.value += (entity.id to SyncStatus.Syncing)
        var clearSyncStatus = true
        try {
            val result = resolveAlbumCloudSync(entity)
            currentCoroutineContext().ensureActive()
            when (result) {
                is DlsiteCloudSyncResolveResult.Success -> {
                    applyResolvedCloudSync(entity, result)
                    if (!silent) messageManager.showSuccess("元数据同步成功")
                }

                is DlsiteCloudSyncResolveResult.Ambiguous -> {
                    clearSyncStatus = false
                    if (onAmbiguous != null) {
                        onAmbiguous(entity, result)
                    } else {
                        continueSyncAlbumMetadataAfterSelection(entity, result.candidates, silent)
                    }
                    return
                }

                DlsiteCloudSyncResolveResult.NotFound -> {
                    if (!silent) messageManager.showError("同步失败：未找到专辑信息")
                }
            }
            if (clearSyncStatus) {
                taskCoordinator.syncStatus.value -= entity.id
            }
        } catch (e: CancellationException) {
            if (clearSyncStatus) {
                taskCoordinator.syncStatus.value -= entity.id
            }
            throw e
        } catch (e: Exception) {
            Log.e("LibraryViewModel", "syncAlbumMetadataInternal failed: ${entity.id}", e)
            taskCoordinator.syncStatus.value += (entity.id to SyncStatus.Error(e.message ?: "同步失败"))
            if (!silent) messageManager.showError("同步异常：${e.message}")
            if (!silent) delay(3000)
            taskCoordinator.syncStatus.value -= entity.id
        }
    }

    private suspend fun ensureAlbumCoverSaved(
        albumId: Long,
        coverPath: String,
        coverUrl: String
    ): Boolean {
        fun debugLog(msg: String) {
            if (BuildConfig.DEBUG) Log.d("LibraryViewModel", msg)
        }
        fun fail(reason: String): Boolean {
            debugLog("ensureAlbumCoverSaved fail albumId=$albumId reason=$reason coverPath=${coverPath.take(160)} coverUrl=${coverUrl.take(160)}")
            return false
        }

        val existingPathRaw = coverPath.trim().takeIf { it.isNotBlank() && it != "null" }
        if (existingPathRaw != null && !existingPathRaw.startsWith("content://", ignoreCase = true)) {
            val f = if (existingPathRaw.startsWith("file://", ignoreCase = true)) {
                runCatching { Uri.parse(existingPathRaw).path.orEmpty() }.getOrNull()?.let { File(it) }
            } else {
                File(existingPathRaw)
            }
            if (f != null && f.exists() && f.length() > 0L) return true
        }

        val url = coverUrl.trim().takeIf { it.isNotBlank() && it != "null" }?.let { u ->
            if (u.startsWith("//")) "https:$u" else u
        }.orEmpty()
        val canUseNetwork = url.isNotBlank() && !com.asmr.player.util.isLikelyPlaceholderCover(url)
        if (!canUseNetwork) return fail("no_network_cover")

        val sourceHash = url.hashCode().toString()
        val coverDir = File(context.filesDir, "album_covers").apply { if (!exists()) mkdirs() }
        val thumbDir = File(context.filesDir, "album_thumbs").apply { if (!exists()) mkdirs() }
        val coverFile = File(coverDir, "a_${albumId}_$sourceHash.jpg")
        val thumbFile = File(thumbDir, "a_${albumId}_${sourceHash}_v2.jpg")

        if (coverFile.exists() && coverFile.length() > 0L && thumbFile.exists() && thumbFile.length() > 0L) {
            val entity = runCatching { libraryReadRepository.getAlbumById(albumId) }.getOrNull()
            if (entity != null && (entity.coverPath != coverFile.absolutePath || entity.coverThumbPath != thumbFile.absolutePath)) {
                runCatching { libraryWriteRepository.updateAlbum(entity.copy(coverPath = coverFile.absolutePath, coverThumbPath = thumbFile.absolutePath)) }
            }
            return true
        }

        val tmpFile = File(coverDir, "a_${albumId}_$sourceHash.tmp")
        val bitmap = try {
            val req = Request.Builder()
                .url(url)
                .header("Accept", "image/*")
                .get()
                .build()
            imageOkHttpClient.newCall(req).execute().use { resp ->
                val contentType = resp.header("Content-Type").orEmpty()
                debugLog("ensureAlbumCoverSaved http albumId=$albumId code=${resp.code} type=$contentType url=${url.take(160)}")
                if (!resp.isSuccessful) return fail("http_${resp.code}")
                if (contentType.isNotBlank() && !contentType.startsWith("image/", ignoreCase = true)) return fail("not_image_$contentType")
                val body = resp.body ?: return fail("empty_body")
                body.byteStream().use { input ->
                    FileOutputStream(tmpFile).use { out ->
                        val buf = ByteArray(256 * 1024)
                        while (true) {
                            val read = input.read(buf)
                            if (read <= 0) break
                            out.write(buf, 0, read)
                        }
                        out.flush()
                    }
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tmpFile.absolutePath, bounds)
            val w = bounds.outWidth
            val h = bounds.outHeight
            if (w <= 0 || h <= 0) return fail("decode_bounds_invalid")
            val maxDim = maxOf(w, h)
            var sample = 1
            while (maxDim / sample > 2048) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(tmpFile.absolutePath, opts) ?: return fail("decode_failed_sample_$sample")
        } finally {
            runCatching { if (tmpFile.exists()) tmpFile.delete() }
        }

        FileOutputStream(coverFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        val thumb = centerCropSquare(bitmap, 640)
        FileOutputStream(thumbFile).use { out ->
            thumb.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }

        return runCatching {
            val entity = libraryReadRepository.getAlbumById(albumId) ?: return true
            libraryWriteRepository.updateAlbum(entity.copy(coverPath = coverFile.absolutePath, coverThumbPath = thumbFile.absolutePath))
            debugLog("ensureAlbumCoverSaved ok albumId=$albumId cover=${coverFile.length()} thumb=${thumbFile.length()}")
            true
        }.getOrElse { e ->
            fail("db_update_${e.javaClass.simpleName}")
        }
    }

    // R3-C1b-ii：删除族实现迁入 LibraryDeleteStateHolder（rescanAlbum 对扫描底层的依赖经构造引用过渡，C1c 换扫描 holder 注入），VM 保留 UI 转发。

    fun rescanAlbum(album: Album) = deleteHolder.rescanAlbum(album)

    fun deleteAlbum(album: Album) = deleteHolder.deleteAlbum(album)

    internal fun deleteAlbumTreeEntry(
        album: Album,
        target: LocalTreeDeletionTarget,
        onComplete: (Boolean) -> Unit = {},
    ) = deleteHolder.deleteAlbumTreeEntry(album, target, onComplete)

    fun removeTrackFromAlbum(trackId: Long) = deleteHolder.removeTrackFromAlbum(trackId)

    // R3-C1b：SAF 树/删除 helper 实现下沉 data/local/tree/SafTreeSupport。
    // R3-C1b-ii：删除侧委托（deletePathSafely/deleteLocalTreeFile/deleteLocalTreeDirectories/
    // isCanonicalDescendant）随删除族迁 holder 后直调 SafTreeSupport，VM 委托消除；
    // resolveTreeDocumentUri 为零引用死委托一并清除（调用点清单：全仓 grep 仅定义处）。

    // R3-C1c：extractWorkNo / legacyOnlineSavedAlbumDir / backfillLegacyOnlineSavedAlbumRoots
    // 及以下扫描底层函数迁入 LibraryScanStateHolder；upsertAlbumFtsIndex / upsertAlbumTagsFromCsv
    // 委托暂留（云同步族 C1d 迁出时随迁）。

    private suspend fun upsertAlbumFtsIndex(albumId: Long, entity: AlbumEntity) {
        libraryWriteRepository.upsertAlbumFtsIndex(albumId, entity)
    }

    private suspend fun upsertAlbumTagsFromCsv(albumId: Long, tagsCsv: String, source: Int) {
        libraryWriteRepository.upsertAlbumTagsFromCsv(albumId, tagsCsv, source)
    }

    // R3-C1c：SafTreeSupport 委托随迁消除（queryChildren/walkTree/documentExists/readSubtitleFromUri
    // 由 LibraryScanStateHolder 直调实现）。

    override fun onCleared() {
        taskCoordinator.cloudSyncSelectionQueue.cancelAll()
        super.onCleared()
    }
}

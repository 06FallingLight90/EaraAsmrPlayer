package com.asmr.player.ui.library

import android.content.Context
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
import com.asmr.player.data.download.DownloadDestination
import com.asmr.player.data.download.DownloadDestinationStore
import com.asmr.player.data.repository.DownloadQueueRepository
import com.asmr.player.data.repository.LibraryReadRepository
import com.asmr.player.data.repository.LibraryWriteRepository
import com.asmr.player.data.repository.OnlineContentRepository
import com.asmr.player.data.settings.SettingsRepository
import com.asmr.player.playback.PlayerConnection
import com.asmr.player.util.GlobalSyncState
import com.asmr.player.util.BulkPhase
import com.asmr.player.util.BulkProgress
import com.asmr.player.util.BulkProgressStore
import com.asmr.player.util.MessageManager
import com.asmr.player.util.SyncCoordinator
import com.asmr.player.util.isScannableLocalDirectoryName
import com.asmr.player.util.parseAlbumTags
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import okhttp3.OkHttpClient
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

// T11：BulkPhase/BulkProgress 迁至 util（应用级进度通道 BulkProgressStore 与扫描 Worker 共用），import 见文件头。

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
    private val bulkProgressStore: BulkProgressStore,
    private val scanPipeline: LibraryScanPipeline,
    @Named("image") private val imageOkHttpClient: OkHttpClient,
    val messageManager: MessageManager,
    private val playerConnection: PlayerConnection,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private companion object {
        const val TAG = "LibraryViewModel"
    }

    /** R3-C1b-ii：任务协调 State Holder（单专辑任务注册表/批量任务门/同步状态/批量进度/云同步选择队列）。 */
    private val taskCoordinator = LibraryTaskCoordinator(messageManager, bulkProgressStore)

    /** R3-C1c：扫描族 State Holder（扫描根管理 + 批量入口调度；扫描底层已下沉 scan.LibraryScanPipeline）。 */
    private val scanHolder = LibraryScanStateHolder(
        scope = viewModelScope,
        context = context,
        readRepository = libraryReadRepository,
        writeRepository = libraryWriteRepository,
        syncCoordinator = syncCoordinator,
        taskCoordinator = taskCoordinator,
        messageManager = messageManager,
        pipeline = scanPipeline,
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

    /** R3-C1d：云同步族 State Holder（全量/按根/单专辑云同步 + applyResolvedCloudSync title 覆盖语义 + ensureAlbumCoverSaved）。 */
    private val cloudHolder = LibraryCloudSyncStateHolder(
        scope = viewModelScope,
        context = context,
        readRepository = libraryReadRepository,
        writeRepository = libraryWriteRepository,
        onlineContentRepository = onlineContentRepository,
        syncCoordinator = syncCoordinator,
        taskCoordinator = taskCoordinator,
        messageManager = messageManager,
        imageOkHttpClient = imageOkHttpClient,
    )
    val scanRoots: StateFlow<List<String>> get() = scanHolder.scanRoots
    val bulkProgress: StateFlow<BulkProgress?> = taskCoordinator.bulkProgress
    internal val cloudSyncSelectionDialogState: StateFlow<CloudSyncSelectionDialogState?> = taskCoordinator.cloudSyncSelectionQueue.dialogState
    val globalSyncState: StateFlow<GlobalSyncState?> = syncCoordinator.state
    val isGlobalSyncRunning: StateFlow<Boolean> = globalSyncState
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), globalSyncState.value != null)
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
            scanHolder.restoreScanRootsFromStore()
            tagHolder.ensureTagTablesInitialized()
            filterHolder.restoreLibraryPreferences()
            scanHolder.backfillLegacyOnlineSavedAlbumRoots()
        }
        viewModelScope.launch {
            val shouldAutoScan = withContext(Dispatchers.IO) {
                val hasAnyAlbum = runCatching { libraryReadRepository.getAllAlbumsOnce().isNotEmpty() }.getOrDefault(false)
                if (hasAnyAlbum) return@withContext false
                val hasRoots = runCatching { scanHolder.getRootsFromStore().isNotEmpty() }.getOrDefault(false)
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
            scanHolder.scanAllRoots()
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
    // T11：批量扫描已下沉 CoroutineWorker——取消时先撤销 unique work（运行中/排队中的扫描），
    // 再走原任务协调取消（VM 侧批量任务如云同步不受影响）。

    fun cancelBulkTask() {
        scanHolder.cancelScheduledScanWork()
        taskCoordinator.cancelBulkTask()
    }

    fun cancelAlbumTask(albumId: Long) = taskCoordinator.cancelAlbumTask(albumId)

    fun confirmCloudSyncSelection(workno: String) = taskCoordinator.confirmCloudSyncSelection(workno)

    fun cancelCloudSyncSelection() = taskCoordinator.cancelCloudSyncSelection()

    fun ignoreAllCloudSyncSelections() = taskCoordinator.ignoreAllCloudSyncSelections()

    // R3-C1c：扫描底层与扫描根管理/三批量入口均迁入 LibraryScanStateHolder，VM 保留 UI 转发。

    fun addScanRoot(uriString: String): Boolean = scanHolder.addScanRoot(uriString)

    fun removeScanRoot(uriString: String) = scanHolder.removeScanRoot(uriString)

    fun removeScanRootAndDeleteAlbums(uriString: String) = scanHolder.removeScanRootAndDeleteAlbums(uriString)

    fun scanAllRoots() = scanHolder.scanAllRoots()

    fun scanCurrentDownloadDestinationAsImport() = scanHolder.scanCurrentDownloadDestinationAsImport()

    fun scanSingleRoot(uriString: String) = scanHolder.scanSingleRoot(uriString)

    // R3-C1d：云同步族实现迁入 LibraryCloudSyncStateHolder（applyResolvedCloudSync title 覆盖语义、
    // ensureAlbumCoverSaved 双实现均逐字随迁），VM 保留 UI 转发。

    fun syncMetadata() = cloudHolder.syncMetadata()

    fun syncMetadataForRoot(uriString: String) = cloudHolder.syncMetadataForRoot(uriString)

    fun syncAlbumMetadata(album: Album) = cloudHolder.syncAlbumMetadata(album)

    // R3-C1d：upsertAlbumFtsIndex / upsertAlbumTagsFromCsv 委托随云族迁出后 VM 零引用，删除。

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
    // 及扫描底层函数迁入 LibraryScanStateHolder；upsertAlbumFtsIndex / upsertAlbumTagsFromCsv
    // 委托已随云同步族（C1d）迁出，VM 零引用删除。

    // R3-C1c：SafTreeSupport 委托随迁消除（queryChildren/walkTree/documentExists/readSubtitleFromUri
    // 由 LibraryScanStateHolder 直调实现）。

    override fun onCleared() {
        taskCoordinator.cloudSyncSelectionQueue.cancelAll()
        super.onCleared()
    }
}

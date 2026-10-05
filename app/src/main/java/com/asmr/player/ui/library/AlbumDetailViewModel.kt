package com.asmr.player.ui.library

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.asmr.player.cache.AppCacheManager
import com.asmr.player.domain.model.TagWithCount
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.domain.model.TagSource
import com.asmr.player.data.local.db.entities.TrackEntity
import com.asmr.player.data.local.db.entities.titleForDisplay
import com.asmr.player.data.lyrics.LyricsLoader
import com.asmr.player.data.remote.ONLINE_DIRECTORY_REQUEST_TIMEOUT_MS
import com.asmr.player.data.remote.api.AsmrOneAvailabilityApi
import com.asmr.player.data.remote.api.AsmrOneEndpoint
import com.asmr.player.data.remote.api.AsmrOneTrackNodeResponse
import com.asmr.player.data.remote.api.AsmrOneRecommendationSeedFeatures
import com.asmr.player.data.remote.api.WorkDetailsResponse
import com.asmr.player.data.remote.auth.DlsiteAuthStore
import com.asmr.player.data.remote.crawler.AsmrOneCrawler
import com.asmr.player.data.remote.crawler.AsmrOneTracksResult
import com.asmr.player.data.remote.dlsite.DlsiteCloudSyncCandidate
import com.asmr.player.data.remote.dlsite.DlsiteCloudSyncResolveResult
import com.asmr.player.data.remote.dlsite.DlsiteLanguageEdition
import com.asmr.player.data.remote.dlsite.DlsitePlayLoadStatus
import com.asmr.player.data.remote.dlsite.DlsitePlayTreeResult
import com.asmr.player.data.remote.dlsite.DlsitePlayWorkClient
import com.asmr.player.data.remote.dlsite.DlsiteProductInfoClient
import com.asmr.player.data.remote.dlsite.resolveCloudSyncWorkId
import com.asmr.player.data.download.DownloadManager
import com.asmr.player.data.download.DownloadBatchRequest
import com.asmr.player.data.download.EnqueueDownloadBatchResult
import com.asmr.player.data.download.RelativeDownloadItem
import com.asmr.player.data.remote.scraper.DLSiteScraper
import com.asmr.player.data.remote.scraper.DlsiteRecommendedWork
import com.asmr.player.data.remote.scraper.DlsiteRecommendations
import com.asmr.player.data.repository.LibraryReadRepository
import com.asmr.player.data.repository.LibraryWriteRepository
import com.asmr.player.data.repository.OnlineContentRepository
import com.asmr.player.data.settings.SettingsRepository
import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.Track
import com.asmr.player.listentogether.ListenTogetherRepository
import com.asmr.player.ui.common.audio.queryTrackFileSize
import com.asmr.player.ui.nav.AlbumCoverHint
import com.asmr.player.ui.nav.AlbumCoverHintStore
import com.asmr.player.ui.nav.albumFromCoverHint
import com.asmr.player.util.DlsiteWorkNo
import com.asmr.player.util.ASMR_ONE_SITE_FAILURE_MESSAGE
import com.asmr.player.util.MessageManager
import com.asmr.player.util.OnlineLyricsStore
import com.asmr.player.domain.model.RemoteSubtitleSource
import com.asmr.player.util.SubtitleMatchSupport
import com.asmr.player.util.SyncCoordinator
import com.asmr.player.util.TagNormalizer
import com.asmr.player.util.TrackKeyNormalizer
import com.asmr.player.util.isOnlineTrackPath
import com.asmr.player.work.AlbumCoverThumbWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import com.asmr.player.ui.library.albumdetail.AlbumDetailModel
import com.asmr.player.ui.library.albumdetail.albumDetailRequestKey
import com.asmr.player.ui.library.albumdetail.AlbumDetailSimilarWorksState
import com.asmr.player.ui.library.albumdetail.AlbumDetailUiState
import com.asmr.player.ui.library.albumdetail.AsmrOneLeafDownload
import com.asmr.player.ui.library.albumdetail.asmrOneTrackRjCandidates
import com.asmr.player.ui.library.albumdetail.collectSubtitleCandidates
import com.asmr.player.ui.library.albumdetail.listenTogetherSummaryRj
import com.asmr.player.ui.library.albumdetail.withPreservedListenTogetherListenerCount
import com.asmr.player.ui.library.albumdetail.withUpdatedLocalCover
import com.asmr.player.ui.library.albumdetail.withResolvedWorkIdentity
import com.asmr.player.ui.library.albumdetail.buildAlbumDetailSimilarWorks
import com.asmr.player.ui.library.albumdetail.buildDlsiteTrialDownloadTree
import com.asmr.player.ui.library.albumdetail.collectLocalSelectionFiles
import com.asmr.player.ui.library.albumdetail.defaultDlsiteEditions
import com.asmr.player.ui.library.albumdetail.flattenAsmrOneLeafDownloads
import com.asmr.player.domain.model.isDownloadableTreeFileType
import com.asmr.player.domain.model.isLibraryResourceSavableTreeFileType
import com.asmr.player.ui.library.albumdetail.isMissingLocalDocumentFailure
import com.asmr.player.ui.library.albumdetail.resolveInitialDlsiteLoadTarget
import com.asmr.player.domain.model.isPlayableTreeFileType
import com.asmr.player.data.local.tree.loadOrBuildLocalTreeIndex
import com.asmr.player.ui.library.albumdetail.LocalIncrementalSelectionPaths
import com.asmr.player.ui.library.albumdetail.LocalSourceAvailability
import com.asmr.player.data.local.tree.localTreeSourcesForAlbum
import com.asmr.player.ui.library.albumdetail.mergeAsmrOneHeaderAlbum
import com.asmr.player.ui.library.albumdetail.mergeDetailHeaderAlbum
import com.asmr.player.ui.library.albumdetail.RemoteSelectionFileRef
import com.asmr.player.ui.library.albumdetail.resolveAlbumDetailRj
import com.asmr.player.ui.library.albumdetail.resolveAsmrOneTrackWorkId
import com.asmr.player.ui.library.albumdetail.ResolvedDlsiteLoadTarget
import com.asmr.player.ui.library.albumdetail.resolveExistingRemoteSelectionPaths
import com.asmr.player.data.local.tree.sanitizeFolderName
import com.asmr.player.ui.library.albumdetail.shouldPreserveHeaderAlbumMetadata
import com.asmr.player.ui.library.albumdetail.shouldReloadAsmrOneForResolvedInitialTarget
import com.asmr.player.ui.library.albumdetail.shouldRemoveMissingLocalAlbum
import com.asmr.player.ui.library.albumdetail.shouldReuseAlbumDetailModel
import com.asmr.player.domain.model.TreeFileType
import com.asmr.player.domain.model.treeFileTypeForName
import com.asmr.player.domain.model.treeFileTypeForNode
import com.asmr.player.ui.library.albumdetail.ALBUM_DETAIL_SIMILAR_WORK_LIMIT
import com.asmr.player.ui.library.albumdetail.DlsiteTrialDownloadDirectoryName

private const val LISTEN_TOGETHER_RJ_SUMMARY_POLL_INTERVAL_MS = 60_000L

internal fun albumDetailAsmrOneFailureMessage(isLocalLibraryDetail: Boolean): String? {
    return if (isLocalLibraryDetail) null else ASMR_ONE_SITE_FAILURE_MESSAGE
}

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val libraryReadRepository: LibraryReadRepository,
    private val libraryWriteRepository: LibraryWriteRepository,
    private val asmrOneCrawler: AsmrOneCrawler,
    private val asmrOneAvailabilityApi: AsmrOneAvailabilityApi,
    private val settingsRepository: SettingsRepository,
    private val dlsiteScraper: DLSiteScraper,
    private val dlsiteProductInfoClient: DlsiteProductInfoClient,
    private val dlsitePlayWorkClient: DlsitePlayWorkClient,
    private val downloadManager: DownloadManager,
    private val lyricsLoader: LyricsLoader,
    private val syncCoordinator: SyncCoordinator,
    private val listenTogetherRepository: ListenTogetherRepository,
    private val appCacheManager: AppCacheManager,
    private val onlineContentRepository: OnlineContentRepository,
    val dlsiteAuthStore: DlsiteAuthStore,
    val messageManager: MessageManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val isLocalLibraryDetail = savedStateHandle.get<Long>("albumId")?.let { it > 0L } == true

    private suspend fun refreshAlbumAudioAggregate(albumId: Long) {
        libraryWriteRepository.refreshAlbumAudioAggregate(albumId) { path ->
            queryTrackFileSize(context, path)
        }
    }

    private val _uiState = MutableStateFlow<AlbumDetailUiState>(
        createRouteInitialUiState(savedStateHandle)
    )
    val uiState = _uiState.asStateFlow()
    private val _similarWorksState = MutableStateFlow(AlbumDetailSimilarWorksState())
    internal val similarWorksState: StateFlow<AlbumDetailSimilarWorksState> = _similarWorksState.asStateFlow()
    private val _cloudSyncSelectionDialogState = MutableStateFlow<CloudSyncSelectionDialogState?>(null)
    internal val cloudSyncSelectionDialogState: StateFlow<CloudSyncSelectionDialogState?> = _cloudSyncSelectionDialogState.asStateFlow()
    private var pendingCloudSyncSelection: CompletableDeferred<String?>? = null

    val availableTags: StateFlow<List<TagWithCount>> = libraryReadRepository.observeTagsWithCounts(TagSource.USER)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private var dlsiteLoadToken: Int = 0
    private var dlsiteTrialLoadToken: Int = 0
    private var asmrOneLoadToken: Int = 0
    private var lastAlbumKey: String? = null
    private var completedAlbumKey: String? = null
    private var initialIntroSettled: Boolean = false
    private var albumLoadJob: Job? = null
    private var dlsiteLoadJob: Job? = null
    private var dlsiteRecommendationEnrichJob: Job? = null
    private var dlsiteTrialLoadJob: Job? = null
    private var asmrOneLoadJob: Job? = null
    private var dlsitePlayLoadJob: Job? = null
    private var similarWorksLoadJob: Job? = null
    private var similarWorksRequestFeatures: AsmrOneRecommendationSeedFeatures? = null
    private var similarWorksLoadToken: Int = 0
    private var localTracksObserveJob: Job? = null
    private val asmrOneAttemptedRj = linkedSetOf<String>()
    private val dlsitePlayAttemptedRj = linkedSetOf<String>()

    private val treeExpandedByKey = linkedMapOf<String, List<String>>()
    private val treeInitializedKeys = linkedSetOf<String>()
    private val listScrollByKey = linkedMapOf<String, Pair<Int, Int>>()
    private val treeCurrentPathByKey = linkedMapOf<String, String>()
    private var listenTogetherRjSummaryJob: Job? = null
    private val listenTogetherRjSummaryInFlight = AtomicBoolean(false)
    private val listenTogetherRjSummaryPollingEnabled = MutableStateFlow(false)
    private val preferredTreePathPrefs by lazy {
        context.getSharedPreferences("album_detail_tree_prefs", Context.MODE_PRIVATE)
    }

    init {
        viewModelScope.launch {
            combine(
                uiState.map {
                    (it as? AlbumDetailUiState.Success)?.model?.listenTogetherSummaryRj().orEmpty()
                },
                listenTogetherRjSummaryPollingEnabled,
            ) { rj, enabled ->
                rj.takeIf { enabled }.orEmpty()
            }
                .distinctUntilChanged()
                .collect { rj ->
                    listenTogetherRjSummaryJob?.cancel()
                    listenTogetherRjSummaryJob = if (rj.isBlank()) {
                        null
                    } else {
                        viewModelScope.launch {
                            while (true) {
                                refreshListenTogetherRjSummary(rj)
                                delay(LISTEN_TOGETHER_RJ_SUMMARY_POLL_INTERVAL_MS)
                            }
                        }
                    }
                }
        }
        viewModelScope.launch {
            settingsRepository.asmrOneSite
                .map(AsmrOneEndpoint::normalize)
                .distinctUntilChanged()
                .drop(1)
                .collect { invalidateAsmrOneEndpointState() }
        }
    }

    fun setListenTogetherRjSummaryPollingEnabled(enabled: Boolean) {
        listenTogetherRjSummaryPollingEnabled.value = enabled
        if (!enabled) {
            listenTogetherRjSummaryJob?.cancel()
            listenTogetherRjSummaryJob = null
        }
    }

    private suspend fun refreshListenTogetherRjSummary(rjCode: String) {
        val normalizedRj = rjCode.trim().uppercase()
        if (normalizedRj.isBlank()) return
        if (!listenTogetherRjSummaryInFlight.compareAndSet(false, true)) return
        try {
            val summary = runCatching {
                listenTogetherRepository.getRjSummary(normalizedRj)
            }.getOrNull() ?: return
            val listenerCount = summary.listenerCount.coerceAtLeast(0)
            val current = _uiState.value as? AlbumDetailUiState.Success ?: return
            if (!current.model.listenTogetherSummaryRj().equals(normalizedRj, ignoreCase = true)) return
            if (current.model.listenTogetherRjListenerCount == listenerCount) return
            _uiState.value = AlbumDetailUiState.Success(
                model = current.model.copy(listenTogetherRjListenerCount = listenerCount)
            )
        } finally {
            listenTogetherRjSummaryInFlight.set(false)
        }
    }

    private fun invalidateAsmrOneEndpointState() {
        asmrOneLoadToken++
        asmrOneLoadJob?.cancel()
        asmrOneLoadJob = null
        asmrOneAttemptedRj.clear()
        onlineContentRepository.invalidateAsmrOneCaches()

        val current = _uiState.value as? AlbumDetailUiState.Success ?: return
        val keyRj = current.model.rjCode.trim().uppercase()
        if (keyRj.isBlank()) return
        val shouldReload = current.model.isLoadingAsmrOne ||
            current.model.hasResolvedAsmrOneContent ||
            current.model.asmrOneTree.isNotEmpty()
        _uiState.value = AlbumDetailUiState.Success(
            model = current.model.copy(
                asmrOneWorkId = null,
                asmrOneSite = null,
                asmrOneTree = emptyList(),
                hasResolvedAsmrOneContent = false,
                isLoadingAsmrOne = false
            )
        )
        if (shouldReload) ensureAsmrOneLoaded()
    }

    fun getTreeExpanded(stateKey: String): List<String> {
        return treeExpandedByKey[stateKey].orEmpty()
    }

    fun isTreeInitialized(stateKey: String): Boolean {
        return treeInitializedKeys.contains(stateKey)
    }

    fun persistTreeState(stateKey: String, expanded: List<String>) {
        treeExpandedByKey[stateKey] = expanded.distinct()
        treeInitializedKeys.add(stateKey)
    }

    fun clearTreeState(stateKey: String) {
        treeExpandedByKey.remove(stateKey)
        treeInitializedKeys.remove(stateKey)
        treeCurrentPathByKey.remove(stateKey)
    }

    fun getTreeCurrentPath(stateKey: String): String {
        return treeCurrentPathByKey[stateKey].orEmpty()
    }

    fun persistTreeCurrentPath(stateKey: String, currentPath: String) {
        if (stateKey.isBlank()) return
        treeCurrentPathByKey[stateKey] = currentPath.trim().trim('/')
    }

    fun getPreferredTreeCurrentPath(stateKey: String): String {
        if (stateKey.isBlank()) return ""
        return preferredTreePathPrefs.getString("preferred_path:$stateKey", "").orEmpty().trim().trim('/')
    }

    fun persistPreferredTreeCurrentPath(stateKey: String, currentPath: String) {
        if (stateKey.isBlank()) return
        val normalized = currentPath.trim().trim('/')
        preferredTreePathPrefs.edit().putString("preferred_path:$stateKey", normalized).apply()
    }

    fun clearPreferredTreeCurrentPath(stateKey: String) {
        if (stateKey.isBlank()) return
        preferredTreePathPrefs.edit().remove("preferred_path:$stateKey").apply()
    }

    suspend fun loadOnlineTextPreview(url: String): String? {
        val u = url.trim()
        if (u.isBlank()) return null
        return lyricsLoader.fetchTextForPreview(u)
    }

    fun cancelActiveLoads() {
        setListenTogetherRjSummaryPollingEnabled(false)
        // 保留已完成的页面数据与推荐结果，仅将被中断的 loading 标志收口。
        // 返回时已完成的部分直接复用，未完成的部分仍可以重试。
        cancelPendingOnlineJobs(resetLoadingState = true)
        albumLoadJob?.cancel()
        albumLoadJob = null
        localTracksObserveJob?.cancel()
        localTracksObserveJob = null
        cancelSimilarWorksLoad()
    }

    internal fun hasCachedAlbum(albumId: Long?, rjCode: String?): Boolean {
        val requestKey = albumDetailRequestKey(albumId, rjCode)
        return shouldReuseAlbumDetailModel(
            force = false,
            hasCurrentModel = _uiState.value is AlbumDetailUiState.Success,
            requestKey = requestKey,
            activeRequestKey = lastAlbumKey,
            completedRequestKey = completedAlbumKey
        )
    }

    internal fun isInitialIntroSettled(): Boolean = initialIntroSettled

    internal fun markInitialIntroSettled() {
        initialIntroSettled = true
    }

    fun ensureSimilarWorksLoaded(
        seedRjCode: String,
        seedFeatures: AsmrOneRecommendationSeedFeatures? = null,
        force: Boolean = false
    ) {
        val normalizedSeed = DlsiteWorkNo.normalizeWorkNo(seedRjCode, minimumDigits = 6)
        if (normalizedSeed.isBlank()) {
            similarWorksLoadToken++
            similarWorksLoadJob?.cancel()
            similarWorksLoadJob = null
            similarWorksRequestFeatures = null
            _similarWorksState.value = AlbumDetailSimilarWorksState(hasLoaded = true)
            return
        }

        val normalizedFeatures = seedFeatures?.takeIf {
            DlsiteWorkNo.normalizeWorkNo(it.rj, minimumDigits = 6)
                .equals(normalizedSeed, ignoreCase = true)
        }?.copy(rj = normalizedSeed)
        val current = _similarWorksState.value
        val isSameSeed = current.seedRjCode.equals(normalizedSeed, ignoreCase = true)
        val isSameRequest = isSameSeed && similarWorksRequestFeatures == normalizedFeatures
        if (!force && isSameRequest && (current.isLoading || current.hasLoaded)) return
        if (!force && isSameSeed && current.hasLoaded && current.works.isNotEmpty()) return

        val token = ++similarWorksLoadToken
        similarWorksLoadJob?.cancel()
        similarWorksRequestFeatures = normalizedFeatures
        if (!asmrOneAvailabilityApi.isBackendConfigured) {
            _similarWorksState.value = AlbumDetailSimilarWorksState(
                seedRjCode = normalizedSeed,
                hasLoaded = true
            )
            return
        }

        _similarWorksState.value = AlbumDetailSimilarWorksState(
            seedRjCode = normalizedSeed,
            works = current.works.takeIf { isSameSeed }.orEmpty(),
            isLoading = true
        )
        similarWorksLoadJob = viewModelScope.launch {
            try {
                val response = asmrOneAvailabilityApi.getRecommendations(
                    seedRjs = listOf(normalizedSeed),
                    seedFeatures = listOfNotNull(normalizedFeatures),
                    excludeRjs = listOf(normalizedSeed),
                    limit = ALBUM_DETAIL_SIMILAR_WORK_LIMIT
                )
                if (
                    token != similarWorksLoadToken ||
                    !_similarWorksState.value.seedRjCode.equals(normalizedSeed, ignoreCase = true) ||
                    similarWorksRequestFeatures != normalizedFeatures
                ) {
                    return@launch
                }
                _similarWorksState.value = AlbumDetailSimilarWorksState(
                    seedRjCode = normalizedSeed,
                    works = buildAlbumDetailSimilarWorks(
                        seedRjCode = normalizedSeed,
                        items = response.items.orEmpty()
                    ),
                    hasLoaded = true
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                val latest = _similarWorksState.value
                if (
                    token == similarWorksLoadToken &&
                    latest.seedRjCode.equals(normalizedSeed, ignoreCase = true) &&
                    similarWorksRequestFeatures == normalizedFeatures
                ) {
                    _similarWorksState.value = latest.copy(
                        isLoading = false,
                        hasLoaded = true,
                        failed = true
                    )
                }
            }
        }
    }

    fun cancelSimilarWorksLoad() {
        similarWorksLoadToken++
        similarWorksLoadJob?.cancel()
        similarWorksLoadJob = null
        val current = _similarWorksState.value
        if (current.isLoading) {
            _similarWorksState.value = current.copy(
                isLoading = false,
                hasLoaded = false
            )
        }
    }

    fun cancelOnlineLoadsForExit() {
        cancelPendingOnlineJobs(resetLoadingState = false)
    }

    private fun cancelPendingOnlineJobs(resetLoadingState: Boolean) {
        dlsiteLoadToken++
        dlsiteTrialLoadToken++
        asmrOneLoadToken++

        dlsiteLoadJob?.cancel()
        dlsiteLoadJob = null
        dlsiteRecommendationEnrichJob?.cancel()
        dlsiteRecommendationEnrichJob = null
        dlsiteTrialLoadJob?.cancel()
        dlsiteTrialLoadJob = null
        asmrOneLoadJob?.cancel()
        asmrOneLoadJob = null
        onlineContentRepository.cancelAsmrOneResolutionInFlight()
        dlsitePlayLoadJob?.cancel()
        dlsitePlayLoadJob = null

        asmrOneAttemptedRj.clear()
        dlsitePlayAttemptedRj.clear()

        if (!resetLoadingState) return
        val current = _uiState.value as? AlbumDetailUiState.Success ?: return
        _uiState.value = AlbumDetailUiState.Success(
            model = current.model.copy(
                isLoadingDlsite = false,
                isLoadingDlsiteTrial = false,
                isLoadingAsmrOne = false,
                isLoadingDlsitePlay = false
            )
        )
    }

    internal fun invalidateDlsitePlayAccess() {
        dlsitePlayLoadJob?.cancel()
        dlsitePlayLoadJob = null
        dlsitePlayAttemptedRj.clear()
        val current = _uiState.value as? AlbumDetailUiState.Success ?: return
        if (
            current.model.dlsitePlayTree.isEmpty() &&
            !current.model.hasResolvedDlsitePlayContent &&
            !current.model.isLoadingDlsitePlay
        ) return
        _uiState.value = AlbumDetailUiState.Success(
            model = current.model.copy(
                dlsitePlayWorkno = "",
                dlsitePlayTree = emptyList(),
                hasResolvedDlsitePlayContent = false,
                isLoadingDlsitePlay = false
            )
        )
    }

    suspend fun prepareDlsitePlayImagePreview(
        url: String,
        optimizedName: String?,
        crypt: Boolean,
        width: Int?,
        height: Int?
    ): String? {
        return onlineContentRepository.prepareDlsitePlayImagePreview(
            url = url,
            optimizedName = optimizedName,
            crypt = crypt,
            width = width,
            height = height,
            onPreviewWritten = appCacheManager::onPreviewCacheChanged
        )
    }

    fun getListScrollPosition(stateKey: String): Pair<Int, Int> {
        return listScrollByKey[stateKey] ?: (0 to 0)
    }

    fun persistListScrollPosition(stateKey: String, index: Int, offset: Int) {
        if (stateKey.isBlank()) return
        listScrollByKey[stateKey] = (index.coerceAtLeast(0) to offset.coerceAtLeast(0))
    }

    suspend fun loadRemoteFileSize(url: String): Long? {
        return onlineContentRepository.loadRemoteFileSize(url)
    }

    fun confirmCloudSyncSelection(workno: String) {
        val deferred = pendingCloudSyncSelection ?: return
        if (deferred.isActive) {
            deferred.complete(workno.trim().uppercase().ifBlank { null })
        }
        if (pendingCloudSyncSelection === deferred) {
            pendingCloudSyncSelection = null
            _cloudSyncSelectionDialogState.value = null
        }
    }

    fun cancelCloudSyncSelection() {
        val deferred = pendingCloudSyncSelection ?: return
        if (deferred.isActive) {
            deferred.complete(null)
        }
        if (pendingCloudSyncSelection === deferred) {
            pendingCloudSyncSelection = null
            _cloudSyncSelectionDialogState.value = null
        }
    }

    private suspend fun awaitCloudSyncSelection(
        albumTitle: String,
        candidates: List<DlsiteCloudSyncCandidate>
    ): String? {
        cancelCloudSyncSelection()
        val normalizedCandidates = candidates
            .filter { it.workno.isNotBlank() }
            .distinctBy { it.workno.trim().uppercase() }
        if (normalizedCandidates.isEmpty()) return null
        val deferred = CompletableDeferred<String?>()
        pendingCloudSyncSelection = deferred
        _cloudSyncSelectionDialogState.value = CloudSyncSelectionDialogState(
            albumTitle = albumTitle,
            candidates = normalizedCandidates
        )
        return try {
            deferred.await()
        } finally {
            if (pendingCloudSyncSelection === deferred) {
                pendingCloudSyncSelection = null
                _cloudSyncSelectionDialogState.value = null
            }
        }
    }

    suspend fun loadAlbumAndAwait(albumId: Long?, rjCode: String?, force: Boolean = false) {
        loadAlbum(albumId, rjCode, force)
        albumLoadJob?.join()
    }

    fun loadAlbum(albumId: Long?, rjCode: String?, force: Boolean = false) {
        val normalizedRj = rjCode?.trim().orEmpty().uppercase()
        val key = albumDetailRequestKey(albumId, normalizedRj)
        val current = _uiState.value as? AlbumDetailUiState.Success
        val isAlbumSwitch = lastAlbumKey != key
        if (
            shouldReuseAlbumDetailModel(
                force = force,
                hasCurrentModel = current != null,
                requestKey = key,
                activeRequestKey = lastAlbumKey,
                completedRequestKey = completedAlbumKey
            )
        ) {
            observeLocalTracks(current?.model?.localAlbum?.id ?: 0L)
            return
        }
        cancelPendingOnlineJobs(resetLoadingState = false)
        if (force || isAlbumSwitch) {
            completedAlbumKey = null
            similarWorksLoadToken++
            similarWorksLoadJob?.cancel()
            similarWorksLoadJob = null
            _similarWorksState.value = AlbumDetailSimilarWorksState()
        }
        albumLoadJob?.cancel()
        localTracksObserveJob?.cancel()
        localTracksObserveJob = null
        lastAlbumKey = key
        val initialHint = AlbumCoverHintStore.peekHint(albumId, normalizedRj)
        val initialRj = normalizedRj.ifBlank { initialHint?.rjCode.orEmpty() }
        val initialHintAlbum = albumFromInitialHint(initialRj, initialHint)
        if (force || current == null || isAlbumSwitch) {
            _uiState.value = AlbumDetailUiState.Success(
                model = createInitialAlbumDetailModel(
                    rj = initialRj,
                    displayAlbum = initialHintAlbum,
                    dlsiteInfo = initialHintAlbum.takeIf { shouldPreserveHeaderAlbumMetadata(initialHint) },
                    preserveHeaderAlbumMetadata = shouldPreserveHeaderAlbumMetadata(initialHint)
                )
            )
        }
        albumLoadJob = viewModelScope.launch {
            try {
                val localAlbum = if (albumId != null && albumId > 0) {
                    when (val result = loadLocalAlbumByIdWithAvailabilityCheck(albumId)) {
                        is LocalAlbumLoadResult.Available -> result.album
                        LocalAlbumLoadResult.Removed -> return@launch
                    }
                } else if (!rjCode.isNullOrBlank()) {
                    loadLocalAlbumByRj(rjCode)
                } else {
                    null
                }

                val rj = resolveAlbumDetailRj(rjCode, localAlbum)

                val hint = AlbumCoverHintStore.peekHint(albumId, rj) ?: initialHint
                val hintAlbum = albumFromInitialHint(rj, hint)
                val preserveHeaderAlbumMetadata = shouldPreserveHeaderAlbumMetadata(hint)
                val dlsiteInfo = hintAlbum.takeIf { preserveHeaderAlbumMetadata }
                // 种入列表点击时记录的封面与元信息：让 hero 与列表卡片使用相同图片 model，
                // 在网络解析完成前即可命中跨尺寸内存缓存，避免重复请求封面。
                val displayAlbum = if (hint != null) hintAlbum else localAlbum ?: hintAlbum
                val initialLoadedModel = createInitialAlbumDetailModel(
                    rj = rj,
                    displayAlbum = displayAlbum,
                    localAlbum = localAlbum,
                    dlsiteInfo = dlsiteInfo,
                    preserveHeaderAlbumMetadata = preserveHeaderAlbumMetadata
                )
                val currentModel = (_uiState.value as? AlbumDetailUiState.Success)?.model
                val loadedModel = initialLoadedModel.withPreservedListenTogetherListenerCount(currentModel)
                if (loadedModel != currentModel) {
                    _uiState.value = AlbumDetailUiState.Success(
                        model = loadedModel
                    )
                }
                val localId = localAlbum?.id ?: 0L
                observeLocalTracks(localId)
                completedAlbumKey = key
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = AlbumDetailUiState.Error(e.message ?: "加载失败")
            }
        }
    }

    private fun observeLocalTracks(localId: Long) {
        localTracksObserveJob?.cancel()
        localTracksObserveJob = null
        if (localId <= 0L) return

        localTracksObserveJob = viewModelScope.launch {
            libraryReadRepository.observeTracksForAlbum(localId)
                .map { entities -> entities.map { it.toDomain() } }
                .flowOn(Dispatchers.Default)
                .distinctUntilChanged()
                .collect { tracks ->
                    val current = _uiState.value as? AlbumDetailUiState.Success ?: return@collect
                    val currentLocal = current.model.localAlbum ?: return@collect
                    if (currentLocal.id != localId) return@collect

                    val updatedLocal = currentLocal.copy(tracks = tracks)
                    val updatedDisplay = if (current.model.displayAlbum.id == localId) {
                        current.model.displayAlbum.copy(tracks = tracks)
                    } else {
                        current.model.displayAlbum
                    }
                    _uiState.value = AlbumDetailUiState.Success(
                        model = current.model.copy(
                            localAlbum = updatedLocal,
                            displayAlbum = updatedDisplay
                        )
                    )
                }
        }
    }

    fun setUserTagsForTrack(trackId: Long, tags: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val pairs = tags
                .asSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .map { it to TagNormalizer.normalize(it) }
                .filter { it.second.isNotBlank() }
                .distinctBy { it.second }
                .toList()

            libraryWriteRepository.replaceTrackUserTags(trackId, pairs)
        }
    }

    fun manualSetRjAndSync(input: String) {
        val normalized = DlsiteWorkNo.extractWorkNo(input)
        if (normalized.isBlank()) {
            messageManager.showError("请输入有效的作品编号")
            return
        }
        val current = _uiState.value as? AlbumDetailUiState.Success
        val local = current?.model?.localAlbum
        if (local == null || local.id <= 0L) {
            messageManager.showError("仅支持本地库专辑手动绑定作品编号")
            return
        }

        viewModelScope.launch {
            val token = syncCoordinator.tryBegin() ?: run {
                messageManager.showInfo("同步任务进行中，请等待完成或取消后再同步")
                return@launch
            }
            _uiState.value = AlbumDetailUiState.Loading
            try {
                val entity = withContext(Dispatchers.IO) { libraryReadRepository.getAlbumById(local.id) } ?: run {
                    messageManager.showError("专辑不存在")
                    loadAlbum(local.id, normalized, force = true)
                    return@launch
                }

                val updatedWorkId = resolveCloudSyncWorkId(entity.workId, normalized)
                withContext(Dispatchers.IO) {
                    libraryWriteRepository.updateAlbum(entity.copy(workId = updatedWorkId, rjCode = normalized))
                }

                when (
                    val result = withContext(Dispatchers.IO) {
                        onlineContentRepository.resolveManualCloudSync(entity, normalized)
                    }
                ) {
                    is DlsiteCloudSyncResolveResult.Success -> {
                        val resolvedWorkno = withContext(Dispatchers.IO) {
                            onlineContentRepository.applyManualCloudSyncSuccess(entity, updatedWorkId, result)
                        }
                        messageManager.showSuccess("已绑定 $resolvedWorkno 并完成云同步")
                        loadAlbum(local.id, resolvedWorkno, force = true)
                    }

                    is DlsiteCloudSyncResolveResult.Ambiguous -> {
                        val selectedWorkno = awaitCloudSyncSelection(
                            albumTitle = local.title,
                            candidates = result.candidates
                        )
                        if (selectedWorkno == null) {
                            loadAlbum(local.id, normalized, force = true)
                            return@launch
                        }
                        when (val selectedResult = withContext(Dispatchers.IO) {
                            onlineContentRepository.resolveSelectedManualCloudSync(selectedWorkno)
                        }) {
                            is DlsiteCloudSyncResolveResult.Success -> {
                                val resolvedWorkno = withContext(Dispatchers.IO) {
                                    onlineContentRepository.applyManualCloudSyncSuccess(entity, updatedWorkId, selectedResult)
                                }
                                messageManager.showSuccess("已绑定 $resolvedWorkno 并完成云同步")
                                loadAlbum(local.id, resolvedWorkno, force = true)
                                return@launch
                            }

                            is DlsiteCloudSyncResolveResult.Ambiguous -> {
                                messageManager.showError("同步失败：搜索结果不唯一")
                                loadAlbum(local.id, normalized, force = true)
                                return@launch
                            }

                            DlsiteCloudSyncResolveResult.NotFound -> {
                                messageManager.showError("同步失败：未找到专辑信息")
                                loadAlbum(local.id, normalized, force = true)
                                return@launch
                            }
                        }
                    }

                    DlsiteCloudSyncResolveResult.NotFound -> {
                        messageManager.showError("同步失败：未找到专辑信息")
                        loadAlbum(local.id, normalized, force = true)
                    }
                }
            } catch (e: Exception) {
                messageManager.showError("同步失败，请稍后重试")
                loadAlbum(local.id, normalized, force = true)
            } finally {
                syncCoordinator.end(token)
            }
        }
    }

    fun setLocalCoverPath(pathOrUri: String) {
        val value = pathOrUri.trim()
        if (value.isBlank()) return
        val current = _uiState.value as? AlbumDetailUiState.Success ?: return
        val local = current.model.localAlbum ?: return
        if (local.id <= 0L) return

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val entity = libraryReadRepository.getAlbumById(local.id) ?: return@withContext
                    libraryWriteRepository.updateAlbum(entity.copy(coverPath = value, coverThumbPath = ""))
                    runCatching { libraryWriteRepository.clearLocalTreeCache(entity.id) }
                }
                updateCurrentCoverState(local.id, value, "")
                enqueueAlbumCoverThumbWork(local.id)
                messageManager.showSuccess("已设置封面")
            } catch (e: Exception) {
                messageManager.showError("设置封面失败，请检查后重试")
            }
        }
    }

    private fun updateCurrentCoverState(albumId: Long, coverPath: String, coverThumbPath: String) {
        val cur = _uiState.value as? AlbumDetailUiState.Success ?: return
        _uiState.value = AlbumDetailUiState.Success(
            model = cur.model.withUpdatedLocalCover(
                albumId = albumId,
                coverPath = coverPath,
                coverThumbPath = coverThumbPath
            )
        )
    }

    private fun enqueueAlbumCoverThumbWork(albumId: Long) {
        if (albumId <= 0L) return
        val request = OneTimeWorkRequestBuilder<AlbumCoverThumbWorker>()
            .setInputData(workDataOf(AlbumCoverThumbWorker.KEY_ALBUM_ID to albumId))
            .addTag("album_cover_thumb")
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("album_cover_thumb_$albumId", ExistingWorkPolicy.REPLACE, request)
    }

    private suspend fun resolveInitialDlsiteLoadTarget(
        model: AlbumDetailModel
    ): ResolvedDlsiteLoadTarget {
        val clean = model.baseRjCode.trim().uppercase()
        if (clean.isBlank()) {
            return ResolvedDlsiteLoadTarget(
                editions = model.dlsiteEditions,
                selectedLang = model.dlsiteSelectedLang,
                workno = model.dlsiteWorkno.trim().uppercase()
            )
        }
        val editions = runCatching { dlsiteProductInfoClient.fetchLanguageEditions(clean) }
            .getOrDefault(emptyList())
        return resolveInitialDlsiteLoadTarget(
            entryRjCode = clean,
            editions = editions
        )
    }

    private fun dlsiteLocaleForLang(lang: String): String {
        return when (lang.trim().uppercase()) {
            "CHI_HANS" -> "zh_CN"
            "CHI_HANT" -> "zh_TW"
            else -> "ja_JP"
        }
    }

    fun ensureDlsiteLoaded() {
        val current = _uiState.value as? AlbumDetailUiState.Success ?: return
        if (current.model.hasLoadedInitialDlsiteContent) return
        if (current.model.isLoadingDlsite) return
        
        dlsiteLoadJob?.cancel()
        dlsiteRecommendationEnrichJob?.cancel()
        // 如果还没有拉取过多语言列表，先拉取一次
        val token = ++dlsiteLoadToken
        dlsiteLoadJob = viewModelScope.launch {
            val latestBefore = (_uiState.value as? AlbumDetailUiState.Success)?.model ?: return@launch
            _uiState.value = AlbumDetailUiState.Success(
                model = latestBefore.copy(
                    isLoadingDlsite = true,
                    isLoadingDlsiteTrial = false
                )
            )
            try {
                var loadModel = (_uiState.value as? AlbumDetailUiState.Success)?.model ?: return@launch
                if (!loadModel.hasResolvedInitialDlsiteTarget) {
                    val resolvedTarget = resolveInitialDlsiteLoadTarget(loadModel)
                    val latestResolved = (_uiState.value as? AlbumDetailUiState.Success)?.model ?: return@launch
                    if (token != dlsiteLoadToken) return@launch
                    val targetWorkno = resolvedTarget.workno.trim().uppercase()
                    val targetChanged = shouldReloadAsmrOneForResolvedInitialTarget(
                        currentRj = latestResolved.rjCode,
                        resolvedWorkno = targetWorkno
                    )
                    val mustReloadAsmrOne = targetChanged
                    val keepAsmrOneContentDuringTargetSwitch = targetChanged &&
                        latestResolved.asmrOneTree.isNotEmpty()
                    if (mustReloadAsmrOne) {
                        asmrOneLoadToken++
                        asmrOneAttemptedRj.clear()
                    }
                    loadModel = latestResolved.copy(
                        rjCode = resolvedTarget.workno,
                        displayAlbum = mergeDetailHeaderAlbum(
                            currentDisplayAlbum = latestResolved.displayAlbum,
                            localAlbum = latestResolved.localAlbum,
                            fetchedDlsiteInfo = latestResolved.dlsiteInfo,
                            rjCode = resolvedTarget.workno,
                            asmrOneWorkId = if (mustReloadAsmrOne) null else latestResolved.asmrOneWorkId,
                            preserveHeaderAlbumMetadata = latestResolved.preserveHeaderAlbumMetadata
                        ),
                        dlsiteWorkno = resolvedTarget.workno,
                        dlsiteEditions = resolvedTarget.editions,
                        dlsiteSelectedLang = resolvedTarget.selectedLang,
                        hasResolvedInitialDlsiteTarget = true,
                        hasResolvedAsmrOneContent = if (mustReloadAsmrOne) false else latestResolved.hasResolvedAsmrOneContent,
                        asmrOneWorkId = if (mustReloadAsmrOne && !keepAsmrOneContentDuringTargetSwitch) {
                            null
                        } else {
                            latestResolved.asmrOneWorkId
                        },
                        asmrOneSite = if (mustReloadAsmrOne && !keepAsmrOneContentDuringTargetSwitch) {
                            null
                        } else {
                            latestResolved.asmrOneSite
                        },
                        asmrOneTree = if (mustReloadAsmrOne && !keepAsmrOneContentDuringTargetSwitch) {
                            emptyList()
                        } else {
                            latestResolved.asmrOneTree
                        },
                        isLoadingDlsite = true,
                        isLoadingAsmrOne = if (mustReloadAsmrOne) false else latestResolved.isLoadingAsmrOne,
                        isLoadingDlsiteTrial = false
                    )
                    _uiState.value = AlbumDetailUiState.Success(model = loadModel)
                }

                if (loadModel.dlsiteInfo != null) {
                    val workno = loadModel.dlsiteWorkno.trim().uppercase().ifBlank { loadModel.rjCode.trim().uppercase() }
                    if (workno.isBlank()) {
                        _uiState.value = AlbumDetailUiState.Success(
                            model = loadModel.copy(
                                hasLoadedInitialDlsiteContent = true,
                                isLoadingDlsite = false
                            )
                        )
                        return@launch
                    }
                }

                val workno = loadModel.dlsiteWorkno.trim().uppercase().ifBlank { loadModel.rjCode.trim().uppercase() }
                if (workno.isBlank()) {
                    _uiState.value = AlbumDetailUiState.Success(
                        model = loadModel.copy(
                            hasLoadedInitialDlsiteContent = true,
                            isLoadingDlsite = false
                        )
                    )
                    return@launch
                }
                val locale = dlsiteLocaleForLang(loadModel.dlsiteSelectedLang)
                val (dlsiteInitialDetail, dlsiteRecommendationsFromV2) = coroutineScope {
                    val initialDeferred = async {
                        runCatching { dlsiteScraper.getInitialWorkDetail(workno, locale = locale) }.getOrNull()
                    }
                    val recDeferred = async {
                        runCatching { dlsiteScraper.getRecommendationsDetailV2(workno, locale = locale) }
                            .getOrDefault(DlsiteRecommendations())
                    }
                    initialDeferred.await() to recDeferred.await()
                }
                val dlsiteWorkInfo = dlsiteInitialDetail?.workInfo
                val dlsiteTrialTracks = dlsiteInitialDetail?.trialTracks.orEmpty()

                val dlsiteInfo = dlsiteWorkInfo?.album
                val dlsiteGalleryUrls = dlsiteWorkInfo?.galleryUrls.orEmpty()
                
                fun mergePreferNonBlank(
                    primary: List<DlsiteRecommendedWork>,
                    secondary: List<DlsiteRecommendedWork>
                ): List<DlsiteRecommendedWork> {
                    if (primary.isEmpty()) return secondary
                    if (secondary.isEmpty()) return primary
                    val secondaryById = secondary.associateBy { it.rjCode.trim().uppercase() }
                    val merged = primary.map { p ->
                        val s = secondaryById[p.rjCode.trim().uppercase()]
                        if (s == null) {
                            p
                        } else {
                            p.copy(
                                title = p.title.ifBlank { s.title },
                                coverUrl = p.coverUrl.ifBlank { s.coverUrl },
                                ribbon = p.ribbon ?: s.ribbon
                            )
                        }
                    }
                    val existing = merged.mapTo(hashSetOf()) { it.rjCode.trim().uppercase() }
                    val appended = secondary.filter { it.rjCode.trim().uppercase() !in existing }
                    return (merged + appended).distinctBy { it.rjCode.trim().uppercase() }
                }

                val fallbackRecs = dlsiteWorkInfo?.recommendations ?: DlsiteRecommendations()
                val circleWorks = mergePreferNonBlank(dlsiteRecommendationsFromV2.circleWorks, fallbackRecs.circleWorks)
                val sameVoiceWorks = mergePreferNonBlank(dlsiteRecommendationsFromV2.sameVoiceWorks, fallbackRecs.sameVoiceWorks)
                val alsoBoughtWorks = mergePreferNonBlank(dlsiteRecommendationsFromV2.alsoBoughtWorks, fallbackRecs.alsoBoughtWorks)
                
                val dlsiteRecommendationsRaw = DlsiteRecommendations(
                    circleWorks = circleWorks,
                    sameVoiceWorks = sameVoiceWorks,
                    alsoBoughtWorks = alsoBoughtWorks
                )
                val updated = (_uiState.value as? AlbumDetailUiState.Success)?.model ?: return@launch
                if (token != dlsiteLoadToken) return@launch
                val displayAlbum = mergeDetailHeaderAlbum(
                    currentDisplayAlbum = updated.displayAlbum,
                    localAlbum = updated.localAlbum,
                    fetchedDlsiteInfo = dlsiteInfo,
                    rjCode = updated.rjCode,
                    asmrOneWorkId = updated.asmrOneWorkId,
                    preserveHeaderAlbumMetadata = updated.preserveHeaderAlbumMetadata
                )
                _uiState.value = AlbumDetailUiState.Success(
                    model = updated.copy(
                        displayAlbum = displayAlbum,
                        dlsiteInfo = if (updated.preserveHeaderAlbumMetadata) updated.dlsiteInfo else dlsiteInfo,
                        dlsiteGalleryUrls = dlsiteGalleryUrls,
                        dlsiteTrialTracks = dlsiteTrialTracks,
                        dlsiteRecommendations = dlsiteRecommendationsRaw,
                        hasLoadedInitialDlsiteContent = true,
                        isLoadingDlsite = false
                    )
                )

                dlsiteRecommendationEnrichJob?.cancel()
                dlsiteRecommendationEnrichJob = viewModelScope.launch enrichLaunch@{
                    val current2 = _uiState.value as? AlbumDetailUiState.Success ?: return@enrichLaunch
                    if (token != dlsiteLoadToken) return@enrichLaunch
                    val recs = current2.model.dlsiteRecommendations
                    if (recs.alsoBoughtWorks.isEmpty() && recs.circleWorks.isEmpty() && recs.sameVoiceWorks.isEmpty()) return@enrichLaunch
                    val enriched = runCatching { onlineContentRepository.enrichRecommendationsWithAsmrOne(recs) }
                        .getOrNull() ?: return@enrichLaunch
                    val updated2 = (_uiState.value as? AlbumDetailUiState.Success)?.model ?: return@enrichLaunch
                    if (token != dlsiteLoadToken) return@enrichLaunch
                    _uiState.value = AlbumDetailUiState.Success(model = updated2.copy(dlsiteRecommendations = enriched))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val updated = (_uiState.value as? AlbumDetailUiState.Success)?.model ?: return@launch
                _uiState.value = AlbumDetailUiState.Success(model = updated.copy(isLoadingDlsite = false))
            }
        }
    }

    fun selectDlsiteLanguage(lang: String) {
        val current = _uiState.value as? AlbumDetailUiState.Success ?: return
        val normalized = lang.trim().uppercase()
        if (normalized.isBlank()) return

        val editions = current.model.dlsiteEditions
        val target = editions.firstOrNull { it.lang == normalized }
            ?: if (normalized == "JPN") DlsiteLanguageEdition(
                workno = current.model.baseRjCode.trim().uppercase(),
                lang = "JPN",
                label = "日本語",
                displayOrder = 1
            ) else null
        val workno = target?.workno?.trim()?.uppercase().orEmpty().ifBlank { current.model.baseRjCode.trim().uppercase() }
        if (workno.isBlank()) return
        if (normalized == current.model.dlsiteSelectedLang.trim().uppercase() && workno == current.model.dlsiteWorkno.trim().uppercase()) return

        cancelPendingOnlineJobs(resetLoadingState = false)
        dlsiteLoadToken++
        asmrOneLoadToken++
        asmrOneAttemptedRj.clear()
        dlsitePlayAttemptedRj.clear()
        _uiState.value = AlbumDetailUiState.Success(
            model = current.model.copy(
                dlsiteSelectedLang = target?.lang ?: normalized,
                dlsiteWorkno = workno,
                dlsitePlayWorkno = "",
                rjCode = workno,
                displayAlbum = mergeDetailHeaderAlbum(
                    currentDisplayAlbum = current.model.displayAlbum,
                    localAlbum = current.model.localAlbum,
                    fetchedDlsiteInfo = null,
                    rjCode = workno,
                    asmrOneWorkId = null,
                    preserveHeaderAlbumMetadata = current.model.preserveHeaderAlbumMetadata
                ),
                dlsiteInfo = null,
                dlsiteGalleryUrls = emptyList(),
                dlsiteTrialTracks = emptyList(),
                dlsiteRecommendations = DlsiteRecommendations(),
                hasResolvedInitialDlsiteTarget = true,
                hasLoadedInitialDlsiteContent = false,
                hasResolvedAsmrOneContent = false,
                hasResolvedDlsitePlayContent = false,
                isDlsiteLanguageUserSelected = true,
                asmrOneWorkId = null,
                asmrOneSite = null,
                asmrOneTree = emptyList(),
                dlsitePlayTree = emptyList(),
                isLoadingDlsite = false,
                isLoadingDlsiteTrial = false,
                isLoadingAsmrOne = false,
                isLoadingDlsitePlay = false
            )
        )
        clearTreeState("tree:asmrOne:$workno")
        clearTreeState("tree:dlsitePlay:$workno")
        clearTreeState("localTree:rj:$workno")
        viewModelScope.launch {
            val local = runCatching { loadLocalAlbumByRj(workno) }.getOrNull() ?: current.model.localAlbum
            val updated = (_uiState.value as? AlbumDetailUiState.Success)?.model ?: return@launch
            if (!updated.rjCode.equals(workno, ignoreCase = true)) return@launch
            val displayAlbum = mergeDetailHeaderAlbum(
                currentDisplayAlbum = updated.displayAlbum,
                localAlbum = local,
                fetchedDlsiteInfo = updated.dlsiteInfo,
                rjCode = updated.rjCode,
                asmrOneWorkId = updated.asmrOneWorkId,
                preserveHeaderAlbumMetadata = updated.preserveHeaderAlbumMetadata
            )
            _uiState.value = AlbumDetailUiState.Success(
                model = updated.copy(
                    localAlbum = local,
                    displayAlbum = displayAlbum
                )
            )
        }
        ensureDlsiteLoaded()
        ensureAsmrOneLoaded()
    }

    fun refreshAsmrOneSection() {
        val current = _uiState.value as? AlbumDetailUiState.Success ?: return
        val keyRj = current.model.rjCode.trim().uppercase()
        if (keyRj.isBlank() || current.model.isLoadingAsmrOne) return

        asmrOneAttemptedRj.remove(keyRj)
        onlineContentRepository.forgetAsmrOneResolution(
            keyRj = keyRj,
            site = current.model.asmrOneSite,
            workId = current.model.asmrOneWorkId
        )

        _uiState.value = AlbumDetailUiState.Success(
            model = current.model.copy(
                asmrOneWorkId = null,
                asmrOneSite = null,
                asmrOneTree = emptyList(),
                hasResolvedAsmrOneContent = false,
                isLoadingAsmrOne = false
            )
        )
        ensureAsmrOneLoaded()
    }

    private fun finishAsmrOneLoad(keyRj: String, resolved: Boolean, showFailureMessage: Boolean = false) {
        val updated = (_uiState.value as? AlbumDetailUiState.Success)?.model ?: return
        val updatedKey = updated.rjCode.trim().uppercase()
        if (updatedKey.equals(keyRj, ignoreCase = true)) {
            if (showFailureMessage && updated.asmrOneTree.isEmpty()) {
                albumDetailAsmrOneFailureMessage(isLocalLibraryDetail)
                    ?.let(messageManager::showError)
            }
            _uiState.value = AlbumDetailUiState.Success(
                model = updated.copy(
                    isLoadingAsmrOne = false,
                    hasResolvedAsmrOneContent = if (resolved) true else updated.hasResolvedAsmrOneContent
                )
            )
        }
    }

    fun refreshDlsiteTrialSection() {
        val current = _uiState.value as? AlbumDetailUiState.Success ?: return
        val workno = current.model.dlsiteWorkno.trim().uppercase().ifBlank { current.model.rjCode.trim().uppercase() }
        if (workno.isBlank() || current.model.isLoadingDlsite || current.model.isLoadingDlsiteTrial) return

        val token = ++dlsiteTrialLoadToken
        val locale = dlsiteLocaleForLang(current.model.dlsiteSelectedLang)
        dlsiteTrialLoadJob?.cancel()
        dlsiteTrialLoadJob = viewModelScope.launch {
            val latestBefore = _uiState.value as? AlbumDetailUiState.Success ?: return@launch
            val latestWorkno = latestBefore.model.dlsiteWorkno.trim().uppercase().ifBlank { latestBefore.model.rjCode.trim().uppercase() }
            if (!latestWorkno.equals(workno, ignoreCase = true)) return@launch
            if (latestBefore.model.isLoadingDlsite || latestBefore.model.isLoadingDlsiteTrial) return@launch
            _uiState.value = AlbumDetailUiState.Success(model = latestBefore.model.copy(isLoadingDlsiteTrial = true))
            try {
                val tracks = runCatching { dlsiteScraper.getTracks(workno, locale = locale) }.getOrDefault(emptyList())
                val updated = (_uiState.value as? AlbumDetailUiState.Success)?.model ?: return@launch
                val updatedWorkno = updated.dlsiteWorkno.trim().uppercase().ifBlank { updated.rjCode.trim().uppercase() }
                if (token != dlsiteTrialLoadToken || !updatedWorkno.equals(workno, ignoreCase = true)) return@launch
                _uiState.value = AlbumDetailUiState.Success(
                    model = updated.copy(
                        dlsiteTrialTracks = tracks,
                        isLoadingDlsiteTrial = false
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val updated = (_uiState.value as? AlbumDetailUiState.Success)?.model ?: return@launch
                val updatedWorkno = updated.dlsiteWorkno.trim().uppercase().ifBlank { updated.rjCode.trim().uppercase() }
                if (token != dlsiteTrialLoadToken || !updatedWorkno.equals(workno, ignoreCase = true)) return@launch
                messageManager.showError("试听刷新失败，请稍后重试")
                _uiState.value = AlbumDetailUiState.Success(model = updated.copy(isLoadingDlsiteTrial = false))
            }
        }
    }

    fun ensureAsmrOneLoaded() {
        val current = _uiState.value as? AlbumDetailUiState.Success ?: return
        val keyRj = current.model.rjCode.trim().uppercase()
        if (current.model.asmrOneTree.isNotEmpty() && current.model.hasResolvedAsmrOneContent) {
            return
        }
        if (
            keyRj.isBlank() ||
            current.model.hasResolvedAsmrOneContent ||
            current.model.isLoadingAsmrOne ||
            asmrOneAttemptedRj.contains(keyRj)
        ) return
        asmrOneAttemptedRj.add(keyRj)
        val token = ++asmrOneLoadToken
        asmrOneLoadJob?.cancel()
        asmrOneLoadJob = viewModelScope.launch {
            val latestBefore = _uiState.value as? AlbumDetailUiState.Success ?: return@launch
            val latestKey = latestBefore.model.rjCode.trim().uppercase()
            if (!latestKey.equals(keyRj, ignoreCase = true)) {
                asmrOneAttemptedRj.remove(keyRj)
                finishAsmrOneLoad(keyRj, resolved = false)
                return@launch
            }
            _uiState.value = AlbumDetailUiState.Success(
                model = latestBefore.model.copy(
                    isLoadingAsmrOne = true,
                    hasResolvedAsmrOneContent = false
                )
            )
            try {
                val latest = (_uiState.value as? AlbumDetailUiState.Success)?.model ?: return@launch
                val selectedLang = latest.dlsiteSelectedLang.trim().uppercase().ifBlank { "JPN" }
                val latestBase = latest.baseRjCode.trim().uppercase()
                val jpnWorkno = latest.dlsiteEditions.firstOrNull { it.lang.equals("JPN", ignoreCase = true) }
                    ?.workno
                    ?.trim()
                    ?.uppercase()
                    .orEmpty()
                val originalRj = jpnWorkno.ifBlank { latestBase.ifBlank { keyRj } }
                // 只有选中日语时才允许用入口 RJ 直接解析目录树。其他语言版本必须走
                // 语言匹配解析，未收录时应显示“暂未收录”，而不是降级到日文目录树。
                val preferInitialRj = !latest.isDlsiteLanguageUserSelected &&
                    selectedLang == "JPN" &&
                    DlsiteWorkNo.normalizeWorkNo(latestBase, minimumDigits = 6).isNotBlank()
                val directoryRjs = asmrOneTrackRjCandidates(
                    baseRj = latestBase,
                    currentRj = keyRj,
                    dlsiteWorkno = latest.dlsiteWorkno,
                    originalRj = originalRj,
                    selectedLang = selectedLang,
                    preferInitialRj = preferInitialRj
                )
                fun finishWithResolvedAsmrOneTree(
                    workId: String?,
                    site: Int?,
                    tree: List<AsmrOneTrackNodeResponse>,
                    resolvedDetails: WorkDetailsResponse? = null
                ): Boolean {
                    val updated = (_uiState.value as? AlbumDetailUiState.Success)?.model ?: return true
                    if (token != asmrOneLoadToken) {
                        asmrOneAttemptedRj.remove(keyRj)
                        finishAsmrOneLoad(keyRj, resolved = false)
                        return true
                    }
                    val resolvedWorkId = workId?.trim().orEmpty().ifBlank { updated.asmrOneWorkId.orEmpty() }
                    val displayAlbum = mergeAsmrOneHeaderAlbum(
                        currentDisplayAlbum = updated.displayAlbum,
                        localAlbum = updated.localAlbum,
                        fetchedDlsiteInfo = updated.dlsiteInfo,
                        resolvedAsmrOneDetails = resolvedDetails,
                        rjCode = updated.rjCode,
                        asmrOneWorkId = resolvedWorkId.takeIf { it.isNotBlank() } ?: updated.asmrOneWorkId,
                        preserveHeaderAlbumMetadata = updated.preserveHeaderAlbumMetadata
                    )
                    _uiState.value = AlbumDetailUiState.Success(
                        model = updated.copy(
                            displayAlbum = displayAlbum,
                            asmrOneWorkId = resolvedWorkId.takeIf { it.isNotBlank() } ?: updated.asmrOneWorkId,
                            asmrOneSite = site,
                            asmrOneTree = tree,
                            hasResolvedAsmrOneContent = true,
                            isLoadingAsmrOne = false
                        )
                    )
                    return true
                }

                if (asmrOneCrawler.selectedEndpoint() == AsmrOneEndpoint.BACKUP) {
                    val metadataRj = latestBase.ifBlank { keyRj }
                    val metadataDeferred = async {
                        runCatching {
                            val resolution = onlineContentRepository.resolveAsmrOneWork(metadataRj, timeoutMs = 2_500L)
                            resolution to onlineContentRepository.peekAsmrOneResolvedDetails(metadataRj)
                        }.getOrNull()
                    }
                    val backupResult = onlineContentRepository.fetchAsmrOneTracksFromBackupEndpoints(
                        candidateRjs = directoryRjs,
                        throwWhenAllRequestsFail = true
                    )
                    if (backupResult.second.isNotEmpty()) {
                        finishWithResolvedAsmrOneTree(
                            workId = backupResult.first,
                            site = AsmrOneEndpoint.BACKUP,
                            tree = backupResult.second
                        )
                    } else {
                        asmrOneAttemptedRj.remove(keyRj)
                        finishAsmrOneLoad(keyRj, resolved = true)
                    }
                    val metadataResult = metadataDeferred.await()
                    val metadataResolution = metadataResult?.first
                    val metadataDetails = metadataResult?.second
                    if (metadataResolution != null || metadataDetails != null) {
                        finishWithResolvedAsmrOneTree(
                            workId = backupResult.first.orEmpty()
                                .ifBlank { metadataResolution?.first.orEmpty() },
                            site = AsmrOneEndpoint.BACKUP,
                            tree = backupResult.second,
                            resolvedDetails = metadataDetails
                        )
                    }
                    return@launch
                }

                var preferredInitialResolution: Pair<String, Int?>? = null
                var preferredInitialDetails: WorkDetailsResponse? = null
                if (preferInitialRj) {
                    val preferredInitial = onlineContentRepository.resolveAsmrOneWork(latestBase, throwOnRequestFailure = true)
                    if (preferredInitial != null) {
                        preferredInitialResolution = preferredInitial
                        val preferredWorkId = preferredInitial.first
                        val searchDetails = onlineContentRepository.peekAsmrOneResolvedDetails(latestBase)
                        val (preferredResult, preferredDetails) = coroutineScope {
                            val tracksDeferred = async {
                                onlineContentRepository.getAsmrOneTracksCached(preferredWorkId, throwOnRequestFailure = true)
                            }
                            val detailsDeferred = async {
                                searchDetails
                                    ?: runCatching { asmrOneCrawler.getDetails(preferredWorkId) }.getOrNull()
                            }
                            tracksDeferred.await() to detailsDeferred.await()
                        }
                        preferredInitialDetails = preferredDetails
                        if (preferredResult.tree.isNotEmpty()) {
                            finishWithResolvedAsmrOneTree(
                                workId = preferredWorkId,
                                site = preferredResult.site,
                                tree = preferredResult.tree,
                                resolvedDetails = preferredDetails
                            )
                            return@launch
                        }
                    }
                }

                var resolvedOriginal = preferredInitialResolution.takeIf {
                    latestBase.equals(originalRj, ignoreCase = true) ||
                        latestBase.equals(keyRj, ignoreCase = true)
                }
                if (resolvedOriginal == null) {
                    val directRjs = listOf(originalRj, keyRj)
                        .map { DlsiteWorkNo.normalizeWorkNo(it, minimumDigits = 6) }
                        .filter { it.isNotBlank() }
                        .distinct()
                    for (candidateRj in directRjs) {
                        val resolved = onlineContentRepository.resolveAsmrOneWork(
                            candidateRj,
                            throwOnRequestFailure = true
                        ) ?: continue
                        resolvedOriginal = resolved
                        preferredInitialDetails = onlineContentRepository.peekAsmrOneResolvedDetails(candidateRj)
                        break
                    }
                }
                if (resolvedOriginal == null) {
                    asmrOneAttemptedRj.remove(keyRj)
                    if (token != asmrOneLoadToken) {
                        finishAsmrOneLoad(keyRj, resolved = false)
                    } else {
                        finishAsmrOneLoad(keyRj, resolved = true)
                    }
                    return@launch
                }
                val originalWorkId = resolvedOriginal.first

                val originalDetails = preferredInitialDetails
                    ?.takeIf { preferredInitialResolution?.first == originalWorkId }
                    ?: runCatching { asmrOneCrawler.getDetails(originalWorkId) }.getOrNull()

                if (originalDetails != null) {
                    val updated = (_uiState.value as? AlbumDetailUiState.Success)?.model ?: return@launch
                    if (token != asmrOneLoadToken) {
                        asmrOneAttemptedRj.remove(keyRj)
                        finishAsmrOneLoad(keyRj, resolved = false)
                        return@launch
                    }
                    _uiState.value = AlbumDetailUiState.Success(
                        model = updated.copy(
                            displayAlbum = mergeAsmrOneHeaderAlbum(
                                currentDisplayAlbum = updated.displayAlbum,
                                localAlbum = updated.localAlbum,
                                fetchedDlsiteInfo = updated.dlsiteInfo,
                                resolvedAsmrOneDetails = originalDetails,
                                rjCode = updated.rjCode,
                                asmrOneWorkId = originalWorkId,
                                preserveHeaderAlbumMetadata = updated.preserveHeaderAlbumMetadata
                            ),
                            asmrOneWorkId = originalWorkId
                        )
                    )
                }

                val workId = resolveAsmrOneTrackWorkId(
                    resolvedWorkId = originalWorkId,
                    resolvedDetails = originalDetails,
                    selectedLang = selectedLang,
                    selectedRjs = directoryRjs
                )
                val trackResult = workId
                    ?.let { onlineContentRepository.getAsmrOneTracksCached(it, throwOnRequestFailure = true) }
                    ?: AsmrOneTracksResult(emptyList(), null)
                if (token != asmrOneLoadToken) {
                    asmrOneAttemptedRj.remove(keyRj)
                    finishAsmrOneLoad(keyRj, resolved = false)
                    return@launch
                }
                if (workId.isNullOrBlank()) {
                    asmrOneAttemptedRj.remove(keyRj)
                    finishAsmrOneLoad(keyRj, resolved = true)
                    return@launch
                }
                finishWithResolvedAsmrOneTree(
                    workId = workId,
                    site = trackResult.site,
                    tree = trackResult.tree,
                    resolvedDetails = originalDetails
                )
            } catch (e: TimeoutCancellationException) {
                asmrOneAttemptedRj.remove(keyRj)
                finishAsmrOneLoad(keyRj, resolved = true, showFailureMessage = true)
            } catch (e: CancellationException) {
                asmrOneAttemptedRj.remove(keyRj)
                finishAsmrOneLoad(keyRj, resolved = false)
            } catch (e: Exception) {
                asmrOneAttemptedRj.remove(keyRj)
                finishAsmrOneLoad(keyRj, resolved = true, showFailureMessage = true)
            }
        }
    }

    fun ensureDlsitePlayLoaded(showFailureMessage: Boolean = true) {
        val current = _uiState.value as? AlbumDetailUiState.Success ?: return
        val baseRj = current.model.baseRjCode.trim().uppercase()
        val candidates0 = DlsiteWorkNo.normalizeCandidates(
            listOf(
                current.model.dlsitePlayWorkno,
                current.model.dlsiteWorkno,
                current.model.rjCode,
                baseRj
            )
        )
        val playCookieFingerprint = dlsiteAuthStore.getPlayCookie().trim().hashCode()
        val attemptKey = candidates0.joinToString("|") + "#" + playCookieFingerprint

        if (
            candidates0.isEmpty() ||
            current.model.dlsitePlayTree.isNotEmpty() ||
            current.model.isLoadingDlsitePlay ||
            dlsitePlayAttemptedRj.contains(attemptKey)
        ) return
        dlsitePlayAttemptedRj.add(attemptKey)
        dlsitePlayLoadJob?.cancel()
        dlsitePlayLoadJob = viewModelScope.launch {
            _uiState.value = AlbumDetailUiState.Success(
                model = current.model.copy(
                    isLoadingDlsitePlay = true,
                    hasResolvedDlsitePlayContent = false
                )
            )
            try {
                val editions = runCatching {
                    if (current.model.dlsiteEditions.size > 1) {
                        current.model.dlsiteEditions
                    } else if (baseRj.isNotBlank()) {
                        dlsiteProductInfoClient.fetchLanguageEditions(baseRj)
                    } else {
                        emptyList()
                    }
                }.getOrDefault(emptyList())
                val editionWorknos = editions
                    .asSequence()
                    .map { it.workno.trim().uppercase() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sortedBy { if (it.startsWith("RJ", ignoreCase = true)) 0 else 1 }
                    .toList()
                val candidates = (candidates0 + editionWorknos).distinct()

                var pickedWorkno: String? = null
                var pickedResult: DlsitePlayTreeResult? = null
                var lastError: Exception? = null
                var sawNotAvailable = false
                for (workno in candidates) {
                    val res = try {
                        withTimeout(ONLINE_DIRECTORY_REQUEST_TIMEOUT_MS) {
                            dlsitePlayWorkClient.fetchPlayableTree(workno)
                        }
                    } catch (e: TimeoutCancellationException) {
                        lastError = e
                        continue
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        lastError = e
                        continue
                    }
                    if (res.status == DlsitePlayLoadStatus.Success && res.tree.isNotEmpty()) {
                        pickedWorkno = workno
                        pickedResult = res
                        break
                    }
                    if (res.status == DlsitePlayLoadStatus.NotAvailable) {
                        sawNotAvailable = true
                    }
                }

                val result = pickedResult ?: DlsitePlayTreeResult(
                    tree = emptyList(),
                    subtitlesByUrl = emptyMap(),
                    status = if (sawNotAvailable) DlsitePlayLoadStatus.NotAvailable else DlsitePlayLoadStatus.Success
                )
                result.subtitlesByUrl.forEach { (url, subs) ->
                    if (subs.isNotEmpty()) OnlineLyricsStore.set(url, subs)
                }
                val updated = (_uiState.value as? AlbumDetailUiState.Success)?.model ?: return@launch
                if (pickedResult == null && lastError != null && !sawNotAvailable) {
                    dlsitePlayAttemptedRj.remove(attemptKey)
                    if (showFailureMessage) {
                        messageManager.showError("DLsite Play 加载失败，请稍后重试")
                    }
                }
                _uiState.value = AlbumDetailUiState.Success(
                    model = updated.copy(
                        dlsitePlayTree = result.tree,
                        dlsitePlayWorkno = pickedWorkno?.trim().orEmpty(),
                        hasResolvedDlsitePlayContent = true,
                        isLoadingDlsitePlay = false
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                dlsitePlayAttemptedRj.remove(attemptKey)
                val updated = (_uiState.value as? AlbumDetailUiState.Success)?.model ?: return@launch
                _uiState.value = AlbumDetailUiState.Success(
                    model = updated.copy(
                        hasResolvedDlsitePlayContent = true,
                        isLoadingDlsitePlay = false
                    )
                )
            }
        }
    }

    private fun albumFromInitialHint(rj: String, hint: AlbumCoverHint?): Album {
        return albumFromCoverHint(rj, hint)
    }

    private fun createRouteInitialUiState(savedStateHandle: SavedStateHandle): AlbumDetailUiState {
        val albumId = savedStateHandle.get<Long>("albumId")?.takeIf { it > 0L }
        val routeRj = savedStateHandle.get<String>("rjCode")
            ?.trim()
            .orEmpty()
            .ifBlank { savedStateHandle.get<String>("rj")?.trim().orEmpty() }
            .uppercase()
        val hint = AlbumCoverHintStore.peekHint(albumId, routeRj)
        val initialRj = routeRj.ifBlank { hint?.rjCode.orEmpty() }
        val hintAlbum = albumFromInitialHint(initialRj, hint)
        val preserveHeaderMetadata = shouldPreserveHeaderAlbumMetadata(hint)
        return AlbumDetailUiState.Success(
            model = createInitialAlbumDetailModel(
                rj = initialRj,
                displayAlbum = hintAlbum,
                dlsiteInfo = hintAlbum.takeIf { preserveHeaderMetadata },
                preserveHeaderAlbumMetadata = preserveHeaderMetadata
            )
        )
    }

    private fun createInitialAlbumDetailModel(
        rj: String,
        displayAlbum: Album,
        localAlbum: Album? = null,
        dlsiteInfo: Album? = null,
        preserveHeaderAlbumMetadata: Boolean = false
    ): AlbumDetailModel {
        return AlbumDetailModel(
            baseRjCode = rj,
            rjCode = rj,
            listenTogetherRjListenerCount = null,
            displayAlbum = displayAlbum,
            localAlbum = localAlbum,
            dlsiteInfo = dlsiteInfo,
            dlsiteGalleryUrls = emptyList(),
            dlsiteTrialTracks = emptyList(),
            dlsiteRecommendations = DlsiteRecommendations(),
            dlsiteWorkno = rj,
            dlsitePlayWorkno = "",
            dlsiteEditions = defaultDlsiteEditions(rj),
            dlsiteSelectedLang = "JPN",
            hasResolvedInitialDlsiteTarget = false,
            hasLoadedInitialDlsiteContent = false,
            hasResolvedAsmrOneContent = false,
            hasResolvedDlsitePlayContent = false,
            preserveHeaderAlbumMetadata = preserveHeaderAlbumMetadata,
            isDlsiteLanguageUserSelected = false,
            asmrOneWorkId = null,
            asmrOneSite = null,
            asmrOneTree = emptyList(),
            dlsitePlayTree = emptyList(),
            isLoadingDlsite = false,
            isLoadingDlsiteTrial = false,
            isLoadingAsmrOne = false,
            isLoadingDlsitePlay = false
        )
    }

    private sealed interface LocalAlbumLoadResult {
        data class Available(val album: Album) : LocalAlbumLoadResult
        data object Removed : LocalAlbumLoadResult
    }

    private suspend fun loadLocalAlbumByIdWithAvailabilityCheck(albumId: Long): LocalAlbumLoadResult {
        val entity = libraryReadRepository.getAlbumById(albumId)
        if (entity == null) {
            notifyLocalAlbumRemoved(albumId, emptySet())
            return LocalAlbumLoadResult.Removed
        }
        val tracks = libraryReadRepository.getTracksForAlbumOnce(albumId)
        val isMissing = withContext(Dispatchers.IO) {
            shouldRemoveMissingLocalAlbum(entity, tracks, ::queryLocalSourceAvailability)
        }
        if (!isMissing) {
            return LocalAlbumLoadResult.Available(entityToDomain(entity, tracks))
        }

        val removedMediaIds = withContext(Dispatchers.IO) {
            libraryWriteRepository.deleteAlbumIfMissingLocally(albumId) { latest, latestTracks ->
                shouldRemoveMissingLocalAlbum(latest, latestTracks, ::queryLocalSourceAvailability)
            }
        }
        val remaining = libraryReadRepository.getAlbumById(albumId)
        if (remaining != null) {
            return LocalAlbumLoadResult.Available(entityToDomain(remaining))
        }
        notifyLocalAlbumRemoved(albumId, removedMediaIds)
        return LocalAlbumLoadResult.Removed
    }

    private fun notifyLocalAlbumRemoved(albumId: Long, mediaIds: Set<String>) {
        _uiState.value = AlbumDetailUiState.Removed(albumId = albumId, mediaIds = mediaIds)
        messageManager.showInfo("作品已被删除")
    }

    private fun queryLocalSourceAvailability(pathOrUri: String): LocalSourceAvailability {
        val source = pathOrUri.trim()
        if (source.isBlank()) return LocalSourceAvailability.Unknown
        if (!source.startsWith("content://", ignoreCase = true)) {
            val file = if (source.startsWith("file://", ignoreCase = true)) {
                runCatching { Uri.parse(source).path.orEmpty() }.getOrNull()?.let(::File)
                    ?: return LocalSourceAvailability.Unknown
            } else {
                File(source)
            }
            return if (file.exists()) LocalSourceAvailability.Available else LocalSourceAvailability.Missing
        }

        val uri = runCatching { Uri.parse(source) }.getOrNull() ?: return LocalSourceAvailability.Unknown
        val documentUri = runCatching {
            val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
                ?: DocumentsContract.getTreeDocumentId(uri)
            DocumentsContract.buildDocumentUriUsingTree(uri, documentId)
        }.getOrElse { return LocalSourceAvailability.Unknown }
        return try {
            val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            context.contentResolver.query(documentUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) LocalSourceAvailability.Available else LocalSourceAvailability.Missing
            } ?: LocalSourceAvailability.Unknown
        } catch (_: SecurityException) {
            LocalSourceAvailability.Unknown
        } catch (error: Exception) {
            if (isMissingLocalDocumentFailure(error)) {
                LocalSourceAvailability.Missing
            } else {
                LocalSourceAvailability.Unknown
            }
        }
    }

    private suspend fun loadLocalAlbumByRj(rjCode: String): Album? {
        val normalized = rjCode.trim().uppercase()
        val entity = libraryReadRepository.getAlbumByWorkIdOnce(normalized)
            ?: libraryReadRepository.getAlbumByWorkIdOnce(rjCode.trim())
            ?: return null
        return entityToDomain(entity)
    }

    private suspend fun entityToDomain(
        entity: AlbumEntity,
        trackEntities: List<TrackEntity>? = null,
    ): Album {
        val tracks = trackEntities?.map { it.toDomain() } ?: loadLocalTracks(entity)
        return Album(
            id = entity.id,
            title = entity.titleForDisplay,
            displayTitle = entity.displayTitle,
            path = entity.path,
            localPath = entity.localPath,
            downloadPath = entity.downloadPath,
            circle = entity.circle,
            cv = entity.cv,
            tags = entity.tags.split(",").filter { it.isNotBlank() },
            coverUrl = entity.coverUrl,
            coverPath = entity.coverPath,
            coverThumbPath = entity.coverThumbPath,
            workId = entity.workId,
            rjCode = entity.rjCode.ifBlank { entity.workId },
            description = entity.description,
            tracks = tracks
        )
    }

    fun downloadAlbum() {
        val current = _uiState.value as? AlbumDetailUiState.Success ?: return
        val model = current.model
        val album = model.displayAlbum

        val existingLocalKeys = LinkedHashSet<String>()
        val existingLocalKeysNoGroup = LinkedHashSet<String>()
        model.localAlbum?.tracks
            ?.filter { !it.path.trim().startsWith("http", ignoreCase = true) }
            ?.forEach { t ->
                existingLocalKeys.add(TrackKeyNormalizer.buildKey(t.title, t.group, null))
                existingLocalKeysNoGroup.add(TrackKeyNormalizer.buildKey(t.title, "", null))
            }
        
        val folderName = safeFolderName(album.rjCode.ifBlank { album.workId }.ifBlank { album.title })
        val items = mutableListOf<RelativeDownloadItem>()
        val coverUrl = album.coverUrl.trim()
        if (coverUrl.startsWith("http", ignoreCase = true)) {
            val ext = coverUrl.substringBefore('?').substringAfterLast('.', "").takeIf { it.length in 2..5 } ?: "jpg"
            items += RelativeDownloadItem(url = coverUrl, relativePath = "cover.$ext")
        }

        album.tracks.forEachIndexed { index, track ->
            val url = track.path.trim()
            if (!url.startsWith("http", ignoreCase = true)) return@forEachIndexed
            val key = TrackKeyNormalizer.buildKey(track.title, track.group, null)
            val keyNoGroup = TrackKeyNormalizer.buildKey(track.title, "", null)
            if (existingLocalKeys.contains(key) || existingLocalKeysNoGroup.contains(keyNoGroup)) {
                return@forEachIndexed
            }
            val ext = url.substringBefore('?').substringAfterLast('.', "").takeIf { it.length in 2..6 } ?: "mp3"
            val fileName = "${(index + 1).toString().padStart(2, '0')}_${safeFileName(track.title)}.$ext"
            items += RelativeDownloadItem(url = url, relativePath = fileName)
        }
        if (items.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            showEnqueueBatchResult(
                downloadManager.enqueueBatch(
                    DownloadBatchRequest(
                        albumDirectoryName = folderName,
                        logicalTaskKey = "album:$folderName",
                        items = items,
                        taskSubtitle = album.title,
                        albumTitle = album.title,
                        albumCircle = album.circle,
                        albumCv = album.cv,
                        albumTagsCsv = album.tags.joinToString(","),
                        albumCoverUrl = album.coverUrl,
                        albumWorkId = album.workId,
                        albumRjCode = album.rjCode,
                    ),
                ),
            )
        }
    }

    fun downloadAsmrOneSelected(selectedLeafPaths: Set<String>) {
        val current = _uiState.value as? AlbumDetailUiState.Success ?: return
        enqueueRemoteTreeSelectionDownload(
            model = current.model,
            tree = current.model.asmrOneTree,
            selectedLeafPaths = selectedLeafPaths,
            relativeBaseDir = ""
        )
    }

    fun downloadDlsitePlaySelected(selectedLeafPaths: Set<String>) {
        val current = _uiState.value as? AlbumDetailUiState.Success ?: return
        enqueueRemoteTreeSelectionDownload(
            model = current.model,
            tree = current.model.dlsitePlayTree,
            selectedLeafPaths = selectedLeafPaths,
            relativeBaseDir = ""
        )
    }

    fun downloadDlsitePlayLosslessArchive() {
        val current = _uiState.value as? AlbumDetailUiState.Success ?: return
        val model = current.model
        if (model.dlsitePlayTree.isEmpty()) return

        val album = model.displayAlbum
        val workno = normalizeWorkNo(
            model.dlsitePlayWorkno
                .ifBlank { model.dlsiteWorkno }
                .ifBlank { model.rjCode }
                .ifBlank { album.rjCode.ifBlank { album.workId } }
        )
        if (workno.isBlank()) {
            messageManager.showError("无法确定 DLsite Play 作品编号")
            return
        }

        val folderName = safeFolderName(album.rjCode.ifBlank { album.workId }.ifBlank { workno }.ifBlank { album.title })
        val items = mutableListOf(
            RelativeDownloadItem(
                url = "https://play.dlsite.com/api/v3/download?workno=$workno",
                relativePath = "dlsite_lossless_archive.zip",
            ),
        )
        val coverUrl = album.coverUrl.trim()
        if (coverUrl.startsWith("http", ignoreCase = true)) {
            val ext = coverUrl.substringBefore('?').substringAfterLast('.', "")
                .takeIf { it.length in 2..5 } ?: "jpg"
            items.add(0, RelativeDownloadItem(url = coverUrl, relativePath = "cover.$ext"))
        }
        viewModelScope.launch(Dispatchers.IO) {
            showEnqueueBatchResult(
                downloadManager.enqueueBatch(
                    DownloadBatchRequest(
                        albumDirectoryName = folderName,
                        logicalTaskKey = "album:$folderName",
                        items = items,
                        taskSubtitle = album.title,
                        albumTitle = album.title,
                        albumCircle = album.circle,
                        albumCv = album.cv,
                        albumTagsCsv = album.tags.joinToString(","),
                        albumCoverUrl = album.coverUrl,
                        albumWorkId = album.workId,
                        albumRjCode = album.rjCode,
                    ),
                ),
            )
        }
    }

    fun downloadDlsiteTrialSelected(selectedLeafPaths: Set<String>) {
        val current = _uiState.value as? AlbumDetailUiState.Success ?: return
        enqueueRemoteTreeSelectionDownload(
            album = current.model.displayAlbum,
            tree = buildDlsiteTrialDownloadTree(current.model.dlsiteTrialTracks),
            selectedLeafPaths = selectedLeafPaths,
            relativeBaseDir = DlsiteTrialDownloadDirectoryName
        )
    }

    fun downloadSavedOnlineTrack(track: Track, relativePath: String) {
        val current = _uiState.value as? AlbumDetailUiState.Success ?: return
        val url = track.path.trim()
        if (!isOnlineTrackPath(url)) return

        val normalizedRelativePath = relativePath
            .replace('\\', '/')
            .trim()
            .trimStart('/')
            .ifBlank { safeFileName(track.title) }
        if (treeFileTypeForNode(normalizedRelativePath, url) != TreeFileType.Audio) return

        enqueueRemoteLeafDownloads(
            album = current.model.displayAlbum,
            selected = listOf(
                AsmrOneLeafDownload(
                    url = url,
                    relativePath = normalizedRelativePath,
                    duration = track.duration
                )
            ),
            relativeBaseDir = "",
            includeCover = false
        )
    }

    internal suspend fun resolveLocalIncrementalSelectionPaths(
        tree: List<AsmrOneTrackNodeResponse>
    ): LocalIncrementalSelectionPaths {
        val current = _uiState.value as? AlbumDetailUiState.Success
            ?: return LocalIncrementalSelectionPaths()
        val localAlbum = current.model.localAlbum ?: return LocalIncrementalSelectionPaths()
        if (localAlbum.id <= 0L || tree.isEmpty()) return LocalIncrementalSelectionPaths()

        return withContext(Dispatchers.IO) {
            val savedResources = libraryReadRepository.getOnlineSavedResourcesForAlbum(localAlbum.id)
            val localIndex = loadOrBuildLocalTreeIndex(
                context = context,
                albumId = localAlbum.id,
                albumPaths = localAlbum.getAllLocalPaths(),
                tracks = localAlbum.tracks,
                onlineSavedResources = savedResources,
                sources = localTreeSourcesForAlbum(localAlbum),
            )
            val localFiles = collectLocalSelectionFiles(localIndex)
            val remoteFiles = flattenAsmrOneLeafDownloads(tree)
                .filter { leaf ->
                    isLibraryResourceSavableTreeFileType(
                        treeFileTypeForNode(leaf.relativePath, leaf.url)
                    )
                }
                .map { leaf ->
                    RemoteSelectionFileRef(
                        relativePath = leaf.relativePath,
                        url = leaf.url
                    )
                }

            LocalIncrementalSelectionPaths(
                downloadedPaths = resolveExistingRemoteSelectionPaths(
                    remoteFiles = remoteFiles,
                    localFiles = localFiles,
                    includeOnlineFiles = false
                ),
                savedPaths = resolveExistingRemoteSelectionPaths(
                    remoteFiles = remoteFiles,
                    localFiles = localFiles,
                    includeOnlineFiles = true
                )
            )
        }
    }

    fun saveOnlineSelectedToLibrary(
        selectedLeafPaths: Set<String>,
        useDlsitePlayTree: Boolean = false
    ) {
        val current = _uiState.value as? AlbumDetailUiState.Success ?: return
        val model = current.model
        val displayAlbum = resolvedOnlineActionAlbum(model)
        val targetLocalAlbumId = model.localAlbum?.id?.takeIf { it > 0L }
        val tree = if (useDlsitePlayTree) model.dlsitePlayTree else model.asmrOneTree
        if (tree.isEmpty()) return

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val leaves = flattenOnlineSaveLeaves(tree)
                val selected = if (selectedLeafPaths.isEmpty()) leaves else leaves.filter { selectedLeafPaths.contains(it.relativePath) }
                if (selected.isEmpty()) {
                    return@withContext SaveOnlineToLibraryResult(
                        selectedCount = 0,
                        insertedCount = 0,
                        resourceSavedCount = 0
                    )
                }

                val rj = normalizeWorkNo(displayAlbum.rjCode.ifBlank { displayAlbum.workId }.ifBlank { model.rjCode })
                val workKey = rj.ifBlank { displayAlbum.workId.trim().ifBlank { displayAlbum.title.trim() } }
                val onlinePath = "web://rj/${workKey.uppercase()}"
                val albumDir = onlineSaveAlbumDir(displayAlbum, workKey).apply {
                    mkdirs()
                    ensureNoMediaMarkers(this)
                }
                val playableSelected = selected.filter { isPlayableTreeFileType(it.fileType) }
                val resourceSelected = selected.filter { !isPlayableTreeFileType(it.fileType) }

                fun canonicalUrl(url: String): String {
                    return url.trim().substringBefore('#').substringBefore('?')
                }

                val saveResult = libraryWriteRepository.saveOnlineSelectedToLibrary(
                    targetLocalAlbumId = targetLocalAlbumId,
                    workKey = workKey,
                    onlinePath = onlinePath,
                    albumDirPath = albumDir.absolutePath,
                    displayAlbum = displayAlbum,
                    rj = rj,
                    playableLeaves = playableSelected.map { leaf ->
                        LibraryWriteRepository.OnlineSaveTrackSpec(
                            title = leaf.title,
                            url = leaf.url,
                            duration = leaf.duration,
                            group = leaf.group,
                            subtitleSources = leaf.subtitleSources
                        )
                    },
                    resourceLeaves = resourceSelected.map { leaf ->
                        LibraryWriteRepository.OnlineSaveResourceSpec(
                            relativePath = leaf.relativePath,
                            url = leaf.url,
                            fileType = leaf.fileType
                        )
                    },
                    canonicalUrl = { url -> canonicalUrl(url) },
                )
                val insertedCount = saveResult.insertedCount
                val resourceSavedCount = saveResult.resourceSavedCount
                val albumIdResult = saveResult.albumId

                if (albumIdResult > 0L) {
                    refreshAlbumAudioAggregate(albumIdResult)
                }

                SaveOnlineToLibraryResult(
                    selectedCount = selected.size,
                    insertedCount = insertedCount,
                    resourceSavedCount = resourceSavedCount
                )
            }

            val selectedCount = result.selectedCount
            val changedCount = result.insertedCount + result.resourceSavedCount
            val skippedCount = selectedCount - changedCount

            if (selectedCount <= 0) {
                messageManager.showInfo("没有可保存文件")
            } else if (changedCount <= 0) {
                messageManager.showInfo("本地已存在，未重复保存")
            } else if (skippedCount > 0) {
                messageManager.showSuccess("已保存到本地库（${changedCount}项），跳过已存在（${skippedCount}项）")
            } else {
                messageManager.showSuccess("已保存到本地库（${changedCount}项）")
            }
        }
    }

    private data class SaveOnlineToLibraryResult(
        val selectedCount: Int,
        val insertedCount: Int,
        val resourceSavedCount: Int
    )

    private data class OnlineSaveLeaf(
        val relativePath: String,
        val title: String,
        val url: String,
        val duration: Double,
        val group: String,
        val fileType: TreeFileType,
        val subtitleSources: List<RemoteSubtitleSource>
    )

    private fun flattenOnlineSaveLeaves(tree: List<AsmrOneTrackNodeResponse>): List<OnlineSaveLeaf> {
        val out = mutableListOf<OnlineSaveLeaf>()
        val subtitleExts = setOf("lrc", "srt", "vtt")

        data class LeafFile(
            val rawTitle: String,
            val safeTitle: String,
            val url: String,
            val duration: Double?,
            val fileType: TreeFileType
        ) {
            val ext: String = run {
                val ext0 = rawTitle.substringAfterLast('.', "").lowercase()
                if (ext0.isNotBlank()) ext0 else url.substringBefore('?').substringAfterLast('.', "").lowercase()
            }
        }

        val subtitleCandidates = collectSubtitleCandidates(
            tree,
            subtitleExts,
            extOf = { rawTitle, url ->
                val ext0 = rawTitle.substringAfterLast('.', "").lowercase()
                if (ext0.isNotBlank()) ext0 else url.substringBefore('?').substringAfterLast('.', "").lowercase()
            }
        ).map { entry ->
            entry.candidate to LeafFile(
                rawTitle = entry.rawTitle,
                safeTitle = entry.safeTitle,
                url = entry.url,
                duration = entry.duration,
                fileType = treeFileTypeForNode(entry.rawTitle, entry.url, entry.node.type)
            )
        }

        fun walk(nodes: List<AsmrOneTrackNodeResponse>, parentPath: String) {
            val leafFiles = nodes.mapNotNull { node ->
                val children = node.children.orEmpty()
                val rawTitle = node.title?.trim().orEmpty().ifBlank { "item" }
                val url = node.mediaDownloadUrl ?: node.streamUrl
                if (children.isNotEmpty() || url.isNullOrBlank()) return@mapNotNull null
                val safeTitle = sanitizeFolderName(rawTitle)
                val fileType = treeFileTypeForNode(rawTitle, url, node.type)
                if (!isLibraryResourceSavableTreeFileType(fileType)) return@mapNotNull null
                LeafFile(
                    rawTitle = rawTitle,
                    safeTitle = safeTitle,
                    url = url,
                    duration = node.duration,
                    fileType = fileType
                )
            }

            leafFiles.forEach { leaf ->
                val path = if (parentPath.isBlank()) leaf.safeTitle else "$parentPath/${leaf.safeTitle}"
                val relDir = path.substringBeforeLast('/', "")
                val group = relDir
                val subsRaw = if (leaf.fileType == TreeFileType.Audio) {
                    val matched = SubtitleMatchSupport.matchBest(path.substringBeforeLast('.'), subtitleCandidates.map { it.first })
                    if (matched != null) {
                        subtitleCandidates.firstOrNull { it.first.sourceRef == matched.sourceRef }?.second?.let { subtitleLeaf ->
                            listOf(RemoteSubtitleSource(url = subtitleLeaf.url, language = matched.language, ext = subtitleLeaf.ext))
                        }.orEmpty()
                    } else {
                        emptyList()
                    }
                } else emptyList()
                val subs = if (leaf.fileType == TreeFileType.Audio && subsRaw.isNotEmpty()) {
                    subsRaw
                } else if (leaf.fileType == TreeFileType.Audio) {
                    OnlineLyricsStore.get(leaf.url)
                } else {
                    emptyList()
                }
                out.add(
                    OnlineSaveLeaf(
                        relativePath = path,
                        title = leaf.safeTitle.substringBeforeLast('.'),
                        url = leaf.url,
                        duration = leaf.duration ?: 0.0,
                        group = group,
                        fileType = leaf.fileType,
                        subtitleSources = subs
                    )
                )
            }

            nodes.forEach { node ->
                val children = node.children.orEmpty()
                if (children.isEmpty()) return@forEach
                val rawTitle = node.title?.trim().orEmpty().ifBlank { "item" }
                val safeTitle = sanitizeFolderName(rawTitle)
                val path = if (parentPath.isBlank()) safeTitle else "$parentPath/$safeTitle"
                walk(children, path)
            }
        }
        walk(tree, "")
        return out.map { leaf ->
            if (leaf.fileType != TreeFileType.Audio) {
                return@map leaf
            }
            val matched = SubtitleMatchSupport.matchBest(leaf.relativePath.substringBeforeLast('.'), subtitleCandidates.map { it.first })
            val subtitles = if (matched != null) {
                subtitleCandidates.firstOrNull { it.first.sourceRef == matched.sourceRef }?.second?.let { subtitleLeaf ->
                    listOf(RemoteSubtitleSource(url = subtitleLeaf.url, language = matched.language, ext = subtitleLeaf.ext))
                }.orEmpty()
            } else {
                leaf.subtitleSources
            }
            leaf.copy(subtitleSources = subtitles)
        }
    }

    private fun onlineSaveAlbumDir(album: Album, workKey: String): File {
        val baseDir = File(context.getExternalFilesDir(null), "albums")
        val folderName = safeFolderName(
            album.rjCode.ifBlank { album.workId }.ifBlank { workKey }.ifBlank { album.title }
        )
        return File(baseDir, folderName)
    }

    private fun ensureNoMediaMarkers(dir: File) {
        runCatching {
            if (!dir.exists()) dir.mkdirs()
            val albumsRoot = File(context.getExternalFilesDir(null), "albums")
            if (!albumsRoot.exists()) albumsRoot.mkdirs()
            val rootMarker = File(albumsRoot, ".nomedia")
            if (!rootMarker.exists()) rootMarker.createNewFile()
            val marker = File(dir, ".nomedia")
            if (!marker.exists()) marker.createNewFile()
        }
    }

    private fun normalizeWorkNo(raw: String): String {
        return DlsiteWorkNo.extractWorkNo(raw)
    }

    private suspend fun loadLocalTracks(albumEntity: AlbumEntity): List<Track> {
        val fromDb = runCatching { libraryReadRepository.getTracksForAlbumOnce(albumEntity.id) }.getOrDefault(emptyList())
        if (fromDb.isNotEmpty()) {
            return fromDb.map { it.toDomain() }
        }
        return emptyList()
    }

    private fun TrackEntity.toDomain(): Track {
        return Track(
            id = id,
            albumId = albumId,
            title = titleForDisplay,
            path = path,
            duration = duration,
            group = group,
            lyricsRelativePathNoExt = ""
        )
    }

    private fun safeFolderName(input: String): String {
        return input.trim().ifEmpty { "album" }.replace(Regex("""[\\/:*?"<>|]"""), "_")
    }

    private fun safeFileName(input: String): String {
        return input.trim().ifEmpty { "track" }.replace(Regex("""[\\/:*?"<>|]"""), "_")
    }

    private fun resolvedOnlineActionAlbum(model: AlbumDetailModel): Album {
        val resolvedRj = resolveAlbumDetailRj(
            routeRj = model.rjCode.ifBlank { model.baseRjCode },
            localAlbum = model.localAlbum
        )
        return model.displayAlbum.withResolvedWorkIdentity(
            rjCode = resolvedRj,
            asmrOneWorkId = model.asmrOneWorkId
        )
    }

    private fun enqueueRemoteTreeSelectionDownload(
        model: AlbumDetailModel,
        tree: List<AsmrOneTrackNodeResponse>,
        selectedLeafPaths: Set<String>,
        relativeBaseDir: String
    ) {
        val album = resolvedOnlineActionAlbum(model)
        val localAlbum = model.localAlbum
        val localAlbumId = localAlbum?.id ?: 0L
        val shouldBindLocalIdentity = localAlbumId > 0L &&
            album.rjCode.isNotBlank() &&
            (localAlbum?.rjCode.isNullOrBlank() || localAlbum?.workId.isNullOrBlank())
        if (!shouldBindLocalIdentity) {
            enqueueRemoteTreeSelectionDownload(album, tree, selectedLeafPaths, relativeBaseDir)
            return
        }

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val entity = libraryReadRepository.getAlbumById(localAlbumId) ?: return@withContext
                    val updated = entity.copy(
                        workId = entity.workId.ifBlank { album.workId.ifBlank { album.rjCode } },
                        rjCode = entity.rjCode.ifBlank { album.rjCode }
                    )
                    if (updated != entity) libraryWriteRepository.updateAlbum(updated)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // 下载任务仍可通过自身的作品元信息在完成后合并进本地库。
            }
            enqueueRemoteTreeSelectionDownload(album, tree, selectedLeafPaths, relativeBaseDir)
        }
    }

    private fun enqueueRemoteTreeSelectionDownload(
        album: Album,
        tree: List<AsmrOneTrackNodeResponse>,
        selectedLeafPaths: Set<String>,
        relativeBaseDir: String
    ) {
        if (tree.isEmpty()) return

        val leaves = flattenAsmrOneLeafDownloads(tree)
        val subExts = setOf("lrc", "srt", "vtt")

        val initialSelected = if (selectedLeafPaths.isEmpty()) {
            leaves.filter { leaf ->
                isDownloadableTreeFileType(treeFileTypeForName(leaf.relativePath))
            }
        } else {
            leaves.filter { selectedLeafPaths.contains(it.relativePath) }
        }

        if (initialSelected.isEmpty()) return

        val selected = linkedSetOf<AsmrOneLeafDownload>()
        initialSelected.forEach { media ->
            selected.add(media)
            val mediaRelPath = media.relativePath
            val mediaBase = mediaRelPath.substringBeforeLast('.')
            val mediaDir = mediaRelPath.substringBeforeLast('/', "")

            leaves.forEach subtitleLoop@{ potentialSub ->
                val subRelPath = potentialSub.relativePath
                val subExt = subRelPath.substringAfterLast('.').lowercase()
                if (!subExts.contains(subExt)) return@subtitleLoop

                val subDir = subRelPath.substringBeforeLast('/', "")
                if (subDir != mediaDir) return@subtitleLoop

                val subBase = subRelPath.substringBeforeLast('.')
                val isMatch = subBase.equals(mediaBase, ignoreCase = true) ||
                    subBase.startsWith("$mediaBase.", ignoreCase = true)
                if (isMatch) selected.add(potentialSub)
            }
        }

        enqueueRemoteLeafDownloads(
            album = album,
            selected = selected,
            relativeBaseDir = relativeBaseDir,
            includeCover = true
        )
    }

    private fun enqueueRemoteLeafDownloads(
        album: Album,
        selected: Collection<AsmrOneLeafDownload>,
        relativeBaseDir: String,
        includeCover: Boolean
    ) {
        if (selected.isEmpty()) return

        val rjOrWorkId = album.rjCode.ifBlank { album.workId }
        val folderName = safeFolderName(rjOrWorkId.ifBlank { album.title })
        val normalizedBaseDir = relativeBaseDir.trim().trim('/', '\\')
        val taskKey = buildRemoteDownloadTaskKey(folderName, normalizedBaseDir)
        val taskSubtitle = album.title
        val batchItems = mutableListOf<RelativeDownloadItem>()
        if (includeCover) {
            val coverUrl = album.coverUrl.trim()
            if (coverUrl.startsWith("http", ignoreCase = true)) {
                val ext = coverUrl.substringBefore('?').substringAfterLast('.', "")
                    .takeIf { it.length in 2..5 } ?: "jpg"
                val relativeCover = listOf(normalizedBaseDir, "cover.$ext")
                    .filter { it.isNotBlank() }
                    .joinToString("/")
                batchItems += RelativeDownloadItem(url = coverUrl, relativePath = relativeCover)
            }
        }

        selected.forEach { item ->
            val relPath = item.relativePath.replace('\\', '/')
            val rawName = relPath.substringAfterLast('/', relPath)

            val url = item.url.trim()
            if (!url.startsWith("http", ignoreCase = true)) return@forEach

            val baseName = safeFileName(rawName)
            val extFromName = baseName.substringAfterLast('.', "").takeIf { it.isNotBlank() }
            val extFromUrl = url.substringBefore('?').substringAfterLast('.', "").takeIf { it.length in 2..6 }
            val fileName = if (extFromName != null) {
                baseName
            } else {
                val defaultExt = when (treeFileTypeForName(relPath)) {
                    TreeFileType.Video -> "mp4"
                    TreeFileType.Image -> "jpg"
                    TreeFileType.Pdf -> "pdf"
                    TreeFileType.Archive -> "zip"
                    TreeFileType.Document -> "doc"
                    TreeFileType.Spreadsheet -> "csv"
                    TreeFileType.Presentation -> "ppt"
                    TreeFileType.Code -> "txt"
                    TreeFileType.Ebook -> "epub"
                    TreeFileType.Font -> "ttf"
                    TreeFileType.AppPackage -> "apk"
                    else -> "mp3"
                }
                val ext = extFromUrl ?: defaultExt
                "$baseName.$ext"
            }

            val relDir = relPath.substringBeforeLast('/', "")
            val relativeFilePath = listOf(normalizedBaseDir, relDir, fileName)
                .filter { it.isNotBlank() }
                .joinToString("/")
            batchItems += RelativeDownloadItem(
                url = url,
                relativePath = relativeFilePath,
                dlsitePlayImageSeed = item.dlsitePlayImageSeed,
                dlsitePlayImageWidth = item.dlsitePlayImageWidth,
                dlsitePlayImageHeight = item.dlsitePlayImageHeight,
            )
        }
        if (batchItems.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            showEnqueueBatchResult(
                downloadManager.enqueueBatch(
                    DownloadBatchRequest(
                        albumDirectoryName = folderName,
                        logicalTaskKey = taskKey,
                        items = batchItems,
                        taskSubtitle = taskSubtitle,
                        albumTitle = album.title,
                        albumCircle = album.circle,
                        albumCv = album.cv,
                        albumTagsCsv = album.tags.joinToString(","),
                        albumCoverUrl = album.coverUrl,
                        albumWorkId = album.workId,
                        albumRjCode = album.rjCode,
                    ),
                ),
            )
        }
    }

    private fun showEnqueueBatchResult(result: EnqueueDownloadBatchResult) {
        when (result) {
            is EnqueueDownloadBatchResult.Accepted -> {
                messageManager.showInfo("正在加入下载队列（${result.itemCount}项）")
            }
            EnqueueDownloadBatchResult.DirectoryUnavailable -> {
                messageManager.showError("下载目录不可用，请重新选择或重置为默认目录")
            }
            EnqueueDownloadBatchResult.TaskBlocked -> {
                messageManager.showInfo("相同作品已有下载任务正在处理")
            }
        }
    }

    private fun buildRemoteDownloadTaskKey(folderName: String, relativeBaseDir: String): String {
        return if (relativeBaseDir.isBlank()) {
            "album:$folderName"
        } else {
            "album:$folderName/$relativeBaseDir"
        }
    }

    override fun onCleared() {
        cancelActiveLoads()
        cancelCloudSyncSelection()
        super.onCleared()
    }
}

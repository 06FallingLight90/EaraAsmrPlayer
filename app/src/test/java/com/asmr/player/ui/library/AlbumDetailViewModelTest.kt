package com.asmr.player.ui.library

import android.content.Context
import android.os.Looper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.Room
import com.asmr.player.cache.AppCacheManager
import com.asmr.player.cache.CacheConfig
import com.asmr.player.cache.CacheStats
import com.asmr.player.cache.DiskCache
import com.asmr.player.cache.ImageCacheManager
import com.asmr.player.cache.ImageLoaderFacade
import com.asmr.player.cache.MemoryCache
import com.asmr.player.data.download.DownloadDirectoryCoordinator
import com.asmr.player.data.download.DownloadDestinationStore
import com.asmr.player.data.download.DownloadManager
import com.asmr.player.data.download.DownloadStorageGateway
import com.asmr.player.data.local.DeviceIdentityStore
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.TrackEntity
import com.asmr.player.data.lyrics.LyricsLoader
import com.asmr.player.data.lyrics.ManualLyricsSourceRepository
import com.asmr.player.data.remote.api.AsmrOneApi
import com.asmr.player.data.remote.api.AsmrOneAvailabilityApi
import com.asmr.player.data.remote.auth.DlsiteAuthStore
import com.asmr.player.data.remote.auth.EncryptedValue
import com.asmr.player.data.remote.auth.ValueCipher
import com.asmr.player.data.remote.crawler.AsmrOneCrawler
import com.asmr.player.data.remote.dlsite.DlsitePlayWorkClient
import com.asmr.player.data.remote.dlsite.DlsiteProductInfoClient
import com.asmr.player.data.remote.scraper.DLSiteScraper
import com.asmr.player.data.repository.LibraryReadRepository
import com.asmr.player.data.repository.LibraryWriteRepository
import com.asmr.player.data.repository.OnlineContentRepository
import com.asmr.player.data.settings.SettingsRepository
import com.asmr.player.listentogether.ListenTogetherRepository
import com.asmr.player.ui.library.albumdetail.AlbumDetailModel
import com.asmr.player.ui.library.albumdetail.AlbumDetailUiState
import com.asmr.player.util.MessageManager
import com.asmr.player.util.SyncCoordinator
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import androidx.lifecycle.SavedStateHandle
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * R3-C8 前置安全网：AlbumDetailViewModel 本体首个直测（此前 VM 无任何直测，
 * 行为仅由 repo 层测试间接覆盖）。用 Robolectric + in-memory Room + 真实协程
 * （不引入 coroutines-test），以"主线程泵循环 + 真实时间小超时"驱动
 * viewModelScope（Dispatchers.Main.immediate）的挂起恢复。
 *
 * 第一批钉住 loadAlbum 的请求键复用 / 强制重载 / 本地缺失移除（Removed）语义。
 * 第二批（C8-0b）钉住三路 ensure*Loaded 的时序 / 幂等 / 去重 / 终态语义：
 * ensureDlsiteLoaded（失败兜底置位 + 幂等 + 语言切换重置重装载）、
 * ensureAsmrOneLoaded（search→tracks 解析装载、未收录终态、attemptedRj 与
 * hasResolved 守卫去重、refresh 恢复）、ensureDlsitePlayLoaded（无 cookie 快速失败、
 * sign 失败移除 attemptKey 可重试、成功装载幂等、NotAvailable 终态不可重试）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [34])
class AlbumDetailViewModelTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var server: MockWebServer
    private lateinit var dlsiteAuthStore: DlsiteAuthStore
    private lateinit var vm: AlbumDetailViewModel

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        server = MockWebServer().apply { start() }
        dlsiteAuthStore = DlsiteAuthStore(context, LegacyPlainCipher())
        vm = buildViewModel()
    }

    @After
    fun tearDown() {
        server.close()
        db.close()
    }

    // ------------------------------------------------------------ harness

    private fun buildViewModel(): AlbumDetailViewModel {
        val settingsRepository = SettingsRepository(InMemoryPreferencesDataStore())
        val imageOkHttpClient = OkHttpClient()
        val libraryReadRepository = LibraryReadRepository(db, context)
        val libraryWriteRepository = LibraryWriteRepository(db, context)
        val crawler = AsmrOneCrawler(
            asmrOneApi = retrofitApi(server),
            asmr100Api = retrofitApi(server),
            asmr200Api = retrofitApi(server),
            asmr300Api = retrofitApi(server),
            settingsRepository = settingsRepository
        )
        val availabilityApi = AsmrOneAvailabilityApi(OkHttpClient(), Gson())
        val dlsiteScraper = DLSiteScraper(context)
        val productInfoClient = DlsiteProductInfoClient(redirectingOkHttpClient())
        val downloadStorage = DownloadStorageGateway(context)
        val downloadManager = DownloadManager(
            context = context,
            downloadDao = db.downloadDao(),
            directoryCoordinator = DownloadDirectoryCoordinator(
                database = db,
                destinationStore = DownloadDestinationStore(context),
                storage = downloadStorage
            ),
            storage = downloadStorage
        )
        val lyricsLoader = LyricsLoader(
            trackDao = db.trackDao(),
            albumDao = db.albumDao(),
            remoteSubtitleSourceDao = db.remoteSubtitleSourceDao(),
            manualLyricsSourceRepository = ManualLyricsSourceRepository(
                manualLyricsSourceDao = db.manualLyricsSourceDao(),
                context = context
            ),
            okHttpClient = imageOkHttpClient,
            context = context
        )
        val appCacheManager = AppCacheManager(
            context = context,
            settingsRepository = settingsRepository,
            imageCacheManager = ImageCacheManager(
                appContext = context,
                config = CacheConfig(cacheVersion = "test"),
                memoryCache = MemoryCache(16 * 1024 * 1024),
                diskCache = DiskCache(
                    directory = File(context.cacheDir, "images-test"),
                    maxSizeBytes = 16L * 1024 * 1024,
                    ttlMs = TimeUnit.HOURS.toMillis(1)
                ),
                loaderFacade = ImageLoaderFacade(
                    context = context,
                    okHttpClient = imageOkHttpClient,
                    imageDispatcher = Dispatchers.Default
                ),
                stats = CacheStats(),
                decodeDispatcher = Dispatchers.Default
            )
        )
        val onlineContentRepository = OnlineContentRepository(
            context = context,
            imageOkHttpClient = imageOkHttpClient,
            asmrOneCrawler = crawler,
            asmrOneAvailabilityApi = availabilityApi,
            dlsiteScraper = dlsiteScraper,
            dlsiteProductInfoClient = productInfoClient,
            libraryReadRepository = libraryReadRepository,
            libraryWriteRepository = libraryWriteRepository,
            dlsiteAuthStore = dlsiteAuthStore,
        )
        return AlbumDetailViewModel(
            savedStateHandle = SavedStateHandle(),
            libraryReadRepository = libraryReadRepository,
            libraryWriteRepository = libraryWriteRepository,
            asmrOneCrawler = crawler,
            asmrOneAvailabilityApi = availabilityApi,
            settingsRepository = settingsRepository,
            dlsiteScraper = dlsiteScraper,
            dlsiteProductInfoClient = productInfoClient,
            dlsitePlayWorkClient = DlsitePlayWorkClient(redirectingOkHttpClient(), context),
            downloadManager = downloadManager,
            lyricsLoader = lyricsLoader,
            syncCoordinator = SyncCoordinator(),
            listenTogetherRepository = ListenTogetherRepository(
                okHttpClient = OkHttpClient(),
                gson = Gson(),
                deviceIdentityStore = DeviceIdentityStore(context)
            ),
            appCacheManager = appCacheManager,
            onlineContentRepository = onlineContentRepository,
            dlsiteAuthStore = dlsiteAuthStore,
            messageManager = MessageManager(),
            context = context
        )
    }

    /** 驱动 viewModelScope（Main.immediate）在挂起恢复点之后继续执行。 */
    private fun pumpMain() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun awaitUiState(
        timeoutMs: Long = 5_000,
        pred: (AlbumDetailUiState) -> Boolean
    ): AlbumDetailUiState {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            pumpMain()
            val state = vm.uiState.value
            if (pred(state)) return state
            Thread.sleep(10)
        }
        throw AssertionError("Timed out waiting for uiState; last=${vm.uiState.value}")
    }

    private fun seedAvailableAlbum(id: Long, rj: String): File {
        val dir = File(context.cacheDir, "album_$id").apply { mkdirs() }
        val trackFile = File(dir, "01_track.wav").apply { writeBytes(ByteArray(4)) }
        runBlocking {
            val albumId = db.albumDao().insertAlbum(
                AlbumEntity(
                    id = id,
                    title = "Album $id",
                    path = dir.absolutePath,
                    workId = rj,
                    rjCode = rj
                )
            )
            db.trackDao().insertTrack(
                TrackEntity(albumId = albumId, title = "Track 1", path = trackFile.absolutePath)
            )
        }
        return dir
    }

    private fun successModel(state: AlbumDetailUiState): AlbumDetailModel {
        return (state as? AlbumDetailUiState.Success)?.model
            ?: throw AssertionError("Expected Success but was $state")
    }

    // ------------------------------------------------------------ tests

    @Test
    fun initialUiState_isSuccessShellWithoutLocalAlbum() {
        val model = successModel(vm.uiState.value)

        assertEquals("", model.baseRjCode)
        assertNull(model.localAlbum)
        assertFalse(model.isLoadingDlsite)
        assertFalse(model.isLoadingAsmrOne)
        assertFalse(vm.hasCachedAlbum(albumId = 1L, rjCode = null))
        assertFalse(vm.isInitialIntroSettled())
    }

    @Test
    fun initialIntroSettled_markedFlagIsReadable() {
        assertFalse(vm.isInitialIntroSettled())
        vm.markInitialIntroSettled()
        assertTrue(vm.isInitialIntroSettled())
    }

    @Test
    fun loadAlbum_localAlbumAvailable_completesWithLocalAlbumAndTracks() {
        seedAvailableAlbum(id = 1L, rj = "RJ00000001")

        vm.loadAlbum(albumId = 1L, rjCode = null)

        val model = successModel(awaitUiState { it is AlbumDetailUiState.Success && it.model.localAlbum != null })
        assertEquals(1L, model.localAlbum!!.id)
        assertTrue(model.localAlbum!!.tracks.isNotEmpty())
        // RJ 由本地专辑解析而来
        assertEquals("RJ00000001", model.rjCode)
        // 加载完成后请求键已完结，缓存判定为可复用
        assertTrue(vm.hasCachedAlbum(albumId = 1L, rjCode = null))
    }

    @Test
    fun loadAlbum_sameRequestKeyWithoutForce_reusesModelWithoutAvailabilityRecheck() {
        val dir = seedAvailableAlbum(id = 1L, rj = "RJ00000001")
        vm.loadAlbum(albumId = 1L, rjCode = null)
        successModel(awaitUiState { it is AlbumDetailUiState.Success && it.model.localAlbum != null })

        // 源文件消失后非强制重复加载：复用路径不做可用性复查，不得进入 Removed
        dir.deleteRecursively()
        vm.loadAlbum(albumId = 1L, rjCode = null, force = false)
        pumpMain()
        val reused = successModel(awaitUiState { it is AlbumDetailUiState.Success && it.model.localAlbum != null })
        assertNotNull(reused.localAlbum)

        // 强制重载走完整可用性检查 → 判定缺失 → 发出 Removed
        vm.loadAlbum(albumId = 1L, rjCode = null, force = true)
        val removed = awaitUiState { it is AlbumDetailUiState.Removed } as AlbumDetailUiState.Removed
        assertEquals(1L, removed.albumId)
    }

    @Test
    fun loadAlbum_missingLocalAlbumFromStart_emitsRemoved() {
        val missingDir = File(context.cacheDir, "album_missing_never_created")
        runBlocking {
            db.albumDao().insertAlbum(
                AlbumEntity(
                    id = 2L,
                    title = "Gone",
                    path = missingDir.absolutePath,
                    workId = "RJ00000002",
                    rjCode = "RJ00000002"
                )
            )
            db.trackDao().insertTrack(
                TrackEntity(albumId = 2L, title = "t", path = File(missingDir, "t.wav").absolutePath)
            )
        }

        vm.loadAlbum(albumId = 2L, rjCode = null)

        val removed = awaitUiState { it is AlbumDetailUiState.Removed } as AlbumDetailUiState.Removed
        assertEquals(2L, removed.albumId)
    }

    @Test
    fun loadAlbum_switchingAlbumKey_loadsFreshTargetAndInvalidatesPreviousKey() {
        seedAvailableAlbum(id = 1L, rj = "RJ00000001")
        seedAvailableAlbum(id = 2L, rj = "RJ00000002")

        vm.loadAlbum(albumId = 1L, rjCode = null)
        successModel(awaitUiState { it is AlbumDetailUiState.Success && it.model.localAlbum?.id == 1L })
        assertTrue(vm.hasCachedAlbum(albumId = 1L, rjCode = null))

        vm.loadAlbum(albumId = 2L, rjCode = null)
        val switched = successModel(awaitUiState { it is AlbumDetailUiState.Success && it.model.localAlbum?.id == 2L })
        assertEquals("RJ00000002", switched.rjCode)
        // 切换后上一请求键不再可复用，新请求键已完结
        assertFalse(vm.hasCachedAlbum(albumId = 1L, rjCode = null))
        assertTrue(vm.hasCachedAlbum(albumId = 2L, rjCode = null))
    }

    // ------------------------------------------------------------ C8-0b ensure* 时序/幂等/去重钉测

    // A 组：ensureDlsiteLoaded。两级网络边界：目标解析的 editions 预取走 productInfoClient
    // （拦截器重定向 → MockWebServer 可控）；页面抓取走 DLSiteScraper（Jsoup 直连，无注入口），
    // 依赖其"离线/404 → ignoreHttpErrors 文档为空 → 兜底置位"的确定性失败路径，不落 MockWebServer。

    @Test
    fun ensureDlsiteLoaded_blankEntry_marksSettledWithoutNetwork() {
        vm.ensureDlsiteLoaded()

        // 空入口（无 RJ）：解析目标为空 → 直接置位完成标记，全程无挂起、无网络请求
        val model = successModel(awaitUiState { it is AlbumDetailUiState.Success && it.model.hasLoadedInitialDlsiteContent })
        assertFalse(model.isLoadingDlsite)
        assertTrue(model.hasResolvedInitialDlsiteTarget)
        assertEquals("", model.rjCode)
        assertEquals("", model.dlsiteWorkno)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun ensureDlsiteLoaded_localAlbum_scraperUnavailable_marksSettledIdempotently() {
        seedAvailableAlbum(id = 1L, rj = "RJ00000001")
        vm.loadAlbum(albumId = 1L, rjCode = null)
        successModel(awaitUiState { it is AlbumDetailUiState.Success && it.model.localAlbum != null })

        // 目标解析先经 productInfoClient 预取语言版本（重定向到 MockWebServer；{} → 空列表），
        // 之后 scraper 走 Jsoup 直连（404/离线 → 文档为空 → 兜底路径）
        enqueueDlsitePlayEditions()
        vm.ensureDlsiteLoaded()

        val model = successModel(awaitUiState(timeoutMs = 60_000) { it is AlbumDetailUiState.Success && it.model.hasLoadedInitialDlsiteContent })
        assertFalse(model.isLoadingDlsite)
        assertTrue(model.hasResolvedInitialDlsiteTarget)
        assertEquals("RJ00000001", model.rjCode)
        assertEquals("RJ00000001", model.dlsiteWorkno)
        assertNull(model.dlsiteInfo)
        // 唯一请求 = editions 预取；scraper 不落 MockWebServer；推荐为空 → enrich 不发请求
        assertEquals(1, server.requestCount)

        // 幂等：hasLoadedInitialDlsiteContent 守卫挡住重入，不再发起装载
        vm.ensureDlsiteLoaded()
        pumpMain()
        val again = successModel(awaitUiState { it is AlbumDetailUiState.Success && it.model.hasLoadedInitialDlsiteContent })
        assertFalse(again.isLoadingDlsite)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun selectDlsiteLanguage_resetsDlsiteLifecycle_thenEnsureReloads() {
        seedAvailableAlbum(id = 1L, rj = "RJ00000001")
        vm.loadAlbum(albumId = 1L, rjCode = null)
        successModel(awaitUiState { it is AlbumDetailUiState.Success && it.model.localAlbum != null })
        enqueueDlsitePlayEditions()
        vm.ensureDlsiteLoaded()
        successModel(awaitUiState(timeoutMs = 60_000) { it is AlbumDetailUiState.Success && it.model.hasLoadedInitialDlsiteContent })

        // 切到不在 editions 的语言 → 重置 Dlsite 装载生命周期（内容/标记/缓存清空）；
        // 尾部同时重发 ensureDlsiteLoaded + ensureAsmrOneLoaded——入队两份 {}：
        // 无论两请求到达顺序如何，editions 收到 {} 得空列表、search 收到 {} 得空 works，均无害降级
        enqueueDlsitePlayEditions()
        enqueueDlsitePlayEditions()
        vm.selectDlsiteLanguage("CHT")

        // 重置后自动重新装载完成（dlsiteSelectedLang 保持用户选择，hasLoaded 翻回 true）
        val reloaded = successModel(
            awaitUiState(timeoutMs = 60_000) {
                it is AlbumDetailUiState.Success && it.model.hasLoadedInitialDlsiteContent &&
                    it.model.dlsiteSelectedLang == "CHT"
            }
        )
        assertTrue(reloaded.isDlsiteLanguageUserSelected)
        assertEquals("RJ00000001", reloaded.dlsiteWorkno)
        assertNull(reloaded.dlsiteInfo)
        assertFalse(reloaded.isLoadingDlsite)
    }

    // B 组：ensureAsmrOneLoaded。网络边界是 AsmrOneCrawler（Retrofit → MockWebServer，完全可控）；
    // loadAlbum 不自动触发 ensure*（selectDlsiteLanguage 尾部才触发），时序完全由测试编排。

    @Test
    fun ensureAsmrOneLoaded_resolvesViaSearch_thenLoadsTreeAndBlocksRetry() {
        seedAvailableAlbum(id = 1L, rj = "RJ00000001")
        vm.loadAlbum(albumId = 1L, rjCode = null)
        successModel(awaitUiState { it is AlbumDetailUiState.Success && it.model.localAlbum != null })

        // search 解析（RJ → workId 123）+ tracks 目录树，默认端点 mirror200
        enqueueAsmrOneSearch("RJ00000001")
        enqueueAsmrOneTracks()
        vm.ensureAsmrOneLoaded()

        val model = successModel(awaitUiState { it is AlbumDetailUiState.Success && it.model.asmrOneTree.isNotEmpty() })
        assertEquals("123", model.asmrOneWorkId)
        assertEquals(200, model.asmrOneSite)
        assertTrue(model.hasResolvedAsmrOneContent)
        assertFalse(model.isLoadingAsmrOne)
        assertEquals(2, server.requestCount)

        // 幂等：树已装载 + hasResolved 守卫挡住重入，不再发请求
        vm.ensureAsmrOneLoaded()
        pumpMain()
        successModel(awaitUiState { it is AlbumDetailUiState.Success && it.model.asmrOneTree.isNotEmpty() })
        assertEquals(2, server.requestCount)
    }

    @Test
    fun ensureAsmrOneLoaded_unlistedRj_finalizesResolvedAndBlocksRetryUntilRefresh() {
        seedAvailableAlbum(id = 1L, rj = "RJ00000001")
        vm.loadAlbum(albumId = 1L, rjCode = null)
        successModel(awaitUiState { it is AlbumDetailUiState.Success && it.model.localAlbum != null })

        // 未收录（search 空 works）→ 终态 hasResolved=true（attemptedRj 虽移除但守卫挡重试）。
        // 解析实际发两次 search：preferInitial 直解 + directRjs 兜底循环（throw 路径不消费 null 缓存）
        enqueueAsmrOneSearch()
        enqueueAsmrOneSearch()
        vm.ensureAsmrOneLoaded()

        val settled = successModel(
            awaitUiState { it is AlbumDetailUiState.Success && it.model.hasResolvedAsmrOneContent && !it.model.isLoadingAsmrOne }
        )
        assertTrue(settled.asmrOneTree.isEmpty())
        assertNull(settled.asmrOneWorkId)
        assertEquals(2, server.requestCount)

        vm.ensureAsmrOneLoaded()
        pumpMain()
        assertEquals(2, server.requestCount)

        // 刷新语义：forget 解析缓存 + 重置终态 → 可从未收录恢复到已装载
        enqueueAsmrOneSearch("RJ00000001")
        enqueueAsmrOneTracks()
        vm.refreshAsmrOneSection()

        val recovered = successModel(awaitUiState { it is AlbumDetailUiState.Success && it.model.asmrOneTree.isNotEmpty() })
        assertEquals("123", recovered.asmrOneWorkId)
        assertTrue(recovered.hasResolvedAsmrOneContent)
        assertEquals(4, server.requestCount)
    }

    // C 组：ensureDlsitePlayLoaded。DlsitePlayWorkClient/DlsiteProductInfoClient 的域名硬编码
    // 但接受 OkHttpClient 注入 → 拦截器重定向到 MockWebServer；请求序 = editions → sign → ziptree。
    // cookie 经 legacy 明文 pref 键注入（见 seedLegacyPlayCookie）。

    @Test
    fun ensureDlsitePlayLoaded_withoutPlayCookie_failsFastWithoutTreeRequests() {
        seedAvailableAlbum(id = 1L, rj = "RJ00000001")
        vm.loadAlbum(albumId = 1L, rjCode = null)
        successModel(awaitUiState { it is AlbumDetailUiState.Success && it.model.localAlbum != null })

        enqueueDlsitePlayEditions()
        vm.ensureDlsitePlayLoaded()

        // 无 play cookie：语言版本预取仍发生（editions），play 树请求在 cookie 校验处抛出快速失败；
        // 失败仍标记 hasResolvedDlsitePlayContent=true（防 UI 无限重试的真实语义）
        val settled = successModel(
            awaitUiState { it is AlbumDetailUiState.Success && it.model.hasResolvedDlsitePlayContent && !it.model.isLoadingDlsitePlay }
        )
        assertTrue(settled.dlsitePlayTree.isEmpty())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun ensureDlsitePlayLoaded_signFailure_retriesAfterAttemptKeyRemoved() {
        seedAvailableAlbum(id = 1L, rj = "RJ00000001")
        vm.loadAlbum(albumId = 1L, rjCode = null)
        successModel(awaitUiState { it is AlbumDetailUiState.Success && it.model.localAlbum != null })
        seedLegacyPlayCookie()

        // 第一轮：editions + sign 500 → lastError 非空且未见 NotAvailable → attemptKey 移除
        enqueueDlsitePlayEditions()
        enqueueDlsitePlaySign(success = false)
        vm.ensureDlsitePlayLoaded()

        val settled = successModel(
            awaitUiState { it is AlbumDetailUiState.Success && it.model.hasResolvedDlsitePlayContent && !it.model.isLoadingDlsitePlay }
        )
        assertTrue(settled.dlsitePlayTree.isEmpty())
        assertEquals(2, server.requestCount)

        // 第二轮：attemptKey 已移除 → 重试再次发起完整请求序列（对照 NotAvailable 终态的不可重试）
        enqueueDlsitePlayEditions()
        enqueueDlsitePlaySign(success = false)
        vm.ensureDlsitePlayLoaded()
        awaitRequestCount(4)
    }

    @Test
    fun ensureDlsitePlayLoaded_success_loadsTreeAndBlocksRetry() {
        seedAvailableAlbum(id = 1L, rj = "RJ00000001")
        vm.loadAlbum(albumId = 1L, rjCode = null)
        successModel(awaitUiState { it is AlbumDetailUiState.Success && it.model.localAlbum != null })
        seedLegacyPlayCookie()

        enqueueDlsitePlayEditions()
        enqueueDlsitePlaySign(success = true)
        enqueueDlsitePlayZiptree(withFile = true)
        vm.ensureDlsitePlayLoaded()

        val model = successModel(awaitUiState { it is AlbumDetailUiState.Success && it.model.dlsitePlayTree.isNotEmpty() })
        assertEquals("RJ00000001", model.dlsitePlayWorkno)
        assertTrue(model.hasResolvedDlsitePlayContent)
        assertFalse(model.isLoadingDlsitePlay)
        assertEquals(3, server.requestCount)

        // 幂等：树已装载守卫挡住重入
        vm.ensureDlsitePlayLoaded()
        pumpMain()
        successModel(awaitUiState { it is AlbumDetailUiState.Success && it.model.dlsitePlayTree.isNotEmpty() })
        assertEquals(3, server.requestCount)
    }

    @Test
    fun ensureDlsitePlayLoaded_notAvailable_terminalStateBlocksRetry() {
        seedAvailableAlbum(id = 1L, rj = "RJ00000001")
        vm.loadAlbum(albumId = 1L, rjCode = null)
        successModel(awaitUiState { it is AlbumDetailUiState.Success && it.model.localAlbum != null })
        seedLegacyPlayCookie()

        // sign 成功但 ziptree 空树 → NotAvailable（无异常）→ attemptKey 留存 → 终态不可重试
        enqueueDlsitePlayEditions()
        enqueueDlsitePlaySign(success = true)
        enqueueDlsitePlayZiptree(withFile = false)
        vm.ensureDlsitePlayLoaded()

        val settled = successModel(
            awaitUiState { it is AlbumDetailUiState.Success && it.model.hasResolvedDlsitePlayContent && !it.model.isLoadingDlsitePlay }
        )
        assertTrue(settled.dlsitePlayTree.isEmpty())
        assertEquals("", settled.dlsitePlayWorkno)
        assertEquals(3, server.requestCount)

        vm.ensureDlsitePlayLoaded()
        pumpMain()
        assertEquals(3, server.requestCount)
    }

    // ------------------------------------------------------------ helpers

    private inline fun <reified T> retrofitApi(server: MockWebServer): T {
        return Retrofit.Builder()
            .baseUrl(server.url("/api/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(T::class.java)
    }

    /**
     * 把任意硬编码域名的请求重定向到 MockWebServer（DlsitePlayWorkClient /
     * DlsiteProductInfoClient 的目标域名硬编码且仅接受 OkHttpClient 注入）。
     */
    private fun redirectingOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val redirectedUrl = request.url.newBuilder()
                    .scheme("http")
                    .host(server.hostName)
                    .port(server.port)
                    .build()
                chain.proceed(request.newBuilder().url(redirectedUrl).build())
            }
            .build()
    }

    /**
     * 经 legacy 明文 pref 键注入 play cookie（键名镜像 DlsiteAuthStore.KEY_COOKIE_PLAY，
     * 该常量为 private）。走 readCookie 的惰性迁移路径：迁移加密失败即跳过写入、原样返回明文，
     * 使 VM 侧（测试 cipher）与 DlsitePlayWorkClient 内部自建的 Keystore cipher 存储读到同一明文。
     */
    private fun seedLegacyPlayCookie(value: String = "test-cookie") {
        context.getSharedPreferences("dlsite_auth", Context.MODE_PRIVATE)
            .edit()
            .putString("cookie_play", value)
            .apply()
    }

    private fun enqueueAsmrOneSearch(vararg sourceIds: String) {
        val works = sourceIds.joinToString(",") { sourceId ->
            """{"id":123,"title":"テスト作品","source_id":"$sourceId","duration":3600,
                "mainCoverUrl":"https://c/1.jpg","dl_count":10,"price":880}"""
        }
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"works":[$works],"pagination":{"totalCount":${sourceIds.size},"pageSize":20,"page":1}}"""
            )
        )
    }

    private fun enqueueAsmrOneTracks() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """[{"title":"track1","mediaDownloadUrl":"https://x/1.mp3"}]"""
            )
        )
    }

    private fun enqueueDlsitePlayEditions() {
        // parseLanguageEditions 找不到 productId 键 → 返回空列表（不改变候选集）
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
    }

    private fun enqueueDlsitePlaySign(success: Boolean) {
        server.enqueue(
            if (success) {
                MockResponse().setResponseCode(200).setBody("""{"url":"http://zip/","params":{"k":"v"}}""")
            } else {
                MockResponse().setResponseCode(500).setBody("boom")
            }
        )
    }

    private fun enqueueDlsitePlayZiptree(withFile: Boolean) {
        val body = if (withFile) {
            """{"revision":"1","tree":[{"type":"file","hashname":"h1","name":"01 track.wav"}],
                "playfile":{"h1":{"type":"audio","audio":{"optimized":{"name":"opt1.m4a","duration":42.5}}}}}"""
        } else {
            """{"revision":"1","tree":[],"playfile":{}}"""
        }
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
    }

    private fun awaitRequestCount(target: Int, timeoutMs: Long = 5_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            pumpMain()
            if (server.requestCount >= target) return
            Thread.sleep(10)
        }
        throw AssertionError("Timed out waiting for requestCount>=$target; last=${server.requestCount}")
    }

    private class InMemoryPreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        private val updateMutex = Mutex()

        override val data: StateFlow<Preferences> = state

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences
        ): Preferences = updateMutex.withLock {
            transform(state.value).also { state.value = it }
        }
    }

    /**
     * 测试用 ValueCipher：AndroidKeyStore 在 Robolectric 不可用（KeyStoreException）。
     * encrypt 恒抛错以阻止 readCookie 的迁移写入（否则写入的"密文"会被
     * DlsitePlayWorkClient 内部 Keystore cipher 读成损坏并清空 cookie）；decrypt 原样透传。
     */
    private class LegacyPlainCipher : ValueCipher {
        override fun encrypt(plain: String): EncryptedValue =
            throw IllegalStateException("legacy-plaintext only cipher")

        override fun decrypt(value: EncryptedValue): String = value.cipherTextBase64
    }
}

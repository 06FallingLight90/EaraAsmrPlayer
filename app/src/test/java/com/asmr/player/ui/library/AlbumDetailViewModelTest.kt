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
 * 第一批钉住 loadAlbum 的请求键复用 / 强制重载 / 本地缺失移除（Removed）语义，
 * 为 C8 状态机重写提供行为锚点。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [34])
class AlbumDetailViewModelTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var server: MockWebServer
    private lateinit var vm: AlbumDetailViewModel

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        server = MockWebServer().apply { start() }
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
        val productInfoClient = DlsiteProductInfoClient(OkHttpClient())
        val dlsiteAuthStore = DlsiteAuthStore(context)
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
            dlsitePlayWorkClient = DlsitePlayWorkClient(OkHttpClient(), context),
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

    // ------------------------------------------------------------ helpers

    private inline fun <reified T> retrofitApi(server: MockWebServer): T {
        return Retrofit.Builder()
            .baseUrl(server.url("/api/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(T::class.java)
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
}

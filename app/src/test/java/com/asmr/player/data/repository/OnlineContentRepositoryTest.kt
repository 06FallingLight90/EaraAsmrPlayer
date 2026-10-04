package com.asmr.player.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.Room
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.TagSource
import com.asmr.player.data.remote.api.AsmrMirrorApi
import com.asmr.player.data.remote.api.AsmrOneApi
import com.asmr.player.data.remote.dlsite.DlsiteCloudSyncResolveResult
import com.asmr.player.data.remote.scraper.DLSiteScraper
import com.asmr.player.data.settings.SettingsRepository
import com.asmr.player.data.remote.crawler.AsmrOneCrawler
import com.asmr.player.domain.model.Album
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File

/**
 * R2-C4b-3 seam 测试：钉住 OnlineContentRepository 从 AlbumDetailViewModel 下沉的
 * 远端文件体积查询 / ASMR.ONE 解析与曲目缓存行为（TTL、按端点隔离、在途去重所依赖的缓存语义）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [34])
class OnlineContentRepositoryTest {
    private lateinit var mainServer: MockWebServer
    private lateinit var mirror100Server: MockWebServer
    private lateinit var mirror200Server: MockWebServer
    private lateinit var mirror300Server: MockWebServer
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var crawler: AsmrOneCrawler
    private lateinit var repo: OnlineContentRepository
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        mainServer = MockWebServer().apply { start() }
        mirror100Server = MockWebServer().apply { start() }
        mirror200Server = MockWebServer().apply { start() }
        mirror300Server = MockWebServer().apply { start() }
        settingsRepository = SettingsRepository(InMemoryPreferencesDataStore())
        crawler = AsmrOneCrawler(
            asmrOneApi = retrofitApi(mainServer),
            asmr100Api = retrofitApi(mirror100Server),
            asmr200Api = retrofitApi(mirror200Server),
            asmr300Api = retrofitApi(mirror300Server),
            settingsRepository = settingsRepository
        )
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repo = OnlineContentRepository(
            context = context,
            imageOkHttpClient = OkHttpClient(),
            asmrOneCrawler = crawler,
            asmrOneAvailabilityApi = com.asmr.player.data.remote.api.AsmrOneAvailabilityApi(
                OkHttpClient(),
                com.google.gson.Gson()
            ),
            dlsiteScraper = DLSiteScraper(context),
            dlsiteProductInfoClient = com.asmr.player.data.remote.dlsite.DlsiteProductInfoClient(OkHttpClient()),
            libraryReadRepository = LibraryReadRepository(db),
            libraryWriteRepository = LibraryWriteRepository(db),
            dlsiteAuthStore = com.asmr.player.data.remote.auth.DlsiteAuthStore(context),
        )
    }

    @After
    fun tearDown() {
        mainServer.close()
        mirror100Server.close()
        mirror200Server.close()
        mirror300Server.close()
        db.close()
    }

    @Test
    fun loadRemoteFileSize_readsContentLengthAndCachesResult() = runBlocking {
        val url = mirror200Server.url("/file.mp3").toString()
        mirror200Server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Length", "123"))

        val first = repo.loadRemoteFileSize(url)
        val second = repo.loadRemoteFileSize(url)

        assertEquals(123L, first)
        assertEquals(123L, second)
        // 第二次命中内存缓存，不再发请求
        assertEquals(1, mirror200Server.requestCount)
    }

    @Test
    fun loadRemoteFileSize_returnsNullForBlankUrl() = runBlocking {
        assertNull(repo.loadRemoteFileSize("   "))
    }

    @Test
    fun resolveAsmrOneWork_returnsNullForBlankWorkNo() = runBlocking {
        assertNull(repo.resolveAsmrOneWork("   "))
    }

    @Test
    fun resolveAsmrOneWork_resolvesViaSelectedMirrorAndCaches() = runBlocking {
        mirror200Server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"works":[{"id":123,"title":"テスト作品","source_id":"RJ01234567","duration":3600,
                    "mainCoverUrl":"https://c/1.jpg","dl_count":10,"price":880}],
                    "pagination":{"totalCount":1,"pageSize":20,"page":1}}"""
            )
        )

        val resolved = repo.resolveAsmrOneWork("rj01234567")
        val cached = repo.resolveAsmrOneWork("RJ01234567")

        assertEquals("123" to 200, resolved)
        assertEquals("123" to 200, cached)
        assertEquals(1, mirror200Server.requestCount)
        // 解析成功后详情缓存可被 peek（ensureAsmrOneLoaded 依赖）
        val details = repo.peekAsmrOneResolvedDetails("RJ01234567")
        assertEquals("テスト作品", details?.title)
    }

    @Test
    fun getAsmrOneTracksCached_cachesNonEmptyTreePerEndpoint() = runBlocking {
        mirror200Server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """[{"title":"track1","mediaDownloadUrl":"https://x/1.mp3"}]"""
            )
        )

        val first = repo.getAsmrOneTracksCached("1580085")
        val second = repo.getAsmrOneTracksCached("1580085")

        assertEquals(200, first.site)
        assertEquals(1, first.tree.size)
        assertEquals(1, second.tree.size)
        assertEquals(1, mirror200Server.requestCount)
    }

    @Test
    fun getAsmrOneTracksCached_returnsEmptyWithoutRequestForBlankWorkId() = runBlocking {
        val result = repo.getAsmrOneTracksCached("  ")

        assertEquals(0, result.tree.size)
        assertEquals(0, mirror200Server.requestCount)
    }

    @Test
    fun invalidateAsmrOneCaches_clearsResolutionAndTracksCaches() = runBlocking {
        mirror200Server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"works":[{"id":123,"title":"テスト作品","source_id":"RJ01234567","duration":3600,
                    "mainCoverUrl":"https://c/1.jpg","dl_count":10,"price":880}],
                    "pagination":{"totalCount":1,"pageSize":20,"page":1}}"""
            )
        )
        repo.resolveAsmrOneWork("RJ01234567")
        repo.invalidateAsmrOneCaches()
        mirror200Server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"works":[{"id":123,"title":"テスト作品","source_id":"RJ01234567","duration":3600,
                    "mainCoverUrl":"https://c/1.jpg","dl_count":10,"price":880}],
                    "pagination":{"totalCount":1,"pageSize":20,"page":1}}"""
            )
        )

        val resolved = repo.resolveAsmrOneWork("RJ01234567")

        assertEquals("123" to 200, resolved)
        assertEquals(2, mirror200Server.requestCount)
    }

    @Test
    fun forgetAsmrOneResolution_dropsEntryForRefreshedRj() = runBlocking {
        mirror200Server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"works":[{"id":123,"title":"テスト作品","source_id":"RJ01234567","duration":3600,
                    "mainCoverUrl":"https://c/1.jpg","dl_count":10,"price":880}],
                    "pagination":{"totalCount":1,"pageSize":20,"page":1}}"""
            )
        )
        repo.resolveAsmrOneWork("RJ01234567")

        repo.forgetAsmrOneResolution(keyRj = "RJ01234567", site = 200, workId = "123")

        assertNull(repo.peekAsmrOneResolvedDetails("RJ01234567"))
    }

    // ---------- 云同步 apply（R2-C4b-3b 下沉） ----------

    private fun seedAlbum(
        title: String,
        workId: String = "",
        coverPath: String = "/p/cover.jpg",
        circle: String = "",
        cv: String = "",
    ): Long = runBlocking {
        db.albumDao().insertAlbum(
            AlbumEntity(
                title = title,
                path = "/p/$title",
                workId = workId,
                coverPath = coverPath,
                circle = circle,
                cv = cv
            )
        )
    }

    private fun cloudSyncSuccess(
        workno: String = "RJ01234567",
        title: String = "ABC",
        circle: String = "社团",
        cv: String = "CV名",
        tags: List<String> = listOf("tag1", "tag2"),
    ) = DlsiteCloudSyncResolveResult.Success(
        workno = workno,
        locale = "ja_JP",
        details = Album(
            title = title,
            path = "",
            circle = circle,
            cv = cv,
            tags = tags,
            coverUrl = "https://example.com/cover.jpg",
            description = "简介"
        )
    )

    @Test
    fun applyManualCloudSyncSuccess_mergesMetadataAndPersists() = runBlocking {
        val albumId = seedAlbum(title = "旧标题超长版本 ABC", workId = "9")

        val resolved = repo.applyManualCloudSyncSuccess(
            entity = db.albumDao().getAlbumById(albumId)!!,
            updatedWorkId = "9",
            result = cloudSyncSuccess()
        )

        assertEquals("RJ01234567", resolved)
        val updated = db.albumDao().getAlbumById(albumId)!!
        // 旧标题包含新标题且更长 → 保留旧标题
        assertEquals("旧标题超长版本 ABC", updated.title)
        assertEquals("社团", updated.circle)
        assertEquals("CV名", updated.cv)
        assertEquals("tag1,tag2", updated.tags)
        assertEquals("https://example.com/cover.jpg", updated.coverUrl)
        assertEquals("简介", updated.description)
        // 旧 workId 非数字作品编号 → 保留
        assertEquals("9", updated.workId)
        assertEquals("RJ01234567", updated.rjCode)
        // 本地封面已存在 → 不触发网络补图（无 MockWebServer 响应，若发起请求会挂起失败）
        assertEquals("/p/cover.jpg", updated.coverPath)
    }

    @Test
    fun applyManualCloudSyncSuccess_usesNewTitleWhenOldNotContaining() = runBlocking {
        val albumId = seedAlbum(
            title = "完全不同",
            workId = "VJ012345",
            circle = "原社团",
            cv = "原CV"
        )

        repo.applyManualCloudSyncSuccess(
            entity = db.albumDao().getAlbumById(albumId)!!,
            updatedWorkId = "VJ012345",
            result = cloudSyncSuccess(title = "全新标题", circle = "", cv = "", tags = emptyList())
        )

        val updated = db.albumDao().getAlbumById(albumId)!!
        assertEquals("全新标题", updated.title)
        // 详情空字段回退原实体值
        assertEquals("原社团", updated.circle)
        assertEquals("原CV", updated.cv)
        // 详情 tags 为空 → 保留实体原 tags（空）
        assertEquals("", updated.tags)
    }

    @Test
    fun ensureAlbumCoverSaved_returnsTrueForExistingLocalFile() = runBlocking {
        val cover = File(context_cacheDir(), "existing_cover.jpg").apply { writeBytes(ByteArray(10)) }

        val ok = repo.ensureAlbumCoverSaved(albumId = 1L, coverPath = cover.absolutePath, coverUrl = "")

        assertTrue(ok)
    }

    private fun context_cacheDir(): File = RuntimeEnvironment.getApplication().cacheDir

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

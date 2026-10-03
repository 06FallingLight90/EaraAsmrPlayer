package com.asmr.player.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.asmr.player.data.remote.api.AsmrMirrorApi
import com.asmr.player.data.remote.api.AsmrOneApi
import com.asmr.player.data.settings.SettingsRepository
import com.asmr.player.data.remote.crawler.AsmrOneCrawler
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

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
        repo = OnlineContentRepository(
            context = RuntimeEnvironment.getApplication(),
            imageOkHttpClient = OkHttpClient(),
            asmrOneCrawler = crawler,
            asmrOneAvailabilityApi = com.asmr.player.data.remote.api.AsmrOneAvailabilityApi(
                OkHttpClient(),
                com.google.gson.Gson()
            ),
        )
    }

    @After
    fun tearDown() {
        mainServer.close()
        mirror100Server.close()
        mirror200Server.close()
        mirror300Server.close()
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

package com.asmr.player.data.remote.crawler

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.asmr.player.data.remote.api.AsmrMirrorApi
import com.asmr.player.data.remote.api.AsmrOneApi
import com.asmr.player.data.remote.api.AsmrOneEndpoint
import com.asmr.player.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AsmrOneCrawlerEndpointRoutingTest {
    private lateinit var mainServer: MockWebServer
    private lateinit var mirror100Server: MockWebServer
    private lateinit var mirror200Server: MockWebServer
    private lateinit var mirror300Server: MockWebServer
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var crawler: AsmrOneCrawler

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
    }

    @After
    fun tearDown() {
        mainServer.close()
        mirror100Server.close()
        mirror200Server.close()
        mirror300Server.close()
    }

    @Test
    fun tracksRequest_usesOnlySelectedMirror() = runBlocking {
        settingsRepository.setAsmrOneSite(AsmrOneEndpoint.MIRROR_100)
        mirror100Server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

        val result = crawler.getTracksWithTrace("1580085")

        assertEquals(AsmrOneEndpoint.MIRROR_100, result.site)
        assertEquals("/api/tracks/1580085?v=2", mirror100Server.takeRequest().path)
        assertEquals(0, mainServer.requestCount)
        assertEquals(0, mirror200Server.requestCount)
        assertEquals(0, mirror300Server.requestCount)
    }

    @Test
    fun searchRequest_onMainUsesMainSearchShapeWithoutMirrorParams() = runBlocking {
        settingsRepository.setAsmrOneSite(AsmrOneEndpoint.MAIN)
        mainServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"works":[{"id":9,"title":"Main Work","source_id":"RJ00000009","duration":60,
                    "mainCoverUrl":"https://c/m.jpg","dl_count":1,"price":100}],
                    "pagination":{"totalCount":7,"pageSize":20,"page":1}}"""
            )
        )

        val result = crawler.searchWithTrace("rain")

        assertEquals(AsmrOneEndpoint.MAIN, result.trace.site)
        assertEquals(1, result.response.works.size)
        assertEquals("Main Work", result.response.works[0].title)
        assertEquals(7, result.response.pagination.totalCount)
        assertEquals(
            "/api/search/rain?page=1&order=release&sort=desc&subtitle=0",
            mainServer.takeRequest().path
        )
        assertEquals(0, mirror100Server.requestCount)
        assertEquals(0, mirror200Server.requestCount)
        assertEquals(0, mirror300Server.requestCount)
    }

    @Test
    fun searchRequest_onMirrorUsesMirrorSearchShapeAndMapsResponse() = runBlocking {
        settingsRepository.setAsmrOneSite(AsmrOneEndpoint.MIRROR_200)
        mirror200Server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"works":[
                    {"id":0,"title":"filtered by non-positive id"},
                    {"id":123,"title":"テスト作品","source_id":"RJ01234567","duration":3600,
                     "mainCoverUrl":"https://c/1.jpg","dl_count":10,"price":880,"name":"named fallback",
                     "vas":[{"name":"CV"}],"tags":[{"name":"tag"}]}]}"""
            )
        )

        val result = crawler.searchWithTrace("rain")

        assertEquals(AsmrOneEndpoint.MIRROR_200, result.trace.site)
        assertEquals(1, result.response.works.size)
        val work = result.response.works[0]
        assertEquals(123, work.id)
        assertEquals("テスト作品", work.title)
        assertEquals("RJ01234567", work.source_id)
        assertEquals("named fallback", work.circle?.name)
        assertEquals(3600, work.duration)
        assertEquals(1, result.response.pagination.totalCount)
        assertEquals(
            "/api/search/rain?page=1&order=create_date&sort=desc&pageSize=20&subtitle=0&includeTranslationWorks=true",
            mirror200Server.takeRequest().path
        )
        assertEquals(0, mainServer.requestCount)
        assertEquals(0, mirror100Server.requestCount)
        assertEquals(0, mirror300Server.requestCount)
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

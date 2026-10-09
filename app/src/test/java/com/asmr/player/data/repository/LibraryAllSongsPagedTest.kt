package com.asmr.player.data.repository

import android.app.Application
import androidx.paging.PagingSource
import androidx.room.Room
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.TrackEntity
import com.asmr.player.domain.model.AllSongsQuery
import com.asmr.player.domain.model.AllSongsSort
import com.asmr.player.domain.model.AllSongsTrackRow
import kotlinx.coroutines.runBlocking
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

/**
 * T5：全部歌曲平铺分页出口（US-05）语义钉测——
 * 经 LibraryReadRepository.allSongsPaged 全链路（AllSongsQueryBuilder SQL → TrackDao @RawQuery）。
 * 覆盖：字段映射（displayTitle 回退链/artist/albumTitle）、LIKE 过滤命中面与不命中、三种排序、分页边界（空库/单页/多页）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class LibraryAllSongsPagedTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: LibraryReadRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repo = LibraryReadRepository(db, RuntimeEnvironment.getApplication())
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** 3 曲基线数据：t1 生效标题 "Btrack"（displayTitle 有值）/t2 回退 title "02" 且 artist 命中/t3 "Atrack"。 */
    private suspend fun seedThreeTracks() {
        val albumA = db.albumDao().insertAlbum(AlbumEntity(title = "Alpha Work", path = "/music/a"))
        val albumB = db.albumDao().insertAlbum(AlbumEntity(title = "Beta Work", path = "/music/b"))
        db.trackDao().insertTrack(
            TrackEntity(albumId = albumA, title = "01", displayTitle = "Btrack", path = "/music/a/01.mp3")
        )
        db.trackDao().insertTrack(
            TrackEntity(albumId = albumA, title = "02", displayTitle = "", path = "/music/a/02.mp3", artist = "ZUN")
        )
        db.trackDao().insertTrack(
            TrackEntity(albumId = albumB, title = "x", displayTitle = "Atrack", path = "/music/b/a1.mp3")
        )
    }

    private suspend fun pageOf(
        spec: AllSongsQuery,
        params: PagingSource.LoadParams<Int>
    ): PagingSource.LoadResult.Page<Int, AllSongsTrackRow> {
        val result = repo.allSongsPaged(spec).load(params)
        assertTrue("expected LoadResult.Page but was $result", result is PagingSource.LoadResult.Page)
        @Suppress("UNCHECKED_CAST")
        return result as PagingSource.LoadResult.Page<Int, AllSongsTrackRow>
    }

    private suspend fun rowsOf(spec: AllSongsQuery, loadSize: Int = 50): List<AllSongsTrackRow> {
        return pageOf(spec, PagingSource.LoadParams.Refresh(key = null, loadSize = loadSize, placeholdersEnabled = false)).data
    }

    @Test
    fun emptyDatabase_returnsEmptyPageWithoutNextKey() = runBlocking {
        val page = pageOf(AllSongsQuery(), PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false))
        assertEquals(0, page.data.size)
        assertNull(page.nextKey)
    }

    @Test
    fun rows_mapProjectionFields() = runBlocking {
        seedThreeTracks()
        val rows = rowsOf(AllSongsQuery())
        val byId = rows.associateBy { it.trackId }

        // displayTitle 回退链：有值用 displayTitle，空串回退 title
        assertEquals("Btrack", byId.getValue(1L).trackTitle)
        assertEquals("02", byId.getValue(2L).trackTitle)
        assertEquals("Atrack", byId.getValue(3L).trackTitle)
        // artist 直出（Room 32 新列）
        assertEquals("ZUN", byId.getValue(2L).artist)
        assertNull(byId.getValue(1L).artist)
        // 专辑上下文
        assertEquals("Alpha Work", byId.getValue(1L).albumTitle)
        assertEquals("Beta Work", byId.getValue(3L).albumTitle)
        assertEquals("/music/a/01.mp3", byId.getValue(1L).trackPath)
    }

    @Test
    fun filter_hitByTitleOrPathOrArtist_missReturnsEmpty() = runBlocking {
        seedThreeTracks()

        // "track" 命中生效标题（Btrack/Atrack，ASCII LIKE 大小写不敏感），不命中 t2
        val byTitle = rowsOf(AllSongsQuery(textFilter = "track"))
        assertEquals(listOf(1L, 3L), byTitle.map { it.trackId }.sorted())

        // "zun" 命中 artist 列
        val byArtist = rowsOf(AllSongsQuery(textFilter = "zun"))
        assertEquals(listOf(2L), byArtist.map { it.trackId })

        // 路径段命中文件名键（mediaId = track.path）
        val byPath = rowsOf(AllSongsQuery(textFilter = "/music/b"))
        assertEquals(listOf(3L), byPath.map { it.trackId })

        // 不命中 → 空页
        val miss = pageOf(AllSongsQuery(textFilter = "no-such-term"), PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false))
        assertEquals(0, miss.data.size)
        assertNull(miss.nextKey)
    }

    @Test
    fun filter_blankTextBehavesAsNoFilter() = runBlocking {
        seedThreeTracks()
        assertEquals(3, rowsOf(AllSongsQuery(textFilter = "   ")).size)
        assertEquals(3, rowsOf(AllSongsQuery(textFilter = null)).size)
    }

    @Test
    fun sort_titleAsc_usesEffectiveTitleWithNumericFirst() = runBlocking {
        seedThreeTracks()
        // NOCASE ASCII 序：数字 "02" < "Atrack" < "Btrack"
        val rows = rowsOf(AllSongsQuery(sort = AllSongsSort.TitleAsc))
        assertEquals(listOf(2L, 3L, 1L), rows.map { it.trackId })
    }

    @Test
    fun sort_fileNameAsc_ordersByPath() = runBlocking {
        seedThreeTracks()
        // /music/a/01.mp3 < /music/a/02.mp3 < /music/b/a1.mp3
        val rows = rowsOf(AllSongsQuery(sort = AllSongsSort.FileNameAsc))
        assertEquals(listOf(1L, 2L, 3L), rows.map { it.trackId })
    }

    @Test
    fun sort_addedDesc_isInsertionOrderReversed() = runBlocking {
        seedThreeTracks()
        val rows = rowsOf(AllSongsQuery(sort = AllSongsSort.AddedDesc))
        assertEquals(listOf(3L, 2L, 1L), rows.map { it.trackId })
    }

    @Test
    fun paging_singlePageCoversAll_noNextKey() = runBlocking {
        seedThreeTracks()
        val page = pageOf(AllSongsQuery(), PagingSource.LoadParams.Refresh(key = null, loadSize = 10, placeholdersEnabled = false))
        assertEquals(3, page.data.size)
        assertNull(page.nextKey)
    }

    @Test
    fun paging_multiPage_walksAllThenStops() = runBlocking {
        // 7 曲单专辑，loadSize=3 → 3/3/1 三页
        val albumId = db.albumDao().insertAlbum(AlbumEntity(title = "Big", path = "/big"))
        db.trackDao().insertTracks(
            (1..7).map { i ->
                TrackEntity(albumId = albumId, title = "t$i", path = "/big/%02d.mp3".format(i))
            }
        )
        val spec = AllSongsQuery(sort = AllSongsSort.FileNameAsc)

        val p1 = pageOf(spec, PagingSource.LoadParams.Refresh(key = null, loadSize = 3, placeholdersEnabled = false))
        assertEquals(3, p1.data.size)
        assertEquals("/big/01.mp3", p1.data.first().trackPath)

        val p2 = pageOf(spec, PagingSource.LoadParams.Append(key = p1.nextKey!!, loadSize = 3, placeholdersEnabled = false))
        assertEquals(3, p2.data.size)

        val p3 = pageOf(spec, PagingSource.LoadParams.Append(key = p2.nextKey!!, loadSize = 3, placeholdersEnabled = false))
        assertEquals(1, p3.data.size)
        assertEquals("/big/07.mp3", p3.data.single().trackPath)
        assertNull(p3.nextKey)

        // 无重叠、全覆盖
        val all = (p1.data + p2.data + p3.data).map { it.trackPath }
        assertEquals((1..7).map { "/big/%02d.mp3".format(it) }, all)
    }

    @Test
    fun paging_filteredMultiPage_respectsFilterAcrossPages() = runBlocking {
        val albumId = db.albumDao().insertAlbum(AlbumEntity(title = "Mix", path = "/mix"))
        // 4 命中 + 3 不命中；过滤后仅 2 页（3+1）
        val tracks = (1..7).map { i ->
            if (i <= 4) TrackEntity(albumId = albumId, title = "t$i", displayTitle = "hit$i", path = "/mix/%02d.mp3".format(i))
            else TrackEntity(albumId = albumId, title = "t$i", path = "/mix/%02d.mp3".format(i))
        }
        db.trackDao().insertTracks(tracks)
        val spec = AllSongsQuery(textFilter = "hit", sort = AllSongsSort.TitleAsc)

        val p1 = pageOf(spec, PagingSource.LoadParams.Refresh(key = null, loadSize = 3, placeholdersEnabled = false))
        assertEquals(listOf("hit1", "hit2", "hit3"), p1.data.map { it.trackTitle })

        val p2 = pageOf(spec, PagingSource.LoadParams.Append(key = p1.nextKey!!, loadSize = 3, placeholdersEnabled = false))
        assertEquals(listOf("hit4"), p2.data.map { it.trackTitle })
        assertNull(p2.nextKey)
    }
}
package com.asmr.player.data.repository

import android.app.Application
import androidx.room.Room
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.TagEntity
import com.asmr.player.data.local.db.entities.TrackEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * R2-B4c seam 测试：验证 LibraryReadRepository 透传查询行为
 * （流直出/PagingSource factory 参数化/一次性查询，契约见 docs/behavior-notes/library-delete-family.md 附录六）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class LibraryReadRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: LibraryReadRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repo = LibraryReadRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun oneShotQueries_passThroughDaoResults() = runBlocking {
        val albumId = db.albumDao().insertAlbum(AlbumEntity(title = "A", path = "/p/A"))
        val trackId = db.trackDao().insertTrack(TrackEntity(albumId = albumId, title = "t", path = "/p/A/01.mp3"))
        db.trackDao().insertTrack(TrackEntity(albumId = albumId, title = "t2", path = "/p/A/02.mp3"))

        assertEquals("/p/A", repo.getAlbumById(albumId)?.path)
        assertNull(repo.getAlbumById(999L))
        assertEquals(1, repo.getAllAlbumsOnce().size)
        assertEquals(2, repo.getTracksForAlbumOnce(albumId).size)
        assertEquals(listOf(trackId), repo.getTracksByIdsOnce(listOf(trackId)).map { it.id })
        assertEquals("t", repo.getTrackByIdOnce(trackId)?.title)
        assertEquals(0L, repo.countTags())
    }

    @Test
    fun observeFlows_emitCurrentTableState() = runBlocking {
        val albumId = db.albumDao().insertAlbum(
            AlbumEntity(title = "A", path = "/p/A", circle = "Circle X", cv = "CV Y")
        )
        assertNotNull(albumId)

        assertEquals(listOf("Circle X"), repo.observeDistinctCircles().first())
        assertEquals(listOf("CV Y"), repo.observeDistinctCvs().first())
        assertTrue(repo.observeTagsWithCounts(0).first().isEmpty()) // 空 tags 表直出空列表
    }

    @Test
    fun getExistingTagIds_returnsOnlyPersistedIds() = runBlocking {
        val keptId = db.tagDao().insertTags(listOf(TagEntity(name = "k", nameNormalized = "k"))).first()

        assertEquals(listOf(keptId), repo.getExistingTagIds(listOf(keptId, 4242L)))
    }

    private fun assertTrue(actual: Boolean) {
        org.junit.Assert.assertTrue(actual)
    }
}

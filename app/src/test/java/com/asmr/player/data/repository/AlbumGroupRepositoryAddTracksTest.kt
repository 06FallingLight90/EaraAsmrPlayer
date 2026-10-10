package com.asmr.player.data.repository

import androidx.room.Room
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.AlbumGroupEntity
import com.asmr.player.data.local.db.entities.AlbumGroupItemEntity
import com.asmr.player.data.local.db.entities.TrackEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * T8/US-04：AlbumGroupRepository.addTracksToGroup（track 粒度批量挂载）的幂等去重钉测。
 * 合集写入约定：mediaId=trackPath（album_group_items 主键语义），重复/已存在/空白静默跳过，
 * itemOrder 接组内全局最大序号连续递增（与 addAlbumToGroup 的专辑内续序并存）。
 */
@RunWith(RobolectricTestRunner::class)
class AlbumGroupRepositoryAddTracksTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: AlbumGroupRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        repository = AlbumGroupRepository(
            database = db,
            groupDao = db.albumGroupDao(),
            groupItemDao = db.albumGroupItemDao(),
            trackDao = db.trackDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun addTracksToGroup_skipsExistingBlanksAndInRequestDuplicates_continuesGroupOrder() = runBlocking {
        val albumA = insertAlbum("Album A", "/albums/a")
        insertTrack(albumA, "A1", "/albums/a/1.mp3")
        insertTrack(albumA, "A2", "/albums/a/2.mp3")
        insertTrack(albumA, "A3", "/albums/a/3.mp3")
        val groupId = db.albumGroupDao().insertGroup(AlbumGroupEntity(name = "分组"))
        // 存量两条（itemOrder 0/3，模拟既有手动序），组内最大序号 = 3
        db.albumGroupItemDao().upsertItems(
            listOf(
                AlbumGroupItemEntity(groupId = groupId, mediaId = "/albums/a/1.mp3", itemOrder = 0),
                AlbumGroupItemEntity(groupId = groupId, mediaId = "/albums/a/2.mp3", itemOrder = 3)
            )
        )

        val added = repository.addTracksToGroup(
            groupId,
            listOf("/albums/a/3.mp3", "  ", "/albums/a/1.mp3", "/albums/a/3.mp3")
        )

        assertEquals(1, added)
        val items = db.albumGroupItemDao().getAlbumItemsOnce(groupId, albumA)
        assertEquals(
            listOf("/albums/a/1.mp3", "/albums/a/2.mp3", "/albums/a/3.mp3"),
            items.map { it.mediaId }
        )
        assertEquals(listOf(0, 3, 4), items.map { it.itemOrder }.sorted())
    }

    @Test
    fun addTracksToGroup_isIdempotentOnRepeat() = runBlocking {
        val albumA = insertAlbum("Album A", "/albums/a")
        insertTrack(albumA, "A1", "/albums/a/1.mp3")
        insertTrack(albumA, "A2", "/albums/a/2.mp3")
        val groupId = db.albumGroupDao().insertGroup(AlbumGroupEntity(name = "分组"))

        assertEquals(2, repository.addTracksToGroup(groupId, listOf("/albums/a/1.mp3", "/albums/a/2.mp3")))
        // 重复请求：全部已在组内，零新增、既有 itemOrder 不被覆写
        assertEquals(0, repository.addTracksToGroup(groupId, listOf("/albums/a/1.mp3", "/albums/a/2.mp3")))

        val items = db.albumGroupItemDao().getAlbumItemsOnce(groupId, albumA)
        assertEquals(2, items.size)
        assertEquals(listOf(0, 1), items.map { it.itemOrder }.sorted())
    }

    @Test
    fun addTracksToGroup_invalidGroupIdOrEmptyInputAddsNothing() = runBlocking {
        val albumA = insertAlbum("Album A", "/albums/a")
        insertTrack(albumA, "A1", "/albums/a/1.mp3")
        val groupId = db.albumGroupDao().insertGroup(AlbumGroupEntity(name = "分组"))

        assertEquals(0, repository.addTracksToGroup(-1L, listOf("/albums/a/1.mp3")))
        assertEquals(0, repository.addTracksToGroup(groupId, emptyList()))
        assertEquals(0, repository.addTracksToGroup(groupId, listOf("  ")))

        assertEquals(
            emptyList<String>(),
            db.albumGroupItemDao().getAlbumItemsOnce(groupId, albumA).map { it.mediaId }
        )
    }

    private suspend fun insertAlbum(title: String, path: String): Long {
        return db.albumDao().insertAlbum(
            AlbumEntity(
                title = title,
                path = path,
                coverUrl = "",
                coverPath = "",
                coverThumbPath = ""
            )
        )
    }

    private suspend fun insertTrack(albumId: Long, title: String, path: String) {
        db.trackDao().insertTrack(
            TrackEntity(
                albumId = albumId,
                title = title,
                path = path,
                duration = 12.0
            )
        )
    }
}

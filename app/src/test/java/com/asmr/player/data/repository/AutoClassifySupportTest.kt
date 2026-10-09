package com.asmr.player.data.repository

import androidx.room.Room
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.AlbumGroupEntity
import com.asmr.player.data.local.db.entities.TrackEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * T7 钉测试：合集三类 seed + 来源自动归类（行为契约见 docs/behavior-notes/collection-auto-classify.md）。
 */
@RunWith(RobolectricTestRunner::class)
class AutoClassifySupportTest {
    private lateinit var db: AppDatabase
    private lateinit var support: AutoClassifySupport

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        support = AutoClassifySupport(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun classify_mapsThreeSourceKinds() {
        assertEquals(
            AutoClassifySupport.DefaultGroupKind.AUDIO_WORKS,
            support.classify(AlbumEntity.SOURCE_DLSITE_DOWNLOAD)
        )
        assertEquals(
            AutoClassifySupport.DefaultGroupKind.SONGS,
            support.classify(AlbumEntity.SOURCE_LOCAL_SCAN)
        )
        assertEquals(AutoClassifySupport.DefaultGroupKind.OTHER, support.classify(null))
        assertEquals(AutoClassifySupport.DefaultGroupKind.OTHER, support.classify(""))
        assertEquals(AutoClassifySupport.DefaultGroupKind.OTHER, support.classify("unknown_source"))
    }

    @Test
    fun ensureDefaultGroups_seedsThreeFixedNamesIdempotently() = runBlocking {
        db.albumGroupDao().insertGroup(AlbumGroupEntity(name = "我的自建"))

        support.ensureDefaultGroups()
        val firstRunIds = defaultGroupNamesToIds()
        support.ensureDefaultGroups()

        assertEquals(setOf("歌曲", "音声", "其它音频"), firstRunIds.keys)
        assertEquals(firstRunIds, defaultGroupNamesToIds())
        // 自建合集不受影响；两次 seed 后总组数不增长。
        assertEquals(4, db.albumGroupDao().getAllGroupsOnce().size)
        assertTrue(db.albumGroupDao().getAllGroupsOnce().any { it.name == "我的自建" })
    }

    @Test
    fun ensureDefaultGroups_backfillsStockTracksBySourceWithDedup() = runBlocking {
        val dlAlbum = insertAlbum("DL 作品", "/dl/a", AlbumEntity.SOURCE_DLSITE_DOWNLOAD)
        val scanAlbum = insertAlbum("扫描专辑", "/scan/b", AlbumEntity.SOURCE_LOCAL_SCAN)
        val noneAlbum = insertAlbum("在线保存", "web://rj/RJ1", source = null)
        val blankAlbum = insertAlbum("空白来源", "/blank/d", source = "")
        insertTrack(dlAlbum, "/dl/a/2.mp3")
        insertTrack(dlAlbum, "/dl/a/1.mp3")
        insertTrack(scanAlbum, "/scan/b/1.mp3")
        insertTrack(noneAlbum, "https://example.com/t1.mp3")
        insertTrack(blankAlbum, "/blank/d/1.mp3")
        // 同专辑重复 path 两行：回填按 mediaId 去重只落一条。
        insertTrack(scanAlbum, "/scan/b/dup.mp3")
        insertTrack(scanAlbum, "/scan/b/dup.mp3")

        support.ensureDefaultGroups()

        assertEquals(listOf("/dl/a/1.mp3", "/dl/a/2.mp3"), itemsOf("音声"))
        assertEquals(listOf("/scan/b/1.mp3", "/scan/b/dup.mp3"), itemsOf("歌曲"))
        assertEquals(
            listOf("/blank/d/1.mp3", "https://example.com/t1.mp3"),
            itemsOf("其它音频")
        )
    }

    @Test
    fun ensureDefaultGroups_secondRunDoesNotBackfillNewStock() = runBlocking {
        val scanAlbum = insertAlbum("扫描专辑", "/scan/b", AlbumEntity.SOURCE_LOCAL_SCAN)
        insertTrack(scanAlbum, "/scan/b/1.mp3")
        support.ensureDefaultGroups()

        // 首建后新出现但未走扫描管线的存量轨：二次 seed 不回填（回填仅首建时一次）。
        insertTrack(scanAlbum, "/scan/b/late.mp3")
        support.ensureDefaultGroups()

        assertEquals(listOf("/scan/b/1.mp3"), itemsOf("歌曲"))
    }

    @Test
    fun attachTracks_isIdempotentAndPreservesExistingOrder() = runBlocking {
        val dlAlbum = insertAlbum("DL 作品", "/dl/a", AlbumEntity.SOURCE_DLSITE_DOWNLOAD)
        insertTrack(dlAlbum, "/dl/a/1.mp3")
        insertTrack(dlAlbum, "/dl/a/2.mp3")
        insertTrack(dlAlbum, "/dl/a/3.mp3")
        support.ensureDefaultGroups()
        val audioWorksId = defaultGroupNamesToIds().getValue("音声")

        // 手动抬高 itemOrder 模拟用户整理过的组。
        val manual = db.albumGroupItemDao().getAlbumItemsOnce(audioWorksId, dlAlbum)
        db.albumGroupItemDao().upsertItems(manual.mapIndexed { index, item -> item.copy(itemOrder = 100 + index) })

        support.attachTracksToDefaultGroups(
            dlAlbum, listOf("/dl/a/1.mp3", "/dl/a/2.mp3", "/dl/a/3.mp3"), AlbumEntity.SOURCE_DLSITE_DOWNLOAD
        )
        support.attachTracksToDefaultGroups(dlAlbum, listOf("/dl/a/1.mp3"), AlbumEntity.SOURCE_DLSITE_DOWNLOAD)
        support.attachTrackToDefaultGroup("/dl/a/2.mp3", AlbumEntity.SOURCE_DLSITE_DOWNLOAD)

        val items = db.albumGroupItemDao().getAlbumItemsOnce(audioWorksId, dlAlbum)
        assertEquals(listOf("/dl/a/1.mp3", "/dl/a/2.mp3", "/dl/a/3.mp3"), items.map { it.mediaId })
        // 既有条目 itemOrder 不被覆写；新条目接在同专辑 max+1 之后。
        assertEquals(listOf(100, 101, 102), items.map { it.itemOrder })
        // dlsite 来源不落其它默认合集。
        assertTrue(db.albumGroupItemDao().getGroupMediaIdsOnce(defaultGroupNamesToIds().getValue("歌曲")).isEmpty())
    }

    @Test
    fun attachTracks_skipsWhenDefaultGroupDeleted() = runBlocking {
        val scanAlbum = insertAlbum("扫描专辑", "/scan/b", AlbumEntity.SOURCE_LOCAL_SCAN)
        insertTrack(scanAlbum, "/scan/b/1.mp3")
        support.ensureDefaultGroups()
        db.albumGroupDao().deleteGroup(db.albumGroupDao().getGroupByNameOnce("歌曲")!!)

        support.attachTracksToDefaultGroups(scanAlbum, listOf("/scan/b/1.mp3"), AlbumEntity.SOURCE_LOCAL_SCAN)

        // 默认合集被用户删除后增量挂载静默跳过（不即时重建，重建归 seed）。
        assertEquals(null, db.albumGroupDao().getGroupByNameOnce("歌曲"))
    }

    @Test
    fun scanSync_attachesOnlyInsertedTracks_andDoesNotReaddRemovedItems() = runBlocking {
        val scanWrite = LibraryScanWriteSupport(
            database = db,
            tagWrite = LibraryTagWriteSupport(db),
            deleteWrite = LibraryDeleteWriteSupport(db),
            scanMetadata = LibraryScanMetadataSupport(null),
            autoClassify = support,
        )
        val scanAlbum = insertAlbum("扫描专辑", "/scan/b", AlbumEntity.SOURCE_LOCAL_SCAN)
        val existingId = db.trackDao().insertTrack(
            TrackEntity(albumId = scanAlbum, title = "E1", path = "/scan/b/1.mp3", duration = 1.0)
        )
        support.ensureDefaultGroups()
        assertEquals(listOf("/scan/b/1.mp3"), itemsOf("歌曲"))

        // 首扫：新插轨 E2 入组（E1 走 update 分支不重复挂）。
        val secondScanUpdate = db.trackDao().getTrackByPathOnce("/scan/b/1.mp3")!!.copy(title = "E1-renamed")
        val newTrack = TrackEntity(albumId = scanAlbum, title = "E2", path = "/scan/b/2.mp3", duration = 0.0)
        scanWrite.syncScannedLocalAlbumTracks(
            tracksToUpdate = listOf(secondScanUpdate),
            tracksToInsert = listOf(newTrack),
            subtitleEntriesByAudioPath = emptyMap(),
            subtitleEntriesByExistingTrackId = emptyMap(),
            removedIds = emptyList(),
        )
        assertEquals(listOf("/scan/b/1.mp3", "/scan/b/2.mp3"), itemsOf("歌曲"))

        // 用户移除 E1 条目后，无新插轨的二扫（update 分支）不回加。
        db.albumGroupItemDao().deleteItem(defaultGroupNamesToIds().getValue("歌曲"), "/scan/b/1.mp3")
        scanWrite.syncScannedLocalAlbumTracks(
            tracksToUpdate = listOf(secondScanUpdate),
            tracksToInsert = emptyList(),
            subtitleEntriesByAudioPath = emptyMap(),
            subtitleEntriesByExistingTrackId = emptyMap(),
            removedIds = emptyList(),
        )
        assertEquals(listOf("/scan/b/2.mp3"), itemsOf("歌曲"))
        // 轨仍在库（移除的是合集条目而非轨）。
        assertEquals(existingId, db.trackDao().getTrackByPathOnce("/scan/b/1.mp3")?.id)
    }

    @Test
    fun scanSync_resolvesSourceFromAlbumRow_rjMergedDownloadAlbumStaysAudioWorks() = runBlocking {
        val scanWrite = LibraryScanWriteSupport(
            database = db,
            tagWrite = LibraryTagWriteSupport(db),
            deleteWrite = LibraryDeleteWriteSupport(db),
            scanMetadata = LibraryScanMetadataSupport(null),
            autoClassify = support,
        )
        // RJ 合并场景：下载专辑（source 保留 dlsite_download）经本地扫描管线补轨——新插轨按专辑最终 source 归音声。
        val dlAlbum = insertAlbum("DL 作品", "/dl/a", AlbumEntity.SOURCE_DLSITE_DOWNLOAD)
        support.ensureDefaultGroups()
        val newTrack = TrackEntity(albumId = dlAlbum, title = "T1", path = "/dl/a/1.mp3", duration = 0.0)
        scanWrite.syncScannedLocalAlbumTracks(
            tracksToUpdate = emptyList(),
            tracksToInsert = listOf(newTrack),
            subtitleEntriesByAudioPath = emptyMap(),
            subtitleEntriesByExistingTrackId = emptyMap(),
            removedIds = emptyList(),
        )

        assertEquals(listOf("/dl/a/1.mp3"), itemsOf("音声"))
        assertTrue(itemsOf("歌曲").isEmpty())
    }

    private suspend fun defaultGroupNamesToIds(): Map<String, Long> {
        return db.albumGroupDao().getAllGroupsOnce()
            .filter { it.name in listOf("歌曲", "音声", "其它音频") }
            .associate { it.name to it.id }
    }

    private suspend fun itemsOf(groupName: String): List<String> {
        val groupId = defaultGroupNamesToIds()[groupName] ?: return emptyList()
        return db.albumGroupItemDao().getGroupMediaIdsOnce(groupId).sorted()
    }

    private suspend fun insertAlbum(title: String, path: String, source: String?): Long {
        return db.albumDao().insertAlbum(
            AlbumEntity(
                title = title,
                path = path,
                source = source
            )
        )
    }

    private suspend fun insertTrack(albumId: Long, path: String) {
        db.trackDao().insertTrack(
            TrackEntity(
                albumId = albumId,
                title = path.substringAfterLast('/'),
                path = path,
                duration = 12.0
            )
        )
    }
}

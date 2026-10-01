package com.asmr.player.data.repository

import android.app.Application
import androidx.room.Room
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.AlbumTagEntity
import com.asmr.player.data.local.db.entities.LocalTreeCacheEntity
import com.asmr.player.data.local.db.entities.OnlineSavedResourceEntity
import com.asmr.player.data.local.db.entities.RemoteSubtitleSourceEntity
import com.asmr.player.data.local.db.entities.SubtitleEntity
import com.asmr.player.data.local.db.entities.TagEntity
import com.asmr.player.data.local.db.entities.TagSource
import com.asmr.player.data.local.db.entities.TrackEntity
import com.asmr.player.data.local.db.entities.TrackTagEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * R2-B4a seam 测试：逐条验证 LibraryWriteRepository 与原 LibraryViewModel 内联实现的
 * 行为契约（见 docs/behavior-notes/library-delete-family.md）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class LibraryWriteRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: LibraryWriteRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repo = LibraryWriteRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ---------- 种子 ----------

    private fun seedAlbum(title: String): Long = runBlocking {
        db.albumDao().insertAlbum(AlbumEntity(title = title, path = "/p/$title"))
    }

    private fun seedTrack(albumId: Long, path: String): Long = runBlocking {
        db.trackDao().insertTrack(TrackEntity(albumId = albumId, title = "t", path = path))
    }

    private fun seedSubtitle(trackId: Long, text: String = "sub"): Long = runBlocking {
        db.trackDao().insertSubtitle(SubtitleEntity(trackId = trackId, startMs = 0L, endMs = 1L, text = text))
    }

    private fun seedUserAlbumTag(albumId: Long, name: String, normalized: String): Long = runBlocking {
        val tagId = getOrCreateTagId(name, normalized)
        db.tagDao().insertAlbumTags(listOf(AlbumTagEntity(albumId = albumId, tagId = tagId, source = TagSource.USER)))
        tagId
    }

    private fun seedUserTrackTag(trackId: Long, name: String, normalized: String): Long = runBlocking {
        val tagId = getOrCreateTagId(name, normalized)
        db.trackTagDao().insertTrackTags(listOf(TrackTagEntity(trackId = trackId, tagId = tagId, source = TagSource.USER)))
        tagId
    }

    /** insertTags 是 IGNORE 冲突策略：已存在的归一名返回 -1，必须先查再插。 */
    private fun getOrCreateTagId(name: String, normalized: String): Long = runBlocking {
        db.tagDao().getTagByNormalized(normalized)?.id
            ?: db.tagDao().insertTags(listOf(TagEntity(name = name, nameNormalized = normalized))).first()
    }

    private fun normalizedNamesOf(albumId: Long): List<String> = runBlocking {
        val refs = db.tagDao().getAlbumTagsOnce(albumId).filter { it.source == TagSource.USER }
        refs.mapNotNull { ref -> db.tagDao().getTagById(ref.tagId)?.nameNormalized }.sorted()
    }

    // ---------- 标签族 ----------

    @Test
    fun replaceAlbumUserTags_replacesUserTagsAndReusesTagRows() = runBlocking {
        val albumA = seedAlbum("A")
        val albumB = seedAlbum("B")
        seedUserAlbumTag(albumA, "old", "old")

        repo.replaceAlbumUserTags(albumA, listOf("Alpha" to "alpha", "Beta" to "beta"))

        assertEquals(listOf("alpha", "beta"), normalizedNamesOf(albumA))
        assertEquals(3L, db.tagDao().countTags()) // old + alpha + beta，各一行

        repo.replaceAlbumUserTags(albumB, listOf("ALPHA" to "alpha"))

        assertEquals(3L, db.tagDao().countTags()) // alpha 行被复用，未新增
        assertEquals(listOf("alpha"), normalizedNamesOf(albumB))
        assertEquals(listOf("alpha", "beta"), normalizedNamesOf(albumA)) // 专辑 A 不受影响
    }

    @Test
    fun replaceAlbumUserTags_keepsOtherSourceRefs() = runBlocking {
        val albumId = seedAlbum("A")
        seedUserAlbumTag(albumId, "old", "old")
        runBlocking {
            val autoTagId = db.tagDao().insertTags(listOf(TagEntity(name = "auto", nameNormalized = "auto"))).first()
            db.tagDao().insertAlbumTags(listOf(AlbumTagEntity(albumId = albumId, tagId = autoTagId, source = TagSource.AUTO)))
        }

        repo.replaceAlbumUserTags(albumId, listOf("new" to "new"))

        assertEquals(listOf("new"), normalizedNamesOf(albumId)) // USER 源被整替
        val allRefs = db.tagDao().getAlbumTagsOnce(albumId)
        assertEquals(2, allRefs.size)
        assertTrue(allRefs.any { it.source == TagSource.AUTO }) // AUTO 源不动
    }

    @Test
    fun replaceTrackUserTags_replacesTrackTags() = runBlocking {
        val albumId = seedAlbum("A")
        val trackId = seedTrack(albumId, "/p/A/01.mp3")
        seedUserTrackTag(trackId, "old", "old")

        repo.replaceTrackUserTags(trackId, listOf("new" to "new"))

        assertEquals("new", db.trackTagDao().getTrackTagsCsvOnce(trackId, TagSource.USER))
        assertEquals(1, db.trackTagDao().getTrackTagsForTrack(trackId).size)
    }

    @Test
    fun renameUserTag_mergesIntoConflictAndReturnsAffectedAlbums() = runBlocking {
        val album1 = seedAlbum("A")
        val album2 = seedAlbum("B")
        val album3 = seedAlbum("C")
        val tagA = seedUserAlbumTag(album1, "A", "a")
        db.tagDao().insertAlbumTags(listOf(AlbumTagEntity(albumId = album2, tagId = tagA, source = TagSource.USER)))
        val tagB = seedUserAlbumTag(album3, "B", "b")
        val trackId = seedTrack(album1, "/p/A/01.mp3")
        seedUserTrackTag(trackId, "A", "a")

        val affected = repo.renameUserTag(tagA, "B")

        assertEquals(setOf(album1, album2, album3), affected)
        assertNull(db.tagDao().getTagById(tagA))
        val survivor = db.tagDao().getTagByNormalized("b")
        assertNotNull(survivor)
        assertEquals(tagB, survivor!!.id)
        assertEquals(setOf(album1, album2, album3), db.tagDao().getAlbumIdsForTag(tagB).toSet())
        assertEquals(listOf(tagB), db.trackTagDao().getTrackTagsForTrack(trackId).map { it.tagId })
        assertEquals(1L, db.tagDao().countTags()) // 合并分支删掉 A 行，只剩 B
    }

    @Test
    fun renameUserTag_blankNormalizedEarlyReturnsCapturedAlbumIds() = runBlocking {
        val album1 = seedAlbum("A")
        val tagA = seedUserAlbumTag(album1, "A", "a")

        val affected = repo.renameUserTag(tagA, "   ")

        assertEquals(setOf(album1), affected) // 早退也返回事务前集合（与原实现一致）
        assertEquals("a", db.tagDao().getTagByNormalized("a")?.nameNormalized) // 标签未变
    }

    @Test
    fun deleteUserTag_removesRefsAndTagReturnsAlbumIds() = runBlocking {
        val album1 = seedAlbum("A")
        val album2 = seedAlbum("B")
        val tagA = seedUserAlbumTag(album1, "A", "a")
        db.tagDao().insertAlbumTags(listOf(AlbumTagEntity(albumId = album2, tagId = tagA, source = TagSource.USER)))
        val trackId = seedTrack(album1, "/p/A/01.mp3")
        seedUserTrackTag(trackId, "A", "a")

        val affected = repo.deleteUserTag(tagA)

        assertEquals(setOf(album1, album2), affected)
        assertNull(db.tagDao().getTagById(tagA))
        assertTrue(db.tagDao().getAlbumIdsForTag(tagA).isEmpty())
        assertTrue(db.trackTagDao().getTrackTagsForTrack(trackId).isEmpty())
    }

    // ---------- 专辑/音轨删除族 ----------

    @Test
    fun deleteAlbumWithContent_removesTracksSubtitlesTagsResourcesAndAlbum() = runBlocking {
        val albumId = seedAlbum("A")
        val track1 = seedTrack(albumId, "/p/A/01.mp3")
        seedTrack(albumId, "/p/A/02.mp3")
        seedSubtitle(track1)
        seedUserAlbumTag(albumId, "tag", "tag")
        val resourceIds = db.onlineSavedResourceDao().insertAll(
            listOf(OnlineSavedResourceEntity(albumId = albumId, relativePath = "a.jpg", url = "https://x/a.jpg", fileType = "image"))
        )
        assertTrue(resourceIds.isNotEmpty())
        val entity = db.albumDao().getAlbumById(albumId)!!

        repo.deleteAlbumWithContent(albumId, entity)

        assertNull(db.albumDao().getAlbumById(albumId))
        assertTrue(db.trackDao().getTracksForAlbumOnce(albumId).isEmpty())
        assertTrue(db.trackDao().getSubtitlesForTrack(track1).isEmpty())
        assertTrue(db.tagDao().getAlbumTagsOnce(albumId).isEmpty())
        assertTrue(db.onlineSavedResourceDao().getForAlbumOnce(albumId).isEmpty())
    }

    @Test
    fun deleteVerifiedTracksAndResources_deletesOnlyListedAndClearsTreeCache() = runBlocking {
        val albumId = seedAlbum("A")
        val track1 = seedTrack(albumId, "/p/A/01.mp3")
        val track2 = seedTrack(albumId, "/p/A/02.mp3")
        seedSubtitle(track1)
        seedSubtitle(track2)
        val resourceIds = db.onlineSavedResourceDao().insertAll(
            listOf(
                OnlineSavedResourceEntity(albumId = albumId, relativePath = "a.jpg", url = "https://x/a.jpg", fileType = "image"),
                OnlineSavedResourceEntity(albumId = albumId, relativePath = "b.jpg", url = "https://x/b.jpg", fileType = "image")
            )
        )
        db.localTreeCacheDao().upsert(LocalTreeCacheEntity(albumId = albumId, cacheKey = "k", stamp = 1L, payloadJson = "{}", updatedAt = 1L))

        repo.deleteVerifiedTracksAndResources(albumId, listOf(track1), listOf(resourceIds.first()))

        assertTrue(db.trackDao().getTracksByIdsOnce(listOf(track1)).isEmpty())
        assertEquals(1, db.trackDao().getTracksByIdsOnce(listOf(track2)).size)
        assertTrue(db.trackDao().getSubtitlesForTrack(track1).isEmpty())
        assertEquals(1, db.trackDao().getSubtitlesForTrack(track2).size)
        assertEquals(1, db.onlineSavedResourceDao().getForAlbumOnce(albumId).size)
        assertNull(db.localTreeCacheDao().getByAlbumAndKey(albumId, "k"))
    }

    @Test
    fun deleteTrackCompletely_removesTrackAndCompanionsKeepsAlbumAndSiblings() = runBlocking {
        val albumId = seedAlbum("A")
        val track1 = seedTrack(albumId, "/p/A/01.mp3")
        val track2 = seedTrack(albumId, "/p/A/02.mp3")
        seedSubtitle(track1)
        db.remoteSubtitleSourceDao().insertAll(
            listOf(RemoteSubtitleSourceEntity(trackId = track1, url = "https://x/s.ass", language = "ja", ext = "ass"))
        )
        seedUserTrackTag(track1, "ttag", "ttag")
        db.localTreeCacheDao().upsert(LocalTreeCacheEntity(albumId = albumId, cacheKey = "k", stamp = 1L, payloadJson = "{}", updatedAt = 1L))

        repo.deleteTrackCompletely(track1, albumId)

        assertTrue(db.trackDao().getTracksByIdsOnce(listOf(track1)).isEmpty())
        assertEquals(1, db.trackDao().getTracksByIdsOnce(listOf(track2)).size)
        assertNotNull(db.albumDao().getAlbumById(albumId)) // 专辑不动
        assertTrue(db.trackDao().getSubtitlesForTrack(track1).isEmpty())
        assertTrue(db.remoteSubtitleSourceDao().getSourcesForTrackOnce(track1).isEmpty())
        assertTrue(db.trackTagDao().getTrackTagsForTrack(track1).isEmpty())
        assertNull(db.localTreeCacheDao().getByAlbumAndKey(albumId, "k"))
    }

    @Test
    fun nonTransactionalDeletePrimitives_matchOriginalSequencing() = runBlocking {
        val albumId = seedAlbum("A")
        val track1 = seedTrack(albumId, "/p/A/01.mp3")
        val track2 = seedTrack(albumId, "/p/A/02.mp3")
        seedSubtitle(track1)
        seedSubtitle(track2)
        val entity = db.albumDao().getAlbumById(albumId)!!

        // deleteTracksWithSubtitles：批量删（空守卫在调用侧，与原实现一致）
        repo.deleteTracksWithSubtitles(listOf(track1))
        assertTrue(db.trackDao().getTracksByIdsOnce(listOf(track1)).isEmpty())
        assertEquals(1, db.trackDao().getTracksByIdsOnce(listOf(track2)).size)

        // deleteTrackWithSubtitlesById：单轨删
        repo.deleteTrackWithSubtitlesById(track2)
        assertTrue(db.trackDao().getTracksByIdsOnce(listOf(track2)).isEmpty())
        assertNotNull(db.albumDao().getAlbumById(albumId)) // 专辑不动

        // deleteAlbumTracksAndSubtitles + deleteAlbumEntity：整专辑删
        seedTrack(albumId, "/p/A/03.mp3")
        db.onlineSavedResourceDao().insertAll(
            listOf(OnlineSavedResourceEntity(albumId = albumId, relativePath = "a.jpg", url = "https://x/a.jpg", fileType = "image"))
        )
        repo.deleteAlbumTracksAndSubtitles(albumId)
        repo.deleteAlbumEntity(entity)
        assertTrue(db.trackDao().getTracksForAlbumOnce(albumId).isEmpty())
        assertNull(db.albumDao().getAlbumById(albumId))
        assertTrue(db.onlineSavedResourceDao().getForAlbumOnce(albumId).isEmpty())
    }

    // ---------- B4b：扫描/初始化/聚合写族 ----------

    @Test
    fun seedAutoTagsFromAlbumTags_createsAutoRefsAndKeepsUserRefs() = runBlocking {
        val albumId = seedAlbum("A")
        seedUserAlbumTag(albumId, "keep", "keep")
        val albums = listOf(
            db.albumDao().getAlbumById(albumId)!!.copy(tags = "ボイス, ＣＧ集, ボイス")
        )

        repo.seedAutoTagsFromAlbumTags(albums)

        val autoRefs = db.tagDao().getAlbumTagsOnce(albumId).filter { it.source == TagSource.AUTO }
        val autoNames = autoRefs.mapNotNull { ref -> db.tagDao().getTagById(ref.tagId)?.nameNormalized }.sorted()
        assertEquals(listOf("cg集", "ボイス"), autoNames) // CSV 播种，重复归一形去重
        assertEquals(1, db.tagDao().getAlbumTagsOnce(albumId).count { it.source == TagSource.USER }) // USER 源保留
        assertEquals(3L, db.tagDao().countTags()) // keep + ボイス + cg集
    }

    @Test
    fun upsertAlbumTagsFromCsv_replacesTargetSourceOnly() = runBlocking {
        val albumId = seedAlbum("A")
        seedUserAlbumTag(albumId, "user-tag", "usertag")
        runBlocking {
            val scanTagId = db.tagDao().insertTags(listOf(TagEntity(name = "old-scan", nameNormalized = "oldscan"))).first()
            db.tagDao().insertAlbumTags(listOf(AlbumTagEntity(albumId = albumId, tagId = scanTagId, source = TagSource.SCAN)))
        }

        repo.upsertAlbumTagsFromCsv(albumId, "new-scan", TagSource.SCAN)

        val scanNames = db.tagDao().getAlbumTagsOnce(albumId)
            .filter { it.source == TagSource.SCAN }
            .mapNotNull { ref -> db.tagDao().getTagById(ref.tagId)?.nameNormalized }
        assertEquals(listOf("newscan"), scanNames)
        assertEquals(1, db.tagDao().getAlbumTagsOnce(albumId).count { it.source == TagSource.USER }) // USER 源不动
    }

    @Test
    fun refreshAlbumAudioAggregate_rewritesAlbumAggregateFields() = runBlocking {
        val albumId = seedAlbum("A")
        seedTrack(albumId, "/p/A/01.mp3")
        seedTrack(albumId, "/p/A/02.mp3")
        db.trackDao().updateTracks(
            listOf(
                db.trackDao().getTracksForAlbumOnce(albumId)[0].copy(duration = 10.0),
                db.trackDao().getTracksForAlbumOnce(albumId)[1].copy(duration = 20.0)
            )
        )
        val sizes = mapOf("/p/A/01.mp3" to 100L, "/p/A/02.mp3" to 250L)

        repo.refreshAlbumAudioAggregate(albumId) { path -> sizes[path] }

        val entity = db.albumDao().getAlbumById(albumId)!!
        assertEquals(2, entity.audioTrackCount)
        assertEquals(30.0, entity.audioTotalDuration, 0.001)
        assertEquals(350L, entity.audioTotalSizeBytes)
    }

    @Test
    fun backfillLegacyOnlineSavedAlbumRoots_fillsOnlyEligibleAlbums() = runBlocking {
        val eligible = db.albumDao().insertAlbum(
            AlbumEntity(title = "web", path = "web://rj/RJ999999", workId = "RJ999999", rjCode = "RJ999999")
        )
        val ineligible = db.albumDao().insertAlbum(
            AlbumEntity(title = "local", path = "/p/local", localPath = "/p/local", workId = "RJ888888", rjCode = "RJ888888")
        )
        db.localTreeCacheDao().upsert(LocalTreeCacheEntity(albumId = eligible, cacheKey = "k", stamp = 1L, payloadJson = "{}", updatedAt = 1L))
        val albums = db.albumDao().getAllAlbumsOnce()

        repo.backfillLegacyOnlineSavedAlbumRoots(albums) { entity ->
            "/legacy/${entity.rjCode}"
        }

        val filled = db.albumDao().getAlbumById(eligible)!!
        assertEquals("/legacy/RJ999999", filled.localPath)
        assertNull(db.localTreeCacheDao().getByAlbumAndKey(eligible, "k")) // 事务内清缓存
        assertEquals("/p/local", db.albumDao().getAlbumById(ineligible)!!.localPath) // 不合条件不动
    }

    @Test
    fun computeAlbumAudioAggregate_sumsCountsDurationsAndSizes() = runBlocking {
        val specs = listOf(
            TrackEntity(albumId = 1L, title = "a", path = "/a.mp3", duration = 1.5),
            TrackEntity(albumId = 1L, title = "b", path = "/b.mp3", duration = 2.5)
        )

        val aggregate = repo.computeAlbumAudioAggregate(specs) { path -> if (path == "/a.mp3") 10L else null }

        assertEquals(2, aggregate.trackCount)
        assertEquals(4.0, aggregate.totalDuration, 0.001)
        assertEquals(10L, aggregate.totalSizeBytes) // null 探查按 0 计
    }
}

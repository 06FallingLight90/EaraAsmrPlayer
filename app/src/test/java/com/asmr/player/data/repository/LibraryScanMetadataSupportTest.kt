package com.asmr.player.data.repository

import android.app.Application
import android.net.Uri
import androidx.room.Room
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.metadata.AudioMetadata
import com.asmr.player.data.local.metadata.AudioMetadataReader
import com.asmr.player.data.local.db.entities.TrackEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
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
 * T3'：扫描入库元数据/来源回填的行为钉测试（见 docs/behavior-notes/scan-metadata-sourcing.md）。
 * - 纯函数：source 保留规则、新插/更新实体构造（成功/缺失）。
 * - 事务集成（fake reader 计数）：SAF 文档树入库——仅新插轨读元数据（增量）、
 *   artist/albumTag 落库、首插轨内嵌封面字节透出、entity.source 透传落库。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class LibraryScanMetadataSupportTest {
    private lateinit var db: AppDatabase
    private lateinit var fakeReader: FakeAudioMetadataReader
    private lateinit var repo: LibraryWriteRepository
    private lateinit var support: LibraryScanMetadataSupport

    private class FakeAudioMetadataReader(
        var result: AudioMetadata? = null,
        var throwError: Boolean = false,
    ) : AudioMetadataReader {
        val calls = mutableListOf<Uri>()

        override fun read(uri: Uri): AudioMetadata? {
            calls.add(uri)
            if (throwError) throw IllegalStateException("boom")
            return result
        }
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        fakeReader = FakeAudioMetadataReader()
        repo = LibraryWriteRepository(db, RuntimeEnvironment.getApplication(), fakeReader)
        support = repo.scanMetadataSupport
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ---------- 来源回填：单点解析规则 ----------

    @Test
    fun resolveAlbumSource_keepsExistingNonBlankSource() {
        assertEquals(
            "dlsite_download",
            support.resolveAlbumSource("dlsite_download", AlbumEntity.SOURCE_LOCAL_SCAN)
        )
        // 空白串视同缺失
        assertEquals(
            AlbumEntity.SOURCE_LOCAL_SCAN,
            support.resolveAlbumSource("   ", AlbumEntity.SOURCE_LOCAL_SCAN)
        )
    }

    @Test
    fun resolveAlbumSource_backfillsBlankExistingWithPipelineSource() {
        assertEquals(
            AlbumEntity.SOURCE_LOCAL_SCAN,
            support.resolveAlbumSource(null, AlbumEntity.SOURCE_LOCAL_SCAN)
        )
        assertEquals(
            AlbumEntity.SOURCE_DLSITE_DOWNLOAD,
            support.resolveAlbumSource(null, AlbumEntity.SOURCE_DLSITE_DOWNLOAD)
        )
        assertNull(support.resolveAlbumSource(null, null))
    }

    // ---------- 元数据读取：成功/缺失/异常/Uri 路由 ----------

    @Test
    fun readForNewTrack_mapsFileAndContentPathsToUriAndTrimsFields() {
        fakeReader.result = AudioMetadata(title = "t", artist = " 圆神 ", album = " 专辑A ", embeddedCover = byteArrayOf(1, 2, 3))

        val meta = support.readForNewTrack("/music/01.mp3")

        assertEquals(listOf(Uri.fromFile(java.io.File("/music/01.mp3"))), fakeReader.calls)
        assertEquals("圆神", meta!!.artist)
        assertEquals("专辑A", meta.albumTag)
        assertArrayEquals(byteArrayOf(1, 2, 3), meta.embeddedCover)

        support.readForNewTrack("content://tree/album/2.flac")
        assertEquals(Uri.parse("content://tree/album/2.flac"), fakeReader.calls[1])
    }

    @Test
    fun readForNewTrack_normalizesBlankFieldsToNull() {
        fakeReader.result = AudioMetadata(title = "t", artist = "  ", album = "", embeddedCover = ByteArray(0))

        val meta = support.readForNewTrack("/music/01.mp3")

        assertNull(meta!!.artist)
        assertNull(meta.albumTag)
        assertNull(meta.embeddedCover)
    }

    @Test
    fun readForNewTrack_returnsNullWhenUnreadableOrReaderMissingOrThrows() {
        // 读不到（read 返 null）
        fakeReader.result = null
        assertNull(support.readForNewTrack("/music/01.mp3"))

        // 读取异常不外抛
        fakeReader.throwError = true
        assertNull(support.readForNewTrack("/music/01.mp3"))
        fakeReader.throwError = false

        // reader 缺席（无 Hilt 绑定注入的构造路径）：整体退化为 null
        val bare = LibraryScanMetadataSupport(metadataReader = null)
        assertNull(bare.readForNewTrack("/music/01.mp3"))
    }

    // ---------- 实体构造：新插回填 / 更新不动 ----------

    @Test
    fun newTrackEntity_fillsArtistAndAlbumTagFromMetadataOrLeavesNull() {
        val filled = support.newTrackEntity(
            albumId = 1L, title = "t1", path = "/a/1.mp3", group = "g",
            metadata = LibraryScanMetadataSupport.ScannedTrackMetadata("artist", "album", null),
        )
        assertEquals("artist", filled.artist)
        assertEquals("album", filled.albumTag)

        val missing = support.newTrackEntity(
            albumId = 1L, title = "t2", path = "/a/2.mp3", group = "",
            metadata = null,
        )
        assertNull(missing.artist)
        assertNull(missing.albumTag)
        assertEquals(0.0, missing.duration, 0.0)
    }

    @Test
    fun updatedTrackEntity_overwritesTitleGroupOnly() {
        val existing = TrackEntity(
            albumId = 1L, title = "old", path = "/a/1.mp3", group = "g0",
            artist = "keep-artist", albumTag = "keep-album",
        )

        val updated = support.updatedTrackEntity(existing, title = "new", group = "g1")

        assertEquals("new", updated.title)
        assertEquals("g1", updated.group)
        assertEquals("keep-artist", updated.artist) // 已存在轨零 MMR 打开、元数据字段不覆盖
        assertEquals("keep-album", updated.albumTag)
    }

    // ---------- 事务集成：SAF 文档树入库（增量计数 + 落库 + 封面透出 + source） ----------

    private fun docSpecs() = listOf(
        LibraryWriteRepository.ScanTrackSpec(title = "t1", path = "content://tree/album/1.mp3", group = ""),
        LibraryWriteRepository.ScanTrackSpec(title = "t2", path = "content://tree/album/2.flac", group = "disc2"),
    )

    private fun scanDocAlbum(entity: AlbumEntity): LibraryWriteRepository.DocumentScanResult = runBlocking {
        repo.upsertScannedDocumentAlbum(
            entity = entity,
            scanRootPath = "content://tree/album",
            trackSpecs = docSpecs(),
            subtitlesByAudioPath = emptyMap(),
            cacheLeaves = emptyList(),
            fileSizeQuery = { null },
            stampProvider = { 0L },
        )
    }

    @Test
    fun upsertScannedDocumentAlbum_readsMetadataOnlyForInsertedTracksAndPersistsFields() = runBlocking {
        fakeReader.result = AudioMetadata(title = "t", artist = "artist1", album = "album1", embeddedCover = byteArrayOf(9, 9))

        val source = AlbumEntity(title = "A", path = "content://tree/album", source = AlbumEntity.SOURCE_LOCAL_SCAN)
        val first = scanDocAlbum(source)

        // 新插两轨 → 恰读两次；artist/albumTag 落库；首插轨封面字节透出
        assertEquals(2, fakeReader.calls.size)
        val tracks = db.trackDao().getTracksForAlbumOnce(first.albumId).sortedBy { it.title }
        assertEquals(listOf("artist1", "artist1"), tracks.map { it.artist })
        assertEquals(listOf("album1", "album1"), tracks.map { it.albumTag })
        assertArrayEquals(byteArrayOf(9, 9), first.firstInsertedCoverBytes)
        assertEquals(AlbumEntity.SOURCE_LOCAL_SCAN, db.albumDao().getAlbumById(first.albumId)!!.source)

        // 二扫同 specs：未变更文件零读取（fake 计数不变），且换一版标签也不覆盖既有值
        fakeReader.calls.clear()
        fakeReader.result = AudioMetadata(title = "t", artist = "artist2", album = "album2", embeddedCover = null)
        val second = scanDocAlbum(source.copy(id = first.albumId))

        assertEquals(0, fakeReader.calls.size)
        assertEquals(first.albumId, second.albumId)
        val after = db.trackDao().getTracksForAlbumOnce(first.albumId).sortedBy { it.title }
        assertEquals(listOf("artist1", "artist1"), after.map { it.artist })
        assertEquals(listOf("album1", "album1"), after.map { it.albumTag })
        assertNull(second.firstInsertedCoverBytes) // 无新插轨 → 无封面字节透出
    }

    @Test
    fun upsertScannedDocumentAlbum_persistsDlsiteSourceAndMissingMetadataAsNull() = runBlocking {
        fakeReader.result = null // 全部标签缺失 → 字段留 null（不造默认值）

        val source = AlbumEntity(
            title = "B", path = "content://tree/album",
            source = AlbumEntity.SOURCE_DLSITE_DOWNLOAD,
        )
        val result = scanDocAlbum(source)

        assertEquals(2, fakeReader.calls.size) // 读尝试照做，结果为 null
        val tracks = db.trackDao().getTracksForAlbumOnce(result.albumId)
        assertEquals(2, tracks.size)
        assertTrue(tracks.all { it.artist == null && it.albumTag == null })
        assertEquals(AlbumEntity.SOURCE_DLSITE_DOWNLOAD, db.albumDao().getAlbumById(result.albumId)!!.source)
    }
}

package com.asmr.player.ui.library.albumdetail

import com.asmr.player.data.remote.api.AsmrOneTrackNodeResponse
import org.junit.Assert.assertEquals
import org.junit.Test

/** A3-1：钉住从三份 flatten 局部实现提取的共享字幕候选收集的行为。 */
class CollectSubtitleCandidatesTest {

    private fun node(
        title: String? = null,
        type: String? = null,
        children: List<AsmrOneTrackNodeResponse>? = null,
        streamUrl: String? = null,
        mediaDownloadUrl: String? = null,
        duration: Double? = null
    ) = AsmrOneTrackNodeResponse(
        title = title,
        type = type,
        children = children,
        streamUrl = streamUrl,
        mediaDownloadUrl = mediaDownloadUrl,
        duration = duration
    )

    @Test
    fun collectsSubtitleLeavesWithNestedPathsAndSkipsNonSubtitleLeaves() {
        val tree = listOf(
            node(title = "01 intro.mp3", mediaDownloadUrl = "https://x/01.mp3"),
            node(
                title = "Folder",
                children = listOf(
                    node(title = "sub.lrc", mediaDownloadUrl = "https://x/f/sub.lrc", duration = 1.0),
                    node(title = "cover.jpg", mediaDownloadUrl = "https://x/f/cover.jpg"),
                    node(title = "empty.lrc")
                )
            )
        )

        val entries = collectSubtitleCandidates(tree, setOf("lrc", "srt", "vtt"))

        assertEquals(1, entries.size)
        val entry = entries.single()
        assertEquals("Folder/sub.lrc", entry.path)
        assertEquals("https://x/f/sub.lrc", entry.url)
        assertEquals(1.0, entry.duration!!, 1e-9)
        assertEquals("sub.lrc", entry.node.title)
    }

    @Test
    fun urlFallbackExtOnlyGatesFirstCheck_pathExtStillDecidesCollect() {
        // 原实现的 quirk：URL 回退扩展名让叶子通过 ext 门，
        // 但路径无扩展名时 inferCandidate 仍返回 null——提取后行为逐字保留
        val tree = listOf(node(title = "track no ext", mediaDownloadUrl = "https://x/a.b/sub.vtt"))
        val entries = collectSubtitleCandidates(
            tree,
            setOf("lrc", "srt", "vtt"),
            extOf = { rawTitle, url ->
                val ext0 = rawTitle.substringAfterLast('.', "").lowercase()
                if (ext0.isNotBlank()) ext0 else url.substringBefore('?').substringAfterLast('.', "").lowercase()
            }
        )
        assertEquals(0, entries.size)
    }
}

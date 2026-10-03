package com.asmr.player.data.remote.crawler

import com.asmr.player.data.remote.api.AsmrOneTrackNodeResponse
import kotlinx.coroutines.CancellationException

/** R2-C4b-3：从 ui/library/albumdetail/AlbumDetailViewModelSupport 迁入 data 层。纯搬迁，逻辑未改。 */
internal fun asmrOneTracksCacheKey(site: Int?, workId: String): String {
    return "${site ?: "unknown"}:${workId.trim()}"
}

internal suspend fun fetchAsmrOneTracksFromBackup(
    candidateRjs: List<String>,
    throwWhenAllRequestsFail: Boolean = false,
    fetchBackup: suspend (String) -> Pair<String, List<AsmrOneTrackNodeResponse>>?
): Pair<String?, List<AsmrOneTrackNodeResponse>> {
    var successfulRequestCount = 0
    var lastFailure: Exception? = null
    candidateRjs
        .asSequence()
        .map { it.trim().uppercase() }
        .filter { it.isNotBlank() }
        .distinct()
        .forEach { rj ->
            val backupResult = try {
                fetchBackup(rj).also { successfulRequestCount += 1 }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                lastFailure = error
                null
            }
            val tree = backupResult?.second.orEmpty()
            if (backupResult != null && tree.isNotEmpty()) {
                return backupResult.first.takeIf { it.isNotBlank() } to tree
            }
        }
    if (throwWhenAllRequestsFail && successfulRequestCount == 0) {
        lastFailure?.let { throw it }
    }
    return null to emptyList()
}

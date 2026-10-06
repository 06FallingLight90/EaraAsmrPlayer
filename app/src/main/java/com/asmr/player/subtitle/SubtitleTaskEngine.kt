package com.asmr.player.subtitle

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import com.asmr.player.util.DEEPSEEK_TRANSLATION_CONCURRENCY
import kotlinx.coroutines.Dispatchers
import com.google.gson.Gson
import java.io.IOException
import kotlinx.coroutines.Job
import android.util.Log
import kotlin.random.Random
import com.asmr.player.data.local.db.entities.SubtitleCommittedCaptionEntity
import com.asmr.player.data.local.db.entities.SubtitleEntity
import com.asmr.player.data.local.db.entities.SubtitleTaskItemEntity
import com.asmr.player.data.local.db.entities.SubtitleTitleOwnerKind
import com.asmr.player.data.local.db.entities.SubtitleTranscriptionChunkEntity
import com.asmr.player.data.local.db.entities.SubtitleTranslationSourceEntity
import com.asmr.player.data.local.db.entities.TrackEntity
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.cancel
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.asmr.player.data.local.db.entities.titleForDisplay
import kotlinx.coroutines.withTimeoutOrNull
import androidx.room.withTransaction

internal suspend fun SubtitleTaskService.schedulerLoop() {
        while (serviceScope.isActive) {
            try {
                withTimeoutOrNull(SubtitleTaskService.SCHEDULER_TICK_MS) { wakeSignals.receive() }
                reconcileControlRequests()
                scheduleTranscription()
                scheduleTranslations()
                scheduleTitleTranslations()
                releaseTranscriptionEngineWhenIdle()
                updateForegroundNotification()
                stopWhenIdle()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Log.e(SubtitleTaskService.TAG, "字幕任务调度循环异常", error)
                delay(SubtitleTaskService.SCHEDULER_TICK_MS)
            }
        }
    }

internal suspend fun SubtitleTaskService.reconcileControlRequests() {
        val items = database.subtitleTaskDao().getAllItems()
        items.filter { it.state == SubtitleItemState.PAUSE_REQUESTED }.forEach { item ->
            cancelRunningJob(item.id)
            if (!isJobRunning(item.id)) {
                database.subtitleTaskDao().updateItem(
                    item.copy(state = SubtitleItemState.PAUSED, updatedAt = System.currentTimeMillis())
                )
                repository.refreshTaskState(item.taskId)
            }
        }
        items.filter { it.state == SubtitleItemState.CANCEL_REQUESTED }.forEach { item ->
            cancelRunningJob(item.id)
            if (!isJobRunning(item.id)) {
                val warning = repository.finishCancellation(item.id)
                if (!warning.isNullOrBlank()) showWarningNotification(warning)
            }
        }
    }

internal suspend fun SubtitleTaskService.scheduleTranscription() {
        if (transcriptionJob?.isActive == true) return
        transcriptionJob = null
        val candidate = database.subtitleTaskDao().getNextTranscription() ?: return
        val selectedId = SubtitleDispatchPolicy.selectTranscriptionItem(
            orderedCandidates = listOf(candidate.id),
            transcriptionActive = false
        ) ?: return
        val item = if (candidate.id == selectedId) candidate else return
        transcriptionItemId = item.id
        transcriptionJob = serviceScope.launch {
            try {
                transcribe(item.id)
            } finally {
                transcriptionJob = null
                transcriptionItemId = null
                signalWake()
            }
        }
    }

internal suspend fun SubtitleTaskService.scheduleTranslations() {
        val dao = database.subtitleTaskDao()
        val candidates = dao.getTranslationCandidates(System.currentTimeMillis(), Int.MAX_VALUE)
            .filterNot { translationJobs[it.id]?.isActive == true }
        if (!isNetworkAvailable()) {
            candidates.forEach { item ->
                if (item.state != SubtitleItemState.WAITING_NETWORK) {
                    dao.updateItem(
                        item.copy(
                            state = SubtitleItemState.WAITING_NETWORK,
                            suspendedFromState = item.suspendedFromState.ifBlank { item.state },
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    repository.refreshTaskState(item.taskId)
                }
            }
            return
        }
        val selectedIds = SubtitleDispatchPolicy.selectTranslationItems(
            orderedCandidates = candidates.map(SubtitleTaskItemEntity::id),
            activeItemIds = translationJobs.filterValues(Job::isActive).keys,
            concurrency = DEEPSEEK_TRANSLATION_CONCURRENCY
        )
        val selected = candidates.filter { it.id in selectedIds }
        candidates.filterNot { it.id in selectedIds }.forEach { item ->
            if (item.state != SubtitleItemState.WAITING_SLOT) {
                dao.updateItem(
                    item.copy(
                        state = SubtitleItemState.WAITING_SLOT,
                        suspendedFromState = item.suspendedFromState.ifBlank { item.state },
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
        selected.forEach { item ->
            val job = serviceScope.launch(start = CoroutineStart.LAZY) {
                try {
                    translate(item.id)
                } finally {
                    translationJobs.remove(item.id)
                    signalWake()
                }
            }
            translationJobs[item.id] = job
            job.start()
        }
    }

internal suspend fun SubtitleTaskService.scheduleTitleTranslations() {
        if (!isNetworkAvailable()) return
        val now = System.currentTimeMillis()
        val ownerDao = database.subtitleTitleOwnerDao()
        ownerDao.getPendingTaskIds().forEach { taskId ->
            if (titleTranslationJobs[taskId]?.isActive == true) return@forEach
            if ((titleTranslationRetryAt[taskId] ?: 0L) > now) return@forEach
            val items = database.subtitleTaskDao().getItemsForTask(taskId)
            if (items.none { it.state !in SubtitleTaskService.TITLE_TRANSLATION_BLOCKED_STATES }) return@forEach
            val job = serviceScope.launch(start = CoroutineStart.LAZY) {
                try {
                    translateDisplayNamesForTask(taskId)
                    repository.finishTitleTranslation(taskId)
                    titleTranslationRetryAt.remove(taskId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: SubtitleTranslationException) {
                    titleTranslationRetryAt[taskId] = if (error.retryable) {
                        System.currentTimeMillis() + SubtitleTaskService.TITLE_TRANSLATION_RETRY_BACKOFF_MS
                    } else {
                        SubtitleTaskService.TITLE_TRANSLATION_DISABLED_RETRY_AT
                    }
                    Log.w(SubtitleTaskService.TAG, "作品显示名翻译失败 taskId=$taskId", error)
                } catch (error: Throwable) {
                    titleTranslationRetryAt[taskId] = System.currentTimeMillis() + SubtitleTaskService.TITLE_TRANSLATION_RETRY_BACKOFF_MS
                    Log.w(SubtitleTaskService.TAG, "作品显示名翻译失败 taskId=$taskId", error)
                } finally {
                    titleTranslationJobs.remove(taskId)
                    signalWake()
                }
            }
            titleTranslationJobs[taskId] = job
            job.start()
        }
    }

    /**
     * 把一个字幕任务涉及的作品标题与音轨标题翻译成中文，写入 displayTitle。
     * 只在任务仍处于翻译阶段（未取消/未删除）时提交结果。
     */
internal suspend fun SubtitleTaskService.translateDisplayNamesForTask(taskId: String) {
        val ownerDao = database.subtitleTitleOwnerDao()
        val registeredOwners = ownerDao.getByTask(taskId)
        if (registeredOwners.none { owner -> owner.displayTitle.isBlank() }) return
        val albumOwners = registeredOwners.filter { it.kind == SubtitleTitleOwnerKind.ALBUM }
        val trackOwners = registeredOwners.filter { it.kind == SubtitleTitleOwnerKind.TRACK }
        val allTracks = database.trackDao()
            .getTracksByIdsOnce(trackOwners.map { it.targetId })
            .associateBy { it.id }
        val albumIds = (albumOwners.map { owner -> owner.targetId } + allTracks.values.map { track -> track.albumId })
            .distinct()
        val albums = albumIds.mapNotNull { albumId -> database.albumDao().getAlbumById(albumId) }
        if (albums.isEmpty()) {
            // 任务涉及的作品行已全部失效（例如已被删除），清除未完成的登记，避免无限重试
            ownerDao.deletePendingForTask(taskId)
            return
        }
        val tracksByAlbum = allTracks.values.groupBy { it.albumId }
        val client = requireTranslationClient()
        albums.forEach { album ->
            val albumTracks = tracksByAlbum[album.id].orEmpty()
            if (albumTracks.isEmpty()) return@forEach
            val translated = try {
                client.translateDisplayNames(
                    albumTitle = album.title,
                    circle = album.circle,
                    cv = album.cv,
                    trackTitles = albumTracks.map { it.id to it.title }
                )
            } finally {
                requestBalanceRefresh()
            }
            database.withTransaction {
                database.subtitleTaskDao().getTask(taskId) ?: return@withTransaction
                val items = database.subtitleTaskDao().getItemsForTask(taskId)
                if (items.none { it.state !in SubtitleTaskService.TITLE_TRANSLATION_BLOCKED_STATES }) return@withTransaction
                if (translated.albumTitle.isNotBlank()) {
                    database.albumDao().updateAlbumDisplayTitle(album.id, translated.albumTitle)
                    ownerDao.updateDisplayTitle(
                        taskId, SubtitleTitleOwnerKind.ALBUM, album.id, translated.albumTitle
                    )
                }
                albumTracks.forEach trackLoop@{ track ->
                    val title = translated.trackTitles[track.id] ?: return@trackLoop
                    database.trackDao().updateTrackDisplayTitle(track.id, title)
                    ownerDao.updateDisplayTitle(
                        taskId, SubtitleTitleOwnerKind.TRACK, track.id, title
                    )
                }
            }
        }
    }

internal suspend fun SubtitleTaskService.transcribe(itemId: String) {
        val dao = database.subtitleTaskDao()
        val initial = dao.getItem(itemId) ?: return
        if (initial.state != SubtitleItemState.QUEUED_TRANSCRIPTION) return
        val modelId = resolveTranscriptionModelId(
            persistedModelId = initial.transcriptionModelId,
            activeModelId = SubtitleModelRepository.get(applicationContext).activeModel().id
        )
        val activeItem = initial.copy(
            state = SubtitleItemState.TRANSCRIBING,
            transcriptionModelId = modelId,
            errorMessage = "",
            updatedAt = System.currentTimeMillis()
        )
        dao.updateItem(activeItem)
        repository.refreshTaskState(activeItem.taskId)
        try {
            val engine = requireTranscriptionEngine(modelId)
            val existingChunks = dao.getChunks(itemId)
            val resumeAtMs = existingChunks.lastOrNull()?.endMs ?: 0L
            LocalAudioDecoder(applicationContext, engine.model.inputSampleRateHz).decode(
                path = activeItem.trackPath,
                startAtMs = resumeAtMs
            ) { chunk ->
                coroutineContext.ensureActive()
                val activeContext = coroutineContext
                val segments = engine.transcribe(
                    channelSamples = chunk.channelSamples,
                    isCancelled = { !activeContext.isActive },
                    onProgress = { }
                )
                coroutineContext.ensureActive()
                val absoluteSegments = segments.map { segment ->
                    GeneratedSubtitle(
                        startMs = chunk.startMs + segment.startMs,
                        endMs = chunk.startMs + segment.endMs,
                        text = segment.text
                    )
                }
                val absoluteTokens = segments.flatMap { segment ->
                    val segmentStart = chunk.startMs + segment.startMs
                    segment.tokens.map { token ->
                        GeneratedSubtitle(
                            startMs = segmentStart + token.startMs,
                            endMs = segmentStart + token.endMs,
                            text = token.text
                        )
                    }
                }
                database.withTransaction {
                    val current = dao.getItem(itemId) ?: throw CancellationException("字幕任务已删除")
                    if (current.state != SubtitleItemState.TRANSCRIBING) throw CancellationException("字幕转录已暂停或取消")
                    val chunkIndex = current.transcriptionChunkCursor
                    val processedMs = chunk.startMs + chunk.durationMs
                    dao.upsertChunk(
                        SubtitleTranscriptionChunkEntity(
                            itemId = itemId,
                            chunkIndex = chunkIndex,
                            startMs = chunk.startMs,
                            endMs = processedMs,
                            segmentsJson = gson.toJson(absoluteSegments),
                            tokensJson = gson.toJson(absoluteTokens),
                            createdAt = System.currentTimeMillis()
                        )
                    )
                    val totalMs = maxOf(current.totalDurationMs, chunk.totalDurationMs, processedMs)
                    dao.updateItem(
                        current.copy(
                            transcriptionChunkCursor = chunkIndex + 1,
                            transcriptionProgress = if (totalMs > 0L) {
                                (processedMs * 100L / totalMs).toInt().coerceIn(0, 99)
                            } else {
                                current.transcriptionProgress
                            },
                            transcribedMs = processedMs,
                            totalDurationMs = totalMs,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
            prepareGeneratedTranslation(itemId)
        } catch (cancelled: CancellationException) {
            settleCancelledExecution(itemId)
            throw cancelled
        } catch (error: Throwable) {
            Log.w(SubtitleTaskService.TAG, "本地字幕转录失败 itemId=$itemId type=${error.javaClass.name}")
            releaseFailedTranscriptionEngine()
            failItem(itemId, SubtitleFailureMessages.transcription(error))
        }
    }

internal suspend fun SubtitleTaskService.prepareGeneratedTranslation(itemId: String) {
        val dao = database.subtitleTaskDao()
        val chunks = dao.getChunks(itemId)
        val generated = chunks.flatMap { chunk -> gson.generatedSubtitles(chunk.segmentsJson) }
        val item = dao.getItem(itemId) ?: return
        val durationMs = maxOf(item.totalDurationMs, chunks.lastOrNull()?.endMs ?: 0L)
        val sourceSegments = SubtitleSegmentNormalizer.normalize(generated, durationMs)
        check(sourceSegments.isNotEmpty()) { "未识别到可生成字幕的日语语音" }
        val fallback = SubtitleSegmentNormalizer.normalize(
            SubtitleSemanticSegmenter.reflow(sourceSegments),
            durationMs
        )
        check(fallback.isNotEmpty()) { "未识别到可生成字幕的日语语音" }
        val layout = buildGeneratedTranslationLayout(itemId, fallback)
        val playerFallback = fallback.map { caption ->
            SubtitleEntity(
                trackId = item.trackId,
                startMs = caption.startMs,
                endMs = caption.endMs,
                text = caption.text,
                japaneseText = caption.text
            )
        }
        database.withTransaction {
            val current = dao.getItem(itemId) ?: return@withTransaction
            check(current.state == SubtitleItemState.TRANSCRIBING) { "字幕转录已暂停或取消" }
            val playerCurrent = database.trackDao().getSubtitlesForTrack(current.trackId).sortedWith(SUBTITLE_ORDER)
            check(subtitleHash(playerCurrent) == current.lastPublishedHash) { "字幕在转录期间已被修改，未覆盖用户版本" }
            dao.deleteSources(itemId)
            dao.deleteFallbackCaptions(itemId)
            dao.insertSources(layout.sources)
            dao.insertFallbackCaptions(layout.fallbackCaptions)
            database.trackDao().deleteSubtitlesForTrack(current.trackId)
            database.trackDao().insertSubtitles(playerFallback)
            val publishedHash = subtitleHash(playerFallback)
            dao.updateItem(
                current.copy(
                    state = SubtitleItemState.QUEUED_TRANSLATION,
                    transcriptionProgress = 100,
                    transcribedMs = durationMs,
                    totalDurationMs = durationMs,
                    translationCursor = 0,
                    translationTotal = layout.sources.size,
                    translationBatchIndex = 0,
                    translationBatchTotal = fullTranslationRequestCount(layout.sources.size),
                    attempt = 0,
                    errorMessage = "",
                    lastPublishedHash = publishedHash,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        repository.refreshTaskState(item.taskId)
    }

internal suspend fun SubtitleTaskService.translate(itemId: String) {
        val dao = database.subtitleTaskDao()
        val item = dao.getItem(itemId) ?: return
        if (item.state !in SubtitleTaskService.TRANSLATION_QUEUE_STATES) return
        val sources = dao.getSources(itemId)
        if (sources.isEmpty()) {
            failItem(itemId, "字幕翻译源已丢失")
            return
        }
        val confirmedSourceCount = dao.getCommittedCaptions(itemId)
            .sumOf { caption -> caption.lastSourceIndex - caption.firstSourceIndex + 1 }
        dao.updateItem(
            item.copy(
                state = SubtitleItemState.TRANSLATING,
                translationCursor = confirmedSourceCount,
                translationTotal = sources.size,
                translationBatchIndex = 1,
                translationBatchTotal = 1,
                suspendedFromState = "",
                errorMessage = "",
                updatedAt = System.currentTimeMillis()
            )
        )
        repository.refreshTaskState(item.taskId)
        try {
            if (item.mode == SubtitleTaskMode.GENERATED) {
                translateGeneratedFull(itemId, sources)
            } else {
                translateManualFull(itemId, sources)
            }
        } catch (cancelled: CancellationException) {
            settleCancelledExecution(itemId)
            throw cancelled
        } catch (error: SubtitleTranslationException) {
            handleTranslationFailure(itemId, error)
        } catch (error: IOException) {
            handleTranslationFailure(
                itemId,
                SubtitleTranslationException(
                    SubtitleFailureMessages.network(error),
                    retryable = true,
                    cause = error
                )
            )
        } catch (error: Throwable) {
            Log.w(SubtitleTaskService.TAG, "字幕翻译运行异常 itemId=$itemId type=${error.javaClass.name}")
            failItem(itemId, SubtitleFailureMessages.translation(error))
        }
    }

internal suspend fun SubtitleTaskService.translateGeneratedFull(
        itemId: String,
        sourceEntities: List<SubtitleTranslationSourceEntity>
    ) {
        translateWithProgress(itemId, sourceEntities, allowMerging = true)
    }

internal suspend fun SubtitleTaskService.translateManualFull(
        itemId: String,
        sourceEntities: List<SubtitleTranslationSourceEntity>
    ) {
        translateWithProgress(itemId, sourceEntities, allowMerging = false)
    }

internal suspend fun SubtitleTaskService.translateWithProgress(
        itemId: String,
        sourceEntities: List<SubtitleTranslationSourceEntity>,
        allowMerging: Boolean
    ) {
        val dao = database.subtitleTaskDao()
        val sources = sourceEntities.map { it.toGeneratedSource() }
        val confirmed = dao.getCommittedCaptions(itemId).map { it.toGeneratedCaption() }
        val client = requireTranslationClient()
        try {
            client.translateSubtitles(
                sources = sources,
                allowMerging = allowMerging,
                confirmedCaptions = confirmed,
                workContext = buildSubtitleWorkContext(itemId),
                scriptContext = buildSubtitleScriptContext(itemId)
            ) { captions ->
                commitTranslationProgress(
                    itemId = itemId,
                    captions = captions,
                    sourceEntities = sourceEntities,
                    generated = allowMerging
                )
            }
        } finally {
            requestBalanceRefresh()
        }
        exportGeneratedSubtitleFile(itemId)
        repository.finishSucceeded(itemId)
    }

internal suspend fun SubtitleTaskService.exportGeneratedSubtitleFile(itemId: String) {
        val item = database.subtitleTaskDao().getItem(itemId) ?: return
        if (item.mode != SubtitleTaskMode.GENERATED) return
        runCatching { generatedSubtitleFileExporter.export(item.trackId) }
            .onSuccess { result ->
                when (result) {
                    is GeneratedSubtitleExportResult.Exported -> {
                        Log.i(SubtitleTaskService.TAG, "自动生成字幕已导出 trackId=${item.trackId} reference=${result.reference}")
                    }
                    is GeneratedSubtitleExportResult.ExistingFilePreserved -> Unit
                    GeneratedSubtitleExportResult.UnsupportedTrackLocation -> {
                        Log.w(SubtitleTaskService.TAG, "音频位置不支持同目录字幕导出 trackId=${item.trackId}")
                        messageManager.showWarning("字幕已生成，但当前音频位置不支持导出 LRC 文件")
                    }
                }
            }
            .onFailure { error ->
                Log.w(SubtitleTaskService.TAG, "自动生成字幕导出失败 trackId=${item.trackId}", error)
                messageManager.showWarning("字幕已生成，但写入音频目录失败：${error.message ?: "未知错误"}")
            }
    }

    /**
     * 手动触发的作品级最终润色入口（由用户在翻译任务列表左滑操作中触发）。
     *
     * 互斥规则：
     * - 该作品当前没有进行中的润色；
     * - 用户已开启"最终润色"；
     * - 该作品下至少有一个已有字幕的音轨；
     * - 该作品下没有进行中的转录或翻译任务。
     *
     * 校验通过后在独立后台 job 中执行润色（非阻塞，失败不影响已有字幕），
     * 并把"润色中"状态通过 [SubtitleTaskRepository.markPolishStarted] 暴露给 UI。
     */
internal suspend fun SubtitleTaskService.startPolishAlbum(albumId: Long) {
        if (!polishingAlbumIds.add(albumId)) return
        var started = false
        try {
            val settings = settingsRepository.loadDeepSeekTranslationSettings()
            if (!settings.finalPolishEnabled) {
                messageManager.showWarning("请先在设置中开启“最终润色”后再执行润色")
                return
            }
            val tracks = database.trackDao().getTracksForAlbumOrderedOnce(albumId)
            if (tracks.isEmpty()) return
            val trackIds = tracks.map(TrackEntity::id)
            val tracksWithSubtitles = database.trackDao().getTrackIdsWithSubtitles(trackIds).toSet()
            val polishableTracks = tracks.filter { it.id in tracksWithSubtitles }
            if (polishableTracks.isEmpty()) {
                messageManager.showWarning("该作品暂无可润色的字幕")
                return
            }
            if (
                database.subtitleTaskDao().getItemsForTracks(trackIds)
                    .any { SubtitleItemState.blocksAlbumPolish(it.state) }
            ) {
                messageManager.showWarning(SubtitleTaskRepository.ACTIVE_TASKS_BLOCK_POLISH_MESSAGE)
                return
            }
            started = true
            repository.markPolishStarted(albumId)
            messageManager.showInfo("已开始润色该作品字幕，完成后会自动更新")
            Log.i(SubtitleTaskService.TAG, "开始作品级手动润色 albumId=$albumId tracks=${polishableTracks.size}")
            serviceScope.launch {
                runCatching { polishAlbum(albumId, polishableTracks) }
                    .onFailure { error ->
                        Log.w(SubtitleTaskService.TAG, "作品级润色失败 albumId=$albumId（不影响已有字幕）", error)
                    }
                    .onSuccess { changedCount ->
                        Log.i(SubtitleTaskService.TAG, "作品级润色完成 albumId=$albumId 修改字幕=$changedCount 条")
                    }
                repository.markPolishFinished(albumId)
                polishingAlbumIds.remove(albumId)
                signalWake()
            }
        } finally {
            if (!started) polishingAlbumIds.remove(albumId)
        }
    }

    /**
     * 为字幕翻译 agent 构建作品级静态元数据（标题日/中、音轨标题日/中、声优、社团）。
     * 只读一次本地库，不依赖其他音轨的翻译进度，各轨并行时也能拿到一致的名称信息。
     */
internal suspend fun SubtitleTaskService.polishAlbum(albumId: Long, tracks: List<TrackEntity>): Int {
        val album = database.albumDao().getAlbumById(albumId) ?: return 0
        // captionId -> 数据库中的原始字幕行（用于精确写回 text）
        val subtitleByCaptionId = HashMap<Long, SubtitleEntity>()
        val polishInputs = tracks.mapIndexedNotNull { index, track ->
            val subtitles = database.trackDao().getSubtitlesForTrack(track.id).sortedWith(SUBTITLE_ORDER)
            if (subtitles.isEmpty()) return@mapIndexedNotNull null
            subtitles.forEach { subtitle -> subtitleByCaptionId[subtitle.id] = subtitle }
            PolishTrackInput(
                trackIndex = index,
                trackTitleJapanese = track.title,
                trackTitleChinese = track.titleForDisplay.takeIf { it != track.title }.orEmpty(),
                captions = subtitles.mapIndexed { captionIndex, subtitle ->
                    PolishCaptionInput(
                        captionId = subtitle.id,
                        sourceIndex = captionIndex,
                        japanese = subtitle.japaneseText,
                        chinese = subtitle.text
                    )
                }
            )
        }
        if (polishInputs.isEmpty()) return 0
        val workContext = SubtitleWorkContext(
            workTitleJapanese = album.title,
            workTitleChinese = album.titleForDisplay.takeIf { it != album.title }.orEmpty(),
            trackTitleJapanese = tracks.firstOrNull()?.title.orEmpty(),
            trackTitleChinese = tracks.firstOrNull()?.titleForDisplay
                ?.takeIf { it != tracks.firstOrNull()?.title }.orEmpty(),
            circle = album.circle,
            cv = album.cv
        )
        val client = requireTranslationClient()
        val results = client.polishSubtitles(
            tracks = polishInputs,
            workContext = workContext
        ) { batch -> commitPolishedCaptions(batch, subtitleByCaptionId) }
        return results.count { result ->
            val original = subtitleByCaptionId[result.captionId]?.text
            original != null && original != result.chinese
        }
    }

    /**
     * 把润色 agent 的逐批结果写回 subtitles 表。只更新 text（中文），不动 japaneseText。
     */
internal suspend fun SubtitleTaskService.commitPolishedCaptions(
        results: List<PolishCaptionResult>,
        subtitleByCaptionId: Map<Long, SubtitleEntity>
    ) {
        if (results.isEmpty()) return
        val updated = results.mapNotNull { result ->
            val existing = subtitleByCaptionId[result.captionId] ?: return@mapNotNull null
            if (existing.text == result.chinese) return@mapNotNull null
            existing.copy(text = result.chinese)
        }
        if (updated.isNotEmpty()) {
            database.trackDao().updateSubtitles(updated)
        }
    }

internal suspend fun SubtitleTaskService.commitTranslationProgress(
        itemId: String,
        captions: List<GeneratedSubtitleCaption>,
        sourceEntities: List<SubtitleTranslationSourceEntity>,
        generated: Boolean
    ) {
        val dao = database.subtitleTaskDao()
        database.withTransaction {
            val item = dao.getItem(itemId) ?: return@withTransaction
            check(item.state == SubtitleItemState.TRANSLATING) { "字幕翻译已暂停或取消" }
            assertTaskControlsCurrentSubtitles(item)
            val existing = dao.getCommittedCaptions(itemId)
            val existingSourceCount = existing.sumOf { it.lastSourceIndex - it.firstSourceIndex + 1 }
            val remainingSources = sourceEntities.drop(existingSourceCount).map { it.toGeneratedSource() }
            val validated = validateSubtitleCaptionBatch(
                captions = captions,
                expectedRemainingSources = remainingSources,
                allowMerging = generated
            )
            val inserted = validated.mapIndexed { offset, caption ->
                SubtitleCommittedCaptionEntity(
                    itemId = itemId,
                    captionIndex = existing.size + offset,
                    firstSourceIndex = caption.sourceIndices.first(),
                    lastSourceIndex = caption.sourceIndices.last(),
                    startMs = caption.startMs,
                    endMs = caption.endMs,
                    correctedJapanese = caption.correctedJapanese,
                    chineseText = caption.chineseText
                )
            }
            dao.insertCommittedCaptions(inserted)
            val allCommitted = existing + inserted
            val confirmedSourceCount = allCommitted.sumOf { it.lastSourceIndex - it.firstSourceIndex + 1 }
            val rebuilt = if (generated) {
                allCommitted.map { caption ->
                    SubtitleEntity(
                        trackId = item.trackId,
                        startMs = caption.startMs,
                        endMs = caption.endMs,
                        text = caption.chineseText,
                        japaneseText = caption.correctedJapanese
                    )
                } + rebuildGeneratedFallbackSuffix(
                    trackId = item.trackId,
                    confirmedSourceCount = confirmedSourceCount,
                    sources = sourceEntities,
                    fallback = dao.getFallbackCaptions(itemId)
                )
            } else {
                val translatedByIndex = allCommitted.associateBy(SubtitleCommittedCaptionEntity::firstSourceIndex)
                dao.getSnapshots(itemId).map { snapshot ->
                    val translated = translatedByIndex[snapshot.captionIndex]
                    SubtitleEntity(
                        trackId = item.trackId,
                        startMs = snapshot.startMs,
                        endMs = snapshot.endMs,
                        text = translated?.chineseText ?: snapshot.text,
                        japaneseText = translated?.correctedJapanese ?: snapshot.text
                    )
                }
            }
            publishRebuiltSubtitles(item.trackId, rebuilt)
            val completed = confirmedSourceCount >= sourceEntities.size
            dao.updateItem(
                item.copy(
                    state = if (completed) SubtitleItemState.SUCCEEDED else SubtitleItemState.TRANSLATING,
                    translationCursor = confirmedSourceCount,
                    translationBatchIndex = 1,
                    translationBatchTotal = 1,
                    attempt = 0,
                    nextAttemptAt = 0L,
                    errorMessage = "",
                    lastPublishedHash = subtitleHash(rebuilt),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

internal suspend fun SubtitleTaskService.assertTaskControlsCurrentSubtitles(item: SubtitleTaskItemEntity) {
        val current = database.trackDao().getSubtitlesForTrack(item.trackId).sortedWith(SUBTITLE_ORDER)
        check(taskStillControlsSubtitles(subtitleHash(current), item.lastPublishedHash)) {
            "字幕在任务期间已被修改，已停止写入"
        }
    }

internal suspend fun SubtitleTaskService.publishRebuiltSubtitles(trackId: Long, subtitles: List<SubtitleEntity>) {
        database.trackDao().deleteSubtitlesForTrack(trackId)
        database.trackDao().insertSubtitles(subtitles.sortedWith(SUBTITLE_ORDER))
    }

internal suspend fun SubtitleTaskService.handleTranslationFailure(itemId: String, error: SubtitleTranslationException) {
        val dao = database.subtitleTaskDao()
        val item = dao.getItem(itemId) ?: return
        if (item.state != SubtitleItemState.TRANSLATING) return
        val attempt = item.attempt + 1
        if (error.retryable && attempt < SubtitleTaskService.MAX_TRANSLATION_ATTEMPTS) {
            val exponential = SubtitleTaskService.BASE_RETRY_DELAY_MS * (1L shl (attempt - 1))
            val delayMs = maxOf(exponential, error.retryAfterMs ?: 0L) + Random.nextLong(0L, SubtitleTaskService.RETRY_JITTER_MS + 1L)
            dao.updateItem(
                item.copy(
                    state = SubtitleItemState.RETRY_WAIT,
                    suspendedFromState = SubtitleItemState.TRANSLATING,
                    attempt = attempt,
                    nextAttemptAt = System.currentTimeMillis() + delayMs,
                    errorMessage = error.message.orEmpty(),
                    updatedAt = System.currentTimeMillis()
                )
            )
            repository.refreshTaskState(item.taskId)
        } else {
            failItem(itemId, error.message.orEmpty().ifBlank { "字幕翻译重试已耗尽" }, attempt)
        }
    }

internal suspend fun SubtitleTaskService.failItem(itemId: String, message: String, attempt: Int? = null) {
        val dao = database.subtitleTaskDao()
        val item = dao.getItem(itemId) ?: return
        if (item.state in setOf(SubtitleItemState.PAUSE_REQUESTED, SubtitleItemState.CANCEL_REQUESTED)) return
        val userMessage = message.trim().ifBlank { "字幕任务失败，请重试。" }
        dao.updateItem(
            item.copy(
                suspendedFromState = item.state,
                state = SubtitleItemState.FAILED,
                attempt = attempt ?: item.attempt,
                errorMessage = userMessage,
                updatedAt = System.currentTimeMillis()
            )
        )
        repository.refreshTaskState(item.taskId)
        if (SubtitleFailureMessages.isUserActionWarning(userMessage)) {
            messageManager.showWarning(userMessage)
        } else {
            messageManager.showError(userMessage)
        }
    }

internal suspend fun SubtitleTaskService.settleCancelledExecution(itemId: String) {
        val dao = database.subtitleTaskDao()
        val item = dao.getItem(itemId) ?: return
        when (item.state) {
            SubtitleItemState.PAUSE_REQUESTED -> {
                dao.updateItem(item.copy(state = SubtitleItemState.PAUSED, updatedAt = System.currentTimeMillis()))
                repository.refreshTaskState(item.taskId)
            }
            SubtitleItemState.CANCEL_REQUESTED -> {
                val warning = repository.finishCancellation(itemId)
                if (!warning.isNullOrBlank()) showWarningNotification(warning)
            }
        }
    }

internal fun SubtitleTaskService.cancelRunningJob(itemId: String) {
        translationJobs[itemId]?.cancel(CancellationException("字幕任务控制请求"))
        if (transcriptionItemId == itemId) {
            transcriptionJob?.cancel(CancellationException("字幕任务控制请求"))
        }
    }

internal fun SubtitleTaskService.isJobRunning(itemId: String): Boolean {
        if (translationJobs[itemId]?.isActive == true) return true
        return transcriptionItemId == itemId && transcriptionJob?.isActive == true
    }

internal suspend fun SubtitleTaskService.pauseAll() {
        database.subtitleTaskDao().getAllItems().map(SubtitleTaskItemEntity::taskId).distinct()
            .forEach { repository.pauseTask(it) }
        signalWake()
    }

internal suspend fun SubtitleTaskService.cancelAll() {
        database.subtitleTaskDao().getAllItems().map(SubtitleTaskItemEntity::taskId).distinct()
            .forEach { repository.cancelTask(it) }
        signalWake()
    }

internal suspend fun SubtitleTaskService.releaseTranscriptionEngineWhenIdle() {
        if (transcriptionJob?.isActive == true) return
        if (database.subtitleTaskDao().getNextTranscription() != null) return
        val engine = transcriptionEngine ?: return
        runCatching { engine.close() }
            .onSuccess { Log.i(SubtitleTaskService.TAG, "字幕转录模型内存已释放") }
            .onFailure { error -> Log.w(SubtitleTaskService.TAG, "释放字幕转录模型失败", error) }
        transcriptionEngine = null
    }

internal fun SubtitleTaskService.releaseFailedTranscriptionEngine() {
        val engine = transcriptionEngine ?: return
        runCatching { engine.close() }
            .onFailure { error -> Log.w(SubtitleTaskService.TAG, "转录失败后释放模型失败", error) }
        transcriptionEngine = null
    }

internal fun SubtitleTaskService.requireTranscriptionEngine(modelId: String): SubtitleTranscriptionEngine {
        transcriptionEngine?.takeIf { it.model.id == modelId }?.let { return it }
        transcriptionEngine?.let { staleEngine ->
            runCatching { staleEngine.close() }
                .onFailure { error -> Log.w(SubtitleTaskService.TAG, "切换转录模型时释放旧模型失败", error) }
        }
        transcriptionEngine = null
        return SubtitleTranscriptionEngineRegistry.factory(applicationContext, modelId)
            .create()
            .also { transcriptionEngine = it }
    }

internal suspend fun SubtitleTaskService.requireTranslationClient(): SubtitleTranslationClient {
        val apiKey = DeepSeekApiKeyStore.get(applicationContext).read()
        check(apiKey.isNotBlank()) { "请先在设置中配置 DeepSeek API Key" }
        deepSeekAccountRepository.bindApiKey(apiKey)
        return SubtitleTranslationClient(
            okHttpClient = deepSeekOkHttpClient,
            gson = gson,
            apiKey = apiKey,
            settings = settingsRepository.loadDeepSeekTranslationSettings(),
            onTokenUsage = { totalTokens ->
                deepSeekAccountRepository.recordTokenUsage(apiKey, totalTokens)
            }
        )
    }

internal fun SubtitleTaskService.requestBalanceRefresh() {
        val jobToStart = synchronized(balanceRefreshLock) {
            balanceRefreshRequested = true
            if (balanceRefreshJob != null) return@synchronized null
            serviceScope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
                while (true) {
                    val shouldRefresh = synchronized(balanceRefreshLock) {
                        if (balanceRefreshRequested) {
                            balanceRefreshRequested = false
                            true
                        } else {
                            balanceRefreshJob = null
                            false
                        }
                    }
                    if (!shouldRefresh) break
                    val apiKey = DeepSeekApiKeyStore.get(applicationContext).read()
                    if (apiKey.isNotBlank()) deepSeekAccountRepository.refreshBalance(apiKey)
                }
                signalWake()
            }.also { balanceRefreshJob = it }
        }
        jobToStart?.start()
    }

private fun Gson.generatedSubtitles(json: String): List<GeneratedSubtitle> {
    val type = object : TypeToken<List<GeneratedSubtitle>>() {}.type
    return fromJson<List<GeneratedSubtitle>>(json, type).orEmpty()
}

private fun SubtitleTranslationSourceEntity.toGeneratedSource(): GeneratedSubtitleSource = GeneratedSubtitleSource(
    index = sourceIndex,
    startMs = startMs,
    endMs = endMs,
    text = text
)

private fun SubtitleCommittedCaptionEntity.toGeneratedCaption(): GeneratedSubtitleCaption = GeneratedSubtitleCaption(
    sourceIndices = (firstSourceIndex..lastSourceIndex).toList(),
    startMs = startMs,
    endMs = endMs,
    correctedJapanese = correctedJapanese,
    chineseText = chineseText
)

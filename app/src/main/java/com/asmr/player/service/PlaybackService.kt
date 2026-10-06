package com.asmr.player.service

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.datasource.cache.CacheDataSource
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.datastore.SettingsDataStore
import com.asmr.player.data.remote.auth.DlsiteAuthStore
import com.asmr.player.data.remote.auth.buildDlsiteCookieHeader
import com.asmr.player.util.NetworkHeaders
import com.asmr.player.data.lyrics.LyricsLoader
import com.asmr.player.data.settings.SettingsRepository
import com.asmr.player.data.settings.AudioEffectController
import com.asmr.player.data.settings.EqualizerSettings
import com.asmr.player.data.settings.PlaybackRuntimeSettings
import com.asmr.player.data.repository.StatisticsRepository
import com.asmr.player.data.repository.ListeningRecordRepository
import com.asmr.player.data.repository.ListeningTrackContext
import com.asmr.player.playback.AsmrRenderersFactory
import com.asmr.player.playback.BalanceAudioProcessor
import com.asmr.player.playback.ChannelModeAudioProcessor
import com.asmr.player.playback.DefaultSpectrumAudioTrackBufferDurationMillis
import com.asmr.player.playback.FadingPlayer
import com.asmr.player.playback.AppVolumeBoostController
import com.asmr.player.playback.PlaybackController
import com.asmr.player.playback.GraphicEqualizerAudioProcessor
import com.asmr.player.playback.PlaybackMediaCache
import com.asmr.player.playback.PlaybackConnectionLifecycle
import com.asmr.player.playback.PlaybackRecoveryPolicy
import com.asmr.player.playback.PlaybackStateStore
import com.asmr.player.playback.RoutingPlaybackDataSource
import com.asmr.player.playback.SceneEffectAudioProcessor
import com.asmr.player.playback.StereoFftAnalyzer
import com.asmr.player.playback.StereoOrbitAudioProcessor
import com.asmr.player.playback.StereoPcmRingBuffer
import com.asmr.player.playback.StereoSpectrumBus
import com.asmr.player.playback.StereoSpectrumTapAudioProcessor
import com.asmr.player.playback.SpectrumOutputBufferSizeProvider
import com.asmr.player.playback.SpectrumPcmRingSlotCount
import com.asmr.player.playback.VolumeThresholdAudioProcessor
import com.asmr.player.playback.VolumeFader
import com.asmr.player.playback.capturePersistedPlaybackState
import com.asmr.player.playback.spectrumVisualDelayMillis
import com.asmr.player.util.SubtitleEntry
import com.asmr.player.util.SubtitleIndexFinder
import dagger.hilt.android.AndroidEntryPoint
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import okhttp3.OkHttpClient

@AndroidEntryPoint
@UnstableApi
class PlaybackService : MediaSessionService() {

    internal var mediaSession: MediaSession? = null
    internal lateinit var exoPlayer: ExoPlayer
    internal lateinit var sessionPlayer: FadingPlayer
    internal lateinit var appVolumeBoostController: AppVolumeBoostController
    internal var startupAppVolumePercent: Int? = null
    internal val graphicEqualizerAudioProcessor = GraphicEqualizerAudioProcessor()
    internal val balanceAudioProcessor = BalanceAudioProcessor()
    internal val stereoOrbitAudioProcessor = StereoOrbitAudioProcessor()
    internal val sceneEffectAudioProcessor = SceneEffectAudioProcessor()
    internal val channelModeAudioProcessor = ChannelModeAudioProcessor()
    internal val volumeThresholdAudioProcessor = VolumeThresholdAudioProcessor()
    internal val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val volumeFader = VolumeFader(serviceScope)
    @Volatile private var spectrumAudioTrackBufferDurationMillis =
        DefaultSpectrumAudioTrackBufferDurationMillis
    @Volatile private var spectrumOutputSampleRate: Int? = null
    @Volatile private var spectrumOutputFramesPerBuffer: Int? = null
    private val spectrumPcmBuffer = StereoPcmRingBuffer(
        frameSize = 1024,
        slotCount = SpectrumPcmRingSlotCount
    )
    private val spectrumAnalyzer = StereoFftAnalyzer(
        pcmBuffer = spectrumPcmBuffer,
        spectrumStore = StereoSpectrumBus.store,
        scope = serviceScope,
        fftSize = 1024,
        binCount = StereoSpectrumBus.DefaultBinCount
    )
    private val spectrumTapAudioProcessor = StereoSpectrumTapAudioProcessor(spectrumPcmBuffer) { sr ->
        spectrumAnalyzer.setSampleRate(sr)
    }
    private val spectrumOutputBufferSizeProvider = SpectrumOutputBufferSizeProvider { durationMillis ->
        spectrumAudioTrackBufferDurationMillis = durationMillis
        updateSpectrumVisualDelay()
    }
    
    // Temporary settings for current session
    internal val sessionSettings = MutableStateFlow<EqualizerSettings?>(null)
    internal var effectApplyJob: Job? = null
    private var sleepTimerJob: Job? = null
    @Volatile internal var lastEffectiveSettings: EqualizerSettings = EqualizerSettings()
    
    internal var currentLyrics: List<SubtitleEntry> = emptyList()
    internal var lyricsIndexFinder: SubtitleIndexFinder? = null
    internal var lastLyricIndex: Int = Int.MIN_VALUE
    internal var floatingLyricsEnabled: Boolean = false
    internal var overlay: FloatingLyricsOverlay? = null
    private var pauseOnOutputDisconnectEnabled: Boolean = true
    private var resumeOnOutputConnectEnabled: Boolean = false
    internal var pauseOnOtherAudioEnabled: Boolean = true
    private var autoPausedByDisconnect: Boolean = false
    private var autoPausedByAudioFocusLoss: Boolean = false
    private var lastOutputEventAtMs: Long = 0L
    internal var audioFocusRequest: AudioFocusRequest? = null
    internal var hasAudioFocus: Boolean = false
    internal var notificationProvider: LyricMediaNotificationProvider? = null
    internal var sfwHideSystemControlsEnabled: Boolean = false
    internal var videoOutputEnabled: Boolean = false

    internal val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> handleAudioFocusGain()
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                handleAudioFocusLoss(resumeWhenFocusReturns = false)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                hasAudioFocus = false
                handleAudioFocusLoss(resumeWhenFocusReturns = true)
            }
        }
    }

    internal val outputBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> handlePotentialOutputDisconnect("becoming_noisy")
                Intent.ACTION_HEADSET_PLUG -> {
                    val state = intent.getIntExtra("state", -1)
                    if (state == 0) {
                        handlePotentialOutputDisconnect("headset_unplugged")
                    } else if (state == 1) {
                        handlePotentialOutputConnect("headset_plugged")
                    }
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) {
                        handlePotentialOutputDisconnect("bluetooth_off")
                    }
                }
            }
        }
    }

    internal val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            if (addedDevices.any { it.isResumeEligibleOutputDevice() }) {
                handlePotentialOutputConnect("device_added")
            }
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            if (removedDevices.any { it.isDisconnectSensitiveOutputDevice() }) {
                handlePotentialOutputDisconnect("device_removed")
            }
        }
    }

    @Inject
    lateinit var audioEffectController: AudioEffectController

    @Inject
    lateinit var lyricsLoader: LyricsLoader

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    @Inject
    lateinit var database: AppDatabase

    @Inject
    lateinit var statisticsRepository: StatisticsRepository

    @Inject
    lateinit var listeningRecordRepository: ListeningRecordRepository

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var playbackStateStore: PlaybackStateStore

    internal var lastMarkedMediaId: String? = null
    internal var lastMarkedElapsedMs: Long = 0L

    private var statsJob: Job? = null
    internal var playbackRecoveryJob: Job? = null
    private var appExitJob: Job? = null
    internal val playbackRecoveryPolicy = PlaybackRecoveryPolicy()
    private var currentTrackListenedMs: Long = 0L
    private var isCurrentTrackCounted: Boolean = false
    private var currentMediaId: String? = null
    internal var lastProgressPersistElapsedMs: Long = 0L
    internal val pendingNetworkTrafficBytes = AtomicLong(0L)

    private fun updateSpectrumVisualDelay() {
        spectrumAnalyzer.setVisualDelayMs(
            spectrumVisualDelayMillis(
                audioTrackBufferDurationMillis = spectrumAudioTrackBufferDurationMillis,
                outputSampleRate = spectrumOutputSampleRate,
                outputFramesPerBuffer = spectrumOutputFramesPerBuffer
            )
        )
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        appVolumeBoostController =
            AppVolumeBoostController(getSystemService(AUDIO_SERVICE) as AudioManager)
        val runtimeSettings = runBlocking {
            // 主线程最多等 2s（正常为内存级 DataStore 读）；超时回退默认设置，避免无限 ANR
            runCatching {
                withTimeout(2_000L) { settingsRepository.loadPlaybackRuntimeSettings() }
            }.getOrDefault(PlaybackRuntimeSettings())
        }
        applyPlaybackRuntimeSettings(runtimeSettings)
        val currentAppVolumePercent = appVolumeBoostController.currentVolumePercent()
        startupAppVolumePercent = currentAppVolumePercent
        val startupAppVolumeSyncJob = serviceScope.launch(Dispatchers.IO) {
            settingsRepository.syncAppVolumePercentFromSystem(currentAppVolumePercent)
        }
        runCatching {
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            spectrumOutputSampleRate = audioManager
                .getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
                ?.toIntOrNull()
            spectrumOutputFramesPerBuffer = audioManager
                .getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
                ?.toIntOrNull()
        }
        updateSpectrumVisualDelay()
        val authStore = DlsiteAuthStore(applicationContext)
        val playbackHttpClient = okHttpClient.newBuilder()
            .connectTimeout(NETWORK_CONNECT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(NETWORK_READ_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .build()
        val httpFactory = OkHttpDataSource.Factory(playbackHttpClient)
            .setUserAgent(DLSITE_UA)
        
        val transferListener = object : TransferListener {
            override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
            override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
            override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
                if (isNetwork && bytesTransferred > 0) {
                    // 该回调位于加载线程且调用非常频繁。只做无锁累加，由后台统计心跳
                    // 批量入库，避免每个网络缓冲都唤醒应用主线程并启动两次数据库切换。
                    pendingNetworkTrafficBytes.addAndGet(bytesTransferred.toLong())
                }
            }
            override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
        }

        val upstreamDataSourceFactory = DefaultDataSource.Factory(
            this,
            ResolvingDataSource.Factory(httpFactory) { dataSpec ->
                val uri = dataSpec.uri
                val host = uri.host.orEmpty().lowercase()
                if (
                    host.endsWith("dlsite.com") ||
                    host.endsWith("chobit.cc") ||
                    host.endsWith("dlsite.com")
                ) {
                    val headers = LinkedHashMap(dataSpec.httpRequestHeaders)
                    headers["User-Agent"] = DLSITE_UA
                    headers["Accept-Language"] = NetworkHeaders.ACCEPT_LANGUAGE
                    headers["Referer"] = "https://www.dlsite.com/"
                    val cookie = buildDlsiteCookieHeader(authStore.getDlsiteCookie())
                    if (cookie.isNotBlank() && !headers.containsKey("Cookie")) {
                        headers["Cookie"] = cookie
                    }
                    dataSpec.buildUpon().setHttpRequestHeaders(headers).build()
                } else {
                    dataSpec
                }
            }
        ).apply {
            setTransferListener(transferListener)
        }

        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(PlaybackMediaCache.getInstance(applicationContext))
            .setCacheReadDataSourceFactory(FileDataSource.Factory())
            .setUpstreamDataSourceFactory(upstreamDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val dataSourceFactory = RoutingPlaybackDataSource.Factory(
            upstreamFactory = upstreamDataSourceFactory,
            cachedFactory = cacheDataSourceFactory
        )
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
            .setLoadErrorHandlingPolicy(
                DefaultLoadErrorHandlingPolicy(NETWORK_MINIMUM_LOADABLE_RETRY_COUNT)
            )

        exoPlayer = ExoPlayer.Builder(this)
            .setRenderersFactory(
                AsmrRenderersFactory(
                    this,
                    graphicEqualizerAudioProcessor,
                    balanceAudioProcessor,
                    stereoOrbitAudioProcessor,
                    sceneEffectAudioProcessor,
                    channelModeAudioProcessor,
                    volumeThresholdAudioProcessor,
                    spectrumTapAudioProcessor,
                    spectrumOutputBufferSizeProvider
                )
            )
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                false
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        applyVideoOutputEnabled()

        spectrumAnalyzer.start()

        sessionPlayer = FadingPlayer(
            delegate = exoPlayer,
            volumeFader = volumeFader,
            playFadeMs = runtimeSettings.playFadeInMs.toLong(),
            pauseFadeMs = runtimeSettings.pauseFadeOutMs.toLong(),
            switchFadeOutMs = 250L,
            switchFadeInMs = 250L,
            onPlayRequested = { requestPlaybackAudioFocus() }
        )
        registerPlaybackRouteListeners()
        startEffectLoops(startupAppVolumeSyncJob)
        mediaSession = buildMediaSession()

        notificationProvider = LyricMediaNotificationProvider(
            context = this,
            initialHideSystemControls = sfwHideSystemControlsEnabled
        )
        setMediaNotificationProvider(notificationProvider!!)
        overlay = FloatingLyricsOverlay(this) { settings ->
            serviceScope.launch {
                settingsRepository.updateFloatingLyricsSettings(settings)
            }
        }
        
        exoPlayer.addListener(object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                StereoSpectrumBus.playbackActive = isPlaying
                if (isPlaying) {
                    markCurrentAlbumPlayed()
                } else {
                    if (
                        !autoPausedByAudioFocusLoss &&
                        (!exoPlayer.playWhenReady || exoPlayer.playbackState == Player.STATE_ENDED)
                    ) {
                        abandonPlaybackAudioFocus()
                    }
                    serviceScope.launch { persistCurrentTrackProgressIfNeeded(force = true) }
                    serviceScope.launch { listeningRecordRepository.flush() }
                }
                refreshMediaNotification()
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                cancelPlaybackRecovery(resetPolicy = true)
                serviceScope.launch { updateArtworkForCurrentMedia() }
                serviceScope.launch { loadLyricsForCurrentMedia() }
                applyVideoOutputEnabled()
                if (exoPlayer.isPlaying) {
                    markCurrentAlbumPlayed()
                }
                
                // Reset track stats for new item
                volumeThresholdAudioProcessor.resetForNewItem()
                currentMediaId = mediaItem?.mediaId
                currentTrackListenedMs = 0L
                isCurrentTrackCounted = false
                lastProgressPersistElapsedMs = 0L
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> cancelPlaybackRecovery(resetPolicy = true)
                    Player.STATE_ENDED -> abandonPlaybackAudioFocus()
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady) {
                    cancelPlaybackRecovery(resetPolicy = true)
                    if (!autoPausedByAudioFocusLoss) {
                        abandonPlaybackAudioFocus()
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                schedulePlaybackRecovery(error)
            }

            override fun onEvents(player: Player, events: Player.Events) {
                if (
                    events.contains(Player.EVENT_AVAILABLE_COMMANDS_CHANGED) ||
                    events.contains(Player.EVENT_TIMELINE_CHANGED) ||
                    events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)
                ) {
                    syncMediaNotificationControllerState()
                    refreshMediaNotification()
                }
            }
        })
        StereoSpectrumBus.playbackActive = exoPlayer.isPlaying

        statsJob = serviceScope.launch(Dispatchers.Default) {
            while (isActive) {
                val tick = withContext(Dispatchers.Main.immediate) {
                    if (!exoPlayer.isPlaying) {
                        null
                    } else {
                        currentTrackListenedMs += 1000L
                        val totalDuration = exoPlayer.duration
                        val shouldIncrementTrackCount =
                            !isCurrentTrackCounted &&
                                totalDuration > 0L &&
                                currentTrackListenedMs > totalDuration * 0.25
                        if (shouldIncrementTrackCount) {
                            isCurrentTrackCounted = true
                        }
                        PlaybackStatsTick(
                            trackContext = currentListeningTrackContext(),
                            incrementTrackCount = shouldIncrementTrackCount,
                        )
                    }
                }

                if (tick != null) {
                    statisticsRepository.addListeningDuration(1000L)

                    // 会话级记录：把这一秒计入当前作品的收听会话。
                    if (tick.trackContext != null) {
                        listeningRecordRepository.recordTick(tick.trackContext, 1000L)
                    }

                    if (tick.incrementTrackCount) {
                        statisticsRepository.incrementTrackCount()
                        listeningRecordRepository.incrementTrackCount()
                    }
                    persistCurrentTrackProgressIfNeeded(force = false)
                }
                flushPendingNetworkTraffic()
                delay(1000L)
            }
        }

        serviceScope.launch {
            settingsRepository.floatingLyricsEnabled.collect { enabled ->
                floatingLyricsEnabled = enabled
                if (!enabled) {
                    overlay?.hide()
                } else {
                    lastLyricIndex = Int.MIN_VALUE
                }
            }
        }
        serviceScope.launch {
            combine(
                settingsRepository.floatingLyricsSettings.distinctUntilChanged(),
                settingsDataStore.nowPlayingLyricsSettings.distinctUntilChanged()
            ) { settings, lyrics -> settings to lyrics.multilineEnabled }
                .distinctUntilChanged()
                .collect { (settings, multilineEnabled) ->
                    overlay?.applySettings(settings, multilineEnabled)
                }
        }
        serviceScope.launch {
            settingsRepository.pauseOnOutputDisconnect.collectLatest { enabled ->
                pauseOnOutputDisconnectEnabled = enabled
                if (!enabled) autoPausedByDisconnect = false
            }
        }
        serviceScope.launch {
            settingsRepository.resumeOnOutputConnect.collectLatest { enabled ->
                resumeOnOutputConnectEnabled = enabled
            }
        }
        serviceScope.launch {
            settingsRepository.pauseOnOtherAudio.collectLatest { enabled ->
                pauseOnOtherAudioEnabled = enabled
                if (!enabled) {
                    val shouldResume = autoPausedByAudioFocusLoss
                    autoPausedByAudioFocusLoss = false
                    abandonPlaybackAudioFocus()
                    if (shouldResume) {
                        runCatching { sessionPlayer.play() }
                    }
                } else if (exoPlayer.isPlaying && !hasAudioFocus) {
                    requestPlaybackAudioFocus()
                }
            }
        }
        serviceScope.launch {
            combine(
                settingsRepository.playFadeInMs,
                settingsRepository.pauseFadeOutMs
            ) { fadeInMs, fadeOutMs ->
                fadeInMs to fadeOutMs
            }.collectLatest { (fadeInMs, fadeOutMs) ->
                sessionPlayer.setFadeDurations(
                    playFadeMs = fadeInMs.toLong(),
                    pauseFadeMs = fadeOutMs.toLong()
                )
            }
        }
        serviceScope.launch {
            settingsRepository.sfwHideSystemControls.collectLatest { hide ->
                val stateChanged = sfwHideSystemControlsEnabled != hide
                sfwHideSystemControlsEnabled = hide
                notificationProvider?.setHideSystemControls(hide)
                if (stateChanged) {
                    refreshMediaNotificationControllerRegistration()
                }
                syncMediaNotificationControllerState()
                refreshMediaNotification()
            }
        }
        serviceScope.launch {
            settingsRepository.sleepTimerEndAtMs.collect { endAtMs ->
                sleepTimerJob?.cancel()
                sleepTimerJob = null

                if (endAtMs <= 0L) return@collect
                val delayMs = endAtMs - System.currentTimeMillis()
                if (delayMs <= 0L) {
                    settingsRepository.clearSleepTimer()
                    return@collect
                }

                sleepTimerJob = serviceScope.launch {
                    delay(delayMs)
                    withContext(Dispatchers.Main.immediate) {
                        exoPlayer.pause()
                    }
                    settingsRepository.clearSleepTimer()
                }
            }
        }
        serviceScope.launch(Dispatchers.Default) {
            loadLyricsForCurrentMedia()
            updateArtworkForCurrentMedia()
            while (isActive) {
                val nextDelayMs = updateLyricsTick()
                delay(nextDelayMs)
            }
        }
    }

    private fun handleAudioFocusGain() {
        hasAudioFocus = true
        if (!autoPausedByAudioFocusLoss) return
        autoPausedByAudioFocusLoss = false
        if (!pauseOnOtherAudioEnabled) return
        if (exoPlayer.mediaItemCount == 0 || exoPlayer.playbackState == Player.STATE_ENDED) return

        Log.d("PlaybackService", "Resume after transient audio focus loss")
        serviceScope.launch(Dispatchers.Main.immediate) {
            runCatching { sessionPlayer.play() }
                .onFailure { Log.w("PlaybackService", "Failed to resume after audio focus gain", it) }
        }
    }

    private fun handleAudioFocusLoss(resumeWhenFocusReturns: Boolean) {
        if (!pauseOnOtherAudioEnabled) return
        if (!exoPlayer.playWhenReady) return
        autoPausedByAudioFocusLoss = resumeWhenFocusReturns
        Log.d("PlaybackService", "Auto pause due to audio focus loss")
        serviceScope.launch(Dispatchers.Main.immediate) {
            sessionPlayer.pause()
        }
    }

    private fun handlePotentialOutputDisconnect(reason: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastOutputEventAtMs < OUTPUT_EVENT_DEBOUNCE_MS) return
        lastOutputEventAtMs = now
        if (!pauseOnOutputDisconnectEnabled) return
        if (!exoPlayer.isPlaying) return
        autoPausedByDisconnect = true
        Log.d("PlaybackService", "Auto pause due to output disconnect: $reason")
        serviceScope.launch(Dispatchers.Main.immediate) {
            sessionPlayer.pause()
        }
    }

    private fun handlePotentialOutputConnect(reason: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastOutputEventAtMs < OUTPUT_EVENT_DEBOUNCE_MS) return
        lastOutputEventAtMs = now
        if (!resumeOnOutputConnectEnabled) return
        if (!hasResumeEligibleOutputDevice()) return
        if (exoPlayer.isPlaying) return
        Log.d("PlaybackService", "Auto resume due to output connect: $reason")
        serviceScope.launch(Dispatchers.Main.immediate) {
            runCatching { sessionPlayer.play() }
            autoPausedByDisconnect = false
            autoPausedByAudioFocusLoss = false
        }
    }

    private fun applyPlaybackRuntimeSettings(settings: PlaybackRuntimeSettings) {
        floatingLyricsEnabled = settings.floatingLyricsEnabled
        pauseOnOutputDisconnectEnabled = settings.pauseOnOutputDisconnect
        resumeOnOutputConnectEnabled = settings.resumeOnOutputConnect
        pauseOnOtherAudioEnabled = settings.pauseOnOtherAudio
        sfwHideSystemControlsEnabled = settings.sfwHideSystemControls
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_FOR_APP_EXIT) {
            shutdownForExplicitAppExit()
            return START_NOT_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlay?.onConfigurationChanged()
    }

    override fun onDestroy() {
        overlay?.hide()
        statsJob?.cancel()
        runBlocking(Dispatchers.IO) {
            // 退出落盘限时 3s：超时放弃本次流量统计 flush，避免 onDestroy 无限阻塞
            runCatching { withTimeout(3_000L) { flushPendingNetworkTraffic() } }
        }
        cancelPlaybackRecovery(resetPolicy = true)
        effectApplyJob?.cancel()
        sleepTimerJob?.cancel()
        unregisterPlaybackRouteListeners()
        abandonPlaybackAudioFocus()
        appVolumeBoostController.release()
        spectrumAnalyzer.stop()
        releaseMediaSession()
        notificationProvider = null
        runCatching { PlaybackMediaCache.release() }
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        shutdownForExplicitAppExit()
    }

    private fun shutdownForExplicitAppExit() {
        if (appExitJob?.isActive == true) return
        // 先阻止控制器重连，再保存暂停后的状态；释放会话只移除系统媒体组件，不清空队列。
        PlaybackConnectionLifecycle.markAppExit()
        val state = mediaSession?.player?.let { player ->
            player.playWhenReady = false
            capturePersistedPlaybackState(player)
        }
        appExitJob = serviceScope.launch {
            if (state != null) {
                runCatching { playbackStateStore.save(state) }
                    .onFailure { error ->
                        Log.e("PlaybackService", "保存退出时播放状态失败", error)
                    }
            }
            releaseMediaSession()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    companion object {
        private const val ACTION_STOP_FOR_APP_EXIT =
            "com.asmr.player.action.STOP_PLAYBACK_FOR_APP_EXIT"

        internal fun requestShutdownForAppExit(context: Context) {
            context.startService(
                Intent(context, PlaybackService::class.java).setAction(ACTION_STOP_FOR_APP_EXIT)
            )
        }

        private const val DLSITE_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        internal const val LYRICS_CHANNEL_ID = "playback"
        internal const val MEDIA_NOTIFICATION_CONTROLLER_HINT =
            "androidx.media3.session.MediaNotificationManager"
        private const val OUTPUT_EVENT_DEBOUNCE_MS = 1200L
        private const val NETWORK_CONNECT_TIMEOUT_MS = 15_000
        private const val NETWORK_READ_TIMEOUT_MS = 30_000
        private const val NETWORK_MINIMUM_LOADABLE_RETRY_COUNT = 6
    }
}

private data class PlaybackStatsTick(
    val trackContext: ListeningTrackContext?,
    val incrementTrackCount: Boolean,
)

/**
 * [PlaybackController] 的 service 侧实现：把 Media3 会话服务的组件名交给 playback 层，
 * 使其不再直接引用 [PlaybackService] 类型（依赖方向 service→playback）。
 */
internal class PlaybackServiceController @Inject constructor() : PlaybackController {
    override fun sessionServiceComponent(context: Context): ComponentName =
        ComponentName(context, PlaybackService::class.java)
}

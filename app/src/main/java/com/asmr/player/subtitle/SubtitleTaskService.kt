package com.asmr.player.subtitle

import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.AppDatabaseProvider
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.ConcurrentHashMap
import android.net.ConnectivityManager
import android.content.Context
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import com.asmr.player.util.DEEPSEEK_HTTP_CLIENT
import kotlinx.coroutines.Dispatchers
import dagger.hilt.EntryPoint
import dagger.hilt.android.EntryPointAccessors
import com.google.gson.Gson
import android.os.IBinder
import dagger.hilt.InstallIn
import android.content.Intent
import kotlinx.coroutines.Job
import com.asmr.player.util.MessageManager
import javax.inject.Named
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.NonCancellable
import okhttp3.OkHttpClient
import android.os.PowerManager
import android.app.Service
import com.asmr.player.data.settings.SettingsRepository
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal class SubtitleTaskService : Service() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ServiceEntryPoint {
        @Named(DEEPSEEK_HTTP_CLIENT)
        fun deepSeekOkHttpClient(): OkHttpClient
        fun gson(): Gson
        fun settingsRepository(): SettingsRepository
        fun messageManager(): MessageManager
        fun deepSeekAccountRepository(): DeepSeekAccountRepository
    }

    internal val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    internal val wakeSignals = Channel<Unit>(Channel.CONFLATED)
    internal lateinit var database: AppDatabase
    internal lateinit var repository: SubtitleTaskRepository
    internal lateinit var connectivityManager: ConnectivityManager
    internal lateinit var gson: Gson
    internal lateinit var deepSeekOkHttpClient: OkHttpClient
    internal lateinit var settingsRepository: SettingsRepository
    internal lateinit var messageManager: MessageManager
    internal lateinit var deepSeekAccountRepository: DeepSeekAccountRepository
    internal lateinit var generatedSubtitleFileExporter: GeneratedSubtitleFileExporter
    internal var transcriptionJob: Job? = null
    internal var transcriptionItemId: String? = null
    internal val translationJobs = ConcurrentHashMap<String, Job>()
    internal val titleTranslationJobs = ConcurrentHashMap<String, Job>()
    internal val titleTranslationRetryAt = ConcurrentHashMap<String, Long>()
    internal val polishingAlbumIds = ConcurrentHashMap.newKeySet<Long>()
    internal val balanceRefreshLock = Any()
    internal var balanceRefreshRequested = false
    internal var balanceRefreshJob: Job? = null
    internal var transcriptionEngine: SubtitleTranscriptionEngine? = null
    internal var stoppingSafely = false
    private var wakeLock: PowerManager.WakeLock? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = signalWake()
        override fun onLost(network: Network) = signalWake()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = signalWake()
    }

    override fun onCreate() {
        super.onCreate()
        database = AppDatabaseProvider.get(applicationContext)
        repository = SubtitleTaskRepository.get(applicationContext)
        connectivityManager = getSystemService(ConnectivityManager::class.java)
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            ServiceEntryPoint::class.java
        )
        gson = entryPoint.gson()
        deepSeekOkHttpClient = entryPoint.deepSeekOkHttpClient()
        settingsRepository = entryPoint.settingsRepository()
        messageManager = entryPoint.messageManager()
        deepSeekAccountRepository = entryPoint.deepSeekAccountRepository()
        generatedSubtitleFileExporter = GeneratedSubtitleFileExporter(applicationContext, database)
        createNotificationChannel()
        startAsForeground(buildNotification(emptyList()))
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:SubtitleTasks")
            .apply {
                setReferenceCounted(false)
                acquire()
            }
        runCatching { connectivityManager.registerDefaultNetworkCallback(networkCallback) }
        serviceScope.launch { schedulerLoop() }
        signalWake()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE_ALL -> serviceScope.launch { pauseAll() }
            ACTION_CANCEL_ALL -> serviceScope.launch { cancelAll() }
            ACTION_POLISH_ALBUM -> {
                val albumId = intent.getLongExtra(EXTRA_ALBUM_ID, -1L)
                if (albumId > 0L) {
                    serviceScope.launch { startPolishAlbum(albumId) }
                }
            }
            else -> signalWake()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        interruptAndStop("应用已从后台清除，字幕任务已中断")
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
        wakeLock?.takeIf(PowerManager.WakeLock::isHeld)?.release()
        wakeLock = null
        runCatching { transcriptionEngine?.close() }
        transcriptionEngine = null
        // 润色 job 随 serviceScope 一并取消，清空内存中的“润色中”标记，避免 UI 残留禁用
        repository.clearPolishState()
        polishingAlbumIds.clear()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        interruptAndStop("系统前台服务时限已到，字幕任务已安全中断")
    }

    internal fun interruptAndStop(message: String) {
        if (stoppingSafely) return
        stoppingSafely = true
        serviceScope.launch(NonCancellable + Dispatchers.IO) {
            val now = System.currentTimeMillis()
            database.subtitleTaskDao().interruptAllIncompleteItems(now, message)
            database.subtitleTaskDao().markInterruptedTasks(now)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    internal fun signalWake() {
        wakeSignals.trySend(Unit)
    }

    companion object {
        internal val TRANSLATION_QUEUE_STATES = setOf(
            SubtitleItemState.QUEUED_TRANSLATION,
            SubtitleItemState.WAITING_SLOT,
            SubtitleItemState.WAITING_NETWORK,
            SubtitleItemState.RETRY_WAIT
        )
        internal val TITLE_TRANSLATION_BLOCKED_STATES = setOf(
            SubtitleItemState.CANCEL_REQUESTED,
            SubtitleItemState.CANCELED
        )
        internal const val ACTION_WAKE = "com.asmr.player.subtitle.WAKE"
        internal const val ACTION_PAUSE_ALL = "com.asmr.player.subtitle.PAUSE_ALL"
        internal const val ACTION_CANCEL_ALL = "com.asmr.player.subtitle.CANCEL_ALL"
        internal const val ACTION_POLISH_ALBUM = "com.asmr.player.subtitle.POLISH_ALBUM"
        internal const val EXTRA_ALBUM_ID = "album_id"
        internal const val NOTIFICATION_CHANNEL_ID = "subtitle_tasks"
        internal const val NOTIFICATION_ID = 7412
        internal const val WARNING_NOTIFICATION_ID = 7413
        internal const val MAX_TRANSLATION_ATTEMPTS = 4
        internal const val MAX_SCRIPT_FILE_CHARS = 200_000
        internal const val MAX_SCRIPT_FILE_BYTES = 20L * 1024L * 1024L
        internal const val BASE_RETRY_DELAY_MS = 1_000L
        internal const val RETRY_JITTER_MS = 250L
        internal const val SCHEDULER_TICK_MS = 500L
        internal const val TITLE_TRANSLATION_RETRY_BACKOFF_MS = 60_000L
        internal const val TITLE_TRANSLATION_DISABLED_RETRY_AT = Long.MAX_VALUE
        internal const val TAG = "SubtitleTaskService"

        fun wake(context: Context) {
            ContextCompat.startForegroundService(
                context.applicationContext,
                Intent(context.applicationContext, SubtitleTaskService::class.java).setAction(ACTION_WAKE)
            )
        }

        fun requestPolishAlbum(context: Context, albumId: Long) {
            ContextCompat.startForegroundService(
                context.applicationContext,
                Intent(context.applicationContext, SubtitleTaskService::class.java)
                    .setAction(ACTION_POLISH_ALBUM)
                    .putExtra(EXTRA_ALBUM_ID, albumId)
            )
        }
    }
}

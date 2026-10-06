package com.asmr.player.subtitle

import android.app.Service
import android.os.Build
import android.content.Intent
import kotlinx.coroutines.Job
import android.net.NetworkCapabilities
import android.app.Notification
import android.app.NotificationChannel
import androidx.core.app.NotificationCompat
import android.app.NotificationManager
import android.app.PendingIntent
import com.asmr.player.R
import android.content.pm.ServiceInfo
import com.asmr.player.data.local.db.entities.SubtitleTaskItemEntity
import com.asmr.player.util.DEEPSEEK_TRANSLATION_CONCURRENCY
import kotlinx.coroutines.isActive

internal suspend fun SubtitleTaskService.updateForegroundNotification() {
        val items = database.subtitleTaskDao().getAllItems()
        getSystemService(NotificationManager::class.java).notify(SubtitleTaskService.NOTIFICATION_ID, buildNotification(items))
    }

internal suspend fun SubtitleTaskService.stopWhenIdle() {
        if (transcriptionJob?.isActive == true || translationJobs.values.any(Job::isActive)) return
        if (titleTranslationJobs.values.any(Job::isActive)) return
        if (polishingAlbumIds.isNotEmpty()) return
        if (balanceRefreshJob != null) return
        if (database.subtitleTaskDao().countRunnableItems() > 0) return
        val pendingTitleTaskIds = database.subtitleTitleOwnerDao().getPendingTaskIds()
        if (pendingTitleTaskIds.any { titleTranslationRetryAt[it] != SubtitleTaskService.TITLE_TRANSLATION_DISABLED_RETRY_AT }) return
        stoppingSafely = true
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

internal fun SubtitleTaskService.buildNotification(items: List<SubtitleTaskItemEntity>): Notification {
        val transcribing = items.firstOrNull { it.state == SubtitleItemState.TRANSCRIBING }
        val translating = items.count { it.state == SubtitleItemState.TRANSLATING }
        val waitingNetwork = items.count { it.state == SubtitleItemState.WAITING_NETWORK }
        val waitingSlot = items.count { it.state == SubtitleItemState.WAITING_SLOT }
        val parts = buildList {
            transcribing?.let { add("转录：${it.trackTitle} ${it.transcriptionProgress}%") }
            if (translating > 0) add("翻译中 $translating/$DEEPSEEK_TRANSLATION_CONCURRENCY")
            if (waitingSlot > 0) add("等待槽位 $waitingSlot")
            if (waitingNetwork > 0) add("等待网络 $waitingNetwork")
            if (isEmpty()) add("正在检查字幕队列")
        }
        return NotificationCompat.Builder(this, SubtitleTaskService.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_playback)
            .setContentTitle("字幕任务")
            .setContentText(parts.joinToString(" · "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(parts.joinToString(" · ")))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppPendingIntent())
            .addAction(0, "打开", openAppPendingIntent())
            .addAction(0, "暂停全部", servicePendingIntent(SubtitleTaskService.ACTION_PAUSE_ALL, 1))
            .addAction(0, "取消全部", servicePendingIntent(SubtitleTaskService.ACTION_CANCEL_ALL, 2))
            .build()
    }

internal fun SubtitleTaskService.startAsForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(SubtitleTaskService.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(SubtitleTaskService.NOTIFICATION_ID, notification)
        }
    }

internal fun SubtitleTaskService.createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(SubtitleTaskService.NOTIFICATION_CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(SubtitleTaskService.NOTIFICATION_CHANNEL_ID, "字幕任务", NotificationManager.IMPORTANCE_LOW)
        )
    }

internal fun SubtitleTaskService.showWarningNotification(message: String) {
        val notification = NotificationCompat.Builder(this, SubtitleTaskService.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_playback)
            .setContentTitle("字幕版本冲突")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent())
            .build()
        getSystemService(NotificationManager::class.java).notify(SubtitleTaskService.WARNING_NOTIFICATION_ID, notification)
    }

internal fun SubtitleTaskService.openAppPendingIntent(): PendingIntent {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(Intent.ACTION_MAIN).setPackage(packageName).addCategory(Intent.CATEGORY_LAUNCHER)
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

internal fun SubtitleTaskService.servicePendingIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        this,
        requestCode,
        Intent(this, SubtitleTaskService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

internal fun SubtitleTaskService.isNetworkAvailable(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }


package com.asmr.player.main

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.asmr.player.ui.settings.AppUpdateState
import com.asmr.player.ui.settings.SettingsViewModel
import com.asmr.player.ui.settings.UpdateCheckSource
import com.asmr.player.ui.update.AppUpdateInstallResult
import com.asmr.player.ui.update.launchDownloadedApkInstall
import com.asmr.player.util.MessageManager

/**
 * MainContainer 的自动更新副作用簇：启动时自动检查更新、下载完成后自动拉起安装、
 * 权限授予返回 ON_RESUME 后续装。pendingAutomaticInstallPath 仅在本簇内使用。
 */
@Composable
internal fun AutomaticUpdateEffects(
    settingsViewModel: SettingsViewModel,
    messageManager: MessageManager,
    context: Context,
    updateState: AppUpdateState,
    automaticUpdateInstallRequested: Boolean,
    setAutomaticUpdateInstallRequested: (Boolean) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var pendingAutomaticInstallPath by rememberSaveable { mutableStateOf<String?>(null) }

    fun handleAutomaticInstallResult(result: AppUpdateInstallResult, apkPath: String) {
        when (result) {
            AppUpdateInstallResult.Started -> {
                pendingAutomaticInstallPath = null
                messageManager.showInfo("正在打开系统安装器")
            }
            AppUpdateInstallResult.PermissionRequired -> {
                pendingAutomaticInstallPath = apkPath
                messageManager.showInfo("请允许 Eara Player 安装未知来源应用后继续安装")
            }
            AppUpdateInstallResult.FileInvalid -> {
                pendingAutomaticInstallPath = null
                messageManager.showError("下载文件无效，请重新下载")
            }
            is AppUpdateInstallResult.Failed -> {
                pendingAutomaticInstallPath = null
                messageManager.showError(result.message)
            }
        }
    }

    DisposableEffect(lifecycleOwner, pendingAutomaticInstallPath, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            val apkPath = pendingAutomaticInstallPath ?: return@LifecycleEventObserver
            val canInstall = Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                context.packageManager.canRequestPackageInstalls()
            if (!canInstall) return@LifecycleEventObserver
            handleAutomaticInstallResult(
                result = launchDownloadedApkInstall(context, apkPath),
                apkPath = apkPath
            )
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(settingsViewModel) {
        settingsViewModel.checkUpdateAutomatically()
    }

    LaunchedEffect(updateState, automaticUpdateInstallRequested) {
        if (!automaticUpdateInstallRequested) return@LaunchedEffect
        when (val state = updateState) {
            is AppUpdateState.ReadyToInstall -> {
                if (state.source != UpdateCheckSource.Automatic) return@LaunchedEffect
                setAutomaticUpdateInstallRequested(false)
                handleAutomaticInstallResult(
                    result = launchDownloadedApkInstall(context, state.apkPath),
                    apkPath = state.apkPath
                )
            }
            is AppUpdateState.Failed -> {
                if (state.source != UpdateCheckSource.Automatic) return@LaunchedEffect
                setAutomaticUpdateInstallRequested(false)
                messageManager.showError(state.message)
            }
            else -> Unit
        }
    }
}

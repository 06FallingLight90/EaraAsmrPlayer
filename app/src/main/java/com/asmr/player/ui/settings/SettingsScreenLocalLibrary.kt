package com.asmr.player.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asmr.player.data.download.DownloadDestination
import com.asmr.player.ui.library.BulkPhase
import com.asmr.player.ui.library.BulkProgress
import com.asmr.player.ui.library.LibraryViewModel
import com.asmr.player.ui.theme.AsmrColorScheme
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.util.documentTreeDisplayPath
import java.io.File

@Composable
internal fun SettingsLocalLibrarySection(
    scanRoots: List<String>,
    downloadDestination: DownloadDestination,
    bulkProgress: BulkProgress?,
    isGlobalSyncRunning: Boolean,
    viewModel: SettingsViewModel,
    libraryViewModel: LibraryViewModel,
    pickRootLauncher: ActivityResultLauncher<Uri?>,
    pickDownloadRootLauncher: ActivityResultLauncher<Uri?>,
    pendingDownloadDestinationState: MutableState<DownloadDestination?>,
    pendingRemoveRootState: MutableState<String?>,
    colorScheme: AsmrColorScheme,
    context: Context,
) {
    var pendingDownloadDestination by pendingDownloadDestinationState
    var pendingRemoveRoot by pendingRemoveRootState
                val isDark = AsmrTheme.colorScheme.isDark
                val buttonColors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = colorScheme.primarySoft,
                    contentColor = if (isDark) colorScheme.onPrimaryContainer else colorScheme.primaryStrong
                )

                Text("下载目录", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colorScheme.surface.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(downloadDestination.label, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = downloadDestination.displayPath,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.textSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    viewModel.requestDownloadDirectoryChange {
                                        pickDownloadRootLauncher.launch(null)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = buttonColors,
                                enabled = !isGlobalSyncRunning,
                            ) {
                                Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = buttonColors.contentColor)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("选择目录")
                            }
                            TextButton(
                                onClick = {
                                    viewModel.requestDownloadDirectoryChange {
                                        pendingDownloadDestination = DownloadDestination.Default(
                                            root = File(context.getExternalFilesDir(null), "albums").absolutePath,
                                        )
                                    }
                                },
                                enabled = downloadDestination is DownloadDestination.DocumentTree && !isGlobalSyncRunning,
                            ) {
                                Text("重置默认")
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilledTonalButton(
                        onClick = { libraryViewModel.scanAllRoots() },
                        modifier = Modifier.weight(1f),
                        colors = buttonColors,
                        enabled = !isGlobalSyncRunning
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, tint = buttonColors.contentColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("刷新本地")
                    }
                    FilledTonalButton(
                        onClick = { libraryViewModel.syncMetadata() },
                        modifier = Modifier.weight(1f),
                        colors = buttonColors,
                        enabled = !isGlobalSyncRunning
                    ) {
                        Icon(Icons.Rounded.CloudSync, contentDescription = null, tint = buttonColors.contentColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("云同步")
                    }
                }

                FilledTonalButton(
                    onClick = { pickRootLauncher.launch(null) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = buttonColors
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = buttonColors.contentColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("添加目录")
                }

                bulkProgress?.let { progress ->
                    val title = when (progress.phase) {
                        BulkPhase.ScanningLocal -> "正在扫描本地库"
                        BulkPhase.SyncingCloud -> "正在云同步"
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = colorScheme.surface.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    if (progress.currentAlbumTitle.isNotBlank()) {
                                        Text(
                                            text = "专辑 ${progress.current}/${progress.total}：${progress.currentAlbumTitle}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.textSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    } else {
                                        Text(
                                            text = "进度 ${progress.current}/${progress.total}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.textSecondary
                                        )
                                    }
                                }
                                TextButton(onClick = { libraryViewModel.cancelBulkTask() }) { Text("取消") }
                            }
                            if (progress.total > 0) {
                                LinearProgressIndicator(
                                    progress = { progress.fraction },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            if (progress.currentFile.isNotBlank()) {
                                Text(
                                    text = "正在扫描：${progress.currentFile}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("已添加目录", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    if (scanRoots.isEmpty()) {
                        Text("暂无", style = MaterialTheme.typography.bodySmall, color = colorScheme.textSecondary)
                    } else {
                        scanRoots.forEach { root ->
                            val label = remember(root) { formatTreeRootLabel(root) }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(label, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        documentTreeDisplayPath(context, root),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                IconButton(onClick = { libraryViewModel.scanSingleRoot(root) }, enabled = !isGlobalSyncRunning) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null, tint = colorScheme.onSurface)
                                }
                                IconButton(onClick = { libraryViewModel.syncMetadataForRoot(root) }, enabled = !isGlobalSyncRunning) {
                                    Icon(Icons.Rounded.CloudSync, contentDescription = null, tint = colorScheme.onSurface)
                                }
                                IconButton(onClick = { pendingRemoveRoot = root }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = null, tint = colorScheme.onSurface)
                                }
                            }
                            }
                    }
                }
}

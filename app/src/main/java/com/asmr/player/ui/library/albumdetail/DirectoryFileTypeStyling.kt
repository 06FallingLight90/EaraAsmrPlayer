package com.asmr.player.ui.library.albumdetail

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.asmr.player.domain.model.TreeFileType
import com.asmr.player.ui.theme.AsmrColorScheme

internal fun fileTypeLabel(fileType: TreeFileType): String = when (fileType) {
    TreeFileType.Audio -> "音频"
    TreeFileType.Video -> "视频"
    TreeFileType.Image -> "图片"
    TreeFileType.Subtitle -> "字幕"
    TreeFileType.Text -> "文本"
    TreeFileType.Pdf -> "PDF"
    TreeFileType.Archive -> "压缩包"
    TreeFileType.Document -> "文档"
    TreeFileType.Spreadsheet -> "表格"
    TreeFileType.Presentation -> "演示文稿"
    TreeFileType.Code -> "代码"
    TreeFileType.Ebook -> "电子书"
    TreeFileType.Font -> "字体"
    TreeFileType.AppPackage -> "安装包"
    TreeFileType.Other -> "文件"
}

internal fun directoryFileTypeLabel(file: DirectoryFileItem): String {
    return if (file.fileType == TreeFileType.Audio) {
        if (file.isOnline) "在线音频" else "本地音频"
    } else {
        fileTypeLabel(file.fileType)
    }
}

internal fun treeFileTypeIcon(fileType: TreeFileType): ImageVector = when (fileType) {
    TreeFileType.Audio -> Icons.Rounded.Audiotrack
    TreeFileType.Video -> Icons.Rounded.Movie
    TreeFileType.Image -> Icons.Rounded.Image
    TreeFileType.Subtitle -> Icons.Rounded.Subtitles
    TreeFileType.Text -> Icons.Rounded.Description
    TreeFileType.Pdf -> Icons.Rounded.PictureAsPdf
    TreeFileType.Archive -> Icons.Rounded.FolderZip
    TreeFileType.Document -> Icons.AutoMirrored.Rounded.Article
    TreeFileType.Spreadsheet -> Icons.Rounded.TableChart
    TreeFileType.Presentation -> Icons.Rounded.Slideshow
    TreeFileType.Code -> Icons.Rounded.Code
    TreeFileType.Ebook -> Icons.AutoMirrored.Rounded.MenuBook
    TreeFileType.Font -> Icons.Rounded.FontDownload
    TreeFileType.AppPackage -> Icons.Rounded.Android
    TreeFileType.Other -> Icons.AutoMirrored.Rounded.InsertDriveFile
}

internal fun treeFileTypeTint(fileType: TreeFileType, colorScheme: AsmrColorScheme): Color = when (fileType) {
    TreeFileType.Audio -> colorScheme.primary
    TreeFileType.Video -> colorScheme.accent
    TreeFileType.Image -> colorScheme.textSecondary
    TreeFileType.Subtitle -> colorScheme.textSecondary
    TreeFileType.Text -> colorScheme.textTertiary
    TreeFileType.Pdf -> colorScheme.danger
    TreeFileType.Archive -> colorScheme.accent
    TreeFileType.Document -> colorScheme.textSecondary
    TreeFileType.Spreadsheet -> colorScheme.primary
    TreeFileType.Presentation -> colorScheme.accent
    TreeFileType.Code -> colorScheme.primary
    TreeFileType.Ebook -> colorScheme.textSecondary
    TreeFileType.Font -> colorScheme.textTertiary
    TreeFileType.AppPackage -> colorScheme.primaryStrong
    TreeFileType.Other -> colorScheme.textTertiary
}


package com.asmr.player.domain.model

/**
 * 本地目录树扫描时对文件类型的分类；序列化进 [LocalTreeCacheEntity.payloadJson]
 * （gson 按枚举名持久化，改名会破坏存量缓存反序列化——legacyFileTypeToTreeFileType
 * 负责旧格式字符串到该枚举的映射）。
 */
internal enum class TreeFileType {
    Audio,
    Video,
    Image,
    Subtitle,
    Text,
    Pdf,
    Archive,
    Document,
    Spreadsheet,
    Presentation,
    Code,
    Ebook,
    Font,
    AppPackage,
    Other
}

/** 本地树索引的叶子缓存条目，与 [TreeFileType] 一同作为 LocalTreeCacheEntity 的 payload 模型。 */
internal data class LocalTreeLeafCacheEntry(
    val relativePath: String,
    val absolutePath: String,
    val fileType: TreeFileType,
    val sizeBytes: Long? = null
)

internal fun treeFileTypeForName(fileName: String): TreeFileType {
    return treeFileTypeForExtension(fileExtensionFromName(fileName))
}

internal fun treeFileTypeForNode(title: String, url: String?, remoteType: String? = null): TreeFileType {
    val fromTitle = treeFileTypeForName(title)

    val urlName = url
        ?.substringBefore('#')
        ?.substringBefore('?')
        ?.substringAfterLast('/')
        ?.substringAfterLast('\\')
        .orEmpty()
    val fromUrl = if (urlName.isNotBlank()) treeFileTypeForName(urlName) else TreeFileType.Other
    val fromRemoteType = treeFileTypeForRemoteType(remoteType)

    return when {
        fromTitle != TreeFileType.Other -> fromTitle
        fromUrl != TreeFileType.Other -> fromUrl
        fromRemoteType != TreeFileType.Other -> fromRemoteType
        !url.isNullOrBlank() -> TreeFileType.Audio
        else -> TreeFileType.Other
    }
}

internal fun isDownloadableTreeFileType(fileType: TreeFileType): Boolean {
    return fileType != TreeFileType.Other && fileType != TreeFileType.Subtitle
}

internal fun isLibraryResourceSavableTreeFileType(fileType: TreeFileType): Boolean {
    return fileType != TreeFileType.Other && fileType != TreeFileType.Subtitle
}

internal fun isPlayableTreeFileType(fileType: TreeFileType): Boolean {
    return fileType == TreeFileType.Audio || fileType == TreeFileType.Video
}

internal fun fileExtensionFromName(name: String): String {
    val fileName = name
        .trim()
        .substringAfterLast('/')
        .substringAfterLast('\\')
    val withoutQuery = fileName.substringBefore('?')
    val fragmentIndex = withoutQuery.indexOf('#')
    val clean = if (fragmentIndex > 0 && withoutQuery.substring(0, fragmentIndex).contains('.')) {
        withoutQuery.substring(0, fragmentIndex)
    } else {
        withoutQuery
    }
    val ext = clean.substringAfterLast('.', missingDelimiterValue = "")
        .lowercase()
        .trim()
    return ext.takeIf { it.length in 1..12 && it.none { ch -> ch == '/' || ch == '\\' } }.orEmpty()
}

private fun treeFileTypeForExtension(ext: String): TreeFileType {
    return when (ext.lowercase()) {
        "mp3", "wav", "flac", "m4a", "m4b", "ogg", "oga", "aac", "opus", "wma", "alac",
        "aiff", "aif", "ape", "amr", "mka", "mid", "midi" -> TreeFileType.Audio

        "mp4", "mkv", "webm", "mov", "m4v", "avi", "wmv", "flv", "mpeg", "mpg", "ts",
        "m2ts", "3gp", "rm", "rmvb", "m3u8" -> TreeFileType.Video

        "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "tif", "tiff",
        "avif", "svg", "ico" -> TreeFileType.Image

        "lrc", "srt", "vtt", "ass", "ssa", "smi", "sbv", "ttml", "dfxp", "sub", "idx" -> TreeFileType.Subtitle

        "txt", "md", "markdown", "nfo", "log", "cue", "ks", "readme" -> TreeFileType.Text

        "pdf" -> TreeFileType.Pdf

        "zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "lz", "lzma", "zst",
        "cab", "iso", "dmg" -> TreeFileType.Archive

        "doc", "docx", "odt", "rtf", "pages", "tex" -> TreeFileType.Document

        "xls", "xlsx", "ods", "csv", "tsv", "numbers" -> TreeFileType.Spreadsheet

        "ppt", "pptx", "odp", "key" -> TreeFileType.Presentation

        "json", "xml", "html", "htm", "css", "js", "jsx", "tsx", "kt", "kts",
        "java", "go", "rs", "py", "rb", "php", "c", "cc", "cpp", "h", "hpp", "cs",
        "swift", "sh", "bash", "zsh", "bat", "cmd", "ps1", "gradle", "properties",
        "yaml", "yml", "toml", "ini", "sql", "lua", "dart" -> TreeFileType.Code

        "epub", "mobi", "azw", "azw3", "fb2", "cbz", "cbr" -> TreeFileType.Ebook

        "ttf", "otf", "woff", "woff2", "eot" -> TreeFileType.Font

        "apk", "aab", "ipa", "exe", "msi", "appx", "deb", "rpm", "pkg" -> TreeFileType.AppPackage

        else -> TreeFileType.Other
    }
}

private fun treeFileTypeForRemoteType(remoteType: String?): TreeFileType {
    val normalized = remoteType
        ?.trim()
        ?.lowercase()
        ?.replace('-', '_')
        .orEmpty()
    if (normalized.isBlank()) return TreeFileType.Other
    return when {
        normalized == "audio" || normalized.startsWith("audio/") -> TreeFileType.Audio
        normalized == "video" || normalized.startsWith("video/") -> TreeFileType.Video
        normalized == "image" || normalized.startsWith("image/") -> TreeFileType.Image
        normalized == "subtitle" || normalized == "subtitles" || normalized == "caption" -> TreeFileType.Subtitle
        normalized == "text" || normalized.startsWith("text/") -> TreeFileType.Text
        normalized == "pdf" || normalized == "application/pdf" -> TreeFileType.Pdf
        normalized == "archive" || normalized == "compressed" -> TreeFileType.Archive
        normalized == "document" -> TreeFileType.Document
        normalized == "spreadsheet" -> TreeFileType.Spreadsheet
        normalized == "presentation" -> TreeFileType.Presentation
        normalized == "code" || normalized == "source" -> TreeFileType.Code
        normalized == "ebook" -> TreeFileType.Ebook
        normalized == "font" || normalized.startsWith("font/") -> TreeFileType.Font
        normalized == "package" || normalized == "app" -> TreeFileType.AppPackage
        else -> TreeFileType.Other
    }
}

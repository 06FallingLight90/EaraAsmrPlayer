package com.asmr.player.domain.model

/**
 * 全部歌曲平铺视图（US-05）的查询语义类型，与 LibraryQuerySpec 族同风格的纯类型。
 * 归位 domain.model（同 LibraryQuerySpec 现状，且 ui-to-db 守护禁 ui import data.local.db.*，
 * T6 的 AllSongsViewModel 需 import 此类型构造过滤/排序参数）。
 * 与 LibraryQuerySpec（专辑/标签维度的库筛选）分开建模：平铺视图只有框内文本过滤与排序两个维度，
 * 且排序是 track 级（LibrarySort 的 Rj/Circle/Cv/LastPlayed 均为专辑级语义，不适用）。
 */
data class AllSongsQuery(
    /** 框内文本过滤；命中 track 标题/displayTitle、文件路径（mediaId 键）与 artist；null/空白 = 不过滤。 */
    val textFilter: String? = null,
    val sort: AllSongsSort = AllSongsSort.AddedDesc
)

/** 平铺视图排序口径（track 级；均带 t.id 兜底保证分页翻页顺序稳定）。 */
enum class AllSongsSort {
    /** 标题（displayTitle 回退 title）NOCASE 升序。 */
    TitleAsc,

    /** 文件路径 NOCASE 升序（平铺视图按文件名展示，路径同序等价于同目录内文件名有序）。 */
    FileNameAsc,

    /** 加入时间倒序（track 自增 id 即插入顺序，与库页 AddedDesc 的 a.id 口径区分）。 */
    AddedDesc
}
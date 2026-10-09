package com.asmr.player.data.local.db.query

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.asmr.player.domain.model.AllSongsQuery
import com.asmr.player.domain.model.AllSongsSort

/**
 * [AllSongsQuery] → Room SQL。spec 纯类型归位 domain/model（同 LibraryQuerySpec 族约定），
 * SQL 构建留 data 层（同 LibraryTrackQueryBuilder 在 data.repository 的分工）。
 * 排序走封闭枚举 when 映射（Room 不支持 ORDER BY 参数绑定，字符串拼接面收敛为枚举常量，无注入面）；
 * 过滤值全部走 ? 绑定参数。JOIN 与投影列对齐 TrackDao 既有写法（a.id = t.albumId，displayTitle 回退链）。
 */
object AllSongsQueryBuilder {

    fun build(query: AllSongsQuery): SupportSQLiteQuery {
        val args = mutableListOf<Any>()

        val sql = StringBuilder()
        sql.append(
            """
            SELECT
                t.id AS trackId,
                t.albumId AS albumId,
                COALESCE(NULLIF(t.displayTitle, ''), t.title) AS trackTitle,
                t.path AS trackPath,
                t.duration AS duration,
                t.artist AS artist,
                COALESCE(NULLIF(a.displayTitle, ''), a.title) AS albumTitle,
                a.coverPath AS coverPath
            FROM tracks t
            JOIN albums a ON a.id = t.albumId
            """.trimIndent()
        )

        val filter = query.textFilter?.trim()
        if (!filter.isNullOrEmpty()) {
            // 命中面：track 标题（displayTitle 回退 title）+ 文件路径 + artist。
            // 专辑级字段（专辑名/社团/CV）不纳入——那些维度由库页既有搜索承担，平铺视图保持找歌语义。
            // 前缀通配 %term% 即可（需求 A4：不做 FTS）；t.artist LIKE 对 NULL 天然不命中，无需特判。
            // 拼接用显式前导空格（SELECT 块 trimIndent 后末尾无换行，直接接 WHERE 会拼成 albumIdWHERE）。
            sql.append(" WHERE (")
            sql.append("COALESCE(NULLIF(t.displayTitle, ''), t.title) LIKE ?")
            sql.append(" OR t.title LIKE ?")
            sql.append(" OR t.path LIKE ?")
            sql.append(" OR t.artist LIKE ?")
            sql.append(")")
            val like = "%$filter%"
            repeat(4) { args.add(like) }
        }

        sql.append(" ORDER BY ")
        sql.append(
            when (query.sort) {
                AllSongsSort.TitleAsc ->
                    "COALESCE(NULLIF(t.displayTitle, ''), t.title) COLLATE NOCASE ASC, t.path COLLATE NOCASE ASC, t.id ASC"
                AllSongsSort.FileNameAsc ->
                    "t.path COLLATE NOCASE ASC, t.id ASC"
                AllSongsSort.AddedDesc ->
                    "t.id DESC, t.path COLLATE NOCASE ASC"
            }
        )

        return SimpleSQLiteQuery(sql.toString(), args.toTypedArray())
    }
}
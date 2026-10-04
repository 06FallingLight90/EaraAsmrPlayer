package com.asmr.player.ui.sample

// Selftest fixture: must be caught by rule 'ui-to-db' (guard self-check).
// 20261004 R3-A1：ui-to-dao 只覆盖 dao. + AppDatabaseProvider，实体/查询类型漏网；
// 本规则把整个 data.local.db.* 纳入禁令。
// Not part of any source set; never compiled by Gradle.
import com.asmr.player.data.local.db.entities.TrackEntity

class RuleCheckSample

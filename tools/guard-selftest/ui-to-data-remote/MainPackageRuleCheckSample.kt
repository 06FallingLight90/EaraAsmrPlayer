package com.asmr.player.main.sample

// Selftest fixture: main 包也必须被规则 'ui-to-data-remote' 命中（20261004 审查 P1-2：
// C1 拆分出的 main/* 文件携带 data.remote import，原规则只扫 ui.* 导致盲区）。
// Not part of any source set; never compiled by Gradle.
import com.asmr.player.data.remote.scraper.DLSITE_DOMAIN

class MainPackageRuleCheckSample

package com.asmr.player.data.local.library

/**
 * 下载存储引用的稳定身份端口；R3-B4 环8 消解：端口定义在消费方（data.local.library），
 * 由 data.download 的 DownloadStorageGateway 实现，消 data.download <-> data.local.library 环。
 */
interface DownloadStorage {
    /** 把文件路径 / SAF document 引用归一化为跨会话稳定的身份键。 */
    fun stableIdentity(reference: String): String
}

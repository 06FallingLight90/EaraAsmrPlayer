package com.asmr.player.data.remote.auth

/** 加密后的值：密文与 IV 均为 Base64（NO_WRAP）。 */
data class EncryptedValue(
    val cipherTextBase64: String,
    val ivBase64: String
)

/**
 * 对称加解 seam：把"如何加密"与"存什么"解耦。
 * 生产实现走 AndroidKeyStore（见 [KeystoreValueCipher]）；
 * 测试注入可逆假实现，验证存储层的迁移与清除语义（S14）。
 */
interface ValueCipher {
    /** 加密失败（如 KeyStore 异常）时抛出，由调用方决定处置。 */
    fun encrypt(plain: String): EncryptedValue

    /** 解密失败返回 null（密文损坏 / 密钥失效），由调用方清除并回退。 */
    fun decrypt(value: EncryptedValue): String?
}

package com.asmr.player.data.remote.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AndroidKeyStore AES/GCM 实现（模式复用自 subtitle/DeepSeekApiKeyStore）。
 * 真机路径（KeyStore 密钥生成/解密）无自动化保障，列入实机验证清单。
 */
internal class KeystoreValueCipher private constructor(
    private val keyAlias: String
) : ValueCipher {
    private val lock = Any()

    override fun encrypt(plain: String): EncryptedValue = synchronized(lock) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        EncryptedValue(
            cipherTextBase64 = Base64.encodeToString(encrypted, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        )
    }

    override fun decrypt(value: EncryptedValue): String? = synchronized(lock) {
        runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, Base64.decode(value.ivBase64, Base64.NO_WRAP))
            )
            cipher.doFinal(Base64.decode(value.cipherTextBase64, Base64.NO_WRAP)).toString(Charsets.UTF_8)
        }.getOrNull()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).run {
            init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            generateKey()
        }
    }

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128

        @Volatile
        private var instances: MutableMap<String, KeystoreValueCipher>? = null

        fun get(keyAlias: String): KeystoreValueCipher {
            val existing = instances?.get(keyAlias)
            if (existing != null) return existing
            return synchronized(this) {
                val map = instances ?: mutableMapOf<String, KeystoreValueCipher>().also { instances = it }
                map.getOrPut(keyAlias) { KeystoreValueCipher(keyAlias) }
            }
        }
    }
}

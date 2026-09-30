package com.asmr.player.data.remote.auth

import android.content.Context
import android.content.SharedPreferences

/**
 * DLsite Cookie 存储（S14 加密改造）。
 *
 * Cookie 值经 [ValueCipher]（AndroidKeyStore AES/GCM）加密后落盘；
 * 读取时若发现历史明文键则惰性迁移为密文（明文读→加密写→明文删除）。
 * KeyStore 真机路径（登录持久化）列入实机验证清单。
 */
class DlsiteAuthStore(
    context: Context,
    private val cipher: ValueCipher = KeystoreValueCipher.get(DLSITE_COOKIE_KEY_ALIAS)
) {
    private val prefs = context.getSharedPreferences("dlsite_auth", Context.MODE_PRIVATE)

    fun saveDlsiteCookie(cookie: String, expiresAtMs: Long? = null) {
        saveCookie(KEY_COOKIE_DLSITE_ENC, KEY_COOKIE_DLSITE_IV, KEY_COOKIE_DLSITE, cookie, expiresAtMs, KEY_COOKIE_DLSITE_EXPIRES_AT_MS)
    }

    fun savePlayCookie(cookie: String, expiresAtMs: Long? = null) {
        saveCookie(KEY_COOKIE_PLAY_ENC, KEY_COOKIE_PLAY_IV, KEY_COOKIE_PLAY, cookie, expiresAtMs, KEY_COOKIE_PLAY_EXPIRES_AT_MS)
    }

    fun getDlsiteCookie(): String = readCookie(KEY_COOKIE_DLSITE_ENC, KEY_COOKIE_DLSITE_IV, KEY_COOKIE_DLSITE)
    fun getPlayCookie(): String = readCookie(KEY_COOKIE_PLAY_ENC, KEY_COOKIE_PLAY_IV, KEY_COOKIE_PLAY)

    fun getDlsiteCookieExpiresAtMs(): Long? = prefs.getLong(KEY_COOKIE_DLSITE_EXPIRES_AT_MS, -1L).takeIf { it > 0L }
    fun getPlayCookieExpiresAtMs(): Long? = prefs.getLong(KEY_COOKIE_PLAY_EXPIRES_AT_MS, -1L).takeIf { it > 0L }

    fun isDlsiteLoggedIn(): Boolean = getDlsiteCookie().isNotBlank()
    fun isPlayLoggedIn(): Boolean = getPlayCookie().isNotBlank()
    fun isLoggedIn(): Boolean = isDlsiteLoggedIn() || isPlayLoggedIn()

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun saveCookie(
        encKey: String,
        ivKey: String,
        legacyPlainKey: String,
        cookie: String,
        expiresAtMs: Long?,
        expiresKey: String
    ) {
        val normalized = cookie.trim()
        val editor = prefs.edit()
        if (normalized.isEmpty()) {
            editor.remove(encKey).remove(ivKey).remove(legacyPlainKey)
        } else {
            val encrypted = cipher.encrypt(normalized)
            editor.putString(encKey, encrypted.cipherTextBase64)
                .putString(ivKey, encrypted.ivBase64)
                .remove(legacyPlainKey)
        }
        if (expiresAtMs != null) {
            editor.putLong(expiresKey, expiresAtMs)
        } else {
            editor.remove(expiresKey)
        }
        editor.apply()
    }

    private fun readCookie(encKey: String, ivKey: String, legacyPlainKey: String): String {
        val enc = prefs.getString(encKey, null)
        val iv = prefs.getString(ivKey, null)
        if (enc != null && iv != null) {
            val decrypted = cipher.decrypt(EncryptedValue(cipherTextBase64 = enc, ivBase64 = iv))
            if (decrypted != null) return decrypted
            // 密文损坏 / 密钥失效：清除该 Cookie 的密文与历史明文，回到未登录态
            prefs.edit().remove(encKey).remove(ivKey).remove(legacyPlainKey).apply()
            return ""
        }
        // 惰性迁移：历史明文 → 加密写 → 明文删除（读路径即迁移路径）
        val legacy = prefs.getString(legacyPlainKey, null)
        if (legacy != null) {
            if (legacy.isNotBlank()) {
                runCatching {
                    val encrypted = cipher.encrypt(legacy)
                    prefs.edit()
                        .putString(encKey, encrypted.cipherTextBase64)
                        .putString(ivKey, encrypted.ivBase64)
                        .remove(legacyPlainKey)
                        .apply()
                }
                return legacy
            }
            prefs.edit().remove(legacyPlainKey).apply()
        }
        return ""
    }

    companion object {
        private const val KEY_COOKIE_DLSITE = "cookie_dlsite"
        private const val KEY_COOKIE_PLAY = "cookie_play"
        private const val KEY_COOKIE_DLSITE_ENC = "cookie_dlsite_encrypted"
        private const val KEY_COOKIE_DLSITE_IV = "cookie_dlsite_iv"
        private const val KEY_COOKIE_PLAY_ENC = "cookie_play_encrypted"
        private const val KEY_COOKIE_PLAY_IV = "cookie_play_iv"
        private const val KEY_COOKIE_DLSITE_EXPIRES_AT_MS = "cookie_dlsite_expires_at_ms"
        private const val KEY_COOKIE_PLAY_EXPIRES_AT_MS = "cookie_play_expires_at_ms"
        private const val DLSITE_COOKIE_KEY_ALIAS = "eara_dlsite_cookie"
    }
}

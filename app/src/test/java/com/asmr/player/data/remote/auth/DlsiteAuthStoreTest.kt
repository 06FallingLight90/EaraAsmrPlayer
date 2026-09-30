package com.asmr.player.data.remote.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.asmr.player.data.remote.auth.DlsiteAuthStore
import com.asmr.player.data.remote.auth.EncryptedValue
import com.asmr.player.data.remote.auth.ValueCipher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** S14：Cookie 存储加密与迁移语义（注入假 cipher，KeyStore 真机路径不在自动化范围）。 */
@RunWith(RobolectricTestRunner::class)
class DlsiteAuthStoreTest {
    private class FakeValueCipher : ValueCipher {
        var failDecrypt = false
        var failEncrypt = false
        var encryptCount = 0

        override fun encrypt(plain: String): EncryptedValue {
            check(!failEncrypt) { "encrypt unavailable" }
            encryptCount++
            // 可逆假加密：Base64 前缀标记，杜绝与明文混淆
            val marked = java.util.Base64.getEncoder().encodeToString(("enc:" + plain).toByteArray())
            return EncryptedValue(cipherTextBase64 = marked, ivBase64 = "iv:" + plain.length)
        }

        override fun decrypt(value: EncryptedValue): String? {
            if (failDecrypt) return null
            return String(java.util.Base64.getDecoder().decode(value.cipherTextBase64)).removePrefix("enc:")
        }
    }

    private fun newStore(cipher: ValueCipher): DlsiteAuthStore =
        DlsiteAuthStore(ApplicationProvider.getApplicationContext<Context>(), cipher)

    private fun rawPrefs() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("dlsite_auth", Context.MODE_PRIVATE)

    @Test
    fun save_storesEncryptedValue_withoutPlaintext_andReadRoundTrips() {
        val cipher = FakeValueCipher()
        val store = newStore(cipher)
        store.saveDlsiteCookie("cookie_a=1; cookie_b=2", expiresAtMs = 123456789L)

        val raw = rawPrefs()
        assertEquals("cookie_a=1; cookie_b=2", store.getDlsiteCookie())
        assertEquals(1, cipher.encryptCount)
        // 明文键不落盘
        assertFalse(raw.getString("cookie_dlsite", null).orEmpty().contains("cookie_a=1"))
        val storedEnc = raw.getString("cookie_dlsite_encrypted", null).orEmpty()
        assertTrue(storedEnc.isNotEmpty() && storedEnc != "cookie_a=1; cookie_b=2")
        assertTrue(java.util.Base64.getDecoder().decode(storedEnc).toString(Charsets.UTF_8).startsWith("enc:"))
        assertTrue(raw.contains("cookie_dlsite_iv"))
        assertEquals(123456789L, store.getDlsiteCookieExpiresAtMs())
        assertTrue(store.isDlsiteLoggedIn())
        assertFalse(store.isPlayLoggedIn())
    }

    @Test
    fun read_migratesLegacyPlaintext_toEncrypted_andRemovesPlaintext() {
        val cipher = FakeValueCipher()
        val store = newStore(cipher)
        // 预置历史明文（旧版本落盘形态）
        rawPrefs().edit()
            .putString("cookie_dlsite", "legacy_cookie=9")
            .putLong("cookie_dlsite_expires_at_ms", 42L)
            .apply()

        // 首次读取：返回原值并完成迁移
        assertEquals("legacy_cookie=9", store.getDlsiteCookie())
        val raw = rawPrefs()
        val migratedEnc = raw.getString("cookie_dlsite_encrypted", null).orEmpty()
        assertTrue(migratedEnc.isNotEmpty())
        assertTrue(java.util.Base64.getDecoder().decode(migratedEnc).toString(Charsets.UTF_8).startsWith("enc:"))
        assertFalse(raw.contains("cookie_dlsite"))
        assertEquals(42L, store.getDlsiteCookieExpiresAtMs())

        // 二次读取走解密路径，值一致（cipher 仅在迁移时加密过一次）
        assertEquals("legacy_cookie=9", store.getDlsiteCookie())
        assertEquals(1, cipher.encryptCount)
    }

    @Test
    fun read_clearsStore_whenDecryptFails() {
        val cipher = FakeValueCipher().apply { failDecrypt = true }
        val store = newStore(cipher)
        store.savePlayCookie("play_cookie=7")

        assertEquals("", store.getPlayCookie())
        val raw = rawPrefs()
        assertFalse(raw.contains("cookie_play_encrypted"))
        assertFalse(raw.contains("cookie_play_iv"))
        assertFalse(raw.contains("cookie_play"))
        assertFalse(store.isPlayLoggedIn())
        assertFalse(store.isLoggedIn())
    }

    @Test
    fun save_blankCookie_removesKeys() {
        val cipher = FakeValueCipher()
        val store = newStore(cipher)
        store.saveDlsiteCookie("cookie_a=1")
        store.saveDlsiteCookie("   ")

        val raw = rawPrefs()
        assertFalse(raw.contains("cookie_dlsite_encrypted"))
        assertFalse(raw.contains("cookie_dlsite_iv"))
        assertFalse(raw.contains("cookie_dlsite"))
        assertFalse(store.isDlsiteLoggedIn())
    }

    @Test
    fun clear_removesEverything() {
        val cipher = FakeValueCipher()
        val store = newStore(cipher)
        store.saveDlsiteCookie("cookie_a=1", expiresAtMs = 1L)
        store.savePlayCookie("play=2", expiresAtMs = 2L)
        store.clear()

        assertFalse(store.isLoggedIn())
        assertEquals(null, store.getDlsiteCookieExpiresAtMs())
        assertEquals(null, store.getPlayCookieExpiresAtMs())
        val raw = rawPrefs()
        assertFalse(raw.contains("cookie_dlsite_encrypted"))
        assertFalse(raw.contains("cookie_play_encrypted"))
        assertFalse(raw.contains("cookie_dlsite"))
        assertFalse(raw.contains("cookie_play"))
    }
}

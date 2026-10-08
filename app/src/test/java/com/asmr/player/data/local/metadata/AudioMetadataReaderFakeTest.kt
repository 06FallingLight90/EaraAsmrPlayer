package com.asmr.player.data.local.metadata

import android.app.Application
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 接口消费示例：手写 fake 演示扫描侧只依赖 AudioMetadataReader seam（T3 接线形态）。 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class AudioMetadataReaderFakeTest {

    private class FakeAudioMetadataReader(
        private val result: AudioMetadata?,
    ) : AudioMetadataReader {
        var lastReadUri: Uri? = null
            private set

        override fun read(uri: Uri): AudioMetadata? {
            lastReadUri = uri
            return result
        }
    }

    @Test
    fun readPassesUriThroughAndReturnsMetadata() {
        val reader = FakeAudioMetadataReader(
            AudioMetadata(title = " 01. 白色季节 ", artist = null, album = null, embeddedCover = null),
        )
        val uri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3Awork%2F01.mp3")

        val metadata = reader.read(uri)

        assertEquals(uri, reader.lastReadUri)
        assertEquals(" 01. 白色季节 ", metadata?.title)
    }

    @Test
    fun readReturningNullPropagatesWithoutThrowing() {
        val reader = FakeAudioMetadataReader(result = null)

        assertNull(reader.read(Uri.parse("content://broken/uri")))
    }
}

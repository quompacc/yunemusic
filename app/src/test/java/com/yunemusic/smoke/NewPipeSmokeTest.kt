package com.yunemusic.smoke

import com.yunemusic.data.youtube.DownloaderImpl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList.YouTube
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.TimeUnit

/**
 * Health-Check gegen die echte YouTube-API über den NewPipe Extractor.
 *
 * Läuft auf der JVM (kein Emulator nötig) und wird vom CI-Workflow
 * (.gitea/workflows/newpipe-healthcheck.yml) täglich mit --refresh-dependencies
 * ausgeführt, um zu erkennen, wann YouTube-Änderungen den Extractor brechen.
 *
 * Benötigt Netzwerkzugriff — schlägt offline fehl.
 */
class NewPipeSmokeTest {

    companion object {
        // Stabiles, seit Jahren verfügbares Video (Rick Astley — Never Gonna Give You Up)
        private const val TEST_VIDEO_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
    }

    @Before
    fun setup() {
        NewPipe.init(DownloaderImpl.getInstance())
    }

    @Test(timeout = 120_000)
    fun `Suche liefert Ergebnisse mit gueltigen Video-IDs`() {
        val extractor = YouTube.getSearchExtractor(
            "never gonna give you up",
            listOf(YoutubeSearchQueryHandlerFactory.VIDEOS),
            null
        )
        extractor.fetchPage()
        val items = extractor.initialPage.items.filterIsInstance<StreamInfoItem>()

        assertFalse("Suche lieferte keine Video-Ergebnisse", items.isEmpty())
        assertTrue(
            "Suchergebnisse enthalten keine gültigen Video-URLs",
            items.any { it.url?.contains("v=") == true }
        )
    }

    @Test(timeout = 120_000)
    fun `Stream-Extraktion liefert abspielbare Audio-URL`() {
        val streamInfo = StreamInfo.getInfo(TEST_VIDEO_URL)

        assertFalse("Keine Audio-Streams gefunden", streamInfo.audioStreams.isEmpty())

        val best = streamInfo.audioStreams.maxByOrNull { it.averageBitrate }!!
        val url = best.content
        assertTrue("Stream-URL ist keine HTTP-URL: $url", url.startsWith("http"))

        // Prüfen, dass die URL tatsächlich Daten liefert (403 = Signatur-Decoder kaputt)
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-1023")
            .build()
        client.newCall(request).execute().use { response ->
            assertTrue(
                "Stream-URL nicht abrufbar (HTTP ${response.code}) — vermutlich Signatur/Throttling-Änderung bei YouTube",
                response.code in 200..299
            )
            val bytes = response.body?.bytes() ?: ByteArray(0)
            assertTrue("Stream-URL lieferte keine Daten", bytes.isNotEmpty())
        }
    }

    @Test(timeout = 120_000)
    fun `Related Tracks verfuegbar fuer Smart Radio`() {
        val streamInfo = StreamInfo.getInfo(TEST_VIDEO_URL)
        val related = streamInfo.relatedItems.orEmpty().filterIsInstance<StreamInfoItem>()
        assertFalse("Keine Related Tracks — Smart Radio würde nicht funktionieren", related.isEmpty())
    }
}

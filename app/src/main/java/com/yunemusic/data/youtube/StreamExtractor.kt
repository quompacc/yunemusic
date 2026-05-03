package com.yunemusic.data.youtube

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "StreamExtractor"
        private const val YTDLP_BINARY = "yt-dlp"
    }

    /**
     * Extracts the audio stream URL for a given YouTube video ID using yt-dlp.
     * yt-dlp binary must be placed in the app's files directory.
     */
    suspend fun extractStreamUrl(videoId: String, quality: AudioQuality = AudioQuality.MEDIUM): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val ytDlpBinary = getYtDlpBinary()
                if (ytDlpBinary == null) {
                    Log.w(TAG, "yt-dlp binary not found, using fallback URL")
                    return@withContext Result.success(getFallbackUrl(videoId))
                }

                val formatSelector = when (quality) {
                    AudioQuality.LOW -> "bestaudio[abr<=128]/bestaudio"
                    AudioQuality.MEDIUM -> "bestaudio[abr<=256]/bestaudio"
                    AudioQuality.HIGH -> "bestaudio[abr<=320]/bestaudio"
                }

                val process = ProcessBuilder(
                    ytDlpBinary.absolutePath,
                    "--no-playlist",
                    "--format", formatSelector,
                    "--get-url",
                    "--no-check-certificates",
                    "https://www.youtube.com/watch?v=$videoId"
                )
                    .redirectErrorStream(false)
                    .start()

                val output = process.inputStream.bufferedReader().readText().trim()
                val error = process.errorStream.bufferedReader().readText().trim()
                val exitCode = process.waitFor()

                if (exitCode == 0 && output.isNotEmpty()) {
                    val url = output.lines().first { it.startsWith("http") }
                    Log.d(TAG, "Successfully extracted stream URL for $videoId")
                    Result.success(url)
                } else {
                    Log.e(TAG, "yt-dlp failed (exit $exitCode): $error")
                    Result.failure(Exception("Stream extraction failed: $error"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception extracting stream URL", e)
                Result.failure(e)
            }
        }

    private fun getYtDlpBinary(): File? {
        // Look in multiple locations
        val locations = listOf(
            File(context.filesDir, YTDLP_BINARY),
            File(context.filesDir, "$YTDLP_BINARY.exe"),
            File("/data/local/tmp/$YTDLP_BINARY")
        )
        return locations.firstOrNull { it.exists() && it.canExecute() }
    }

    /**
     * Fallback: construct a direct YouTube embed URL (limited use, for testing)
     */
    private fun getFallbackUrl(videoId: String): String {
        return "https://www.youtube.com/watch?v=$videoId"
    }

    enum class AudioQuality {
        LOW,    // ~128 kbps
        MEDIUM, // ~256 kbps
        HIGH    // ~320 kbps
    }
}

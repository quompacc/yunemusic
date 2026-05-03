package com.yunemusic.data.youtube

import okhttp3.OkHttpClient
import okhttp3.Request as OkRequest
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.util.concurrent.TimeUnit

class DownloaderImpl private constructor(private val client: OkHttpClient) : Downloader() {

    companion object {
        @Volatile
        private var instance: DownloaderImpl? = null

        fun getInstance(): DownloaderImpl = instance ?: synchronized(this) {
            instance ?: DownloaderImpl(
                OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .build()
            ).also { instance = it }
        }
    }

    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBuilder = OkRequest.Builder().url(url)

        headers.forEach { (key, values) ->
            values.forEach { value -> requestBuilder.addHeader(key, value) }
        }

        val body = dataToSend?.toRequestBody("application/json".toMediaType())

        val okRequest = when (httpMethod) {
            "GET" -> requestBuilder.get().build()
            "POST" -> requestBuilder.post(body ?: ByteArray(0).toRequestBody()).build()
            "DELETE" -> requestBuilder.delete(body).build()
            else -> requestBuilder.get().build()
        }

        val response = client.newCall(okRequest).execute()

        if (response.code == 429) {
            throw ReCaptchaException("reCaptcha Challenge requested", url)
        }

        val responseBody = response.body?.string() ?: ""
        val latestUrl = response.request.url.toString()

        return Response(
            response.code,
            response.message,
            response.headers.toMultimap(),
            responseBody,
            latestUrl
        )
    }
}

package com.example.askmechat.data.remote.network

import com.example.askmechat.data.remote.api.ChatApiService
import com.example.askmechat.data.remote.api.ChatEndpoints
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Lazy, single-instance Retrofit / OkHttp factory.
 *
 * IMPORTANT: Logging level MUST be HEADERS (not BODY). Level.BODY buffers
 * the full response in memory before forwarding it, which defeats the
 * @Streaming annotation and causes chunks to arrive all at once.
 */
object RetrofitProvider {

    val gson: Gson by lazy { GsonBuilder().create() }

    private val loggingInterceptor: HttpLoggingInterceptor by lazy {
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }
    }

    /** Exposed so alternate repositories (e.g. GeminiChatRepositoryImpl) can
     *  reuse the same HTTP stack and logging config. */
    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(ChatEndpoints.CHAT_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    val chatApi: ChatApiService by lazy { retrofit.create(ChatApiService::class.java) }
}

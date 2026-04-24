package com.example.askmechat.data.remote.api

import com.example.askmechat.data.remote.dto.ChatRequestDto
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Streaming
import retrofit2.http.Url

/**
 * Retrofit interface for the streaming chat backend.
 *
 * The `@Streaming` annotation is required — without it, Retrofit buffers
 * the full body before handing it off and you lose the streaming effect.
 * The URL is passed via `@Url` so you control the full path from
 * [ChatEndpoints]; Retrofit resolves it against the configured base URL.
 */
interface ChatApiService {

    @Streaming
    @Headers("Content-Type: application/json")
    @POST
    suspend fun sendMessageStreaming(
        @Url url: String,
        @Body request: ChatRequestDto
    ): ResponseBody

    @GET
    suspend fun getSuggestions(
        @Url url: String
    ): ResponseBody
}

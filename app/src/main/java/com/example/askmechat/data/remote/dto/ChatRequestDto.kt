package com.example.askmechat.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Wire-format request payload. Lives in the data layer; the domain layer
 * never sees this type.
 */
data class ChatRequestDto(
    @SerializedName("action")
    val action: String = "sendMessage",

    @SerializedName("sessionId")
    val sessionId: String? = null,

    @SerializedName("device_id")
    val deviceId: String,

    @SerializedName("chatInput")
    val chatInput: String,

    @SerializedName("user_city")
    val userCity: String = "",

    @SerializedName("user_id")
    val userId: Int? = null
)

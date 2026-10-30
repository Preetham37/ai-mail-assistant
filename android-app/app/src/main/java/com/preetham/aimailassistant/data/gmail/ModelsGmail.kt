package com.preetham.aimailassistant.data.gmail

import com.google.gson.annotations.SerializedName

data class MessageListResponse(
    @SerializedName("messages")
    val messages: List<MessageRef>? = null,
    @SerializedName("nextPageToken")
    val nextPageToken: String? = null,
    @SerializedName("resultSizeEstimate")
    val resultSizeEstimate: Int? = null
)

data class MessageRef(
    @SerializedName("id")
    val id: String = "",
    @SerializedName("threadId")
    val threadId: String? = null
)

data class GmailMessage(
    @SerializedName("id")
    val id: String = "",
    @SerializedName("threadId")
    val threadId: String? = null,
    @SerializedName("labelIds")
    val labelIds: List<String>? = null,
    @SerializedName("snippet")
    val snippet: String? = null,
    @SerializedName("payload")
    val payload: MessagePart? = null
)

data class MessagePart(
    @SerializedName("partId")
    val partId: String? = null,
    @SerializedName("mimeType")
    val mimeType: String? = null,
    @SerializedName("filename")
    val filename: String? = null,
    @SerializedName("headers")
    val headers: List<MessageHeader>? = null,
    @SerializedName("body")
    val body: MessageBody? = null,
    @SerializedName("parts")
    val parts: List<MessagePart>? = null
)

data class MessageHeader(
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("value")
    val value: String? = null
)

data class MessageBody(
    @SerializedName("size")
    val size: Int? = null,
    @SerializedName("data")
    val data: String? = null
)

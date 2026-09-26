package com.example.core.dtn.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_messages",
    indices = [
        Index(value = ["peerNodeId"]),
        Index(value = ["createdAtEpochMs"]),
        Index(value = ["bundleId"], unique = true)
    ]
)
data class ChatMessageEntity(
    @PrimaryKey
    val messageId: String,
    val bundleId: String,
    val peerNodeId: String,
    val peerAlias: String,
    val isOutgoing: Boolean,
    val plaintext: String,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val deliveryCaption: String = "Sent via Mesh"
)

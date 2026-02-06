package com.example.ebook

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity
data class ChatSessionEntity(
    @PrimaryKey val sessionId: String,
    val title: String,
    val createdAt: Long
)

@Entity
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long,
    val imageUri: String? = null,
    val diagramImages: String? = null,
    @ColumnInfo(name = "mermaid_code_blocks")
    val mermaidCodeBlocks: String? = null
)

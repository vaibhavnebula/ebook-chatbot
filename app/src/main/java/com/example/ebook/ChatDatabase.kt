package com.example.ebook

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ChatSessionEntity::class,
        ChatMessageEntity::class
    ],
    version = 2
)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
}

package com.example.ebook

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface ChatDao {

    /* -------- INSERT -------- */

    @Insert
    suspend fun insertSession(session: ChatSessionEntity)

    @Insert
    suspend fun insertMessage(message: ChatMessageEntity)

    /* -------- READ -------- */

    @Query("SELECT * FROM ChatSessionEntity ORDER BY createdAt DESC")
    suspend fun getAllSessions(): List<ChatSessionEntity>

    @Query(
        "SELECT * FROM ChatMessageEntity WHERE sessionId = :sessionId ORDER BY timestamp"
    )
    suspend fun getMessages(sessionId: String): List<ChatMessageEntity>

    /* -------- DELETE (NEW) -------- */

    @Query("DELETE FROM ChatMessageEntity WHERE sessionId = :sessionId")
    suspend fun deleteMessagesBySession(sessionId: String)

    @Query("DELETE FROM ChatSessionEntity WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)

    /* -------- SAFE FULL DELETE -------- */

    @Transaction
    suspend fun deleteSessionWithMessages(sessionId: String) {
        deleteMessagesBySession(sessionId)
        deleteSession(sessionId)
    }
}

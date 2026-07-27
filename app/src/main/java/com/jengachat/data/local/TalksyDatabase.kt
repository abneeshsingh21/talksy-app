package com.jengachat.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.jengachat.data.local.dao.ChatDao
import com.jengachat.data.local.dao.MessageDao
import com.jengachat.data.local.entity.ChatEntity
import com.jengachat.data.local.entity.MessageEntity

@Database(
    entities = [MessageEntity::class, ChatEntity::class],
    version = 1,
    exportSchema = false
)
abstract class TalksyDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun chatDao(): ChatDao
}

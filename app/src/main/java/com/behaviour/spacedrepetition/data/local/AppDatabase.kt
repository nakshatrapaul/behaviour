package com.behaviour.spacedrepetition.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.behaviour.spacedrepetition.data.local.entity.Note
import com.behaviour.spacedrepetition.data.local.entity.Revision

@Database(
    entities = [Note::class, Revision::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
}

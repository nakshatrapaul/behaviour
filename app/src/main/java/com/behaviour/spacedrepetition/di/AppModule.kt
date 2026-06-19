package com.behaviour.spacedrepetition.di

import android.content.Context
import androidx.room.Room
import com.behaviour.spacedrepetition.data.local.AppDatabase
import com.behaviour.spacedrepetition.data.local.NoteDao
import com.behaviour.spacedrepetition.scheduling.AlarmScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "behaviour_database"
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    @Singleton
    fun provideNoteDao(database: AppDatabase): NoteDao {
        return database.noteDao()
    }

    @Provides
    @Singleton
    fun provideAlarmScheduler(): AlarmScheduler {
        return AlarmScheduler()
    }
}

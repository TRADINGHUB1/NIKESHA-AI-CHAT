package com.example.data

import android.content.Context
import androidx.room.Room

object DatabaseProvider {
    private var database: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
        return database ?: synchronized(this) {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "nikesha_chat_db"
            ).fallbackToDestructiveMigration()
                .build()
            database = instance
            instance
        }
    }
}

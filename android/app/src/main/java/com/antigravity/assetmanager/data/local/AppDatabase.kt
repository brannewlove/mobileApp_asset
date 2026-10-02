package com.antigravity.assetmanager.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.antigravity.assetmanager.model.Asset
import com.antigravity.assetmanager.model.InspectionSession
import com.antigravity.assetmanager.model.SessionScan
import com.antigravity.assetmanager.model.TradeLog
import com.antigravity.assetmanager.model.User

@Database(
    entities = [Asset::class, User::class, TradeLog::class, InspectionSession::class, SessionScan::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun assetDao(): AssetDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "asset_manager.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

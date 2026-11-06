package com.wenbo.habitrecord.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        HabitEntity::class,
        CheckInEntity::class,
        PointsLedgerEntity::class,
        RewardEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun habitDao(): HabitDao
    abstract fun checkInDao(): CheckInDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun rewardDao(): RewardDao

    companion object {
        fun create(context: Context): AppDatabase {
            // 兜底：个别设备/模拟器上 databases 目录不会自动创建，Room 打开时直接抛 SQLITE_CANTOPEN
            context.getDatabasePath("habitrecord.db").parentFile?.mkdirs()
            return Room.databaseBuilder(context, AppDatabase::class.java, "habitrecord.db")
                .build()
        }
    }
}

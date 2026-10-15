package com.easyledger.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ProjectEntity::class, WorkRecordEntity::class, WorkSegmentEntity::class, PreferenceEntity::class], version = 2, exportSchema = true)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun dao(): LedgerDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN note TEXT NOT NULL DEFAULT ''")
            }
        }
        fun open(context: Context, name: String = "easy-ledger.db") = Room.databaseBuilder(context, LedgerDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2).build()
    }
}

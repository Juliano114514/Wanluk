package com.wanluk.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.wanluk.data.local.dao.WordCaseDao
import com.wanluk.data.local.entities.WordCaseEntity

@Database(entities = [WordCaseEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
  abstract fun wordCaseDao(): WordCaseDao
}
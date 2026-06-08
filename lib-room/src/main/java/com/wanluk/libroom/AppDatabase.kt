package com.wanluk.libroom

import androidx.room.Database
import androidx.room.RoomDatabase
import com.wanluk.libroom.dao.WordCaseDao
import com.wanluk.libroom.entity.WordCaseEntity

@Database(entities = [WordCaseEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
  abstract fun wordCaseDao(): WordCaseDao
}

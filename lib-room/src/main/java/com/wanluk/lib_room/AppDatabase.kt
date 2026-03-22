package com.wanluk.lib_room

import androidx.room.Database
import androidx.room.RoomDatabase
import com.wanluk.lib_room.dao.WordCaseDao
import com.wanluk.lib_room.entities.WordCaseEntity

@Database(entities = [WordCaseEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
  abstract fun wordCaseDao(): WordCaseDao
}

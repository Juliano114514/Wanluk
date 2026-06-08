package com.wanluk.libroom.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {

  val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL(
        "ALTER TABLE word_cases ADD COLUMN rarity INTEGER NOT NULL DEFAULT 0"
      )
    }
  }
}

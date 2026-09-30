package com.wanluk.libroom.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {

  val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL("""CREATE TABLE IF NOT EXISTS session_task_chunks (
        sessionId TEXT NOT NULL, chunkIndex INTEGER NOT NULL, json TEXT NOT NULL,
        PRIMARY KEY(sessionId, chunkIndex),
        FOREIGN KEY(sessionId) REFERENCES survey_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE)""")
      db.execSQL("CREATE INDEX IF NOT EXISTS index_session_task_chunks_sessionId ON session_task_chunks(sessionId)")
    }
  }

  val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL("ALTER TABLE word_cases ADD COLUMN source_id TEXT")
      db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_word_cases_source_id ON word_cases(source_id)")
      db.execSQL("CREATE TABLE IF NOT EXISTS builtin_assets (name TEXT NOT NULL PRIMARY KEY, sha256 TEXT NOT NULL)")
      db.execSQL("CREATE TABLE IF NOT EXISTS survey_packages (packageId TEXT NOT NULL, revision INTEGER NOT NULL, title TEXT NOT NULL, json TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(packageId, revision))")
      db.execSQL("""CREATE TABLE IF NOT EXISTS survey_sessions (
        id TEXT NOT NULL PRIMARY KEY, packageId TEXT NOT NULL, revision INTEGER NOT NULL,
        title TEXT NOT NULL, packageJson TEXT NOT NULL, speakerAlias TEXT NOT NULL, dialect TEXT NOT NULL,
        consentConfirmedAt INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
        currentPosition INTEGER NOT NULL, completedSteps INTEGER NOT NULL, totalSteps INTEGER NOT NULL,
        exportedAt INTEGER)""")
      db.execSQL("""CREATE TABLE IF NOT EXISTS survey_steps (
        sessionId TEXT NOT NULL, position INTEGER NOT NULL, itemId TEXT NOT NULL, repetition INTEGER NOT NULL,
        selectedTakeId TEXT, skipReason TEXT, note TEXT NOT NULL, PRIMARY KEY(sessionId, position),
        FOREIGN KEY(sessionId) REFERENCES survey_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE)""")
      db.execSQL("CREATE INDEX IF NOT EXISTS index_survey_steps_sessionId ON survey_steps(sessionId)")
      db.execSQL("""CREATE TABLE IF NOT EXISTS recording_takes (
        id TEXT NOT NULL PRIMARY KEY, sessionId TEXT NOT NULL, position INTEGER NOT NULL,
        state TEXT NOT NULL, createdAt INTEGER NOT NULL, durationMs INTEGER NOT NULL,
        sampleRate INTEGER NOT NULL, channels INTEGER NOT NULL, bitsPerSample INTEGER NOT NULL,
        audioSource TEXT NOT NULL, inputDevice TEXT NOT NULL, peak REAL NOT NULL, rms REAL NOT NULL,
        clippedFraction REAL NOT NULL, warning TEXT NOT NULL,
        FOREIGN KEY(sessionId) REFERENCES survey_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE)""")
      db.execSQL("CREATE INDEX IF NOT EXISTS index_recording_takes_sessionId_position ON recording_takes(sessionId, position)")
    }
  }

  val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL(
        "ALTER TABLE word_cases ADD COLUMN rarity INTEGER NOT NULL DEFAULT 0"
      )
    }
  }
}

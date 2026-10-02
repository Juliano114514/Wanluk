package com.wanluk.libroom.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {

  val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL("ALTER TABLE recording_takes ADD COLUMN appliedGain REAL NOT NULL DEFAULT 1.0")
    }
  }

  val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL("ALTER TABLE word_cases ADD COLUMN polyphonic INTEGER")
      db.execSQL("ALTER TABLE survey_packages ADD COLUMN itemCount INTEGER")
      db.execSQL("ALTER TABLE survey_packages ADD COLUMN totalSteps INTEGER")
      db.execSQL("ALTER TABLE survey_packages ADD COLUMN description TEXT NOT NULL DEFAULT ''")
      db.execSQL("ALTER TABLE survey_packages ADD COLUMN dialect TEXT NOT NULL DEFAULT ''")
      db.execSQL("ALTER TABLE survey_sessions ADD COLUMN recordedSteps INTEGER NOT NULL DEFAULT 0")
      db.execSQL("ALTER TABLE survey_sessions ADD COLUMN skippedSteps INTEGER NOT NULL DEFAULT 0")
      db.execSQL("ALTER TABLE survey_sessions ADD COLUMN contentRevision INTEGER NOT NULL DEFAULT 0")
      db.execSQL("ALTER TABLE survey_sessions ADD COLUMN exportedContentRevision INTEGER")
      db.execSQL("ALTER TABLE survey_sessions ADD COLUMN researchCode TEXT NOT NULL DEFAULT ''")
      db.execSQL("ALTER TABLE survey_sessions ADD COLUMN collectionLocation TEXT NOT NULL DEFAULT ''")
      db.execSQL("ALTER TABLE survey_sessions ADD COLUMN collector TEXT NOT NULL DEFAULT ''")
      db.execSQL("UPDATE survey_sessions SET recordedSteps = (SELECT COUNT(*) FROM survey_steps WHERE sessionId = survey_sessions.id AND selectedTakeId IS NOT NULL), skippedSteps = (SELECT COUNT(*) FROM survey_steps WHERE sessionId = survey_sessions.id AND selectedTakeId IS NULL AND skipReason IS NOT NULL), exportedContentRevision = CASE WHEN exportedAt IS NULL THEN NULL ELSE 0 END")
      db.execSQL("UPDATE survey_sessions SET completedSteps = recordedSteps + skippedSteps")
      db.execSQL("CREATE INDEX IF NOT EXISTS index_survey_sessions_updatedAt ON survey_sessions(updatedAt)")
      db.execSQL("ALTER TABLE recording_takes ADD COLUMN reviewStatus TEXT NOT NULL DEFAULT 'unreviewed'")
      db.execSQL("CREATE INDEX IF NOT EXISTS index_recording_takes_sessionId_position_createdAt ON recording_takes(sessionId, position, createdAt)")
      db.execSQL("CREATE INDEX IF NOT EXISTS index_recording_takes_sessionId_state ON recording_takes(sessionId, state)")
      db.execSQL("CREATE TABLE IF NOT EXISTS session_item_locations (sessionId TEXT NOT NULL, itemId TEXT NOT NULL, chunkIndex INTEGER NOT NULL, itemIndex INTEGER NOT NULL, PRIMARY KEY(sessionId, itemId), FOREIGN KEY(sessionId) REFERENCES survey_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
      db.execSQL("CREATE INDEX IF NOT EXISTS index_session_item_locations_sessionId ON session_item_locations(sessionId)")
      db.execSQL("CREATE TABLE IF NOT EXISTS recording_cleanup_jobs (`key` TEXT NOT NULL PRIMARY KEY, sessionId TEXT NOT NULL, takeId TEXT, attempts INTEGER NOT NULL, lastError TEXT NOT NULL)")
      db.execSQL("CREATE TABLE IF NOT EXISTS survey_drafts (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, updatedAt INTEGER NOT NULL)")
      db.execSQL("CREATE TABLE IF NOT EXISTS survey_draft_chunks (draftId TEXT NOT NULL, chunkIndex INTEGER NOT NULL, json TEXT NOT NULL, PRIMARY KEY(draftId, chunkIndex), FOREIGN KEY(draftId) REFERENCES survey_drafts(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
      db.execSQL("CREATE INDEX IF NOT EXISTS index_survey_draft_chunks_draftId ON survey_draft_chunks(draftId)")
      db.execSQL("CREATE TABLE IF NOT EXISTS word_favorites (sourceKey TEXT NOT NULL PRIMARY KEY, createdAt INTEGER NOT NULL)")
    }
  }

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

package com.wanluk.libroom

import androidx.room.Database
import androidx.room.RoomDatabase
import com.wanluk.libroom.dao.WordCaseDao
import com.wanluk.libroom.dao.SurveyDao
import com.wanluk.libroom.entity.*

@Database(entities = [WordCaseEntity::class, BuiltinAssetEntity::class, SurveyPackageEntity::class,
  SurveySessionEntity::class, SurveyStepEntity::class, RecordingTakeEntity::class, SessionTaskChunkEntity::class],
  version = 4, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
  abstract fun wordCaseDao(): WordCaseDao
  abstract fun surveyDao(): SurveyDao
}

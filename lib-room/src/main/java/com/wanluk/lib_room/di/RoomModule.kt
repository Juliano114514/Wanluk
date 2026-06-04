package com.wanluk.lib_room.di

import androidx.room.Room
import com.wanluk.lib_room.AppDatabase
import com.wanluk.lib_room.repository.WordCaseRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val roomModule = module {
  // 数据库单例
  single {
    Room.databaseBuilder(
      androidContext(),
      AppDatabase::class.java,
      "wanluk_database"
    ).build()
  }

  // Dao 注入
  single { get<AppDatabase>().wordCaseDao() }

  // Repository 注入
  single { WordCaseRepository(androidContext(), get()) }
}
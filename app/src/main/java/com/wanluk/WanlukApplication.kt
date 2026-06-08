package com.wanluk

import android.app.Application
import com.wanluk.di.appModule
import com.wanluk.libroom.di.roomModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class WanlukApplication : Application() {

  override fun onCreate() {
    super.onCreate()
    startKoin {
      androidContext(this@WanlukApplication)
      modules(roomModule, appModule)
    }
  }
}

package com.wanluk.di

import com.wanluk.ui.demo.WordCaseDemoViewModel
import com.wanluk.ui.studio.StudioViewModel
import com.wanluk.ui.studio.WordLibraryViewModel
import com.wanluk.ui.studio.SurveyTransferViewModel
import com.wanluk.foundation.recording.RecordingFiles
import com.wanluk.librecord.WavRecorder
import com.wanluk.librecord.RecordingPlayer
import com.wanluk.libexport.ResultExporter
import com.wanluk.libsettings.RecorderSettingsStore
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
  viewModel { WordCaseDemoViewModel(get()) }
  viewModel { WordLibraryViewModel(get()) }
  single { RecordingFiles(androidContext()) }
  factory { WavRecorder(androidContext()) }
  factory { RecordingPlayer() }
  factory { ResultExporter(get()) }
  single { RecorderSettingsStore(androidContext()) }
  viewModel { SurveyTransferViewModel(androidContext(), get()) }
  viewModel { StudioViewModel(androidContext(), get(), get(), get(), get(), get(), get(), get()) }
}

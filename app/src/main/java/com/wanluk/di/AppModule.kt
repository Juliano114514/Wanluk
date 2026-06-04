package com.wanluk.di

import com.wanluk.ui.demo.WordCaseDemoViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
  viewModel { WordCaseDemoViewModel(get()) }
}

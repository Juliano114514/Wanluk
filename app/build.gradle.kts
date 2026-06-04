plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
}

apply(from = rootProject.file("foundation/wanluk-android.gradle"))

android {
  namespace = "com.wanluk"

  defaultConfig {
    applicationId = "com.wanluk"
    versionCode = 1
    versionName = "1.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }

  buildFeatures {
    compose = true
  }
}

dependencies {
  implementation(project(":foundation"))
  implementation(project(":lib-room"))
  implementation(project(":lib-compose-ui"))
  implementation(project(":lib-record"))
  implementation(project(":lib-exoprt"))

  implementation(libs.bundles.androidx.base)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.bundles.compose)
  debugImplementation(libs.androidx.ui.tooling)

  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.koin.android)
  implementation(libs.koin.androidx.compose)
  implementation(libs.gson)

  testImplementation(libs.junit)
  androidTestImplementation(libs.bundles.android.test)
}

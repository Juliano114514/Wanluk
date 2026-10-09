plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
}

apply(from = rootProject.file("gradle/wanluk-android.gradle"))

android {
  namespace = "com.wanluk"

  defaultConfig {
    applicationId = "com.wanluk.app"
    versionCode = 2
    versionName = "1.1.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
      if (!keystorePath.isNullOrBlank()) {
        storeFile = file(keystorePath)
        storePassword = System.getenv("ANDROID_STORE_PASSWORD")
        keyAlias = System.getenv("ANDROID_KEY_ALIAS")
        keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      signingConfig = signingConfigs.getByName("release")
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
  implementation(project(":lib-export"))
  implementation(project(":lib-settings"))
  implementation(project(":lib-settings-ui"))
  implementation(project(":lib-record-ui"))
  implementation(project(":lib-survey-transfer"))

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

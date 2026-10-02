import com.google.protobuf.gradle.*

plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.protobuf)
}

apply(from = rootProject.file("gradle/wanluk-android.gradle"))

android {
  namespace = "com.wanluk.foundation"
}

dependencies {
  api(libs.androidx.core.ktx)
  api(libs.bundles.coroutines)
  implementation(libs.gson)
  implementation(libs.protobuf.javalite)
}

protobuf {
  protoc { artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}" }
  generateProtoTasks {
    all().configureEach {
      builtins { maybeCreate("java").option("lite") }
    }
  }
}

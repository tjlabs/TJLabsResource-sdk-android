import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun String.escapeForBuildConfig(): String = this
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

val authClientSecret: String = providers.gradleProperty("AUTH_CLIENT_SECRET").orNull
    ?: localProperties.getProperty("AUTH_CLIENT_SECRET", "")
val authAccessKey: String = providers.gradleProperty("AUTH_ACCESS_KEY").orNull
    ?: localProperties.getProperty("AUTH_ACCESS_KEY", "")
val authSecretAccessKey: String = providers.gradleProperty("AUTH_SECRET_ACCESS_KEY").orNull
    ?: localProperties.getProperty("AUTH_SECRET_ACCESS_KEY", "")

android {
    namespace = "com.tjlabs.resource_sdk_sample_app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tjlabs.resource_sdk_sample_app"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        buildConfigField(
            "String",
            "AUTH_CLIENT_SECRET",
            "\"${authClientSecret.escapeForBuildConfig()}\""
        )
        buildConfigField(
            "String",
            "AUTH_ACCESS_KEY",
            "\"${authAccessKey.escapeForBuildConfig()}\""
        )
        buildConfigField(
            "String",
            "AUTH_SECRET_ACCESS_KEY",
            "\"${authSecretAccessKey.escapeForBuildConfig()}\""
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":sdk"))
    implementation ("androidx.compose.material3:material3:1.2.1") // 또는 최신 버전
    implementation ("com.google.android.material:material:1.9.0") // M3 호환 목적
    implementation (libs.opencsv)
    implementation(libs.androidx.core.ktx.v131)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.appcompat.v120)
    implementation(libs.material.v120)
    implementation(libs.androidx.constraintlayout.v213)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit.v113)
    androidTestImplementation(libs.androidx.espresso.core.v340)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
}

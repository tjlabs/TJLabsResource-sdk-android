import com.android.build.gradle.internal.api.BaseVariantOutputImpl

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")

}

val versionMajor = 1
val versionMinor = 1
val versionPatch = 20

// 진단 배포용 publish 좌표 override.
// 2026-10-06 전환 (TJ-609): Jupiter SDK 2.0.37 작업과 함께 Resource SDK 1.1.20 코드를
// mavenLocal 에 **정식 좌표** (1.1.20) 로 발행해 Jupiter/VM 양쪽에서 참조. Jupiter 쪽
// `resourceSdkVersion` 도 1.1.20 으로 올려 소비 좌표 일치시킴. 공식 1.1.20 tag 배포 후에는
// 이 override 는 null 유지만으로 충분 (mavenLocal 우선 순위로 로컬 iterate 보장).
val publishVersionOverride: String? = null


android {
    namespace = "com.tjlabs.tjlabsresource_sdk_android"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        targetSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    libraryVariants.all {
        outputs.all {
            (this as BaseVariantOutputImpl).outputFileName = "app-release-resource.aar"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        release {
            isMinifyEnabled = true
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
}

dependencies {
    // Auth 1.0.27 : setServerURL(env) 도입. env 인자로 PROD/DEV 스위칭 가능.
    api ("com.github.tjlabs:TJLabsAuth-sdk-android:1.0.28")
    api ("androidx.security:security-crypto-ktx:1.1.0-alpha03") //auth 사용을 위해 같이 추가해야함
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.github.tjlabs"
                artifactId = "TJLabsResource-sdk-android"
                version = publishVersionOverride ?: "$versionMajor.$versionMinor.$versionPatch"
            }
        }
    }
}

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
// Jupiter SDK 2.0.36 이 `strictly 1.1.19` constraint 로 TJLabsResource 를 pin 하므로
// 1.1.20 좌표는 소비자 (VM SDK 등) 에서 downgrade 되어 반영되지 않는다.
// mavenLocal 이 jitpack 보다 repository 우선순위 높은 점을 이용해 같은 좌표 (1.1.19) 로
// 덮어씌우면 이 브랜치의 코드 (entrance CSV optional / zip 스키마 등) 가 소비자에 반영된다.
// 검증 완료 후 이 override 를 지우고 정식 1.1.20 tag 배포로 전환.
val publishVersionOverride: String? = "1.1.19"


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

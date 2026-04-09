# TJLabsResource-sdk-android

Android resource SDK distributed via JitPack.

## Requirements

- Android Gradle Plugin 8+
- JDK 17
- minSdk 26+

## Installation (JitPack)

Add JitPack repository:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}
```

Add dependency:

```kotlin
dependencies {
    implementation("com.github.tjlabs:TJLabsResource-sdk-android:<tag>")
}
```

`<tag>` should be a Git tag (for example `1.0.22`).

## Artifact

- Module: `:sdk`
- Artifact: `TJLabsResource-sdk-android`
- Group: `com.github.tjlabs`

## Local publish check

```bash
./gradlew :sdk:publishToMavenLocal -x test --stacktrace
```

pluginManagement {
    repositories {
        maven(url = "https://jitpack.io") // JitPack plugin 지원 필요 시
        mavenLocal()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven(url = "https://jitpack.io") // JitPack plugin 지원 필요 시

        mavenLocal()
        google()
        mavenCentral()
    }
}

rootProject.name = "TJLabsResource-sdk-android"
include(":sdk")
include(":sdk-sample-app")

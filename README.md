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

## Release Automation (GitHub Actions)

- PR Validation Workflow: `.github/workflows/pr-validate.yml`
- Trigger:
  - pull request to `main` or `release/*`
  - or manual `workflow_dispatch`
- Pipeline does:
  1. run SDK unit tests (`:sdk:testDebugUnitTest`, `:sdk:testReleaseUnitTest`)
  2. run publish check (`:sdk:publishToMavenLocal -x test`)

- Workflow: `.github/workflows/release-jitpack.yml`
- Trigger:
  - push to `release/x.y.z` branch
  - or manual `workflow_dispatch`
- Pipeline does:
  1. validate release version format (`x.y.z`)
  2. verify `sdk/build.gradle.kts` version equals release version
  3. run SDK unit tests (`:sdk:testDebugUnitTest`, `:sdk:testReleaseUnitTest`)
  4. run publish check (`:sdk:publishToMavenLocal -x test`)
  5. create/push tag (`x.y.z`) from the release commit
  6. call JitPack build log URL to warm up JitPack build

> Note: Live API smoke test is intentionally excluded from automation.

## Auth Release Receiver Automation

- Workflow: `.github/workflows/receive-auth-release.yml`
- Trigger:
  - `repository_dispatch` with type `auth_release_published`
  - `workflow_dispatch` with required input `auth_version` (`x.y.z`)
- Required workflow permissions:
  - `contents: write`
  - `pull-requests: write`

Flow:
1. Read `auth_version` (dispatch payload first, then workflow input) and validate `x.y.z`.
2. Checkout `origin/main` and apply dependency update only on `:sdk` module build file.
3. Run validation:
   - `./gradlew :sdk:testDebugUnitTest :sdk:testReleaseUnitTest --stacktrace --no-daemon`
   - `./gradlew :sdk:publishToMavenLocal -x test --stacktrace --no-daemon`
4. Create branch `chore/bump-auth-x.y.z`, commit, push, and open PR to `main`.

Safety policy:
- Base branch is fixed to `main`.
- Existing open bump PR for the same version is detected and the workflow exits without creating a new PR.
- If remote branch `chore/bump-auth-x.y.z` already exists, workflow exits safely (no reset/force push).
- Ongoing feature/release branches are not modified by this workflow.
- Live API smoke test is intentionally excluded.

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

## Usage

### Manager

Use a single manager:

```kotlin
val manager = TJLabsResourceManager()
```

### Resource Load APIs

- `loadJupiterResource(...)`
- `loadVenusResource(...)`
- `loadWarpResource(...)`
- `loadSimulationData(...)`

### Delegates

Set only the delegates you need.

- `delegate: TJLabsResourceManagerDelegate` (Jupiter core data)
- `venusDelegate: TJLabsVenusResourceManagerDelegate`
- `warpDelegate: TJLabsWarpResourceManagerDelegate`
- `simulationDelegate: TJLabsSimulationResourceManagerDelegate`

Simulation callback:

```kotlin
override fun onSimulationData(sectorId: Int, data: SimulationBundleOutput) {
    // data.vehicle: List<SimulationItemOutput(name, url)>
    // data.pdr: List<SimulationItemOutput(name, url)>
}
```

`loadSimulationData(...)` downloads simulation JSON files and caches them with names like:

- `vehicle_<name>.json`
- `pdr_<name>.json`

### Affine Type

`AffineTransParamOutput` fields are `Double`.

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

## Deployment modes: cloud vs on-prem (하나 PMS)

The SDK supports two mutually-exclusive routing modes. **TJ-620 (2026-10) changes affect
cloud only** — on-prem consumers (하나은행 PMS 등) are not impacted by any changes in
`1.1.23+`.

### Cloud mode (K-BOS, demo apps, TJLabs internal use)

Activated by calling `TJLabsAuthManager.setClientSecret(...)` + `TJLabsResourceNetworkConstants.setServerURL(provider, region, env)` (indirectly through `TJHanaEnvironment.useCloudForTesting` or `TJJupiterAuth.setServerConfig*`).

- Base URL: `https://{regionPrefix}.user.jupiter.{tjlabscorp.com | tjlabs.dev}`
  (`currentOlympusSuffix` chooses between PROD/DEV).
- Endpoints:
  - Jupiter (v2): `GET /{2026-10-12}/sectors/bundle?sector_ids=...`
  - Venus:        `GET /{2026-04-27}/sectors/{pk}/bundle/lite`
  - **Warp (TJ-620):** `GET /{2026-10-07}/sectors/warp-bundle?sector_ids=...`
    — moved from the legacy `.warp.tjlabs.dev` AWS infrastructure to the unified
    Jupiter olympus domain. Supports env (PROD/DEV) switching via `loadWarpResource(env=...)`.
- Auth: `TJLabsAuthManager.getAccessToken()` injects a Jupiter cloud bearer.

### On-prem mode (하나 PMS)

Activated by calling `TJHanaEnvironment.setBaseUrl(...)` which enables
`OnPremRoutingState`. Mutually exclusive with cloud mode — the latest call wins and
the other side is cleared defensively.

- Base URL: whatever is passed to `setBaseUrl` (default
  `https://hqsw.hanafn.com:18443/api/v1/sdk-proxy/api`).
- Endpoints (service-prefixed, **not affected by TJ-620**):
  - Warp:  `GET {baseUrl}/v2/warp/sectors/{pk}/bundle`
  - Venus: `GET {baseUrl}/v2/venus/sectors/{pk}/bundle`
  - Jupiter: not supported on-prem (501 returned if attempted).
- Auth: on-prem access token owned by jupiter-sdk (`OnPremAuthState`), injected per
  request by `TJLabsResourceNetworkConstants.genRetrofit`.

### Guarantees for on-prem consumers upgrading past 1.1.22

- `getWarpBundleOnPrem` / `getVenusBundleOnPrem` endpoints are unchanged.
- `OnPremRoutingState.isEnabled` branch in `TJLabsBundleDataManager.requestBundleMeta`
  is untouched.
- Hostname verifier (`onPremScopedHostnameVerifier`) and TLS behaviour unchanged.
- Response schema (`SectorBundleMetaOutput`) is shared with cloud but was already
  compatible — no field was added that on-prem servers do not populate.
- `loadWarpResource` added an optional `env` parameter with default
  `ResourceServerEnv.PROD`. On-prem call sites that don't pass `env` behave exactly
  as before (the parameter is irrelevant in on-prem routing anyway — `baseUrl` wins).

Whenever changing the cloud path in `TJLabsBundleDataManager.requestBundleMeta`, keep
all mutations outside the `if (OnPremRoutingState.isEnabled) { ... }` block to avoid
regressing the PMS deployment.

#!/usr/bin/env bash
#
# Phase 1-4 (현재 코드) vs jitpack 공식 1.1.19 바이너리 사이에서 VM SDK 샘플 앱 테스트 좌표를 전환.
#
# 사용:
#   ./scripts/toggle-sdk-version.sh current     # mavenLocal 의 Phase 1-4 코드 사용 (Multi + fallback)
#   ./scripts/toggle-sdk-version.sh legacy      # jitpack 공식 1.1.19 (Multi/fallback 없음)
#
# 원리:
#   - current: Resource SDK 를 mavenLocal 에 1.1.19 좌표로 publish → VM SDK 샘플이 그걸 소비
#   - legacy : mavenLocal 의 1.1.19 를 삭제 → Gradle 이 jitpack 에서 공식 1.1.19 다운로드
#
# 왜 Resource SDK 자체 샘플 앱 (sdk-sample-app) 을 안 쓰나:
#   그 샘플은 Phase 2+ 심볼 (TJLabsMultiResourceManager, SectorLoadResult 등) 을 참조하므로
#   jitpack 공식 1.1.19 로 전환 시 compile 실패. VM SDK 샘플은 1.1.19 호환 API 만 쓰므로
#   어느 좌표든 build 성공 → "같은 소비자 코드에서 SDK 바이너리만 바꿔치기" 비교가 깔끔.

set -euo pipefail

MODE="${1:-}"
if [[ "$MODE" != "current" && "$MODE" != "legacy" ]]; then
    cat >&2 <<USAGE
usage: $0 {current|legacy}

  current  Phase 1-4 코드 (mavenLocal 1.1.19) 를 VM SDK 샘플에 설치.
           Multi 로더 사용 가능하지만 VM SDK 샘플에는 Multi 테스트 UI 가 없으므로
           기존 (단일 섹터) 흐름 안에서 Phase 1 fallback 동작만 간접 확인.

  legacy   jitpack 공식 1.1.19 (Phase 1-4 없음) 를 VM SDK 샘플에 설치.
           메타/raw 실패 시 fallback 없이 그대로 실패 콜백 — Phase 1-4 전 상태 재현.
USAGE
    exit 1
fi

# 작업 디렉토리 추정 — 이 스크립트가 Resource SDK 레포 안에 있음.
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
RES_SDK_DIR="$( cd "$SCRIPT_DIR/.." && pwd )"
VM_SDK_DIR="$( cd "$RES_SDK_DIR/../TJJupiterVM-sdk-android" && pwd 2>/dev/null )" \
    || { echo "VM SDK 경로를 못 찾음: $RES_SDK_DIR/../TJJupiterVM-sdk-android" >&2; exit 1; }
MAVEN_LOCAL_ARTIFACT="$HOME/.m2/repository/com/github/tjlabs/TJLabsResource-sdk-android/1.1.19"

echo "[toggle-sdk] mode=$MODE"
echo "[toggle-sdk] Resource SDK: $RES_SDK_DIR"
echo "[toggle-sdk] VM SDK:       $VM_SDK_DIR"

if [[ "$MODE" == "legacy" ]]; then
    echo "[toggle-sdk] jitpack 공식 1.1.19 바이너리로 전환"
    if [[ -d "$MAVEN_LOCAL_ARTIFACT" ]]; then
        rm -rf "$MAVEN_LOCAL_ARTIFACT"
        echo "[toggle-sdk]   mavenLocal 1.1.19 삭제 완료"
    else
        echo "[toggle-sdk]   mavenLocal 1.1.19 이미 비어있음"
    fi
    cd "$VM_SDK_DIR"
    # Gradle 모듈 캐시도 purge — 이전 mavenLocal 참조가 모듈 캐시에 남아 재사용될 수 있음.
    rm -rf "$HOME/.gradle/caches/modules-2/files-2.1/com.github.tjlabs/TJLabsResource-sdk-android/1.1.19" || true
    rm -rf "$HOME/.gradle/caches/transforms-3/"*"TJLabsResource-sdk-android-1.1.19"* 2>/dev/null || true
    ./gradlew :app:installDebug --refresh-dependencies
    echo "[toggle-sdk] 완료 — VM SDK 샘플이 jitpack 공식 1.1.19 사용 중"
    echo
    echo "비교 포인트: airplane mode 로 Multi/Load 테스트 시 success=false 로 실패하는지 확인"
    echo "(Phase 1-4 current 와 달리 fallback 미발화)"
else
    echo "[toggle-sdk] mavenLocal 의 Phase 1-4 코드로 전환"
    cd "$RES_SDK_DIR"
    ./gradlew :sdk:publishToMavenLocal
    echo "[toggle-sdk]   Phase 1-4 code → mavenLocal 1.1.19 발행 완료"
    cd "$VM_SDK_DIR"
    rm -rf "$HOME/.gradle/caches/modules-2/files-2.1/com.github.tjlabs/TJLabsResource-sdk-android/1.1.19" || true
    rm -rf "$HOME/.gradle/caches/transforms-3/"*"TJLabsResource-sdk-android-1.1.19"* 2>/dev/null || true
    ./gradlew :app:installDebug --refresh-dependencies
    echo "[toggle-sdk] 완료 — VM SDK 샘플이 Phase 1-4 코드 사용 중"
    echo
    echo "비교 포인트: airplane mode 로 테스트 시 fallback 발화 (versionVerified=false) 로 success=true"
fi

package com.tjlabs.tjlabsresource_sdk_android

/**
 * loadResource 시 층별 이미지(building level PNG) 다운로드 정책.
 *
 * ── 도입 배경 (2026-09-07)
 * VM SDK · Jupiter SDK 내부 로직은 층 이미지 bitmap 을 소비하지 않는다
 * (`onBuildingLevelImageData` 콜백 구현이 전부 no-op). 그럼에도 기본값 [ALL] 정책은
 * sector 초기 로드에서 층 이미지 (수 MB · N 장) 를 병렬 다운로드해 init 시간을 크게 늘렸다.
 *
 * 이 enum 으로 소비자가 원하는 만큼만 초기 로드하고, 나머지는 [TJLabsResourceManager] 의
 * on-demand fetch API 로 필요 시점에 채울 수 있다.
 *
 * @property ALL 모든 층 이미지를 병렬 다운로드 (기존 동작). 호스트 앱이 sector 로드 완료 직후
 *   전체 층 지도를 필요로 하는 경우.
 * @property NONE 이미지 다운로드 완전 skip. `onBuildingLevelImageData(key, null)` 로 emit 되며
 *   호스트 앱은 필요 시 [TJLabsResourceManager.loadLevelImage] / [prefetchRemainingImages] 를
 *   호출해 개별 fetch. VM SDK 처럼 이미지를 아예 소비하지 않는 경로에 권장.
 * @property DEFAULT_ONLY sector 의 `default_position` 이 가리키는 층 이미지만 다운로드.
 *   앱 진입 직후 표시할 첫 층만 blocking 으로 채우고 나머지 층은 사용자 이동 시점에 on-demand
 *   fetch. `default_position` 이 null 이거나 매핑 실패 시 [NONE] 과 동일하게 동작 + 경고 로그.
 */
enum class ImageLoadPolicy {
    ALL,
    NONE,
    DEFAULT_ONLY,
}

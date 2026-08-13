package com.tjlabs.tjlabsresource_sdk_android

/**
 * loadResource / loadJupiterResource / loadVenusResource / loadWarpResource 완료 콜백에
 * 함께 전달되는 로딩 메타. 성공 시 non-null, 실패 시 null.
 *
 * ── 필드
 *  - [versionId]: 서버 meta 응답의 `version_id`. 특정 sector·bundle 의 현재 배포 버전.
 *  - [fromCache]: raw bundle 이 로컬 캐시 (메모리 또는 디스크) 에서 온 경우 true.
 *                 서버로부터 새로 다운로드 받은 경우 false.
 *                 meta 만 네트워크 fetch 하고 raw 는 로컬 재사용한 경우도 true (raw 기준).
 *
 * SDK 소비자 측 telemetry 용 — 어떤 버전의 번들이 실제 사용됐고 최초 다운인지 캐시인지 추적.
 */
data class ResourceLoadInfo(
    val versionId: String,
    val fromCache: Boolean,
)

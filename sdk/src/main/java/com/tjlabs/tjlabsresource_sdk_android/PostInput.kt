package com.tjlabs.tjlabsresource_sdk_android

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Resource SDK 가 사용하는 Retrofit 엔드포인트 모음. **cloud 와 on-prem (하나 PMS) 두 경로** 가
 * 공존한다 — 어떤 함수를 호출할지는 [TJLabsBundleDataManager.requestBundleMeta] 가
 * `OnPremRoutingState.isEnabled` 로 분기해서 결정.
 *
 * - **Cloud 경로**: `getSectorBundle`, `getSectorBundleV2`, `getSectorWarpBundle`,
 *   `getSectorLiteBundle`. baseUrl 은 `TJLabsResourceNetworkConstants.USER_URL` 또는
 *   `WARP_USER_URL` (TJ-620 이후 둘 다 Jupiter 올림푸스 도메인).
 *
 * - **On-prem (하나 PMS) 경로**: `getWarpBundleOnPrem`, `getVenusBundleOnPrem`. baseUrl 은
 *   `OnPremRoutingState.baseUrl` (= `TJHanaEnvironment.setBaseUrl(...)` 값).
 *   `v2/warp/...` / `v2/venus/...` 접두어 고정이고 TJ-620 변경과 무관. 하나은행 PMS 서버 쪽에서
 *   deprecate 되지 않는 한 그대로 유지한다.
 *
 * 두 그룹을 섞어 호출하면 안 된다 — baseUrl 과 토큰 체계가 다르므로 반드시 분기 블록 안에서만
 * 사용. 신규 cloud endpoint 추가 시 기존 on-prem 함수에 영향 없는지 확인.
 */
internal interface PostInput {
    @Headers(
        "accept: application/json",
        "content-type: application/json"
    )

    // 2026-09-10 이하 스펙 (single sector, JSON 응답). VENUS lite 및 legacy 소비자 fallback 용.
    @GET("/{server_version}/sectors/{pk}/bundle")
    fun getSectorBundle(
        @Path("server_version") serverVersion: String,
        @Path("pk") pk: Int,
        @Query("operating_system") os: String = "Android",
    ): Call<SectorBundleMetaOutput>

    // 2026-09-28+ 스펙: sector_ids 를 반복 키(`sector_ids=42&sector_ids=7`)로 실어 보내는 통합 endpoint.
    // 응답은 여전히 `{url, version_id}` 이지만 url 은 zip 을 가리킨다. sector_ids 는 1~5개.
    // Retrofit 의 `@Query("k") List<T>` 는 기본이 반복 키라 서버 요구 형식(OpenAPI 3 배열 기본형)에 부합.
    @GET("/{server_version}/sectors/bundle")
    fun getSectorBundleV2(
        @Path("server_version") serverVersion: String,
        @Query("sector_ids") sectorIds: List<Int>,
        @Query("operating_system") os: String = "Android",
    ): Call<SectorBundleMetaOutput>

    // TJ-620 (2026-10-07+): Warp 전용 신규 endpoint. Jupiter 통합 도메인에서 `/sectors/warp-bundle`
    //   을 호출하고, zip 응답(`{url, version_id}`) 을 돌려받는다. Jupiter V2 와 달리 단일 sector 만
    //   필요한 K-BOS·Hana 소비자를 위해 호출측이 `[sectorId]` 로 넣어 쓴다.
    @GET("/{server_version}/sectors/warp-bundle")
    fun getSectorWarpBundle(
        @Path("server_version") serverVersion: String,
        @Query("sector_ids") sectorIds: List<Int>,
        @Query("operating_system") os: String = "Android",
    ): Call<SectorBundleMetaOutput>


    @GET("/{server_version}/sectors/{pk}/bundle/lite")
    fun getSectorLiteBundle(
        @Path("server_version") serverVersion: String,
        @Path("pk") pk: Int,
        @Query("operating_system") os: String = "Android",
    ): Call<SectorBundleMetaOutput>

    // ---- On-prem PMS endpoints (`/v2/warp`, `/v2/venus` 접두어 고정) ----
    // cloud 의 `{server_version}` 자리에 `v2/warp` · `v2/venus` 를 넣는 대신
    // 명시적인 별도 endpoint 를 둔다. 응답 body 는 cloud 와 동일한 flat
    // `{url, version_id}` 이므로 [SectorBundleMetaOutput] 을 재사용.

    // relative path (leading / 없음) — baseUrl 의 path 부분 (예: 하나 서버의 "/api") 을
    // 존중해서 append 되도록. 절대 경로로 두면 Retrofit 이 baseUrl 의 path 를 버림.
    // 여기에 명시적으로 `api/` 를 붙이면 baseUrl 이 `/api` 를 포함할 때 이중이 되므로 주의.
    @GET("v2/warp/sectors/{pk}/bundle")
    fun getWarpBundleOnPrem(
        @Path("pk") pk: Int,
        @Query("operating_system") os: String = "Android",
    ): Call<SectorBundleMetaOutput>

    @GET("v2/venus/sectors/{pk}/bundle")
    fun getVenusBundleOnPrem(
        @Path("pk") pk: Int,
        @Query("operating_system") os: String = "Android",
    ): Call<SectorBundleMetaOutput>

    // 2026-09-28+ 흐름에서는 bundle 파일 다운로드가 Retrofit 을 거치지 않고
    // [TJLabsBundleDataManager.downloadBundleFile] 이 HttpURLConnection 으로 직접 스트리밍한다
    // (40MB 급 zip 이라 ResponseBody.string() 로 통째로 메모리에 올리면 부담이 크다).
    // 따라서 `getSectorBundleJsonRaw` 는 더 이상 필요하지 않다.
}

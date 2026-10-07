package com.tjlabs.tjlabsresource_sdk_android

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

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

package com.tjlabs.tjlabsresource_sdk_android

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

internal interface PostInput {
    @Headers(
        "accept: application/json",
        "content-type: application/json"
    )

    @GET("/{server_version}/sectors/{pk}/bundle")
    fun getSectorBundle(
        @Path("server_version") serverVersion: String,
        @Path("pk") pk: Int,
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

    @GET
    fun getSectorBundleJsonRaw(
        @Url url: String
    ): Call<ResponseBody>
}

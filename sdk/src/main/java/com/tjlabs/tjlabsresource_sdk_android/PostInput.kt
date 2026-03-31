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

    @GET
    fun getSectorBundleJsonRaw(
        @Url url: String
    ): Call<ResponseBody>
}

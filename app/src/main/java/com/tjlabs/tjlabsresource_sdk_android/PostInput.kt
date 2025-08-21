package com.tjlabs.tjlabsresource_sdk_android

import retrofit2.Call
import retrofit2.http.*

internal interface PostInput {
    @Headers(
        "accept: application/json",
        "content-type: application/json"
    )

    @GET("/{server_version}/sectors/{pk}")
    fun getSector(
        @Path("server_version") serverVersion: String,
        @Path("pk") pk: Int
    ): Call<SectorOutput>

    @GET("/{server_version}/levels/{pk}/entrance")
    fun getEntrance(
        @Path("server_version") serverVersion: String,
        @Path("pk") pk: Int,
        @Query("operating_system") os: String
    ): Call<EntranceOutput>

    @GET("/{server_version}/levels/{pk}/scale")
    fun getScaleOffset(
        @Path("server_version") serverVersion: String,
        @Path("pk") pk: Int,
        @Query("operating_system") os: String
    ): Call<ScaleOffsetOutput>

    @GET("/{server_version}/levels/{pk}/path")
    fun getPathPixel(
        @Path("server_version") serverVersion: String,
        @Path("pk") pk: Int,
        @Query("operating_system") os: String
    ): Call<PathPixelOutput>

    @GET("/{server_version}/levels/{pk}/geofence")
    fun getGeofence(
        @Path("server_version") serverVersion: String,
        @Path("pk") pk: Int,
        @Query("operating_system") os: String
    ): Call<GeofenceData>

    @GET("/{server_version}/sectors/{pk}/parameter")
    fun getSectorParam(
        @Path("server_version") serverVersion: String,
        @Path("pk") pk: Int,
        @Query("operating_system") os: String
    ): Call<SectorParameterOutput>

    @GET("/{server_version}/levels/{pk}/parameter")
    fun getLevelParam(
        @Path("server_version") serverVersion: String,
        @Path("pk") pk: Int,
        @Query("operating_system") os: String
    ): Call<LevelParameterOutput>

    @GET("/{server_version}/levels/{pk}/unit")
    fun getUnit(
        @Path("server_version") serverVersion: String,
        @Path("pk") pk: Int
    ): Call<UnitOutput>
}
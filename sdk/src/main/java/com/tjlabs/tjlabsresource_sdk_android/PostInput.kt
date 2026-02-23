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

    @GET("/{server_version}/levels/{pk}/entrances")
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
        @Path("pk") pk: Int
    ): Call<GeofenceData>

    //제거
    @GET("/{server_version}/sectors/{pk}/parameter")
    fun getSectorParam(
        @Path("server_version") serverVersion: String,
        @Path("pk") pk: Int,
        @Query("operating_system") os: String
    ): Call<SectorParameterOutput>

    //제거
    @GET("/{server_version}/levels/{pk}/parameter")
    fun getLevelParam(
        @Path("server_version") serverVersion: String,
        @Path("pk") pk: Int,
        @Query("operating_system") os: String
    ): Call<LevelParameterOutput>

    @GET("/{server_version}/levels/{pk}/units")
    fun getLevelUnits(
        @Path("server_version") serverVersion: String,
        @Path("pk") pk: Int,
        @Query("category") category: Category?
    ): Call<LevelUnitsOutput>

    @GET("/{server_version}/levels/{pk}")
    fun getLevelWards(
        @Path("server_version") serverVersion: String,
        @Path("pk") pk: Int
    ): Call<LevelWardsOutput>

    @GET("/{user_affine_server_version}/sectors/{sector_id}/wgs84-transform")
    fun getUserAffineTrans(
        @Path("user_affine_server_version") userAffineServerVersion: String,
        @Path("sector_id") sectorId: Int
    ): Call<AffineTransParamOutput>

}
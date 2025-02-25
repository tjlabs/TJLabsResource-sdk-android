package com.tjlabs.tjlabsresource_sdk_android

import retrofit2.Call
import retrofit2.http.*

internal interface PostInput {
    @Headers(
        "accept: application/json",
        "content-type: application/json"
    )
    @POST("/{path_pixel_version}/path")
    fun postPathPixel(@Body param: SectorInput, @Path("path_pixel_version") pathPixelVersion : String) : Call<OutputPathPixel>

    @POST("/{scale_version}/scale")
    fun postSectorScale(@Body param: SectorInput, @Path("scale_version") scaleVersion : String) : Call<ScaleOutputList>

    @POST("/{level_version}/level")
    fun postLevel(@Body param: SectorIdInput, @Path("level_version") levelVersion : String) : Call<LevelOutputList>

    @POST("/{entrance_version}/entrance")
    fun postEntrance(@Body param: SectorInput, @Path("entrance_version") entranceVersion : String) : Call<EntranceOutputList>

    @POST("/{unit_version}/unit")
    fun postUnit(@Body param: SectorIdInput, @Path("unit_version") unitVersion : String) : Call<UnitOutputList>

}
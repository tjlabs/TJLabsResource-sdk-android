package com.tjlabs.tjlabsresource_sdk_android

import retrofit2.Call
import retrofit2.http.*

interface PostInput {
    @Headers(
        "accept: application/json",
        "content-type: application/json"
    )
    @POST("/{path_pixel_version}/path")
    fun postPathPixel(@Body param: InputSector, @Path("path_pixel_version") pathPixelVersion : String) : Call<OutputPathPixel>

}
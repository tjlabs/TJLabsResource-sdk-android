package com.tjlabs.tjlabsresource_sdk_android

import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

internal object TJLabsResourceNetworkManager {
    fun postPathPixel(url : String, input : InputSector, pathPixelServerVersion: String, completion: (Int, OutputPathPixel) -> Unit) {
        val retrofit = TJLabsResourceNetworkConstant.genRetrofit(url)
        val postGeofence = retrofit.create(PostInput::class.java)
        postGeofence.postPathPixel(input, pathPixelServerVersion).enqueue(object :
            Callback<OutputPathPixel> {
            override fun onFailure(call: Call<OutputPathPixel>, t: Throwable) {
                completion(500, OutputPathPixel())
            }
            override fun onResponse(call: Call<OutputPathPixel>, response: Response<OutputPathPixel>) {
                val statusCode = response.code()
                if (statusCode in 200 until 300) {
                    val resultData = response.body()?: OutputPathPixel()
                    completion(statusCode, resultData)
                } else {
                    completion(500,  OutputPathPixel())
                }
            }
        })
    }
}
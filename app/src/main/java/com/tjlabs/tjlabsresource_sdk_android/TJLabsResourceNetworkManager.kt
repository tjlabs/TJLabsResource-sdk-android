package com.tjlabs.tjlabsresource_sdk_android

import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

internal object TJLabsResourceNetworkManager {
    fun postPathPixel(url : String, input : SectorInput, pathPixelServerVersion: String, completion: (Int, OutputPathPixel) -> Unit) {
        val retrofit = TJLabsResourceNetworkConstant.genRetrofit(url)
        val postPathPixel = retrofit.create(PostInput::class.java)
        postPathPixel.postPathPixel(input, pathPixelServerVersion).enqueue(object :
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

    fun postScaleOffset(url : String, input : SectorInput, scaleServerVersion : String, completion: (Int, ScaleOutputList) -> Unit) {
        val retrofit = TJLabsResourceNetworkConstant.genRetrofit(url)
        val postScaleInput = retrofit.create(PostInput::class.java)
        postScaleInput.postSectorScale(input, scaleServerVersion).enqueue(object :
            Callback<ScaleOutputList> {
            override fun onFailure(call: Call<ScaleOutputList>, t: Throwable) {
                completion(500, ScaleOutputList())
            }
            override fun onResponse(call: Call<ScaleOutputList>, response: Response<ScaleOutputList>) {
                val statusCode = response.code()
                if (statusCode in 200 until 300) {
                    val resultData = response.body()?: ScaleOutputList()
                    completion(statusCode, resultData)
                } else {
                    completion(500,  ScaleOutputList())
                }
            }
        })
    }

    fun postBuildingLevel(url : String, input : SectorIdInput, levelVersion : String, completion: (Int, LevelOutputList) -> Unit) {
        val retrofit = TJLabsResourceNetworkConstant.genRetrofit(url)
        val postLevel = retrofit.create(PostInput::class.java)
        postLevel.postLevel(input, levelVersion).enqueue(object :
            Callback<LevelOutputList> {
            override fun onFailure(call: Call<LevelOutputList>, t: Throwable) {
                completion(500, LevelOutputList())
            }
            override fun onResponse(call: Call<LevelOutputList>, response: Response<LevelOutputList>) {
                val statusCode = response.code()
                if (statusCode in 200 until 300) {
                    val resultData = response.body()?: LevelOutputList()
                    completion(statusCode, resultData)
                } else {
                    completion(500,  LevelOutputList())
                }
            }
        })
    }

    fun postEntrance(url : String, input : SectorInput, entranceServerVersion: String, completion: (Int, EntranceOutputList) -> Unit) {
        val retrofit = TJLabsResourceNetworkConstant.genRetrofit(url)
        val postEntrance = retrofit.create(PostInput::class.java)
        postEntrance.postEntrance(input, entranceServerVersion).enqueue(object :
            Callback<EntranceOutputList> {
            override fun onFailure(call: Call<EntranceOutputList>, t: Throwable) {
                completion(500, EntranceOutputList())
            }
            override fun onResponse(call: Call<EntranceOutputList>, response: Response<EntranceOutputList>) {
                val statusCode = response.code()
                if (statusCode in 200 until 300) {
                    val resultData = response.body()?: EntranceOutputList()
                    completion(statusCode, resultData)
                } else {
                    completion(500,  EntranceOutputList())
                }
            }
        })
    }

    fun postUnit(url : String, input : SectorIdInput, unitServerVersion: String, completion: (Int, UnitOutputList) -> Unit) {
        val retrofit = TJLabsResourceNetworkConstant.genRetrofit(url)
        val postUnit = retrofit.create(PostInput::class.java)
        postUnit.postUnit(input, unitServerVersion).enqueue(object :
            Callback<UnitOutputList> {
            override fun onFailure(call: Call<UnitOutputList>, t: Throwable) {
                completion(500, UnitOutputList())
            }
            override fun onResponse(call: Call<UnitOutputList>, response: Response<UnitOutputList>) {
                val statusCode = response.code()
                if (statusCode in 200 until 300) {
                    val resultData = response.body()?: UnitOutputList()
                    completion(statusCode, resultData)
                } else {
                    completion(500,  UnitOutputList())
                }
            }
        })
    }

    fun postParameter(url : String, input : SectorInput, parameterServerVersion : String, completion: (Int, ParameterData) -> Unit) {
        val retrofit = TJLabsResourceNetworkConstant.genRetrofit(url)
        val postParameter = retrofit.create(PostInput::class.java)
        postParameter.postParameter(input, parameterServerVersion).enqueue(object :
            Callback<ParameterData> {
            override fun onFailure(call: Call<ParameterData>, t: Throwable) {
                completion(500, ParameterData())
            }
            override fun onResponse(call: Call<ParameterData>, response: Response<ParameterData>) {
                val statusCode = response.code()
                if (statusCode in 200 until 300) {
                    val resultData = response.body()?: ParameterData()
                    completion(statusCode, resultData)
                } else {
                    completion(500,  ParameterData())
                }
            }
        })
    }
}
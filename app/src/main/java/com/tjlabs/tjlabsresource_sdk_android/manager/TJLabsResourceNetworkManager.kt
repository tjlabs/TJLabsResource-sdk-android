package com.tjlabs.tjlabsresource_sdk_android.manager

import com.tjlabs.tjlabsresource_sdk_android.EntranceOutput
import com.tjlabs.tjlabsresource_sdk_android.GeofenceData
import com.tjlabs.tjlabsresource_sdk_android.LevelIdOsInput
import com.tjlabs.tjlabsresource_sdk_android.LevelParameterOutput
import com.tjlabs.tjlabsresource_sdk_android.LevelWardsOutput
import com.tjlabs.tjlabsresource_sdk_android.PathPixelOutput
import com.tjlabs.tjlabsresource_sdk_android.PostInput
import com.tjlabs.tjlabsresource_sdk_android.ScaleOffsetOutput
import com.tjlabs.tjlabsresource_sdk_android.SectorIdInput
import com.tjlabs.tjlabsresource_sdk_android.SectorIdOsInput
import com.tjlabs.tjlabsresource_sdk_android.SectorOutput
import com.tjlabs.tjlabsresource_sdk_android.SectorParameterOutput
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstants
import com.tjlabs.tjlabsresource_sdk_android.UnitOutput
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

internal object TJLabsResourceNetworkManager {
    fun getSector(url : String, input : SectorIdInput, serverVersion : String, completion : (Int, String, SectorOutput?) -> Unit) {
        val retrofit = TJLabsResourceNetworkConstants.genRetrofit(url)
        val getSector = retrofit.create(PostInput::class.java)
        getSector.getSector(serverVersion, input.sector_id).enqueue(object :
            Callback<SectorOutput> {
            override fun onFailure(call: Call<SectorOutput>, t: Throwable) {
                completion(500, "(TJLabsResource) Failure : getSector // status code : 500 // input : $input", null)
            }
            override fun onResponse(call: Call<SectorOutput>, response: Response<SectorOutput>) {
                val statusCode = response.code()
                if (statusCode in 200 until 300) {
                    val resultData = response.body()
                    completion(statusCode, "(TJLabsResource) Success : getSector", resultData)
                } else {
                    completion(statusCode, "(TJLabsResource) Error : getSector // status code : $statusCode // input : $input", null)
                }
            }
        })


    }

    fun getEntrance(url : String, input : LevelIdOsInput, serverVersion: String, completion: (Int, String, EntranceOutput?) -> Unit) {
        val retrofit = TJLabsResourceNetworkConstants.genRetrofit(url)
        val postEntrance = retrofit.create(PostInput::class.java)
        postEntrance.getEntrance(serverVersion, input.level_id, input.operating_system).enqueue(object :
            Callback<EntranceOutput> {
            override fun onFailure(call: Call<EntranceOutput>, t: Throwable) {
                completion(500, "(TJLabsResource) Failure : getEntrance  // status code : 500  // input : $input", null)
            }
            override fun onResponse(call: Call<EntranceOutput>, response: Response<EntranceOutput>) {
                val statusCode = response.code()
                if (statusCode in 200 until 300) {
                    val resultData = response.body()
                    completion(statusCode, "(TJLabsResource) Success : getEntrance", resultData)
                } else {
                    completion(statusCode, "(TJLabsResource) Error : getEntrance // status code : $statusCode // input : $input", null)
                }
            }
        })
    }

    fun getScaleOffset(url : String, input : LevelIdOsInput, serverVersion : String, completion: (Int, String, ScaleOffsetOutput?) -> Unit) {
        val retrofit = TJLabsResourceNetworkConstants.genRetrofit(url)
        val getScaleOffset = retrofit.create(PostInput::class.java)
        getScaleOffset.getScaleOffset(serverVersion, input.level_id, input.operating_system).enqueue(object :
            Callback<ScaleOffsetOutput> {
            override fun onFailure(call: Call<ScaleOffsetOutput>, t: Throwable) {
                completion(500, "(TJLabsResource) Failure : getScaleOffset  // status code : 500  // input : $input", null)
            }
            override fun onResponse(call: Call<ScaleOffsetOutput>, response: Response<ScaleOffsetOutput>) {
                val statusCode = response.code()
                if (statusCode in 200 until 300) {
                    val resultData = response.body()
                    completion(statusCode, "(TJLabsResource) Success : getScaleOffset", resultData)
                } else {
                    completion(statusCode, "(TJLabsResource) Error : getScaleOffset // status code : $statusCode // input : $input", null)
                }
            }
        })
    }

    fun getPathPixel(url : String, input : LevelIdOsInput, serverVersion : String, completion: (Int, String, PathPixelOutput?) -> Unit) {
        val retrofit = TJLabsResourceNetworkConstants.genRetrofit(url)
        val getPathPixel = retrofit.create(PostInput::class.java)
        getPathPixel.getPathPixel(serverVersion, input.level_id, input.operating_system).enqueue(object :
            Callback<PathPixelOutput> {
            override fun onFailure(call: Call<PathPixelOutput>, t: Throwable) {
                completion(500, "(TJLabsResource) Failure : getPathPixel  // status code : 500  // input : $input", null)
            }
            override fun onResponse(call: Call<PathPixelOutput>, response: Response<PathPixelOutput>) {
                val statusCode = response.code()
                if (statusCode in 200 until 300) {
                    val resultData = response.body()
                    completion(statusCode, "(TJLabsResource) Success : getPathPixel", resultData)
                } else {
                    completion(statusCode, "(TJLabsResource) Error : getPathPixel // status code : $statusCode // input : $input", null)
                }
            }
        })
    }

    fun getGeofence(url : String, input : LevelIdOsInput, serverVersion : String, completion: (Int, String, GeofenceData?) -> Unit) {
        val retrofit = TJLabsResourceNetworkConstants.genRetrofit(url)
        val getGeofence = retrofit.create(PostInput::class.java)
        getGeofence.getGeofence(serverVersion, input.level_id, input.operating_system).enqueue(object :
            Callback<GeofenceData> {
            override fun onFailure(call: Call<GeofenceData>, t: Throwable) {
                completion(500, "(TJLabsResource) Failure : getGeofence  // status code : 500  // input : $input", null)
            }
            override fun onResponse(call: Call<GeofenceData>, response: Response<GeofenceData>) {
                val statusCode = response.code()
                if (statusCode in 200 until 300) {
                    val resultData = response.body()
                    completion(statusCode, "(TJLabsResource) Success : getGeofence", resultData)
                } else {
                    completion(statusCode, "(TJLabsResource) Error : getGeofence // status code : $statusCode // input : $input", null)
                }
            }
        })
    }


    fun getSectorParam(url : String, input : SectorIdOsInput, serverVersion : String, completion: (Int, String, SectorParameterOutput?) -> Unit) {
        val retrofit = TJLabsResourceNetworkConstants.genRetrofit(url)
        val getSectorParam = retrofit.create(PostInput::class.java)
        getSectorParam.getSectorParam(serverVersion, input.sector_id, input.operating_system).enqueue(object :
            Callback<SectorParameterOutput> {
            override fun onFailure(call: Call<SectorParameterOutput>, t: Throwable) {
                completion(500, "(TJLabsResource) Failure : getSectorParam  // status code : 500  // input : $input", null)
            }
            override fun onResponse(call: Call<SectorParameterOutput>, response: Response<SectorParameterOutput>) {
                val statusCode = response.code()
                if (statusCode in 200 until 300) {
                    val resultData = response.body()
                    completion(statusCode, "(TJLabsResource) Success : getSectorParam", resultData)
                } else {
                    completion(statusCode, "(TJLabsResource) Error : getSectorParam // status code : $statusCode // input : $input", null)
                }
            }
        })
    }

    fun getLevelParam(url : String, input : LevelIdOsInput, serverVersion : String, completion: (Int, String, LevelParameterOutput?) -> Unit) {
        val retrofit = TJLabsResourceNetworkConstants.genRetrofit(url)
        val getLevelParam = retrofit.create(PostInput::class.java)
        getLevelParam.getLevelParam(serverVersion, input.level_id, input.operating_system).enqueue(object :
            Callback<LevelParameterOutput> {
            override fun onFailure(call: Call<LevelParameterOutput>, t: Throwable) {
                completion(500, "(TJLabsResource) Failure : getLevelParam  // status code : 500  // input : $input", null)
            }
            override fun onResponse(call: Call<LevelParameterOutput>, response: Response<LevelParameterOutput>) {
                val statusCode = response.code()
                if (statusCode in 200 until 300) {
                    val resultData = response.body()
                    completion(statusCode, "(TJLabsResource) Success : getLevelParam", resultData)
                } else {
                    completion(statusCode, "(TJLabsResource) Error : getLevelParam // status code : $statusCode // input : $input", null)
                }
            }
        })
    }

    fun getUnit(url : String, input : LevelIdOsInput, serverVersion : String, completion: (Int, String, UnitOutput?) -> Unit) {
        val retrofit = TJLabsResourceNetworkConstants.genRetrofit(url)
        val getLevelParam = retrofit.create(PostInput::class.java)
        getLevelParam.getUnit(serverVersion, input.level_id).enqueue(object :
            Callback<UnitOutput> {
            override fun onFailure(call: Call<UnitOutput>, t: Throwable) {
                completion(500, "(TJLabsResource) Failure : getUnit  // status code : 500  // input : $input", null)
            }
            override fun onResponse(call: Call<UnitOutput>, response: Response<UnitOutput>) {
                val statusCode = response.code()
                if (statusCode in 200 until 300) {
                    val resultData = response.body()
                    completion(statusCode, "(TJLabsResource) Success : getUnit", resultData)
                } else {
                    completion(statusCode, "(TJLabsResource) Error : getUnit // status code : $statusCode // input : $input", null)
                }
            }
        })
    }

    fun getLevelWards(url : String, input : LevelIdOsInput, serverVersion : String, completion : (Int, String, LevelWardsOutput?) -> Unit) {
        val retrofit = TJLabsResourceNetworkConstants.genRetrofit(url)
        val getLevelWards = retrofit.create(PostInput::class.java)
        getLevelWards.getLevelWards(serverVersion, input.level_id).enqueue(object :
            Callback<LevelWardsOutput> {
            override fun onFailure(call: Call<LevelWardsOutput>, t: Throwable) {
                completion(500, "(TJLabsResource) Failure : getLevelWards  // status code : 500  // input : $input", null)
            }
            override fun onResponse(call: Call<LevelWardsOutput>, response: Response<LevelWardsOutput>) {
                val statusCode = response.code()
                if (statusCode in 200 until 300) {
                    val resultData = response.body()
                    completion(statusCode, "(TJLabsResource) Success : getLevelWards", resultData)
                } else {
                    completion(statusCode, "(TJLabsResource) Error : getLevelWards // status code : $statusCode // input : $input", null)
                }
            }
        })

    }


}
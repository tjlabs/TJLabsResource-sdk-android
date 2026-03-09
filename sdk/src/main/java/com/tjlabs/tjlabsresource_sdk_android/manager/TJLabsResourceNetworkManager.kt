package com.tjlabs.tjlabsresource_sdk_android.manager

import android.util.Log
import com.tjlabs.tjlabsresource_sdk_android.AffineTransParamOutput
import com.tjlabs.tjlabsresource_sdk_android.EntranceOutput
import com.tjlabs.tjlabsresource_sdk_android.GeofenceData
import com.tjlabs.tjlabsresource_sdk_android.GraphLevelLinksGroupsOutput
import com.tjlabs.tjlabsresource_sdk_android.GraphLevelLinksOutput
import com.tjlabs.tjlabsresource_sdk_android.GraphLevelNodesOutput
import com.tjlabs.tjlabsresource_sdk_android.GraphLevelPathsOutput
import com.tjlabs.tjlabsresource_sdk_android.LevelIdOsInput
import com.tjlabs.tjlabsresource_sdk_android.LevelLandmarkOutput
import com.tjlabs.tjlabsresource_sdk_android.LevelParameterOutput
import com.tjlabs.tjlabsresource_sdk_android.LevelUnitsInput
import com.tjlabs.tjlabsresource_sdk_android.LevelWardsOutput
import com.tjlabs.tjlabsresource_sdk_android.PathPixelOutput
import com.tjlabs.tjlabsresource_sdk_android.PostInput
import com.tjlabs.tjlabsresource_sdk_android.ScaleOffsetOutput
import com.tjlabs.tjlabsresource_sdk_android.SectorIdInput
import com.tjlabs.tjlabsresource_sdk_android.SectorIdOsInput
import com.tjlabs.tjlabsresource_sdk_android.SectorOutput
import com.tjlabs.tjlabsresource_sdk_android.SectorParameterOutput
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstants
import com.tjlabs.tjlabsresource_sdk_android.LevelUnitsOutput
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
        getGeofence.getGeofence(serverVersion, input.level_id).enqueue(object :
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

    fun getLevelUnits(url : String, input : LevelUnitsInput, serverVersion : String, completion: (Int, String, LevelUnitsOutput?) -> Unit) {
        val retrofit = TJLabsResourceNetworkConstants.genRetrofit(url)
        val getLevelParam = retrofit.create(PostInput::class.java)
        getLevelParam.getLevelUnits(serverVersion, input.level_id, input.category).enqueue(object :
            Callback<LevelUnitsOutput> {
            override fun onFailure(call: Call<LevelUnitsOutput>, t: Throwable) {
                completion(500, "(TJLabsResource) Failure : getUnit  // status code : 500  // input : $input", null)
            }
            override fun onResponse(call: Call<LevelUnitsOutput>, response: Response<LevelUnitsOutput>) {
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

    fun getUserAffineTrans(url : String, sectorId : Int, userAffineTransVersion : String, completion : (Int, String, AffineTransParamOutput?) -> Unit) {
        val retrofit = TJLabsResourceNetworkConstants.genRetrofit(url)
        val getAffineTrans = retrofit.create(PostInput::class.java)
        getAffineTrans.getUserAffineTrans(userAffineTransVersion, sectorId).enqueue(object :
            Callback<AffineTransParamOutput>{
            override fun onFailure(call: Call<AffineTransParamOutput>, t: Throwable) {
                completion(500, "(TJLabsResource) Failure : getLevelWards  // status code : 500  // sectorId : $sectorId", null)
            }

            override fun onResponse(
                call: Call<AffineTransParamOutput>,
                response: Response<AffineTransParamOutput>
            ) {
                val statusCode = response.code()
                if (statusCode in 200 until 300){
                    val resultData = response.body()
                    if (resultData != null) {
                        completion(statusCode, "(TJLabsResource) Success : getUserAffineTrans", resultData)
                    } else {
                        completion(statusCode, "(TJLabsResource) AffineParam code : $statusCode // result == null", null)
                    }
                } else if (statusCode in 400 until 500){
                    // 값이 없을 경우에도 400 -> 실행은 된다.
                    completion(statusCode, "(TJLabsResource) AffineParam : sector $sectorId is not support affine converting // code : $statusCode", null)
                } else {
                    completion(statusCode, "(TJLabsResource) Error : getUserAffineTrans // status code : $statusCode // sectorId : $sectorId", null)
                }
            }
        })
    }

    fun getLevelNodes(url: String, input: LevelIdOsInput, serverVersion: String, completion: (Int, String, GraphLevelNodesOutput?) -> Unit) {
        val retrofit = TJLabsResourceNetworkConstants.genRetrofit(url)
        val getLevelNodes = retrofit.create(PostInput::class.java)
        getLevelNodes.getLevelNodes(serverVersion, input.level_id).enqueue(object :
            Callback<GraphLevelNodesOutput> {
            override fun onFailure(call: Call<GraphLevelNodesOutput>, t: Throwable) {
                completion(500, "(TJLabsResource) Failure : getLevelNodes  // status code : 500  // input : $input", null)
            }
            override fun onResponse(call: Call<GraphLevelNodesOutput>, response: Response<GraphLevelNodesOutput>) {
                val statusCode = response.code()
                if (statusCode in 200 until 300) {
                    val resultData = response.body()
                    completion(statusCode, "(TJLabsResource) Success : getLevelNodes", resultData)
                } else {
                    completion(statusCode, "(TJLabsResource) Error : getLevelNodes // status code : $statusCode // input : $input", null)
                }
            }
        })
    }

    fun getLevelLinks(url: String, input: LevelIdOsInput, serverVersion: String, completion: (Int, String, GraphLevelLinksOutput?) -> Unit) {
        val retrofit = TJLabsResourceNetworkConstants.genRetrofit(url)
        val getLevelLinks = retrofit.create(PostInput::class.java)
        getLevelLinks.getLevelLinks(serverVersion, input.level_id).enqueue(object :
            Callback<GraphLevelLinksOutput> {
            override fun onFailure(call: Call<GraphLevelLinksOutput>, t: Throwable) {
                completion(500, "(TJLabsResource) Failure : getLevelLinks  // status code : 500  // input : $input", null)
            }
            override fun onResponse(call: Call<GraphLevelLinksOutput>, response: Response<GraphLevelLinksOutput>) {
                val statusCode = response.code()
                if (statusCode in 200 until 300) {
                    val resultData = response.body()
                    completion(statusCode, "(TJLabsResource) Success : getLevelLinks", resultData)
                } else {
                    completion(statusCode, "(TJLabsResource) Error : getLevelLinks // status code : $statusCode // input : $input", null)
                }
            }
        })
    }

    fun getLevelLinkGroups(url: String, input: LevelIdOsInput, serverVersion: String, completion: (Int, String, GraphLevelLinksGroupsOutput?) -> Unit) {
        val retrofit = TJLabsResourceNetworkConstants.genRetrofit(url)
        val getLevelLinkGroups = retrofit.create(PostInput::class.java)
        getLevelLinkGroups.getLevelLinkGroups(serverVersion, input.level_id).enqueue(object :
            Callback<GraphLevelLinksGroupsOutput> {
            override fun onFailure(call: Call<GraphLevelLinksGroupsOutput>, t: Throwable) {
                completion(500, "(TJLabsResource) Failure : getLevelLinkGroups  // status code : 500  // input : $input", null)
            }
            override fun onResponse(call: Call<GraphLevelLinksGroupsOutput>, response: Response<GraphLevelLinksGroupsOutput>) {
                val statusCode = response.code()
                if (statusCode in 200 until 300) {
                    val resultData = response.body()
                    completion(statusCode, "(TJLabsResource) Success : getLevelLinkGroups", resultData)
                } else {
                    completion(statusCode, "(TJLabsResource) Error : getLevelLinkGroups // status code : $statusCode // input : $input", null)
                }
            }
        })
    }

    fun getLevelPaths(url: String, input: LevelIdOsInput, serverVersion: String, completion: (Int, String, GraphLevelPathsOutput?) -> Unit) {
        val retrofit = TJLabsResourceNetworkConstants.genRetrofit(url)
        val getLevelPaths = retrofit.create(PostInput::class.java)
        getLevelPaths.getLevelPaths(serverVersion, input.level_id, input.operating_system).enqueue(object :
            Callback<GraphLevelPathsOutput> {
            override fun onFailure(call: Call<GraphLevelPathsOutput>, t: Throwable) {
                completion(500, "(TJLabsResource) Failure : getLevelPaths  // status code : 500  // input : $input", null)
            }
            override fun onResponse(call: Call<GraphLevelPathsOutput>, response: Response<GraphLevelPathsOutput>) {
                val statusCode = response.code()
                if (statusCode in 200 until 300) {
                    val resultData = response.body()
                    completion(statusCode, "(TJLabsResource) Success : getLevelPaths", resultData)
                } else {
                    completion(statusCode, "(TJLabsResource) Error : getLevelPaths // status code : $statusCode // input : $input", null)
                }
            }
        })
    }

    fun getLevelLandmarks(
        url: String,
        input: LevelIdOsInput,
        serverVersion: String,
        completion: (Int, String, LevelLandmarkOutput?) -> Unit
    ) {
        val retrofit = TJLabsResourceNetworkConstants.genRetrofit(url)
        val getLevelLandmarks = retrofit.create(PostInput::class.java)
        Log.d("CheckLandmark", "getLevelLandmarks : $getLevelLandmarks")

        getLevelLandmarks.getLevelLandmarks(serverVersion, input.level_id).enqueue(object :
            Callback<LevelLandmarkOutput> {
            override fun onFailure(call: Call<LevelLandmarkOutput>, t: Throwable) {
                Log.d("CheckLandmark", "onFailure : $input")

                completion(500, "(TJLabsResource) Failure : getLevelLandmarks  // status code : 500  // input : $input", null)
            }
            override fun onResponse(
                call: Call<LevelLandmarkOutput>,
                response: Response<LevelLandmarkOutput>
            ) {
                val statusCode = response.code()
                Log.d("CheckLandmark", "input : $input // response : ${response.body()} // call : ${call.request()}")
                if (statusCode in 200 until 300) {
                    val resultData = response.body()
                    completion(statusCode, "(TJLabsResource) Success : getLevelLandmarks", resultData)
                } else {
                    completion(statusCode, "(TJLabsResource) Error : getLevelLandmarks // status code : $statusCode // input : $input", null)
                }
            }
        })
    }

}

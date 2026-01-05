package com.tjlabs.tjlabsresource_sdk_android.manager

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tjlabs.tjlabsresource_sdk_android.BuildingOutput
import com.tjlabs.tjlabsresource_sdk_android.LandmarkData
import com.tjlabs.tjlabsresource_sdk_android.SectorOutput
import com.tjlabs.tjlabsresource_sdk_android.util.TJLogger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors


internal interface LandmarkDelegate {
    fun onLandmarkData(landmarkKey : String, data: Map<String, LandmarkData>)
    fun onLandmarkError(landmarkKey : String)
}


internal class TJLabsLandmarkManager {
    companion object {
        // static var landmarksDataMap
        val landmarksDataMap: MutableMap<String, Map<String, LandmarkData>> =
            mutableMapOf()
    }

    var delegate: LandmarkDelegate? = null


    // Backward-compatible name
    fun loadNodeLinks(
        context: Context,
        sectorId: Int,
        buildingsData: List<BuildingOutput>,
        completion: (Boolean) -> Unit
    ) {
        loadLandmarks(context, sectorId, buildingsData, completion)
    }

    fun loadLandmarks(
        context: Context,
        sectorId: Int,
        buildingsData: List<BuildingOutput>,
        completion: (Boolean) -> Unit
    ) {

        val lock = Any()
        var isAllSuccess = true
        var hasAsyncWork = false

        val tasks = mutableListOf<() -> Unit>()

        fun updateSuccess(success: Boolean) {
            if (!success) {
                synchronized(lock) {
                    isAllSuccess = false
                }
            }
        }

        for (building in buildingsData) {
            for (level in building.levels) {
                if (level.name.contains("_D")) continue

                val blKey = "${sectorId}_${building.name}_${level.name}"

                // Cache hit
                val cached = landmarksDataMap[blKey]
                if (!cached.isNullOrEmpty()) {
                    delegate?.onLandmarkData(blKey, cached)
                    continue
                }

                hasAsyncWork = true

                tasks += {
                    try {
                        val fileName = "${blKey}_landmark.json"

                        val jsonString = context.assets
                            .open(fileName)
                            .bufferedReader()
                            .use { it.readText() }

                        processLandmarkJson(jsonString, blKey) { success, dict ->
                            Handler(Looper.getMainLooper()).post {
                                if (success && dict != null) {
                                    landmarksDataMap[blKey] = dict
                                    delegate?.onLandmarkData(blKey, dict)
                                    updateSuccess(true)
                                } else {
                                    delegate?.onLandmarkError(blKey)
                                    updateSuccess(false)
                                }
                            }
                        }

                    } catch (e: Exception) {
                        TJLogger.d("[processLandmarkFile] File read error for key=$blKey : ${e.localizedMessage}")
                        Handler(Looper.getMainLooper()).post {
                            delegate?.onLandmarkError(blKey)
                            updateSuccess(false)
                        }
                    }
                }
            }
        }

        // 전부 캐시 히트였던 경우
        if (!hasAsyncWork) {
            completion(true)
            return
        }

        val latch = CountDownLatch(tasks.size)

        val executor = Executors.newFixedThreadPool(4)
        tasks.forEach { task ->
            executor.execute {
                try {
                    task()
                } finally {
                    latch.countDown()
                }
            }
        }

        Executors.newSingleThreadExecutor().execute {
            latch.await()
            Handler(Looper.getMainLooper()).post {
                completion(isAllSuccess)
            }
        }
    }

    // MARK: - Helper

    private fun processLandmarkJson(
        jsonString: String,
        key: String,
        completion: (Boolean, Map<String, LandmarkData>?) -> Unit
    ) {
        val dict = decodeLandmarkDict(jsonString)
        if (dict == null) {
            completion(false, null)
        } else {
            completion(true, dict)
        }
    }

    // MARK: - Decoding

    private fun decodeLandmarkDict(jsonString: String): Map<String, LandmarkData>? {
        val gson = Gson()

        // 1) 배열 형태 시도
        try {
            val listType = object : TypeToken<List<LandmarkData>>() {}.type
            val arr: List<LandmarkData> = gson.fromJson(jsonString, listType)
            return arr.associateBy { it.ward_id }
        } catch (_: Exception) {
        }

        // 2) Wrapper 형태 시도
        return try {
            data class Wrapper(val landmarks: List<LandmarkData>)
            val wrapper = gson.fromJson(jsonString, Wrapper::class.java)
            wrapper.landmarks.associateBy { it.ward_id }
        } catch (e: Exception) {
            TJLogger.d("[decodeLandmarkDict] Landmark decoding failed: ${e.localizedMessage}")
            null
        }
    }
}
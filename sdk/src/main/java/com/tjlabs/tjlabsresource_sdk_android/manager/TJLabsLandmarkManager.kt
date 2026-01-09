package com.tjlabs.tjlabsresource_sdk_android.manager

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tjlabs.tjlabsresource_sdk_android.BuildingOutput
import com.tjlabs.tjlabsresource_sdk_android.LandmarkData
import com.tjlabs.tjlabsresource_sdk_android.SectorOutput
import com.tjlabs.tjlabsresource_sdk_android.util.TJLogger
import java.net.URL
import java.util.ResourceBundle
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean


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

    fun loadLandmarks(
        context: Context,
        sectorId: Int,
        buildingsData: List<BuildingOutput>,
        completion: (Boolean) -> Unit
    ) {
        val latch = CountDownLatch(
            buildingsData.sumOf { building ->
                building.levels.count { !it.name.contains("_D") }
            }
        )

        val isAllSuccess = AtomicBoolean(true)
        var hasAsyncWork = false
        val mainHandler = Handler(Looper.getMainLooper())

        fun updateSuccess(success: Boolean) {
            if (!success) {
                isAllSuccess.set(false)
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
                    latch.countDown()
                    continue
                }

                hasAsyncWork = true

                Executors.newSingleThreadExecutor().execute {
                    Log.d("CheckLandmark", "▶ executor start: $blKey")
                    val resourceName = "${blKey}_landmark"
                    fun resolveAssetPath(): String? {
                        val pathWithDir = "$resourceName.json"
                        val pathWithoutDir = "$resourceName.json"
                        Log.d("CheckLandmark", "pathWithDir : $pathWithDir // pathWithoutDir : $pathWithoutDir")

                        return try {
                            context.assets.open(pathWithDir).close()
                            pathWithDir
                        } catch (_: Exception) {
                            try {
                                context.assets.open(pathWithoutDir).close()
                                pathWithoutDir
                            } catch (_: Exception) {
                                null
                            }
                        }
                    }

                    val assetPath = resolveAssetPath()
                    Log.d("CheckLandmark", "assetPath : $assetPath")

                    //데이터 없으면 스킵
                    if (assetPath == null) {
                        Log.d("CheckLandmark", "No landmark asset for $blKey (skip)")
                        latch.countDown()
                        return@execute
                    }
                    processLandmarkFile(context, assetPath, blKey) { success, dict ->
                        mainHandler.post {
                            if (success && dict != null) {
                                // Cache
                                landmarksDataMap[blKey] = dict

                                // Notify
                                delegate?.onLandmarkData(blKey, dict)
                                updateSuccess(true)
                            } else {
                                delegate?.onLandmarkError(blKey)
                                updateSuccess(false)
                            }
                            latch.countDown()
                        }
                    }
                }
            }
        }

        // All cache-hit or skipped
        if (!hasAsyncWork) {
            completion(true)
            return
        }

        Thread {
            latch.await()
            mainHandler.post {
                completion(isAllSuccess.get())
            }
        }.start()
    }


    private fun loadAssetJson(
        context: Context,
        assetPath: String
    ): String? {
        return try {
            context.assets.open(assetPath)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
        } catch (e: Exception) {
            Log.d("CheckLandmark", "asset read error: $assetPath / ${e.message}")
            null
        }
    }


    private fun processLandmarkFile(
        context: Context,
        assetPath: String,
        key: String,
        completion: (Boolean, Map<String, LandmarkData>?) -> Unit
    ) {
        Log.d("CheckLandmark", "▶ processLandmarkFile start: $assetPath")
        try {
            val jsonString = loadAssetJson(context, assetPath)
                ?: run {
                    completion(false, null)
                    return
                }

            val dict = decodeLandmarkDict(jsonString)
            if (dict == null) {
                completion(false, null)
                return
            }

            Log.d("CheckLandmark", "▶ processLandmarkFile dict: $dict")

            completion(true, dict)
        } catch (e: Exception) {
            Log.d("CheckLandmark", "aFile read error for key=$key: \${e.message")
            completion(false, null)
        }
    }


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
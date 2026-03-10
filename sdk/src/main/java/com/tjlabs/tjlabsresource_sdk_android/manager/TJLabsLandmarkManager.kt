package com.tjlabs.tjlabsresource_sdk_android.manager

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.tjlabs.tjlabsresource_sdk_android.BuildingOutput
import com.tjlabs.tjlabsresource_sdk_android.LevelIdOsInput
import com.tjlabs.tjlabsresource_sdk_android.LevelLandmarkOutput
import com.tjlabs.tjlabsresource_sdk_android.LandmarkData
import com.tjlabs.tjlabsresource_sdk_android.LevelLandmark
import com.tjlabs.tjlabsresource_sdk_android.PeakData
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstants
import com.tjlabs.tjlabsresource_sdk_android.util.TJLogger
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
        sectorId: Int,
        buildingsData: List<BuildingOutput>,
        completion: (Boolean) -> Unit
    ) {
        val latch = CountDownLatch(
            buildingsData.sumOf { building ->
                building.levels.count { level ->
                    !level.name.contains("_D") && !level.name.contains("B0")
                }
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
                if (level.name.contains("_D") || level.name.contains("B0")) continue

                val blKey = "${sectorId}_${building.name}_${level.name}"
                Log.d("CheckLandmark", "blKey : $blKey")

                // Cache hit
                val cached = landmarksDataMap[blKey]
                Log.d("CheckLandmark", "cached : $cached")

                if (!cached.isNullOrEmpty()) {
                    delegate?.onLandmarkData(blKey, cached)
                    latch.countDown()
                    continue
                }

                hasAsyncWork = true

                Executors.newSingleThreadExecutor().execute {
                    val input = LevelIdOsInput(level_id = level.id)
                    TJLabsResourceNetworkManager.getLevelLandmarks(
                        TJLabsResourceNetworkConstants.getUserBaseURL(),
                        input,
                        TJLabsResourceNetworkConstants.getUserLandmarkServerVersion()
                    ) { status, msg, result ->
                        mainHandler.post {
                            if (status !in 200 until 300 || result == null) {
                                TJLogger.d(msg)
                                delegate?.onLandmarkError(blKey)
                                updateSuccess(false)
                                latch.countDown()
                                return@post
                            }

                            val dict = buildLandmarkDict(result.wards)
                            if (dict.isNotEmpty()) {
                                landmarksDataMap[blKey] = dict
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


    private fun buildLandmarkDict(items: List<LevelLandmark>): Map<String, LandmarkData> {
        val result = mutableMapOf<String, LandmarkData>()

        for (ward in items) {
            val wardKey = ward.name
            for (info in ward.rf_landmarks) {
                val matchedLinks = info.links.map { it.number }
                val peak = PeakData(
                    x = info.x,
                    y = info.y,
                    rssi = info.rssi,
                    matched_links = matchedLinks
                )

                val existing = result[wardKey]
                if (existing == null) {
                    result[wardKey] = LandmarkData(
                        ward_id = wardKey,
                        peaks = listOf(peak)
                    )
                } else {
                    result[wardKey] = existing.copy(
                        peaks = existing.peaks + peak
                    )
                }
            }
        }

        return result
    }
}

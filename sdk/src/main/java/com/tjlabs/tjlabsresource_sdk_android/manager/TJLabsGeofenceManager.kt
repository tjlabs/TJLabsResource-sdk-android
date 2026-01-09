package com.tjlabs.tjlabsresource_sdk_android.manager


import com.tjlabs.tjlabsresource_sdk_android.BuildingOutput
import com.tjlabs.tjlabsresource_sdk_android.GeofenceData
import com.tjlabs.tjlabsresource_sdk_android.LevelIdOsInput
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstants
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors


internal interface GeofenceDelegate {
    fun onGeofenceData(geofenceKey: String, data: GeofenceData)
    fun onGeofenceError(geofenceKey: String)
}

internal class TJLabsGeofenceManager {
    companion object {
        val geofenceDataMap: MutableMap<String, GeofenceData> = mutableMapOf()
    }

    var delegate: GeofenceDelegate? = null

    fun loadGeofence(
        sectorId: Int,
        buildingsData: List<BuildingOutput>,
        completion: (Boolean) -> Unit
    ) {
        val mainHandler = Handler(Looper.getMainLooper())

        val lock = Any()
        var isAllSuccess = true
        var hasAsyncWork = false

        fun updateSuccess(success: Boolean) {
            if (!success) {
                synchronized(lock) {
                    isAllSuccess = false
                }
            }
        }

        val tasks = mutableListOf<(CountDownLatch) -> Unit>()

        for (building in buildingsData) {
            for (level in building.levels) {
                if (level.name.contains("_D")) continue

                val geoKey = "${sectorId}_${building.name}_${level.name}"

                // ✅ 캐시 히트
                val cached = geofenceDataMap[geoKey]
                if (cached != null) {
                    delegate?.onGeofenceData(geoKey, cached)
                    continue
                }

                // ❌ 캐시 미스 → 비동기 작업 필요
                hasAsyncWork = true

                tasks += { latch ->
                    updateLevelGeofence(geoKey, level.id) { isSuccess ->
                        updateSuccess(isSuccess)
                        latch.countDown()
                    }
                }
            }
        }

        // 모든 level 이 캐시 히트였던 경우
        if (!hasAsyncWork) {
            completion(true)
            return
        }

        val latch = CountDownLatch(tasks.size)
        val executor = Executors.newFixedThreadPool(4)

        // 비동기 작업 병렬 실행
        tasks.forEach { task ->
            executor.execute {
                task(latch)
            }
        }

        // 모든 비동기 작업 종료 대기
        Executors.newSingleThreadExecutor().execute {
            latch.await()
            mainHandler.post {
                completion(isAllSuccess)
            }
        }
    }

    //원하는 key, level id 로 직접 요청하기
    fun updateLevelGeofence(
        key: String,
        levelId: Int,
        completion: (Boolean) -> Unit
    ) {
        val input = LevelIdOsInput(level_id = levelId)

        TJLabsResourceNetworkManager.getGeofence(
            TJLabsResourceNetworkConstants.getUserBaseURL(),
            input,
            TJLabsResourceNetworkConstants.getUserGeoServerVersion()
        ) { status, msg, result ->

            if (status != 200) {
                delegate?.onGeofenceError(key)
                completion(false)
            } else {
                if (result != null) {
                    geofenceDataMap[key] = result
                    delegate?.onGeofenceData(key, result)
                    completion(true)
                } else {
                    delegate?.onGeofenceError(key)
                    completion(false)
                }
            }
        }
    }
}
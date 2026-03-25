package com.tjlabs.tjlabsresource_sdk_android.manager

import com.tjlabs.tjlabsresource_sdk_android.BuildingOutput
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstants
import com.tjlabs.tjlabsresource_sdk_android.UnitData
import com.tjlabs.tjlabsresource_sdk_android.util.TJLogger
import android.os.Handler
import android.os.Looper
import com.tjlabs.tjlabsresource_sdk_android.LevelUnitsInput
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

internal interface LevelUnitsDelegate {
    fun onLevelUnitsData(unitKey: String, data : List<UnitData>?)
    fun onLevelUnitsDataError(unitKey: String)
}


internal class TJLabsLevelUnitsManager {
    companion object {
        val levelUnitsDataMap : MutableMap<String, List<UnitData>> = mutableMapOf()
    }

    var delegate: LevelUnitsDelegate? = null

    fun loadLevelUnits(
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

        // level 단위 비동기 작업 목록
        val tasks = mutableListOf<(CountDownLatch) -> Unit>()

        for (building in buildingsData) {
            for (level in building.levels) {
                if (level.name.contains("_D")) continue

                val unitKey = "${sectorId}_${building.name}_${level.name}"

                // ✅ 캐시 히트
                val cached = levelUnitsDataMap[unitKey]
                if (cached != null) {
                    delegate?.onLevelUnitsData(unitKey, cached)
                    continue
                }

                // ❌ 캐시 미스 → 비동기 작업 필요
                hasAsyncWork = true

                tasks += { latch ->
                    updateLevelUnit(unitKey, level.id) { isSuccess ->
                        updateSuccess(isSuccess)
                        latch.countDown()
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

        // 비동기 Unit 로딩 병렬 실행
        tasks.forEach { task ->
            executor.execute {
                task(latch)
            }
        }

        // 모든 Unit 로딩 완료 대기
        Executors.newSingleThreadExecutor().execute {
            latch.await()
            mainHandler.post {
                completion(isAllSuccess)
            }
        }
    }

    fun updateLevelUnit(
        key: String,
        levelId: Int,
        completion: (Boolean) -> Unit
    ) {
        val input = LevelUnitsInput(level_id = levelId)

        TJLabsResourceNetworkManager.getLevelUnits(
            TJLabsResourceNetworkConstants.getUserBaseURL(),
            input,
            TJLabsResourceNetworkConstants.getUserLevelUnitsServerVersion()
        ) { status, msg, result ->

            if (status != 200) {
                TJLogger.d(msg)
                delegate?.onLevelUnitsDataError(key)
                completion(false)
                return@getLevelUnits
            }

            if (result != null) {
                levelUnitsDataMap[key] = result.units
                delegate?.onLevelUnitsData(key, result.units)
                completion(true)
            } else {
                delegate?.onLevelUnitsDataError(key)
                completion(false)
            }
        }
    }
}
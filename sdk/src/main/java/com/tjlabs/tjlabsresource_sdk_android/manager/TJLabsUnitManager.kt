package com.tjlabs.tjlabsresource_sdk_android.manager

import com.tjlabs.tjlabsresource_sdk_android.BuildingOutput
import com.tjlabs.tjlabsresource_sdk_android.LevelIdOsInput
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstants
import com.tjlabs.tjlabsresource_sdk_android.UnitData
import com.tjlabs.tjlabsresource_sdk_android.util.TJLogger
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

internal interface UnitDelegate {
    fun onUnitData(unitKey: String, data : List<UnitData>?)
    fun onUnitDataError(unitKey: String)
}


internal class TJLabsUnitManager {
    companion object {
        val unitDataMap : MutableMap<String, List<UnitData>> = mutableMapOf()
    }

    var delegate: UnitDelegate? = null

    fun loadUnit(
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
                val cached = unitDataMap[unitKey]
                if (cached != null) {
                    delegate?.onUnitData(unitKey, cached)
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
        val input = LevelIdOsInput(level_id = levelId)

        TJLabsResourceNetworkManager.getUnit(
            TJLabsResourceNetworkConstants.getUserBaseURL(),
            input,
            TJLabsResourceNetworkConstants.getUserUnitServerVersion()
        ) { status, msg, result ->

            if (status != 200) {
                TJLogger.d(msg)
                delegate?.onUnitDataError(key)
                completion(false)
                return@getUnit
            }

            if (result != null) {
                unitDataMap[key] = result.units
                delegate?.onUnitData(key, result.units)
                completion(true)
            } else {
                delegate?.onUnitDataError(key)
                completion(false)
            }
        }
    }
}
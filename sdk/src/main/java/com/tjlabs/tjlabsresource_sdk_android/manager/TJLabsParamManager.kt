package com.tjlabs.tjlabsresource_sdk_android.manager

import com.tjlabs.tjlabsresource_sdk_android.BuildingOutput
import com.tjlabs.tjlabsresource_sdk_android.LevelIdOsInput
import com.tjlabs.tjlabsresource_sdk_android.LevelParameterOutput
import com.tjlabs.tjlabsresource_sdk_android.ResourceRegion
import com.tjlabs.tjlabsresource_sdk_android.SectorIdOsInput
import com.tjlabs.tjlabsresource_sdk_android.SectorParameterOutput
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstants
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors


enum class ParamErrorType {
    Sector, Level
}

internal interface ParamDelegate {
    fun onSectorParamData(data: SectorParameterOutput)
    fun onLevelParamData(paramKey: String, data: LevelParameterOutput)
    fun onParamError(type: ParamErrorType, paramKey: String?)
}

internal class TJLabsParamManager {
    companion object {
        val sectorParamData: MutableMap<Int, SectorParameterOutput> = mutableMapOf()
        val levelParamData: MutableMap<String, LevelParameterOutput> = mutableMapOf()
    }

    var delegate: ParamDelegate? = null


    fun loadSectorParam(
        sectorId: Int,
        completion: (Boolean) -> Unit
    ) {
        val input = SectorIdOsInput(sector_id = sectorId)

        TJLabsResourceNetworkManager.getSectorParam(
            TJLabsResourceNetworkConstants.getUserBaseURL(),
            input,
            TJLabsResourceNetworkConstants.getUserSectorParamVersion()
        ) { status, _, result ->

            if (status != 200 || result == null) {
                delegate?.onParamError(ParamErrorType.Sector, null)
                completion(false)
                return@getSectorParam
            }

            sectorParamData[sectorId] = result
            delegate?.onSectorParamData(result)
            completion(true)
        }
    }

    fun loadLevelParam(
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

                val levelKey = "${sectorId}_${building.name}_${level.name}"

                // ✅ 캐시 히트
                val cached = levelParamData[levelKey]
                if (cached != null) {
                    delegate?.onLevelParamData(levelKey, cached)
                    continue
                }

                // ❌ 캐시 미스 → 비동기 작업
                hasAsyncWork = true

                tasks += { latch ->
                    updateLevelParam(levelKey, level.id) { isSuccess ->
                        updateSuccess(isSuccess)
                        latch.countDown()
                    }
                }
            }
        }

        // 전부 캐시 히트
        if (!hasAsyncWork) {
            completion(true)
            return
        }

        val latch = CountDownLatch(tasks.size)
        val executor = Executors.newFixedThreadPool(4)

        // 병렬 실행
        tasks.forEach { task ->
            executor.execute {
                task(latch)
            }
        }

        // 전체 완료 대기
        Executors.newSingleThreadExecutor().execute {
            latch.await()
            mainHandler.post {
                completion(isAllSuccess)
            }
        }
    }


    fun updateLevelParam(
        key: String,
        levelId: Int,
        completion: (Boolean) -> Unit
    ) {
        val input = LevelIdOsInput(level_id = levelId)

        TJLabsResourceNetworkManager.getLevelParam(
            TJLabsResourceNetworkConstants.getUserBaseURL(),
            input,
            TJLabsResourceNetworkConstants.getUserLevelParamVersion()
        ) { status, _, result ->

            if (status != 200 || result == null) {
                delegate?.onParamError(ParamErrorType.Level, key)
                completion(false)
                return@getLevelParam
            }

            levelParamData[key] = result
            delegate?.onLevelParamData(key, result)
            completion(true)
        }
    }
}
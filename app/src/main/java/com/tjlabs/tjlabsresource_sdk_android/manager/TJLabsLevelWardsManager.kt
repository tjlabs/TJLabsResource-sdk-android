package com.tjlabs.tjlabsresource_sdk_android.manager
import android.os.Handler
import android.os.Looper
import com.tjlabs.tjlabsresource_sdk_android.BuildingOutput
import com.tjlabs.tjlabsresource_sdk_android.LevelIdOsInput
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstants
import com.tjlabs.tjlabsresource_sdk_android.util.TJLogger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

internal interface LevelsDelegate {
    fun onLevelWardsData(levelKey: String, data: List<String>)
    fun onLevelWardsDataError(unitKey: String)
}

internal class TJLabsLevelsManager {
    companion object {
        val levelWardsDataMap: MutableMap<String, List<String>> = mutableMapOf()
    }

    var delegate: LevelsDelegate? = null

    fun loadLevelWards(
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

        // 각 level 에 대한 비동기 작업 정의
        val tasks = mutableListOf<(CountDownLatch) -> Unit>()

        for (building in buildingsData) {
            for (level in building.levels) {
                if (level.name.contains("_D")) continue

                val levelKey = "${sectorId}_${building.name}_${level.name}"

                // ✅ 캐시 히트: 즉시 delegate 호출, 비동기 대상 아님
                val cached = levelWardsDataMap[levelKey]
                if (cached != null) {
                    delegate?.onLevelWardsData(levelKey, cached)
                    continue
                }

                // ❌ 캐시 미스 → 비동기 작업 필요
                hasAsyncWork = true

                tasks += { latch ->
                    /*
                     * updateLevelWards 는 비동기 네트워크/파일 처리
                     * 완료 시 반드시 callback 에서 latch.countDown()
                     */
                    updateLevelWards(levelKey, level.id) { isSuccess ->
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

    private fun updateLevelWards(key: String, levelId: Int, completion: (Boolean) -> Unit) {
        val input = LevelIdOsInput(level_id = levelId)

        TJLabsResourceNetworkManager.getLevelWards(
            TJLabsResourceNetworkConstants.getUserBaseURL(),
            input,
            TJLabsResourceNetworkConstants.getUserLevelWardsVersion()
        ) { status, msg, result ->
            // 실패 처리
            if (status != 200) {
                TJLogger.d(msg)
                delegate?.onLevelWardsDataError(key)
                completion(false)
            }
            if (result != null) {
                levelWardsDataMap[key] = result.wards.map { it.name }
                delegate?.onLevelWardsData(key, result.wards.map { it.name })
                completion(true)

            } else {
                delegate?.onLevelWardsDataError(key)
                completion(false)

            }
        }
    }
}
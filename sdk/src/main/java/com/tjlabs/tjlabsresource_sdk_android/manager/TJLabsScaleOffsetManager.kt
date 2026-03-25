package com.tjlabs.tjlabsresource_sdk_android.manager

import android.os.Handler
import android.os.Looper
import com.tjlabs.tjlabsresource_sdk_android.BuildingOutput
import com.tjlabs.tjlabsresource_sdk_android.LevelIdOsInput
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstants
import com.tjlabs.tjlabsresource_sdk_android.util.TJLogger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

internal interface ScaleOffsetDelegate {
    fun onScaleOffsetData(scaleKey: String, data: List<Float>)
    fun onScaleOffsetError(scaleKey: String)
}


internal class TJLabsScaleOffsetManager {
    companion object {
        val scaleOffsetDataMap: MutableMap<String, List<Float>> = mutableMapOf()
    }

    var delegate: ScaleOffsetDelegate? = null

    fun loadScaleOffset(
        sectorId: Int,
        buildingsData: List<BuildingOutput>,
        completion: (Boolean) -> Unit
    ) {
        // 메인 스레드에서 delegate / completion 을 호출하기 위한 Handler
        val mainHandler = Handler(Looper.getMainLooper())

        // 여러 비동기 작업 중 하나라도 실패하면 false 로 유지하기 위한 동기화 객체
        val lock = Any()
        var isAllSuccess = true

        // 실제 네트워크 비동기 작업이 존재하는지 여부
        // (모두 캐시 히트면 비동기 없이 바로 completion 호출)
        var hasAsyncWork = false

        // 비동기 네트워크 결과에 따라 전체 성공 여부 갱신
        fun updateSuccess(success: Boolean) {
            if (!success) {
                synchronized(lock) {
                    isAllSuccess = false
                }
            }
        }

        /*
         * 각 level 에 대해 실행할 "비동기 작업 단위"
         *
         * - task 는 네트워크 요청을 시작만 함
         * - 실제 완료 시점은 updateLevelScaleOffset 의 callback
         * - 완료 시 latch.countDown() 으로 종료를 알림
         *
         * Swift DispatchGroup.enter / leave 와 1:1 대응
         */
        val tasks = mutableListOf<(CountDownLatch) -> Unit>()

        for (building in buildingsData) {
            for (level in building.levels) {
                if (level.name.contains("_D")) continue

                val scaleKey = "${sectorId}_${building.name}_${level.name}"

                // 캐시 히트:
                // - 네트워크 요청 없이 즉시 delegate 호출
                // - latch 대상이 아님
                val cached = scaleOffsetDataMap[scaleKey]
                if (cached != null) {
                    delegate?.onScaleOffsetData(scaleKey, cached)
                    continue
                }

                // 캐시 미스 → 실제 비동기 네트워크 작업 발생
                hasAsyncWork = true

                // 하나의 level 에 대한 비동기 작업 정의
                tasks += { latch ->
                    /*
                     * updateLevelScaleOffset 는 비동기 네트워크 호출
                     * → 여기서 바로 반환되며,
                     * → 네트워크 완료 시 callback 이 호출됨
                     */
                    updateLevelScaleOffset(scaleKey, level.id) { isSuccess ->
                        // 네트워크 결과를 전체 성공 여부에 반영
                        updateSuccess(isSuccess)

                        // 이 level 에 대한 비동기 작업 종료 알림
                        latch.countDown()
                    }
                }
            }
        }

        // 모든 level 이 캐시 히트였던 경우
        // → 비동기 작업 없이 즉시 성공 처리
        if (!hasAsyncWork) {
            completion(true)
            return
        }

        // 비동기 작업 개수만큼 latch 생성
        // → 모든 latch 가 countDown 될 때까지 대기
        val latch = CountDownLatch(tasks.size)

        // 동시에 최대 4개의 네트워크 작업만 실행하도록 제한
        val executor = Executors.newFixedThreadPool(4)

        // 모든 비동기 작업 실행
        tasks.forEach { task ->
            executor.execute {
                task(latch)
            }
        }

        /*
         * 모든 네트워크 비동기 작업 종료 대기 스레드
         *
         * - latch.await() 는 모든 countDown 이 호출될 때까지 블록
         * - 완료 후 메인 스레드에서 completion 호출
        */
        Executors.newSingleThreadExecutor().execute {
            latch.await()
            mainHandler.post {
                completion(isAllSuccess)
            }
        }
    }

    //원하는 key, level id 로 직접 요청하기
    fun updateLevelScaleOffset(key : String, levelId : Int, completion: (Boolean) -> Unit) {
        val input = LevelIdOsInput(level_id = levelId)

        TJLabsResourceNetworkManager.getScaleOffset(
            TJLabsResourceNetworkConstants.getUserBaseURL(),
            input,
            TJLabsResourceNetworkConstants.getUserScaleServerVersion()
        ) { status, msg, result ->

            // 실패 처리
            if (status != 200) {
                TJLogger.d(msg)
                delegate?.onScaleOffsetError(key)
                completion(false)
            }

            if (result != null) {
                scaleOffsetDataMap[key] = result.image_scale
                delegate?.onScaleOffsetData(key, result.image_scale)
                completion(true)
            } else {
                delegate?.onScaleOffsetError(key)
                completion(false)
            }
        }

    }
}
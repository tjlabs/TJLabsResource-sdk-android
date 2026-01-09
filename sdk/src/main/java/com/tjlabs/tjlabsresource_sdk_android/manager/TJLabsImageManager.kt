package com.tjlabs.tjlabsresource_sdk_android.manager

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.tjlabs.tjlabsresource_sdk_android.BuildingOutput
import com.tjlabs.tjlabsresource_sdk_android.util.TJLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

internal interface BuildingLevelImageDelegate {
    fun onBuildingLevelImageData(imageKey: String, data : Bitmap?)
    fun onBuildingLevelImageError(imageKey: String)

}
internal class TJLabsImageManager {
    companion object {
        val buildingLevelImageDataMap = mutableMapOf<String, Bitmap>()
    }

    var delegate: BuildingLevelImageDelegate? = null

    fun loadImage(
        sectorId: Int,
        buildingsData: List<BuildingOutput>,
        completion: (Boolean) -> Unit
    ) {
        TJLogger.d("(TJLabsResource) loadImage")

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

                val imageKey = "${sectorId}_${building.name}_${level.name}"

                // ✅ 캐시 히트
                val cached = buildingLevelImageDataMap[imageKey]
                if (cached != null) {
                    delegate?.onBuildingLevelImageData(imageKey, cached)
                    continue
                }

                // ❌ 캐시 미스 → 비동기 작업 필요
                hasAsyncWork = true

                tasks += { latch ->
                    updateLevelImage(imageKey, level.image) { isSuccess ->
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

        // 비동기 이미지 로딩 병렬 실행
        tasks.forEach { task ->
            executor.execute {
                task(latch)
            }
        }

        // 모든 이미지 로딩 종료 대기
        Executors.newSingleThreadExecutor().execute {
            latch.await()
            mainHandler.post {
                completion(isAllSuccess)
            }
        }
    }

    fun updateLevelImage(
        key: String,
        url: String,
        completion: (Boolean) -> Unit
    ) {
        TJLogger.d("(TJLabsResource) updateLevelImage")

        loadBuildingLevelImage(url) { bitmap, _ ->
            if (bitmap != null) {
                TJLogger.d("(TJLabsResource) loadBuildingLevelImage // bitmap : $bitmap")

                buildingLevelImageDataMap[key] = bitmap
                delegate?.onBuildingLevelImageData(key, bitmap)
                completion(true)
            } else {
                delegate?.onBuildingLevelImageError(key)
                completion(false)
            }
        }
    }

    private fun loadBuildingLevelImage(urlString : String, completion: (Bitmap?, Exception?) -> Unit) {
        try {
            val url = URL(urlString)

            // 캐시 확인
            val cachedImage = TJLabsImageCacheManager.getInstance().getBitmap(urlString)
            if (cachedImage != null) {
                completion(cachedImage, null)
                return
            }

            // 네트워크 요청
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connect()

                    if (connection.responseCode == 200) {
                        val inputStream = connection.inputStream
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        inputStream.close()

                        if (bitmap != null) {
                            TJLabsImageCacheManager.getInstance().putBitmap(urlString, bitmap)
                            withContext(Dispatchers.Main) {
                                completion(bitmap, null)
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                completion(null, Exception("Failed to decode bitmap"))
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            completion(null, Exception("HTTP Error: ${connection.responseCode}"))
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        completion(null, e)
                    }
                }
            }
        } catch (e: MalformedURLException) {
            completion(null, e)
        }
    }
}
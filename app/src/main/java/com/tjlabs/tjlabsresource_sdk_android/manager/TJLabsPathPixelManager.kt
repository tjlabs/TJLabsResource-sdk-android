package com.tjlabs.tjlabsresource_sdk_android.manager

import android.app.Application
import android.content.SharedPreferences
import com.tjlabs.tjlabsresource_sdk_android.BuildingOutput
import com.tjlabs.tjlabsresource_sdk_android.LevelIdOsInput
import com.tjlabs.tjlabsresource_sdk_android.PathPixelData
import com.tjlabs.tjlabsresource_sdk_android.ResourceRegion
import com.tjlabs.tjlabsresource_sdk_android.TJLabsFileDownloader.downloadCSVFile
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstants
import com.tjlabs.tjlabsresource_sdk_android.util.TJLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.net.URL
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

internal interface PathPixelDelegate {
    fun onPathPixelData(pathPixelKey: String, data: PathPixelData)
    fun onPathPixelError(pathPixelKey: String)
}

/**
 * PathPixel 정보를 가져오는 클래스
 *
 */
internal class TJLabsPathPixelManager {
    private lateinit var application: Application
    private lateinit var sharedPrefs : SharedPreferences

    companion object {
        val ppDataMap: MutableMap<String, PathPixelData> = mutableMapOf()
    }

    var delegate: PathPixelDelegate? = null
    private var region: String = ResourceRegion.KOREA.value

    fun setRegion(region: String) {
        this.region = region
    }

    fun init(application: Application, sharedPreferences: SharedPreferences) {
        this.application = application
        this.sharedPrefs = sharedPreferences
    }

    /**
     * 서버에서 path pixel 정보를 가져오기 위한 url 를 받아옴
     * 한번 받아온 url 정보는 캐시에 저장됨.
     * @see savePathPixelUrlToCache
     * @param sectorId
     * @param buildingsData
     * @param completion
     */

    private fun loadPathPixelUrl(sectorId : Int, buildingsData : List<BuildingOutput>, completion : (Map<String, String>) -> Unit){
        val pathPixelUrl = mutableMapOf<String, String>()
        val latch = java.util.concurrent.CountDownLatch(buildingsData.sumOf { it.levels.count { lvl -> !lvl.name.contains("_D") } })

        for (building in buildingsData) {
            for (level in building.levels) {
                if (level.name.contains("_D")) continue

                val input = LevelIdOsInput(level_id = level.id)
                val key = "${sectorId}_${building.name}_${level.name}"

                TJLabsResourceNetworkManager.getPathPixel(
                    TJLabsResourceNetworkConstants.getUserBaseURL(),
                    input,
                    TJLabsResourceNetworkConstants.getUserScaleServerVersion()
                ) { status, msg, result ->
                    try {
                        // 실패 처리
                        if (status != 200) {
                            delegate?.onPathPixelError(key)
                        }

                        if (result != null) {
                            savePathPixelUrlToCache(key, result.csv)
                            pathPixelUrl[key] = result.csv
                        } else {
                            delegate?.onPathPixelError(key)
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }
        }

        // 모든 요청 완료 후 실행
        Thread {
            latch.await()
            TJLogger.d("(TJLabsResource) Info : complete $pathPixelUrl")
            completion(pathPixelUrl)
        }.start()
    }

    /**
     *
     * @param region
     * @param sectorId
     * @param buildingsData
     */
    fun loadPathPixel(
        region: String,
        sectorId: Int,
        buildingsData: List<BuildingOutput>,
        completion: (Boolean) -> Unit
    ) {
        val mainHandler = Handler(Looper.getMainLooper())

        loadPathPixelUrl(sectorId, buildingsData) { pathPixelUrl ->

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

            for ((key, value) in pathPixelUrl) {
                val pathPixelUrlFromCache = loadPathPixelServerUrlFromCache(key)

                // 캐시 URL 동일
                if (pathPixelUrlFromCache != null && pathPixelUrlFromCache == value) {
                    val ppData = loadPathPixelFileFromCache(key)
                    if (ppData != null) {
                        ppDataMap[key] = ppData
                        delegate?.onPathPixelData(key, ppData)
                        continue
                    }
                }

                // 캐시 미스 or 파일 없음 → 비동기 다운로드
                hasAsyncWork = true
                tasks += { latch ->
                    updatePathPixel(key, sectorId, value) { isSuccess ->
                        updateSuccess(isSuccess)
                        latch.countDown()
                    }
                }
            }

            if (!hasAsyncWork) {
                completion(true)
                return@loadPathPixelUrl
            }

            val latch = CountDownLatch(tasks.size)
            val executor = Executors.newFixedThreadPool(4)

            tasks.forEach { task ->
                executor.execute {
                    task(latch)
                }
            }

            Executors.newSingleThreadExecutor().execute {
                latch.await()
                mainHandler.post {
                    completion(isAllSuccess)
                }
            }
        }
    }


    /**
     * 서버에서 받아온 url 을 이용하여 csv 를 다운로드 받음.
     * 다운로드함과 동시에 캐시에 해당 file 경로를 저장하고,
     * 경로를 이용하여 file 정보를 가져옴
     *
     * @param key
     * @param sectorId
     * @param pathPixelUrlFromServer
     */

    private fun updatePathPixel(
        key: String,
        sectorId: Int,
        pathPixelUrlFromServer: String,
        completion: (Boolean) -> Unit
    ) {
        val parsedUrl = try {
            URL(pathPixelUrlFromServer)
        } catch (e: Exception) {
            delegate?.onPathPixelError(key)
            completion(false)
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val (file, dir, _) = downloadCSVFile(
                    application,
                    parsedUrl,
                    sectorId,
                    "$key.csv"
                )

                if (file != null) {
                    val fileText = file.readText()
                    val ppData = parsePathPixelData(fileText)

                    ppDataMap[key] = ppData
                    savePathPixelCacheDirToCache(key, dir)
                    savePathPixelUrlToCache(key, pathPixelUrlFromServer)

                    withContext(Dispatchers.Main) {
                        delegate?.onPathPixelData(key, ppData)
                    }
                    completion(true)
                } else {
                    withContext(Dispatchers.Main) {
                        delegate?.onPathPixelError(key)
                    }
                    completion(false)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    delegate?.onPathPixelError(key)
                }
                completion(false)
            }
        }
    }
    fun updateLevelPathPixel(key: String, sectorId: Int, levelId: Int, completion: (Boolean) -> Unit) {
        val input = LevelIdOsInput(level_id = levelId)

        TJLabsResourceNetworkManager.getPathPixel(
            TJLabsResourceNetworkConstants.getUserBaseURL(),
            input,
            TJLabsResourceNetworkConstants.getUserScaleServerVersion()
        ) { status, msg, result ->

            // 실패 처리
            if (status != 200) {
                delegate?.onPathPixelError(key)
                completion(false)
            }

            if (result != null) {
                val ppUrl = result.csv
                val pathPixelUrlFromCache = loadPathPixelServerUrlFromCache(key)
                if (pathPixelUrlFromCache != null) {
                    if (pathPixelUrlFromCache == ppUrl) {
                        // 버전이 같다면
                        // 내가 가지고 있는 파일을 그대로 사용해도 됨.
                        val ppData = loadPathPixelFileFromCache(key)
                        if (ppData != null) {
                            ppDataMap[key] = ppData
                            delegate?.onPathPixelData(key, ppData)
                        } else {
                            // 파일이 없으면 서버에서 다운로드
                            updatePathPixel(key, sectorId, ppUrl) {
                                    isSuccess -> completion(isSuccess)
                            }
                        }
                    } else {
                        // 버전이 다르다면 서버에서 다운로드
                        updatePathPixel(key, sectorId, ppUrl) {
                            isSuccess -> completion(isSuccess)
                        }
                    }
                } else {
                    // Cache에서 파일 URL 가져오기 실패
                    updatePathPixel(key, sectorId, ppUrl) {
                            isSuccess -> completion(isSuccess)
                    }
                }
            } else {
                delegate?.onPathPixelError(key)
                completion(false)
            }
        }
    }


    private fun loadPathPixelServerUrlFromCache(key: String): String? {
        val keyPpURL = "TJLabsPathPixelURL_$key"
        return sharedPrefs.getString(keyPpURL, null)
    }

    private fun loadPathPixelFileFromCache(key : String) : PathPixelData?{
        val loadedPpLocalDir = loadPathPixelCacheDirFromCache(key)
        if (!loadedPpLocalDir.isNullOrEmpty()) {
            var fivalext = ""
            val file = File(loadedPpLocalDir)
            if (file.exists()) {
                fivalext = file.readText()
            }
            return parsePathPixelData(fivalext)
        }
        return null
    }

    private fun savePathPixelUrlToCache(key: String, pathPixelUrlFromServer: String) {
        val keyPpURL = "TJLabsPathPixelURL_$key"
        sharedPrefs.edit().putString(keyPpURL, pathPixelUrlFromServer).apply()
        TJLogger.d("Info: save $key Path-Pixel URL $pathPixelUrlFromServer")
    }

    private fun loadPathPixelCacheDirFromCache(key: String): String? {
        val keyPpURL = "TJLabsPathPixelDir_$key"
        return sharedPrefs.getString(keyPpURL, null)
    }

    private fun savePathPixelCacheDirToCache(key: String, pathPixelUrlFromServer: String) {
        val keyPpURL = "TJLabsPathPixelDir_$key"
        sharedPrefs.edit().putString(keyPpURL, pathPixelUrlFromServer).apply()
        TJLogger.d("Info: save $key Path-Pixel URL $pathPixelUrlFromServer")
    }

    private fun parsePathPixelData(data: String): PathPixelData {
        val roadType = mutableListOf<Int>()
        val nodeNumber = mutableListOf<Int>()
        val roadX = mutableListOf<Float>()
        val roadY = mutableListOf<Float>()
        val roadScale = mutableListOf<Float>()
        val roadHeading = mutableListOf<String>()

        val lines = data.lines()
        val bracketRegex = Regex("\\[[^\\]]+\\]") // [ ... ] 구간 추출

        for (line in lines) {
            if (line.isEmpty()) continue
            if (line.contains("encoding=")) continue

            val parts = line.split(",")
            if (parts.size < 5) {
                // 잘못된 라인 형태
                TJLogger.d("Invalid line format: $line")
                continue
            }

            val typeString = parts[0].trim()
            val nodeString = parts[1].trim()
            val xString = parts[2].trim()
            val yString = parts[3].trim()
            val scaleString = parts[4].trim()

            if (xString.isNotEmpty() && yString.isNotEmpty()) {
                val typeVal = typeString.toDoubleOrNull()?.toInt()
                val nodeVal = nodeString.toDoubleOrNull()?.toInt()
                val xVal = xString.toFloatOrNull()
                val yVal = yString.toFloatOrNull()
                val scaleVal = scaleString.toFloatOrNull()

                if (typeVal == null || nodeVal == null || xVal == null || yVal == null || scaleVal == null) {
                    TJLogger.d("Parse error: $line")
                    continue
                }

                roadType.add(typeVal)
                nodeNumber.add(nodeVal)
                roadX.add(xVal)
                roadY.add(yVal)
                roadScale.add(scaleVal)

                // [ ... ] 안의 heading 리스트 추출 → "a,b,c" 문자열로 저장
                val match = bracketRegex.find(line)
                val headingValues = if (match != null) {
                    val raw = match.value
                        .replace("[", "")
                        .replace("]", "")
                        .split(",")
                        .mapNotNull { it.trim().toDoubleOrNull() }  // Swift의 Double → Kotlin에선 Float로 내려감
                        .joinToString(",") { it.toString() }
                    raw
                } else {
                    ""
                }
                roadHeading.add(headingValues)
            }
        }

        val road = listOf(roadX, roadY)
        val minX = roadX.minOrNull() ?: 0f
        val minY = roadY.minOrNull() ?: 0f
        val maxX = roadX.maxOrNull() ?: 0f
        val maxY = roadY.maxOrNull() ?: 0f
        val roadMinMax = listOf(minX, minY, maxX, maxY)

        return PathPixelData(
            roadType = roadType,
            nodeNumber = nodeNumber,
            road = road,
            roadMinMax = roadMinMax,
            roadScale = roadScale,
            roadHeading = roadHeading
        )
    }
}
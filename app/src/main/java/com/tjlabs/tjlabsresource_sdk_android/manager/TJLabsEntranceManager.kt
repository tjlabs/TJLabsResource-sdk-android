package com.tjlabs.tjlabsresource_sdk_android.manager

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import com.tjlabs.tjlabsresource_sdk_android.BuildingOutput
import com.tjlabs.tjlabsresource_sdk_android.EntranceData
import com.tjlabs.tjlabsresource_sdk_android.EntranceRouteData
import com.tjlabs.tjlabsresource_sdk_android.LevelIdOsInput
import com.tjlabs.tjlabsresource_sdk_android.ResourceRegion
import com.tjlabs.tjlabsresource_sdk_android.TJLabsFileDownloader.downloadCSVFile
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstants
import com.tjlabs.tjlabsresource_sdk_android.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.net.URL

enum class EntranceErrorType {
    Common,
    Route
}

internal interface EntranceDelegate {
    fun onEntranceData(entranceKey: String, data: EntranceData)
    fun onEntranceRouteData(entranceKey: String, data: EntranceRouteData)
    fun onEntranceError(type: EntranceErrorType, entranceKey: String)
}
internal class TJLabsEntranceManager {
    private lateinit var application: Application
    private lateinit var sharedPrefs : SharedPreferences

    companion object {
        val entranceDataMap: MutableMap<String, EntranceData> = mutableMapOf()
        val entranceRouteDataMap: MutableMap<String, EntranceRouteData> = mutableMapOf()
    }

    var delegate: EntranceDelegate? = null
    private var region = ResourceRegion.KOREA.value

    fun setRegion(region: String) {
        this.region = region
    }

    fun init(application: Application, sharedPreferences: SharedPreferences) {
        this.application = application
        this.sharedPrefs = sharedPreferences
    }

    private fun loadEntranceUrl(sectorId : Int, buildingsData : List<BuildingOutput>, completion: (Map<String, String>) -> Unit) {
        val entranceUrl = mutableMapOf<String, String>()
        val latch = java.util.concurrent.CountDownLatch(buildingsData.sumOf { it.levels.count { lvl -> !lvl.name.contains("_D") } })

        for (building in buildingsData) {
            for (level in building.levels) {
                if (level.name.contains("_D")) continue

                val input = LevelIdOsInput(level_id = level.id)
                val key = "${sectorId}_${building.name}_${level.name}"

                Logger.d("loadEntranceUrl // key : $key")
                TJLabsResourceNetworkManager.getEntrance(
                    TJLabsResourceNetworkConstants.getUserBaseURL(),
                    input,
                    TJLabsResourceNetworkConstants.getUserEntranceServerVersion()
                ) { status, msg, result ->
                    try {
                        // 실패 처리
                        Logger.d("loadEntranceUrl // status : $status // result : $result")

                        if (status != 200) {
                            delegate?.onEntranceError(EntranceErrorType.Common, key)
                        }

                        if (result != null) {
                            for (ent in result.entrances) {
                                val entUrl = ent.csv
                                val entKey = key + "_${ent.number}"
                                entranceUrl[entKey] = entUrl
                                saveEntranceRouteUrlToCache(entKey, entUrl)
                                val innerWardCoord = listOf(ent.innermost_ward.x.toFloat(), ent.innermost_ward.y.toFloat())
                                val entInfo = EntranceData(ent.number, ent.network_status, ent.scale, ent.innermost_ward.name, ent.innermost_ward.rssi, innerWardCoord, ent.outermost_ward_name)
                                entranceDataMap[key] = entInfo
                                delegate?.onEntranceData(entKey, entInfo)
                            }
                        } else {
                            delegate?.onEntranceError(EntranceErrorType.Common, key)
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
            Logger.d("(TJLabsResource) Info : complete $entranceUrl")
            completion(entranceUrl)
        }.start()
    }

    fun loadEntrance(region: String,
                     sectorId: Int,
                     buildingsData: List<BuildingOutput>) {

        loadEntranceUrl(sectorId, buildingsData) {
            entranceRouteUrl ->
            Logger.d("(TJLabsResource) loadPathPixel $entranceRouteUrl")

            for ((key, value) in entranceRouteUrl) {
                //서버에서 가져온 결과를 캐시에 저장된 값과 비교
                val pathPixelUrlFromCache = loadEntranceRouteUrlFromCache(key)
                if (pathPixelUrlFromCache != null) {
                    if (pathPixelUrlFromCache == value) {
                        // 버전이 같다면
                        // 내가 가지고 있는 파일을 그대로 사용해도 됨.
                        val entranceRouteData = loadEntranceRouteFileUrlFromCache(key)
                        if (entranceRouteData != null) {
                            entranceRouteDataMap[key] = entranceRouteData
                            delegate?.onEntranceRouteData(key, entranceRouteData)
                        } else {
                            // 파일이 없으면 서버에서 다운로드
                            updateEntranceRoute(key, sectorId, value)
                        }
                    } else {
                        // 버전이 다르다면 서버에서 다운로드
                        updateEntranceRoute(key, sectorId, value)
                    }
                } else {
                    // Cache에서 파일 URL 가져오기 실패
                    updateEntranceRoute(key, sectorId, value)
                }
            }
        }
    }

    private fun updateEntranceRoute(key: String, sectorId: Int, entranceRouteUrlFromServer: String) {
        val parsedUrl = try {
            URL(entranceRouteUrlFromServer)
        } catch (e: Exception) {
            delegate?.onEntranceError(EntranceErrorType.Route, key)
            return
        }

        GlobalScope.launch(Dispatchers.Main) {
            try {
                val (file, dir, exception) = downloadCSVFile(
                    application,
                    parsedUrl,
                    sectorId,
                    "$key.csv"
                )
                if (file != null) {
                    val fileText = file.readText()
                    val parseData = parseRoute(fileText)
                    entranceRouteDataMap[key] = parseData
                    saveEntranceRouteDirToCache(key, dir)
                    delegate?.onEntranceRouteData(key, parseData)

                } else {
                    delegate?.onEntranceError(EntranceErrorType.Route, key)
                }
            } catch (e: Exception) {
                delegate?.onEntranceError(EntranceErrorType.Route, key)
            }
        }
    }

    private fun loadEntranceRouteUrlFromCache(key: String): String? {
        val keyPpURL = "TJLabsEntranceRouteURL_$key"
        return sharedPrefs.getString(keyPpURL, null)
    }

    private fun saveEntranceRouteUrlToCache(key: String, entranceUrlFromServer: String) {
        val keyPpURL = "TJLabsEntranceRouteURL_$key"
        sharedPrefs.edit().putString(keyPpURL, entranceUrlFromServer).apply()
        Log.d("TJLabsResource", "Info: save $key Entrnace URL $entranceUrlFromServer")
    }

    private fun loadEntranceRouteFileUrlFromCache(key : String) : EntranceRouteData? {
        val loadedPpLocalUrl = loadEntranceRouteDirFromCache(key)
        if (!loadedPpLocalUrl.isNullOrEmpty()) {
            var fivalext = ""
            val file = File(loadedPpLocalUrl)
            if (file.exists()) {
                fivalext = file.readText()
            }
            return parseRoute(fivalext)
        }
        return null
    }

    private fun loadEntranceRouteDirFromCache(key: String): String? {
        val keyPpURL = "TJLabsEntranceRouteDir_$key"
        return sharedPrefs.getString(keyPpURL, null)
    }

    private fun saveEntranceRouteDirToCache(key: String, pathPixelUrlFromServer: String) {
        val keyPpURL = "TJLabsEntranceRouteDir_$key"
        sharedPrefs.edit().putString(keyPpURL, pathPixelUrlFromServer).apply()
    }


    private fun parseRoute(data: String): EntranceRouteData {
        val entranceLevelArray = mutableListOf<String>()
        val entranceArray = mutableListOf<List<Float>>()

        val entranceString = data.split("\n")
        for (line in entranceString) {
            if (line.isNotBlank()) {
                val lineData = line.split(",")
                val entrance = listOf(lineData[1].toFloat(), lineData[2].toFloat(), lineData[3].toFloat())

                entranceLevelArray.add(lineData[0])
                entranceArray.add(entrance)
            }
        }

        return EntranceRouteData(entranceLevelArray, entranceArray)
    }

}
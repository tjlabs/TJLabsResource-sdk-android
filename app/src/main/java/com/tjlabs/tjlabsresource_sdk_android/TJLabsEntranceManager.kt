package com.tjlabs.tjlabsresource_sdk_android

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import com.tjlabs.tjlabsresource_sdk_android.TJLabsFileDownloader.downloadCSVFile
import com.tjlabs.tjlabsresource_sdk_android.TJLabsPathPixelManager.Companion.ppDataLoaded
import com.tjlabs.tjlabsresource_sdk_android.TJLabsPathPixelManager.Companion.ppDataMap
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstant.getEntranceServerVersion
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstant.getUserBaseURL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.net.URL

interface EntranceDelegate {
    fun onEntranceData(manager: TJLabsEntranceManager, isOn: Boolean, entranceKey: String)
    fun onEntranceError(manager: TJLabsEntranceManager)
}

class TJLabsEntranceManager {
    private lateinit var application: Application
    private lateinit var sharedPrefs : SharedPreferences



    companion object {
        var entranceNumbers: Int = 0
        val entranceDataMap: MutableMap<String, EntranceData> = mutableMapOf()
        val entranceRouteDataMap: MutableMap<String, EntranceRouteData> = mutableMapOf()
        val entranceRouteDataLoaded: MutableMap<String, EntranceRouteDataIsLoaded> = mutableMapOf()
        val entranceOuterWards: MutableList<String> = mutableListOf()
    }

    var delegate: EntranceDelegate? = null
    var region = ResourceRegion.KOREA

    fun init(application: Application, sharedPreferences: SharedPreferences) {
        this.application = application
        this.sharedPrefs = sharedPreferences
    }

    fun loadEntrance(sectorId : Int) {
        getEntranceInfo(region, sectorId)
        { isSuccess, msg, sectorPathPixelInfo ->
            Log.d(TAG, msg)

            if (isSuccess) {
                for ((key, url) in sectorPathPixelInfo) {
                    val pathPixelUrlInPrefs = loadEntranceRouteUrlFromCache(key)
                    if (pathPixelUrlInPrefs != url) {
                        updateEntranceRoute(region, sectorId, key, url) { isSuccessSave, _ ->
                            if (isSuccessSave) {
                                saveEntranceRouteUrlToCache(key, url)
                            }
                            entranceRouteDataMap[key] = loadEntranceRouteFileUrlFromCache(key)
                            entranceRouteDataLoaded[key] = EntranceRouteDataIsLoaded(isSuccessSave, url)
                        }
                    } else {
                        Log.d(TAG, "already exist entrance data // data key : $key")

                        entranceRouteDataMap[key] = loadEntranceRouteFileUrlFromCache(key)
                        entranceRouteDataLoaded[key] = EntranceRouteDataIsLoaded(true, url)
                        delegate?.onEntranceData(this, true, key)
                    }
                }
            } else {
                delegate?.onEntranceError(this)
            }
        }
    }

    private fun getEntranceInfo(region: String, sectorId: Int, completion: (Boolean, String, Map<String, String>) -> Unit) {
        val entranceRouteUrl = mutableMapOf<String, String>()
        val input = SectorInput(sectorId, operating_system = "Android")
        TJLabsResourceNetworkManager.postEntrance(
            getUserBaseURL(),
            input,
            getEntranceServerVersion()
        ) { statusCode, outputEntrance ->
            if (statusCode == 200) {
                // 섹터 내 모든 pp 가져옴
                if (outputEntrance.entrance_list.isNotEmpty()) {
                    val entranceInfo = outputEntrance.entrance_list
                    for (element in entranceInfo) {
                        val buildingName = element.building_name
                        val levelName = element.level_name
                        val key = "${region}_${input.sector_id}_${buildingName}_${levelName}"

                        val entrances = element.entrances
                        entranceNumbers += entrances.size
                        for (ent in entrances) {
                            val entranceKey = "${key}_${ent.spot_number}"
                            val entranceData = EntranceData(ent.spot_number, ent.network_status, ent.scale, ent.innermost_ward.id, ent.innermost_ward.rss, ent.innermost_ward.pos + listOf(ent.innermost_ward.direction))
                            entranceDataMap[key] = entranceData
                            entranceRouteUrl[entranceKey] = ent.url
                            entranceOuterWards.add(ent.outermost_ward_id)
                            Log.d(TAG,"(Olympus) entrance[$entranceKey] : $ent")
                        }

                    }
                    val msg = "(Olympus) Success : Load Sector Info // Path"
                    completion(true, msg, entranceRouteUrl)
                } else {
                    val msg = "(Olympus) Error Path Pixel List is empty // Level $statusCode"
                    completion(false, msg, entranceRouteUrl)
                }
            } else {
                val msg = "(Olympus) Error Load Sector Info // Level $statusCode"
                completion(false, msg, entranceRouteUrl)
            }
        }
    }

    private fun updateEntranceRoute(region : String, sectorId: Int, key: String, entranceUrl : String,
                                    completion: (Boolean, String) -> Unit
    ) {
        GlobalScope.launch(Dispatchers.Main) {
            try {
                val (file, dir, exception) = downloadCSVFile(application, URL(entranceUrl),sectorId, "$key.csv")
                if (file != null) {
                    entranceRouteDataMap[key] = loadEntranceRouteFileUrlFromCache(key)
                    entranceRouteDataLoaded[key] = EntranceRouteDataIsLoaded(true, entranceUrl)
                    saveEntranceRouteUrlToCache(key, dir)
                    completion(true, "")
                    Log.d(TAG, "success update entrance // key :$key")

                } else {
                    if (exception != null) {
                        completion(false, exception.message.toString())
                    }
                    ppDataLoaded[key] = PathPixelDataIsLoaded(false, entranceUrl)

                }
            } catch (e: Exception) {
                completion(false, "")
                ppDataLoaded[key] = PathPixelDataIsLoaded(false, entranceUrl)

            }
        }
    }

    private fun loadEntranceRouteUrlFromCache(key: String): String? {
        val keyPpURL = "TJLabsEntranceRouteURL_$key"
        return sharedPrefs.getString(keyPpURL, null)
    }

    private fun saveEntranceRouteUrlToCache(key: String, pathPixelUrlFromServer: String) {
        val keyPpURL = "TJLabsEntranceRouteURL_$key"
        sharedPrefs.edit().putString(keyPpURL, pathPixelUrlFromServer).apply()
        Log.d("TJLabsResource", "Info: save $key Entrnace URL $pathPixelUrlFromServer")
    }

    private fun loadEntranceRouteFileUrlFromCache(key : String) : EntranceRouteData{
        val loadedPpLocalUrl = loadEntranceRouteUrlFromCache(key)
        if (!loadedPpLocalUrl.isNullOrEmpty()) {
            var fivalext = ""
            val file = File(loadedPpLocalUrl)
            if (file.exists()) {
                fivalext = file.readText()
            }
            return parseRoute(fivalext)
        }
        return EntranceRouteData()
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
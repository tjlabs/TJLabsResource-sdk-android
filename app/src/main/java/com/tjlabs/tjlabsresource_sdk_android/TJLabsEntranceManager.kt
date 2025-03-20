package com.tjlabs.tjlabsresource_sdk_android

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import com.tjlabs.tjlabsresource_sdk_android.TJLabsFileDownloader.downloadCSVFile
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstant.getEntranceServerVersion
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstant.getUserBaseURL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.net.URL

internal interface EntranceDelegate {
    fun onEntranceRouteData(isOn: Boolean, entranceKey: String, data : EntranceRouteData?)
    fun onEntranceData(isOn: Boolean, entranceKey : String, data : EntranceData?)
    fun onEntranceError()
}

internal class TJLabsEntranceManager {
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
        { isSuccess, msg, sectorPathPixelInfo, entranceDataMap ->
            Log.d(TAG, msg)

            if (isSuccess) {
                for ((key, url) in sectorPathPixelInfo) {
                    val entranceUrlInPrefs = loadEntranceRouteUrlFromCache(key)
                    if (entranceUrlInPrefs != url) {
                        updateEntranceRoute(sectorId, key, url) { isSuccessSave, _ ->
                            if (isSuccessSave) {
                                saveEntranceRouteUrlToCache(key, url)
                            }
                            val entranceRouteData = loadEntranceRouteFileUrlFromCache(key)
                            entranceRouteDataMap[key] = loadEntranceRouteFileUrlFromCache(key)
                            entranceRouteDataLoaded[key] = EntranceRouteDataIsLoaded(isSuccessSave, url)
                            delegate?.onEntranceRouteData( true, key, entranceRouteData)
                        }
                    } else {
                        Log.d(TAG, "already exist entrance data // data key : $key")
                        val entranceRouteData = loadEntranceRouteFileUrlFromCache(key)
                        entranceRouteDataMap[key] = entranceRouteData
                        entranceRouteDataLoaded[key] = EntranceRouteDataIsLoaded(true, url)
                        delegate?.onEntranceRouteData( true, key, entranceRouteData)
                    }
                }

                for ((key, entranceData) in entranceDataMap) {
                    Log.d(TAG, "entrance data map : $entranceData")
                    delegate?.onEntranceData( true, key, entranceData)
                }
            } else {
                delegate?.onEntranceError()
            }
        }
    }

    private fun getEntranceInfo(region: String, sectorId: Int, completion: (Boolean, String, Map<String, String>, Map<String, EntranceData>) -> Unit) {
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
                            entranceDataMap[entranceKey] = entranceData
                            entranceRouteUrl[entranceKey] = ent.url
                            entranceOuterWards.add(ent.outermost_ward_id)
                            Log.d(TAG,"(Olympus) entrance[$entranceKey] : $ent")
                        }

                    }
                    val msg = "(Olympus) Success : Load Sector Info // Path"
                    completion(true, msg, entranceRouteUrl, entranceDataMap)
                } else {
                    val msg = "(Olympus) Error Path Pixel List is empty // Level $statusCode"
                    completion(false, msg, entranceRouteUrl, entranceDataMap)
                }
            } else {
                val msg = "(Olympus) Error Load Sector Info // Level $statusCode"
                completion(false, msg, entranceRouteUrl, entranceDataMap)
            }
        }
    }

    private fun updateEntranceRoute(sectorId: Int, key: String, entranceUrl : String,
                                    completion: (Boolean, String) -> Unit
    ) {
        GlobalScope.launch(Dispatchers.Main) {
            try {
                val (file, dir, exception) = downloadCSVFile(application, URL(entranceUrl),sectorId, "$key.csv")
                if (file != null) {
                    entranceRouteDataMap[key] = loadEntranceRouteFileUrlFromCache(key)
                    entranceRouteDataLoaded[key] = EntranceRouteDataIsLoaded(true, entranceUrl)
                    saveEntranceRouteDirToCache(key, dir)
                    completion(true, "")
                    Log.d(TAG, "success update entrance // key :$key")

                } else {
                    if (exception != null) {
                        completion(false, exception.message.toString())
                    }
                    entranceRouteDataLoaded[key] = EntranceRouteDataIsLoaded(false, entranceUrl)
                    delegate?.onEntranceRouteData(false, key, null)
                }
            } catch (e: Exception) {
                completion(false, "")
                entranceRouteDataLoaded[key] = EntranceRouteDataIsLoaded(false, entranceUrl)
                delegate?.onEntranceRouteData(false, key, null)
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
        val loadedPpLocalUrl = loadEntranceRouteDirFromCache(key)
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

    private fun loadEntranceRouteDirFromCache(key: String): String? {
        val keyPpURL = "TJLabsEntranceRouteDir_$key"
        return sharedPrefs.getString(keyPpURL, null)
    }

    private fun saveEntranceRouteDirToCache(key: String, pathPixelUrlFromServer: String) {
        val keyPpURL = "TJLabsEntranceRouteDir_$key"
        sharedPrefs.edit().putString(keyPpURL, pathPixelUrlFromServer).apply()
        Log.d("TJLabsResource", "Info: save $key Entrnace URL $pathPixelUrlFromServer")
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
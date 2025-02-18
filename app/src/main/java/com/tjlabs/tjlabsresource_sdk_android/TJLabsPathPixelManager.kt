package com.tjlabs.tjlabsresource_sdk_android

import android.app.Application
import android.content.SharedPreferences
import com.tjlabs.tjlabsresource_sdk_android.TJLabsFileDownloader.downloadCSVFile
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceManager.getResourceDirInPrefs
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceManager.getResourceVersionFromPrefs
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceManager.ppDataLoaded
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceManager.ppDataMap
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceManager.saveResourceDirInPrefs
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceManager.saveResourceVersionInPrefs
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstant.getPathPixelServerVersion
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstant.getUserBaseURL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.net.URL

internal class TJLabsPathPixelManager(private val application: Application, private val sharedPrefs : SharedPreferences) {
    fun loadPathPixel(region: String, sectorId: Int) {
        //1. path pixel의 버젼을 확인
        //2. 저장되어 있는 버젼과 일치하면 업데이트 x
        //3. 저장되어 있는 버젼과 일치하지 않으면 서버로 부터 csv 파일을 읽어 저장
        getSectorPathPixelInfo(region, sectorId)
        { isSuccess, msg, sectorPathPixelInfo ->
            if (isSuccess) {
                for ((key, url) in sectorPathPixelInfo) {
                    val pathPixelUrlInPrefs = getResourceVersionFromPrefs(sharedPrefs, key, PATH_PIXEL_KEY_NAME)
                    if (pathPixelUrlInPrefs != url) {
                        saveSectorPathPixelFromUrl(application, region, sectorId, key, url) { isSuccessSave, msgSave ->
                            if (isSuccessSave) {
                                saveResourceVersionInPrefs(sharedPrefs, key, PATH_PIXEL_KEY_NAME, url)
                            }
                            ppDataMap[key] = loadSectorPathPixelFromCache(key)
                            ppDataLoaded[key] = PathPixelDataIsLoaded(isSuccessSave, url)
                        }
                    } else {
                        ppDataMap[key] = loadSectorPathPixelFromCache(key)
                    }
                }
            }
        }
    }

    private fun getSectorPathPixelInfo(region: String, sectorId: Int, completion: (Boolean, String, Map<String, String>) -> Unit) {
        val sectorPathPixelInfo = mutableMapOf<String, String>()
        val input = SectorInput(sectorId, operating_system = "Android")
        TJLabsResourceNetworkManager.postPathPixel(
            getUserBaseURL(),
            input,
            getPathPixelServerVersion()
        ) { statusCode, outputPath ->
            if (statusCode == 200) {
                // 섹터 내 모든 pp 가져옴
                if (outputPath.path_pixel_list.isNotEmpty()) {
                    val pathInfo = outputPath.path_pixel_list
                    for (element in pathInfo) {
                        val buildingName = element.building_name
                        val levelName = element.level_name
                        val key = "${region}_${input.sector_id}_${buildingName}_${levelName}"
                        val ppUrl = element.url
                        sectorPathPixelInfo[key] = ppUrl
                    }
                    val msg = "(Olympus) Success : Load Sector Info // Path"
                    completion(true, msg, sectorPathPixelInfo)
                } else {
                    val msg = "(Olympus) Error Path Pixel List is empty // Level $statusCode"
                    completion(false, msg, sectorPathPixelInfo)
                }
            } else {
                val msg = "(Olympus) Error Load Sector Info // Level $statusCode"
                completion(false, msg, sectorPathPixelInfo)
            }
        }
    }

    private fun saveSectorPathPixelFromUrl(application: Application, region : String, sectorId: Int, key: String, ppUrl : String,
        completion: (Boolean, String) -> Unit
    ) {
        GlobalScope.launch(Dispatchers.Main) {
            try {
                val (file, dir, exception) = downloadCSVFile(application, URL(ppUrl), region,sectorId, "$key.csv")
                if (file != null) {
                    saveResourceDirInPrefs(sharedPrefs, key, dir, PATH_PIXEL_KEY_NAME)
                    completion(true, "")
                } else {
                    if (exception != null) {
                        completion(false, exception.message.toString())
                    }

                }
            } catch (e: Exception) {
                completion(false, "")
            }
        }
    }

    private fun loadSectorPathPixelFromCache(key : String) : PathPixelData{
        val loadedPpLocalUrl = getResourceDirInPrefs(sharedPrefs, key, PATH_PIXEL_KEY_NAME)
        if (!loadedPpLocalUrl.isNullOrEmpty()) {
            var fivalext = ""
            val file = File(loadedPpLocalUrl)
            if (file.exists()) {
                fivalext = file.readText()
            }
            return parsePathPixelData(fivalext)
        }
        return PathPixelData()
    }

    private fun parsePathPixelData(data: String): PathPixelData {
        val roadType = mutableListOf<Int>()
        val nodeNumber = mutableListOf<Int>()
        val roadScale = mutableListOf<Float>()
        val roadHeading = mutableListOf<String>()

        val roadX = mutableListOf<Float>()
        val roadY = mutableListOf<Float>()

        val roadString = data.split("\n")

        for (line in roadString) {
            if (line.isNotEmpty()) {
                val parts = line.split("[")
                val parts1 = parts[0].split(",")
                val parts2 = parts[1]
                val type = parts1[0]
                val nodeNum = parts1[1]
                val x = parts1[2].toFloat()
                val y = parts1[3].toFloat()
                val scale = parts1[4].toFloat()
                val headingData = parts2.replace("\"", "").replace("[", "").replace("]", "").split(",")

                roadType.add(type.toInt())
                nodeNumber.add(nodeNum.toInt())

                roadX.add(x)
                roadY.add(y)

                roadScale.add(scale)
                val headingArray = mutableListOf<String>()

                for (j in headingData) {
                    if (j.isNotEmpty()) {
                        headingArray.add(j)
                    }
                }
                roadHeading.add(headingArray.joinToString(","))
            }
        }
        val road = listOf(roadX, roadY)
        return PathPixelData(roadType, nodeNumber, road, roadScale, roadHeading)
    }

}
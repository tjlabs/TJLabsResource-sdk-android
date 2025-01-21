package com.tjlabs.tjlabsresource_sdk_android

import android.app.Application
import android.content.SharedPreferences
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceManager.Companion.PATH_PIXEL_KEY_NAME
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceManager.Companion.getResourceDirInPrefs
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceManager.Companion.getResourceVersionFromPrefs
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceManager.Companion.saveResourceDirInPrefs
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceManager.Companion.saveResourceVersionInPrefs
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstant.getPathPixelServerVersion
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstant.getUserBaseURL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

class TJLabsPathPixelManager(private val application: Application, private val sharedPrefs : SharedPreferences) {
    private val ppDataMap : MutableMap<String, PathPixelData> = mutableMapOf()

    fun returnPathPixelData() : MutableMap<String, PathPixelData> {
        return ppDataMap
    }

    fun updatePathPixel(region: String, sectorId: Int, completion: (Boolean, String) -> Unit) {
        //1. path pixel의 버젼을 확인
        //2. 저장되어 있는 버젼과 일치하면 업데이트 x
        //3. 저장되어 있는 버젼과 일치하지 않으면 서버로 부터 csv 파일을 읽어 저장
        getSectorPathPixelInfo(region, sectorId)
        { isSuccess, msg, sectorPathPixelInfo ->
            if (isSuccess) {
                var successCount = 0
                var failKeys = ""
                for ((key, url) in sectorPathPixelInfo) {
                    val pathPixelUrlInPrefs = getResourceVersionFromPrefs(sharedPrefs, key)
                    if (pathPixelUrlInPrefs != url) {
                        saveSectorPathPixelFromUrl(region, sectorId, key, url) { isSuccessSave, msgSave ->
                            if (isSuccessSave) {
                                saveResourceVersionInPrefs(sharedPrefs, key, url)
                                ppDataMap[key] = loaSectorPathPixelFromCache(key)
                                successCount++

                                if (successCount == sectorPathPixelInfo.size) {
                                    completion(true, "")
                                } else {
                                    completion(false, failKeys)
                                }

                            } else {
                                failKeys += "$key/"
                                completion(false, msgSave)
                            }
                        }
                    } else {
                        ppDataMap[key] = loaSectorPathPixelFromCache(key)
                        successCount++
                        if (successCount == sectorPathPixelInfo.size) {
                            completion(true, "")
                        } else {
                            completion(false, failKeys)
                        }
                    }
                }
            } else {
                completion(false, msg)
            }
        }
    }

    private suspend fun downloadCSVFile(url: URL, region : String, sectorId: Int, fileName: String): Triple<File?, String, Exception?> =
        withContext(
            Dispatchers.IO
        ) {
            lateinit var outputFile: File
            lateinit var output: OutputStream
            val exception: Exception?

            try {
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                val input: InputStream = connection.inputStream

                val subFolder = File(application.cacheDir, "${region}_$sectorId")
                if (!subFolder.exists()) {
                    subFolder.mkdirs() // 하위 폴더 생성
                }

                outputFile = File(subFolder, fileName) // 캐시에 파일 생성
                output = FileOutputStream(outputFile)

                input.use {
                    output.use { output ->
                        input.copyTo(output)
                    }
                }
                Triple(outputFile, "${application.cacheDir}/${region}_$sectorId/$fileName", null)
            } catch (e: IOException) {
                exception = e
                Triple(null, "", exception)
            } finally {
                try {
                    output.close()
                } catch (e: IOException) {
                    // ignore
                }
            }
        }

    private fun getSectorPathPixelInfo(region: String, sectorId: Int, completion: (Boolean, String, Map<String, String>) -> Unit) {
        val sectorPathPixelInfo = mutableMapOf<String, String>()
        val input = InputSector(sectorId, operating_system = "Android")
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
                        val key = "${region}_${input.sector_id}_${buildingName}_${levelName}_${PATH_PIXEL_KEY_NAME}"
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

    private fun saveSectorPathPixelFromUrl(region : String, sectorId: Int, key: String, ppUrl : String,
        completion: (Boolean, String) -> Unit
    ) {
        GlobalScope.launch(Dispatchers.Main) {
            try {
                val (file, dir, exception) = downloadCSVFile(URL(ppUrl), region,sectorId, "$key.csv")
                if (file != null) {
                    saveResourceDirInPrefs(sharedPrefs, key, dir)
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

    private fun loaSectorPathPixelFromCache(key : String) : PathPixelData{
        val loadedPpLocalUrl = getResourceDirInPrefs(sharedPrefs, key)
        if (!loadedPpLocalUrl.isNullOrEmpty()) {
            var fivalext = ""
            val file = File(loadedPpLocalUrl)
            if (file.exists()) {
                fivalext = file.readText()
            }
            return parseRoad(fivalext)
        }
        return PathPixelData()
    }

    private fun parseRoad(data: String): PathPixelData {
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
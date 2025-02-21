package com.tjlabs.tjlabsresource_sdk_android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstant.getImageBaseURL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL


internal interface BuildingLevelImageDelegate {
    fun onBuildingLevelImageData(isOn: Boolean, imageKey: String, data : Bitmap?)
}
internal class TJLabsImageManager {
    companion object {
        val buildingLevelImageDataMap = mutableMapOf<String, Bitmap>()
    }

    var delegate: BuildingLevelImageDelegate? = null
    private var baseURL = getImageBaseURL()
    var region = ResourceRegion.KOREA

    fun loadImage(sectorId: Int, buildingLevelData : Map<String, List<String>>){
        for ((buildingName, levelNameList) in buildingLevelData) {
            for (levelName in levelNameList) {
                val imageKey = "image_${sectorId}_${buildingName}_$levelName"
                loadBuildingLevelImage(sectorId, buildingName, levelName) { bitmap, _ ->
                    if (bitmap != null) {
                        buildingLevelImageDataMap[imageKey] = bitmap
                        delegate?.onBuildingLevelImageData( true, imageKey, bitmap)

                    } else {
                        delegate?.onBuildingLevelImageData( false, imageKey, null)
                    }
                }
            }
        }
    }

    private fun loadBuildingLevelImage(sectorId: Int, building: String, level: String, completion: (Bitmap?, Exception?) -> Unit) {
        val urlString = "$baseURL/map/$sectorId/$building/$level.png"

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
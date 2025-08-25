package com.tjlabs.tjlabsresource_sdk_android.manager

import com.tjlabs.tjlabsresource_sdk_android.ResourceRegion
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.tjlabs.tjlabsresource_sdk_android.BuildingOutput
import com.tjlabs.tjlabsresource_sdk_android.LevelIdOsInput
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstants
import com.tjlabs.tjlabsresource_sdk_android.manager.TJLabsParamManager.Companion.levelParamData
import com.tjlabs.tjlabsresource_sdk_android.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL


internal interface BuildingLevelImageDelegate {
    fun onBuildingLevelImageData(imageKey: String, data : Bitmap?)
    fun onBuildingLevelImageError(imageKey: String)

}
internal class TJLabsImageManager {
    companion object {
        val buildingLevelImageDataMap = mutableMapOf<String, Bitmap>()
    }

    var delegate: BuildingLevelImageDelegate? = null

    fun loadImage(sectorId: Int, buildingsData: List<BuildingOutput>) {
        Logger.d("(TJLabsResource) loadImage")

        for (building in buildingsData) {
            for (level in building.levels) {
                if (level.name.contains("_D")) continue
                val imageKey = "${sectorId}_${building.name}_${level.name}"

                val cached = buildingLevelImageDataMap[imageKey]
                if (cached != null) {
                    delegate?.onBuildingLevelImageData(imageKey, cached)
                    continue
                }

                updateLevelImage(imageKey, level.image)
            }
        }
    }

    fun updateLevelImage(key: String, url: String) {
        Logger.d("(TJLabsResource) updateLevelImage")

        loadBuildingLevelImage(url) { bitmap, _ ->
            if (bitmap != null) {
                Logger.d("(TJLabsResource) loadBuildingLevelImage // bitmap : $bitmap")

                buildingLevelImageDataMap[key] = bitmap
                delegate?.onBuildingLevelImageData(key, bitmap)

            } else {
                delegate?.onBuildingLevelImageError(key)
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
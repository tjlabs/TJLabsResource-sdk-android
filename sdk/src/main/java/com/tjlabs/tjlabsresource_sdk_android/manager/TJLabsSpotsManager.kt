package com.tjlabs.tjlabsresource_sdk_android.manager

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tjlabs.tjlabsresource_sdk_android.BuildingLevelTag
import com.tjlabs.tjlabsresource_sdk_android.SpotsOutput
import com.tjlabs.tjlabsresource_sdk_android.SpotType
import com.tjlabs.tjlabsresource_sdk_android.util.TJLogger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors


internal interface SpotsDelegate {
    fun onSpotsData(spotsKey : Int, type : SpotType, data : Any)
    fun onSpotsError(spotsKey : Int, type : SpotType)
}

internal class TJLabsSpotsManager {
    companion object {
        val blTagDataMap: MutableMap<Int, List<BuildingLevelTag>> = mutableMapOf()
    }
    var delegate : SpotsDelegate? = null

    fun loadSpots(context : Context, sectorId : Int, completion : (Boolean) -> Unit) {
        val latch = CountDownLatch(1)
        val lock = Any()
        var isAllSuccess = true

        if (sectorId != 6) {
            completion(true)
            return
        }

        TJLogger.d("[loadSpots] Generated key: $sectorId")

        blTagDataMap[sectorId]?.let { cached ->
            TJLogger.d("[loadSpots] Found cached data for key: $sectorId")
            delegate?.onSpotsData(sectorId, SpotType.BUILDING_LEVEL_TAG, cached)
            completion(true)
            return
        }

        TJLogger.d("[loadSpots] No cache found for key: $sectorId, starting async loading")

        Executors.newSingleThreadExecutor().execute {
            try {
                val resourceName = "${sectorId}_bl_tag"
                val inputStream = context.assets.open("$resourceName.json")
                val jsonString = inputStream.bufferedReader().use { it.readText() }

                processJson(jsonString, sectorId) { success ->
                    synchronized(lock) {
                        if (!success) isAllSuccess = false
                    }
                    latch.countDown()
                }
            } catch (e: Exception) {
                TJLogger.d("[loadSpots] File load error: ${e.localizedMessage}")
                Handler(Looper.getMainLooper()).post {
                    delegate?.onSpotsError(
                        sectorId,
                        SpotType.BUILDING_LEVEL_TAG
                    )
                }
                isAllSuccess = false
                latch.countDown()
            }
        }

        Executors.newSingleThreadExecutor().execute {
            latch.await()
            Handler(Looper.getMainLooper()).post {
                completion(isAllSuccess)
            }
        }
    }


    private fun processJson(
        jsonString: String,
        key: Int,
        completion: (Boolean) -> Unit
    ) {
        TJLogger.d("[processJson] Reading JSON, length: ${jsonString.length}")

        try {
            val output = decodeSpotsOutput(jsonString)
            if (output == null) {
                Handler(Looper.getMainLooper()).post {
                    delegate?.onSpotsError(
                        key,
                        SpotType.BUILDING_LEVEL_TAG
                    )
                    completion(false)
                }
                return
            }

            val tags = output.buildilng_level_tags

            Handler(Looper.getMainLooper()).post {
                blTagDataMap[key] = tags

                delegate?.onSpotsData(
                    key,
                    SpotType.BUILDING_LEVEL_TAG,
                    tags
                )
                completion(true)
            }

        } catch (e: Exception) {
            TJLogger.d("[processJson] Error: ${e.localizedMessage}")
            Handler(Looper.getMainLooper()).post {
                delegate?.onSpotsError(
                    key,
                    SpotType.BUILDING_LEVEL_TAG
                )
                completion(false)
            }
        }
    }

    private fun decodeSpotsOutput(jsonString: String): SpotsOutput? {
        TJLogger.d(
            "[decodeSpotsOutput] Start decoding, length: ${jsonString.length}"
        )

        val gson = Gson()

        // 1차 시도: 배열 디코딩
        try {
            val listType = object : TypeToken<List<BuildingLevelTag>>() {}.type
            val tags: List<BuildingLevelTag> = gson.fromJson(jsonString, listType)
            return SpotsOutput(tags)
        } catch (e: Exception) {
            TJLogger.d(
                "[decodeSpotsOutput] Array decode failed: ${e.localizedMessage}"
            )
        }

        // 2차 시도: SpotsOutput 디코딩
        return try {
            gson.fromJson(jsonString, SpotsOutput::class.java)
        } catch (e: Exception) {
            TJLogger.d(
                "[decodeSpotsOutput] SpotsOutput decode failed: ${e.localizedMessage}"
            )
            null
        }
    }


}
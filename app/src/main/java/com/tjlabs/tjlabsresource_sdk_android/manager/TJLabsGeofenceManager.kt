package com.tjlabs.tjlabsresource_sdk_android.manager

import android.app.Application
import android.content.SharedPreferences
import com.tjlabs.tjlabsresource_sdk_android.BuildingOutput
import com.tjlabs.tjlabsresource_sdk_android.GeofenceData
import com.tjlabs.tjlabsresource_sdk_android.LevelIdOsInput
import com.tjlabs.tjlabsresource_sdk_android.PathPixelData
import com.tjlabs.tjlabsresource_sdk_android.ResourceRegion
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstants
import com.tjlabs.tjlabsresource_sdk_android.manager.TJLabsScaleOffsetManager.Companion.scaleOffsetDataMap
import com.tjlabs.tjlabsresource_sdk_android.util.Logger


internal interface GeofenceDelegate {
    fun onGeofenceData(geofenceKey: String, data: GeofenceData)
    fun onGeofenceError(geofenceKey: String)
}

internal class TJLabsGeofenceManager {
    companion object {
        val geofenceDataMap: MutableMap<String, GeofenceData> = mutableMapOf()
    }

    var delegate: GeofenceDelegate? = null
    private var region: String = ResourceRegion.KOREA.value

    fun setRegion(region: String) {
        this.region = region
    }

    fun loadGeofence(region: String,
                     sectorId: Int,
                     buildingsData: List<BuildingOutput>
    ) {
        for (building in buildingsData) {
            for (level in building.levels) {
                if (level.name.contains("_D")) continue

                val geoKey = "${sectorId}_${building.name}_${level.name}"

                val cached = geofenceDataMap[geoKey]
                if (cached != null) {
                    delegate?.onGeofenceData(geoKey, cached)
                    continue
                }

                updateLevelGeofence(geoKey, level.id)
            }
        }
    }

    //원하는 key, level id 로 직접 요청하기
    fun updateLevelGeofence(key : String, levelId : Int) {
        val input = LevelIdOsInput(level_id = levelId)

        TJLabsResourceNetworkManager.getGeofence(
            TJLabsResourceNetworkConstants.getUserBaseURL(),
            input,
            TJLabsResourceNetworkConstants.getUserGeoServerVersion()
        ) { status, msg, result ->

            // 실패 처리
            if (status != 200) {
                delegate?.onGeofenceError(key)
            }

            if (result != null) {
                geofenceDataMap[key] = result
                delegate?.onGeofenceData(key, result)
            } else {
                delegate?.onGeofenceError(key)
            }
        }
    }
}
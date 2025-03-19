package com.tjlabs.tjlabsresource_sdk_android

import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstant.getGeoServerVersion
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstant.getUserBaseURL


internal interface GeofenceDelegate{
    fun onGeofenceData(isOn : Boolean, key : String, geofenceData : GeofenceData )
    fun onGeofenceError()
}


internal class TJLabsGeofenceManager {
    companion object {
        var geofenceDataMap : MutableMap<String, GeofenceData> = mutableMapOf()

    }

    var delegate: GeofenceDelegate? = null
    var region = ResourceRegion.KOREA


    fun loadGeofenceData(sectorId: Int){
        val input = SectorInput(sectorId, operating_system = "Android")
        TJLabsResourceNetworkManager.postGeofence(
            getUserBaseURL(),
            input,
            getGeoServerVersion()
        ) { statusCode, outputGeo ->
            if (statusCode == 200) {
                if (outputGeo.geofence_list.isNotEmpty()) {
                    val geoInfo = outputGeo.geofence_list
                    for (element in geoInfo) {
                        val buildingName = element.building_name
                        val levelName = element.level_name
                        val key = "geofence_${sectorId}_${buildingName}_${levelName}"

                        val entranceAreaResult = element.entrance_area
                        val entranceMatchingAreaResult = element.entrance_matching_area
                        val levelChangeAreaResult = element.level_change_area
                        val drModeAreasResult = element.dr_mode_areas
                        geofenceDataMap[key] = GeofenceData(entranceAreaResult, entranceMatchingAreaResult, levelChangeAreaResult, drModeAreasResult)
                        delegate?.onGeofenceData(true, key, GeofenceData(entranceAreaResult, entranceMatchingAreaResult, levelChangeAreaResult, drModeAreasResult))
                    }
                }else {
                    delegate?.onGeofenceError()
                }
            } else {
                delegate?.onGeofenceError()
            }
        }

    }
}
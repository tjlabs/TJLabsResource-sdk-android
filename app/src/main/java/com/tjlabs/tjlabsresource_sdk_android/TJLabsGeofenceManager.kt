package com.tjlabs.tjlabsresource_sdk_android

import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstant.getGeoServerVersion
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstant.getUserBaseURL


internal interface GeofenceDelegate{
    fun onEntranceAreaData(isOn : Boolean, key : String, data : List<List<Float>>?)
    fun onEntranceMatchingAreaData(isOn : Boolean, key : String, data : List<List<Float>> ?)
    fun onLevelChangeArea(isOn : Boolean, key : String, data : List<List<Float>>?)
    fun onDrModeArea(isOn : Boolean, key : String, data : DrModeArea?)
    fun onGeofenceError()
}


internal class TJLabsGeofenceManager {
    companion object {
        var entranceArea: MutableMap<String, List<List<Float>>> = mutableMapOf()
        var entranceMatchingArea: MutableMap<String, List<List<Float>>> = mutableMapOf()
        var levelChangeArea: MutableMap<String, List<List<Float>>> = mutableMapOf()
        var sectorDRModeArea : MutableMap<String, DrModeArea> = mutableMapOf()
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
                        val key = "${input.sector_id}_${buildingName}_${levelName}"

                        val entranceAreaResult = element.entrance_area
                        val entranceMatchingAreaResult = element.entrance_matching_area
                        val levelChangeAreaResult = element.level_change_area
                        val drModeAreasResult = element.dr_mode_areas

                        if (entranceAreaResult.isNotEmpty()) {
                            entranceArea[key] = entranceAreaResult.map { innerList ->
                                innerList.map { it.toFloat() }
                            }
                            delegate?.onEntranceAreaData(true, key, entranceArea[key])
                        }
                        if (entranceMatchingAreaResult.isNotEmpty()) {
                            entranceMatchingArea[key] = entranceMatchingAreaResult.map { innerList ->
                                innerList.map { it.toFloat() }
                            }
                            delegate?.onEntranceMatchingAreaData(true, key, entranceMatchingArea[key])
                        }
                        if (levelChangeAreaResult.isNotEmpty()) {
                            levelChangeArea[key] = levelChangeAreaResult.map { innerList ->
                                innerList.map { it.toFloat() }
                            }
                            delegate?.onLevelChangeArea(true, key, levelChangeArea[key])
                        }
                        if (drModeAreasResult.isNotEmpty()) {
                            for (info in drModeAreasResult) {
                                val drModeKey = "${sectorId}_${buildingName}_${levelName}_${info.number}"
                                sectorDRModeArea[drModeKey] = DrModeArea(info.number, info.range, info.direction, info.nodes)
                                delegate?.onDrModeArea(true, drModeKey, DrModeArea(info.number, info.range, info.direction, info.nodes))
                            }
                        }
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
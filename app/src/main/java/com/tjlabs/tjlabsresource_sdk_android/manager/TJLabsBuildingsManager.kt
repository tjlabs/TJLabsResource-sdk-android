package com.tjlabs.tjlabsresource_sdk_android.manager
import com.tjlabs.tjlabsresource_sdk_android.BuildingOutput
import com.tjlabs.tjlabsresource_sdk_android.ResourceRegion
import com.tjlabs.tjlabsresource_sdk_android.util.Logger

internal interface BuildingsDelegate {
    fun onBuildingsData(data: List<BuildingOutput>)
}

internal class TJLabsBuildingsManager {
    companion object {
        val buildingsDataMap: MutableMap<Int, List<BuildingOutput>> = mutableMapOf()
        val levelIdMap: MutableMap<String, Int> = mutableMapOf()
        val levelImageUrlMap: MutableMap<String, String> = mutableMapOf()
    }

    var delegate: BuildingsDelegate? = null

    fun setBuildings(sectorId: Int, buildings: List<BuildingOutput>) {
        buildingsDataMap[sectorId] = buildings
        for (building in buildings) {
            for (level in building.levels) {
                val key = "${sectorId}_${building.name}_${level.name}"
                levelIdMap[key] = level.id
                levelImageUrlMap[key] = level.image
            }
        }
        delegate?.onBuildingsData(buildings)
    }

    fun getBuildings(sectorId: Int): List<BuildingOutput>? {
        return buildingsDataMap[sectorId]
    }
}
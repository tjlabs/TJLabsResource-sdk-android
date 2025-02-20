package com.tjlabs.tjlabsresource_sdk_android

import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstant.getLevelServerVersion
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstant.getScaleServerVersion
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstant.getUserBaseURL

class TJLabsBuildingLevelManager {
    companion object {
        var buildingLevelDataMap: MutableMap<Int, MutableMap<String, MutableList<String>>> = mutableMapOf()
    }

    var region = ResourceRegion.KOREA

    fun loadBuildingLevel(region: String, sectorId: Int, completion: (Boolean, Map<String, List<String>>) -> Unit) {
        val result = mutableMapOf<String, MutableList<String>>()

        buildingLevelDataMap[sectorId]?.let {
            completion(true, it)
        } ?: run {
            val input = SectorIdInput(sectorId)
            TJLabsResourceNetworkManager.postBuildingLevel(
                getUserBaseURL(), input, getLevelServerVersion()
            ) { statusCode, buildingLevelList ->
                if (statusCode == 200) {
                    val buildingLevelInfo = makeBuildingLevelInfo(sectorId, buildingLevelList)
                    setBuildingLevelDataMap(sectorId, buildingLevelInfo)
                    println("(TJLabsResource) Success : loadBuildingLevel")
                    completion(true, buildingLevelInfo)
                } else {
                    println("(TJLabsResource) Fail : loadBuildingLevel")
                    completion(false, result)
                }
            }
        }
    }

    private fun setBuildingLevelDataMap(sectorId: Int, buildingLevelInfo: Map<String, List<String>>) {
        buildingLevelDataMap[sectorId] = buildingLevelInfo.mapValues { it.value.toMutableList() }.toMutableMap()
    }

    private fun makeBuildingLevelInfo(sectorId: Int, outputLevel: LevelOutputList): Map<String, List<String>> {
        val infoBuildingLevel = mutableMapOf<String, MutableList<String>>()
        for (element in outputLevel.level_list) {
            val buildingName = element.building_name
            val levelName = element.level_name

            if (!levelName.contains("_D")) {
                val levels = infoBuildingLevel.getOrDefault(buildingName, mutableListOf())
                levels.add(levelName)
                infoBuildingLevel[buildingName] = levels.sortedWith(::compareFloorNames).toMutableList()
            }
        }
        return infoBuildingLevel
    }

    private fun compareFloorNames(lhs: String, rhs: String): Int {
        fun floorValue(floor: String): Int {
            return when {
                floor.startsWith("B") -> floor.drop(1).toIntOrNull()?.let { -it } ?: 0
                floor.endsWith("F") -> floor.dropLast(1).toIntOrNull() ?: 0
                else -> 0
            }
        }
        return floorValue(rhs) - floorValue(lhs)
    }

}

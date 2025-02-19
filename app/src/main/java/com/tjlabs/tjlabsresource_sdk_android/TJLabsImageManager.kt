package com.tjlabs.tjlabsresource_sdk_android

import com.tjlabs.tjlabsresource_sdk_android.TJLabsBuildingLevelManager.Companion.buildingLevelDataMap
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstant.getLevelServerVersion
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstant.getUserBaseURL

class TJLabsImageManager {
    companion object {
        var isPerformed = false
    }

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
                } else {
                    println("(TJLabsResource) Fail : loadBuildingLevel")
                    completion(false, result)
                }
            }
        }
    }
}
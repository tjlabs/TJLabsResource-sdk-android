package com.tjlabs.tjlabsresource_sdk_android.manager
import com.tjlabs.tjlabsresource_sdk_android.BuildingOutput
import com.tjlabs.tjlabsresource_sdk_android.LevelIdOsInput
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstants
import com.tjlabs.tjlabsresource_sdk_android.util.TJLogger

internal interface LevelsDelegate {
    fun onLevelWardsData(levelKey: String, data: List<String>)
    fun onLevelWardsDataError(unitKey: String)
}

internal class TJLabsLevelsManager {
    companion object {
        val levelWardsDataMap: MutableMap<String, List<String>> = mutableMapOf()
    }

    var delegate: LevelsDelegate? = null

    fun loadLevelsWards(sectorId : Int, buildingsData: List<BuildingOutput>
    ) {
        for (building in buildingsData) {
            for (level in building.levels) {
                if (level.name.contains("_D")) continue

                val levelKey = "${sectorId}_${building.name}_${level.name}"

                val cached = levelWardsDataMap[levelKey]
                if (cached != null) {
                    delegate?.onLevelWardsData(levelKey, cached)
                    continue
                }

                updateLevelWards(levelKey, level.id)
            }
        }
    }

    private fun updateLevelWards(key: String, levelId: Int) {
        val input = LevelIdOsInput(level_id = levelId)

        TJLabsResourceNetworkManager.getLevelWards(
            TJLabsResourceNetworkConstants.getUserBaseURL(),
            input,
            TJLabsResourceNetworkConstants.getUserLevelWardsVersion()
        ) { status, msg, result ->
            // 실패 처리
            if (status != 200) {
                TJLogger.d(msg)
                delegate?.onLevelWardsDataError(key)
            }
            if (result != null) {
                levelWardsDataMap[key] = result.wards.map { it.name }
                delegate?.onLevelWardsData(key, result.wards.map { it.name })
            } else {
                delegate?.onLevelWardsDataError(key)
            }
        }
    }
}
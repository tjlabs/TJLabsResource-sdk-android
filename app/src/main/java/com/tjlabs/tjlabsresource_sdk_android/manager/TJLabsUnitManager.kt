package com.tjlabs.tjlabsresource_sdk_android.manager

import com.tjlabs.tjlabsresource_sdk_android.BuildingOutput
import com.tjlabs.tjlabsresource_sdk_android.LevelIdOsInput
import com.tjlabs.tjlabsresource_sdk_android.ResourceRegion
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstants
import com.tjlabs.tjlabsresource_sdk_android.UnitData
import com.tjlabs.tjlabsresource_sdk_android.util.Logger

internal interface UnitDelegate {
    fun onUnitData(unitKey: String, data : List<UnitData>?)
    fun onUnitDataError(unitKey: String)
}


internal class TJLabsUnitManager {
    companion object {
        val unitDataMap : MutableMap<String, List<UnitData>> = mutableMapOf()
    }

    var delegate: UnitDelegate? = null

    fun loadUnit(
        sectorId: Int,
        buildingsData: List<BuildingOutput>
    ) {
        for (building in buildingsData) {
            for (level in building.levels) {
                if (level.name.contains("_D")) continue

                val unitKey = "${sectorId}_${building.name}_${level.name}"

                val cached = unitDataMap[unitKey]
                if (cached != null) {
                    delegate?.onUnitData(unitKey, cached)
                    continue
                }

                updateLevelUnit(unitKey, level.id)
            }
        }
    }

    fun updateLevelUnit(key: String, levelId: Int) {
        val input = LevelIdOsInput(level_id = levelId)

        TJLabsResourceNetworkManager.getUnit(
            TJLabsResourceNetworkConstants.getUserBaseURL(),
            input,
            TJLabsResourceNetworkConstants.getUserUnitServerVersion()
        ) { status, msg, result ->

            // 실패 처리
            if (status != 200) {
                Logger.d(msg)
                delegate?.onUnitDataError(key)
            }

            if (result != null) {
                unitDataMap[key] = result.units
                delegate?.onUnitData(key, result.units)
            } else {
                delegate?.onUnitDataError(key)
            }
        }
    }
}
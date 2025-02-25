package com.tjlabs.tjlabsresource_sdk_android

import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstant.getUnitServerVersion
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstant.getUserBaseURL

internal interface UnitDelegate {
    fun onUnitData(isOn: Boolean, unitKey: String, data : List<UnitData>?)
    fun onUnitDataError()
}


internal class TJLabsUnitManager {
    companion object {
        val unitDataMap : MutableMap<String, List<UnitData>> = mutableMapOf()
    }

    var delegate: UnitDelegate? = null
    var region = ResourceRegion.KOREA

    fun loadUnits(sectorId : Int) {
        val input = SectorIdInput(sectorId)
        TJLabsResourceNetworkManager.postUnit(
            getUserBaseURL(), input, getUnitServerVersion()
        ) { statusCode, unitOutputList ->
            if (statusCode == 200) {
                updateUnits(sectorId, unitOutputList)
            } else {
                delegate?.onUnitDataError()
            }
        }

    }


    private fun updateUnits(sectorId: Int, unitOutputList: UnitOutputList) {
        val unitList = unitOutputList.unit_list
        for (unit in unitList) {
            val buildingName = unit.building_name
            val levelName = unit.level_name

            val unitKey = "unit_${sectorId}_${buildingName}_${levelName}"
            unitDataMap[unitKey] = unit.units
            delegate?.onUnitData(true, unitKey, unit.units)
        }
    }
}
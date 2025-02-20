package com.tjlabs.tjlabsresource_sdk_android

import android.util.Log
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstant.getScaleServerVersion
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstant.getUserBaseURL


class TJLabsScaleOffsetManager {
    interface ScaleOffsetDelegate {
        fun onScaleOffsetData(manager: TJLabsScaleOffsetManager, isOn: Boolean)
    }

    companion object {
        var scaleOffsetDataMap: MutableMap<String, List<Float>> = mutableMapOf()
    }

    var delegate: ScaleOffsetDelegate? = null
    var region = ResourceRegion.KOREA

    fun loadScaleOffset(region: String, sectorId: Int) {
        val input = SectorInput(sectorId, "Android")
        TJLabsResourceNetworkManager.postScaleOffset(getUserBaseURL(), input, getScaleServerVersion()) {
          statusCode, scaleOutputList ->
            if (statusCode == 200) {
                updateScaleOffset(sectorId, scaleOutputList)
            } else {
                delegate?.onScaleOffsetData(this, false)
            }
        }
    }

    private fun updateScaleOffset(sectorId: Int, scaleOutputList: ScaleOutputList) {
        for (element in scaleOutputList.scale_list) {
            val buildingName = element.building_name
            val levelName = element.level_name
            val scaleKey = "scale_${sectorId}_${buildingName}_${levelName}"
            scaleOffsetDataMap[scaleKey] = element.image_scale
            Log.d(TAG, "success update offset // scaleKey :$scaleKey // scale : ${element.image_scale}")
        }
        delegate?.onScaleOffsetData(this, true)
    }
}



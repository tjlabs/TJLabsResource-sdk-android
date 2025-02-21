package com.tjlabs.tjlabsresource_sdk_android

import android.util.Log
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstant.getScaleServerVersion
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstant.getUserBaseURL

internal interface ScaleOffsetDelegate {
    fun onScaleOffsetData(isOn: Boolean, scaleKey: String, data : List<Float>)
    fun onScaleError()
}

internal class TJLabsScaleOffsetManager {
    companion object {
        var scaleOffsetDataMap: MutableMap<String, List<Float>> = mutableMapOf()
    }

    var delegate: ScaleOffsetDelegate? = null
    var region = ResourceRegion.KOREA

    fun loadScaleOffset(sectorId: Int) {
        val input = SectorInput(sectorId, "Android")
        TJLabsResourceNetworkManager.postScaleOffset(getUserBaseURL(), input, getScaleServerVersion()) {
          statusCode, scaleOutputList ->
            if (statusCode == 200) {
                updateScaleOffset(sectorId, scaleOutputList)
            } else {
                delegate?.onScaleError()
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
            delegate?.onScaleOffsetData( true, scaleKey, element.image_scale)
        }
    }
}



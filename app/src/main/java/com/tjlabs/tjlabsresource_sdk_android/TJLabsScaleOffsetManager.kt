package com.tjlabs.tjlabsresource_sdk_android

import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstant.getScaleServerVersion
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstant.getUserBaseURL

interface ScaleOffsetDelegate {
    fun onScaleOffsetData(manager: TJLabsScaleOffsetManager, isOn: Boolean)
}

class TJLabsScaleOffsetManager {
    companion object {
        var isPerformed: Boolean = false
        var scaleOffsetDataMap: MutableMap<String, List<Float>> = mutableMapOf()
    }

    var delegate: ScaleOffsetDelegate? = null

    fun loadScaleOffset(region: String, sectorId: Int) {
        val input = SectorInput(sectorId, "Android")
        TJLabsResourceNetworkManager.postScaleOffset(getUserBaseURL(), input, getScaleServerVersion()) {
          statusCode, scaleOutputList ->
            if (statusCode == 200) {
                updateScaleOffset(sectorId, scaleOutputList)
            } else {
                println("(TJLabsResource) Fail : loadScaleOffset")
                delegate?.onScaleOffsetData(this, false)
            }
        }
    }

    private fun updateScaleOffset(sectorId: Int, scaleOutputList: ScaleOutputList) {
        for (element in scaleOutputList.scale_list) {
            val scaleKey = "scale_${'$'}sectorId_${'$'}{element.buildingName}_${'$'}{element.levelName}"
            scaleOffsetDataMap[scaleKey] = element.image_scale
        }
        delegate?.onScaleOffsetData(this, true)
    }
}



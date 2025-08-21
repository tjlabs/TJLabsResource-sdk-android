package com.tjlabs.tjlabsresource_sdk_android.manager

import com.tjlabs.tjlabsresource_sdk_android.BuildingOutput
import com.tjlabs.tjlabsresource_sdk_android.LevelIdOsInput
import com.tjlabs.tjlabsresource_sdk_android.ResourceRegion
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstants
import com.tjlabs.tjlabsresource_sdk_android.util.Logger

internal interface ScaleOffsetDelegate {
    fun onScaleOffsetData(scaleKey: String, data: List<Float>)
    fun onScaleOffsetError(scaleKey: String)
}


internal class TJLabsScaleOffsetManager {
    companion object {
        val scaleOffsetDataMap: MutableMap<String, List<Float>> = mutableMapOf()
    }

    var delegate: ScaleOffsetDelegate? = null
    private var region: String = ResourceRegion.KOREA.value

    fun setRegion(region: String) {
        this.region = region
    }

    fun loadScaleOffset(
        region: String,
        sectorId: Int,
        buildingsData: List<BuildingOutput>
    ) {
        for (building in buildingsData) {
            for (level in building.levels) {
                if (level.name.contains("_D")) continue

                val input = LevelIdOsInput(level_id = level.id)
                val scaleKey = "${sectorId}_${building.name}_${level.name}"

                val cached = scaleOffsetDataMap[scaleKey]
                if (cached != null) {
                    delegate?.onScaleOffsetData(scaleKey, cached)
                    continue
                }

                TJLabsResourceNetworkManager.getScaleOffset(
                    TJLabsResourceNetworkConstants.getUserBaseURL(),
                    input,
                    TJLabsResourceNetworkConstants.getUserScaleServerVersion()
                ) { status, msg, result ->

                    // 실패 처리
                    if (status != 200) {
                        Logger.d(msg)
                        delegate?.onScaleOffsetError(scaleKey)
                    }

                    if (result != null) {
                        scaleOffsetDataMap[scaleKey] = result.image_scale
                        delegate?.onScaleOffsetData(scaleKey, result.image_scale)
                    } else {
                        delegate?.onScaleOffsetError(scaleKey)
                    }
                }
            }
        }
    }

    //원하는 key, level id 로 직접 요청하기
    fun updateLevelScaleOffset(key : String, levelId : Int) {
        val input = LevelIdOsInput(level_id = levelId)

        TJLabsResourceNetworkManager.getScaleOffset(
            TJLabsResourceNetworkConstants.getUserBaseURL(),
            input,
            TJLabsResourceNetworkConstants.getUserScaleServerVersion()
        ) { status, msg, result ->

            // 실패 처리
            if (status != 200) {
                Logger.d(msg)
                delegate?.onScaleOffsetError(key)
            }

            if (result != null) {
                scaleOffsetDataMap[key] = result.image_scale
                delegate?.onScaleOffsetData(key, result.image_scale)
            } else {
                delegate?.onScaleOffsetError(key)
            }
        }

    }
}
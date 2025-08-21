package com.tjlabs.tjlabsresource_sdk_android.manager

import com.tjlabs.tjlabsresource_sdk_android.BuildingOutput
import com.tjlabs.tjlabsresource_sdk_android.LevelIdOsInput
import com.tjlabs.tjlabsresource_sdk_android.LevelParameterOutput
import com.tjlabs.tjlabsresource_sdk_android.ResourceRegion
import com.tjlabs.tjlabsresource_sdk_android.SectorIdOsInput
import com.tjlabs.tjlabsresource_sdk_android.SectorParameterOutput
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstants

enum class ParamErrorType {
    Sector, Level
}

internal interface ParamDelegate {
    fun onSectorParamData(data: SectorParameterOutput)
    fun onLevelParamData(paramKey: String, data: LevelParameterOutput)
    fun onParamError(type: ParamErrorType, paramKey: String?)
}

internal class TJLabsParamManager {
    companion object {
        val sectorParamData: MutableMap<Int, SectorParameterOutput> = mutableMapOf()
        val levelParamData: MutableMap<String, LevelParameterOutput> = mutableMapOf()
    }

    var delegate: ParamDelegate? = null
    private var region: String = ResourceRegion.KOREA.value

    fun setRegion(region: String) {
        this.region = region
    }

    fun loadSectorParam(region: String,
                     sectorId: Int) {
        val input = SectorIdOsInput(sector_id = sectorId)

        TJLabsResourceNetworkManager.getSectorParam(
            TJLabsResourceNetworkConstants.getUserBaseURL(),
            input,
            TJLabsResourceNetworkConstants.getUserSectorParamVersion()
        ) { status, msg, result ->

            // 실패 처리
            if (status != 200) {
                delegate?.onParamError(ParamErrorType.Sector, null)
            }

            if (result != null) {
                sectorParamData[sectorId] = result
                delegate?.onSectorParamData(result)
            } else {
                delegate?.onParamError(ParamErrorType.Sector, null)
            }
        }
    }

    fun loadLevelParam(region: String,
                     sectorId: Int,
                     buildingsData: List<BuildingOutput>
    ) {
        for (building in buildingsData) {
            for (level in building.levels) {
                if (level.name.contains("_D")) continue

                val levelKey = "${sectorId}_${building.name}_${level.name}"

                val cached = levelParamData[levelKey]
                if (cached != null) {
                    delegate?.onLevelParamData(levelKey, cached)
                    continue
                }

                updateLevelParam(levelKey, level.id)
            }
        }
    }


    fun updateLevelParam(key : String, levelId : Int) {
        val input = LevelIdOsInput(level_id = levelId)

        TJLabsResourceNetworkManager.getLevelParam(
            TJLabsResourceNetworkConstants.getUserBaseURL(),
            input,
            TJLabsResourceNetworkConstants.getUserLevelParamVersion()
        ) { status, msg, result ->

            // 실패 처리
            if (status != 200) {
                delegate?.onParamError(ParamErrorType.Level, key)
            }

            if (result != null) {
                levelParamData[key] = result
                delegate?.onLevelParamData(key, result)
            } else {
                delegate?.onParamError(ParamErrorType.Level, key)
            }
        }
    }
}
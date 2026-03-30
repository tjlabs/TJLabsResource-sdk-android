package com.tjlabs.tjlabsresource_sdk_android.manager

import com.tjlabs.tjlabsresource_sdk_android.SectorIdInput
import com.tjlabs.tjlabsresource_sdk_android.SectorBundleOutput
import com.tjlabs.tjlabsresource_sdk_android.SectorOutput
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstants
import com.tjlabs.tjlabsresource_sdk_android.BuildingOutput
import com.tjlabs.tjlabsresource_sdk_android.LevelOutput
import com.tjlabs.tjlabsresource_sdk_android.util.TJLogger

internal interface SectorDelegate {
    fun onSectorData(data: SectorOutput)
    fun onSectorError()
}

internal class TJLabsSectorManager {
    companion object {
        val sectorDataMap: MutableMap<Int, SectorOutput> = mutableMapOf()
    }

    var delegate: SectorDelegate? = null

    fun loadSector(sectorId: Int,
        forceUpdate: Boolean,
        completion: (SectorOutput?) -> Unit
    ) {
        // 1) 강제 업데이트가 아니면
        if (!forceUpdate) {
            val cached = sectorDataMap[sectorId]
            if (cached != null) {
                delegate?.onSectorData(cached)
                completion(cached)
                return
            }
        }

        // 2) 네트워크 요청
        val input = SectorIdInput(sectorId)
        TJLabsResourceNetworkManager.getSectorBundleMeta(
            TJLabsResourceNetworkConstants.getUserBaseURL(),
            input,
            TJLabsResourceNetworkConstants.getUserSectorBundleVersion()
        ) { bundleStatus, bundleMsg, bundleMeta ->
            if (bundleStatus in 200 until 300 && bundleMeta != null) {
                TJLabsResourceNetworkManager.getSectorBundleJson(
                    TJLabsResourceNetworkConstants.getUserBaseURL(),
                    bundleMeta.url
                ) { jsonStatus, jsonMsg, bundleData ->
                    if (jsonStatus in 200 until 300 && bundleData != null) {
                        val sectorData = mapBundleToSectorOutput(bundleData)
                        setSectorDataMap(sectorId, sectorData)
                        completion(sectorData)
                        delegate?.onSectorData(sectorData)
                    } else {
                        TJLogger.d(jsonMsg)
                        loadLegacySector(input, sectorId, completion)
                    }
                }
                return@getSectorBundleMeta
            }

            TJLogger.d(bundleMsg)
            loadLegacySector(input, sectorId, completion)
        }
    }

    private fun loadLegacySector(
        input: SectorIdInput,
        sectorId: Int,
        completion: (SectorOutput?) -> Unit
    ) {
        TJLabsResourceNetworkManager.getSector(
            TJLabsResourceNetworkConstants.getUserBaseURL(),
            input,
            TJLabsResourceNetworkConstants.getUserSectorVersion()
        ) { status, msg, result ->
            if (status != 200) {
                TJLogger.d(msg)
                delegate?.onSectorError()
                completion(null)
                return@getSector
            }

            if (result != null) {
                setSectorDataMap(sectorId, result)
                completion(result)
                delegate?.onSectorData(result)
            } else {
                delegate?.onSectorError()
                completion(null)
            }
        }
    }

    private fun mapBundleToSectorOutput(bundle: SectorBundleOutput): SectorOutput {
        val buildings = bundle.buildings.map { bundleBuilding ->
            BuildingOutput(
                id = bundleBuilding.id,
                name = bundleBuilding.name,
                levels = bundleBuilding.levels.map { bundleLevel ->
                    LevelOutput(
                        id = bundleLevel.id,
                        name = bundleLevel.name,
                        image = bundleLevel.map_image?.url ?: ""
                    )
                }
            )
        }

        return SectorOutput(
            id = bundle.id,
            name = bundle.name,
            debug = bundle.debug,
            buildings = buildings
        )
    }

    private fun setSectorDataMap(sectorId: Int, sectorData : SectorOutput) {
        sectorDataMap[sectorId] = sectorData
        TJLogger.d("Info : sectorDataMap = $sectorDataMap")
    }
}

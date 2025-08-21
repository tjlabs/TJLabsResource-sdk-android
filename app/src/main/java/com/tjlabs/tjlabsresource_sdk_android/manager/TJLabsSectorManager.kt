package com.tjlabs.tjlabsresource_sdk_android.manager

import android.util.Log
import com.tjlabs.tjlabsresource_sdk_android.BuildingOutput
import com.tjlabs.tjlabsresource_sdk_android.ResourceRegion
import com.tjlabs.tjlabsresource_sdk_android.SectorIdInput
import com.tjlabs.tjlabsresource_sdk_android.SectorOutput
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstants
import com.tjlabs.tjlabsresource_sdk_android.util.Logger

internal interface SectorDelegate {
    fun onSectorData(data: SectorOutput)
    fun onSectorError()
}

internal class TJLabsSectorManager {
    companion object {
        val sectorDataMap: MutableMap<Int, SectorOutput> = mutableMapOf()
    }

    var delegate: SectorDelegate? = null
    private var region: String = ResourceRegion.KOREA.value

    fun setRegion(region: String) {
        this.region = region
    }

    fun loadSector(
        region: String,
        sectorId: Int,
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
        TJLabsResourceNetworkManager.getSector(
            TJLabsResourceNetworkConstants.getUserBaseURL(),
            input,
            TJLabsResourceNetworkConstants.getUserSectorVersion()
        ) { status, msg, result ->

            // 실패 처리
            if (status != 200) {
                Logger.d(msg)
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
                return@getSector
            }
        }
    }

    private fun setSectorDataMap(sectorId: Int, sectorData : SectorOutput) {
        sectorDataMap[sectorId] = sectorData
        Logger.d("Info : sectorDataMap = $sectorDataMap")
    }
}
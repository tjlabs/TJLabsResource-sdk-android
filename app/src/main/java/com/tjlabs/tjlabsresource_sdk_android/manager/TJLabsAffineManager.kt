package com.tjlabs.tjlabsresource_sdk_android.manager

import com.tjlabs.tjlabsresource_sdk_android.AffineTransParamOutput
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstants
import com.tjlabs.tjlabsresource_sdk_android.util.TJLogger


internal interface AffineDelegate {
    fun onAffineData(sectorId: Int, data: AffineTransParamOutput)
    fun onAffineError(sectorId: Int)
}


internal class TJLabsAffineManager {
    companion object {
        val affineParamMap : MutableMap<Int, AffineTransParamOutput?> = mutableMapOf()
    }

    var delegate: AffineDelegate? = null

    fun loadAffineParam(sectorId: Int) {
        val cached = affineParamMap[sectorId]
        if (cached != null) {
            delegate?.onAffineData(sectorId, cached)
        } else {
            updateAffineParam(sectorId)

        }
    }


    fun updateAffineParam(sectorId: Int) {
        TJLabsResourceNetworkManager.getUserAffineTrans(
            TJLabsResourceNetworkConstants.getUserBaseURL(),
            sectorId,
            TJLabsResourceNetworkConstants.getUserAffineServerVersion(),
        ) { status, msg, result ->
            // 실패 처리
            if (status != 200) {
                TJLogger.d(msg)
                delegate?.onAffineError(sectorId)
            }

            if (result != null) {
                affineParamMap[sectorId] = result.copy()
                delegate?.onAffineData(sectorId, result.copy())
            } else {
                delegate?.onAffineError(sectorId)
            }
        }
    }

}
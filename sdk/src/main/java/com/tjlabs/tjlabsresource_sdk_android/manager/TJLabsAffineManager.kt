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

    fun loadAffineParam(sectorId: Int, completion : (Boolean) -> Unit) {
        val cached = affineParamMap[sectorId]
        if (cached != null) {
            delegate?.onAffineData(sectorId, cached)
            completion(true)
        } else {
            updateAffineParam(sectorId) {
                isSuccess -> completion(isSuccess)
            }

        }
    }


    fun updateAffineParam(sectorId: Int, completion : (Boolean) -> Unit) {
        TJLabsResourceNetworkManager.getUserAffineTrans(
            TJLabsResourceNetworkConstants.getUserBaseURL(),
            sectorId,
            TJLabsResourceNetworkConstants.getUserAffineServerVersion(),
        ) { status, msg, result ->
            // 실패 처리
            if (status != 200) {
                TJLogger.d(msg)
                delegate?.onAffineError(sectorId)
                completion(false)
            }

            if (result != null) {
                affineParamMap[sectorId] = result.copy()
                delegate?.onAffineData(sectorId, result.copy())
                completion(true)
            } else {
                delegate?.onAffineError(sectorId)
                completion(false)
            }
        }
    }

}
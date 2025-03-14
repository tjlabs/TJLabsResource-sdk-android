package com.tjlabs.tjlabsresource_sdk_android

import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstant.getParamServerVersion
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstant.getUserBaseURL

internal interface ParamDelegate {
    fun onParamData(isOn : Boolean, data : ParameterData?)
    fun onParamError()
}

internal class TJLabsParamManager {
    var delegate: ParamDelegate? = null


    fun loadParam(sectorId: Int) {
        val input = SectorInput(sectorId)
        TJLabsResourceNetworkManager.postParameter(getUserBaseURL(), input, getParamServerVersion()) {
            statusCode, outputParam ->
            if (statusCode == 200) {
                if (outputParam != ParameterData()) {
                    delegate?.onParamData(true, outputParam)
                } else{
                    delegate?.onParamData(false, outputParam)
                    delegate?.onParamError()
                }
            } else {
                delegate?.onParamData(false, outputParam)
                delegate?.onParamError()
            }

        }

    }

}
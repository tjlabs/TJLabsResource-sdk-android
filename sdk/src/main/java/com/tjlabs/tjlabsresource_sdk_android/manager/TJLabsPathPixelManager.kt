package com.tjlabs.tjlabsresource_sdk_android.manager

import android.app.Application
import android.content.SharedPreferences
import com.tjlabs.tjlabsresource_sdk_android.BuildingOutput
import com.tjlabs.tjlabsresource_sdk_android.PathPixelData
import com.tjlabs.tjlabsresource_sdk_android.ResourceRegion

internal interface PathPixelDelegate {
    fun onPathPixelData(pathPixelKey: String, data: PathPixelData)
    fun onPathPixelError(pathPixelKey: String)
}

/**
 * PathPixel 정보를 가져오는 클래스
 *
 */
internal class TJLabsPathPixelManager {
    private lateinit var application: Application
    private lateinit var sharedPrefs : SharedPreferences
    private val graphsManager = TJLabsGraphsManager()

    companion object {
        val ppDataMap: MutableMap<String, PathPixelData> = mutableMapOf()
    }

    var delegate: PathPixelDelegate? = null
    private var region: String = ResourceRegion.KOREA.value

    fun setRegion(region: String) {
        this.region = region
    }

    fun init(application: Application, sharedPreferences: SharedPreferences) {
        this.application = application
        this.sharedPrefs = sharedPreferences
        graphsManager.init(application, sharedPreferences)
    }

    /**
     * 서버에서 path pixel 정보를 가져오기 위한 url 를 받아옴
     * 한번 받아온 url 정보는 캐시에 저장됨.
     * @see savePathPixelUrlToCache
     * @param sectorId
     * @param buildingsData
     * @param completion
     */

    /**
     *
     * @param region
     * @param sectorId
     * @param buildingsData
     */
    fun loadPathPixel(
        region: String,
        sectorId: Int,
        buildingsData: List<BuildingOutput>,
        completion: (Boolean) -> Unit
    ) {
        graphsManager.pathPixelDelegate = delegate
        graphsManager.loadPathPixel(region, sectorId, buildingsData) { success ->
            ppDataMap.clear()
            ppDataMap.putAll(TJLabsGraphsManager.pathPixelDataMap)
            completion(success)
        }
    }


    /**
     * 서버에서 받아온 url 을 이용하여 csv 를 다운로드 받음.
     * 다운로드함과 동시에 캐시에 해당 file 경로를 저장하고,
     * 경로를 이용하여 file 정보를 가져옴
     *
     * @param key
     * @param sectorId
     * @param pathPixelUrlFromServer
     */

    fun updateLevelPathPixel(key: String, sectorId: Int, levelId: Int, completion: (Boolean) -> Unit) {
        graphsManager.pathPixelDelegate = delegate
        graphsManager.updateLevelPathPixel(key, sectorId, levelId) { success ->
            ppDataMap.clear()
            ppDataMap.putAll(TJLabsGraphsManager.pathPixelDataMap)
            completion(success)
        }
    }
}

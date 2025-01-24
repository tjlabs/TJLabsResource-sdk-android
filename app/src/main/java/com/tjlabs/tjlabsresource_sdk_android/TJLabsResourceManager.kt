package com.tjlabs.tjlabsresource_sdk_android

import android.app.Application
import android.content.Context
import android.content.SharedPreferences

class TJLabsResourceManager(private val application: Application) {
    //TODO() 네트워크 상황 고려하여 진행 및 다운로드 요청
    private val sharedPrefs: SharedPreferences = application.getSharedPreferences("TJLabsResourcesPref", Context.MODE_PRIVATE)
    private val pathPixelManager = TJLabsPathPixelManager(application, sharedPrefs)
    private var region = JupiterRegion.KOREA
    private var sectorId = 0

    companion object {
        const val SERVICE_NAVI = "navigation"
        const val SERVICE_MAP = "map"
        const val PATH_PIXEL_KEY_NAME = "path-pixel"
        val ppDataMap : MutableMap<String, PathPixelData> = mutableMapOf()

        fun getResourceDirInPrefs(sharedPrefs: SharedPreferences, key: String) : String? {
            val prefKey = "${key}_dir"
            return sharedPrefs.getString(prefKey, null)
        }

        fun getResourceVersionFromPrefs(sharedPrefs: SharedPreferences, key : String) : String? {
            val prefKey = "${key}_version"
            return sharedPrefs.getString(prefKey, null)
        }

        fun saveResourceVersionInPrefs(sharedPrefs: SharedPreferences, key : String, resourceVersion: String
        ) {
            val prefKey = "${key}_version"
            sharedPrefs.edit().putString(prefKey, resourceVersion).apply()
        }

        fun saveResourceDirInPrefs(sharedPrefs: SharedPreferences, key: String, fileDir : String) {
            val prefKey = "${key}_dir"
            sharedPrefs.edit().putString(prefKey, fileDir).apply()
        }
    }

    fun updateResources(region: String, sectorId: Int, completion : (Boolean, String) -> Unit) {
        // 1. 서버에 해당 섹터가 어떤 서비스를 가지고 있는지 요청하기
        // 2. SharedPref에 저징된 리소스별 버젼을 통해 업데이트 필요한 리소스 확인하기
        // 3. 서버 요청 및 버젼 업데이트 및 저장
        this.region = region
        this.sectorId = sectorId
        TJLabsResourceNetworkConstant.setServerURL(region)
        val sectorServiceList = getSectorServiceFromServer(region, sectorId) // 됐다고 가정
        for (service in sectorServiceList) {
            when (service) {
                SERVICE_NAVI -> {
                    pathPixelManager.updatePathPixel(region, sectorId) { bool, msg ->
                        completion(bool, msg)
                    }
                    //resource1.updateResource1(sectorId, building, level, resourceVersion1)
                    //resource2.updateResource2(sectorId, building, level, resourceVersion2) ...

                }

                SERVICE_MAP -> {
                }
            }
        }
    }

    private fun getSectorServiceFromServer(region: String, sectorId: Int) : List<String> {
        //TODO()
        //서버로 부터 해당 리젼의 섹터가 어떤 서비스를 하는 지 요청하고, List<String>으로 전달받음
        return listOf("navigation", "map")
    }
}
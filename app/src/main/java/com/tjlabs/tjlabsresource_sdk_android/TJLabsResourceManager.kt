package com.tjlabs.tjlabsresource_sdk_android

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log


const val PATH_PIXEL_KEY_NAME = "path-pixel"

object TJLabsResourceManager  {
    //TODO() 네트워크 상황 고려하여 진행 및 다운로드 요청
    private lateinit var pathPixelManager : TJLabsPathPixelManager
    private var region = JupiterRegion.KOREA
    private var sectorId = 0
    private lateinit var sharedPrefs: SharedPreferences

    val ppDataMap : MutableMap<String, PathPixelData> = mutableMapOf()
    val ppDataLoaded : MutableMap<String, PathPixelDataIsLoaded> = mutableMapOf()

    internal fun getResourceDirInPrefs(sharedPrefs: SharedPreferences, key: String, resourceName : String) : String? {
        val prefKey = "${key}_${resourceName}_dir"
        return sharedPrefs.getString(prefKey, null)
    }

    internal fun getResourceVersionFromPrefs(sharedPrefs: SharedPreferences, key : String, resourceName : String) : String? {
        val prefKey = "${key}_${resourceName}_version"
        return sharedPrefs.getString(prefKey, null)
    }

    internal fun saveResourceVersionInPrefs(sharedPrefs: SharedPreferences, key : String, resourceName : String, resourceVersion: String
    ) {
        val prefKey = "${key}_${resourceName}_version"
        sharedPrefs.edit().putString(prefKey, resourceVersion).apply()
    }

    internal fun saveResourceDirInPrefs(sharedPrefs: SharedPreferences, key: String, fileDir : String, resourceName : String) {
        val prefKey = "${key}_${resourceName}_dir"
        sharedPrefs.edit().putString(prefKey, fileDir).apply()
    }

    fun loadJupiterResources(application: Application ,region: String, sectorId: Int) {
        init(application, region, sectorId)
        loadPathPixel(application)
    }

    fun loadMapResources(application: Application, region: String, sectorId: Int) {
        init(application, region, sectorId)
        loadPathPixel(application)
    }

    private fun init(application: Application, region: String, sectorId: Int) {
        this.region = region
        this.sectorId = sectorId
        this.sharedPrefs = application.getSharedPreferences("TJLabsResourcesPref", Context.MODE_PRIVATE)
        TJLabsResourceNetworkConstant.setServerURL(region)
    }

    private fun loadPathPixel(application: Application) {
        if (!::pathPixelManager.isInitialized) {
            pathPixelManager = TJLabsPathPixelManager(application, sharedPrefs)
            pathPixelManager.loadPathPixel(region, sectorId)
        } else {
            Log.d("TJLabsResourceLog", "pp is already loaded")
        }
    }

    private fun loadScaleOffset(application: Application) {

    }

    private fun loadUnit(application: Application) {

    }

    private fun loadRouteTrack(application: Application) {

    }

}
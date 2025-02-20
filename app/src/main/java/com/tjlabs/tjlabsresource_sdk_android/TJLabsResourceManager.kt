package com.tjlabs.tjlabsresource_sdk_android

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log


const val TAG = "TJLabsResourceManager"
class TJLabsResourceManager : TJLabsScaleOffsetManager.ScaleOffsetDelegate, TJLabsImageManager.BuildingLevelImageDelegate{
    override fun onBuildingLevelImageData(
        manager: TJLabsImageManager,
        isOn: Boolean,
        imageKey: String
    ) {
        //TODO("Not yet implemented")
    }

    override fun onScaleOffsetData(manager: TJLabsScaleOffsetManager, isOn: Boolean) {
        //TODO("Not yet implemented")
    }

    var delegate: TJLabsResourceManagerDelegate? = null

    private var pathPixelManager = TJLabsPathPixelManager()
    private var buildingLevelManager = TJLabsBuildingLevelManager()
    private var imageManager = TJLabsImageManager()
    private var scaleOffsetManager = TJLabsScaleOffsetManager()

    private lateinit var sharedPrefs: SharedPreferences


    fun loadJupiterResources(application: Application ,region: String, sectorId: Int) {
        init(application, region)
        loadPathPixel(application, region, sectorId)
        loadRouteTrack(region, sectorId)
    }

    fun loadMapResources(application: Application, region: String, sectorId: Int) {
        Log.d(TAG, "load map resources...")
        init(application, region)
        loadPathPixel(application, region, sectorId)
        loadImage(region, sectorId)
        loadScaleOffset(region, sectorId)
        loadUnit(region,sectorId)
    }


    private fun init(application: Application, region: String) {
        this.sharedPrefs = application.getSharedPreferences("TJLabsResourcesPref", Context.MODE_PRIVATE)
        TJLabsResourceNetworkConstant.setServerURL(region)
        Log.d(TAG, "init sharedPreference")

    }

    // MARK: - Public Get Methods
    fun getBuildingLevelData() : Map<Int, Map<String, List<String>>> {
        return TJLabsBuildingLevelManager.buildingLevelDataMap
    }

    fun getPathPixelData() : Map<String, PathPixelData> {
        return TJLabsPathPixelManager.ppDataMap
    }

    fun getPathPixelDataIsLoaded() : Map<String, PathPixelDataIsLoaded> {
        return TJLabsPathPixelManager.ppDataLoaded
    }

    fun getScaleOffset() : Map<String, List<Float>> {
        return TJLabsScaleOffsetManager.scaleOffsetDataMap
    }

    fun updatePathPixelData(region: String, sectorId: Int, key : String, url : String) {
        TJLabsPathPixelManager.isPerformed = true
        pathPixelManager.updatePathPixel(region, sectorId, key, url) { _,_ ->}
    }

    private fun loadPathPixel(application: Application, region: String, sectorId: Int) {
        pathPixelManager.init(application, sharedPrefs)

        if (!TJLabsPathPixelManager.isPerformed) {
            TJLabsPathPixelManager.isPerformed = true
            pathPixelManager.loadPathPixel(sectorId)
            Log.d(TAG, "Load Path Pixel ... sector id : $sectorId ")
        } else {
            Log.d(TAG, "loadPathPixel already performed")
        }
    }

    private fun loadBuildingLevel(region: String, sectorId: Int, completion: (Boolean, Map<String, List<String>>) -> Unit) {
        val buildingLevelData = TJLabsBuildingLevelManager.buildingLevelDataMap[sectorId]

        if (!buildingLevelData.isNullOrEmpty()) {
            completion(true, buildingLevelData)
            return
        }

        buildingLevelManager.loadBuildingLevel(region, sectorId, completion)
    }

    private fun loadImage(region : String, sectorId: Int) {
        loadBuildingLevel(region, sectorId) {
            isSuccess, buildingLevelData ->
            if (isSuccess) imageManager.loadImage(region, sectorId, buildingLevelData)
            else {
                //Fail
            }
        }
    }

    private fun loadScaleOffset(region : String, sectorId: Int) {
        scaleOffsetManager.loadScaleOffset(region, sectorId)
    }

    private fun loadUnit(region : String, sectorId: Int) {

    }

    private fun loadRouteTrack(region : String, sectorId: Int) {

    }

    private fun setRegion(region : String) {
        TJLabsResourceNetworkConstant.setServerURL(region)
        TJLabsFileDownloader.region = region
        buildingLevelManager.region = region
        imageManager.region = region
        pathPixelManager.region = region
        scaleOffsetManager.region = region
    }




}
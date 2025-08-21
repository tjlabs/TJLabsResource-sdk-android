package com.tjlabs.tjlabsresource_sdk_android.manager

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.util.Log
import com.tjlabs.tjlabsresource_sdk_android.BuildingOutput
import com.tjlabs.tjlabsresource_sdk_android.EntranceData
import com.tjlabs.tjlabsresource_sdk_android.EntranceRouteData
import com.tjlabs.tjlabsresource_sdk_android.GeofenceData
import com.tjlabs.tjlabsresource_sdk_android.LevelParameterOutput
import com.tjlabs.tjlabsresource_sdk_android.ParameterData
import com.tjlabs.tjlabsresource_sdk_android.PathPixelData
import com.tjlabs.tjlabsresource_sdk_android.SectorOutput
import com.tjlabs.tjlabsresource_sdk_android.SectorParameterOutput
import com.tjlabs.tjlabsresource_sdk_android.TJLabsFileDownloader
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceManagerDelegate
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstants
import com.tjlabs.tjlabsresource_sdk_android.UnitData
import com.tjlabs.tjlabsresource_sdk_android.util.Logger

class TJLabsResourceManager :
    SectorDelegate,
    BuildingsDelegate,
    ScaleOffsetDelegate,
    PathPixelDelegate,
    GeofenceDelegate,
    EntranceDelegate,
    ParamDelegate,
    BuildingLevelImageDelegate,
    UnitDelegate{

    var delegate: TJLabsResourceManagerDelegate? = null

    private var buildingLevelManager = TJLabsBuildingsManager()
    private var sectorManager = TJLabsSectorManager()
    private var scaleOffsetManager = TJLabsScaleOffsetManager()
    private var pathPixelManager = TJLabsPathPixelManager()
    private var geofenceManager = TJLabsGeofenceManager()
    private var entranceManager = TJLabsEntranceManager()
    private var paramManager = TJLabsParamManager()
    private var imageManager = TJLabsImageManager()
    private var unitManager = TJLabsUnitManager()

    private lateinit var sharedPrefs: SharedPreferences

    init {
        sectorManager.delegate = this
        buildingLevelManager.delegate = this
        scaleOffsetManager.delegate = this
        pathPixelManager.delegate = this
        geofenceManager.delegate = this
        entranceManager.delegate = this
        paramManager.delegate = this
        imageManager.delegate = this
        unitManager.delegate = this
    }

    fun loadMapResource(application: Application, region: String, sectorId: Int) {
        init(application, region)
        setRegion(region)
        loadSector(region = region, sectorId = sectorId) {
                sectorData ->
            Logger.d("sectorData : $sectorData")

            if (sectorData != null) {
                buildingLevelManager.setBuildings(sectorId, sectorData.buildings)
                pathPixelManager.loadPathPixel(region, sectorId, sectorData.buildings)
                imageManager.loadImage(region, sectorId, sectorData.buildings)
                unitManager.loadUnit(region, sectorId, sectorData.buildings)
            } else {
                delegate?.onSectorError()
            }
        }

    }

    fun loadJupiterResource(application: Application, region: String, sectorId: Int) {
        init(application, region)
        setRegion(region)
        loadSector(region = region, sectorId = sectorId) {
            sectorData ->
            Logger.d("sectorData : $sectorData")

            if (sectorData != null) {
                buildingLevelManager.setBuildings(sectorId, sectorData.buildings)
                scaleOffsetManager.loadScaleOffset(region, sectorId, sectorData.buildings)
                pathPixelManager.loadPathPixel(region, sectorId, sectorData.buildings)
                geofenceManager.loadGeofence(region, sectorId, sectorData.buildings)
                entranceManager.loadEntrance(region, sectorId, sectorData.buildings)
                paramManager.loadSectorParam(region, sectorId)
                paramManager.loadLevelParam(region, sectorId, sectorData.buildings)
            } else {
                delegate?.onSectorError()
            }
        }

    }

    private fun init(application: Application, region: String) {
        this.sharedPrefs = application.getSharedPreferences("TJLabsResourcesPref", Context.MODE_PRIVATE)
        setRegion(region)

        //cached 에 접근하기 위한
        pathPixelManager.init(application, sharedPrefs)
        entranceManager.init(application, sharedPrefs)
    }

    private fun setRegion(region : String) {
        TJLabsResourceNetworkConstants.setServerURL(region)
        TJLabsFileDownloader.region = region
        sectorManager.setRegion(region)
        buildingLevelManager.setRegion(region)
        scaleOffsetManager.setRegion(region)
        pathPixelManager.setRegion(region)
        geofenceManager.setRegion(region)
        entranceManager.setRegion(region)
        paramManager.setRegion(region)
    }

    private fun loadSector(region: String, sectorId: Int, forceUpdate: Boolean = false, completion: (SectorOutput?) -> Unit) {
        sectorManager.loadSector(region, sectorId, forceUpdate) {
            data -> completion(data)
        }
    }

    fun setDebugOption(set : Boolean) {
        Logger.setDebugOption(set)
    }

    override fun onSectorData(data: SectorOutput) {
        delegate?.onSectorData(data)
    }

    override fun onSectorError() {
        delegate?.onSectorError()
    }

    override fun onBuildingsData(data: List<BuildingOutput>) {
        delegate?.onBuildingsData(data)
    }

    override fun onScaleOffsetData(scaleKey: String, data: List<Float>) {
        delegate?.onScaleOffsetData(scaleKey, data)
    }

    override fun onScaleOffsetError(scaleKey: String) {
        delegate?.onScaleOffsetError(scaleKey)

    }

    override fun onPathPixelData(pathPixelKey: String, data: PathPixelData) {
        delegate?.onPathPixelData(pathPixelKey, data)
    }

    override fun onPathPixelError(pathPixelKey: String) {
        delegate?.onPathPixelError(pathPixelKey)
    }

    override fun onGeofenceData(geofenceKey: String, data: GeofenceData) {
        delegate?.onGeofenceData(geofenceKey, data)
    }

    override fun onGeofenceError(geofenceKey: String) {
        delegate?.onGeofenceError(geofenceKey,)
    }

    override fun onEntranceData(entranceKey: String, data: EntranceData) {
        delegate?.onEntranceData(entranceKey, data)
    }

    override fun onEntranceRouteData(entranceKey: String, data: EntranceRouteData) {
        delegate?.onEntranceRouteData(entranceKey, data)
    }

    override fun onEntranceError(type: EntranceErrorType, entranceKey: String) {
        delegate?.onEntranceError(type, entranceKey)
    }

    override fun onSectorParamData(data: SectorParameterOutput) {
        delegate?.onSectorParamData(data)
    }

    override fun onLevelParamData(paramKey: String, data: LevelParameterOutput) {
        delegate?.onLevelParamData(paramKey, data)
    }

    override fun onParamError(type: ParamErrorType, paramKey: String?) {
        delegate?.onParamError(type, paramKey)
    }

    override fun onBuildingLevelImageData(imageKey: String, data: Bitmap?) {
        delegate?.onBuildingLevelImageData(imageKey, data)
    }

    override fun onBuildingLevelImageError(imageKey: String) {
        delegate?.onBuildingLevelImageError(imageKey)
    }

    override fun onUnitData(unitKey: String, data: List<UnitData>?) {
        delegate?.onUnitData(unitKey, data)
    }

    override fun onUnitDataError(unitKey: String) {
        delegate?.onUnitDataError(unitKey)
    }
}
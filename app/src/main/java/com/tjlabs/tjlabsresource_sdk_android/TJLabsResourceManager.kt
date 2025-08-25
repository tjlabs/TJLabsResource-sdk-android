package com.tjlabs.tjlabsresource_sdk_android

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import com.tjlabs.tjlabsresource_sdk_android.manager.BuildingLevelImageDelegate
import com.tjlabs.tjlabsresource_sdk_android.manager.BuildingsDelegate
import com.tjlabs.tjlabsresource_sdk_android.manager.EntranceDelegate
import com.tjlabs.tjlabsresource_sdk_android.manager.EntranceErrorType
import com.tjlabs.tjlabsresource_sdk_android.manager.GeofenceDelegate
import com.tjlabs.tjlabsresource_sdk_android.manager.ParamDelegate
import com.tjlabs.tjlabsresource_sdk_android.manager.ParamErrorType
import com.tjlabs.tjlabsresource_sdk_android.manager.PathPixelDelegate
import com.tjlabs.tjlabsresource_sdk_android.manager.ScaleOffsetDelegate
import com.tjlabs.tjlabsresource_sdk_android.manager.SectorDelegate
import com.tjlabs.tjlabsresource_sdk_android.manager.TJLabsBuildingsManager
import com.tjlabs.tjlabsresource_sdk_android.manager.TJLabsEntranceManager
import com.tjlabs.tjlabsresource_sdk_android.manager.TJLabsGeofenceManager
import com.tjlabs.tjlabsresource_sdk_android.manager.TJLabsImageManager
import com.tjlabs.tjlabsresource_sdk_android.manager.TJLabsParamManager
import com.tjlabs.tjlabsresource_sdk_android.manager.TJLabsPathPixelManager
import com.tjlabs.tjlabsresource_sdk_android.manager.TJLabsScaleOffsetManager
import com.tjlabs.tjlabsresource_sdk_android.manager.TJLabsSectorManager
import com.tjlabs.tjlabsresource_sdk_android.manager.TJLabsUnitManager
import com.tjlabs.tjlabsresource_sdk_android.manager.UnitDelegate
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
    UnitDelegate {

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
        loadSector(sectorId = sectorId) {
                sectorData ->
            if (sectorData != null) {
                buildingLevelManager.setBuildings(sectorId, sectorData.buildings)
                pathPixelManager.loadPathPixel(region, sectorId, sectorData.buildings)
                imageManager.loadImage(sectorId, sectorData.buildings)
                unitManager.loadUnit(sectorId, sectorData.buildings)
            } else {
                delegate?.onSectorError(ResourceError.Sector)
            }
        }

    }

    fun loadJupiterResource(application: Application, region: String, sectorId: Int) {
        init(application, region)
        setRegion(region)
        loadSector(sectorId = sectorId) {
            sectorData ->
            if (sectorData != null) {
                buildingLevelManager.setBuildings(sectorId, sectorData.buildings)
                scaleOffsetManager.loadScaleOffset(sectorId, sectorData.buildings)
                pathPixelManager.loadPathPixel(region, sectorId, sectorData.buildings)
                geofenceManager.loadGeofence(sectorId, sectorData.buildings)
                entranceManager.loadEntrance(region, sectorId, sectorData.buildings)
                paramManager.loadSectorParam(sectorId)
                paramManager.loadLevelParam(sectorId, sectorData.buildings)
            } else {
                delegate?.onSectorError(ResourceError.Sector)
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
        pathPixelManager.setRegion(region)
        entranceManager.setRegion(region)
    }

    private fun loadSector(sectorId: Int, forceUpdate: Boolean = false, completion: (SectorOutput?) -> Unit) {
        sectorManager.loadSector(sectorId, forceUpdate) {
            data -> completion(data)
        }
    }

    fun getMatchedLevelId(key: String): Int? {
        return TJLabsBuildingsManager.levelIdMap[key]
    }

    fun getMatchedLevelImageUrl(key: String): String? {
        return TJLabsBuildingsManager.levelImageUrlMap[key]
    }

    fun getSectorData(sectorId: Int): SectorOutput? {
        return TJLabsSectorManager.sectorDataMap[sectorId]
    }

    fun getBuildingLevelData(): Map<Int, List<BuildingOutput>> {
        return TJLabsBuildingsManager.buildingsDataMap
    }

    fun getScaleOffset(): Map<String, List<Float>> {
        return TJLabsScaleOffsetManager.scaleOffsetDataMap
    }

    fun getPathPixelData(): Map<String, PathPixelData> {
        return TJLabsPathPixelManager.ppDataMap
    }

    fun getUnitData(): Map<String, List<UnitData>> {
        return TJLabsUnitManager.unitDataMap
    }

    fun getGeofenceData(): Map<String, GeofenceData> {
        return TJLabsGeofenceManager.geofenceDataMap
    }

    fun getEntranceData(): Map<String, EntranceData> {
        return TJLabsEntranceManager.entranceDataMap
    }

    fun getEntranceRouteData(): Map<String, EntranceRouteData> {
        return TJLabsEntranceManager.entranceRouteDataMap
    }

    fun getBuildingLevelImageData(): Map<String, Bitmap> {
        return TJLabsImageManager.buildingLevelImageDataMap
    }

    fun getSectorParamData(): Map<Int, SectorParameterOutput> {
        return TJLabsParamManager.sectorParamData
    }

    fun getLevelParamData(): Map<String, LevelParameterOutput> {
        return TJLabsParamManager.levelParamData
    }

    
    // MARK: - Public Update Methods
    fun updateScaleOffsetData(key: String) {
        val levelId = getMatchedLevelId(key)
        if (levelId != null) {
            scaleOffsetManager.updateLevelScaleOffset(key, levelId)
        } else {
            delegate?.onError(ResourceError.Scale, key)
        }
    }

    fun updatePathPixelData(sectorId: Int, key: String) {
        val levelId = getMatchedLevelId(key)
        if (levelId != null) {
            pathPixelManager.updateLevelPathPixel(key, sectorId, levelId)
        } else {
            delegate?.onError(ResourceError.PathPixel, key)
        }
    }

    fun updateUnitData(key: String) {
        val levelId = getMatchedLevelId(key)
        if (levelId != null) {
            unitManager.updateLevelUnit(key, levelId)
        } else {
            delegate?.onError(ResourceError.Unit, key)
        }
    }

    fun updateGeofence(key: String) {
        val levelId = getMatchedLevelId(key)
        if (levelId != null) {
            geofenceManager.updateLevelGeofence(key, levelId)
        } else {
            delegate?.onError(ResourceError.Geofence, key)
        }
    }

    fun updateEntrance(sectorId: Int, key: String) {
        val levelId = getMatchedLevelId(key)
        if (levelId != null) {
            entranceManager.updateLevelEntrance(key, sectorId, levelId)
        } else {
            delegate?.onError(ResourceError.Entrance, key)
        }
    }

    fun updateImage(key: String) {
        val imageUrl = getMatchedLevelImageUrl(key)
        if (imageUrl != null) {
            imageManager.updateLevelImage(key, imageUrl)
        } else {
            delegate?.onError(ResourceError.Image, key)
        }
    }

    fun updateLevelParam(sectorId: Int, key: String) {
        val levelId = getMatchedLevelId(key)
        if (levelId != null) {
            paramManager.updateLevelParam(key, levelId)
        } else {
            delegate?.onError(ResourceError.Param, key)
        }
    }

    fun setDebugOption(set : Boolean) {
        Logger.setDebugOption(set)
    }

    override fun onSectorData(data: SectorOutput) {
        delegate?.onSectorData(data)
    }

    override fun onSectorError() {
        delegate?.onSectorError(ResourceError.Sector)
    }

    override fun onBuildingsData(data: List<BuildingOutput>) {
        delegate?.onBuildingsData(data)
    }

    override fun onScaleOffsetData(scaleKey: String, data: List<Float>) {
        delegate?.onScaleOffsetData(scaleKey, data)
    }

    override fun onScaleOffsetError(scaleKey: String) {
        delegate?.onError(ResourceError.Scale, scaleKey)
    }

    override fun onPathPixelData(pathPixelKey: String, data: PathPixelData) {
        delegate?.onPathPixelData(pathPixelKey, data)
    }

    override fun onPathPixelError(pathPixelKey: String) {
        delegate?.onError(ResourceError.PathPixel, pathPixelKey)
    }

    override fun onGeofenceData(geofenceKey: String, data: GeofenceData) {
        delegate?.onGeofenceData(geofenceKey, data)
    }

    override fun onGeofenceError(geofenceKey: String) {
        delegate?.onError(ResourceError.Geofence, geofenceKey)
    }

    override fun onEntranceData(entranceKey: String, data: EntranceData) {
        delegate?.onEntranceData(entranceKey, data)
    }

    override fun onEntranceRouteData(entranceKey: String, data: EntranceRouteData) {
        delegate?.onEntranceRouteData(entranceKey, data)
    }

    override fun onEntranceError(type: EntranceErrorType, entranceKey: String) {
        delegate?.onError(ResourceError.Entrance, entranceKey)
    }

    override fun onSectorParamData(data: SectorParameterOutput) {
        delegate?.onSectorParamData(data)
    }

    override fun onLevelParamData(paramKey: String, data: LevelParameterOutput) {
        delegate?.onLevelParamData(paramKey, data)
    }

    override fun onParamError(type: ParamErrorType, paramKey: String?) {
        Logger.e("onParamError // type : $type")
    }

    override fun onBuildingLevelImageData(imageKey: String, data: Bitmap?) {
        delegate?.onBuildingLevelImageData(imageKey, data)
    }

    override fun onBuildingLevelImageError(imageKey: String) {
        delegate?.onError(ResourceError.Image, imageKey)
    }

    override fun onUnitData(unitKey: String, data: List<UnitData>?) {
        delegate?.onUnitData(unitKey, data)
    }

    override fun onUnitDataError(unitKey: String) {
        delegate?.onError(ResourceError.Unit, unitKey)
    }
}
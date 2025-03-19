package com.tjlabs.tjlabsresource_sdk_android

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.util.Log

const val TAG = "TJLabsResourceManager"

class TJLabsResourceManager :
    BuildingLevelDelegate,
    PathPixelDelegate,
    BuildingLevelImageDelegate,
    ScaleOffsetDelegate,
    EntranceDelegate,
    UnitDelegate, ParamDelegate, GeofenceDelegate {

    var delegate: TJLabsResourceManagerDelegate? = null

    private var pathPixelManager = TJLabsPathPixelManager()
    private var buildingLevelManager = TJLabsBuildingLevelManager()
    private var imageManager = TJLabsImageManager()
    private var scaleOffsetManager = TJLabsScaleOffsetManager()
    private var entranceManager = TJLabsEntranceManager()
    private var unitManager = TJLabsUnitManager()
    private var paramManager = TJLabsParamManager()
    private var geofenceManager = TJLabsGeofenceManager()

    private lateinit var sharedPrefs: SharedPreferences

    init {
        pathPixelManager.delegate = this
        buildingLevelManager.delegate = this
        imageManager.delegate = this
        scaleOffsetManager.delegate = this
        entranceManager.delegate = this
        unitManager.delegate = this
        paramManager.delegate = this
        geofenceManager.delegate = this
    }

    override fun onBuildingLevelData(isOn: Boolean, buildingLevelData: Map<String, List<String>>) {
        delegate?.onBuildingLevelData(isOn, buildingLevelData)
    }

    override fun onBuildingLevelError() {
        delegate?.onError(ResourceError.BuildingLevel)
    }

    override fun onPathPixelData(
        isOn: Boolean,
        pathPixelKey: String,
        data: PathPixelData?
    ) {
        delegate?.onPathPixelData(isOn, pathPixelKey, data)
    }

    override fun onPathPixelError() {
        delegate?.onError(ResourceError.PathPixel)
    }

    override fun onBuildingLevelImageData(
        isOn: Boolean,
        imageKey: String,
        data: Bitmap?
    ) {
        delegate?.onBuildingLevelImageData(isOn, imageKey, data)
    }

    override fun onScaleOffsetData(
        isOn: Boolean,
        scaleKey: String,
        data: List<Float>
    ) {
        delegate?.onScaleOffsetData(isOn, scaleKey, data)
    }

    override fun onScaleError() {
        delegate?.onError(ResourceError.Scale)
    }

    override fun onEntranceData(
        isOn: Boolean,
        entranceKey: String,
        data: EntranceRouteData?
    ) {
        delegate?.onEntranceData(isOn, entranceKey, data)
    }

    override fun onEntranceError() {
        delegate?.onError(ResourceError.Entrance)
    }

    override fun onUnitData(isOn: Boolean, unitKey: String, data: List<UnitData>?) {
        delegate?.onUnitData(isOn, unitKey, data)
    }

    override fun onUnitDataError() {
        delegate?.onError(ResourceError.Unit)
    }


    override fun onParamData(isOn: Boolean, data: ParameterData?) {
        delegate?.onParamData(isOn, data)
    }

    override fun onParamError() {
        delegate?.onError(ResourceError.Param)
    }

    override fun onGeofenceData(isOn: Boolean, key: String, geofenceData: Map<String, Areas>) {
        delegate?.onGeofenceData(isOn, key, geofenceData)
    }

    override fun onGeofenceError() {
        delegate?.onError(ResourceError.Geo)
    }


    fun loadJupiterResources(application: Application ,region: String, sectorId: Int) {
        init(application, region)
        loadPathPixel(application, sectorId)
        loadEntrance(application, sectorId)
        loadParam(sectorId)
        loadGeo(sectorId)
    }

    fun loadMapResources(application: Application, region: String, sectorId: Int) {
        init(application, region)
        loadPathPixel(application, sectorId)
        loadImage(sectorId)
        loadScaleOffset(sectorId)
        loadUnit(sectorId)
    }


    private fun init(application: Application, region: String) {
        this.sharedPrefs = application.getSharedPreferences("TJLabsResourcesPref", Context.MODE_PRIVATE)
        setRegion(region)
        TJLabsResourceNetworkConstant.setServerURL(region)
    }

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

    fun getEntranceNumbers() : Int {
        return TJLabsEntranceManager.entranceNumbers
    }

    fun getEntranceData() : Map<String, EntranceData>
    {
        return TJLabsEntranceManager.entranceDataMap
    }

    fun getEntranceRouteData() : Map<String, EntranceRouteData>
    {
        return TJLabsEntranceManager.entranceRouteDataMap
    }

    fun getEntranceRouteDataIsLoaded() : Map<String, EntranceRouteDataIsLoaded>
    {
        return TJLabsEntranceManager.entranceRouteDataLoaded
    }

    fun getEntranceOuterWards() : List<String>
    {
        return TJLabsEntranceManager.entranceOuterWards
    }

    fun getBuildingLevelImageData() : Map<String, Bitmap>{
        return TJLabsImageManager.buildingLevelImageDataMap
    }

    fun getUnitData() : Map<String, List<UnitData>> {
        return TJLabsUnitManager.unitDataMap
    }

    fun getGeofenceData() : Map<String, Areas> {
        return TJLabsGeofenceManager.geofenceDataMap
    }

    fun updatePathPixelData(region: String, sectorId: Int, key : String, url : String) {
        TJLabsPathPixelManager.isPerformed = true
        pathPixelManager.updatePathPixel(region, sectorId, key, url) { _,_ ->}
    }

    private fun loadPathPixel(application: Application, sectorId: Int) {
        pathPixelManager.init(application, sharedPrefs)

        if (!TJLabsPathPixelManager.isPerformed) {
            TJLabsPathPixelManager.isPerformed = true
            pathPixelManager.loadPathPixel(sectorId)
            Log.d(TAG, "Load Path Pixel ... sector id : $sectorId ")
        } else {
            Log.d(TAG, "loadPathPixel already performed")
        }
    }

    private fun loadBuildingLevel(sectorId: Int, completion: (Boolean, Map<String, List<String>>) -> Unit) {
        val buildingLevelData = TJLabsBuildingLevelManager.buildingLevelDataMap[sectorId]

        if (!buildingLevelData.isNullOrEmpty()) {
            completion(true, buildingLevelData)
            return
        }

        buildingLevelManager.loadBuildingLevel(sectorId, completion)
    }

    private fun loadImage(sectorId: Int) {
        loadBuildingLevel(sectorId) {
            isSuccess, buildingLevelData ->
            if (isSuccess) imageManager.loadImage(sectorId, buildingLevelData)
            else {
                //Fail
            }
        }
    }

    private fun loadScaleOffset(sectorId: Int) {
        scaleOffsetManager.loadScaleOffset(sectorId)
    }

    private fun loadUnit(sectorId: Int) {
        unitManager.loadUnits(sectorId)
    }

    private fun loadEntrance(application: Application, sectorId: Int) {
        entranceManager.init(application, sharedPrefs)
        entranceManager.loadEntrance(sectorId)
    }

    private fun loadParam(sectorId: Int) {
        paramManager.loadParam(sectorId)
    }

    private fun loadGeo(sectorId: Int) {
        geofenceManager.loadGeofenceData(sectorId)
    }
    private fun setRegion(region : String) {
        TJLabsResourceNetworkConstant.setServerURL(region)
        TJLabsFileDownloader.region = region
        buildingLevelManager.region = region
        imageManager.region = region
        pathPixelManager.region = region
        scaleOffsetManager.region = region
        entranceManager.region = region
        unitManager.region = region
        geofenceManager.region = region
    }

}
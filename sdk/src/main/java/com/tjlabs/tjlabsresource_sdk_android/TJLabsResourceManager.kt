package com.tjlabs.tjlabsresource_sdk_android

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.tjlabs.tjlabsresource_sdk_android.manager.AffineDelegate
import com.tjlabs.tjlabsresource_sdk_android.manager.BuildingLevelImageDelegate
import com.tjlabs.tjlabsresource_sdk_android.manager.BuildingsDelegate
import com.tjlabs.tjlabsresource_sdk_android.manager.EntranceDelegate
import com.tjlabs.tjlabsresource_sdk_android.manager.EntranceErrorType
import com.tjlabs.tjlabsresource_sdk_android.manager.GeofenceDelegate
import com.tjlabs.tjlabsresource_sdk_android.manager.GraphsDelegate
import com.tjlabs.tjlabsresource_sdk_android.manager.LandmarkDelegate
import com.tjlabs.tjlabsresource_sdk_android.manager.LevelsDelegate
import com.tjlabs.tjlabsresource_sdk_android.manager.NodeLinkDelegate
import com.tjlabs.tjlabsresource_sdk_android.manager.ParamDelegate
import com.tjlabs.tjlabsresource_sdk_android.manager.ParamErrorType
import com.tjlabs.tjlabsresource_sdk_android.manager.PathPixelDelegate
import com.tjlabs.tjlabsresource_sdk_android.manager.ScaleOffsetDelegate
import com.tjlabs.tjlabsresource_sdk_android.manager.SectorDelegate
import com.tjlabs.tjlabsresource_sdk_android.manager.SpotsDelegate
import com.tjlabs.tjlabsresource_sdk_android.manager.TJLabsAffineManager
import com.tjlabs.tjlabsresource_sdk_android.manager.TJLabsBuildingsManager
import com.tjlabs.tjlabsresource_sdk_android.manager.TJLabsEntranceManager
import com.tjlabs.tjlabsresource_sdk_android.manager.TJLabsGeofenceManager
import com.tjlabs.tjlabsresource_sdk_android.manager.TJLabsGraphsManager
import com.tjlabs.tjlabsresource_sdk_android.manager.TJLabsImageManager
import com.tjlabs.tjlabsresource_sdk_android.manager.TJLabsLandmarkManager
import com.tjlabs.tjlabsresource_sdk_android.manager.TJLabsLevelsManager
import com.tjlabs.tjlabsresource_sdk_android.manager.TJLabsParamManager
import com.tjlabs.tjlabsresource_sdk_android.manager.TJLabsScaleOffsetManager
import com.tjlabs.tjlabsresource_sdk_android.manager.TJLabsSectorManager
import com.tjlabs.tjlabsresource_sdk_android.manager.TJLabsSpotsManager
import com.tjlabs.tjlabsresource_sdk_android.manager.TJLabsLevelUnitsManager
import com.tjlabs.tjlabsresource_sdk_android.manager.LevelUnitsDelegate
import com.tjlabs.tjlabsresource_sdk_android.util.TJLogger
import java.util.concurrent.CountDownLatch

class TJLabsResourceManager :
    SectorDelegate,
    BuildingsDelegate,
    LevelsDelegate,
    ScaleOffsetDelegate,
    PathPixelDelegate,
    GeofenceDelegate,
    EntranceDelegate,
    ParamDelegate,
    BuildingLevelImageDelegate,
    LevelUnitsDelegate,
    AffineDelegate,
    LandmarkDelegate,
    NodeLinkDelegate,
    SpotsDelegate,
    GraphsDelegate {

    var delegate: TJLabsResourceManagerDelegate? = null

    private val sectorManager = TJLabsSectorManager()
    private val buildingLevelManager = TJLabsBuildingsManager()
    private val levelsManager = TJLabsLevelsManager()
    private val scaleOffsetManager = TJLabsScaleOffsetManager()
    private val geofenceManager = TJLabsGeofenceManager()
    private val entranceManager = TJLabsEntranceManager()
    private val paramManager = TJLabsParamManager()
    private val imageManager = TJLabsImageManager()
    private val unitManager = TJLabsLevelUnitsManager()
    private val affineManager = TJLabsAffineManager()
    private val landmarkManager = TJLabsLandmarkManager()
    private val spotsManager = TJLabsSpotsManager()
    private val graphsManager = TJLabsGraphsManager()

    private lateinit var sharedPrefs: SharedPreferences

    init {
        sectorManager.delegate = this
        buildingLevelManager.delegate = this
        levelsManager.delegate = this
        scaleOffsetManager.delegate = this
        graphsManager.nodeLinkDelegate = this
        graphsManager.pathPixelDelegate = this
        geofenceManager.delegate = this
        entranceManager.delegate = this
        paramManager.delegate = this
        imageManager.delegate = this
        unitManager.delegate = this
        affineManager.delegate = this
        landmarkManager.delegate = this
        spotsManager.delegate = this
        graphsManager.delegate = this
    }

    fun loadMapResource(
        application: Application,
        region: String,
        sectorId: Int,
        completion: (Boolean) -> Unit
    ) {
        init(application, region)
        setRegion(region)
        TJLogger.d("loadMapResource")
        val mainHandler = Handler(Looper.getMainLooper())

        loadSector(sectorId = sectorId) { sectorData ->
            if (sectorData == null) {
                delegate?.onSectorError(ResourceError.Sector)
                TJLogger.d("onSectorError")

                completion(false)
                return@loadSector
            }

            // buildings 먼저 세팅
            buildingLevelManager.setBuildings(sectorId, sectorData.buildings)

            val lock = Any()
            var isAllSuccess = true

            val tasks = mutableListOf<(CountDownLatch) -> Unit>()


            fun finishOne(success: Boolean, latch: CountDownLatch) {
                if (!success) {
                    synchronized(lock) {
                        isAllSuccess = false
                    }
                }
                latch.countDown()
            }

            // MapResource에서 병렬로 로딩할 항목 수
            // PathPixel / Image / Unit

            // 1. PathPixel
            tasks += { latch -> graphsManager.loadPathPixel(
                region,
                sectorId,
                sectorData.buildings
            ) { isSuccess ->
                finishOne(isSuccess, latch)
                TJLogger.d("loadPathPixel : $isSuccess")
            }
            }


            // 2. Image
            tasks += { latch ->
                imageManager.loadImage(
                    sectorId,
                    sectorData.buildings
                ) { isSuccess ->
                    TJLogger.d("loadImage : $isSuccess")

                    finishOne(isSuccess, latch)
                }
            }

            tasks += { latch ->
                scaleOffsetManager.loadScaleOffset(
                    sectorId,
                    sectorData.buildings
                ) { isSuccess ->
                    TJLogger.d("loadScaleOffset : $isSuccess")
                    finishOne(isSuccess, latch)
                }
            }

            // 3. Unit
            tasks += { latch ->
                unitManager.loadLevelUnits(
                    sectorId,
                    sectorData.buildings
                ) { isSuccess ->
                    TJLogger.d("loadLevelUnits : $isSuccess")
                    finishOne(isSuccess, latch)
                }
            }

            val latch = CountDownLatch(tasks.size)

            tasks.forEach { task ->
                task(latch)
            }

            // 모든 Map 리소스 로딩 완료 대기
            Thread {
                latch.await()
                mainHandler.post {
                    completion(isAllSuccess)
                }
            }.start()
        }
    }


    fun loadJupiterResource(
        application: Application,
        region: String,
        sectorId: Int,
        completion: (Boolean) -> Unit
    ) {
        init(application, region)
        setRegion(region)

        val mainHandler = Handler(Looper.getMainLooper())

        loadSector(sectorId = sectorId) { sectorData ->
            if (sectorData == null) {
                delegate?.onSectorError(ResourceError.Sector)
                completion(false)
                return@loadSector
            }

            buildingLevelManager.setBuildings(sectorId, sectorData.buildings)

            val lock = Any()
            var isAllSuccess = true
            val tasks = mutableListOf<(CountDownLatch) -> Unit>()

            fun finishOne(success: Boolean, latch: CountDownLatch) {
                if (!success) {
                    synchronized(lock) {
                        isAllSuccess = false
                    }
                }
                latch.countDown()
            }

            // 1. ScaleOffset
            tasks += { latch ->
                scaleOffsetManager.loadScaleOffset(
                    sectorId,
                    sectorData.buildings
                ) { isSuccess ->
                    TJLogger.d("loadScaleOffset : $isSuccess")
                    finishOne(isSuccess, latch)
                }
            }

            // 2. PathPixel
            tasks += { latch ->
                graphsManager.loadPathPixel(
                    region,
                    sectorId,
                    sectorData.buildings
                ) { isSuccess ->
                    TJLogger.d("loadPathPixel : $isSuccess")

                    finishOne(isSuccess, latch)
                }
            }

            // 3. NodeLink
            tasks += { latch ->
                graphsManager.loadNodeLinks(
                    sectorId,
                    sectorData.buildings
                ) { isSuccess ->
                    TJLogger.d("loadNodeLinks : $isSuccess")
                    finishOne(isSuccess, latch)
                }
            }

            // 4. Geofence
            tasks += { latch ->
                geofenceManager.loadGeofence(
                    sectorId,
                    sectorData.buildings
                ) { isSuccess ->
                    TJLogger.d("loadGeofence : $isSuccess")

                    finishOne(isSuccess, latch)
                }
            }

            // 5. Entrance
            tasks += { latch ->
                entranceManager.loadEntrance(
                    region,
                    sectorId,
                    sectorData.buildings
                ) { isSuccess ->
                    TJLogger.d("loadEntrance : $isSuccess")

                    finishOne(isSuccess, latch)
                }
            }

            /* 미사용 api
            // 6. SectorParam
            paramManager.loadSectorParam(sectorId) { isSuccess ->
                TJLogger.d("loadSectorParam : $isSuccess")

                finishOne(isSuccess, latch)
            }

            // 7. LevelParam
            paramManager.loadLevelParam(
                sectorId,
                sectorData.buildings
            ) { isSuccess ->
                TJLogger.d("loadLevelParam : $isSuccess")

                finishOne(isSuccess, latch)
            }

             */

            // 8. LevelWards
            tasks += { latch ->
                levelsManager.loadLevelWards(
                    sectorId,
                    sectorData.buildings
                ) { isSuccess ->
                    TJLogger.d("loadLevelWards : $isSuccess")

                    finishOne(isSuccess, latch)
                }
            }

            // 9. Spots
            //tasks += { latch ->
            //    spotsManager.loadSpots(
            //        application.applicationContext,
            //        sectorId
            //    ) { isSuccess ->
            //        TJLogger.d("loadSpots : $isSuccess")
            //        finishOne(isSuccess, latch)
            //    }
            //}

            // 10. Graphs
            tasks += { latch ->
                graphsManager.loadGraphs(
                    sectorId,
                    sectorData.buildings
                ) { isSuccess ->
                    TJLogger.d("loadGraphs : $isSuccess")
                    finishOne(isSuccess, latch)
                }
            }

            // Landmark
            tasks += { latch ->
                landmarkManager.loadLandmarks(
                    sectorId,
                    sectorData.buildings
                ) { isSuccess ->
                    TJLogger.d("loadLandmarks : $isSuccess")
                    finishOne(isSuccess, latch)
                    synchronized(lock) {
                        if (!isSuccess) isAllSuccess = false
                    }
                }
            }

            // AffineParam (성공/실패가 전체 결과에 영향 없음)
            affineManager.loadAffineParam(sectorId) {
                // intentionally ignored
            }

            val latch = CountDownLatch(tasks.size)

            // 실행
            tasks.forEach { task ->
                task(latch)
            }

            Thread {
                latch.await()
                mainHandler.post {
                    completion(isAllSuccess)
                }
            }.start()
        }
    }

    private fun init(application: Application, region: String) {
        this.sharedPrefs = application.getSharedPreferences("TJLabsResourcesPref", Context.MODE_PRIVATE)
        setRegion(region)

        //cached 에 접근하기 위한
        graphsManager.init(application, sharedPrefs)
        entranceManager.init(application, sharedPrefs)
    }

    private fun setRegion(region : String) {
        TJLabsResourceNetworkConstants.setServerURL(region)
        TJLabsFileDownloader.region = region
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

    fun getLevelWardsData() : Map<String, List<String>> {
        return TJLabsLevelsManager.levelWardsDataMap
    }

    fun getScaleOffset(): Map<String, List<Float>> {
        return TJLabsScaleOffsetManager.scaleOffsetDataMap
    }

    fun getPathPixelData(): Map<String, PathPixelData> {
        return TJLabsGraphsManager.pathPixelDataMap
    }

    fun getUnitData(): Map<String, List<UnitData>> {
        return TJLabsLevelUnitsManager.levelUnitsDataMap
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

    fun getAffineParamData() : Map<Int, AffineTransParamOutput?> {
        return TJLabsAffineManager.affineParamMap
    }

    
    // MARK: - Public Update Methods
    fun updateScaleOffsetData(key: String, completion: (Boolean) -> Unit) {
        val levelId = getMatchedLevelId(key)
        if (levelId != null) {
            scaleOffsetManager.updateLevelScaleOffset(key, levelId) {
                isSuccess -> completion(isSuccess)
            }
        } else {
            delegate?.onError(ResourceError.Scale, key)
            completion(false)
        }
    }

    fun updatePathPixelData(sectorId: Int, key: String, completion: (Boolean) -> Unit) {
        val levelId = getMatchedLevelId(key)
        if (levelId != null) {
            graphsManager.updateLevelPathPixel(key, sectorId, levelId){
                    isSuccess -> completion(isSuccess)
            }
        } else {
            delegate?.onError(ResourceError.PathPixel, key)
            completion(false)
        }
    }

    fun updateUnitData(key: String, completion: (Boolean) -> Unit) {
        val levelId = getMatchedLevelId(key)
        if (levelId != null) {
            unitManager.updateLevelUnit(key, levelId){
                    isSuccess -> completion(isSuccess)
            }
        } else {
            delegate?.onError(ResourceError.LevelUnits, key)
            completion(false)
        }
    }

    fun updateGeofence(key: String, completion: (Boolean) -> Unit) {
        val levelId = getMatchedLevelId(key)
        if (levelId != null) {
            geofenceManager.updateLevelGeofence(key, levelId){
                    isSuccess -> completion(isSuccess)
            }
        } else {
            delegate?.onError(ResourceError.Geofence, key)
            completion(false)
        }
    }

    fun updateEntrance(sectorId: Int, key: String, completion: (Boolean) -> Unit) {
        val levelId = getMatchedLevelId(key)
        if (levelId != null) {
            entranceManager.updateLevelEntrance(key, sectorId, levelId){
                    isSuccess -> completion(isSuccess)
            }
        } else {
            delegate?.onError(ResourceError.Entrance, key)
            completion(false)
        }
    }

    fun updateImage(key: String, completion: (Boolean) -> Unit) {
        val imageUrl = getMatchedLevelImageUrl(key)
        if (imageUrl != null) {
            imageManager.updateLevelImage(key, imageUrl){
                    isSuccess -> completion(isSuccess)
            }
        } else {
            delegate?.onError(ResourceError.Image, key)
        }
    }

    fun updateLevelParam(sectorId: Int, key: String, completion: (Boolean) -> Unit) {
        val levelId = getMatchedLevelId(key)
        if (levelId != null) {
            paramManager.updateLevelParam(key, levelId){
                    isSuccess -> completion(isSuccess)
            }
        } else {
            delegate?.onError(ResourceError.Param, key)
            completion(false)
        }
    }

    fun updateAffineParam(sectorId: Int, completion: (Boolean) -> Unit) {
        affineManager.updateAffineParam(sectorId){
                isSuccess -> completion(isSuccess)
        }
    }

    fun setDebugOption(set : Boolean) {
        TJLogger.setDebugOption(set)
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
        TJLogger.e("onParamError // type : $type")
    }

    override fun onBuildingLevelImageData(imageKey: String, data: Bitmap?) {
        delegate?.onBuildingLevelImageData(imageKey, data)
    }

    override fun onBuildingLevelImageError(imageKey: String) {
        delegate?.onError(ResourceError.Image, imageKey)
    }

    override fun onLevelUnitsData(unitKey: String, data: List<UnitData>?) {
        delegate?.onLevelUnitsData(unitKey, data)
    }

    override fun onLevelUnitsDataError(unitKey: String) {
        delegate?.onError(ResourceError.LevelUnits, unitKey)
    }

    override fun onLevelWardsData(levelKey: String, data : List<String>) {
        delegate?.onLevelWardsData(levelKey, data)
    }

    override fun onLevelWardsDataError(unitKey: String) {
        delegate?.onError(ResourceError.LevelWards, unitKey)
    }

    override fun onAffineData(sectorId: Int, data: AffineTransParamOutput) {
        delegate?.onAffineData(sectorId, data)
    }

    override fun onAffineError(sectorId: Int) {
        delegate?.onError(ResourceError.Affine, sectorId.toString())
    }

    override fun onLandmarkData(landmarkKey: String, data: Map<String, LandmarkData>) {
        delegate?.onLandmarkData(landmarkKey, data)
    }

    override fun onLandmarkError(landmarkKey: String) {
        delegate?.onError(ResourceError.Landmark,landmarkKey)
    }

    override fun onNodeLinkData(nodeLinkKey: String, type: NodeLinkType, data: Any) {
        delegate?.onNodeLinkData(nodeLinkKey, type, data)
    }

    override fun onNodeLinkError(nodeLinkKey: String, type: NodeLinkType) {
        delegate?.onError(ResourceError.Node, type.toString())
    }

    override fun onSpotsData(spotsKey: Int, type: SpotType, data: Any) {
        delegate?.onSpotsData(spotsKey, type, data)
    }

    override fun onSpotsError(spotsKey: Int, type: SpotType) {
        delegate?.onError(ResourceError.Spots, spotsKey.toString())
    }

    override fun onGraphNodesData(key: String, data: List<GraphLevelNode>) {
        // intentionally no-op (graphs are internal)
    }

    override fun onGraphLinksData(key: String, data: List<GraphLevelLink>) {
        // intentionally no-op (graphs are internal)
    }

    override fun onGraphLinkGroupsData(key: String, data: List<GraphLevelLinkGroup>) {
        // intentionally no-op (graphs are internal)
    }

    override fun onGraphLinkFeatureData(key: String, data: List<GraphLevelLinkFeature>) {

    }

    override fun onGraphPathsData(key: String, data: List<GraphLevelPath>) {
        // intentionally no-op (graphs are internal)
    }

    override fun onGraphError(key: String, type: GraphResourceType) {
        // intentionally no-op (graphs are internal)
    }
}

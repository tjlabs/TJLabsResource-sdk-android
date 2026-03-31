package com.tjlabs.tjlabsresource_sdk_android

import android.app.Application
import android.graphics.Bitmap
import com.tjlabs.tjlabsresource_sdk_android.manager.BundleDataSnapshot
import com.tjlabs.tjlabsresource_sdk_android.manager.TJLabsBundleDataManager
import com.tjlabs.tjlabsresource_sdk_android.util.TJLogger

class TJLabsResourceManager {
    var delegate: TJLabsResourceManagerDelegate? = null

    companion object {
        private val sectorDataMap: MutableMap<Int, SectorOutput> = mutableMapOf()
        private val buildingsDataMap: MutableMap<Int, List<BuildingOutput>> = mutableMapOf()
        private val levelIdMap: MutableMap<String, Int> = mutableMapOf()
        private val levelImageUrlMap: MutableMap<String, String> = mutableMapOf()

        private val levelWardsDataMap: MutableMap<String, List<String>> = mutableMapOf()
        private val scaleOffsetDataMap: MutableMap<String, List<Float>> = mutableMapOf()
        private val pathPixelDataMap: MutableMap<String, PathPixelData> = mutableMapOf()
        private val geofenceDataMap: MutableMap<String, GeofenceData> = mutableMapOf()
        private val entranceDataMap: MutableMap<String, EntranceData> = mutableMapOf()
        private val entranceItemDataMap: MutableMap<String, EntranceData> = mutableMapOf()
        private val entranceRouteDataMap: MutableMap<String, EntranceRouteData> = mutableMapOf()
        private val levelUnitsDataMap: MutableMap<String, List<UnitData>> = mutableMapOf()
        private val landmarkDataMap: MutableMap<String, Map<String, LandmarkData>> = mutableMapOf()
        private val nodeDataMap: MutableMap<String, Map<Int, NodeData>> = mutableMapOf()
        private val linkDataMap: MutableMap<String, Map<Int, LinkData>> = mutableMapOf()
        private val affineParamMap: MutableMap<Int, AffineTransParamOutput?> = mutableMapOf()

        private val imageDataMap: MutableMap<String, Bitmap> = mutableMapOf()
        private val sectorParamData: MutableMap<Int, SectorParameterOutput> = mutableMapOf()
        private val levelParamData: MutableMap<String, LevelParameterOutput> = mutableMapOf()
    }

    private val bundleDataManager = TJLabsBundleDataManager()

    fun loadResource(
        application: Application,
        region: String,
        sectorId: Int,
        completion: (Boolean) -> Unit
    ) {
        TJLogger.d("(TJLabsResource) loadResource request // region=$region // sectorId=$sectorId")
        setRegion(region)

        bundleDataManager.loadBundle(application, sectorId) { isSuccess, message, snapshot ->
            TJLogger.d("(TJLabsResource) loadResource callback // success=$isSuccess // message=$message")
            if (!isSuccess || snapshot == null) {
                TJLogger.d("(TJLabsResource) loadResource failed // sectorId=$sectorId // snapshotNull=${snapshot == null}")
                delegate?.onSectorError(ResourceError.Sector)
                completion(false)
                return@loadBundle
            }

            cacheSnapshot(sectorId, snapshot)
            emitSnapshot(snapshot)
            TJLogger.d("(TJLabsResource) loadResource success // sectorId=$sectorId // versionId=${snapshot.versionId}")
            completion(true)
        }
    }

    fun testLoadSectorBundle(
        application: Application,
        region: String,
        sectorId: Int,
        completion: (Boolean, String, String?, String?, SectorOutput?) -> Unit
    ) {
        setRegion(region)
        bundleDataManager.testLoadBundle(sectorId) { success, msg, meta, raw, mappedSector ->
            completion(success, msg, meta?.version_id, meta?.url, mappedSector)
        }
    }

    private fun setRegion(region: String) {
        TJLabsResourceNetworkConstants.setServerURL(region)
        TJLabsFileDownloader.region = region
    }

    private fun cacheSnapshot(sectorId: Int, snapshot: BundleDataSnapshot) {
        clearDebugLevelCache(sectorId)

        sectorDataMap[sectorId] = snapshot.sectorData
        buildingsDataMap[sectorId] = snapshot.sectorData.buildings

        for (building in snapshot.sectorData.buildings) {
            for (level in building.levels) {
                val key = "${sectorId}_${building.name}_${level.name}"
                levelIdMap[key] = level.id
                if (level.name.contains("_D").not()) {
                    levelImageUrlMap[key] = level.image
                }
            }
        }

        levelWardsDataMap.putAll(snapshot.levelWardsDataMap.filterKeys { it.contains("_D").not() })
        scaleOffsetDataMap.putAll(snapshot.scaleOffsetDataMap)
        pathPixelDataMap.putAll(snapshot.pathPixelDataMap)
        geofenceDataMap.putAll(snapshot.geofenceDataMap)
        entranceDataMap.putAll(snapshot.entranceDataMap)
        entranceItemDataMap.putAll(snapshot.entranceItemDataMap)
        entranceRouteDataMap.putAll(snapshot.entranceRouteDataMap)
        levelUnitsDataMap.putAll(snapshot.levelUnitsDataMap)
        landmarkDataMap.putAll(snapshot.landmarkDataMap.filterKeys { it.contains("_D").not() })
        nodeDataMap.putAll(snapshot.nodeDataMap.filterKeys { it.contains("_D").not() })
        linkDataMap.putAll(snapshot.linkDataMap.filterKeys { it.contains("_D").not() })
        imageDataMap.putAll(snapshot.imageDataMap.filterKeys { it.contains("_D").not() })
        affineParamMap[sectorId] = snapshot.affineParam
    }

    private fun clearDebugLevelCache(sectorId: Int) {
        val prefix = "${sectorId}_"
        levelImageUrlMap.keys.removeAll { it.startsWith(prefix) && it.contains("_D") }
        levelWardsDataMap.keys.removeAll { it.startsWith(prefix) && it.contains("_D") }
        landmarkDataMap.keys.removeAll { it.startsWith(prefix) && it.contains("_D") }
        nodeDataMap.keys.removeAll { it.startsWith(prefix) && it.contains("_D") }
        linkDataMap.keys.removeAll { it.startsWith(prefix) && it.contains("_D") }
        imageDataMap.keys.removeAll { it.startsWith(prefix) && it.contains("_D") }
    }

    private fun emitSnapshot(snapshot: BundleDataSnapshot) {
        delegate?.onSectorData(snapshot.sectorData)
        delegate?.onBuildingsData(snapshot.sectorData.buildings)

        snapshot.levelWardsDataMap.filterKeys { it.contains("_D").not() }.forEach { (key, value) ->
            delegate?.onLevelWardsData(key, value)
        }

        snapshot.scaleOffsetDataMap.forEach { (key, value) ->
            delegate?.onScaleOffsetData(key, value)
        }

        snapshot.pathPixelDataMap.forEach { (key, value) ->
            delegate?.onPathPixelData(key, value)
        }

        snapshot.geofenceDataMap.forEach { (key, value) ->
            delegate?.onGeofenceData(key, value)
        }

        snapshot.entranceItemDataMap.forEach { (key, value) ->
            delegate?.onEntranceData(key, value)
        }

        snapshot.entranceRouteDataMap.forEach { (key, value) ->
            delegate?.onEntranceRouteData(key, value)
        }

        snapshot.levelUnitsDataMap.forEach { (key, value) ->
            delegate?.onLevelUnitsData(key, value)
        }

        snapshot.landmarkDataMap.filterKeys { it.contains("_D").not() }.forEach { (key, value) ->
            delegate?.onLandmarkData(key, value)
        }

        snapshot.nodeDataMap.filterKeys { it.contains("_D").not() }.forEach { (key, value) ->
            delegate?.onNodeLinkData(key, NodeLinkType.NODE, value)
        }

        snapshot.linkDataMap.filterKeys { it.contains("_D").not() }.forEach { (key, value) ->
            delegate?.onNodeLinkData(key, NodeLinkType.LINK, value)
        }

        snapshot.imageUrlsByKey.filterKeys { it.contains("_D").not() }.forEach { (key, _) ->
            delegate?.onBuildingLevelImageData(key, snapshot.imageDataMap[key])
        }

        val sectorId = snapshot.sectorData.id
        val affine = snapshot.affineParam
        if (affine != null) {
            delegate?.onAffineData(sectorId, affine)
        }
    }

    fun setDebugOption(set: Boolean) {
        TJLogger.setDebugOption(set)
    }

    fun getMatchedLevelId(key: String): Int? = levelIdMap[key]

    fun getMatchedLevelImageUrl(key: String): String? = levelImageUrlMap[key]

    fun getSectorData(sectorId: Int): SectorOutput? = sectorDataMap[sectorId]

    fun getBuildingLevelData(): Map<Int, List<BuildingOutput>> = buildingsDataMap

    fun getLevelWardsData(): Map<String, List<String>> = levelWardsDataMap

    fun getScaleOffset(): Map<String, List<Float>> = scaleOffsetDataMap

    fun getPathPixelData(): Map<String, PathPixelData> = pathPixelDataMap

    fun getUnitData(): Map<String, List<UnitData>> = levelUnitsDataMap

    fun getGeofenceData(): Map<String, GeofenceData> = geofenceDataMap

    fun getEntranceData(): Map<String, EntranceData> = entranceDataMap

    fun getEntranceRouteData(): Map<String, EntranceRouteData> = entranceRouteDataMap

    fun getBuildingLevelImageData(): Map<String, Bitmap> = imageDataMap

    fun getSectorParamData(): Map<Int, SectorParameterOutput> = sectorParamData

    fun getLevelParamData(): Map<String, LevelParameterOutput> = levelParamData

    fun getAffineParamData(): Map<Int, AffineTransParamOutput?> = affineParamMap

    fun updateScaleOffsetData(key: String, completion: (Boolean) -> Unit) {
        val cached = scaleOffsetDataMap[key]
        if (cached != null) {
            delegate?.onScaleOffsetData(key, cached)
            completion(true)
        } else {
            delegate?.onError(ResourceError.Scale, key)
            completion(false)
        }
    }

    fun updatePathPixelData(sectorId: Int, key: String, completion: (Boolean) -> Unit) {
        val cached = pathPixelDataMap[key]
        if (cached != null) {
            delegate?.onPathPixelData(key, cached)
            completion(true)
        } else {
            delegate?.onError(ResourceError.PathPixel, key)
            completion(false)
        }
    }

    fun updateUnitData(key: String, completion: (Boolean) -> Unit) {
        val cached = levelUnitsDataMap[key]
        if (cached != null) {
            delegate?.onLevelUnitsData(key, cached)
            completion(true)
        } else {
            delegate?.onError(ResourceError.LevelUnits, key)
            completion(false)
        }
    }

    fun updateGeofence(key: String, completion: (Boolean) -> Unit) {
        val cached = geofenceDataMap[key]
        if (cached != null) {
            delegate?.onGeofenceData(key, cached)
            completion(true)
        } else {
            delegate?.onError(ResourceError.Geofence, key)
            completion(false)
        }
    }

    fun updateEntrance(sectorId: Int, key: String, completion: (Boolean) -> Unit) {
        val matchedEntrances = entranceItemDataMap.filterKeys { it.startsWith("${key}_") }
        if (matchedEntrances.isEmpty()) {
            delegate?.onError(ResourceError.Entrance, key)
            completion(false)
            return
        }

        matchedEntrances.forEach { (entKey, data) ->
            delegate?.onEntranceData(entKey, data)
            entranceRouteDataMap[entKey]?.let { route ->
                delegate?.onEntranceRouteData(entKey, route)
            }
        }
        completion(true)
    }

    fun updateImage(key: String, completion: (Boolean) -> Unit) {
        val cached = imageDataMap[key]
        if (cached != null) {
            delegate?.onBuildingLevelImageData(key, cached)
            completion(true)
        } else {
            delegate?.onError(ResourceError.Image, key)
            completion(false)
        }
    }

    fun updateLevelParam(key: String, completion: (Boolean) -> Unit) {
        val cached = levelParamData[key]
        if (cached != null) {
            delegate?.onLevelParamData(key, cached)
            completion(true)
        } else {
            delegate?.onError(ResourceError.Param, key)
            completion(false)
        }
    }

    fun updateAffineParam(sectorId: Int, completion: (Boolean) -> Unit) {
        val cached = affineParamMap[sectorId]
        if (cached != null) {
            delegate?.onAffineData(sectorId, cached)
            completion(true)
        } else {
            delegate?.onError(ResourceError.Affine, sectorId.toString())
            completion(false)
        }
    }
}

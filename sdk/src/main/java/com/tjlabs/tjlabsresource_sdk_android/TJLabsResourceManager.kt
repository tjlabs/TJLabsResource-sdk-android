package com.tjlabs.tjlabsresource_sdk_android

import android.app.Application
import android.graphics.Bitmap
import com.tjlabs.tjlabsresource_sdk_android.manager.BundleDataSnapshot
import com.tjlabs.tjlabsresource_sdk_android.manager.TJLabsBundleDataManager
import com.tjlabs.tjlabsresource_sdk_android.util.TJResourceLogger

class TJLabsResourceManager {
    var delegate: TJLabsResourceManagerDelegate? = null
    var warpDelegate: TJLabsWarpResourceManagerDelegate? = null
    var venusDelegate: TJLabsVenusResourceManagerDelegate? = null
    var simulationDelegate: TJLabsSimulationResourceManagerDelegate? = null

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
        private val simulationDataMap: MutableMap<Int, SimulationBundleOutput> = mutableMapOf()
        // "${sectorId}_${bldg}_${전이층이름}" → TransitionOutput. 다른 level-scope 콜백과
        // 동일한 key 형식. 소비자는 이 key 로 onGeofenceData/onNodeLinkData 등과 상관관계 잡음.
        private val transitionsByKey: MutableMap<String, TransitionOutput> = mutableMapOf()
        // sectorId → transitions[] : 섹터 전체 조회용 aggregate 인덱스.
        private val transitionsBySector: MutableMap<Int, List<TransitionOutput>> = mutableMapOf()
        // levelKey ("${sectorId}_${bldg}_${level}") → level.type ("floor"|"transition")
        // 콜백 emit 시 level 종류를 O(1) 로 판정하기 위한 인덱스.
        private val levelTypeMap: MutableMap<String, String> = mutableMapOf()

        private val imageDataMap: MutableMap<String, Bitmap> = mutableMapOf()
        private val sectorParamData: MutableMap<Int, SectorParameterOutput> = mutableMapOf()
        private val levelParamData: MutableMap<String, LevelParameterOutput> = mutableMapOf()
    }

    private val bundleDataManager = TJLabsBundleDataManager()

    /**
     * loadBundle 콜백 source 문자열을 [ResourceLoadInfo.fromCache] 로 변환.
     * source 에 "network_raw" 가 포함되어 있으면 raw bundle 을 새로 다운로드한 것이므로 fromCache=false.
     * (meta 만 네트워크 fetch 하고 raw 는 재사용한 경우도 raw 기준으로 fromCache=true 로 취급.)
     */
    private fun isFromCache(source: String): Boolean = !source.contains("network_raw")

    private fun loadResourceByType(
        bundleType: ResourceBundleType,
        application: Application,
        provider: String,
        region: String,
        sectorId: Int,
        env: ResourceServerEnv = ResourceServerEnv.PROD,
        completion: (Boolean, ResourceLoadInfo?) -> Unit,
    ) {
        setRegion(provider, region, env)
        bundleDataManager.loadBundle(application, bundleType, sectorId) { isSuccess, message, snapshot, source ->
            TJResourceLogger.d("(TJLabsResource) loadResourceByType callback // type=$bundleType // success=$isSuccess // message=$message // source=$source")
            if (!isSuccess || snapshot == null) {
                when (bundleType) {
                    ResourceBundleType.JUPITER -> delegate?.onSectorError(ResourceError.Sector)
                    ResourceBundleType.WARP -> warpDelegate?.onWarpError(ResourceError.Sector)
                    ResourceBundleType.VENUS -> venusDelegate?.onVenusError(ResourceError.Sector)
                }
                completion(false, null)
                return@loadBundle
            }

            cacheSnapshot(sectorId, snapshot)
            when (bundleType) {
                ResourceBundleType.JUPITER -> emitSnapshot(snapshot)
                ResourceBundleType.WARP -> emitWarpSnapshot(snapshot)
                ResourceBundleType.VENUS -> emitVenusSnapshot(snapshot)
            }
            completion(true, ResourceLoadInfo(versionId = snapshot.versionId, fromCache = isFromCache(source)))
        }
    }

    fun loadJupiterResource(
        application: Application,
        provider: String,
        region: String,
        sectorId: Int,
        env: ResourceServerEnv = ResourceServerEnv.PROD,
        completion: (Boolean, ResourceLoadInfo?) -> Unit,
    ) {
        TJResourceLogger.d("(TJLabsResource) loadJupiterResource request // provider=$provider // region=$region // sectorId=$sectorId // env=$env")
        loadResourceByType(ResourceBundleType.JUPITER, application, provider, region, sectorId, env, completion)
    }

    fun loadVenusResource(
        application: Application,
        provider: String,
        region: String,
        sectorId: Int,
        env: ResourceServerEnv = ResourceServerEnv.PROD,
        completion: (Boolean, ResourceLoadInfo?) -> Unit,
    ) {
        TJResourceLogger.d("(TJLabsResource) loadVenusResource request // provider=$provider // region=$region // sectorId=$sectorId // env=$env")
        loadResourceByType(ResourceBundleType.VENUS, application, provider, region, sectorId, env, completion)
    }

    /**
     * WARP 는 AWS 기반 별도 인프라라 env 스위칭 대상 아님 → env 파라미터 없음.
     */
    fun loadWarpResource(
        application: Application,
        provider: String,
        region: String,
        sectorId: Int,
        completion: (Boolean, ResourceLoadInfo?) -> Unit
    ) {
        TJResourceLogger.d("(TJLabsResource) loadWarpResource request // provider=$provider // region=$region // sectorId=$sectorId")
        loadResourceByType(ResourceBundleType.WARP, application, provider, region, sectorId, ResourceServerEnv.PROD, completion)
    }

    fun loadResource(
        application: Application,
        provider: String,
        region: String,
        sectorId: Int,
        env: ResourceServerEnv = ResourceServerEnv.PROD,
        completion: (Boolean, ResourceLoadInfo?) -> Unit,
    ) {
        loadJupiterResource(application, provider, region, sectorId, env, completion)
    }

    private fun setRegion(provider: String, region: String, env: ResourceServerEnv = ResourceServerEnv.PROD) {
        TJLabsResourceNetworkConstants.setServerURL(provider, region, env)
        TJLabsFileDownloader.provider = provider
        TJLabsFileDownloader.region = region
    }

    private fun cacheSnapshot(sectorId: Int, snapshot: BundleDataSnapshot) {
        clearDebugLevelCache(sectorId)

        sectorDataMap[sectorId] = snapshot.sectorData
        buildingsDataMap[sectorId] = snapshot.sectorData.buildings

        // transitions 인덱싱: sector-aggregate + per-key ("${sectorId}_${bldg}_${전이층이름}")
        transitionsBySector[sectorId] = snapshot.transitions
        val buildingNameById = snapshot.sectorData.buildings.associate { it.id to it.name }
        for (t in snapshot.transitions) {
            val bldgName = buildingNameById[t.level.building_id] ?: continue
            val key = "${sectorId}_${bldgName}_${t.level.name}"
            transitionsByKey[key] = t
        }

        for (building in snapshot.sectorData.buildings) {
            for (level in building.levels) {
                val key = "${sectorId}_${building.name}_${level.name}"
                levelIdMap[key] = level.id
                levelTypeMap[key] = level.type
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
            delegate?.onPathPixelData(key, resolveLevelType(key), value)
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

        val loadedSectorId = snapshot.sectorData.id
        val affine = snapshot.affineParam
        if (affine != null) {
            delegate?.onAffineData(loadedSectorId, affine)
        }

        // 층이동 구간 per-key emit. key 형식은 다른 level-scope 콜백들과 동일.
        // 구버전 응답에는 transitions 가 없어 발동 자체가 없음.
        val buildingNameById = snapshot.sectorData.buildings.associate { it.id to it.name }
        for (t in snapshot.transitions) {
            val bldgName = buildingNameById[t.level.building_id] ?: continue
            val key = "${loadedSectorId}_${bldgName}_${t.level.name}"
            delegate?.onTransitionData(key, t)
        }
    }

    private fun emitWarpSnapshot(snapshot: BundleDataSnapshot) {
        val data = snapshot.warpSectorData
        if (data != null) {
            warpDelegate?.onWarpSectorData(data)
        } else {
            warpDelegate?.onWarpError(ResourceError.Sector)
        }
    }

    private fun emitVenusSnapshot(snapshot: BundleDataSnapshot) {
        val data = snapshot.venusSectorData
        if (data != null) {
            venusDelegate?.onVenusSectorData(data)
        } else {
            venusDelegate?.onVenusError(ResourceError.Sector)
        }
    }

    fun setDebugOption(set: Boolean) {
        TJResourceLogger.setDebugOption(set)
    }

    /**
     * 메모리 + 디스크 캐시 전부 비움. sectorId = null 이면 전체.
     * cold-start 측정/디버깅 용도.
     */
    fun clearCache(application: Application, sectorId: Int? = null) {
        if (sectorId == null) {
            sectorDataMap.clear()
            buildingsDataMap.clear()
            levelIdMap.clear()
            levelImageUrlMap.clear()
            levelWardsDataMap.clear()
            scaleOffsetDataMap.clear()
            pathPixelDataMap.clear()
            geofenceDataMap.clear()
            entranceDataMap.clear()
            entranceItemDataMap.clear()
            entranceRouteDataMap.clear()
            levelUnitsDataMap.clear()
            landmarkDataMap.clear()
            nodeDataMap.clear()
            linkDataMap.clear()
            affineParamMap.clear()
            simulationDataMap.clear()
            imageDataMap.clear()
            sectorParamData.clear()
            levelParamData.clear()
            transitionsByKey.clear()
            transitionsBySector.clear()
            levelTypeMap.clear()
        } else {
            sectorDataMap.remove(sectorId)
            buildingsDataMap.remove(sectorId)
            affineParamMap.remove(sectorId)
            simulationDataMap.remove(sectorId)
            sectorParamData.remove(sectorId)
            transitionsBySector.remove(sectorId)
            val prefix = "${sectorId}_"
            transitionsByKey.keys.removeAll { it.startsWith(prefix) }
            levelTypeMap.keys.removeAll { it.startsWith(prefix) }
            levelIdMap.keys.removeAll { it.startsWith(prefix) }
            levelImageUrlMap.keys.removeAll { it.startsWith(prefix) }
            levelWardsDataMap.keys.removeAll { it.startsWith(prefix) }
            scaleOffsetDataMap.keys.removeAll { it.startsWith(prefix) }
            pathPixelDataMap.keys.removeAll { it.startsWith(prefix) }
            geofenceDataMap.keys.removeAll { it.startsWith(prefix) }
            entranceDataMap.keys.removeAll { it.startsWith(prefix) }
            entranceItemDataMap.keys.removeAll { it.startsWith(prefix) }
            entranceRouteDataMap.keys.removeAll { it.startsWith(prefix) }
            levelUnitsDataMap.keys.removeAll { it.startsWith(prefix) }
            landmarkDataMap.keys.removeAll { it.startsWith(prefix) }
            nodeDataMap.keys.removeAll { it.startsWith(prefix) }
            linkDataMap.keys.removeAll { it.startsWith(prefix) }
            imageDataMap.keys.removeAll { it.startsWith(prefix) }
            levelParamData.keys.removeAll { it.startsWith(prefix) }
        }
        bundleDataManager.clearCache(application, sectorId)
        TJResourceLogger.d { "(TJLabsResource) TJLabsResourceManager.clearCache done // sectorId=${sectorId ?: "ALL"}" }
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

    fun getSimulationData(sectorId: Int): SimulationBundleOutput? = simulationDataMap[sectorId]

    /**
     * 섹터의 전체 층이동 구간 목록. 서버 스키마 2026-08-06+ 응답에서만 채워지고,
     * 구버전에서는 항상 emptyList. 로드된 적 없으면 null.
     */
    fun getTransitions(sectorId: Int): List<TransitionOutput>? = transitionsBySector[sectorId]

    /**
     * 전이층 key ("${sectorId}_${bldg}_${전이층이름}") 로 단일 층이동 구간 조회.
     * 다른 level-scope 콜백에서 받은 key 를 그대로 넣으면 됨.
     */
    fun getTransition(transitionKey: String): TransitionOutput? = transitionsByKey[transitionKey]

    /**
     * 전체 층이동 구간을 key 기반으로 조회. key 형식은 [getTransition] 참고.
     */
    fun getTransitionsByKey(): Map<String, TransitionOutput> = transitionsByKey

    /**
     * levelKey ("${sectorId}_${bldg}_${level}") 로부터 level.type ("floor" | "transition") 을
     * 조회. path pixel 키의 "_PDR" 접미사는 자동으로 제거해 base key 로 조회한다.
     *
     * 매칭이 없으면 (아직 sector 로드 전이거나 알 수 없는 key) "floor" 를 반환 — 구버전
     * 스키마에서도 안전하게 기존 흐름과 동일하게 동작하도록.
     */
    fun resolveLevelType(key: String): String {
        val baseKey = if (key.endsWith("_PDR")) key.removeSuffix("_PDR") else key
        return levelTypeMap[baseKey] ?: "floor"
    }

    fun getLevelType(key: String): String? = levelTypeMap[key]

    /**
     * BC-preserving 5-arg overload. env 를 명시하지 않으면 마지막으로 세팅된 env
     * ([TJLabsResourceNetworkConstants.getCurrentEnv]) 를 사용한다. Jupiter SDK 2.0.24
     * 이하 등 기존 caller 가 env 를 넘기지 않고 이 오버로드를 호출해도, 이전에 확정된
     * env (예: loadJupiterResource 로 세팅된 DEV) 가 유지된다.
     */
    fun loadSimulationData(
        application: Application,
        provider: String,
        region: String,
        sectorId: Int,
        completion: (Boolean) -> Unit
    ) {
        loadSimulationData(
            application,
            provider,
            region,
            sectorId,
            TJLabsResourceNetworkConstants.getCurrentEnv(),
            completion
        )
    }

    /**
     * env 를 명시적으로 지정하는 오버로드. 신규 caller 는 이 시그니처를 사용해 URL 이
     * 항상 의도된 env 로 세팅되도록 해야 한다.
     */
    fun loadSimulationData(
        application: Application,
        provider: String,
        region: String,
        sectorId: Int,
        env: ResourceServerEnv,
        completion: (Boolean) -> Unit
    ) {
        TJResourceLogger.d(
            "(TJLabsResource) loadSimulationData request // provider=$provider // region=$region // sectorId=$sectorId // env=$env"
        )
        setRegion(provider, region, env)
        bundleDataManager.loadSimulationData(application, sectorId) { isSuccess, message, simulationData ->
            TJResourceLogger.d(
                "(TJLabsResource) loadSimulationData callback // success=$isSuccess // message=$message // sectorId=$sectorId"
            )
            if (isSuccess && simulationData != null) {
                simulationDataMap[sectorId] = simulationData
                simulationDelegate?.onSimulationData(sectorId, simulationData)
                completion(true)
            } else {
                completion(false)
            }
        }
    }

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
            delegate?.onPathPixelData(key, resolveLevelType(key), cached)
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

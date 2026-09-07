package com.tjlabs.tjlabsresource_sdk_android

import android.app.Application
import android.graphics.Bitmap
import com.tjlabs.tjlabsresource_sdk_android.manager.BundleDataSnapshot
import com.tjlabs.tjlabsresource_sdk_android.manager.TJLabsBundleDataManager
import com.tjlabs.tjlabsresource_sdk_android.onprem.OnPremRoutingState
import com.tjlabs.tjlabsresource_sdk_android.util.TJResourceLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TJLabsResourceManager {
    var delegate: TJLabsResourceManagerDelegate? = null
    var warpDelegate: TJLabsWarpResourceManagerDelegate? = null
    var venusDelegate: TJLabsVenusResourceManagerDelegate? = null
    var simulationDelegate: TJLabsSimulationResourceManagerDelegate? = null

    companion object {
        /**
         * On-prem PMS 서버로 번들 로드를 라우팅한다.
         *
         * resource-sdk 는 auth 를 소유하지 않는다 — 요청 시점마다 [tokenProvider] 를 호출해
         * 최신 JWT 를 얻어 `Authorization: Bearer` 헤더에 실을 뿐이다. 토큰 캐시·갱신·만료
         * 처리는 상위 계층 (jupiter-sdk 의 on-prem auth 클라이언트) 이 담당한다.
         *
         * cloud (기존 provider/region 조립) 와 상호 배타. 이 함수를 호출한 시점 이후의
         * loadWarpResource / loadVenusResource 는 on-prem endpoint (`/v2/warp`, `/v2/venus`) 로
         * 라우팅되고, [clearOnPremConfig] 로 해제하기 전까지 cloud 로 돌아가지 않는다.
         *
         * @param baseUrl scheme + host + port (예: `http://192.168.120.75:5050`)
         * @param tokenProvider 요청 시점마다 호출되는 토큰 조회 콜백. 토큰이 아직 없거나
         *   만료됐으면 `null` / 빈 문자열 반환 허용 (그 경우 요청은 401 로 실패).
         */
        @JvmStatic
        fun setOnPremConfig(baseUrl: String, tokenProvider: () -> String?) {
            OnPremRoutingState.enable(baseUrl, tokenProvider)
        }

        /** On-prem 라우팅을 해제하고 cloud 모드로 되돌린다. */
        @JvmStatic
        fun clearOnPremConfig() {
            OnPremRoutingState.disable()
        }

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
        // 2026-08-28 스키마 — level id → 그 층의 parking matches 파일 파싱 결과.
        // 파일이 업로드된 층만 채워진다. 파일 없는 층은 map 에 key 자체가 없음.
        // ParkingMatchesData 는 matches (id ↔ matchingId) 와 level_match (사용자 표기, 예: "3") 를 함께 담는다.
        private val parkingMatchesDataMap: MutableMap<Int, ParkingMatchesData> = mutableMapOf()
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
        imageLoadPolicy: ImageLoadPolicy = ImageLoadPolicy.ALL,
        completion: (Boolean, ResourceLoadInfo?) -> Unit,
    ) {
        setRegion(provider, region, env)
        bundleDataManager.loadBundle(application, bundleType, sectorId, imageLoadPolicy) { isSuccess, message, snapshot, source ->
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

            val fromCache = isFromCache(source)
            val postLoadStartMs = postLoadNowMs()
            val cacheMs = measurePostLoadMs { cacheSnapshot(sectorId, snapshot) }
            val emitMs = measurePostLoadMs {
                when (bundleType) {
                    ResourceBundleType.JUPITER -> emitSnapshot(snapshot)
                    ResourceBundleType.WARP -> emitWarpSnapshot(snapshot)
                    ResourceBundleType.VENUS -> emitVenusSnapshot(snapshot)
                }
            }
            logPostLoadTotal(bundleType, sectorId, fromCache, cacheMs, emitMs, postLoadStartMs)
            completion(true, ResourceLoadInfo(versionId = snapshot.versionId, fromCache = fromCache))
        }
    }

    fun loadJupiterResource(
        application: Application,
        provider: String,
        region: String,
        sectorId: Int,
        env: ResourceServerEnv = ResourceServerEnv.PROD,
        imageLoadPolicy: ImageLoadPolicy = ImageLoadPolicy.ALL,
        completion: (Boolean, ResourceLoadInfo?) -> Unit,
    ) {
        TJResourceLogger.d("(TJLabsResource) loadJupiterResource request // provider=$provider // region=$region // sectorId=$sectorId // env=$env // imagePolicy=$imageLoadPolicy")
        loadResourceByType(ResourceBundleType.JUPITER, application, provider, region, sectorId, env, imageLoadPolicy, completion)
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
        loadResourceByType(ResourceBundleType.VENUS, application, provider, region, sectorId, env, completion = completion)
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
        loadResourceByType(ResourceBundleType.WARP, application, provider, region, sectorId, ResourceServerEnv.PROD, completion = completion)
    }

    fun loadResource(
        application: Application,
        provider: String,
        region: String,
        sectorId: Int,
        env: ResourceServerEnv = ResourceServerEnv.PROD,
        imageLoadPolicy: ImageLoadPolicy = ImageLoadPolicy.ALL,
        completion: (Boolean, ResourceLoadInfo?) -> Unit,
    ) {
        loadJupiterResource(application, provider, region, sectorId, env, imageLoadPolicy, completion)
    }

    /**
     * companion memory 캐시에 남은 sector 스냅샷을 즉시 현재 delegate 로 re-emit 한다.
     * [loadBundle] (네트워크 meta 조회 · 파싱 · 디스크 IO) 를 완전히 스킵하는 fast-path.
     *
     * ── 사용 시나리오 (2026-09-07)
     * 상위 계층 (VM SDK) 이 자기 [TJLabsResourceManager] 인스턴스로 [loadResource] 를 이미 성공시킨
     * 상태에서, 하위 계층 (JupiterCalcManager) 이 자기 delegate 로 같은 스냅샷을 흘려 받고 싶을 때.
     * bundleCache 는 companion 이라 두 인스턴스 간 공유되므로, 같은 (provider, region, sectorId,
     * bundleType) 이면 즉시 히트한다.
     *
     * @return 캐시 히트 시 [ResourceLoadInfo] (fromCache=true), 미스 시 null. 호출자는 null 이면
     *         일반 [loadResource] 로 fallback 해야 한다.
     */
    fun emitCachedSnapshot(bundleType: ResourceBundleType, sectorId: Int): ResourceLoadInfo? {
        val snapshot = bundleDataManager.getCachedSnapshot(bundleType, sectorId) ?: return null
        TJResourceLogger.d(
            "(TJLabsResource) emitCachedSnapshot hit // type=$bundleType // sectorId=$sectorId // versionId=${snapshot.versionId}"
        )
        val postLoadStartMs = postLoadNowMs()
        val cacheMs = measurePostLoadMs { cacheSnapshot(sectorId, snapshot) }
        val emitMs = measurePostLoadMs {
            when (bundleType) {
                ResourceBundleType.JUPITER -> emitSnapshot(snapshot)
                ResourceBundleType.WARP -> emitWarpSnapshot(snapshot)
                ResourceBundleType.VENUS -> emitVenusSnapshot(snapshot)
            }
        }
        logPostLoadTotal(bundleType, sectorId, fromCache = true, cacheMs = cacheMs, emitMs = emitMs, startMs = postLoadStartMs)
        return ResourceLoadInfo(versionId = snapshot.versionId, fromCache = true)
    }

    private fun setRegion(provider: String, region: String, env: ResourceServerEnv = ResourceServerEnv.PROD) {
        TJLabsResourceNetworkConstants.setServerURL(provider, region, env)
        TJLabsFileDownloader.provider = provider
        TJLabsFileDownloader.region = region
    }

    private fun cacheSnapshot(sectorId: Int, snapshot: BundleDataSnapshot) {
        val timer = PostLoadTimer("cache", sectorId)
        timer.step("clearDebugLevelCache") { clearDebugLevelCache(sectorId) }
        timer.step("sectorData") {
            sectorDataMap[sectorId] = snapshot.sectorData
            buildingsDataMap[sectorId] = snapshot.sectorData.buildings
        }
        timer.step("transitions", snapshot.transitions.size) {
            transitionsBySector[sectorId] = snapshot.transitions
            val buildingNameById = snapshot.sectorData.buildings.associate { it.id to it.name }
            for (t in snapshot.transitions) {
                val bldgName = buildingNameById[t.level.building_id] ?: continue
                val key = "${sectorId}_${bldgName}_${t.level.name}"
                transitionsByKey[key] = t
            }
        }
        timer.step("levelIndex", snapshot.sectorData.buildings.sumOf { it.levels.size }) {
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
        }

        val filteredLevelWards = snapshot.levelWardsDataMap.filterKeys { it.contains("_D").not() }
        timer.step("levelWardsDataMap", filteredLevelWards.size) { levelWardsDataMap.putAll(filteredLevelWards) }
        timer.step("scaleOffsetDataMap", snapshot.scaleOffsetDataMap.size) { scaleOffsetDataMap.putAll(snapshot.scaleOffsetDataMap) }
        timer.step("pathPixelDataMap", snapshot.pathPixelDataMap.size) { pathPixelDataMap.putAll(snapshot.pathPixelDataMap) }
        timer.step("geofenceDataMap", snapshot.geofenceDataMap.size) { geofenceDataMap.putAll(snapshot.geofenceDataMap) }
        timer.step("entranceDataMap", snapshot.entranceDataMap.size) { entranceDataMap.putAll(snapshot.entranceDataMap) }
        timer.step("entranceItemDataMap", snapshot.entranceItemDataMap.size) { entranceItemDataMap.putAll(snapshot.entranceItemDataMap) }
        timer.step("entranceRouteDataMap", snapshot.entranceRouteDataMap.size) { entranceRouteDataMap.putAll(snapshot.entranceRouteDataMap) }
        timer.step("parkingMatchesDataMap", snapshot.parkingMatchesDataByLevelId.size) { parkingMatchesDataMap.putAll(snapshot.parkingMatchesDataByLevelId) }
        timer.step("levelUnitsDataMap", snapshot.levelUnitsDataMap.size) { levelUnitsDataMap.putAll(snapshot.levelUnitsDataMap) }

        val filteredLandmark = snapshot.landmarkDataMap.filterKeys { it.contains("_D").not() }
        timer.step("landmarkDataMap", filteredLandmark.size) { landmarkDataMap.putAll(filteredLandmark) }
        val filteredNode = snapshot.nodeDataMap.filterKeys { it.contains("_D").not() }
        timer.step("nodeDataMap", filteredNode.size) { nodeDataMap.putAll(filteredNode) }
        val filteredLink = snapshot.linkDataMap.filterKeys { it.contains("_D").not() }
        timer.step("linkDataMap", filteredLink.size) { linkDataMap.putAll(filteredLink) }
        val filteredImage = snapshot.imageDataMap.filterKeys { it.contains("_D").not() }
        timer.step("imageDataMap", filteredImage.size) { imageDataMap.putAll(filteredImage) }
        timer.step("affineParam") { affineParamMap[sectorId] = snapshot.affineParam }
        timer.logSummary()
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
        val sectorId = snapshot.sectorData.id
        val timer = PostLoadTimer("emit", sectorId)

        timer.item("onSectorData", "id=$sectorId buildings=${snapshot.sectorData.buildings.size}") {
            delegate?.onSectorData(snapshot.sectorData)
        }
        timer.item("onBuildingsData", "n=${snapshot.sectorData.buildings.size}") {
            delegate?.onBuildingsData(snapshot.sectorData.buildings)
        }

        snapshot.levelWardsDataMap.filterKeys { it.contains("_D").not() }.forEach { (key, value) ->
            timer.item("onLevelWardsData", "key=$key wards=${value.size}") {
                delegate?.onLevelWardsData(key, value)
            }
        }
        snapshot.scaleOffsetDataMap.forEach { (key, value) ->
            timer.item("onScaleOffsetData", "key=$key n=${value.size}") {
                delegate?.onScaleOffsetData(key, value)
            }
        }
        snapshot.pathPixelDataMap.forEach { (key, value) ->
            timer.item("onPathPixelData", "key=$key") {
                delegate?.onPathPixelData(key, resolveLevelType(key), value)
            }
        }
        snapshot.geofenceDataMap.forEach { (key, value) ->
            timer.item("onGeofenceData", "key=$key") {
                delegate?.onGeofenceData(key, value)
            }
        }
        snapshot.entranceItemDataMap.forEach { (key, value) ->
            timer.item("onEntranceData", "key=$key") {
                delegate?.onEntranceData(key, value)
            }
        }
        snapshot.entranceRouteDataMap.forEach { (key, value) ->
            timer.item("onEntranceRouteData", "key=$key") {
                delegate?.onEntranceRouteData(key, value)
            }
        }
        snapshot.levelUnitsDataMap.forEach { (key, value) ->
            timer.item("onLevelUnitsData", "key=$key n=${value.size}") {
                delegate?.onLevelUnitsData(key, value)
            }
        }
        snapshot.landmarkDataMap.filterKeys { it.contains("_D").not() }.forEach { (key, value) ->
            timer.item("onLandmarkData", "key=$key n=${value.size}") {
                delegate?.onLandmarkData(key, value)
            }
        }
        snapshot.nodeDataMap.filterKeys { it.contains("_D").not() }.forEach { (key, value) ->
            timer.item("onNodeLinkData:NODE", "key=$key n=${value.size}") {
                delegate?.onNodeLinkData(key, NodeLinkType.NODE, value)
            }
        }
        snapshot.linkDataMap.filterKeys { it.contains("_D").not() }.forEach { (key, value) ->
            timer.item("onNodeLinkData:LINK", "key=$key n=${value.size}") {
                delegate?.onNodeLinkData(key, NodeLinkType.LINK, value)
            }
        }
        snapshot.imageUrlsByKey.filterKeys { it.contains("_D").not() }.forEach { (key, _) ->
            val bitmap = snapshot.imageDataMap[key]
            timer.item("onBuildingLevelImageData", "key=$key ${bitmapDim(bitmap)}") {
                delegate?.onBuildingLevelImageData(key, bitmap)
            }
        }

        val affine = snapshot.affineParam
        if (affine != null) {
            timer.item("onAffineData", "sector=$sectorId") {
                delegate?.onAffineData(sectorId, affine)
            }
        }

        // 층이동 구간 per-key emit. key 형식은 다른 level-scope 콜백들과 동일.
        // 구버전 응답에는 transitions 가 없어 발동 자체가 없음.
        val buildingNameById = snapshot.sectorData.buildings.associate { it.id to it.name }
        for (t in snapshot.transitions) {
            val bldgName = buildingNameById[t.level.building_id] ?: continue
            val key = "${sectorId}_${bldgName}_${t.level.name}"
            timer.item("onTransitionData", "key=$key") {
                delegate?.onTransitionData(key, t)
            }
        }

        timer.logSummary()
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

    /**
     * 2026-08-28 스키마 — level id 에 해당하는 GeoJSON 피처 id ↔ 외부 업체 주차면 id 매핑.
     * 파일이 업로드되지 않은 층은 null, 업로드됐지만 matches 가 비어있으면 emptyList.
     * `matchingId` 는 숫자처럼 보여도 String 이니 Int 로 파싱하지 말 것.
     */
    fun getParkingMatches(levelId: Int): List<ParkingMatch>? = parkingMatchesDataMap[levelId]?.matches

    /** 전체 sector 의 모든 level 에 대한 parking matches 인덱스 (levelId → matches). */
    fun getAllParkingMatches(): Map<Int, List<ParkingMatch>> =
        parkingMatchesDataMap.mapValues { it.value.matches }

    /**
     * parking_matches 파일 root 의 "level_match" (사용자/호스트 앱이 인식하는 층 표기, 예: "3").
     * 파일이 업로드되지 않았거나 파일에 필드가 없으면 null.
     * building 단위로만 유일하므로 (buildingId, level_match) 조합으로 levelId 를 역인덱싱할 것.
     */
    fun getLevelMatch(levelId: Int): String? = parkingMatchesDataMap[levelId]?.level_match

    /** 전체 sector 의 levelId → level_match 인덱스 (level_match 가 실린 층만 포함). */
    fun getAllLevelMatches(): Map<Int, String> =
        parkingMatchesDataMap.mapNotNull { (id, data) ->
            data.level_match?.let { id to it }
        }.toMap()

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

    /**
     * 특정 층 이미지를 on-demand 로 다운로드 (또는 캐시 히트 시 즉시 사용).
     *
     * ── 사용 시나리오 (2026-09-07)
     * [ImageLoadPolicy.NONE] / [ImageLoadPolicy.DEFAULT_ONLY] 로 초기 loadResource 를 마친 뒤,
     * 사용자가 다른 층으로 이동하거나 호스트 앱이 층 지도를 새로 요청할 때 이 API 를 호출하면
     * bundleCache 에 저장된 스냅샷의 imageUrlsByKey 에서 URL 을 찾아 fetch 하고 delegate 로 emit.
     *
     * @param application 컨텍스트
     * @param sectorId 대상 sector
     * @param levelKey `"${sectorId}_${buildingName}_${levelName}"` 형식. sector 로드 시 emit 된
     *   `onBuildingLevelImageData(imageKey, null)` 의 imageKey 를 그대로 사용.
     * @param completion (성공 여부). 성공 시 delegate 로 `onBuildingLevelImageData(key, bitmap)` 발화.
     */
    fun loadLevelImage(
        application: Application,
        sectorId: Int,
        levelKey: String,
        completion: (Boolean) -> Unit,
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val (bitmap, ok) = bundleDataManager.loadSingleImage(
                application, ResourceBundleType.JUPITER, sectorId, levelKey
            )
            withContext(Dispatchers.Main) {
                if (ok && bitmap != null) {
                    imageDataMap[levelKey] = bitmap
                    delegate?.onBuildingLevelImageData(levelKey, bitmap)
                    completion(true)
                } else {
                    delegate?.onError(ResourceError.Image, levelKey)
                    completion(false)
                }
            }
        }
    }

    /**
     * sector 안에서 아직 다운로드되지 않은 층 이미지 전부를 background 로 병렬 fetch.
     *
     * [ImageLoadPolicy.DEFAULT_ONLY] 로 첫 층만 받은 뒤 여유 시점에 이 함수를 호출하면
     * 나머지 층이 미리 채워져 사용자 층 전환 시 즉시 표시 가능.
     *
     * @param completion (loaded, failed) — 이번 호출에서 새로 받은 개수와 실패 개수.
     *   이미 캐시에 있는 것은 skip 되어 loaded 에도 포함되지 않음.
     */
    fun prefetchRemainingImages(
        application: Application,
        sectorId: Int,
        completion: (loaded: Int, failed: Int) -> Unit,
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val (loaded, failed) = bundleDataManager.prefetchMissingImages(
                application, ResourceBundleType.JUPITER, sectorId
            )
            // prefetch 로 새로 받은 이미지는 별도 emit 하지 않는다. 호스트 앱이 개별 층에 실제로
            // 접근할 때 [loadLevelImage] 나 [updateImage] 를 통해 emit 하면 됨.
            withContext(Dispatchers.Main) { completion(loaded, failed) }
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

    // ---- post-load timing helpers ----------------------------------------
    //
    // loadBundle 콜백 이후 (cacheSnapshot + emitSnapshot) 각 항목별 소요시간을 계측한다.
    // loadBundle 내부의 network/parse/organize 타이밍은 TJLabsBundleDataManager 가 이미
    // "(loadResources timing)" 프리픽스로 기록하므로, 여기서는 "(postLoad timing)" 프리픽스로
    // 분리 발화한다. TJResourceLogger.setDebugOption(true) 로 켠 상태에서만 남는다.
    //
    // 필터: `adb logcat -s TJLabsResourceManager:I`  (카테고리 요약)
    //       `adb logcat -s TJLabsResourceManager:D`  (항목별 상세)

    private fun postLoadNowMs(): Long = System.nanoTime() / 1_000_000L

    private inline fun measurePostLoadMs(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000L
    }

    private fun bitmapDim(bitmap: Bitmap?): String =
        if (bitmap != null) "${bitmap.width}x${bitmap.height}" else "null"

    private fun logPostLoadTotal(
        bundleType: ResourceBundleType,
        sectorId: Int,
        fromCache: Boolean,
        cacheMs: Long,
        emitMs: Long,
        startMs: Long,
    ) {
        if (!TJResourceLogger.isDebugEnabled()) return
        val totalMs = (System.nanoTime() / 1_000_000L) - startMs
        val mode = if (fromCache) "CACHE" else "FRESH"
        TJResourceLogger.i(
            "(postLoad timing) TOTAL = ${totalMs}ms (cache ${cacheMs}ms + emit ${emitMs}ms) // sectorId=$sectorId, type=$bundleType, mode=$mode"
        )
    }

    /**
     * 한 phase (cache 또는 emit) 안에서 개별 항목(카테고리/키)별 elapsed 를 집계한다.
     *
     * - `item(name, detail)` : 델리게이트 콜백 1건 시간 측정 (per-key). DEBUG 로그 + 카테고리별 누적.
     * - `step(name, count)`  : bulk 연산 1건 시간 측정 (putAll 등). DEBUG 로그 + 카테고리별 누적.
     * - `logSummary()`       : 카테고리별 요약 (INFO). 항목이 없으면 INFO 만 남김.
     *
     * TJResourceLogger.isDebugEnabled() = false 이면 전부 no-op (측정 자체도 skip).
     */
    private class PostLoadTimer(private val phase: String, private val sectorId: Int) {
        private val enabled: Boolean = TJResourceLogger.isDebugEnabled()
        private val startNs: Long = if (enabled) System.nanoTime() else 0L
        private val counts = LinkedHashMap<String, Int>()
        private val totalsNs = LinkedHashMap<String, Long>()
        private val itemsNs = LinkedHashMap<String, Long>() // per-item count for avg

        inline fun item(name: String, detail: String = "", block: () -> Unit) {
            if (!enabled) { block(); return }
            recordStart(name)
            val t0 = System.nanoTime()
            block()
            recordEnd(name, System.nanoTime() - t0, detail, itemGranular = true)
        }

        inline fun step(name: String, count: Int = 1, block: () -> Unit) {
            if (!enabled) { block(); return }
            recordStart(name)
            val t0 = System.nanoTime()
            block()
            recordEnd(name, System.nanoTime() - t0, "n=$count", itemGranular = false)
            countsBumpBy(name, count - 1) // step already counted as 1 in recordEnd
        }

        fun logSummary() {
            if (!enabled) return
            val totalMs = (System.nanoTime() - startNs) / 1_000_000L
            if (counts.isEmpty()) {
                TJResourceLogger.i("(postLoad timing) [$phase] sectorId=$sectorId total=${totalMs}ms (no items)")
                return
            }
            TJResourceLogger.i(
                "(postLoad timing) [$phase] sectorId=$sectorId total=${totalMs}ms items=${counts.values.sum()} categories=${counts.size}"
            )
            for ((name, count) in counts) {
                val sumMs = (totalsNs[name] ?: 0L) / 1_000_000L
                val avgUs = if (count > 0) ((totalsNs[name] ?: 0L) / count) / 1_000L else 0L
                TJResourceLogger.i("(postLoad timing)   [$phase] $name count=$count sumMs=$sumMs avgUs=$avgUs")
            }
        }

        @PublishedApi internal fun recordStart(name: String) {
            counts[name] = (counts[name] ?: 0)
        }

        @PublishedApi internal fun recordEnd(name: String, deltaNs: Long, detail: String, itemGranular: Boolean) {
            counts[name] = (counts[name] ?: 0) + 1
            totalsNs[name] = (totalsNs[name] ?: 0L) + deltaNs
            itemsNs[name] = (itemsNs[name] ?: 0L) + deltaNs
            if (itemGranular) {
                val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
                val dMicros = deltaNs / 1_000L
                TJResourceLogger.d(
                    "(postLoad timing) [$phase] +${elapsedMs}ms (Δ${dMicros}us) $name $detail"
                )
            } else {
                val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
                val dMicros = deltaNs / 1_000L
                TJResourceLogger.d(
                    "(postLoad timing) [$phase] +${elapsedMs}ms (Δ${dMicros}us) step $name $detail"
                )
            }
        }

        @PublishedApi internal fun countsBumpBy(name: String, extra: Int) {
            if (extra <= 0) return
            counts[name] = (counts[name] ?: 0) + extra
        }
    }
}

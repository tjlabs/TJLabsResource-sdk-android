package com.tjlabs.tjlabsresource_sdk_android.manager

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.tjlabs.tjlabsresource_sdk_android.AffineTransParamOutput
import com.tjlabs.tjlabsresource_sdk_android.BuildingOutput
import com.tjlabs.tjlabsresource_sdk_android.Category
import com.tjlabs.tjlabsresource_sdk_android.CategoryData
import com.tjlabs.tjlabsresource_sdk_android.DefaultPositionBuildingOutput
import com.tjlabs.tjlabsresource_sdk_android.DefaultPositionLevelOutput
import com.tjlabs.tjlabsresource_sdk_android.DefaultPositionOutput
import com.tjlabs.tjlabsresource_sdk_android.EntranceData
import com.tjlabs.tjlabsresource_sdk_android.EntranceRouteData
import com.tjlabs.tjlabsresource_sdk_android.GeofenceData
import com.tjlabs.tjlabsresource_sdk_android.GraphLevelLink
import com.tjlabs.tjlabsresource_sdk_android.GraphLevelLinkGroup
import com.tjlabs.tjlabsresource_sdk_android.GraphLevelNode
import com.tjlabs.tjlabsresource_sdk_android.InnermostWard
import com.tjlabs.tjlabsresource_sdk_android.ItemIdNumber
import com.tjlabs.tjlabsresource_sdk_android.LandmarkData
import com.tjlabs.tjlabsresource_sdk_android.LevelOutput
import com.tjlabs.tjlabsresource_sdk_android.LinkData
import com.tjlabs.tjlabsresource_sdk_android.NodeData
import com.tjlabs.tjlabsresource_sdk_android.NodeDirection
import com.tjlabs.tjlabsresource_sdk_android.OutermostWard
import com.tjlabs.tjlabsresource_sdk_android.PathPixelData
import com.tjlabs.tjlabsresource_sdk_android.PeakData
import com.tjlabs.tjlabsresource_sdk_android.PostInput
import com.tjlabs.tjlabsresource_sdk_android.SectorBundleMetaOutput
import com.tjlabs.tjlabsresource_sdk_android.SectorOutput
import com.tjlabs.tjlabsresource_sdk_android.ResourceBundleType
import com.tjlabs.tjlabsresource_sdk_android.onprem.OnPremRoutingState
import com.tjlabs.tjlabsresource_sdk_android.ResourceRegion
import com.tjlabs.tjlabsresource_sdk_android.ServerProvider
import com.tjlabs.tjlabsresource_sdk_android.SimulationBundleOutput
import com.tjlabs.tjlabsresource_sdk_android.SimulationItemOutput
import com.tjlabs.tjlabsresource_sdk_android.TJLabsFileDownloader
import com.tjlabs.tjlabsresource_sdk_android.TransitionLevelRef
import com.tjlabs.tjlabsresource_sdk_android.TransitionOutput
import com.tjlabs.tjlabsresource_sdk_android.TransitionPoint
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstants
import com.tjlabs.tjlabsresource_sdk_android.UnitData
import com.tjlabs.tjlabsresource_sdk_android.VenusBuildingOutput
import com.tjlabs.tjlabsresource_sdk_android.VenusLevelOutput
import com.tjlabs.tjlabsresource_sdk_android.VenusSectorOutput
import com.tjlabs.tjlabsresource_sdk_android.VenusWardOutput
import com.tjlabs.tjlabsresource_sdk_android.WarpBuildingOutput
import com.tjlabs.tjlabsresource_sdk_android.WarpLevelOutput
import com.tjlabs.tjlabsresource_sdk_android.WarpSectorOutput
import com.tjlabs.tjlabsresource_sdk_android.WarpWardContentOutput
import com.tjlabs.tjlabsresource_sdk_android.WarpWardOutput
import com.tjlabs.tjlabsresource_sdk_android.SectorBundleMapImageOutput
import com.tjlabs.tjlabsresource_sdk_android.util.TJResourceLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.FileOutputStream
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

internal data class BundleDataSnapshot(
    val bundleType: ResourceBundleType,
    val versionId: String,
    val bundleUrl: String,
    val sectorData: SectorOutput,
    val levelWardsDataMap: Map<String, List<String>>,
    val scaleOffsetDataMap: Map<String, List<Float>>,
    val geofenceDataMap: Map<String, GeofenceData>,
    val levelUnitsDataMap: Map<String, List<UnitData>>,
    val landmarkDataMap: Map<String, Map<String, LandmarkData>>,
    val nodeDataMap: Map<String, Map<Int, NodeData>>,
    val linkDataMap: Map<String, Map<Int, LinkData>>,
    val pathPixelDataMap: Map<String, PathPixelData>,
    val entranceDataMap: Map<String, EntranceData>,
    val entranceItemDataMap: Map<String, EntranceData>,
    val entranceRouteDataMap: Map<String, EntranceRouteData>,
    val imageUrlsByKey: Map<String, String>,
    val imageDataMap: Map<String, Bitmap>,
    val affineParam: AffineTransParamOutput?,
    val graphPathUrlsByKey: Map<String, String>,
    val entranceRouteUrlsByKey: Map<String, String>,
    val warpSectorData: WarpSectorOutput?,
    val venusSectorData: VenusSectorOutput?,
    val transitions: List<TransitionOutput> = emptyList()
)

internal class TJLabsBundleDataManager {
    companion object {
        private val bundleCache: MutableMap<String, BundleDataSnapshot> = mutableMapOf()
        private const val PREF_NAME = "TJLabsResourcesPref"
        private const val CSV_DIR = "tj_bundle_csv"
        private const val PREF_BUNDLE_VERSION_PREFIX = "bundle_version_"
        private const val PREF_BUNDLE_URL_PREFIX = "bundle_url_"
        private const val PREF_BUNDLE_FILE_PREFIX = "bundle_file_"
        private const val PREF_BUNDLE_META_TS_PREFIX = "bundle_meta_ts_"
        private const val PREF_PATH_VERSION_PREFIX = "graph_path_version_"
        private const val PREF_PATH_URL_PREFIX = "graph_path_url_"
        private const val PREF_PATH_FILE_PREFIX = "graph_path_file_"
        private const val PREF_ENTRANCE_VERSION_PREFIX = "entrance_route_version_"
        private const val PREF_ENTRANCE_URL_PREFIX = "entrance_route_url_"
        private const val PREF_ENTRANCE_FILE_PREFIX = "entrance_route_file_"
        private const val PREF_SIM_VERSION_PREFIX = "simulation_version_"
        private const val PREF_SIM_URL_PREFIX = "simulation_url_"
        private const val PREF_SIM_FILE_PREFIX = "simulation_file_"
        private const val PREF_IMAGE_VERSION_PREFIX = "image_version_"
        private const val PREF_IMAGE_URL_PREFIX = "image_url_"
        private const val PREF_IMAGE_FILE_PREFIX = "image_file_"
        private const val PDR_LEVEL_KEY_SUFFIX = "_PDR"
        private const val IMG_DIR = "tj_bundle_img"

        private val bitmapMemoryCache: MutableMap<String, Bitmap> = mutableMapOf()
    }

    private fun buildSnapshotCacheKey(bundleType: ResourceBundleType, sectorId: Int): String {
        return "${buildCacheNamespace()}_${bundleType.name}_$sectorId"
    }

    /**
     * 메모리 + 디스크 캐시(번들 raw json, csv, image, prefs)를 모두 비웁니다.
     * sectorId 가 null 이면 모든 sector 데이터를 비웁니다.
     */
    fun clearCache(application: Application, sectorId: Int? = null) {
        // 1) 메모리 캐시
        if (sectorId == null) {
            bundleCache.clear()
            bitmapMemoryCache.clear()
        } else {
            val suffix = "_$sectorId"
            bundleCache.keys.removeAll { it.endsWith(suffix) }
            bitmapMemoryCache.keys.removeAll { it.startsWith("${sectorId}_") }
        }

        // 2) 디스크 파일 (cacheDir/$CSV_DIR, cacheDir/$IMG_DIR)
        runCatching {
            val csvRoot = File(application.cacheDir, CSV_DIR)
            val imgRoot = File(application.cacheDir, IMG_DIR)
            if (sectorId == null) {
                csvRoot.deleteRecursivelyQuietly()
                imgRoot.deleteRecursivelyQuietly()
            } else {
                File(csvRoot, buildSectorCacheFolderName(sectorId)).deleteRecursivelyQuietly()
                File(imgRoot, buildSectorCacheFolderName(sectorId)).deleteRecursivelyQuietly()
            }
        }

        // 3) SharedPreferences — sector 단위로 키를 prefix-match 해서 삭제
        runCatching {
            val prefs = application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            val all = prefs.all
            if (sectorId == null) {
                for (k in all.keys) editor.remove(k)
            } else {
                val sectorTokens = listOf("_${sectorId}_", "_${sectorId}.", "_$sectorId")
                for (k in all.keys) {
                    if (sectorTokens.any { token -> k.contains(token) }) {
                        editor.remove(k)
                    }
                }
            }
            editor.apply()
        }
        TJResourceLogger.d { "(TJLabsResource) clearCache done // sectorId=${sectorId ?: "ALL"}" }
    }

    private fun File.deleteRecursivelyQuietly() {
        runCatching { if (exists()) deleteRecursively() }
    }

    /**
     * 번들 로드 콜백. [source] 는 로딩 경로를 식별하는 내부 문자열:
     *   memory_fastpath, pref_meta+memory_cache, pref_meta+disk_raw, pref_meta+network_raw,
     *   network_meta+memory_cache, network_meta+disk_raw, network_meta+network_raw,
     *   meta_fail, network_meta+raw_fail, network_meta+parse_fail
     * TJLabsResourceManager 는 이 문자열로 `fromCache` 를 판정 (network_raw 포함 여부).
     */
    fun loadBundle(
        application: Application,
        bundleType: ResourceBundleType,
        sectorId: Int,
        completion: (Boolean, String, BundleDataSnapshot?, String) -> Unit
    ) {
        val loadStartMs = nowMs()

        // ---- per-load phase timing ----
        var metaMs: Long = -1L
        var rawFetchMs: Long = -1L
        var parseMs: Long = -1L
        var enrichMs: Long = -1L
        var pathCount = 0
        var entCount = 0
        var imgCount = 0

        fun emitSummary(source: String, success: Boolean) {
            val total = elapsedMs(loadStartMs)
            fun fmt(v: Long) = if (v < 0L) "  -  " else "%5dms".format(v)
            android.util.Log.i(
                "TJLabsResource_PERF",
                "[${bundleType.name}/$sectorId] total=%5dms src=%-26s ok=%-5s | meta=%s raw=%s parse=%s enrich=%s | path=%2d ent=%2d img=%2d"
                    .format(total, source, success.toString(), fmt(metaMs), fmt(rawFetchMs), fmt(parseMs), fmt(enrichMs), pathCount, entCount, imgCount)
            )
        }

        fun finish(success: Boolean, message: String, snapshot: BundleDataSnapshot?, source: String) {
            emitSummary(source, success)
            completion(success, message, snapshot, source)
        }
        fun finishOnMain(success: Boolean, message: String, snapshot: BundleDataSnapshot?, source: String) {
            emitSummary(source, success)
            CoroutineScope(Dispatchers.Main).launch {
                completion(success, message, snapshot, source)
            }
        }

        fun applyCounts(s: BundleDataSnapshot) {
            pathCount = s.pathPixelDataMap.size
            entCount = s.entranceRouteDataMap.size
            imgCount = s.imageDataMap.size
        }

        fun proceedWithMeta(meta: SectorBundleMetaOutput, metaSource: String) {
            val cacheKey = buildSnapshotCacheKey(bundleType, sectorId)
            val cached = bundleCache[cacheKey]
            if (cached != null && cached.versionId == meta.version_id) {
                applyCounts(cached)
                finish(true, "(TJLabsResource) Success : use cached bundle", cached, "${metaSource}+memory_cache")
                return
            }

            // 디스크 캐시 읽기 + JSON 파싱은 IO 스레드에서 수행, 메인 스레드 점유 제거
            CoroutineScope(Dispatchers.IO).launch {
                val cachedRaw = loadBundleRawFromCache(application, bundleType, sectorId, meta)
                if (cachedRaw != null) {
                    val parseCacheStartMs = nowMs()
                    val parsedFromCache = parseBundleRaw(bundleType, sectorId, meta, cachedRaw)
                    parseMs = elapsedMs(parseCacheStartMs)
                    if (parsedFromCache != null) {
                        val enrichStartMs = nowMs()
                        enrichCsvData(application, sectorId, parsedFromCache) { csvSuccess, enriched ->
                            enrichMs = elapsedMs(enrichStartMs)
                            bundleCache[cacheKey] = enriched
                            applyCounts(enriched)
                            finish(csvSuccess, "(TJLabsResource) Success : use cached bundle(raw)", enriched, "${metaSource}+disk_raw")
                        }
                        return@launch
                    }
                }

                // 디스크 캐시 미스 → 네트워크 fetch (Retrofit 콜백은 Main 스레드에서 옴)
                val rawStartMs = nowMs()
                requestBundleRaw(bundleType, meta.url) { rawStatus, rawMsg, raw ->
                    rawFetchMs = elapsedMs(rawStartMs)
                    if ((rawStatus in 200 until 300) == false || raw.isNullOrEmpty()) {
                        finish(false, rawMsg, null, "${metaSource}+raw_fail")
                        return@requestBundleRaw
                    }

                    CoroutineScope(Dispatchers.IO).launch {
                        val parseRawStartMs = nowMs()
                        val parsed = parseBundleRaw(bundleType, sectorId, meta, raw)
                        parseMs = elapsedMs(parseRawStartMs)
                        if (parsed == null) {
                            finishOnMain(false, "(TJLabsResource) Error : parse bundle raw", null, "${metaSource}+parse_fail")
                            return@launch
                        }

                        val enrichStartMs = nowMs()
                        enrichCsvData(application, sectorId, parsed) { csvSuccess, enriched ->
                            enrichMs = elapsedMs(enrichStartMs)
                            bundleCache[cacheKey] = enriched
                            applyCounts(enriched)
                            // 1) prefs 는 동기로 즉시 기록 → 다음 호출이 freshness shortcut 적중
                            saveBundleMetaPrefs(application, bundleType, sectorId, meta.version_id, meta.url, null)
                            // 2) raw json 파일 디스크 쓰기는 비동기 (큰 IO, 메인 콜백 블로킹 회피)
                            CoroutineScope(Dispatchers.IO).launch {
                                saveBundleRawCache(application, bundleType, sectorId, meta.version_id, meta.url, raw)
                            }
                            finish(csvSuccess, "(TJLabsResource) Success : load bundle", enriched, "${metaSource}+network_raw")
                        }
                    }
                }
            }
        }

        // Fast-path: 메모리 스냅샷 + freshness window 적중 시, meta HTTP 와 디스크 IO 모두 스킵
        val cacheKeyForFastPath = buildSnapshotCacheKey(bundleType, sectorId)
        val inMemorySnapshot = bundleCache[cacheKeyForFastPath]
        if (inMemorySnapshot != null) {
            val cachedMetaFast = getSavedBundleMetaIfFresh(application, bundleType, sectorId)
            if (cachedMetaFast != null && cachedMetaFast.version_id == inMemorySnapshot.versionId) {
                applyCounts(inMemorySnapshot)
                finish(true, "(TJLabsResource) Success : use cached bundle (fastpath)", inMemorySnapshot, "memory_fastpath")
                return
            }
        }

        val cachedMeta = getSavedBundleMetaIfFresh(application, bundleType, sectorId)
        if (cachedMeta != null) {
            // metaMs 는 0 (네트워크 호출 없음) — prefs hit
            metaMs = 0L
            proceedWithMeta(cachedMeta, "pref_meta")
            return
        }

        val metaStartMs = nowMs()
        requestBundleMeta(bundleType, sectorId) { metaStatus, metaMsg, meta ->
            metaMs = elapsedMs(metaStartMs)
            if ((metaStatus in 200 until 300) == false || meta == null) {
                finish(false, metaMsg, null, "meta_fail")
                return@requestBundleMeta
            }
            saveBundleMetaTimestamp(application, bundleType, sectorId, nowMs())
            proceedWithMeta(meta, "network_meta")
        }
    }

    fun loadSimulationData(
        application: Application,
        sectorId: Int,
        completion: (Boolean, String, SimulationBundleOutput?) -> Unit
    ) {
        val bundleType = ResourceBundleType.JUPITER
        requestBundleMeta(bundleType, sectorId) { metaStatus, metaMsg, meta ->
            if ((metaStatus in 200 until 300) == false || meta == null) {
                completion(false, metaMsg, null)
                return@requestBundleMeta
            }

            loadBundleRawFromCache(application, bundleType, sectorId, meta)?.let { cachedRaw ->
                val parsed = parseSimulationsFromRaw(cachedRaw)
                if (parsed != null) {
                    downloadSimulationFiles(application, sectorId, meta.version_id, parsed) { downloadSuccess ->
                        completion(
                            downloadSuccess,
                            if (downloadSuccess) {
                                "(TJLabsResource) Success : load simulation from cache"
                            } else {
                                "(TJLabsResource) Failure : download simulation files from cache"
                            },
                            parsed
                        )
                    }
                    return@requestBundleMeta
                }
            }

            requestBundleRaw(bundleType, meta.url) { rawStatus, rawMsg, raw ->
                if ((rawStatus in 200 until 300) == false || raw.isNullOrBlank()) {
                    completion(false, rawMsg, null)
                    return@requestBundleRaw
                }

                saveBundleRawCache(application, bundleType, sectorId, meta.version_id, meta.url, raw)
                val parsed = parseSimulationsFromRaw(raw)
                if (parsed != null) {
                    downloadSimulationFiles(application, sectorId, meta.version_id, parsed) { downloadSuccess ->
                        completion(
                            downloadSuccess,
                            if (downloadSuccess) {
                                "(TJLabsResource) Success : load simulation"
                            } else {
                                "(TJLabsResource) Failure : download simulation files"
                            },
                            parsed
                        )
                    }
                } else {
                    completion(false, "(TJLabsResource) Error : simulations not found", null)
                }
            }
        }
    }

    private fun requestBundleMeta(
        bundleType: ResourceBundleType,
        sectorId: Int,
        completion: (Int, String, SectorBundleMetaOutput?) -> Unit
    ) {
        val baseUrl = TJLabsResourceNetworkConstants.getBaseUrl(bundleType)
        val serverVersion = TJLabsResourceNetworkConstants.getBundleServerVersion(bundleType)
        val env = TJLabsResourceNetworkConstants.getCurrentEnv()
        TJResourceLogger.d(
            "(TJLabsResource) request bundle meta // type=$bundleType // env=$env // baseUrl=$baseUrl // version=$serverVersion // sectorId=$sectorId"
        )
        TJLabsResourceNetworkConstants.genRetrofit(baseUrl) { retrofit, authStatus, authMessage ->
            if (retrofit == null) {
                TJResourceLogger.d(
                    "(TJLabsResource) request bundle meta auth fail // type=$bundleType // sectorId=$sectorId // status=$authStatus // message=$authMessage"
                )
                completion(authStatus, "(TJLabsResource) Failure : getSectorBundleMeta(auth)", null)
                return@genRetrofit
            }

            val api = retrofit.create(PostInput::class.java)
            val call = if (OnPremRoutingState.isEnabled) {
                // On-prem PMS 는 서비스별 접두어 endpoint (`/v2/warp`, `/v2/venus`) 를 쓴다.
                // 응답 body 는 cloud 와 동일 (`SectorBundleMetaOutput` 재사용). JUPITER 는
                // on-prem 스펙 미확정이라 실패시킴 — 호출측이 warp/venus 만 로드하도록 스코프.
                when (bundleType) {
                    ResourceBundleType.WARP -> api.getWarpBundleOnPrem(sectorId)
                    ResourceBundleType.VENUS -> api.getVenusBundleOnPrem(sectorId)
                    ResourceBundleType.JUPITER -> {
                        completion(501, "(TJLabsResource) on-prem JUPITER endpoint not implemented", null)
                        return@genRetrofit
                    }
                }
            } else {
                when (bundleType) {
                    ResourceBundleType.VENUS -> api.getSectorLiteBundle(serverVersion, sectorId)
                    ResourceBundleType.JUPITER, ResourceBundleType.WARP -> api.getSectorBundle(serverVersion, sectorId)
                }
            }
            call.enqueue(object : Callback<SectorBundleMetaOutput> {
                override fun onFailure(call: Call<SectorBundleMetaOutput>, t: Throwable) {
                    TJResourceLogger.d("(TJLabsResource) request bundle meta fail // type=$bundleType // sectorId=$sectorId // error=${t.localizedMessage}")
                    completion(500, "(TJLabsResource) Failure : getSectorBundleMeta", null)
                }

                override fun onResponse(call: Call<SectorBundleMetaOutput>, response: Response<SectorBundleMetaOutput>) {
                    val status = response.code()
                    if (status in 200 until 300) {
                        val body = response.body()
                        TJResourceLogger.d(
                            "(TJLabsResource) request bundle meta success // type=$bundleType // sectorId=$sectorId // status=$status // versionId=${body?.version_id} // url=${body?.url}"
                        )
                        completion(status, "(TJLabsResource) Success : getSectorBundleMeta", body)
                    } else {
                        val errorBody = try { response.errorBody()?.string() } catch (_: Exception) { "read_error" }
                        TJResourceLogger.d(
                            "(TJLabsResource) request bundle meta error // type=$bundleType // sectorId=$sectorId // status=$status // errorBody=$errorBody"
                        )
                        completion(status, "(TJLabsResource) Error : getSectorBundleMeta", null)
                    }
                }
            })
        }
    }

    private fun requestBundleRaw(
        bundleType: ResourceBundleType,
        bundleUrl: String,
        completion: (Int, String, String?) -> Unit
    ) {
        val env = TJLabsResourceNetworkConstants.getCurrentEnv()
        TJResourceLogger.d("(TJLabsResource) request bundle raw // type=$bundleType // env=$env // url=$bundleUrl")
        val retrofit = TJLabsResourceNetworkConstants.genPlainRetrofit(TJLabsResourceNetworkConstants.getBaseUrl(bundleType))
        val api = retrofit.create(PostInput::class.java)
        api.getSectorBundleJsonRaw(bundleUrl).enqueue(object : Callback<okhttp3.ResponseBody> {
            override fun onFailure(call: Call<okhttp3.ResponseBody>, t: Throwable) {
                TJResourceLogger.d("(TJLabsResource) request bundle raw fail // url=$bundleUrl // error=${t.localizedMessage}")
                completion(500, "(TJLabsResource) Failure : getSectorBundleJsonRaw", null)
            }

            override fun onResponse(call: Call<okhttp3.ResponseBody>, response: Response<okhttp3.ResponseBody>) {
                val status = response.code()
                if (status in 200 until 300) {
                    val raw = response.body()?.string()
                    TJResourceLogger.d(
                        "(TJLabsResource) request bundle raw success // status=$status // url=$bundleUrl // rawSize=${raw?.length ?: 0}"
                    )
                    completion(status, "(TJLabsResource) Success : getSectorBundleJsonRaw", raw)
                } else {
                    val errorBody = try { response.errorBody()?.string() } catch (_: Exception) { "read_error" }

                    TJResourceLogger.d(
                        "(TJLabsResource) request bundle raw error // code =${call.request()}"
                    )

                    TJResourceLogger.d(
                        "(TJLabsResource) request bundle raw error // status=$status // url=$bundleUrl // errorBody=$errorBody"
                    )
                    completion(status, "(TJLabsResource) Error : getSectorBundleJsonRaw", null)
                }
            }
        })
    }

    private fun enrichCsvData(
        application: Application,
        sectorId: Int,
        snapshot: BundleDataSnapshot,
        completion: (Boolean, BundleDataSnapshot) -> Unit
    ) {
        val enrichTotalStartMs = nowMs()
        val hasPathUrls = snapshot.graphPathUrlsByKey.isNotEmpty()
        val hasEntranceUrls = snapshot.entranceRouteUrlsByKey.isNotEmpty()
        val hasImageUrls = snapshot.imageUrlsByKey.isNotEmpty()
        if (!hasPathUrls && !hasEntranceUrls && !hasImageUrls) {
            TJResourceLogger.d("(TJLabsResource) enrichCsvData skip // no enrich urls")
            completion(true, snapshot)
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            TJResourceLogger.d {
                "(TJLabsResource) enrichCsvData start // pathCsvCount=${snapshot.graphPathUrlsByKey.size} // entranceCsvCount=${snapshot.entranceRouteUrlsByKey.size} // imageCount=${snapshot.imageUrlsByKey.size}"
            }

            val pathTargets = snapshot.graphPathUrlsByKey.filterKeys { it.contains("_D").not() }
            val entranceTargets = snapshot.entranceRouteUrlsByKey
            val imageTargets = snapshot.imageUrlsByKey

            // path / entrance / image 세 단계를 모두 한 번에 fan-out 시켜 병렬 처리
            val parallelStart = nowMs()
            val pathDeferred = pathTargets.map { (key, url) ->
                async { key to fetchPathPixelData(application, sectorId, snapshot.versionId, key, url) }
            }
            val entranceDeferred = entranceTargets.map { (key, url) ->
                async { key to fetchEntranceRouteData(application, sectorId, snapshot.versionId, key, url) }
            }
            val imageDeferred = imageTargets.map { (key, url) ->
                async { key to loadImageWithCache(application, sectorId, snapshot.versionId, key, url) }
            }

            val pathResults = pathDeferred.awaitAll()
            val entranceResults = entranceDeferred.awaitAll()
            val imageResults = imageDeferred.awaitAll()
            TJResourceLogger.d {
                "(TJLabsResource) perf enrichCsvData parallel stage // sectorId=$sectorId // pathCount=${pathTargets.size} // entranceCount=${entranceTargets.size} // imageCount=${imageTargets.size} // elapsedMs=${elapsedMs(parallelStart)}"
            }

            var isAllSuccess = true
            val pathPixelData = snapshot.pathPixelDataMap.toMutableMap()
            val entranceRouteData = snapshot.entranceRouteDataMap.toMutableMap()
            val imageData = snapshot.imageDataMap.toMutableMap()

            for ((key, parsed) in pathResults) {
                if (parsed != null) {
                    pathPixelData[key] = parsed
                } else if (key.endsWith(PDR_LEVEL_KEY_SUFFIX)) {
                    TJResourceLogger.d { "(TJLabsResource) enrichCsvData optional fail@PathPixelCsv(PDR) // key=$key" }
                } else {
                    isAllSuccess = false
                    TJResourceLogger.d { "(TJLabsResource) enrichCsvData failed@PathPixelCsv(DR) // key=$key" }
                }
            }
            for ((key, parsed) in entranceResults) {
                if (parsed != null) entranceRouteData[key] = parsed
                else {
                    isAllSuccess = false
                    TJResourceLogger.d { "(TJLabsResource) enrichCsvData failed@EntranceCsv // key=$key" }
                }
            }
            for ((key, image) in imageResults) {
                if (image != null) imageData[key] = image
                else TJResourceLogger.d { "(TJLabsResource) enrichCsvData failed@Image // key=$key" }
            }

            val enriched = snapshot.copy(
                pathPixelDataMap = pathPixelData,
                entranceRouteDataMap = entranceRouteData,
                imageDataMap = imageData
            )

            withContext(Dispatchers.Main) {
                TJResourceLogger.d {
                    "(TJLabsResource) enrichCsvData done // success=$isAllSuccess // elapsedMs=${elapsedMs(enrichTotalStartMs)}"
                }
                completion(isAllSuccess, enriched)
            }
        }
    }

    private fun loadImageWithCache(
        application: Application,
        sectorId: Int,
        versionId: String,
        key: String,
        url: String
    ): Bitmap? {
        bitmapMemoryCache[key]?.let { return it }

        val prefs = application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val versionKey = buildScopedPrefKey(PREF_IMAGE_VERSION_PREFIX, sectorId, key)
        val urlKey = buildScopedPrefKey(PREF_IMAGE_URL_PREFIX, sectorId, key)
        val fileKey = buildScopedPrefKey(PREF_IMAGE_FILE_PREFIX, sectorId, key)
        val savedVersion = prefs.getString(versionKey, null)
        val savedUrl = prefs.getString(urlKey, null)
        val savedPath = prefs.getString(fileKey, null)

        if (savedVersion == versionId && savedUrl == url && !savedPath.isNullOrBlank()) {
            val cachedFile = File(savedPath)
            if (cachedFile.exists() && cachedFile.length() > 0) {
                try {
                    val bitmap = BitmapFactory.decodeFile(cachedFile.absolutePath)
                    if (bitmap != null) {
                        bitmapMemoryCache[key] = bitmap
                        TJResourceLogger.d { "(TJLabsResource) image cache hit // key=$key // path=${cachedFile.absolutePath}" }
                        return bitmap
                    }
                } catch (e: Exception) {
                    TJResourceLogger.d { "(TJLabsResource) image cache read fail // key=$key // error=${e.localizedMessage}" }
                }
            }
        }

        val downloaded = fetchImageFromUrl(key, url) ?: return null
        try {
            val imgDir = File(application.cacheDir, "$IMG_DIR/${buildSectorCacheFolderName(sectorId)}")
            if (!imgDir.exists()) imgDir.mkdirs()
            val outFile = File(imgDir, buildImageFileName(sectorId, key))
            FileOutputStream(outFile).use { out ->
                downloaded.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            prefs.edit()
                .putString(versionKey, versionId)
                .putString(urlKey, url)
                .putString(fileKey, outFile.absolutePath)
                .apply()
            TJResourceLogger.d { "(TJLabsResource) image cache save // key=$key // path=${outFile.absolutePath}" }
        } catch (e: Exception) {
            TJResourceLogger.d { "(TJLabsResource) image cache save fail // key=$key // error=${e.localizedMessage}" }
        }
        bitmapMemoryCache[key] = downloaded
        return downloaded
    }

    private fun buildImageFileName(sectorId: Int, key: String): String {
        val normalized = key
            .replace("/", "_").replace("\\", "_").replace(":", "_")
            .replace("*", "_").replace("?", "_").replace("\"", "_")
            .replace("<", "_").replace(">", "_").replace("|", "_")
            .trim()
            .ifEmpty { "${sectorId}_unknown" }
        return "$normalized.png"
    }

    private fun fetchPathPixelData(
        application: Application,
        sectorId: Int,
        versionId: String,
        key: String,
        url: String
    ): PathPixelData? {
        val startMs = nowMs()
        TJResourceLogger.d("(TJLabsResource) fetchPathPixelData start // key=$key // url=$url")
        val text = getCsvTextWithCache(
            application = application,
            sectorId = sectorId,
            versionId = versionId,
            key = key,
            url = url,
            source = "pathPixel:$key",
            versionPrefix = PREF_PATH_VERSION_PREFIX,
            urlPrefix = PREF_PATH_URL_PREFIX,
            filePrefix = PREF_PATH_FILE_PREFIX
        ) ?: run {
            TJResourceLogger.d("(TJLabsResource) perf fetchPathPixelData fail // key=$key // elapsedMs=${elapsedMs(startMs)}")
            return null
        }
        val parsed = parsePathPixelData(text)
        TJResourceLogger.d(
            "(TJLabsResource) fetchPathPixelData success // key=$key // points=${parsed.road.firstOrNull()?.size ?: 0} // elapsedMs=${elapsedMs(startMs)}"
        )
        return parsed
    }

    private fun fetchEntranceRouteData(
        application: Application,
        sectorId: Int,
        versionId: String,
        key: String,
        url: String
    ): EntranceRouteData? {
        val startMs = nowMs()
        TJResourceLogger.d("(TJLabsResource) fetchEntranceRouteData start // key=$key // url=$url")
        val text = getCsvTextWithCache(
            application = application,
            sectorId = sectorId,
            versionId = versionId,
            key = key,
            url = url,
            source = "entrance:$key",
            versionPrefix = PREF_ENTRANCE_VERSION_PREFIX,
            urlPrefix = PREF_ENTRANCE_URL_PREFIX,
            filePrefix = PREF_ENTRANCE_FILE_PREFIX
        ) ?: run {
            TJResourceLogger.d("(TJLabsResource) perf fetchEntranceRouteData fail // key=$key // elapsedMs=${elapsedMs(startMs)}")
            return null
        }
        val parsed = parseEntranceRouteData(text)
        TJResourceLogger.d(
            "(TJLabsResource) fetchEntranceRouteData success // key=$key // routes=${parsed.route.size} // elapsedMs=${elapsedMs(startMs)}"
        )
        return parsed
    }

    private fun getCsvTextWithCache(
        application: Application,
        sectorId: Int,
        versionId: String,
        key: String,
        url: String,
        source: String,
        versionPrefix: String,
        urlPrefix: String,
        filePrefix: String
    ): String? {
        val prefs = application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val versionKey = buildScopedPrefKey(versionPrefix, sectorId, key)
        val urlKey = buildScopedPrefKey(urlPrefix, sectorId, key)
        val fileKey = buildScopedPrefKey(filePrefix, sectorId, key)

        val savedVersion = prefs.getString(versionKey, null)
        val savedUrl = prefs.getString(urlKey, null)
        val savedPath = prefs.getString(fileKey, null)
        if (savedVersion == versionId && savedUrl == url && savedPath.isNullOrBlank().not()) {
            val cachedFile = File(savedPath!!)
            if (cachedFile.exists() && cachedFile.length() > 0) {
                try {
                    val cachedText = cachedFile.readText()
                    TJResourceLogger.d(
                        "(TJLabsResource) csv cache hit // source=$source // key=$key // version=$versionId // path=${cachedFile.absolutePath} // bytes=${cachedText.length}"
                    )
                    return cachedText
                } catch (e: Exception) {
                    TJResourceLogger.d(
                        "(TJLabsResource) csv cache read fail // source=$source // key=$key // path=${cachedFile.absolutePath} // error=${e.localizedMessage}"
                    )
                }
            } else {
                TJResourceLogger.d(
                    "(TJLabsResource) csv cache stale // source=$source // key=$key // path=$savedPath"
                )
            }
        } else {
            TJResourceLogger.d(
                "(TJLabsResource) csv cache miss // source=$source // key=$key // savedVersion=$savedVersion // newVersion=$versionId"
            )
        }

        val downloaded = fetchTextFromUrl(url, source) ?: return null
        saveCsvCache(
            application = application,
            sectorId = sectorId,
            key = key,
            url = url,
            versionId = versionId,
            source = source,
            content = downloaded,
            versionPrefix = versionPrefix,
            urlPrefix = urlPrefix,
            filePrefix = filePrefix
        )
        return downloaded
    }

    private fun saveCsvCache(
        application: Application,
        sectorId: Int,
        key: String,
        url: String,
        versionId: String,
        source: String,
        content: String,
        versionPrefix: String,
        urlPrefix: String,
        filePrefix: String
    ) {
        try {
            val cacheDir = File(application.cacheDir, "$CSV_DIR/${buildSectorCacheFolderName(sectorId)}")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            val fileName = buildCsvFileName(sectorId = sectorId, key = key)
            val csvFile = File(cacheDir, fileName)
            csvFile.writeText(content)

            val prefs = application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val versionKey = buildScopedPrefKey(versionPrefix, sectorId, key)
            val urlKey = buildScopedPrefKey(urlPrefix, sectorId, key)
            val fileKey = buildScopedPrefKey(filePrefix, sectorId, key)
            prefs.edit()
                .putString(versionKey, versionId)
                .putString(urlKey, url)
                .putString(fileKey, csvFile.absolutePath)
                .apply()

            TJResourceLogger.d(
                "(TJLabsResource) csv cache save // source=$source // key=$key // version=$versionId // path=${csvFile.absolutePath} // bytes=${content.length}"
            )
        } catch (e: Exception) {
            TJResourceLogger.d(
                "(TJLabsResource) csv cache save fail // source=$source // key=$key // error=${e.localizedMessage}"
            )
        }
    }

    private fun getSavedBundleMetaIfFresh(
        application: Application,
        bundleType: ResourceBundleType,
        sectorId: Int
    ): SectorBundleMetaOutput? {
        val prefs = application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val versionKey = getBundleMetaKey(bundleType, PREF_BUNDLE_VERSION_PREFIX, sectorId)
        val urlKey = getBundleMetaKey(bundleType, PREF_BUNDLE_URL_PREFIX, sectorId)
        val tsKey = getBundleMetaKey(bundleType, PREF_BUNDLE_META_TS_PREFIX, sectorId)
        val savedVersion = prefs.getString(versionKey, null)
        val savedUrl = prefs.getString(urlKey, null)
        val savedTs = prefs.getLong(tsKey, 0L)
        if (savedVersion.isNullOrBlank() || savedUrl.isNullOrBlank() || savedTs <= 0L) {
            return null
        }

        val ageMs = nowMs() - savedTs
        val freshWindowMs = 5 * 60 * 1000L
        if (ageMs > freshWindowMs) {
            TJResourceLogger.d(
                "(TJLabsResource) perf loadBundle meta shortcut miss // type=$bundleType // sectorId=$sectorId // reason=stale // ageMs=$ageMs"
            )
            return null
        }
        TJResourceLogger.d(
            "(TJLabsResource) perf loadBundle meta shortcut hit // type=$bundleType // sectorId=$sectorId // ageMs=$ageMs"
        )
        return SectorBundleMetaOutput(url = savedUrl, version_id = savedVersion)
    }

    private fun saveBundleMetaTimestamp(
        application: Application,
        bundleType: ResourceBundleType,
        sectorId: Int,
        timestampMs: Long
    ) {
        val prefs = application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val tsKey = getBundleMetaKey(bundleType, PREF_BUNDLE_META_TS_PREFIX, sectorId)
        prefs.edit().putLong(tsKey, timestampMs).apply()
    }

    private fun loadBundleRawFromCache(
        application: Application,
        bundleType: ResourceBundleType,
        sectorId: Int,
        meta: SectorBundleMetaOutput
    ): String? {
        val prefs = application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val versionKey = getBundleMetaKey(bundleType, PREF_BUNDLE_VERSION_PREFIX, sectorId)
        val urlKey = getBundleMetaKey(bundleType, PREF_BUNDLE_URL_PREFIX, sectorId)
        val fileKey = getBundleMetaKey(bundleType, PREF_BUNDLE_FILE_PREFIX, sectorId)

        val savedVersion = prefs.getString(versionKey, null)
        val savedUrl = prefs.getString(urlKey, null)
        val savedPath = prefs.getString(fileKey, null)

        if (savedVersion != meta.version_id || savedUrl != meta.url || savedPath.isNullOrBlank()) {
            TJResourceLogger.d(
                "(TJLabsResource) bundle raw cache miss // type=$bundleType // sectorId=$sectorId // savedVersion=$savedVersion // newVersion=${meta.version_id}"
            )
            return null
        }

        val rawFile = File(savedPath)
        if (rawFile.exists().not() || rawFile.length() <= 0) {
            TJResourceLogger.d(
                "(TJLabsResource) bundle raw cache stale // type=$bundleType // sectorId=$sectorId // path=$savedPath"
            )
            return null
        }

        return try {
            val cachedRaw = rawFile.readText()
            TJResourceLogger.d(
                "(TJLabsResource) bundle raw cache hit // type=$bundleType // sectorId=$sectorId // version=${meta.version_id} // path=${rawFile.absolutePath} // bytes=${cachedRaw.length}"
            )
            cachedRaw
        } catch (e: Exception) {
            TJResourceLogger.d(
                "(TJLabsResource) bundle raw cache read fail // type=$bundleType // sectorId=$sectorId // path=${rawFile.absolutePath} // error=${e.localizedMessage}"
            )
            null
        }
    }

    /**
     * meta 정보(version/url/ts) 를 prefs 에 동기 저장.
     * - prefs.apply() 자체는 비동기 디스크 flush 지만 in-memory 캐시는 즉시 갱신되므로
     *   곧바로 다시 호출되는 getSavedBundleMetaIfFresh() 가 적중 가능.
     * - raw 파일 디스크 쓰기는 별도로 비동기에서 처리.
     */
    private fun saveBundleMetaPrefs(
        application: Application,
        bundleType: ResourceBundleType,
        sectorId: Int,
        versionId: String,
        bundleUrl: String,
        rawFilePath: String?
    ) {
        val prefs = application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
            .putString(getBundleMetaKey(bundleType, PREF_BUNDLE_VERSION_PREFIX, sectorId), versionId)
            .putString(getBundleMetaKey(bundleType, PREF_BUNDLE_URL_PREFIX, sectorId), bundleUrl)
            .putLong(getBundleMetaKey(bundleType, PREF_BUNDLE_META_TS_PREFIX, sectorId), nowMs())
        if (rawFilePath != null) {
            editor.putString(getBundleMetaKey(bundleType, PREF_BUNDLE_FILE_PREFIX, sectorId), rawFilePath)
        }
        editor.apply()
    }

    private fun saveBundleRawCache(
        application: Application,
        bundleType: ResourceBundleType,
        sectorId: Int,
        versionId: String,
        bundleUrl: String,
        raw: String
    ) {
        try {
            val cacheDir = File(application.cacheDir, "$CSV_DIR/${buildSectorCacheFolderName(sectorId)}")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            val rawFile = File(cacheDir, buildBundleRawFileName(bundleType, sectorId))
            rawFile.writeText(raw)

            saveBundleMetaPrefs(application, bundleType, sectorId, versionId, bundleUrl, rawFile.absolutePath)

            TJResourceLogger.d {
                "(TJLabsResource) bundle raw cache save // type=$bundleType // sectorId=$sectorId // version=$versionId // path=${rawFile.absolutePath} // bytes=${raw.length}"
            }
        } catch (e: Exception) {
            TJResourceLogger.d {
                "(TJLabsResource) bundle raw cache save fail // type=$bundleType // sectorId=$sectorId // error=${e.localizedMessage}"
            }
        }
    }

    private fun getBundleMetaKey(bundleType: ResourceBundleType, prefix: String, sectorId: Int): String {
        val bundleTypeKey = bundleType.name.lowercase()
        return "${prefix}${buildCacheNamespace()}_${bundleTypeKey}_$sectorId"
    }

    private fun buildBundleRawFileName(bundleType: ResourceBundleType, sectorId: Int): String {
        return when (bundleType) {
            ResourceBundleType.JUPITER -> "bundle_${sectorId}.json"
            ResourceBundleType.VENUS -> "bundle_venus_${sectorId}.json"
            ResourceBundleType.WARP -> "bundle_warp_${sectorId}.json"
        }
    }

    private fun buildCsvFileName(sectorId: Int, key: String): String {
        // key format: {sectorId}_{buildingName}_{levelName} or {sectorId}_{buildingName}_{levelName}_{entranceNumber}
        // requested naming: {sectorId}_{buildingName}_{levelName}.csv
        val normalized = key
            .replace("/", "_")
            .replace("\\", "_")
            .replace(":", "_")
            .replace("*", "_")
            .replace("?", "_")
            .replace("\"", "_")
            .replace("<", "_")
            .replace(">", "_")
            .replace("|", "_")
            .trim()
            .ifEmpty { "${sectorId}_unknown" }
        return "$normalized.csv"
    }

    private fun buildSectorCacheFolderName(sectorId: Int): String {
        return "${buildCacheNamespace()}_${sectorId}"
    }

    private fun buildCacheNamespace(): String {
        // On-prem 모드는 cloud 와 캐시를 완전히 격리한다. 이유:
        //  1) 같은 sectorId 라도 cloud/on-prem 서버가 다른 데이터를 서빙할 수 있고
        //     bundle 파일명은 sectorId 만으로 정해지므로 네임스페이스가 겹치면 서로 덮어씀
        //  2) version_id 는 파일 내용 해시라 두 서버가 우연히 같은 해시를 낼 가능성은
        //     사실상 없지만, 서로 다른 origin 데이터를 같은 캐시 슬롯에서 관리하는 것은
        //     추적성·디버깅 관점에서도 나쁨
        //  3) 여러 on-prem 서버 (사내 10.0.5.110 vs 현장 192.168.120.75) 도 격리 대상
        if (OnPremRoutingState.isEnabled) {
            val hostPort = OnPremRoutingState.baseUrl
                .removePrefix("https://")
                .removePrefix("http://")
                .replace(Regex("[^A-Za-z0-9]"), "_")
                .trim('_')
                .ifEmpty { "unknown" }
            return "onprem_$hostPort"
        }
        val provider = sanitizeStorageSegment(TJLabsFileDownloader.provider, ServerProvider.AWS.value)
        val region = sanitizeStorageSegment(TJLabsFileDownloader.region, ResourceRegion.KOREA.value)
        return "${provider}_${region}"
    }

    private fun buildScopedPrefKey(prefix: String, sectorId: Int, key: String): String {
        return "${prefix}${buildCacheNamespace()}_${sectorId}_$key"
    }

    private fun sanitizeStorageSegment(value: String, fallback: String): String {
        return value
            .replace("/", "_")
            .replace("\\", "_")
            .replace(":", "_")
            .replace("*", "_")
            .replace("?", "_")
            .replace("\"", "_")
            .replace("<", "_")
            .replace(">", "_")
            .replace("|", "_")
            .trim()
            .ifEmpty { fallback }
    }

    private fun fetchTextFromUrl(urlString: String, source: String): String? {
        return try {
            val connection = URL(urlString).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.connect()
            val status = connection.responseCode
            if ((status in 200 until 300) == false) {
                val errorBody = try {
                    connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                } catch (_: Exception) {
                    ""
                }
                TJResourceLogger.d(
                    "(TJLabsResource) fetchTextFromUrl http error // source=$source // url=$urlString // status=$status // error=${errorBody.take(300)}"
                )
                connection.disconnect()
                return null
            }

            val text = connection.inputStream.bufferedReader().use { it.readText() }
            TJResourceLogger.d(
                "(TJLabsResource) fetchTextFromUrl http success // source=$source // status=$status // bytes=${text.length}"
            )
            connection.disconnect()
            text
        } catch (e: Exception) {
            TJResourceLogger.d(
                "(TJLabsResource) fetchTextFromUrl exception // source=$source // url=$urlString // error=${e.localizedMessage}"
            )
            null
        }
    }

    private fun fetchImageFromUrl(key: String, urlString: String): Bitmap? {
        return try {
            val connection = URL(urlString).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.connect()
            val status = connection.responseCode
            if ((status in 200 until 300).not()) {
                TJResourceLogger.d(
                    "(TJLabsResource) fetchImageFromUrl http error // key=$key // url=$urlString // status=$status"
                )
                connection.disconnect()
                return null
            }

            val bitmap = connection.inputStream.use { input ->
                BitmapFactory.decodeStream(input)
            }
            connection.disconnect()

            if (bitmap == null) {
                TJResourceLogger.d("(TJLabsResource) fetchImageFromUrl decode fail // key=$key // url=$urlString")
            } else {
                TJResourceLogger.d("(TJLabsResource) fetchImageFromUrl success // key=$key // size=${bitmap.width}x${bitmap.height}")
            }
            bitmap
        } catch (e: Exception) {
            TJResourceLogger.d(
                "(TJLabsResource) fetchImageFromUrl exception // key=$key // url=$urlString // error=${e.localizedMessage}"
            )
            null
        }
    }

    private fun parseBundleRaw(
        bundleType: ResourceBundleType,
        sectorId: Int,
        meta: SectorBundleMetaOutput,
        raw: String
    ): BundleDataSnapshot? {
        return try {
            val root = JSONObject(raw)

            val buildings = mutableListOf<BuildingOutput>()
            val levelWardsMap = mutableMapOf<String, List<String>>()
            val scaleOffsetMap = mutableMapOf<String, List<Float>>()
            val geofenceMap = mutableMapOf<String, GeofenceData>()
            val levelUnitsMap = mutableMapOf<String, List<UnitData>>()
            val landmarkMap = mutableMapOf<String, Map<String, LandmarkData>>()
            val nodeMap = mutableMapOf<String, Map<Int, NodeData>>()
            val linkMap = mutableMapOf<String, Map<Int, LinkData>>()
            val pathPixelMap = mutableMapOf<String, PathPixelData>()
            val entranceLevelMap = mutableMapOf<String, EntranceData>()
            val entranceItemMap = mutableMapOf<String, EntranceData>()
            val entranceRouteMap = mutableMapOf<String, EntranceRouteData>()
            val imageUrlsByKey = mutableMapOf<String, String>()
            val graphPathUrls = mutableMapOf<String, String>()
            val entranceRouteUrls = mutableMapOf<String, String>()

            val buildingsJson = root.optJSONArray("buildings") ?: JSONArray()
            for (i in 0 until buildingsJson.length()) {
                val buildingObj = buildingsJson.optJSONObject(i) ?: continue
                val buildingId = buildingObj.optInt("id")
                val buildingName = buildingObj.optString("name")
                val levelsJson = buildingObj.optJSONArray("levels") ?: JSONArray()
                val levels = mutableListOf<LevelOutput>()

                for (j in 0 until levelsJson.length()) {
                    val levelObj = levelsJson.optJSONObject(j) ?: continue
                    val levelId = levelObj.optInt("id")
                    val levelName = levelObj.optString("name")
                    val levelKey = "${sectorId}_${buildingName}_${levelName}"
                    val isDebugLevel = levelName.contains("_D")

                    val mapImage = levelObj.optJSONObject("map_image")
                    val imageUrl = mapImage?.optString("url").orEmpty()
                    // 2026-08-06+ 스키마: "floor" | "transition". 이전 스키마엔 필드 없음 → 기본 "floor".
                    val levelType = levelObj.optString("type", "floor").ifBlank { "floor" }
                    levels.add(LevelOutput(id = levelId, name = levelName, image = imageUrl, type = levelType))
                    if (isDebugLevel.not() && imageUrl.isNotBlank()) {
                        imageUrlsByKey[levelKey] = imageUrl
                    }

                    if (mapImage != null) {
                        val sx = mapImage.optFloatOrNull("scale_x")
                        val sy = mapImage.optFloatOrNull("scale_y")
                        val ox = mapImage.optFloatOrNull("offset_x")
                        val oy = mapImage.optFloatOrNull("offset_y")
                        if (sx != null && sy != null && ox != null && oy != null) {
                            scaleOffsetMap[levelKey] = listOf(sx, sy, ox, oy)
                        }
                    }

                    parseGeofence(levelObj.optJSONObject("geofence"))?.let { geofenceMap[levelKey] = it }

                    parseUnits(levelObj.optJSONArray("units"))?.let { levelUnitsMap[levelKey] = it }

                    val wardsJson = levelObj.optJSONArray("wards")
                    if (isDebugLevel.not() && wardsJson != null) {
                        levelWardsMap[levelKey] = parseWards(wardsJson)
                        landmarkMap[levelKey] = parseLandmarks(wardsJson)
                    }

                    if (isDebugLevel.not() && TJResourceLogger.isDebugEnabled()) {
                        val graphsArray = levelObj.optJSONArray("graphs")
                        if (graphsArray == null) {
                            TJResourceLogger.d(
                                "(TJLabsResource) parseBundleRaw graphs missing // levelKey=$levelKey"
                            )
                        } else {
                            TJResourceLogger.d(
                                "(TJLabsResource) parseBundleRaw graphs count // levelKey=$levelKey // count=${graphsArray.length()}"
                            )
                            for (graphIndex in 0 until graphsArray.length()) {
                                val graphItem = graphsArray.optJSONObject(graphIndex) ?: continue
                                val drType = graphItem.optString("dead_reckoning")
                                val pathUrl = graphItem.optJSONObject("path")?.optString("url").orEmpty()
                                val nodeCount = graphItem.optJSONArray("nodes")?.length() ?: 0
                                val linkCount = graphItem.optJSONArray("links")?.length() ?: 0
                                TJResourceLogger.d(
                                    "(TJLabsResource) parseBundleRaw graph item // levelKey=$levelKey // idx=$graphIndex // dr=$drType // nodes=$nodeCount // links=$linkCount // pathUrl=$pathUrl"
                                )
                            }
                        }
                    }

                    val drGraphObj = resolveDrGraphObject(levelObj)
                    if (isDebugLevel.not() && drGraphObj != null) {
                        TJResourceLogger.d(
                            "(TJLabsResource) parseBundleRaw DR graph selected // levelKey=$levelKey // dead_reckoning=${drGraphObj.optString("dead_reckoning")}"
                        )
                        val nodes = parseGraphNodes(drGraphObj.optJSONArray("nodes"))
                        val links = parseGraphLinks(drGraphObj.optJSONArray("links"))
                        val linkGroups = parseGraphLinkGroups(drGraphObj.optJSONArray("link_groups"))

                        if (nodes != null && links != null) {
                            nodeMap[levelKey] = buildNodeDict(nodes)
                            linkMap[levelKey] = buildLinkDict(links, linkGroups ?: emptyList())
                        }

                        val pathUrl = drGraphObj.optJSONObject("path")?.optString("url").orEmpty()
                        // 그래프가 실제로 비어있으면 (nodes=0, links=0) pathUrl 은 서버에 잔존하는
                        // 껍데기일 뿐 실제 CSV 는 없다 — 2026-08-06 스키마부터 순수 floor 의
                        // walkable 데이터가 전이층으로 이동한 경우 이런 상태가 발생. fetch 시도
                        // 자체를 스킵해서 404 → sector 실패 오판을 예방.
                        val hasGraphContent = (nodes?.isNotEmpty() == true) || (links?.isNotEmpty() == true)
                        if (pathUrl.isNotBlank() && hasGraphContent) {
                            graphPathUrls[levelKey] = pathUrl
                            TJResourceLogger.d(
                                "(TJLabsResource) parseBundleRaw DR path mapped // levelKey=$levelKey // url=$pathUrl"
                            )
                        } else if (pathUrl.isNotBlank()) {
                            TJResourceLogger.d(
                                "(TJLabsResource) parseBundleRaw DR path skipped (empty graph) // levelKey=$levelKey // url=$pathUrl"
                            )
                        } else {
                            TJResourceLogger.d(
                                "(TJLabsResource) parseBundleRaw DR path missing // levelKey=$levelKey"
                            )
                        }
                    } else if (isDebugLevel.not()) {
                        TJResourceLogger.d(
                            "(TJLabsResource) parseBundleRaw DR graph not found // levelKey=$levelKey"
                        )
                    }

                    if (isDebugLevel.not()) {
                        val pdrGraphObj = resolvePdrGraphObject(levelObj)
                        val pdrPathUrl = pdrGraphObj?.optJSONObject("path")?.optString("url").orEmpty()
                        val pdrNodeCount = pdrGraphObj?.optJSONArray("nodes")?.length() ?: 0
                        val pdrLinkCount = pdrGraphObj?.optJSONArray("links")?.length() ?: 0
                        val pdrHasGraphContent = pdrNodeCount > 0 || pdrLinkCount > 0
                        if (pdrPathUrl.isNotBlank() && pdrHasGraphContent) {
                            val pdrLevelKey = "${levelKey}${PDR_LEVEL_KEY_SUFFIX}"
                            graphPathUrls[pdrLevelKey] = pdrPathUrl
                            TJResourceLogger.d(
                                "(TJLabsResource) parseBundleRaw PDR path mapped // levelKey=$pdrLevelKey // url=$pdrPathUrl"
                            )
                        } else if (pdrPathUrl.isNotBlank()) {
                            TJResourceLogger.d(
                                "(TJLabsResource) parseBundleRaw PDR path skipped (empty graph) // levelKey=$levelKey // url=$pdrPathUrl"
                            )
                        } else {
                            TJResourceLogger.d(
                                "(TJLabsResource) parseBundleRaw PDR path missing // levelKey=$levelKey"
                            )
                        }
                    }

                    val entrancesJson = levelObj.optJSONArray("entrances") ?: JSONArray()
                    for (k in 0 until entrancesJson.length()) {
                        val entObj = entrancesJson.optJSONObject(k) ?: continue
                        val number = entObj.optInt("number")
                        val entKey = "${levelKey}_${number}"
                        val entData = parseEntranceData(entObj) ?: continue
                        entranceItemMap[entKey] = entData
                        entranceLevelMap[levelKey] = entData

                        val routeUrl = entObj.optString("url")
                        if (routeUrl.isNotBlank()) {
                            entranceRouteUrls[entKey] = routeUrl
                        }
                    }
                }

                buildings.add(
                    BuildingOutput(
                        id = buildingId,
                        name = buildingName,
                        levels = levels
                    )
                )
            }

            val transitions = parseTransitions(root.optJSONArray("transitions"))

            val sectorData = SectorOutput(
                id = root.optInt("id"),
                name = root.optString("name"),
                debug = root.optBoolean("debug"),
                buildings = buildings,
                default_position = parseDefaultPosition(root.optJSONObject("default_position")),
                transitions = transitions
            )

            BundleDataSnapshot(
                bundleType = bundleType,
                versionId = meta.version_id,
                bundleUrl = meta.url,
                sectorData = sectorData,
                levelWardsDataMap = levelWardsMap,
                scaleOffsetDataMap = scaleOffsetMap,
                geofenceDataMap = geofenceMap,
                levelUnitsDataMap = levelUnitsMap,
                landmarkDataMap = landmarkMap,
                nodeDataMap = nodeMap,
                linkDataMap = linkMap,
                pathPixelDataMap = pathPixelMap,
                entranceDataMap = entranceLevelMap,
                entranceItemDataMap = entranceItemMap,
                entranceRouteDataMap = entranceRouteMap,
                imageUrlsByKey = imageUrlsByKey,
                imageDataMap = emptyMap(),
                affineParam = parseAffine(root.optJSONObject("wgs84_transform")),
                graphPathUrlsByKey = graphPathUrls,
                entranceRouteUrlsByKey = entranceRouteUrls,
                warpSectorData = if (bundleType == ResourceBundleType.WARP) parseWarpSector(root) else null,
                venusSectorData = if (bundleType == ResourceBundleType.VENUS) parseVenusSector(root) else null,
                transitions = transitions
            )
        } catch (e: Exception) {
            TJResourceLogger.d("(TJLabsResource) parseBundleRaw failed // type=$bundleType // sectorId=$sectorId // error=${e.localizedMessage}")
            null
        }
    }

    private fun parseAffine(obj: JSONObject?): AffineTransParamOutput? {
        if (obj == null) return null
        return AffineTransParamOutput(
            xx_scale = obj.optDoubleOrDefault("xx_scale"),
            xy_shear = obj.optDoubleOrDefault("xy_shear"),
            x_translation = obj.optDoubleOrDefault("x_translation"),
            yx_shear = obj.optDoubleOrDefault("yx_shear"),
            yy_scale = obj.optDoubleOrDefault("yy_scale"),
            y_translation = obj.optDoubleOrDefault("y_translation"),
            heading_offset = obj.optDoubleOrDefault("heading_offset")
        )
    }

    private fun parseDefaultPosition(obj: JSONObject?): DefaultPositionOutput? {
        if (obj == null) return null
        val buildingObj = obj.optJSONObject("building") ?: return null
        val levelObj = buildingObj.optJSONObject("level") ?: return null
        return DefaultPositionOutput(
            building = DefaultPositionBuildingOutput(
                id = buildingObj.optInt("id"),
                name = buildingObj.optString("name"),
                level = DefaultPositionLevelOutput(
                    id = levelObj.optInt("id"),
                    name = levelObj.optString("name"),
                    x = levelObj.optInt("x"),
                    y = levelObj.optInt("y"),
                    heading = levelObj.optFloatOrDefault("heading")
                )
            )
        )
    }

    private fun parseSimulations(arr: JSONArray?): SimulationBundleOutput? {
        if (arr == null) return null

        val vehicleItems = mutableListOf<SimulationItemOutput>()
        val pdrItems = mutableListOf<SimulationItemOutput>()

        for (i in 0 until arr.length()) {
            val simulationObj = arr.optJSONObject(i) ?: continue
            val isVehicle = when (val raw = simulationObj.opt("is_vehicle")) {
                is Boolean -> raw
                is Number -> raw.toInt() != 0
                is String -> raw.equals("true", ignoreCase = true) || raw == "1"
                else -> false
            }

            val itemsJson = simulationObj.optJSONArray("items") ?: JSONArray()
            for (j in 0 until itemsJson.length()) {
                val itemObj = itemsJson.optJSONObject(j) ?: continue
                val name = itemObj.optString("name").trim()
                val url = itemObj.optString("url").trim()
                if (name.isBlank() || url.isBlank()) continue

                val item = SimulationItemOutput(name = name, url = url)
                if (isVehicle) {
                    vehicleItems.add(item)
                } else {
                    pdrItems.add(item)
                }
            }
        }

        return SimulationBundleOutput(
            vehicle = vehicleItems,
            pdr = pdrItems
        ).also {
            TJResourceLogger.d(
                "(TJLabsResource) parseSimulations // vehicle=${it.vehicle.size} // pdr=${it.pdr.size}"
            )
        }
    }

    private fun parseSimulationsFromRaw(raw: String): SimulationBundleOutput? {
        return try {
            val root = JSONObject(raw)
            parseSimulations(root.optJSONArray("simulations"))
        } catch (e: Exception) {
            TJResourceLogger.d("(TJLabsResource) parseSimulationsFromRaw failed // error=${e.localizedMessage}")
            null
        }
    }

    private fun downloadSimulationFiles(
        application: Application,
        sectorId: Int,
        versionId: String,
        simulationData: SimulationBundleOutput,
        completion: (Boolean) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            var allSuccess = true

            val vehicleResults = simulationData.vehicle.map { item ->
                async {
                    val key = "vehicle_${sanitizeSimulationName(item.name)}"
                    val saved = cacheSimulationJson(
                        application = application,
                        sectorId = sectorId,
                        versionId = versionId,
                        key = key,
                        url = item.url
                    )
                    if (!saved) {
                        TJResourceLogger.d("(TJLabsResource) simulation download fail // type=vehicle // name=${item.name} // url=${item.url}")
                    } else {
                        TJResourceLogger.d("(TJLabsResource) simulation download success // type=vehicle // name=${item.name} // url=${item.url}")
                    }
                    saved
                }
            }

            val pdrResults = simulationData.pdr.map { item ->
                async {
                    val key = "pdr_${sanitizeSimulationName(item.name)}"
                    val saved = cacheSimulationJson(
                        application = application,
                        sectorId = sectorId,
                        versionId = versionId,
                        key = key,
                        url = item.url
                    )
                    if (!saved) {
                        TJResourceLogger.d("(TJLabsResource) simulation download fail // type=pdr // name=${item.name} // url=${item.url}")
                    } else {
                        TJResourceLogger.d("(TJLabsResource) simulation download success // type=pdr // name=${item.name} // url=${item.url}")
                    }
                    saved
                }
            }

            val results = (vehicleResults + pdrResults).awaitAll()
            if (results.any { it.not() }) {
                allSuccess = false
            }

            withContext(Dispatchers.Main) {
                completion(allSuccess)
            }
        }
    }

    private fun cacheSimulationJson(
        application: Application,
        sectorId: Int,
        versionId: String,
        key: String,
        url: String
    ): Boolean {
        val text = getCachedContentIfValid(
            application = application,
            sectorId = sectorId,
            versionId = versionId,
            key = key,
            url = url,
            source = "simulation:$key",
            versionPrefix = PREF_SIM_VERSION_PREFIX,
            urlPrefix = PREF_SIM_URL_PREFIX,
            filePrefix = PREF_SIM_FILE_PREFIX
        ) ?: run {
            val downloaded = fetchTextFromUrl(url, "simulation:$key") ?: return false
            saveSimulationCache(
                application = application,
                sectorId = sectorId,
                versionId = versionId,
                key = key,
                url = url,
                content = downloaded
            )
            downloaded
        }

        return text.isNotBlank()
    }

    private fun getCachedContentIfValid(
        application: Application,
        sectorId: Int,
        versionId: String,
        key: String,
        url: String,
        source: String,
        versionPrefix: String,
        urlPrefix: String,
        filePrefix: String
    ): String? {
        val prefs = application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val versionKey = buildScopedPrefKey(versionPrefix, sectorId, key)
        val urlKey = buildScopedPrefKey(urlPrefix, sectorId, key)
        val fileKey = buildScopedPrefKey(filePrefix, sectorId, key)

        val savedVersion = prefs.getString(versionKey, null)
        val savedUrl = prefs.getString(urlKey, null)
        val savedPath = prefs.getString(fileKey, null)
        if (savedVersion == versionId && savedUrl == url && savedPath.isNullOrBlank().not()) {
            val cachedFile = File(savedPath!!)
            if (cachedFile.exists() && cachedFile.length() > 0) {
                return try {
                    cachedFile.readText().also {
                        TJResourceLogger.d(
                            "(TJLabsResource) simulation cache hit // source=$source // key=$key // version=$versionId // path=${cachedFile.absolutePath} // bytes=${it.length}"
                        )
                    }
                } catch (e: Exception) {
                    TJResourceLogger.d(
                        "(TJLabsResource) simulation cache read fail // source=$source // key=$key // error=${e.localizedMessage}"
                    )
                    null
                }
            }
        }
        return null
    }

    private fun saveSimulationCache(
        application: Application,
        sectorId: Int,
        versionId: String,
        key: String,
        url: String,
        content: String
    ) {
        try {
            val cacheDir = File(application.cacheDir, "$CSV_DIR/${buildSectorCacheFolderName(sectorId)}")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            val fileName = "${sanitizeSimulationName(key)}.json"
            val jsonFile = File(cacheDir, fileName)
            jsonFile.writeText(content)

            val prefs = application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val versionKey = buildScopedPrefKey(PREF_SIM_VERSION_PREFIX, sectorId, key)
            val urlKey = buildScopedPrefKey(PREF_SIM_URL_PREFIX, sectorId, key)
            val fileKey = buildScopedPrefKey(PREF_SIM_FILE_PREFIX, sectorId, key)
            prefs.edit()
                .putString(versionKey, versionId)
                .putString(urlKey, url)
                .putString(fileKey, jsonFile.absolutePath)
                .apply()

            TJResourceLogger.d(
                "(TJLabsResource) simulation cache save // key=$key // version=$versionId // path=${jsonFile.absolutePath} // bytes=${content.length}"
            )
        } catch (e: Exception) {
            TJResourceLogger.d(
                "(TJLabsResource) simulation cache save fail // key=$key // error=${e.localizedMessage}"
            )
        }
    }

    private fun sanitizeSimulationName(name: String): String {
        return name
            .replace("/", "_")
            .replace("\\", "_")
            .replace(":", "_")
            .replace("*", "_")
            .replace("?", "_")
            .replace("\"", "_")
            .replace("<", "_")
            .replace(">", "_")
            .replace("|", "_")
            .trim()
            .ifEmpty { "unknown" }
    }

    private fun resolveDrGraphObject(levelObj: JSONObject): JSONObject? {
        // Current format only: "graphs": [ { dead_reckoning: "DR", ... }, { dead_reckoning: "PDR", ... } ]
        val graphsArray = levelObj.optJSONArray("graphs") ?: return null
        for (i in 0 until graphsArray.length()) {
            val graphObj = graphsArray.optJSONObject(i) ?: continue
            val drType = resolveDeadReckoningType(graphObj)
            if (drType.equals("DR", ignoreCase = true)) {
                return graphObj
            }
        }

        // Fallback: if dead_reckoning field is missing/empty, infer DR by path URL.
        for (i in 0 until graphsArray.length()) {
            val graphObj = graphsArray.optJSONObject(i) ?: continue
            val pathUrl = graphObj.optJSONObject("path")?.optString("url").orEmpty()
            if (pathUrl.contains("/paths/dr/", ignoreCase = true)) {
                return graphObj
            }
        }

        return graphsArray.optJSONObject(0)
    }

    private fun resolvePdrGraphObject(levelObj: JSONObject): JSONObject? {
        val graphsArray = levelObj.optJSONArray("graphs") ?: return null
        for (i in 0 until graphsArray.length()) {
            val graphObj = graphsArray.optJSONObject(i) ?: continue
            val drType = resolveDeadReckoningType(graphObj)
            if (drType.equals("PDR", ignoreCase = true)) {
                return graphObj
            }
        }

        // Fallback: infer PDR by path URL when dead_reckoning is blank.
        for (i in 0 until graphsArray.length()) {
            val graphObj = graphsArray.optJSONObject(i) ?: continue
            val pathUrl = graphObj.optJSONObject("path")?.optString("url").orEmpty()
            if (pathUrl.contains("/paths/pdr/", ignoreCase = true)) {
                return graphObj
            }
        }
        return null
    }


    private fun resolveDeadReckoningType(graphObj: JSONObject): String {
        // Current schema rule:
        // is_vehicle == true  -> DR
        // otherwise           -> PDR
        val isVehicleRaw = when {
            graphObj.has("is_vehicle") -> graphObj.opt("is_vehicle")
            graphObj.has("isVehicle") -> graphObj.opt("isVehicle")
            else -> null
        }
        val isVehicle = when (isVehicleRaw) {
            is Boolean -> isVehicleRaw
            is Number -> isVehicleRaw.toInt() != 0
            is String -> isVehicleRaw.equals("true", ignoreCase = true) || isVehicleRaw == "1"
            else -> null
        }
        return if (isVehicle == true) "DR" else "PDR"
    }

    private fun parseTransitions(arr: JSONArray?): List<TransitionOutput> {
        if (arr == null) return emptyList()
        val result = mutableListOf<TransitionOutput>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val levelRef = parseTransitionLevelRef(obj.optJSONObject("level")) ?: continue
            val lowerRef = parseTransitionLevelRef(obj.optJSONObject("lower_level")) ?: continue
            val upperRef = parseTransitionLevelRef(obj.optJSONObject("upper_level")) ?: continue
            result.add(
                TransitionOutput(
                    id = obj.optInt("id"),
                    name = obj.optString("name"),
                    level = levelRef,
                    lower_level = lowerRef,
                    upper_level = upperRef,
                    points = parseTransitionPoints(obj.optJSONArray("points"))
                )
            )
        }
        return result
    }

    private fun parseTransitionLevelRef(obj: JSONObject?): TransitionLevelRef? {
        if (obj == null) return null
        return TransitionLevelRef(
            id = obj.optInt("id"),
            name = obj.optString("name"),
            building_id = obj.optInt("building_id")
        )
    }

    private fun parseTransitionPoints(arr: JSONArray?): List<TransitionPoint> {
        if (arr == null) return emptyList()
        val result = mutableListOf<TransitionPoint>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            result.add(
                TransitionPoint(
                    id = obj.optInt("id"),
                    lower_x = obj.optInt("lower_x"),
                    lower_y = obj.optInt("lower_y"),
                    upper_x = obj.optInt("upper_x"),
                    upper_y = obj.optInt("upper_y"),
                    transition_type = obj.optString("transition_type"),
                    is_vehicle = obj.optBoolean("is_vehicle")
                )
            )
        }
        return result
    }

    private fun parseWarpSector(root: JSONObject): WarpSectorOutput {
        val buildings = mutableListOf<WarpBuildingOutput>()
        val buildingsJson = root.optJSONArray("buildings") ?: JSONArray()
        for (i in 0 until buildingsJson.length()) {
            val buildingObj = buildingsJson.optJSONObject(i) ?: continue
            val levels = mutableListOf<WarpLevelOutput>()
            val levelsJson = buildingObj.optJSONArray("levels") ?: JSONArray()
            for (j in 0 until levelsJson.length()) {
                val levelObj = levelsJson.optJSONObject(j) ?: continue
                val wards = mutableListOf<WarpWardOutput>()
                val wardsJson = levelObj.optJSONArray("wards") ?: JSONArray()
                for (k in 0 until wardsJson.length()) {
                    val wardObj = wardsJson.optJSONObject(k) ?: continue
                    val contents = mutableListOf<WarpWardContentOutput>()

                    val contentsJson = wardObj.optJSONArray("contents")
                    if (contentsJson != null) {
                        for (contentIndex in 0 until contentsJson.length()) {
                            val contentObj = contentsJson.optJSONObject(contentIndex) ?: continue
                            contents.add(
                                WarpWardContentOutput(
                                    number = contentObj.optInt("number"),
                                    description = contentObj.optString("description"),
                                    url = contentObj.optString("url")
                                )
                            )
                        }
                    }

                    val legacyContentObj = wardObj.optJSONObject("content")
                    if (legacyContentObj != null && contents.isEmpty()) {
                        contents.add(
                            WarpWardContentOutput(
                                number = legacyContentObj.optInt("number"),
                                description = legacyContentObj.optString("description"),
                                url = legacyContentObj.optString("url")
                            )
                        )
                    }
                    wards.add(
                        WarpWardOutput(
                            id = wardObj.optInt("id"),
                            name = wardObj.optString("name"),
                            x = wardObj.optInt("x"),
                            y = wardObj.optInt("y"),
                            rssi = wardObj.optFloatOrNull("rssi")
                                ?: legacyContentObj?.optFloatOrNull("rssi")
                                ?: -99f,
                            contents = contents
                        )
                    )
                }
                levels.add(
                    WarpLevelOutput(
                        id = levelObj.optInt("id"),
                        name = levelObj.optString("name"),
                        map_image = parseMapImage(levelObj.optJSONObject("map_image")),
                        wards = wards
                    )
                )
            }
            buildings.add(
                WarpBuildingOutput(
                    id = buildingObj.optInt("id"),
                    name = buildingObj.optString("name"),
                    levels = levels
                )
            )
        }

        return WarpSectorOutput(
            id = root.optInt("id"),
            name = root.optString("name"),
            operating_system = root.optString("operating_system"),
            buildings = buildings
        )
    }

    private fun parseVenusSector(root: JSONObject): VenusSectorOutput {
        val buildings = mutableListOf<VenusBuildingOutput>()
        val buildingsJson = root.optJSONArray("buildings") ?: JSONArray()
        for (i in 0 until buildingsJson.length()) {
            val buildingObj = buildingsJson.optJSONObject(i) ?: continue
            val levels = mutableListOf<VenusLevelOutput>()
            val levelsJson = buildingObj.optJSONArray("levels") ?: JSONArray()
            for (j in 0 until levelsJson.length()) {
                val levelObj = levelsJson.optJSONObject(j) ?: continue
                val wards = mutableListOf<VenusWardOutput>()
                val wardsJson = levelObj.optJSONArray("wards") ?: JSONArray()
                for (k in 0 until wardsJson.length()) {
                    val wardObj = wardsJson.optJSONObject(k) ?: continue
                    wards.add(
                        VenusWardOutput(
                            id = wardObj.optInt("id"),
                            name = wardObj.optString("name"),
                            x = wardObj.optInt("x"),
                            y = wardObj.optInt("y"),
                            rssi = wardObj.optFloatOrNull("rssi")
                        )
                    )
                }
                levels.add(
                    VenusLevelOutput(
                        id = levelObj.optInt("id"),
                        name = levelObj.optString("name"),
                        map_image = parseMapImage(levelObj.optJSONObject("map_image")),
                        wards = wards
                    )
                )
            }
            buildings.add(
                VenusBuildingOutput(
                    id = buildingObj.optInt("id"),
                    name = buildingObj.optString("name"),
                    levels = levels
                )
            )
        }

        return VenusSectorOutput(
            id = root.optInt("id"),
            name = root.optString("name"),
            operating_system = root.optString("operating_system"),
            buildings = buildings
        )
    }

    private fun parseMapImage(obj: JSONObject?): SectorBundleMapImageOutput? {
        if (obj == null) return null
        return SectorBundleMapImageOutput(
            url = obj.optString("url"),
            image_width = obj.optIntOrNull("image_width"),
            image_height = obj.optIntOrNull("image_height"),
            scale_x = obj.optFloatOrNull("scale_x"),
            scale_y = obj.optFloatOrNull("scale_y"),
            offset_x = obj.optFloatOrNull("offset_x"),
            offset_y = obj.optFloatOrNull("offset_y")
        )
    }

    private fun parseEntranceData(obj: JSONObject): EntranceData? {
        val innermostObj = obj.optJSONObject("innermost_ward") ?: return null
        val outermostObj = obj.optJSONObject("outermost_ward") ?: return null
        val levelObj = innermostObj.optJSONObject("level")

        val innermostLevel = LevelOutput(
            id = levelObj?.optInt("id") ?: 0,
            name = levelObj?.optString("name").orEmpty(),
            image = levelObj?.optString("image").orEmpty()
        )

        val innermost = InnermostWard(
            level = innermostLevel,
            id = innermostObj.optInt("id"),
            name = innermostObj.optString("name"),
            x = innermostObj.optInt("x"),
            y = innermostObj.optInt("y"),
            is_turn = innermostObj.optBoolean("is_turn"),
            headings = parseFloatArray(innermostObj.optJSONArray("headings"))
        )

        val outermost = OutermostWard(
            id = outermostObj.optInt("id"),
            name = outermostObj.optString("name")
        )

        return EntranceData(
            number = obj.optInt("number"),
            velocityScale = obj.optFloatOrDefault("scale"),
            innermost_ward = innermost,
            outermost_ward = outermost
        )
    }

    private fun parseGeofence(obj: JSONObject?): GeofenceData? {
        if (obj == null) return null
        return GeofenceData(
            entrance_area = parseIntMatrix(obj.optJSONArray("entrance_area")),
            entrance_matching_area = parseIntMatrix(obj.optJSONArray("entrance_matching_area")),
            level_change_area = parseIntMatrix(obj.optJSONArray("level_change_area"))
        )
    }

    private fun parseUnits(arr: JSONArray?): List<UnitData>? {
        if (arr == null) return null
        val result = mutableListOf<UnitData>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            result.add(
                UnitData(
                    id = obj.optInt("id"),
                    category = parseCategory(obj.opt("category")),
                    name = obj.optString("name"),
                    is_restricted = obj.optBoolean("is_restricted"),
                    x = obj.optFloatOrDefault("x"),
                    y = obj.optFloatOrDefault("y"),
                    parking_space_code = obj.optString("parking_space_code")
                )
            )
        }
        return result
    }

    private fun parseCategory(raw: Any?): CategoryData {
        var id = 0
        var name = ""
        var keyRaw = ""

        when (raw) {
            is JSONObject -> {
                id = raw.optInt("id", 0)
                name = raw.optString("name")
                keyRaw = raw.optString("key")
                if (keyRaw.isBlank()) keyRaw = raw.optString("category")
                if (keyRaw.isBlank()) keyRaw = raw.optString("value")
                if (keyRaw.isBlank()) keyRaw = raw.optString("code")
                if (name.isBlank()) name = keyRaw
            }
            is String -> {
                name = raw
                keyRaw = raw
            }
            is Number -> {
                name = raw.toString()
                keyRaw = raw.toString()
            }
            else -> {
                name = ""
                keyRaw = ""
            }
        }

        val key = Category.fromRaw(if (keyRaw.isBlank()) name else keyRaw)
        if (key == Category.UNKNOWN && (name.isNotBlank() || keyRaw.isNotBlank())) {
            TJResourceLogger.d("(TJLabsResource) unknown category // raw=$raw")
        }

        return CategoryData(
            id = id,
            name = name,
            key = key
        )
    }

    private fun parseWards(arr: JSONArray): List<String> {
        val result = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val name = obj.optString("name")
            if (name.isNotBlank()) {
                result.add(name)
            }
        }
        return result
    }

    private fun parseLandmarks(arr: JSONArray): Map<String, LandmarkData> {
        val result = mutableMapOf<String, LandmarkData>()
        for (i in 0 until arr.length()) {
            val wardObj = arr.optJSONObject(i) ?: continue
            val wardName = wardObj.optString("name")
            if (wardName.isBlank()) continue

            val rfLandmarks = wardObj.optJSONArray("rf_landmarks") ?: JSONArray()
            for (j in 0 until rfLandmarks.length()) {
                val info = rfLandmarks.optJSONObject(j) ?: continue
                val links = info.optJSONArray("links") ?: JSONArray()
                val matchedLinks = mutableListOf<Int>()
                for (k in 0 until links.length()) {
                    val link = links.optJSONObject(k) ?: continue
                    matchedLinks.add(link.optInt("number"))
                }

                val peak = PeakData(
                    x = info.optInt("x"),
                    y = info.optInt("y"),
                    rssi = info.optFloatOrDefault("rssi"),
                    matched_links = matchedLinks
                )

                val existing = result[wardName]
                if (existing == null) {
                    result[wardName] = LandmarkData(
                        ward_id = wardName,
                        peaks = listOf(peak)
                    )
                } else {
                    result[wardName] = existing.copy(peaks = existing.peaks + peak)
                }
            }
        }
        return result
    }

    private fun parseGraphNodes(arr: JSONArray?): List<GraphLevelNode>? {
        if (arr == null) return null
        val result = mutableListOf<GraphLevelNode>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            result.add(
                GraphLevelNode(
                    id = obj.optInt("id"),
                    number = obj.optInt("number"),
                    x = obj.optInt("x"),
                    y = obj.optInt("y"),
                    available_in_headings = parseIntArray(obj.optJSONArray("available_in_headings")),
                    available_out_headings = parseIntArray(obj.optJSONArray("available_out_headings")),
                    connected_links = parseItemIdNumberArray(obj.optJSONArray("connected_links")),
                    connected_nodes = parseItemIdNumberArray(obj.optJSONArray("connected_nodes"))
                )
            )
        }
        return result
    }

    private fun parseGraphLinks(arr: JSONArray?): List<GraphLevelLink>? {
        if (arr == null) return null
        val result = mutableListOf<GraphLevelLink>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val nodeA = obj.optJSONObject("node_a")
            val nodeB = obj.optJSONObject("node_b")
            result.add(
                GraphLevelLink(
                    id = obj.optInt("id"),
                    number = obj.optInt("number"),
                    node_a = ItemIdNumber(nodeA?.optInt("id") ?: 0, nodeA?.optInt("number") ?: 0),
                    node_b = ItemIdNumber(nodeB?.optInt("id") ?: 0, nodeB?.optInt("number") ?: 0),
                    available_headings = parseIntArray(obj.optJSONArray("available_headings")),
                    distance = obj.optInt("distance")
                )
            )
        }
        return result
    }

    private fun parseGraphLinkGroups(arr: JSONArray?): List<GraphLevelLinkGroup>? {
        if (arr == null) return null
        val result = mutableListOf<GraphLevelLinkGroup>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            result.add(
                GraphLevelLinkGroup(
                    id = obj.optInt("id"),
                    number = obj.optInt("number"),
                    links = parseItemIdNumberArray(obj.optJSONArray("links"))
                )
            )
        }
        return result
    }

    private fun buildNodeDict(nodes: List<GraphLevelNode>): Map<Int, NodeData> {
        val result = mutableMapOf<Int, NodeData>()
        for (node in nodes) {
            val inSet = node.available_in_headings.toSet()
            val outSet = node.available_out_headings.toSet()
            val endOnly = inSet.subtract(outSet)

            val merged = LinkedHashSet<Int>()
            merged.addAll(node.available_in_headings)
            merged.addAll(node.available_out_headings)

            val directions = merged.map { heading ->
                NodeDirection(
                    heading = heading.toFloat(),
                    is_end = endOnly.contains(heading)
                )
            }

            result[node.number] = NodeData(
                number = node.number,
                coords = listOf(node.x.toFloat(), node.y.toFloat()),
                directions = directions,
                connected_nodes = node.connected_nodes.map { it.number },
                connected_links = node.connected_links.map { it.number }
            )
        }
        return result
    }

    private fun buildLinkDict(
        links: List<GraphLevelLink>,
        linkGroups: List<GraphLevelLinkGroup>
    ): Map<Int, LinkData> {
        val linkIdToGroup = mutableMapOf<Int, Int>()
        for (group in linkGroups) {
            for (item in group.links) {
                linkIdToGroup[item.number] = group.number
            }
        }

        val result = mutableMapOf<Int, LinkData>()
        for (link in links) {
            result[link.number] = LinkData(
                number = link.number,
                start_node = link.node_a.number,
                end_node = link.node_b.number,
                distance = link.distance.toFloat(),
                included_heading = link.available_headings.map { it.toFloat() },
                group_id = linkIdToGroup[link.number] ?: -1
            )
        }
        return result
    }

    private fun parsePathPixelData(data: String): PathPixelData {
        val roadX = mutableListOf<Float>()
        val roadY = mutableListOf<Float>()
        val roadScale = mutableListOf<Float>()
        val roadHeading = mutableListOf<String>()

        val lines = data.lines()
        val bracketRegex = Regex("\\[[^\\]]*\\]")

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            if (line.contains("encoding=")) continue

            val match = bracketRegex.find(line)
            val headingValues = if (match != null) {
                match.value
                    .removePrefix("[")
                    .removeSuffix("]")
                    .split(",")
                    .mapNotNull { it.trim().toDoubleOrNull() }
                    .joinToString(",") { it.toString() }
            } else {
                ""
            }

            val cleaned = if (match != null) line.replace(match.value, "") else line
            val parts = cleaned.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            if (parts.size < 4) continue

            val xVal = parts[0].toFloatOrNull()
            val yVal = parts[1].toFloatOrNull()
            val headingRaw = if (headingValues.isNotEmpty()) headingValues else parts[2]
            val scaleVal = parts[3].toFloatOrNull()

            if (xVal == null || yVal == null || scaleVal == null) continue

            roadX.add(xVal)
            roadY.add(yVal)
            roadScale.add(scaleVal)
            roadHeading.add(headingRaw)
        }

        val road = listOf(roadX, roadY)
        val minX = roadX.minOrNull() ?: 0f
        val minY = roadY.minOrNull() ?: 0f
        val maxX = roadX.maxOrNull() ?: 0f
        val maxY = roadY.maxOrNull() ?: 0f
        val roadMinMax = listOf(minX, minY, maxX, maxY)

        return PathPixelData(
            road = road,
            roadMinMax = roadMinMax,
            roadScale = roadScale,
            roadHeading = roadHeading
        )
    }

    private fun parseEntranceRouteData(data: String): EntranceRouteData {
        val levels = mutableListOf<String>()
        val routes = mutableListOf<List<Float>>()

        data.lines().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            val parts = line.split(",")
            if (parts.size < 4) return@forEach

            val x = parts[1].trim().toFloatOrNull() ?: return@forEach
            val y = parts[2].trim().toFloatOrNull() ?: return@forEach
            val heading = parts[3].trim().toFloatOrNull() ?: return@forEach

            levels.add(parts[0].trim())
            routes.add(listOf(x, y, heading))
        }

        return EntranceRouteData(levels, routes)
    }

    private fun parseItemIdNumberArray(arr: JSONArray?): List<ItemIdNumber> {
        if (arr == null) return emptyList()
        val result = mutableListOf<ItemIdNumber>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            result.add(ItemIdNumber(id = obj.optInt("id"), number = obj.optInt("number")))
        }
        return result
    }

    private fun parseIntArray(arr: JSONArray?): List<Int> {
        if (arr == null) return emptyList()
        val result = mutableListOf<Int>()
        for (i in 0 until arr.length()) {
            val value = arr.opt(i)
            when (value) {
                is Number -> result.add(value.toInt())
                is String -> value.toIntOrNull()?.let { result.add(it) }
            }
        }
        return result
    }

    private fun parseFloatArray(arr: JSONArray?): List<Float> {
        if (arr == null) return emptyList()
        val result = mutableListOf<Float>()
        for (i in 0 until arr.length()) {
            val value = arr.opt(i)
            when (value) {
                is Number -> result.add(value.toFloat())
                is String -> value.toFloatOrNull()?.let { result.add(it) }
            }
        }
        return result
    }

    private fun parseIntMatrix(arr: JSONArray?): List<List<Int>> {
        if (arr == null) return emptyList()
        val result = mutableListOf<List<Int>>()
        for (i in 0 until arr.length()) {
            val row = arr.optJSONArray(i) ?: continue
            result.add(parseIntArray(row))
        }
        return result
    }

    private fun JSONObject.optFloatOrDefault(key: String, defaultValue: Float = 0f): Float {
        val value = opt(key)
        return when (value) {
            is Number -> value.toFloat()
            is String -> value.toFloatOrNull() ?: defaultValue
            else -> defaultValue
        }
    }

    private fun JSONObject.optDoubleOrDefault(key: String, defaultValue: Double = 0.0): Double {
        val value = opt(key)
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: defaultValue
            else -> defaultValue
        }
    }

    private fun JSONObject.optFloatOrNull(key: String): Float? {
        if (has(key) == false || isNull(key)) return null
        val value = opt(key)
        return when (value) {
            is Number -> value.toFloat()
            is String -> value.toFloatOrNull()
            else -> null
        }
    }

    private fun JSONObject.optIntOrNull(key: String): Int? {
        if (has(key) == false || isNull(key)) return null
        val value = opt(key)
        return when (value) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }
    }

    private fun nowMs(): Long = System.currentTimeMillis()

    private fun elapsedMs(startMs: Long): Long = nowMs() - startMs
}

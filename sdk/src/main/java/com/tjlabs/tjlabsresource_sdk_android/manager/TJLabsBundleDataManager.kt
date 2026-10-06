package com.tjlabs.tjlabsresource_sdk_android.manager

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.tjlabs.tjlabsresource_sdk_android.AffineTransParamOutput
import com.tjlabs.tjlabsresource_sdk_android.BuildingOutput
import com.tjlabs.tjlabsresource_sdk_android.ParkingMatch
import com.tjlabs.tjlabsresource_sdk_android.ParkingMatchesData
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
import com.tjlabs.tjlabsresource_sdk_android.ResourceLoadStage
import com.tjlabs.tjlabsresource_sdk_android.ResourceLoadStageFailure
import com.tjlabs.tjlabsresource_sdk_android.util.TJResourceLogger
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
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
import java.util.zip.ZipFile

internal data class BundleDataSnapshot(
    val bundleType: ResourceBundleType,
    val versionId: String,
    val bundleUrl: String,
    // 2026-09-28+ JUPITER: 로컬에 저장된 zip 경로. 아카이브 원본을 남겨두어 재-extract fallback 에 사용.
    // WARP/VENUS 나 legacy JSON 응답의 경우 null.
    val bundleZipPath: String? = null,
    // 2026-09-28+ JUPITER: zip 을 실제로 압축 해제한 디렉토리. path CSV · parking_matches JSON · bundle.json
    // 등 모든 entry 가 이 경로 아래에 원본 이름 그대로 존재한다 (예:
    // `<assetsRoot>/sectors/111/assets/levels/128/paths/dr.csv`). enrichCsvData 는 여기서 개별
    // 자원 파일을 직접 read 한다. legacy 는 null.
    val bundleAssetsRoot: String? = null,
    val sectorData: SectorOutput,
    val levelWardsDataMap: Map<String, List<String>>,
    val scaleOffsetDataMap: Map<String, List<Float>>,
    val geofenceDataMap: Map<String, GeofenceData>,
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
    // 2026-09-28+ JUPITER: 값은 zip 내부 경로(`sectors/{id}/assets/levels/{level_id}/paths/{dr|pdr}.csv`).
    // WARP/VENUS 는 절대 URL 유지 (legacy 스키마). enrichCsvData 가 소스 타입을 자동 판별한다.
    val graphPathUrlsByKey: Map<String, String>,
    val entranceRouteUrlsByKey: Map<String, String>,
    // 2026-09-28+: 값은 zip 내부 경로(`sectors/{id}/assets/levels/{level_id}/parking_matches.json`).
    // 파일 미업로드 층은 두 map 모두 key 부재 (null 대신 absence).
    val parkingMatchesUrlsByLevelId: Map<Int, String> = emptyMap(),
    val parkingMatchesDataByLevelId: Map<Int, ParkingMatchesData> = emptyMap(),
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
        private const val PREF_PARKING_MATCHES_VERSION_PREFIX = "parking_matches_version_"
        private const val PREF_PARKING_MATCHES_URL_PREFIX = "parking_matches_url_"
        private const val PREF_PARKING_MATCHES_FILE_PREFIX = "parking_matches_file_"
        // 2026-09-28+ 흐름에서는 simulation JSON 도 sector 번들 zip 안에 포함된다 —
        // [extractSimulationsFromZip] 이 zip entry 를 로컬 파일로 흘려보내므로 별도 per-file
        // pref 캐시는 필요 없다. (기존 PREF_SIM_* prefix 는 제거됨. 이전 앱 캐시에 남아 있어도
        // [clearCache] 의 sectorToken 매치가 함께 지운다.)
        private const val PREF_IMAGE_VERSION_PREFIX = "image_version_"
        private const val PREF_IMAGE_URL_PREFIX = "image_url_"
        private const val PREF_IMAGE_FILE_PREFIX = "image_file_"
        private const val PDR_LEVEL_KEY_SUFFIX = "_PDR"
        private const val IMG_DIR = "tj_bundle_img"
        // Path CSV 바이트 레벨 파서용 상수 (iOS `PathCSV` enum 매핑).
        private const val LF: Byte = 0x0A
        private const val CR: Byte = 0x0D
        private const val COMMA: Byte = 0x2C
        private const val OPEN_BRACKET: Byte = 0x5B
        private const val CLOSE_BRACKET: Byte = 0x5D
        private val ENCODING_MARKER: ByteArray = "encoding=".toByteArray(Charsets.UTF_8)

        // 벤치마크 목적으로 유지되는 v1 (2026-09-10) 서버 버전. `Freezed` 상태라 이미 만들어져 있던
        // sector×OS 조합만 응답하지만, v1 vs v2 다운로드 크기/시간 비교에 유효하다.
        private const val LEGACY_JUPITER_SECTOR_BUNDLE_SERVER_VERSION = "2026-09-10"

        // Meta / raw bundle 요청이 502/503/504/IOException 을 받았을 때 재시도까지의 지연.
        // proxy 뒤 upstream rebuild 가 대개 수초 안에 끝나므로 짧은 backoff 로 충분.
        private const val META_RETRY_BACKOFF_MS = 1200L
        private const val RAW_RETRY_BACKOFF_MS = 1500L

        private val bitmapMemoryCache: MutableMap<String, Bitmap> = mutableMapOf()

        // ── Benchmark infrastructure (TJLabsResourceBenchmark 가 토글) ───────────────────
        // production 흐름에는 영향 없음 — flag 가 false 이면 어느 함수도 분기 안 함.
        //  * [benchmarkCsvParserMode] : SINGLE_PASS (default) → 현재 production 파서
        //    LEGACY_REGEX → Phase 4 이전의 regex 기반 파서 ([parsePathPixelDataLegacy]) 호출.
        //  * [benchmarkTimingEnabled] : true 면 extract/decode/parse 단계마다 nanoTime 차이를
        //    [benchmarkStageTimings] 에 누적. iteration 간 reset 필요.
        @Volatile internal var benchmarkCsvParserMode: com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceBenchmark.CsvParserMode =
            com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceBenchmark.CsvParserMode.SINGLE_PASS
        @Volatile internal var benchmarkTimingEnabled: Boolean = false
        internal val benchmarkStageTimings = com.tjlabs.tjlabsresource_sdk_android.BenchmarkStageTimingCollector()
    }

    private fun buildSnapshotCacheKey(
        bundleType: ResourceBundleType,
        sectorId: Int,
        useLegacyEndpoint: Boolean = false
    ): String {
        val suffix = if (useLegacyEndpoint) "_v1" else ""
        return "${buildCacheNamespace()}_${bundleType.name}_${sectorId}${suffix}"
    }

    /**
     * 이미 로드된 sector 스냅샷을 companion memory 캐시에서 조회. 네트워크 · 디스크 IO 없음.
     * VM SDK 처럼 상위 계층이 먼저 loadBundle 을 성공시킨 뒤, 하위 계층 (JupiterCalcManager 등) 이
     * 자기 delegate 로 같은 스냅샷을 즉시 re-emit 하려는 경우에 사용.
     * bundleCache 는 companion 이라 [TJLabsBundleDataManager] 인스턴스 간 공유되므로,
     * 상위 계층의 [TJLabsResourceManager] 인스턴스가 채운 값도 하위 인스턴스에서 볼 수 있다.
     */
    internal fun getCachedSnapshot(bundleType: ResourceBundleType, sectorId: Int): BundleDataSnapshot? {
        return bundleCache[buildSnapshotCacheKey(bundleType, sectorId)]
    }

    /**
     * 단일 층 이미지를 on-demand 로 fetch (또는 캐시 히트 시 즉시 반환).
     * [ImageLoadPolicy.NONE] / [ImageLoadPolicy.DEFAULT_ONLY] 로 초기 로드한 뒤 사용자가 층 이동 시
     * [TJLabsResourceManager.loadLevelImage] 를 통해 이 함수가 호출된다.
     *
     * bundleCache 에서 sector 스냅샷을 찾아 해당 levelKey 의 image URL 로 다운로드하고,
     * 성공 시 `bundleCache[cacheKey].imageDataMap` 을 in-place 로 갱신 (다음 loadResource 에서
     * emitSnapshot 이 이 값을 그대로 사용).
     *
     * @return 성공 시 (bitmap, true) — bitmap 은 이미 캐시된 인스턴스일 수 있음.
     *         실패 시 (null, false) — snapshot 미보유 · levelKey 매핑 실패 · 네트워크 오류.
     */
    internal fun loadSingleImage(
        application: Application,
        bundleType: ResourceBundleType,
        sectorId: Int,
        levelKey: String,
    ): Pair<Bitmap?, Boolean> {
        val cacheKey = buildSnapshotCacheKey(bundleType, sectorId)
        val snapshot = bundleCache[cacheKey] ?: run {
            TJResourceLogger.w("(TJLabsResource) loadSingleImage snapshot missing // type=$bundleType // sectorId=$sectorId // levelKey=$levelKey")
            return null to false
        }
        // 이미 mem cache 에 있으면 즉시 반환
        snapshot.imageDataMap[levelKey]?.let { return it to true }
        val url = snapshot.imageUrlsByKey[levelKey] ?: run {
            TJResourceLogger.w("(TJLabsResource) loadSingleImage url missing // sectorId=$sectorId // levelKey=$levelKey")
            return null to false
        }
        val bitmap = loadImageWithCache(application, sectorId, snapshot.versionId, levelKey, url)
            ?: return null to false
        // snapshot 의 imageDataMap 을 갱신한 copy 로 bundleCache 교체.
        // Map 이 immutable 타입으로 노출되므로 in-place 수정은 안전하지 않음 → copy() 사용.
        val newImageMap = snapshot.imageDataMap.toMutableMap().apply { put(levelKey, bitmap) }
        bundleCache[cacheKey] = snapshot.copy(imageDataMap = newImageMap)
        return bitmap to true
    }

    /**
     * sector 안에서 아직 다운로드되지 않은 이미지 전부를 병렬 fetch. 이미 있는 것은 skip.
     * [TJLabsResourceManager.prefetchRemainingImages] 의 backend.
     * @return (성공 개수, 실패 개수)
     */
    internal suspend fun prefetchMissingImages(
        application: Application,
        bundleType: ResourceBundleType,
        sectorId: Int,
    ): Pair<Int, Int> {
        val cacheKey = buildSnapshotCacheKey(bundleType, sectorId)
        val snapshot = bundleCache[cacheKey] ?: run {
            TJResourceLogger.w("(TJLabsResource) prefetchMissingImages snapshot missing // type=$bundleType // sectorId=$sectorId")
            return 0 to 0
        }
        val missing = snapshot.imageUrlsByKey.filterKeys { snapshot.imageDataMap[it] == null }
        if (missing.isEmpty()) {
            TJResourceLogger.d("(TJLabsResource) prefetchMissingImages nothing to do // sectorId=$sectorId")
            return 0 to 0
        }
        TJResourceLogger.d("(TJLabsResource) prefetchMissingImages start // sectorId=$sectorId // count=${missing.size}")
        val startMs = nowMs()
        val results = withContext(Dispatchers.IO) {
            missing.map { (key, url) ->
                async {
                    val bmp = loadImageWithCache(application, sectorId, snapshot.versionId, key, url)
                    key to bmp
                }
            }.awaitAll()
        }
        var loaded = 0
        var failed = 0
        val newImageMap = snapshot.imageDataMap.toMutableMap()
        for ((key, bmp) in results) {
            if (bmp != null) {
                newImageMap[key] = bmp
                loaded++
            } else {
                failed++
            }
        }
        bundleCache[cacheKey] = snapshot.copy(imageDataMap = newImageMap)
        TJResourceLogger.i(
            "(TJLabsResource) prefetchMissingImages done // sectorId=$sectorId // loaded=$loaded // failed=$failed // elapsedMs=${elapsedMs(startMs)}"
        )
        return loaded to failed
    }

    /**
     * iOS parity — `resetMultiState` 매핑.
     *
     * **메모리 bundleCache 만** 초기화 (디스크 조합 zip / raw / csv / prefs 유지).
     * Multi 로더가 `loadResources` 진입 시 호출해 "**새 로드 = 전역 교체**" 정책을 구현한다.
     *   - iOS 는 멀티 로드 시 이전 조합의 섹터 데이터를 전부 제거 (`resetMultiState`),
     *     요청 섹터만 다시 채운다.
     *   - Android bundleCache 는 섹터별 key 로 companion static — 다른 조합 로드하면
     *     이전 섹터 데이터가 남아 메모리 누적 가능. 이 함수로 멀티 진입 시 전역 교체.
     *
     * 디스크 캐시 (CSV / 이미지 / 조합 zip LRU) 는 유지 — Multi 로더가 조합 캐시 LRU 를
     * 별도 관리하므로 메모리만 리셋해도 충분. 단일 섹터 흐름 (`loadJupiterResource`) 은
     * sectorId 별 교체가 자동 (`bundleCache[key] = enriched` 로 덮어씀) 이라 이 함수를 호출 X.
     */
    internal fun resetMultiBundleCache() {
        bundleCache.clear()
        bitmapMemoryCache.clear()
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
     *   network_meta+memory_cache, network_meta+disk_raw, network_meta+network_raw,
     *   meta_fail, network_meta+raw_fail, network_meta+parse_fail
     * v1.1.14 부터 memory_fastpath / pref_meta+* 는 발생하지 않음
     * ([getSavedBundleMetaIfFresh] 가 항상 null → 항상 meta API 호출 후 version 비교).
     * TJLabsResourceManager 는 이 문자열로 `fromCache` 를 판정 (network_raw 포함 여부).
     */
    /**
     * @param useLegacyEndpoint true 면 v1 (2026-09-10 JSON) endpoint 강제 호출.
     *  벤치마크/비교 전용 — 메모리·디스크 캐시 lookup 을 완전 스킵하고 매 호출마다 fresh fetch 한다.
     *  이 flag 는 JUPITER 에서만 의미가 있고, VENUS/WARP 는 무시 (해당 flow 는 legacy 를 그대로 씀).
     */
    fun loadBundle(
        application: Application,
        bundleType: ResourceBundleType,
        sectorId: Int,
        imageLoadPolicy: com.tjlabs.tjlabsresource_sdk_android.ImageLoadPolicy =
            com.tjlabs.tjlabsresource_sdk_android.ImageLoadPolicy.ALL,
        useLegacyEndpoint: Boolean = false,
        // 2026-10 iOS 4-2 오프라인 폴백 규칙 포팅. 기본 false — meta HTTP 또는 raw zip 다운로드가
        // 실패해도 디스크의 저장된 bundle zip 이 있으면 그걸로 재조립해 소비자에게 전달한다
        // (versionVerified=false). true 면 폴백 생략하고 원 실패를 그대로 전달 — 운영자가 강제 갱신
        // 의도로 호출하는 경로 (예: 설정 화면의 "리소스 다시 받기") 용.
        forceUpdate: Boolean = false,
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
            // TJResourceLogger.setDebugOption(true) 로 켠 상태에서만 emit.
            // 릴리즈 빌드/기본 상태에서는 조용히 무시되어 소비자 앱 logcat 을 오염시키지 않음.
            if (!TJResourceLogger.isDebugEnabled()) return

            val total = elapsedMs(loadStartMs)

            // iOS `[TJLabsResourceManager] (loadResources timing)` 로그와 포맷 매칭.
            // 필터: `adb logcat -s TJLabsResourceManager:I` (INFO 만 → 하단 debug 로그 노이즈 제거)
            val iosPrefix = "(loadResources timing)"
            val isMemoryHit = source.contains("memory_cache") || source == "memory_fastpath"
            val isDiskHit = source.contains("disk_raw")
            val isCached = isMemoryHit || isDiskHit
            val bypassLocalCache = source.startsWith("network_meta")

            // [1/3] metadata — v1.1.14 는 항상 서버 meta 조회 → bypassLocalCache=true 가 기본.
            val metaVal = if (metaMs < 0L) 0.0 else metaMs.toDouble()
            TJResourceLogger.i(
                "$iosPrefix : [1/3] sector bundle metadata fetch = %.1fms // sectorId = $sectorId, bypassLocalCache = $bypassLocalCache"
                    .format(metaVal)
            )

            // [2/3] bundle json (download+decode) — 캐시 hit 시 0ms.
            val jsonMs = when {
                source.contains("network_raw") -> (rawFetchMs.coerceAtLeast(0) + parseMs.coerceAtLeast(0)).toDouble()
                source.contains("disk_raw") -> parseMs.coerceAtLeast(0).toDouble()
                else -> 0.0
            }
            TJResourceLogger.i(
                "$iosPrefix : [2/3] sector bundle json download+decode = %.1fms // sectorId = $sectorId, isCached = $isCached"
                    .format(jsonMs)
            )

            // [3/3] organize — Android 는 sync/async 를 분리 계측하지 않아 sync 는 0.0ms 로 표기,
            //   async 만 enrichMs 로 잡힘. memory_cache 경로는 organize 전체 스킵.
            val organizeAsyncMs = if (enrichMs < 0L) 0.0 else enrichMs.toDouble()
            val organizeSyncMs = 0.0
            val organizeTotalMs = organizeSyncMs + organizeAsyncMs
            TJResourceLogger.i(
                "$iosPrefix :   organize[a] sync in-memory build + dispatch = %.1fms // sectorId = $sectorId"
                    .format(organizeSyncMs)
            )
            TJResourceLogger.i(
                "$iosPrefix :   organize[b] async resource loads (DispatchGroup wait) = %.1fms // sectorId = $sectorId"
                    .format(organizeAsyncMs)
            )
            TJResourceLogger.i(
                "$iosPrefix : [3/3] organize sector bundle = %.1fms // sectorId = $sectorId"
                    .format(organizeTotalMs)
            )

            // TOTAL — metadata + json + organize (loadBundle 진입~종료)
            TJResourceLogger.i(
                "$iosPrefix : TOTAL = %.1fms (metadata %.1fms + bundle %.1fms + organize %.1fms) // sectorId = $sectorId, isCached = $isCached, success = $success"
                    .format(total.toDouble(), metaVal, jsonMs, organizeTotalMs)
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
            val cacheKey = buildSnapshotCacheKey(bundleType, sectorId, useLegacyEndpoint)
            // v1 (useLegacyEndpoint=true) 는 벤치마크 fresh 경로 — memory cache 도 우회.
            val cached = if (useLegacyEndpoint) null else bundleCache[cacheKey]
            if (cached != null && cached.versionId == meta.version_id) {
                applyCounts(cached)
                finish(true, "(TJLabsResource) Success : use cached bundle", cached, "${metaSource}+memory_cache")
                return
            }

            // 파싱/네트워크 IO 는 IO 스레드에서 수행, 메인 스레드 점유 제거.
            CoroutineScope(Dispatchers.IO).launch {
                val isV2ZipFlow = bundleType == ResourceBundleType.JUPITER && !useLegacyEndpoint

                // v2 zip 흐름 전용: zip 을 로컬 디렉토리에 풀어 개별 자원 파일로 노출. 실패 시 null →
                // enrichCsvData 가 zipPath fallback (또는 URL fetch) 로 진행한다.
                //
                // @param forceReExtract fresh download 이후에는 zip 내용이 새 version 으로 덮어써졌으므로
                //   기존 extract 는 무조건 stale — marker 존재 여부와 무관하게 다시 풀어야 한다.
                //   disk-cache hit (같은 version 이 확정된 zip) 경우에만 marker 존재 시 재사용 최적화가 유효.
                fun ensureExtractedAssetsRoot(zipFile: File, forceReExtract: Boolean = false): File? {
                    if (!isV2ZipFlow) return null
                    val extractRoot = File(application.cacheDir, "$CSV_DIR/${buildSectorCacheFolderName(sectorId)}/extracted")
                    if (!forceReExtract) {
                        val marker = File(extractRoot, "sectors/$sectorId/bundle.json")
                        if (marker.exists() && marker.length() > 0) return extractRoot
                    }
                    return extractBundleZipToDisk(application, sectorId, zipFile)
                }

                fun bundleZipPathIfJupiter(file: File): String? =
                    // v1 응답은 zip 이 아닌 JSON — zip entry read 를 하지 않도록 null.
                    if (isV2ZipFlow) file.absolutePath else null

                // ── 1) 디스크 캐시: version_id · url 이 일치하면 그대로 재사용. v1 은 스킵.
                val cachedFile = if (useLegacyEndpoint) null else loadBundleRawFromCache(application, bundleType, sectorId, meta)
                if (cachedFile != null) {
                    val parseCacheStartMs = nowMs()
                    val extractedRoot = ensureExtractedAssetsRoot(cachedFile)
                    val bundleJson = readBundleJsonFromFile(bundleType, sectorId, cachedFile, useLegacyEndpoint, extractedRoot)
                    val parsedFromCache = bundleJson?.let {
                        parseBundleRaw(
                            bundleType, sectorId, meta, it,
                            bundleZipPathIfJupiter(cachedFile),
                            extractedRoot?.absolutePath
                        )
                    }
                    parseMs = elapsedMs(parseCacheStartMs)
                    if (parsedFromCache != null) {
                        val enrichStartMs = nowMs()
                        enrichCsvData(application, sectorId, parsedFromCache, imageLoadPolicy) { csvSuccess, enriched ->
                            enrichMs = elapsedMs(enrichStartMs)
                            bundleCache[cacheKey] = enriched
                            applyCounts(enriched)
                            val statusLabel = if (csvSuccess) "Success" else "Partial-fail(DR path CSV missing)"
                            finish(csvSuccess, "(TJLabsResource) $statusLabel : use cached bundle(raw)", enriched, "${metaSource}+disk_raw")
                        }
                        return@launch
                    }
                }

                // ── 2) 캐시 미스: URL 로부터 zip/JSON 을 스트리밍 다운로드해 디스크 캐시에 저장.
                val rawStartMs = nowMs()
                downloadBundleFile(application, bundleType, sectorId, meta.url, useLegacyEndpoint) { rawStatus, rawMsg, downloadedFile ->
                    rawFetchMs = elapsedMs(rawStartMs)
                    if ((rawStatus in 200 until 300) == false || downloadedFile == null) {
                        // iOS 4-2 오프라인 폴백: meta 는 성공했지만 raw 다운로드가 실패 (네트워크 끊김,
                        // 5xx, 서버 bundle 삭제 등). forceUpdate=false 이고 디스크에 이전 zip 이 있으면
                        // 그걸로 재조립해 성공으로 전달. 저장된 version 과 서버 meta.version_id 가 다를
                        // 수도 있지만 (서버에 더 새 버전 있음) 신뢰성 우선 — versionVerified=false 표기.
                        if (!forceUpdate) {
                            TJResourceLogger.w(
                                "(TJLabsResource) raw download failed — trying offline fallback // type=$bundleType // sectorId=$sectorId // rawStatus=$rawStatus // rawMsg=$rawMsg"
                            )
                            loadBundleFromCachedFile(application, bundleType, sectorId, useLegacyEndpoint, imageLoadPolicy) { fallbackSnapshot ->
                                if (fallbackSnapshot != null) {
                                    bundleCache[cacheKey] = fallbackSnapshot
                                    applyCounts(fallbackSnapshot)
                                    finish(
                                        true,
                                        "(TJLabsResource) fallback(raw_fail): use cached bundle // versionVerified=false // version=${fallbackSnapshot.versionId} // serverVersion=${meta.version_id}",
                                        fallbackSnapshot,
                                        "${metaSource}+fallback_raw_fail"
                                    )
                                } else {
                                    finish(false, rawMsg, null, "${metaSource}+raw_fail")
                                }
                            }
                        } else {
                            finish(false, rawMsg, null, "${metaSource}+raw_fail")
                        }
                        return@downloadBundleFile
                    }

                    // 라벨 지정: 바깥 IO launch 와 구분해 [return@parseLaunch] 로 이 launch 만 종료.
                    CoroutineScope(Dispatchers.IO).launch parseLaunch@{
                        val parseRawStartMs = nowMs()
                        // fresh download: zip 내용이 새 version 으로 덮어써졌으므로 반드시 재-extract.
                        // 이전 version 의 extracted/ 파일들이 남아 있으면 stale 데이터로 파싱될 수 있다.
                        val extractedRoot = ensureExtractedAssetsRoot(downloadedFile, forceReExtract = true)
                        val bundleJson = readBundleJsonFromFile(bundleType, sectorId, downloadedFile, useLegacyEndpoint, extractedRoot)
                        val parsed = bundleJson?.let {
                            parseBundleRaw(
                                bundleType, sectorId, meta, it,
                                bundleZipPathIfJupiter(downloadedFile),
                                extractedRoot?.absolutePath
                            )
                        }
                        parseMs = elapsedMs(parseRawStartMs)
                        if (parsed == null) {
                            finishOnMain(false, "(TJLabsResource) Error : parse bundle raw", null, "${metaSource}+parse_fail")
                            return@parseLaunch
                        }

                        val enrichStartMs = nowMs()
                        enrichCsvData(application, sectorId, parsed, imageLoadPolicy) { csvSuccess, enriched ->
                            enrichMs = elapsedMs(enrichStartMs)
                            bundleCache[cacheKey] = enriched
                            applyCounts(enriched)
                            // v1 은 pref 캐시 오염 방지 — v2 만 다음 호출을 위한 shortcut 을 심는다.
                            if (!useLegacyEndpoint) {
                                saveBundleMetaPrefs(application, bundleType, sectorId, meta.version_id, meta.url, downloadedFile.absolutePath)
                                // v2 network_raw 성공 시점에 legacy per-file 캐시(orphan) 를 정리.
                                pruneLegacyPerFileCache(application, sectorId)
                            }
                            val statusLabel = if (csvSuccess) "Success" else "Partial-fail(DR path CSV missing)"
                            finish(csvSuccess, "(TJLabsResource) $statusLabel : load bundle", enriched, "${metaSource}+network_raw")
                        }
                    }
                }
            }
        }

        // Fast-path: 메모리 스냅샷 + freshness window 적중 시, meta HTTP 와 디스크 IO 모두 스킵.
        // v1 (useLegacyEndpoint=true) 은 벤치마크 목적이라 fastpath 자체를 스킵해 매 호출마다 실 트래픽을 발생시킨다.
        if (!useLegacyEndpoint) {
            val cacheKeyForFastPath = buildSnapshotCacheKey(bundleType, sectorId, useLegacyEndpoint = false)
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
        }

        val metaStartMs = nowMs()
        requestBundleMeta(bundleType, sectorId, useLegacyEndpoint) { metaStatus, metaMsg, meta ->
            metaMs = elapsedMs(metaStartMs)
            if ((metaStatus in 200 until 300) == false || meta == null) {
                // iOS 4-2 오프라인 폴백: forceUpdate=false 이고 디스크에 저장된 bundle zip 이
                // 있으면 그걸로 재조립해 성공으로 전달. forceUpdate=true 면 폴백 생략.
                if (!forceUpdate) {
                    TJResourceLogger.w(
                        "(TJLabsResource) meta failed — trying offline fallback // type=$bundleType // sectorId=$sectorId // metaStatus=$metaStatus // metaMsg=$metaMsg"
                    )
                    loadBundleFromCachedFile(application, bundleType, sectorId, useLegacyEndpoint, imageLoadPolicy) { fallbackSnapshot ->
                        if (fallbackSnapshot != null) {
                            val fallbackCacheKey = buildSnapshotCacheKey(bundleType, sectorId, useLegacyEndpoint)
                            bundleCache[fallbackCacheKey] = fallbackSnapshot
                            applyCounts(fallbackSnapshot)
                            finish(
                                true,
                                "(TJLabsResource) fallback(meta_fail): use cached bundle // versionVerified=false // version=${fallbackSnapshot.versionId}",
                                fallbackSnapshot,
                                "fallback+meta_fail"
                            )
                        } else {
                            finish(false, metaMsg, null, "meta_fail")
                        }
                    }
                } else {
                    finish(false, metaMsg, null, "meta_fail")
                }
                return@requestBundleMeta
            }
            if (!useLegacyEndpoint) saveBundleMetaTimestamp(application, bundleType, sectorId, nowMs())
            proceedWithMeta(meta, "network_meta")
        }
    }

    /**
     * 2026-09-28+ 흐름 — simulations 는 sector 번들 zip 안에 들어 있다.
     *
     * 1) meta 를 얻어 version_id 를 서버 기준으로 확정
     * 2) 디스크에 캐시된 zip (version 일치) 을 재사용하거나, 없으면 새로 스트리밍 다운로드
     * 3) zip 안 `sectors/{id}/bundle.json` 을 읽어 simulations 항목을 파싱
     * 4) 각 simulation JSON entry (`sectors/{id}/assets/simulations/{dr|pdr}/{name}.json`) 를
     *    로컬 디스크로 extract 후 [SimulationItemOutput.url] 을 로컬 파일 경로로 갱신
     *
     * 소비자는 갱신된 url 을 File 로 열어 내용을 읽으면 된다 (HTTP GET 아님). VENUS/WARP 는 simulations
     * 응답 자체가 없으므로 JUPITER 만 지원.
     */
    fun loadSimulationData(
        application: Application,
        sectorId: Int,
        completion: (Boolean, String, SimulationBundleOutput?) -> Unit
    ) {
        val bundleType = ResourceBundleType.JUPITER
        // iOS parity (TJ-609, 2026-10-06): Multi 로더가 이미 조합 zip 과 extract 를 소유한 경우
        // bundle.zip 재다운로드 없이 바로 extractSimulationsFromZip 로 진입. bundleCache[sector] 가
        // bundleAssetsRoot + bundleZipPath 를 들고 있다면 그걸 그대로 재사용 — mock 모드 활성 시
        // "aws_korea_{sector}/bundle.zip" 재다운로드로 발생하던 중복 캐시 디렉토리 제거.
        val cached = getCachedSnapshot(bundleType, sectorId)
        val cachedZip = cached?.bundleZipPath?.let { File(it) }?.takeIf { it.exists() && it.length() > 0 }
        val cachedAssets = cached?.bundleAssetsRoot?.let { File(it) }?.takeIf { it.exists() }
        if (cached != null && cachedZip != null && cachedAssets != null) {
            TJResourceLogger.d(
                "(TJLabsResource) loadSimulationData fast-path from Multi cache // sectorId=$sectorId " +
                    "zip=${cachedZip.absolutePath} assets=${cachedAssets.absolutePath}"
            )
            CoroutineScope(Dispatchers.IO).launch {
                val bundleJson = readBundleJsonFromFile(
                    bundleType, sectorId, cachedZip, useLegacyEndpoint = false, extractedRoot = cachedAssets
                )
                if (bundleJson.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        completion(false, "(TJLabsResource) Error : read bundle.json from Multi cache", null)
                    }
                    return@launch
                }
                val parsed = parseSimulationsFromRaw(bundleJson)
                if (parsed == null) {
                    withContext(Dispatchers.Main) {
                        completion(false, "(TJLabsResource) Error : simulations not found (Multi cache)", null)
                    }
                    return@launch
                }
                val extracted = extractSimulationsFromZip(application, sectorId, cachedZip, cachedAssets, parsed)
                withContext(Dispatchers.Main) {
                    completion(true, "(TJLabsResource) Success : load simulation (Multi cache)", extracted)
                }
            }
            return
        }

        requestBundleMeta(bundleType, sectorId) { metaStatus, metaMsg, meta ->
            if ((metaStatus in 200 until 300) == false || meta == null) {
                completion(false, metaMsg, null)
                return@requestBundleMeta
            }

            // @param freshlyDownloaded true 면 zip 이 방금 새 version 으로 덮어써져서 기존 extracted/ 는
            //   stale — 반드시 재-extract. false 는 disk cache hit 로 zip 내용이 안 바뀐 상태 → 기존 extract 재사용 가능.
            fun onZipReady(zipFile: File, freshlyDownloaded: Boolean) {
                CoroutineScope(Dispatchers.IO).launch {
                    val extractRoot = File(application.cacheDir, "$CSV_DIR/${buildSectorCacheFolderName(sectorId)}/extracted")
                    val marker = File(extractRoot, "sectors/$sectorId/bundle.json")
                    val assetsRoot = if (!freshlyDownloaded && marker.exists() && marker.length() > 0) {
                        extractRoot
                    } else {
                        extractBundleZipToDisk(application, sectorId, zipFile)
                    }
                    val bundleJson = readBundleJsonFromFile(bundleType, sectorId, zipFile, useLegacyEndpoint = false, extractedRoot = assetsRoot)
                    if (bundleJson.isNullOrBlank()) {
                        withContext(Dispatchers.Main) {
                            completion(false, "(TJLabsResource) Error : read bundle.json from zip", null)
                        }
                        return@launch
                    }
                    val parsed = parseSimulationsFromRaw(bundleJson)
                    if (parsed == null) {
                        withContext(Dispatchers.Main) {
                            completion(false, "(TJLabsResource) Error : simulations not found", null)
                        }
                        return@launch
                    }
                    val extracted = extractSimulationsFromZip(application, sectorId, zipFile, assetsRoot, parsed)
                    withContext(Dispatchers.Main) {
                        completion(true, "(TJLabsResource) Success : load simulation", extracted)
                    }
                }
            }

            // 1) 캐시된 zip 재사용 (version 매치 확정 → zip 내용 안 바뀜 → extract 재사용 OK)
            loadBundleRawFromCache(application, bundleType, sectorId, meta)?.let { cachedZip ->
                onZipReady(cachedZip, freshlyDownloaded = false)
                return@requestBundleMeta
            }

            // 2) 새로 다운로드 (zip 이 새 version 으로 덮어써짐 → 기존 extract 는 stale)
            downloadBundleFile(application, bundleType, sectorId, meta.url) { rawStatus, rawMsg, downloadedFile ->
                if ((rawStatus in 200 until 300) == false || downloadedFile == null) {
                    completion(false, rawMsg, null)
                    return@downloadBundleFile
                }
                // 다운로드 완료 → prefs 를 즉시 갱신해 다음 호출이 캐시 히트하도록.
                saveBundleMetaPrefs(application, bundleType, sectorId, meta.version_id, meta.url, downloadedFile.absolutePath)
                onZipReady(downloadedFile, freshlyDownloaded = true)
            }
        }
    }

    /**
     * @param retriesLeft transient 실패 (502/503/504/IOException) 시 자동 재시도 회수. 기본 1회.
     *  proxy 뒤 upstream 이 first-request 빌드로 timeout 걸린 케이스는 대부분 다음 호출에서 캐시 히트해 성공.
     *  auth 실패나 4xx (권한/스펙 오류) 는 재시도 무의미 → skip.
     */
    private fun requestBundleMeta(
        bundleType: ResourceBundleType,
        sectorId: Int,
        useLegacyEndpoint: Boolean = false,
        retriesLeft: Int = 1,
        completion: (Int, String, SectorBundleMetaOutput?) -> Unit
    ) {
        val baseUrl = TJLabsResourceNetworkConstants.getBaseUrl(bundleType)
        val serverVersion = if (useLegacyEndpoint && bundleType == ResourceBundleType.JUPITER) {
            // v1 강제 — 프리즈된 2026-09-10 버전으로 legacy /sectors/{pk}/bundle JSON endpoint 호출.
            LEGACY_JUPITER_SECTOR_BUNDLE_SERVER_VERSION
        } else {
            TJLabsResourceNetworkConstants.getBundleServerVersion(bundleType)
        }
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
                    ResourceBundleType.JUPITER -> if (useLegacyEndpoint) {
                        // v1 강제: 프리즈된 legacy JSON endpoint (`/{2026-09-10}/sectors/{pk}/bundle`).
                        api.getSectorBundle(serverVersion, sectorId)
                    } else {
                        // 2026-09-28+: 신 통합 endpoint (`/sectors/bundle?sector_ids=...`) + zip 응답.
                        // SDK 는 sector 단위 API 이므로 `[sectorId]` 단일 원소 배열로 요청 — 조합 고정 원칙(문서 6번)
                        // 을 준수해 조합별 재빌드 지연을 피한다.
                        api.getSectorBundleV2(serverVersion, listOf(sectorId))
                    }
                    // WARP 는 별도 인프라(.warp.tjlabs.dev, server_version=2026-04-27) 라 zip 스펙 미적용 → 기존 endpoint 유지.
                    ResourceBundleType.WARP -> api.getSectorBundle(serverVersion, sectorId)
                }
            }
            fun scheduleRetry(reason: String) {
                TJResourceLogger.w(
                    "(TJLabsResource) request bundle meta transient failure → retry in ${META_RETRY_BACKOFF_MS}ms // sectorId=$sectorId // reason=$reason // retriesLeft=$retriesLeft"
                )
                CoroutineScope(Dispatchers.IO).launch {
                    delay(META_RETRY_BACKOFF_MS)
                    requestBundleMeta(bundleType, sectorId, useLegacyEndpoint, retriesLeft - 1, completion)
                }
            }

            call.enqueue(object : Callback<SectorBundleMetaOutput> {
                override fun onFailure(call: Call<SectorBundleMetaOutput>, t: Throwable) {
                    // network exception (socket timeout / dns / unreachable) — 대개 transient. 남은 재시도 있으면 시도.
                    if (retriesLeft > 0) {
                        scheduleRetry("onFailure: ${t.javaClass.simpleName}: ${t.localizedMessage}")
                        return
                    }
                    TJResourceLogger.w(
                        "(TJLabsResource) request bundle meta fail // type=$bundleType // sectorId=$sectorId // error=${t.javaClass.simpleName}: ${t.localizedMessage}"
                    )
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
                        return
                    }
                    val errorBody = (try { response.errorBody()?.string() } catch (_: Exception) { null }).orEmpty()
                    // 502 Bad Gateway / 503 Service Unavailable / 504 Gateway Timeout — proxy 앞단이 upstream 응답을
                    // 못 기다리고 끊었을 때. 다음 요청은 upstream rebuild 가 끝나 캐시 히트할 확률이 높으므로 재시도.
                    val isTransient = status in 502..504
                    if (isTransient && retriesLeft > 0) {
                        scheduleRetry("status=$status errorBody=${errorBody.take(200)}")
                        return
                    }
                    TJResourceLogger.w(
                        "(TJLabsResource) request bundle meta error // type=$bundleType // sectorId=$sectorId // status=$status // errorBody=${errorBody.take(300)}"
                    )
                    completion(status, "(TJLabsResource) Error : getSectorBundleMeta", null)
                }
            })
        }
    }

    /**
     * bundle 파일(JUPITER: zip, VENUS/WARP: JSON) 을 원격 URL 로부터 로컬 캐시 파일로 스트리밍한다.
     *
     * zip 은 sector 당 40MB 급이라 `ResponseBody.string()` 으로 메모리에 통째로 올리면 부담이 크다.
     * 여기선 [Retrofit] 을 거치지 않고 [HttpURLConnection] 으로 직접 열어 InputStream 을 파일로 흘려보낸다.
     * (인증 헤더 · path prefix 는 meta 응답의 url 이 이미 온전히 포함하고 있어 재조립 불필요.)
     *
     * 성공 시 target [File] 을 그대로 넘기고, 이후 소비자가 [readBundleJsonFromFile] 로 bundle.json 을 얻는다.
     */
    private fun downloadBundleFile(
        application: Application,
        bundleType: ResourceBundleType,
        sectorId: Int,
        bundleUrl: String,
        useLegacyEndpoint: Boolean = false,
        retriesLeft: Int = 1,
        completion: (Int, String, File?) -> Unit
    ) {
        val env = TJLabsResourceNetworkConstants.getCurrentEnv()
        val effectiveUrl = applyBaseUrlPathPrefix(TJLabsResourceNetworkConstants.getBaseUrl(bundleType), bundleUrl)
        if (effectiveUrl != bundleUrl) {
            TJResourceLogger.d("(TJLabsResource) request bundle raw url rewrite: $bundleUrl -> $effectiveUrl")
        }
        TJResourceLogger.d("(TJLabsResource) request bundle raw // type=$bundleType // env=$env // url=$effectiveUrl")

        val cacheDir = File(application.cacheDir, "$CSV_DIR/${buildSectorCacheFolderName(sectorId)}")
        if (cacheDir.exists().not()) cacheDir.mkdirs()
        val dstFile = File(cacheDir, buildBundleRawFileName(bundleType, sectorId, useLegacyEndpoint))

        fun scheduleRawRetry(reason: String) {
            TJResourceLogger.w(
                "(TJLabsResource) request bundle raw transient failure → retry in ${RAW_RETRY_BACKOFF_MS}ms // sectorId=$sectorId // reason=$reason // retriesLeft=$retriesLeft"
            )
            CoroutineScope(Dispatchers.IO).launch {
                delay(RAW_RETRY_BACKOFF_MS)
                downloadBundleFile(application, bundleType, sectorId, bundleUrl, useLegacyEndpoint, retriesLeft - 1, completion)
            }
        }

        var connection: HttpURLConnection? = null
        try {
            connection = URL(effectiveUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 60_000
            connection.connect()
            val status = connection.responseCode
            if ((status in 200 until 300).not()) {
                val errorBody = try {
                    connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                } catch (_: Exception) {
                    ""
                }
                // 502/503/504 는 대개 proxy 앞단이 upstream 을 못 기다린 케이스 → 다음 요청은 캐시 히트 확률 높음.
                if (status in 502..504 && retriesLeft > 0) {
                    runCatching { connection.disconnect() }
                    scheduleRawRetry("status=$status errorBody=${errorBody.take(200)}")
                    return
                }
                TJResourceLogger.w(
                    "(TJLabsResource) request bundle raw error // status=$status // url=$effectiveUrl // errorBody=${errorBody.take(300)}"
                )
                completion(status, "(TJLabsResource) Error : downloadBundleFile", null)
                return
            }

            connection.inputStream.use { input ->
                FileOutputStream(dstFile).use { output ->
                    val buf = ByteArray(64 * 1024)
                    var totalRead = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        totalRead += n
                    }
                    TJResourceLogger.d(
                        "(TJLabsResource) request bundle raw success // status=$status // url=$effectiveUrl // bytes=$totalRead // dst=${dstFile.absolutePath}"
                    )
                }
            }
            completion(status, "(TJLabsResource) Success : downloadBundleFile", dstFile)
        } catch (e: Exception) {
            runCatching { if (dstFile.exists()) dstFile.delete() }
            // socket timeout / dns / unreachable — transient 로 취급, 남은 재시도 있으면 시도.
            if (retriesLeft > 0) {
                runCatching { connection?.disconnect() }
                scheduleRawRetry("exception: ${e.javaClass.simpleName}: ${e.localizedMessage}")
                return
            }
            TJResourceLogger.w(
                "(TJLabsResource) request bundle raw exception // url=$effectiveUrl // error=${e.javaClass.simpleName}: ${e.localizedMessage}"
            )
            completion(500, "(TJLabsResource) Failure : downloadBundleFile", null)
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    /**
     * 하나 외부망 proxy (`.../sdk-proxy`) 이관 이후 meta 응답의 raw bundle URL 은 서버가
     * proxy prefix 를 포함해 온전히 리턴한다 (예: `.../sdk-proxy/bundle/warp/1/xxx.json`).
     * 예전 온프레미스 (`192.168.120.104`) 대응으로 baseUrl basePath 를 앞에 붙이는 로직이
     * 있었으나, 새 proxy 환경에선 오히려 `/api` 이중 삽입을 유발해 404 를 낸다. 서버 URL 을
     * 그대로 신뢰하고 rewrite 하지 않는다.
     */
    private fun applyBaseUrlPathPrefix(baseUrl: String, resourceUrl: String): String {
        return resourceUrl
    }

    private fun enrichCsvData(
        application: Application,
        sectorId: Int,
        snapshot: BundleDataSnapshot,
        imageLoadPolicy: com.tjlabs.tjlabsresource_sdk_android.ImageLoadPolicy,
        completion: (Boolean, BundleDataSnapshot) -> Unit
    ) {
        val enrichTotalStartMs = nowMs()
        val hasPathUrls = snapshot.graphPathUrlsByKey.isNotEmpty()
        val hasEntranceUrls = snapshot.entranceRouteUrlsByKey.isNotEmpty()
        val hasImageUrls = snapshot.imageUrlsByKey.isNotEmpty()
        val hasParkingMatchesUrls = snapshot.parkingMatchesUrlsByLevelId.isNotEmpty()
        if (!hasPathUrls && !hasEntranceUrls && !hasImageUrls && !hasParkingMatchesUrls) {
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
            val imageTargets = filterImageTargetsByPolicy(sectorId, snapshot, imageLoadPolicy)
            val parkingMatchesTargets = snapshot.parkingMatchesUrlsByLevelId

            // path / entrance / image / parking-matches 를 한 번에 fan-out 시켜 병렬 처리
            val parallelStart = nowMs()
            // 2026-09-28+ JUPITER: 그래프 path CSV · parking_matches JSON 은 zip 안의 entry.
            // 다운로드 시 zip 을 [BundleDataSnapshot.bundleAssetsRoot] 아래에 실제 파일로 풀어두고
            // 여기서 그 파일을 직접 읽는다 — ZipFile 를 매 read 마다 다시 열지 않아 IO 효율적이고
            // 디버깅 시 개별 자원을 파일로 열어볼 수 있다. 압축 해제 실패 시엔 zip 을 fallback.
            val assetsRoot: File? = snapshot.bundleAssetsRoot?.let { File(it) }?.takeIf { it.exists() }
            val zipFileFallback: File? = snapshot.bundleZipPath?.let { File(it) }?.takeIf { it.exists() }

            val pathDeferred = pathTargets.map { (key, ref) ->
                async { key to fetchPathPixelData(application, sectorId, snapshot.versionId, key, ref, assetsRoot, zipFileFallback) }
            }
            // entrance route CSV 는 zip 스키마에도 포함되지 않는다 (여전히 절대 URL).
            val entranceDeferred = entranceTargets.map { (key, url) ->
                async { key to fetchEntranceRouteData(application, sectorId, snapshot.versionId, key, url) }
            }
            // map_image 는 도면 PNG → 항상 절대 URL. CDN 캐시가 잘 듣는다.
            val imageDeferred = imageTargets.map { (key, url) ->
                async { key to loadImageWithCache(application, sectorId, snapshot.versionId, key, url) }
            }
            val parkingMatchesDeferred = parkingMatchesTargets.map { (levelId, ref) ->
                async { levelId to fetchParkingMatchesData(application, sectorId, snapshot.versionId, levelId, ref, assetsRoot, zipFileFallback) }
            }

            val pathResults = pathDeferred.awaitAll()
            val entranceResults = entranceDeferred.awaitAll()
            val imageResults = imageDeferred.awaitAll()
            val parkingMatchesResults = parkingMatchesDeferred.awaitAll()
            TJResourceLogger.d {
                "(TJLabsResource) perf enrichCsvData parallel stage // sectorId=$sectorId // pathCount=${pathTargets.size} // entranceCount=${entranceTargets.size} // imageCount=${imageTargets.size} // elapsedMs=${elapsedMs(parallelStart)}"
            }

            var isAllSuccess = true
            val pathPixelData = snapshot.pathPixelDataMap.toMutableMap()
            val entranceRouteData = snapshot.entranceRouteDataMap.toMutableMap()
            val imageData = snapshot.imageDataMap.toMutableMap()
            val parkingMatchesData = snapshot.parkingMatchesDataByLevelId.toMutableMap()

            for ((levelId, parsed) in parkingMatchesResults) {
                if (parsed != null) {
                    parkingMatchesData[levelId] = parsed
                } else {
                    // 파일이 서버에 있다고 표기되어 있었으나 fetch/parse 실패 — enrich 전체 성공 여부에는
                    // 영향을 주지 않는다 (parking-matches 는 optional 데이터). 로그만 남긴다.
                    TJResourceLogger.d { "(TJLabsResource) enrichCsvData optional fail@ParkingMatches // levelId=$levelId" }
                }
            }

            // DR path CSV 실패는 sector 성공 판정에 치명적 (isAllSuccess=false).
            // 개별 로그 + 마지막에 요약 WARN 을 남겨 어떤 level 이 문제인지 즉시 파악 가능.
            val failedDrPaths = mutableListOf<String>()
            for ((key, parsed) in pathResults) {
                if (parsed != null) {
                    pathPixelData[key] = parsed
                } else if (key.endsWith(PDR_LEVEL_KEY_SUFFIX)) {
                    TJResourceLogger.d { "(TJLabsResource) enrichCsvData optional fail@PathPixelCsv(PDR) // key=$key" }
                } else {
                    isAllSuccess = false
                    failedDrPaths.add(key)
                    TJResourceLogger.w(
                        "(TJLabsResource) enrichCsvData failed@PathPixelCsv(DR) // key=$key // ref=${snapshot.graphPathUrlsByKey[key]}"
                    )
                }
            }
            for ((key, parsed) in entranceResults) {
                if (parsed != null) {
                    entranceRouteData[key] = parsed
                } else {
                    // entrance route CSV 는 zip 밖 절대 URL(2026-09-28+ 에서도 유지) 로 서빙되는
                    // 보조 자원. 특정 entrance 의 CSV 가 CDN 에 아직 업로드되지 않아 404 로 떨어지는
                    // 케이스는 실제 운영에서 흔하고 (예: 2026-09-28 DEV 환경에서 sector 111 의
                    // 몇몇 entrance 파일 누락), 이걸로 sector 로드 전체를 실패시키면 지도·측위·
                    // 경로탐색까지 못 쓴다. parking_matches 나 PDR path 처럼 optional 로 완화한다.
                    // 소비자 (jupiter-sdk) 는 entranceRouteDataMap 에 key 부재로 감지 가능.
                    TJResourceLogger.d { "(TJLabsResource) enrichCsvData optional fail@EntranceCsv // key=$key" }
                }
            }
            for ((key, image) in imageResults) {
                if (image != null) imageData[key] = image
                else TJResourceLogger.d { "(TJLabsResource) enrichCsvData failed@Image // key=$key" }
            }

            // parking-matches 를 sectorData 안 각 LevelOutput 에 주입해 소비자가
            // SectorOutput 순회만으로 접근 가능하게 한다 (manager lookup 도 별도 제공).
            // level_match 는 파일 root 에 실린 사용자 표기 (예: "3") — building 단위 유일.
            val updatedSectorData = if (parkingMatchesData.isEmpty()) {
                snapshot.sectorData
            } else {
                snapshot.sectorData.copy(
                    buildings = snapshot.sectorData.buildings.map { b ->
                        b.copy(
                            levels = b.levels.map { lv ->
                                parkingMatchesData[lv.id]?.let { pmd ->
                                    lv.copy(
                                        parking_matches = pmd.matches,
                                        level_match = pmd.level_match
                                    )
                                } ?: lv
                            }
                        )
                    }
                )
            }
            val enriched = snapshot.copy(
                sectorData = updatedSectorData,
                pathPixelDataMap = pathPixelData,
                entranceRouteDataMap = entranceRouteData,
                imageDataMap = imageData,
                parkingMatchesDataByLevelId = parkingMatchesData
            )

            withContext(Dispatchers.Main) {
                if (isAllSuccess) {
                    TJResourceLogger.d {
                        "(TJLabsResource) enrichCsvData done // success=true // elapsedMs=${elapsedMs(enrichTotalStartMs)}"
                    }
                } else {
                    // 요약 WARN 으로 어느 자원이 sector 실패를 초래했는지 한 줄 정리 —
                    // 대부분 DR path CSV 가 zip 안 · extracted 안 · CDN 어디에도 없어 read null 이 원인.
                    TJResourceLogger.w(
                        "(TJLabsResource) enrichCsvData done // success=false // elapsedMs=${elapsedMs(enrichTotalStartMs)} // failedDrPaths=${failedDrPaths.size} keys=$failedDrPaths"
                    )
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
                        // 개별 hit 로그 제거 — enrichCsvData 요약에서 imageCount 로 집계됨.
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
            // 개별 save 로그 제거 — 반복 노이즈.
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

    /**
     * path CSV 를 읽어 [PathPixelData] 로 파싱.
     *
     * @param ref 스키마별 참조: zip 스키마이면 zip entry 경로(예: `sectors/111/.../dr.csv`),
     *  legacy 면 절대 URL.
     * @param assetsRoot 압축 해제된 자원 디렉토리. non-null 이면 `<assetsRoot>/<ref>` 에서 파일로 직접 read.
     * @param zipFileFallback assetsRoot 에서 파일이 없거나 실패했을 때 열어볼 zip 파일 (동일 zip). 둘 다
     *  null 이면 URL fetch (legacy 흐름).
     */
    private fun fetchPathPixelData(
        application: Application,
        sectorId: Int,
        versionId: String,
        key: String,
        ref: String,
        assetsRoot: File?,
        zipFileFallback: File?
    ): PathPixelData? {
        val startMs = nowMs()
        val text = when {
            assetsRoot != null -> {
                val f = File(assetsRoot, ref)
                if (f.exists() && f.length() > 0) f.readText()
                else zipFileFallback?.let { readZipEntryText(it, ref) }
            }
            zipFileFallback != null -> readZipEntryText(zipFileFallback, ref)
            else -> getCsvTextWithCache(
                application = application,
                sectorId = sectorId,
                versionId = versionId,
                key = key,
                url = ref,
                source = "pathPixel:$key",
                versionPrefix = PREF_PATH_VERSION_PREFIX,
                urlPrefix = PREF_PATH_URL_PREFIX,
                filePrefix = PREF_PATH_FILE_PREFIX
            )
        } ?: run {
            TJResourceLogger.d("(TJLabsResource) fetchPathPixelData FAIL // key=$key // elapsedMs=${elapsedMs(startMs)}")
            return null
        }
        // Benchmark 토글 — flag OFF 면 production 흐름 (single-pass).
        val parseStartNs = if (benchmarkTimingEnabled) System.nanoTime() else 0L
        val result = when (benchmarkCsvParserMode) {
            com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceBenchmark.CsvParserMode.LEGACY_REGEX ->
                parsePathPixelDataLegacy(text)
            com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceBenchmark.CsvParserMode.SINGLE_PASS ->
                parsePathPixelData(text)
        }
        if (benchmarkTimingEnabled) {
            benchmarkStageTimings.addPathCsvParse((System.nanoTime() - parseStartNs) / 1_000_000)
        }
        return result
        // 성공 케이스는 조용히 진행 — enrichCsvData 최종 요약에서 총계 확인 (`perf enrichCsvData parallel stage`).
    }

    /**
     * level 별 parking-matches JSON 을 읽어 [ParkingMatchesData] 로 파싱.
     * @param ref zip 스키마면 zip entry 경로, legacy 면 절대 URL.
     * @param assetsRoot 압축 해제된 자원 디렉토리. non-null 이면 파일 read 우선.
     * @param zipFileFallback extracted 실패 시 fallback zip 파일.
     */
    private fun fetchParkingMatchesData(
        application: Application,
        sectorId: Int,
        versionId: String,
        levelId: Int,
        ref: String,
        assetsRoot: File?,
        zipFileFallback: File?
    ): ParkingMatchesData? {
        val key = "level_$levelId"
        val startMs = nowMs()
        val text = when {
            assetsRoot != null -> {
                val f = File(assetsRoot, ref)
                if (f.exists() && f.length() > 0) f.readText()
                else zipFileFallback?.let { readZipEntryText(it, ref) }
            }
            zipFileFallback != null -> readZipEntryText(zipFileFallback, ref)
            else -> getCsvTextWithCache(
                application = application,
                sectorId = sectorId,
                versionId = versionId,
                key = key,
                url = ref,
                source = "parkingMatches:$key",
                versionPrefix = PREF_PARKING_MATCHES_VERSION_PREFIX,
                urlPrefix = PREF_PARKING_MATCHES_URL_PREFIX,
                filePrefix = PREF_PARKING_MATCHES_FILE_PREFIX,
                extension = "json"
            )
        } ?: run {
            TJResourceLogger.d("(TJLabsResource) fetchParkingMatchesData FAIL // levelId=$levelId // elapsedMs=${elapsedMs(startMs)}")
            return null
        }
        return parseParkingMatchesData(text)
    }

    // 매칭 파일 포맷: {"level_match":"<user-facing level, e.g., \"3\">", "matches":[{"id":"<uuid>","matchingId":"<string>"}, ...]}
    // matchingId 는 숫자처럼 보여도 문자열 (앞자리 0 이나 문자 포함 ID 가능성). Int 로 파싱하지 않음.
    // matches 는 빈 배열일 수 있고, 그 경우 emptyList 로 담김.
    // level_match 도 옵셔널 — 없거나 빈 문자열이면 null 로 취급.
    private fun parseParkingMatchesData(text: String): ParkingMatchesData? {
        return try {
            val root = JSONObject(text)
            val levelMatch: String? = if (root.isNull("level_match")) {
                null
            } else {
                root.optString("level_match").takeIf { it.isNotBlank() }
            }
            val arr = root.optJSONArray("matches")
                ?: return ParkingMatchesData(matches = emptyList(), level_match = levelMatch)
            val out = ArrayList<ParkingMatch>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id").orEmpty()
                if (id.isBlank()) continue
                // matchingId 는 null 가능 (지도에는 있으나 현장에 없는 주차면).
                // JSON 에서 null 이거나 문자열이지만 비어 있는 경우 모두 null 로 취급.
                val matchingId: String? = if (obj.isNull("matchingId")) {
                    null
                } else {
                    obj.optString("matchingId").takeIf { it.isNotBlank() }
                }
                out.add(ParkingMatch(id = id, matchingId = matchingId))
            }
            ParkingMatchesData(matches = out, level_match = levelMatch)
        } catch (t: Throwable) {
            TJResourceLogger.d("(TJLabsResource) parseParkingMatchesData failed // err=${t.message}")
            null
        }
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
        filePrefix: String,
        extension: String = "csv"
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
                    return cachedFile.readText()
                    // hit 로그 제거 — 엔트리마다 반복 노이즈. 실패 시에만 로그.
                } catch (e: Exception) {
                    TJResourceLogger.d(
                        "(TJLabsResource) csv cache read fail // source=$source // key=$key // path=${cachedFile.absolutePath} // error=${e.localizedMessage}"
                    )
                }
            }
            // stale / miss 개별 로그 제거 — 로드 성공 시 자연스러운 상태.
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
            filePrefix = filePrefix,
            extension = extension
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
        filePrefix: String,
        extension: String = "csv"
    ) {
        try {
            val cacheDir = File(application.cacheDir, "$CSV_DIR/${buildSectorCacheFolderName(sectorId)}")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            val fileName = buildCsvFileName(sectorId = sectorId, key = key, extension = extension)
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

            // 저장 성공 개별 로그 제거 — 반복 노이즈. 실패만 아래 catch 에서 로그.
        } catch (e: Exception) {
            TJResourceLogger.d(
                "(TJLabsResource) csv cache save fail // source=$source // key=$key // error=${e.localizedMessage}"
            )
        }
    }

    /**
     * v1.1.14 정책 변경 — 항상 null 반환.
     *
     * 이전 (v1.1.13 까지): prefs 에 저장된 meta 가 5분 이내면 network fetch 없이 재사용
     *   → memory_fastpath / pref_meta 경로가 활성화되었지만, 서버가 그 사이 bundle version 을
     *     bump 한 경우 최대 5분 stale 데이터가 반환될 수 있음.
     *
     * 이후 (v1.1.14+): 모든 loadBundle 호출은 항상 [requestBundleMeta] API 로 최신 meta 를
     *   서버에서 조회 → [proceedWithMeta] 에서 bundleCache / 디스크 raw 의 version 과 대조 →
     *   일치 시 캐시 재사용, 불일치 시 raw 재다운로드. version 판정을 서버 기준으로 항상 확정.
     *
     * trade-off: 매 loadBundle 마다 meta HTTP round-trip (~수백ms~1s) 발생.
     */
    private fun getSavedBundleMetaIfFresh(
        application: Application,
        bundleType: ResourceBundleType,
        sectorId: Int
    ): SectorBundleMetaOutput? {
        TJResourceLogger.d(
            "(TJLabsResource) perf loadBundle meta shortcut disabled // type=$bundleType // sectorId=$sectorId // (v1.1.14 policy: always fetch meta then compare version)"
        )
        return null
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

    /**
     * 캐시된 bundle 파일(zip 또는 JSON) 을 반환. version_id / url 이 meta 와 일치할 때만 hit.
     * JUPITER 는 .zip, VENUS/WARP 는 .json 파일이 반환된다. 호출측이 [readBundleJsonFromFile]
     * 로 실제 bundle.json 텍스트를 얻는다.
     */
    /**
     * 오프라인 폴백 (iOS `TJLabsMultiResourceManager` 4-2 규칙 포팅).
     * meta HTTP 또는 raw zip 다운로드가 실패했을 때, 저장된 bundle 메타 (version/path) 로
     * 디스크 zip 을 그대로 재조립해 snapshot 을 돌려준다. **버전 검증은 하지 않는다**
     * (서버에 더 새로운 버전이 있을 수 있다는 걸 알면서도 이전 버전으로 서비스를 이어가는 경우).
     *
     * 반환:
     *  - `BundleDataSnapshot` : 재조립 성공. 호출부가 소비자에게 success=true 로 전달
     *    (message 에 `versionVerified=false` 명시, source 에 `fallback` 포함).
     *  - `null` : 저장된 캐시 없음 / 파일 손상 / bundle.json 읽기 실패 / parse 실패.
     *
     * `enrichCsvData` 는 그대로 호출된다 — zip 안의 자원은 네트워크 없이 읽히고,
     * zip 밖의 자원 (entrance route CSV, 이미지) 은 optional 로 강등되어 있어 네트워크 끊김
     * 상황에서도 best-effort 로 enriched snapshot 을 돌려받는다.
     */
    private fun loadBundleFromCachedFile(
        application: Application,
        bundleType: ResourceBundleType,
        sectorId: Int,
        useLegacyEndpoint: Boolean,
        imageLoadPolicy: com.tjlabs.tjlabsresource_sdk_android.ImageLoadPolicy,
        onResult: (BundleDataSnapshot?) -> Unit
    ) {
        val prefs = application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val savedVersion = prefs.getString(getBundleMetaKey(bundleType, PREF_BUNDLE_VERSION_PREFIX, sectorId), null)
        val savedUrl = prefs.getString(getBundleMetaKey(bundleType, PREF_BUNDLE_URL_PREFIX, sectorId), null)
        val savedPath = prefs.getString(getBundleMetaKey(bundleType, PREF_BUNDLE_FILE_PREFIX, sectorId), null)
        if (savedVersion.isNullOrBlank() || savedPath.isNullOrBlank()) {
            TJResourceLogger.w(
                "(TJLabsResource) fallback: no cached bundle metadata // type=$bundleType // sectorId=$sectorId"
            )
            onResult(null)
            return
        }
        val rawFile = File(savedPath)
        if (!rawFile.exists() || rawFile.length() <= 0) {
            TJResourceLogger.w(
                "(TJLabsResource) fallback: cached bundle file missing // type=$bundleType // sectorId=$sectorId // path=$savedPath"
            )
            onResult(null)
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val isV2ZipFlow = bundleType == ResourceBundleType.JUPITER && !useLegacyEndpoint
            val extractedRoot = if (isV2ZipFlow) {
                val root = File(application.cacheDir, "$CSV_DIR/${buildSectorCacheFolderName(sectorId)}/extracted")
                val marker = File(root, "sectors/$sectorId/bundle.json")
                if (marker.exists() && marker.length() > 0) root
                else extractBundleZipToDisk(application, sectorId, rawFile)
            } else null
            val bundleJson = readBundleJsonFromFile(bundleType, sectorId, rawFile, useLegacyEndpoint, extractedRoot)
            if (bundleJson == null) {
                TJResourceLogger.w(
                    "(TJLabsResource) fallback: bundle.json read fail // type=$bundleType // sectorId=$sectorId"
                )
                withContext(Dispatchers.Main) { onResult(null) }
                return@launch
            }
            val cachedMeta = SectorBundleMetaOutput(url = savedUrl ?: "", version_id = savedVersion)
            val parsed = parseBundleRaw(
                bundleType, sectorId, cachedMeta, bundleJson,
                if (isV2ZipFlow) rawFile.absolutePath else null,
                extractedRoot?.absolutePath
            )
            if (parsed == null) {
                TJResourceLogger.w(
                    "(TJLabsResource) fallback: parseBundleRaw fail // type=$bundleType // sectorId=$sectorId"
                )
                withContext(Dispatchers.Main) { onResult(null) }
                return@launch
            }
            TJResourceLogger.i(
                "(TJLabsResource) fallback: parsed cached bundle // type=$bundleType // sectorId=$sectorId // version=$savedVersion // enriching..."
            )
            // enrichCsvData 는 Main-thread 로 콜백을 전달 — 그대로 호출부에 bubble up.
            enrichCsvData(application, sectorId, parsed, imageLoadPolicy) { csvSuccess, enriched ->
                TJResourceLogger.i(
                    "(TJLabsResource) fallback: enrich done // sectorId=$sectorId // csvSuccess=$csvSuccess"
                )
                // Best-effort — DR path CSV 가 zip 에 없거나 깨져서 csvSuccess=false 여도 enriched snapshot 반환.
                // 호출부가 소비자에게 success=true 로 전달 (versionVerified=false 메시지와 함께) — iOS 4-2 와 동일.
                onResult(enriched)
            }
        }
    }

    /**
     * 멀티 섹터 번들 로드 (TJLabsMultiResourceManager) 전용 - 조합 zip 안의 특정 섹터 하나를
     * 처리해 BundleDataSnapshot 을 companion `bundleCache` 에 commit 한다.
     * iOS `prepareSectorFromArchive + applyPreparedSector` 와 같은 역할.
     *
     * extractedRoot/sectors/{sectorId}/bundle.json 을 읽어 기존 [parseBundleRaw] + [enrichCsvData]
     * 플로우로 재조립 — 단일 섹터 loadBundle 과 결과물은 동일한 BundleDataSnapshot 이라 소비자
     * (TJLabsResourceManager getter / delegate) 는 변경 없이 사용 가능.
     *
     * 섹터별로 호출돼야 하며, 조합의 공유 extracted 디렉토리를 **읽기만** 하므로 섹터간 병렬 안전.
     */
    internal suspend fun processSectorFromArchive(
        application: Application,
        sectorId: Int,
        archiveFile: File,
        extractedRoot: File,
        versionId: String,
        imageLoadPolicy: com.tjlabs.tjlabsresource_sdk_android.ImageLoadPolicy
    ): com.tjlabs.tjlabsresource_sdk_android.SectorProcessOutcome {
        val bundleJsonFile = File(extractedRoot, "sectors/$sectorId/bundle.json")
        if (!bundleJsonFile.exists() || bundleJsonFile.length() <= 0) {
            TJResourceLogger.w(
                "(TJLabsResource) multi: bundle.json missing // sectorId=$sectorId // path=${bundleJsonFile.absolutePath}"
            )
            return com.tjlabs.tjlabsresource_sdk_android.SectorProcessOutcome(
                isSuccess = false,
                failures = listOf(ResourceLoadStageFailure(ResourceLoadStage.SECTOR_BUNDLE_DOWNLOAD, "$sectorId", true))
            )
        }
        val bundleJson = try {
            bundleJsonFile.readText()
        } catch (e: Exception) {
            TJResourceLogger.w(
                "(TJLabsResource) multi: bundle.json read fail // sectorId=$sectorId // error=${e.localizedMessage}"
            )
            return com.tjlabs.tjlabsresource_sdk_android.SectorProcessOutcome(
                isSuccess = false,
                failures = listOf(ResourceLoadStageFailure(ResourceLoadStage.SECTOR_BUNDLE_DOWNLOAD, "$sectorId", true))
            )
        }
        // Multi archive 의 섹터는 항상 JUPITER / v2 (zip) 스키마.
        val meta = SectorBundleMetaOutput(url = "", version_id = versionId)
        val parsed = parseBundleRaw(
            ResourceBundleType.JUPITER, sectorId, meta, bundleJson,
            archiveFile.absolutePath, extractedRoot.absolutePath
        )
        if (parsed == null) {
            TJResourceLogger.w(
                "(TJLabsResource) multi: parseBundleRaw fail // sectorId=$sectorId"
            )
            return com.tjlabs.tjlabsresource_sdk_android.SectorProcessOutcome(
                isSuccess = false,
                failures = listOf(ResourceLoadStageFailure(ResourceLoadStage.SECTOR_BUNDLE_DOWNLOAD, "$sectorId", true))
            )
        }
        // enrichCsvData 는 callback-style — 외부 suspend 로 wrap. completion 이 메인 스레드에서
        // 호출되므로 resume 도 메인에서 재개된다 (호출부가 멀티 처리 전체를 Dispatchers.IO 로
        // 둘러싸고 있어도 안전).
        return suspendCoroutine { cont ->
            enrichCsvData(application, sectorId, parsed, imageLoadPolicy) { csvSuccess, enriched ->
                val cacheKey = buildSnapshotCacheKey(ResourceBundleType.JUPITER, sectorId, useLegacyEndpoint = false)
                bundleCache[cacheKey] = enriched
                val failures = if (!csvSuccess) {
                    listOf(ResourceLoadStageFailure(ResourceLoadStage.PATH_PIXEL, "$sectorId", true))
                } else {
                    emptyList()
                }
                cont.resume(
                    com.tjlabs.tjlabsresource_sdk_android.SectorProcessOutcome(
                        isSuccess = csvSuccess,
                        failures = failures
                    )
                )
            }
        }
    }

    private fun loadBundleRawFromCache(
        application: Application,
        bundleType: ResourceBundleType,
        sectorId: Int,
        meta: SectorBundleMetaOutput
    ): File? {
        val prefs = application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val versionKey = getBundleMetaKey(bundleType, PREF_BUNDLE_VERSION_PREFIX, sectorId)
        val fileKey = getBundleMetaKey(bundleType, PREF_BUNDLE_FILE_PREFIX, sectorId)

        val savedVersion = prefs.getString(versionKey, null)
        val savedPath = prefs.getString(fileKey, null)

        // meta.url 은 GCS presigned URL — X-Goog-Date/X-Goog-Signature 가 매 요청마다 달라져서
        // 같은 version_id 라도 URL 은 절대 같지 않다. 예전에 savedUrl 도 비교했더니 매 세션마다
        // cache miss 로 강제 재다운로드가 발생했다 (실측 관측). version_id 는 콘텐츠 해시라
        // 이 값 하나만 매치해도 원본 정합성이 보장된다.
        if (savedVersion != meta.version_id || savedPath.isNullOrBlank()) {
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

        TJResourceLogger.d(
            "(TJLabsResource) bundle raw cache hit // type=$bundleType // sectorId=$sectorId // version=${meta.version_id} // path=${rawFile.absolutePath} // bytes=${rawFile.length()}"
        )
        return rawFile
    }

    /**
     * bundle 파일에서 실제 bundle.json 텍스트를 추출한다.
     * - JUPITER v2 (zip): [extractedRoot] 가 있으면 압축 해제된 파일에서 직접 read, 없으면 zip entry 로 fallback.
     * - JUPITER v1 (useLegacyEndpoint=true, JSON): 파일 전체를 String 으로 읽음.
     * - VENUS/WARP (JSON): 파일 전체를 String 으로 읽음.
     */
    private fun readBundleJsonFromFile(
        bundleType: ResourceBundleType,
        sectorId: Int,
        file: File,
        useLegacyEndpoint: Boolean = false,
        extractedRoot: File? = null
    ): String? {
        return try {
            when (bundleType) {
                ResourceBundleType.JUPITER -> when {
                    useLegacyEndpoint -> file.readText()
                    extractedRoot != null -> {
                        val extractedBundleJson = File(extractedRoot, "sectors/$sectorId/bundle.json")
                        if (extractedBundleJson.exists() && extractedBundleJson.length() > 0) {
                            extractedBundleJson.readText()
                        } else {
                            readZipEntryText(file, "sectors/$sectorId/bundle.json")
                        }
                    }
                    else -> readZipEntryText(file, "sectors/$sectorId/bundle.json")
                }
                ResourceBundleType.VENUS, ResourceBundleType.WARP -> file.readText()
            }
        } catch (e: Exception) {
            TJResourceLogger.d(
                "(TJLabsResource) readBundleJsonFromFile fail // type=$bundleType // sectorId=$sectorId // path=${file.absolutePath} // error=${e.localizedMessage}"
            )
            null
        }
    }

    /**
     * 다운로드된 bundle zip 을 로컬 디렉토리에 압축 해제해 개별 자원 파일을 노출한다.
     *
     * 해제 위치: `<sectorFolder>/extracted/`. 실행 전에 기존 디렉토리를 삭제해 이전 버전의 stale
     * 파일을 남기지 않는다 (version_id 는 zip 파일 자체가 관리하므로 별도 marker 는 두지 않음).
     *
     * 실패 시 null 반환 — 호출측은 zip 을 직접 여는 fallback 으로 진행할 수 있다.
     */
    private fun extractBundleZipToDisk(
        application: Application,
        sectorId: Int,
        zipFile: File
    ): File? {
        val extractRoot = File(application.cacheDir, "$CSV_DIR/${buildSectorCacheFolderName(sectorId)}/extracted")
        // stale 방지 — 이전 version 의 extract 흔적을 완전히 제거.
        runCatching { if (extractRoot.exists()) extractRoot.deleteRecursively() }
        if (!extractRoot.mkdirs()) {
            TJResourceLogger.d("(TJLabsResource) zip extract mkdirs fail // dst=${extractRoot.absolutePath}")
            return null
        }
        return try {
            val startMs = nowMs()
            var count = 0
            var totalBytes = 0L
            ZipFile(zipFile).use { zf ->
                val entries = zf.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val outFile = File(extractRoot, entry.name)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                        continue
                    }
                    outFile.parentFile?.mkdirs()
                    zf.getInputStream(entry).use { input ->
                        FileOutputStream(outFile).use { output ->
                            totalBytes += input.copyTo(output)
                        }
                    }
                    count++
                }
            }
            TJResourceLogger.d(
                "(TJLabsResource) zip extract done // sectorId=$sectorId // files=$count // bytes=$totalBytes // dst=${extractRoot.absolutePath} // elapsedMs=${elapsedMs(startMs)}"
            )
            extractRoot
        } catch (e: Exception) {
            TJResourceLogger.d(
                "(TJLabsResource) zip extract fail // sectorId=$sectorId // error=${e.localizedMessage}"
            )
            runCatching { extractRoot.deleteRecursively() }
            null
        }
    }

    /**
     * legacy (2026-09-10 이전) 흐름이 남긴 per-file 캐시 (path CSV / parking-matches JSON / entrance
     * route CSV) 를 이 sector 범위에서 정리. zip 이 곧 스냅샷이 된 이후로 이 캐시들은 stale 이고
     * 소비되지 않으므로 디스크·prefs 를 깨끗이 비운다.
     *
     * PREF_IMAGE_* / PREF_ENTRANCE_* 는 여전히 URL 기반 자원이라 유지. PREF_BUNDLE_* 는 zip 자체 캐시 유지.
     */
    private fun pruneLegacyPerFileCache(application: Application, sectorId: Int) {
        val prefs = application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val sectorNamespaceFragment = "_${sectorId}_"  // buildScopedPrefKey 형식: <prefix><namespace>_<sectorId>_<key>
        var prefRemoved = 0
        for (k in prefs.all.keys) {
            if (k.contains(sectorNamespaceFragment) &&
                (k.startsWith(PREF_PATH_VERSION_PREFIX) || k.startsWith(PREF_PATH_URL_PREFIX) || k.startsWith(PREF_PATH_FILE_PREFIX) ||
                 k.startsWith(PREF_PARKING_MATCHES_VERSION_PREFIX) || k.startsWith(PREF_PARKING_MATCHES_URL_PREFIX) || k.startsWith(PREF_PARKING_MATCHES_FILE_PREFIX))
            ) {
                editor.remove(k)
                prefRemoved++
            }
        }
        editor.apply()

        // 개별 CSV/JSON 파일 삭제. bundle_{id}.zip / bundle_v1_{id}.json / extracted/ / simulations/ 는 보존.
        val sectorDir = File(application.cacheDir, "$CSV_DIR/${buildSectorCacheFolderName(sectorId)}")
        val zipName = buildBundleRawFileName(ResourceBundleType.JUPITER, sectorId, useLegacyEndpoint = false)
        val jsonName = buildBundleRawFileName(ResourceBundleType.JUPITER, sectorId, useLegacyEndpoint = true)
        val keepDirs = setOf("extracted", "simulations")
        var fileRemoved = 0
        runCatching {
            sectorDir.listFiles()?.forEach { child ->
                if (child.isDirectory && child.name in keepDirs) return@forEach
                if (child.isFile && (child.name == zipName || child.name == jsonName)) return@forEach
                if (child.deleteRecursively()) fileRemoved++
            }
        }
        if (prefRemoved > 0 || fileRemoved > 0) {
            TJResourceLogger.d(
                "(TJLabsResource) prune legacy per-file cache // sectorId=$sectorId // prefRemoved=$prefRemoved // fileRemoved=$fileRemoved"
            )
        }
    }

    /**
     * zip 안 텍스트 entry 를 UTF-8 로 읽는다. entry 부재 시 null.
     * ZipFile 은 random-access 라 전체 zip 을 메모리에 올리지 않고 필요한 entry 만 스트림한다.
     * 압축 해제된 파일이 있으면 우선 그것을 사용하고, 없을 때만 zip 열기로 fallback.
     */
    private fun readZipEntryText(zipFile: File, entryPath: String): String? {
        if (zipFile.exists().not()) return null
        return try {
            ZipFile(zipFile).use { zf ->
                val entry = zf.getEntry(entryPath) ?: run {
                    TJResourceLogger.d("(TJLabsResource) zip entry missing // zip=${zipFile.name} // entry=$entryPath")
                    return@use null
                }
                zf.getInputStream(entry).use { input ->
                    input.bufferedReader(Charsets.UTF_8).readText()
                }
            }
        } catch (e: Exception) {
            TJResourceLogger.d(
                "(TJLabsResource) zip entry read fail // zip=${zipFile.absolutePath} // entry=$entryPath // error=${e.localizedMessage}"
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

    // 2026-09-28+: bundle 파일은 [downloadBundleFile] 이 직접 디스크로 스트리밍한다.
    // 별도의 saveBundleRawCache 는 필요 없고, prefs 만 [saveBundleMetaPrefs] 로 기록.

    private fun getBundleMetaKey(bundleType: ResourceBundleType, prefix: String, sectorId: Int): String {
        val bundleTypeKey = bundleType.name.lowercase()
        return "${prefix}${buildCacheNamespace()}_${bundleTypeKey}_$sectorId"
    }

    private fun buildBundleRawFileName(
        bundleType: ResourceBundleType,
        sectorId: Int,
        useLegacyEndpoint: Boolean = false
    ): String {
        return when (bundleType) {
            // 2026-09-28+ JUPITER: 응답이 zip. 확장자를 .zip 으로 저장해 ZipFile 로 random-access 하기 좋게 한다.
            // v1 (useLegacyEndpoint=true) 은 JSON 응답이므로 별도 파일명(.json) 으로 저장해 v2 캐시와 격리.
            ResourceBundleType.JUPITER -> if (useLegacyEndpoint) "bundle_v1_${sectorId}.json" else "bundle_${sectorId}.zip"
            ResourceBundleType.VENUS -> "bundle_venus_${sectorId}.json"
            ResourceBundleType.WARP -> "bundle_warp_${sectorId}.json"
        }
    }

    private fun buildCsvFileName(sectorId: Int, key: String, extension: String = "csv"): String {
        // key format: {sectorId}_{buildingName}_{levelName} or {sectorId}_{buildingName}_{levelName}_{entranceNumber}
        // requested naming: {sectorId}_{buildingName}_{levelName}.{extension}
        // 대부분의 소비자는 CSV 저장 (path pixel · entrance route). parking-matches 처럼 JSON
        // 컨텐츠는 호출부에서 extension="json" 을 명시적으로 넘겨 확장자로 표기해준다.
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
        return "$normalized.$extension"
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
            connection.connectTimeout = 10000
            connection.readTimeout = 30000
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
            // 성공 로그 제거 — 파일 fetch 마다 반복 노이즈. 실패만 위 http error / 아래 exception 블록.
            connection.disconnect()
            text
        } catch (e: Exception) {
            TJResourceLogger.d(
                "(TJLabsResource) fetchTextFromUrl exception // source=$source // url=$urlString // error=${e.localizedMessage}"
            )
            null
        }
    }

    /**
     * [ImageLoadPolicy] 에 따라 초기 로드에서 fetch 할 이미지 URL 을 필터링.
     * ALL: 모든 층. NONE: 빈 map. DEFAULT_ONLY: sector 의 default_position 이 가리키는 층만.
     * default_position 이 없거나 매핑 실패 시 NONE 과 동일 (빈 map) + 경고 로그.
     */
    private fun filterImageTargetsByPolicy(
        sectorId: Int,
        snapshot: BundleDataSnapshot,
        policy: com.tjlabs.tjlabsresource_sdk_android.ImageLoadPolicy,
    ): Map<String, String> {
        val all = snapshot.imageUrlsByKey
        return when (policy) {
            com.tjlabs.tjlabsresource_sdk_android.ImageLoadPolicy.ALL -> all
            com.tjlabs.tjlabsresource_sdk_android.ImageLoadPolicy.NONE -> {
                TJResourceLogger.d(
                    "(TJLabsResource) enrichCsvData image skip // policy=NONE // sectorId=$sectorId // skippedImageCount=${all.size}"
                )
                emptyMap()
            }
            com.tjlabs.tjlabsresource_sdk_android.ImageLoadPolicy.DEFAULT_ONLY -> {
                val defaultKey = resolveDefaultLevelKey(sectorId, snapshot)
                if (defaultKey == null) {
                    TJResourceLogger.w(
                        "(TJLabsResource) enrichCsvData image skip // policy=DEFAULT_ONLY // sectorId=$sectorId // reason=no default_position → treated as NONE"
                    )
                    emptyMap()
                } else {
                    val filtered = all.filterKeys { it == defaultKey }
                    if (filtered.isEmpty()) {
                        TJResourceLogger.w(
                            "(TJLabsResource) enrichCsvData image skip // policy=DEFAULT_ONLY // sectorId=$sectorId // defaultKey=$defaultKey not in imageUrlsByKey → treated as NONE"
                        )
                    } else {
                        TJResourceLogger.d(
                            "(TJLabsResource) enrichCsvData image filter // policy=DEFAULT_ONLY // sectorId=$sectorId // keepKey=$defaultKey // skippedCount=${all.size - filtered.size}"
                        )
                    }
                    filtered
                }
            }
        }
    }

    /**
     * SectorOutput.default_position → imageKey ("${sectorId}_${bldg.name}_${level.name}") 매핑.
     * default_position 이 없으면 null.
     */
    private fun resolveDefaultLevelKey(sectorId: Int, snapshot: BundleDataSnapshot): String? {
        val dp = snapshot.sectorData.default_position ?: return null
        return "${sectorId}_${dp.building.name}_${dp.building.level.name}"
    }

    private fun fetchImageFromUrl(key: String, urlString: String): Bitmap? {
        return try {
            val connection = URL(urlString).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 30000
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
            }
            // 성공 케이스 로그는 enrichCsvData 요약에서 imageCount 로 집계됨 — 개별 로그는 생략.
            bitmap
        } catch (e: Exception) {
            TJResourceLogger.d(
                "(TJLabsResource) fetchImageFromUrl exception // key=$key // url=$urlString // error=${e.localizedMessage}"
            )
            null
        }
    }

    /**
     * bundle.json 텍스트를 [BundleDataSnapshot] 으로 파싱한다.
     *
     * @param bundleZipPath JUPITER 2026-09-28+ 스키마 전용. 로컬에 저장된 zip 파일 경로.
     *  이 값이 non-null 이면 `graphs[].path` · `parking_matches` · `simulations[].items[].src`
     *  는 zip 내부 entry 경로로 해석되어 그대로 [BundleDataSnapshot] 의 `*UrlsByKey` 맵에 실린다.
     *  null 이면 (VENUS/WARP · legacy JSON) 기존 `{ url: "..." }` 형태의 절대 URL 로 파싱.
     * @param bundleAssetsRoot 압축 해제된 자원 디렉토리 (있을 때). enrichCsvData 가 여기서 파일을 읽는다.
     */
    private fun parseBundleRaw(
        bundleType: ResourceBundleType,
        sectorId: Int,
        meta: SectorBundleMetaOutput,
        raw: String,
        bundleZipPath: String? = null,
        bundleAssetsRoot: String? = null
    ): BundleDataSnapshot? {
        return try {
            val root = JSONObject(raw)

            // iOS parity : path · parking_matches · simulations 는 bare string / `{url: "..."}` 둘 다
            // 수용 (readStringOrUrl). 스키마 판별 플래그 없이 값 타입만 보고 추출한다.

            val buildings = mutableListOf<BuildingOutput>()
            val levelWardsMap = mutableMapOf<String, List<String>>()
            val scaleOffsetMap = mutableMapOf<String, List<Float>>()
            val geofenceMap = mutableMapOf<String, GeofenceData>()
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
            // 2026-08-28 스키마 — level 별 parking_matches 참조를 levelId 기준으로 수집.
            // 값은 zip 스키마에서는 zip entry 경로, legacy 에서는 절대 URL. 미업로드 층은 key 부재.
            val parkingMatchesUrls = mutableMapOf<Int, String>()

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
                    // parking_matches: 파일 미업로드 시 서버가 null 을 보낸다.
                    // iOS `ParkingMatchesInfo` decoder 와 동일하게 bare string / `{url: "..."}` 오브젝트
                    // 둘 다 수용. 2026-10-02 통합 네임스페이스에서는 single/multi 경로가 섞여 발화될 수 있어
                    // useZipPath 분기 대신 값 타입을 보고 추출.
                    val parkingMatchesRef: String = readStringOrUrl(levelObj, "parking_matches")
                    if (parkingMatchesRef.isNotBlank()) parkingMatchesUrls[levelId] = parkingMatchesRef
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

                    val wardsJson = levelObj.optJSONArray("wards")
                    if (isDebugLevel.not() && wardsJson != null) {
                        levelWardsMap[levelKey] = parseWards(wardsJson)
                    }
                    // 2026-09-10 스키마: rf_landmarks 가 level 직속 평면 배열로 이동.
                    // ward 정보는 각 랜드마크의 `ward` 필드에서 읽는다 (다른 층 ward 일 수 있음).
                    val rfLandmarksJson = levelObj.optJSONArray("rf_landmarks")
                    if (isDebugLevel.not() && rfLandmarksJson != null) {
                        landmarkMap[levelKey] = parseLandmarks(rfLandmarksJson)
                    }

                    // 그래프 파싱은 level 마다 반복되는 hot path 라 개별 DEBUG 로그는 폭주 원인.
                    // 이상 케이스 (graphs 배열 부재, DR 미검출 등) 만 로그 남기고 성공 케이스는 조용히 진행.
                    val drGraphObj = resolveDrGraphObject(levelObj)
                    if (isDebugLevel.not() && drGraphObj != null) {
                        val nodes = parseGraphNodes(drGraphObj.optJSONArray("nodes"))
                        val links = parseGraphLinks(drGraphObj.optJSONArray("links"))
                        val linkGroups = parseGraphLinkGroups(drGraphObj.optJSONArray("link_groups"))

                        if (nodes != null && links != null) {
                            nodeMap[levelKey] = buildNodeDict(nodes)
                            linkMap[levelKey] = buildLinkDict(links, linkGroups ?: emptyList())
                        }

                        val pathUrl = readGraphPathRef(drGraphObj)
                        // 그래프가 실제로 비어있으면 (nodes=0, links=0) pathUrl 은 서버에 잔존하는
                        // 껍데기일 뿐 실제 CSV 는 없다 — 2026-08-06 스키마부터 순수 floor 의
                        // walkable 데이터가 전이층으로 이동한 경우 이런 상태가 발생. fetch 시도
                        // 자체를 스킵해서 404 → sector 실패 오판을 예방.
                        val hasGraphContent = (nodes?.isNotEmpty() == true) || (links?.isNotEmpty() == true)
                        if (pathUrl.isNotBlank() && hasGraphContent) {
                            graphPathUrls[levelKey] = pathUrl
                        }
                        // 그 외 (skipped/missing) 케이스는 조용히 진행 — 정상 상태이며 반복 로그 노이즈만 만듬.
                    } else if (isDebugLevel.not()) {
                        TJResourceLogger.d(
                            "(TJLabsResource) parseBundleRaw DR graph not found // levelKey=$levelKey"
                        )
                    }

                    if (isDebugLevel.not()) {
                        val pdrGraphObj = resolvePdrGraphObject(levelObj)
                        val pdrPathUrl = if (pdrGraphObj != null) readGraphPathRef(pdrGraphObj) else ""
                        val pdrNodeCount = pdrGraphObj?.optJSONArray("nodes")?.length() ?: 0
                        val pdrLinkCount = pdrGraphObj?.optJSONArray("links")?.length() ?: 0
                        val pdrHasGraphContent = pdrNodeCount > 0 || pdrLinkCount > 0
                        if (pdrPathUrl.isNotBlank() && pdrHasGraphContent) {
                            val pdrLevelKey = "${levelKey}${PDR_LEVEL_KEY_SUFFIX}"
                            graphPathUrls[pdrLevelKey] = pdrPathUrl
                        }
                        // PDR mapped/missing 개별 로그 제거 — 아래 요약 로그에서 총합으로 확인.
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

            // 파싱 완료 요약 — level 마다 개별 로그 대신 한 줄로 총계 (DEBUG 노이즈 방지).
            TJResourceLogger.d(
                "(TJLabsResource) parseBundleRaw done // sectorId=$sectorId // buildings=${buildings.size} // levels=${buildings.sumOf { it.levels.size }} // graphPaths=${graphPathUrls.size} // parkingMatches=${parkingMatchesUrls.size} // entrances=${entranceItemMap.size} // images=${imageUrlsByKey.size} // transitions=${transitions.size}"
            )

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
                bundleZipPath = bundleZipPath,
                bundleAssetsRoot = bundleAssetsRoot,
                sectorData = sectorData,
                levelWardsDataMap = levelWardsMap,
                scaleOffsetDataMap = scaleOffsetMap,
                geofenceDataMap = geofenceMap,
                landmarkDataMap = landmarkMap,
                nodeDataMap = nodeMap,
                linkDataMap = linkMap,
                parkingMatchesUrlsByLevelId = parkingMatchesUrls,
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
            ),
            zoom_level = parseZoomLevel(obj.optJSONObject("zoom_level")),
        )
    }

    /**
     * iOS parity (2026-10-02 server, iOS commit `feat: zoom level`) — 섹터 지도 줌 레벨 디코딩.
     * `default_position.zoom_level` 객체로 서버 전송 — 없으면 null (하위 호환).
     * 유효성: min <= default <= max 는 서버 검증 통과 가정, 추가 가드 없음.
     */
    private fun parseZoomLevel(obj: JSONObject?): com.tjlabs.tjlabsresource_sdk_android.ZoomLevel? {
        if (obj == null) return null
        return com.tjlabs.tjlabsresource_sdk_android.ZoomLevel(
            min = obj.optDouble("min", 0.0),
            default_value = obj.optDouble("default", 0.0),
            max = obj.optDouble("max", 0.0),
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
                // 2026-09-28+: 필드명이 `src` (zip 내부 경로). legacy: `url` (절대 URL).
                // 두 필드 모두 확인해 어느 스키마든 안전하게 파싱한다.
                val ref = itemObj.optString("src").trim().ifEmpty { itemObj.optString("url").trim() }
                if (name.isBlank() || ref.isBlank()) continue

                // SimulationItemOutput.url 필드에 zip entry 경로/URL 을 그대로 담고, extract 이후에
                // 로컬 절대 경로로 갱신한다 (2026-09-28+ 흐름). VENUS/WARP 는 simulations 응답이 없어
                // 실행 경로 자체가 없다.
                val item = SimulationItemOutput(name = name, url = ref)
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

    /**
     * 2026-09-28+ 흐름 — simulation JSON 은 zip 이 이미 압축 해제된 `<assetsRoot>/sectors/{id}/assets/simulations/{kind}/{name}.json`
     * 에 파일로 존재한다. [SimulationItemOutput.url] 을 그 절대 파일 경로로 갱신한 새 [SimulationBundleOutput] 을 반환.
     *
     * `assetsRoot` 가 유효하지 않으면 zip 에서 직접 뽑아 별도 dir 로 복사하는 fallback 을 취한다 (구 흐름 호환).
     * 소비자 (jupiter-sdk 등) 는 갱신된 url 을 그대로 [File] 로 열어 텍스트를 읽으면 된다.
     */
    private fun extractSimulationsFromZip(
        application: Application,
        sectorId: Int,
        zipFile: File,
        assetsRoot: File?,
        simulationData: SimulationBundleOutput
    ): SimulationBundleOutput {
        fun mapExtractedPath(kind: String, item: SimulationItemOutput): SimulationItemOutput? {
            val entryPath = item.url  // parseSimulations 가 zip entry 경로를 url 필드에 채워둔다.
            if (entryPath.isBlank()) return null
            if (assetsRoot != null) {
                val src = File(assetsRoot, entryPath)
                if (src.exists() && src.length() > 0) {
                    TJResourceLogger.d(
                        "(TJLabsResource) simulation extracted // kind=$kind // name=${item.name} // path=${src.absolutePath}"
                    )
                    return item.copy(url = src.absolutePath)
                }
                TJResourceLogger.d(
                    "(TJLabsResource) simulation extract fallback (missing in assets) // kind=$kind // name=${item.name} // entry=$entryPath"
                )
            }
            // Fallback: zip 을 직접 열어 시뮬레이션 하나를 copy. (assetsRoot 실패 케이스 방어)
            val outDir = File(application.cacheDir, "$CSV_DIR/${buildSectorCacheFolderName(sectorId)}/simulations")
            if (outDir.exists().not()) outDir.mkdirs()
            val text = readZipEntryText(zipFile, entryPath) ?: return null
            return try {
                val outFile = File(outDir, "${kind}_${sanitizeSimulationName(item.name)}.json")
                outFile.writeText(text)
                item.copy(url = outFile.absolutePath)
            } catch (e: Exception) {
                TJResourceLogger.d("(TJLabsResource) simulation extract write fail // kind=$kind // error=${e.localizedMessage}")
                null
            }
        }

        val vehicle = simulationData.vehicle.mapNotNull { mapExtractedPath("vehicle", it) }
        val pdr = simulationData.pdr.mapNotNull { mapExtractedPath("pdr", it) }
        return SimulationBundleOutput(vehicle = vehicle, pdr = pdr)
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

    /**
     * graph 의 `path` 필드 값을 스키마와 무관하게 추출한다 (iOS `PathInfo` decoder 매핑).
     *   - bare string          : zip entry 경로. 2026-09-28+ zip 번들에서 사용.
     *   - `{ "url": "..." }`   : 절대 URL. 레거시 single-sector 응답 포맷.
     *   - null / 다른 타입     : "" (미지정) — 소비측에서 fetch 자체가 스킵된다.
     * 2026-10-02 통합 server version 하에서는 두 포맷이 섞여 올 수 있어 분기 대신 값 타입을 본다.
     */
    private fun readGraphPathRef(graphObj: JSONObject): String {
        return readStringOrUrl(graphObj, "path")
    }

    /**
     * [iOS parity · `PathInfo` / `ParkingMatchesInfo` decoder]
     * 번들 JSON 에서 "object with url" / "bare string" 두 포맷을 모두 수용해 URL 문자열만 뽑는다.
     * `useZipPath` 분기를 대체해 single/multi/legacy 모든 응답에서 안전하게 동작.
     */
    private fun readStringOrUrl(obj: JSONObject, key: String): String {
        if (obj.isNull(key)) return ""
        val value = obj.opt(key) ?: return ""
        return when (value) {
            is String -> value
            is JSONObject -> value.optString("url").orEmpty()
            else -> ""
        }
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
        // path 가 bare string / {url: ...} 둘 다일 수 있으므로 readStringOrUrl 로 추출 (iOS parity).
        for (i in 0 until graphsArray.length()) {
            val graphObj = graphsArray.optJSONObject(i) ?: continue
            val pathUrl = readStringOrUrl(graphObj, "path")
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
            entrance_area = parsePolygonList(obj.optJSONArray("entrance_area")),
            entrance_matching_area = parsePolygonList(obj.optJSONArray("entrance_matching_area")),
            level_change_area = parsePolygonList(obj.optJSONArray("level_change_area"))
        )
    }

    /**
     * 2026-09-28+ 지오펜스 파싱 helper. 서버 응답은 두 포맷을 모두 쓸 수 있다 — iOS parity.
     *
     *  - **다각형 리스트** (주 포맷, 2026-09-28+): `[[[x,y],[x,y],...], ...]`
     *      각 다각형이 꼭짓점 `[x, y]` 쌍의 리스트. 닫힌 다각형, 오목 허용.
     *  - **AABB 리스트** (legacy 호환): `[[xMin, yMin, xMax, yMax], ...]`
     *      각 요소가 flat 4-tuple 사각형. 다각형 꼭짓점 4개로 변환해 통일한다 (CW 순).
     *
     * iOS `BundleModel.swift` 가 `GeofencePolygon` 디코딩 시 두 포맷을 모두 받는 것과 매핑.
     * 서버가 포맷 전환 중일 때 Android 가 디코딩 fail 하지 않도록 방어.
     */
    private fun parsePolygonList(arr: JSONArray?): List<List<List<Int>>> {
        if (arr == null) return emptyList()
        val result = mutableListOf<List<List<Int>>>()
        for (i in 0 until arr.length()) {
            val entry = arr.optJSONArray(i) ?: continue
            val firstEl = entry.opt(0)
            when {
                firstEl is JSONArray -> {
                    // 다각형 꼭짓점 리스트 (`[[x,y], [x,y], ...]`)
                    val vertices = parseIntMatrix(entry)
                    if (vertices.size >= 3) result.add(vertices)
                }
                firstEl is Number && entry.length() == 4 -> {
                    // AABB fallback: `[xMin, yMin, xMax, yMax]` → 4 꼭짓점 다각형 변환
                    val xMin = entry.optInt(0)
                    val yMin = entry.optInt(1)
                    val xMax = entry.optInt(2)
                    val yMax = entry.optInt(3)
                    result.add(listOf(
                        listOf(xMin, yMin),
                        listOf(xMax, yMin),
                        listOf(xMax, yMax),
                        listOf(xMin, yMax),
                    ))
                }
                // 그 외 포맷은 skip (파싱 실패 방어)
            }
        }
        return result
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

    // 2026-09-10 스키마: `arr` 는 level.rf_landmarks (평면). 각 항목이 자기 ward 를 들고 있다.
    // ward 는 이 level 의 wards[] 에 없을 수 있으므로 각 랜드마크의 ward.name 을 그대로 그룹 키로 쓴다.
    private fun parseLandmarks(arr: JSONArray): Map<String, LandmarkData> {
        val result = mutableMapOf<String, LandmarkData>()
        for (i in 0 until arr.length()) {
            val info = arr.optJSONObject(i) ?: continue
            val wardName = info.optJSONObject("ward")?.optString("name").orEmpty()
            if (wardName.isBlank()) continue

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

    /**
     * **Benchmark 전용** — Phase 4 (iOS parity single-pass 파서) 적용 전의 regex 기반 구현.
     * 운영 경로는 [parsePathPixelData] 를 쓰고, 이 함수는 성능 비교/회귀 체크를 위한 **보존판**.
     * 삭제하지 말 것 — Benchmark Suite UI 가 두 파서를 교대 호출해 regex vs single-pass 평균
     * 지연을 수치화한다 (iOS 문서 §9 "경로 CSV 파싱 280ms → 4.9ms" 와 동일 성격의 측정).
     */
    internal fun parsePathPixelDataLegacy(data: String): PathPixelData {
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

    /**
     * Path CSV 파서 (iOS `TJLabsPathPixelManager.parsePathPixelData` single-pass 포팅).
     *
     * 입력 포맷: 헤더 1줄 + `x,y,"[h1,h2,...]",scale` 데이터 N줄. heading 그룹 미존재 가능.
     * UTF-8 바이트 레벨 단일 패스로 처리 — 줄마다 regex 를 새로 컴파일/실행하는 기존 구현의
     * 비용 (iOS 기준 로드 시간의 ~78%) 을 제거하고, 같은 heading 목록 반복에 대비해 캐시한다.
     *
     * 규칙 (문서 TJ-580 §5 와 1:1):
     *  - 줄 구분자: \n, \r, \r\n. 빈 줄 skip. `encoding=` 포함 줄 skip.
     *  - x = 첫 콤마 앞, y = 첫·둘째 콤마 사이, scale = **마지막 콤마 뒤**
     *    (heading 안에도 콤마가 있으므로 마지막 콤마 기준). x/y 빈 문자열이면 데이터 아님.
     *  - heading = 첫 비어있지 않은 `[...]` 그룹의 각 토큰을 trim → Double → ","로 join.
     *    숫자 아닌 토큰 drop. 그룹 없거나 `[]` 이면 빈 문자열.
     *  - x/y/scale 중 하나라도 숫자 아니면 그 행 skip (크래시 대신).
     */
    private fun parsePathPixelData(data: String): PathPixelData {
        val roadX = mutableListOf<Float>()
        val roadY = mutableListOf<Float>()
        val roadScale = mutableListOf<Float>()
        val roadHeading = mutableListOf<String>()
        // 같은 heading 그룹이 반복되는 경우가 많아 (예: 대부분 "[0,180]") 원문→변환 결과 캐시.
        val headingCache = HashMap<String, String>()

        val buf = data.toByteArray(Charsets.UTF_8)
        val count = buf.size
        var i = 0

        // 헤더 (첫 줄) skip — 데이터 행이 아님.
        while (i < count && buf[i] != LF && buf[i] != CR) i++

        while (i < count) {
            // 줄바꿈 소비 (\n, \r, \r\n 혼용 지원). 빈 줄은 자연 skip.
            while (i < count && (buf[i] == LF || buf[i] == CR)) i++
            val lineStart = i
            while (i < count && buf[i] != LF && buf[i] != CR) i++
            val lineEnd = i
            if (lineStart == lineEnd) continue

            if (bytesContain(buf, lineStart, lineEnd, ENCODING_MARKER)) continue

            val firstComma = indexOfByte(buf, COMMA, lineStart, lineEnd)
            if (firstComma < 0) continue
            val yEnd = indexOfByte(buf, COMMA, firstComma + 1, lineEnd).let { if (it < 0) lineEnd else it }
            val lastComma = lastIndexOfByte(buf, COMMA, firstComma, lineEnd).let { if (it < 0) firstComma else it }

            // 빈 x 또는 y → 데이터 행이 아님.
            if (firstComma == lineStart || yEnd == firstComma + 1) continue

            val xStr = utf8String(buf, lineStart, firstComma)
            val yStr = utf8String(buf, firstComma + 1, yEnd)
            val scaleStr = utf8String(buf, lastComma + 1, lineEnd)
            val x = xStr.toFloatOrNull() ?: continue
            val y = yStr.toFloatOrNull() ?: continue
            val scale = scaleStr.toFloatOrNull() ?: continue

            // Heading: 첫 `[...]` 그룹. open/close 짝이 안 맞으면 null (빈 문자열).
            var heading = ""
            val bracket = firstBracketGroup(buf, lineStart, lineEnd)
            if (bracket != null) {
                val (contentStart, contentEnd) = bracket
                val raw = utf8String(buf, contentStart, contentEnd)
                heading = headingCache[raw] ?: run {
                    val formatted = raw.split(',')
                        .mapNotNull { it.trim().toDoubleOrNull() }
                        .joinToString(",") { it.toString() }
                    headingCache[raw] = formatted
                    formatted
                }
            }

            roadX.add(x)
            roadY.add(y)
            roadScale.add(scale)
            roadHeading.add(heading)
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

    /** [parsePathPixelData] 전용 — UTF-8 바이트 레벨 helper. 범위는 half-open [start, end). */
    private fun utf8String(buf: ByteArray, start: Int, end: Int): String =
        String(buf, start, end - start, Charsets.UTF_8)

    private fun indexOfByte(buf: ByteArray, b: Byte, start: Int, end: Int): Int {
        for (idx in start until end) if (buf[idx] == b) return idx
        return -1
    }

    private fun lastIndexOfByte(buf: ByteArray, b: Byte, start: Int, end: Int): Int {
        for (idx in (end - 1) downTo start) if (buf[idx] == b) return idx
        return -1
    }

    /** `[...]` 그룹의 **내용** 범위를 open 바로 뒤 ~ close 전 (half-open) 로 리턴. */
    private fun firstBracketGroup(buf: ByteArray, start: Int, end: Int): Pair<Int, Int>? {
        var open = -1
        for (idx in start until end) {
            val b = buf[idx]
            if (b == OPEN_BRACKET && open < 0) open = idx
            else if (b == CLOSE_BRACKET && open >= 0) return (open + 1) to idx
        }
        return null
    }

    private fun bytesContain(buf: ByteArray, start: Int, end: Int, needle: ByteArray): Boolean {
        val n = needle.size
        if (n == 0 || end - start < n) return false
        val lastStart = end - n
        for (idx in start..lastStart) {
            var match = true
            for (j in 0 until n) {
                if (buf[idx + j] != needle[j]) { match = false; break }
            }
            if (match) return true
        }
        return false
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

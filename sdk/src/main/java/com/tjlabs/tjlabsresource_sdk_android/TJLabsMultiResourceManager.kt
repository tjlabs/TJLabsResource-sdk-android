package com.tjlabs.tjlabsresource_sdk_android

import android.app.Application
import android.content.Context
import com.tjlabs.tjlabsresource_sdk_android.manager.BundleDataSnapshot
import com.tjlabs.tjlabsresource_sdk_android.manager.TJLabsBundleDataManager
import com.tjlabs.tjlabsresource_sdk_android.util.TJResourceLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 멀티 섹터 번들 로더 (TJ-559 / TJ-580 Android 포팅).
 *
 * 한 번의 요청으로 N개 섹터 (1~5) 의 번들을 **하나의 zip** 으로 받아 처리. 서버 endpoint 는
 * `GET /{server_version}/sectors/bundle?sector_ids=20&sector_ids=112&...` ([PostInput.getSectorBundleV2]).
 * 응답 body 는 단일 섹터와 같은 `{url, version_id}` 지만 url 이 zip 을 가리킨다.
 *
 * ── 특징
 *  - **조합 캐시**: 요청 섹터를 정렬·join 한 조합 키 (예: `[113, 20, 112]` → `20_112_113`) 로 디스크에
 *    `cache/<CSV_DIR>/multiBundle/{조합키}.zip` 과 SharedPreferences 버전 기록을 저장.
 *  - **LRU**: 조합 zip 은 최근 사용 3개까지만 유지 (mtime 기준). 캐시 재사용 시 mtime touch.
 *  - **오프라인 폴백**: `forceUpdate=false` 이고 ① 메타 요청 실패 또는 ② zip 다운로드 실패일 때
 *    (ⅰ) 정확한 조합 키의 캐시 → (ⅱ) 요청 섹터를 **모두 포함하는 상위집합 조합** 캐시를 순서대로 탐색해
 *    버전 검증 없이 재사용. 조합 로드 전체를 실패시키지 않는다.
 *  - **처리는 각 섹터별로 기존 [TJLabsBundleDataManager.processSectorFromArchive] 를 호출**.
 *    섹터 bundle.json / 그래프 path CSV / parking_matches JSON 은 zip 안에서 직접 read 하고,
 *    map image / entrance route CSV 는 네트워크 fetch 로 보존 (zip 밖 자원).
 *
 * ── 결과 전달
 *  - 섹터별 결과: [MultiResourceLoadResult.sectorResults] (per-sector `isSuccess`, `failedStage`, 등)
 *  - 전체 결과: [MultiResourceLoadResult] (**모든 섹터 성공일 때만** `isSuccess=true`)
 *  - 소비자가 등록한 기존 TJLabsResourceManagerDelegate 는 각 섹터별로 data callback 을 받는다
 *    (TJLabsBundleDataManager 가 bundleCache 를 채우고 그걸 emitSnapshot 으로 전달하는 기존 흐름 재사용).
 */
object TJLabsMultiResourceManager {

    /** 조합 캐시 디스크 유지 최대 개수. 초과분은 mtime 가장 오래된 순으로 삭제. */
    private const val MAX_CACHED_COMBINATIONS = 3

    private const val CSV_DIR = "tj_bundle_csv"
    private const val MULTI_DIR = "multiBundle"
    private const val PREF_NAME = "TJLabsMultiBundlePrefs"
    private const val PREF_VERSION_PREFIX = "TJLabsMultiBundleVersion_"

    private val bundleDataManager = TJLabsBundleDataManager()

    /**
     * 가장 최근 로드된 조합 zip 의 경로. [getBundleArchiveFileData] 같은 "조합 내부 자원
     * 꺼내보기" API 를 위해 보관. 멀티 로드 시작 때 교체되며 clearCache 로 null.
     */
    @Volatile
    private var latestArchive: File? = null

    @Volatile
    private var latestSectorIds: List<Int> = emptyList()

    @Volatile
    private var latestVersionId: String? = null

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * 조합 번들 로드. 결과는 [completion] 으로 1회 전달. 각 섹터의 BundleDataSnapshot 은
     * [TJLabsBundleDataManager] 의 companion `bundleCache` 에 저장되므로, 소비자는
     * [TJLabsResourceManager] 기존 getter / delegate 로 계속 접근 가능.
     *
     * @param forceUpdate true 면 캐시 무시하고 서버 다운로드. meta/zip 실패 시 폴백 안 함.
     * @param imageLoadPolicy map image 네트워크 다운로드 범위 (iOS 와 동일 — 멀티는 기본 NONE 권장).
     */
    fun loadResources(
        resourceManager: TJLabsResourceManager,
        application: Application,
        provider: String,
        region: String,
        env: ResourceServerEnv = ResourceServerEnv.PROD,
        sectorIds: List<Int>,
        imageLoadPolicy: ImageLoadPolicy = ImageLoadPolicy.NONE,
        forceUpdate: Boolean = false,
        completion: (MultiResourceLoadResult) -> Unit
    ) {
        val input = MultiResourceLoadInput(
            provider = provider,
            region = region,
            env = env,
            sectorIds = sectorIds,
            forceUpdate = forceUpdate,
            imageLoadPolicy = imageLoadPolicy
        )
        if (sectorIds.isEmpty()) {
            finishFatal(input, ResourceLoadStage.SECTOR_BUNDLE_METADATA, TJLabsResourceCode.UNCLASSIFIED_FAILURE_STATUS, null, completion)
            return
        }
        TJLabsResourceNetworkConstants.setServerURL(provider, region, env)

        // iOS parity — `resetMultiState`. 멀티 로더 진입 시 메모리 bundleCache 를 전부 비워
        // 이전 조합의 섹터 데이터가 다음 로드에 섞이지 않도록 한다 ("새 로드 = 전역 교체").
        // 디스크 조합 zip 캐시는 LRU 로 별도 관리되므로 메모리만 리셋.
        bundleDataManager.resetMultiBundleCache()

        val comboKey = combinationKey(sectorIds)
        val cachedVersion = loadCachedVersion(application, comboKey)
        val tStart = nowMs()

        TJResourceLogger.i(
            "(TJLabsMultiResourceManager) loadResources start // sectorIds=$sectorIds // comboKey=$comboKey // cachedVersion=$cachedVersion // forceUpdate=$forceUpdate (bundleCache reset per iOS parity)"
        )

        // iOS parity — stageTimings 캡처용 metadataFetchMs, zipDownloadMs 저장소.
        // processArchive 로 전달해서 MultiResourceLoadResult.stageTimings 에 포함.
        var metadataFetchMs = 0L
        var zipDownloadMs = 0L

        // 1) Metadata 요청 — version_id + zip url 조회
        requestMultiMeta(sectorIds, forceUpdate) { status, meta, errMsg ->
            metadataFetchMs = elapsedMs(tStart)
            TJResourceLogger.i(
                "(TJLabsMultiResourceManager) metadata fetch done // status=$status // elapsedMs=$metadataFetchMs // msg=$errMsg"
            )
            if (status != 200 || meta == null) {
                if (!forceUpdate && tryFallback(resourceManager, application, "metadata fetch failed status=$status", sectorIds, input, imageLoadPolicy, tStart, completion)) {
                    return@requestMultiMeta
                }
                finishFatal(input, ResourceLoadStage.SECTOR_BUNDLE_METADATA, status, null, completion)
                return@requestMultiMeta
            }

            // 2) 캐시 재사용: 버전 동일하고 forceUpdate=false
            val canUseCache = !forceUpdate && cachedVersion == meta.version_id
            val cachedZip = loadCachedZipFile(application, comboKey)
            if (canUseCache && cachedZip != null) {
                touchCachedZip(cachedZip)
                TJResourceLogger.i(
                    "(TJLabsMultiResourceManager) cache HIT // comboKey=$comboKey // versionId=${meta.version_id} // path=${cachedZip.absolutePath}"
                )
                processArchive(resourceManager, application, cachedZip, sectorIds, meta.version_id, isCached = true, isVersionVerified = true, input, imageLoadPolicy, tStart, metadataFetchMs, zipDownloadMs = 0, completion)
                return@requestMultiMeta
            }

            // 3) zip 다운로드
            val tDownload = nowMs()
            downloadZip(meta.url, application, comboKey) { downloadedFile ->
                if (downloadedFile == null) {
                    TJResourceLogger.w(
                        "(TJLabsMultiResourceManager) zip download failed // comboKey=$comboKey // version=${meta.version_id}"
                    )
                    if (!forceUpdate && tryFallback(resourceManager, application, "zip download failed version=${meta.version_id}", sectorIds, input, imageLoadPolicy, tStart, completion)) {
                        return@downloadZip
                    }
                    finishFatal(input, ResourceLoadStage.SECTOR_BUNDLE_DOWNLOAD, TJLabsResourceCode.UNCLASSIFIED_FAILURE_STATUS, meta.version_id, completion)
                    return@downloadZip
                }
                zipDownloadMs = elapsedMs(tDownload)
                TJResourceLogger.i(
                    "(TJLabsMultiResourceManager) zip download done // elapsedMs=$zipDownloadMs // bytes=${downloadedFile.length()}"
                )
                saveCachedVersion(application, comboKey, meta.version_id)
                evictOldCombinations(application, keep = comboKey)
                processArchive(resourceManager, application, downloadedFile, sectorIds, meta.version_id, isCached = false, isVersionVerified = true, input, imageLoadPolicy, tStart, metadataFetchMs, zipDownloadMs, completion)
            }
        }
    }

    /**
     * 가장 최근 성공한 조합 zip 안의 임의 경로 (예: 시뮬레이션 JSON) 을 꺼내 바이트로 반환.
     * iOS `TJLabsResourceManager.getBundleArchiveFileData(path:)` 매핑.
     *
     * @return 조합 로드 미완료 / 경로 미존재 시 null.
     */
    fun getBundleArchiveFileData(path: String): ByteArray? {
        val archive = latestArchive ?: return null
        return try {
            java.util.zip.ZipFile(archive).use { zip ->
                val entry = zip.getEntry(path) ?: return null
                zip.getInputStream(entry).use { it.readBytes() }
            }
        } catch (e: Exception) {
            TJResourceLogger.w("(TJLabsMultiResourceManager) getBundleArchiveFileData fail // path=$path // error=${e.localizedMessage}")
            null
        }
    }

    fun getLoadedSectorIds(): List<Int> = latestSectorIds
    fun getLatestVersionId(): String? = latestVersionId

    /**
     * 조합 캐시 전부 삭제 (zip + prefs version). 테스트 / 설정 화면 "리소스 다시 받기" 용.
     */
    fun clearCache(application: Application) {
        runCatching {
            val dir = multiBundleDir(application, createIfNeeded = false)
            if (dir?.exists() == true) dir.deleteRecursively()
        }
        runCatching {
            val prefs = application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            for (k in prefs.all.keys) if (k.startsWith(PREF_VERSION_PREFIX)) editor.remove(k)
            editor.apply()
        }
        latestArchive = null
        latestSectorIds = emptyList()
        latestVersionId = null
        TJResourceLogger.d("(TJLabsMultiResourceManager) clearCache done")
    }

    // =========================================================================
    // Internal: metadata / download
    // =========================================================================

    private fun requestMultiMeta(
        sectorIds: List<Int>,
        forceUpdate: Boolean,
        onResult: (status: Int, meta: SectorBundleMetaOutput?, errMsg: String?) -> Unit
    ) {
        val baseUrl = TJLabsResourceNetworkConstants.getBaseUrl(ResourceBundleType.JUPITER)
        TJLabsResourceNetworkConstants.genRetrofit(baseUrl) { retrofit, status, msg ->
            if (retrofit == null) {
                onResult(status, null, msg)
                return@genRetrofit
            }
            val api = retrofit.create(PostInput::class.java)
            // 멀티 endpoint 전용 server version — 단일 섹터용 2026-09-28 과 분리된 2026-10-02.
            // iOS `USER_MULTI_SECTOR_BUNDLE_SERVER_VERSION` 와 1:1. 단일 version 을 그대로 쓰면
            // 서버가 `{detail: Not Found}` 404 로 떨어뜨린다 (멀티 endpoint 네임스페이스가 다름).
            val serverVersion = TJLabsResourceNetworkConstants.getMultiBundleServerVersion()
            // Retrofit 의 @Query("sector_ids") List<Int> 는 기본적으로 반복 키로 직렬화되어
            // 서버 요구 (`sector_ids=20&sector_ids=112`) 를 그대로 만족. forceUpdate 는 아직
            // 쿼리 반영 안 함 — iOS 는 `_rt=<now>` + Cache-Control 로 중간 캐시 우회하나,
            // Android 는 OkHttp 캐시를 쓰지 않아 (Retrofit 기본) 생략.
            val call: Call<SectorBundleMetaOutput> = api.getSectorBundleV2(serverVersion, sectorIds)
            call.enqueue(object : Callback<SectorBundleMetaOutput> {
                override fun onResponse(call: Call<SectorBundleMetaOutput>, response: Response<SectorBundleMetaOutput>) {
                    if (response.isSuccessful) {
                        val body = response.body()
                        if (body != null) onResult(200, body, null)
                        else onResult(500, null, "empty body")
                    } else {
                        onResult(response.code(), null, response.errorBody()?.string().orEmpty())
                    }
                }
                override fun onFailure(call: Call<SectorBundleMetaOutput>, t: Throwable) {
                    onResult(TJLabsResourceCode.UNCLASSIFIED_FAILURE_STATUS, null, t.message)
                }
            })
        }
    }

    private fun downloadZip(url: String, application: Application, comboKey: String, onResult: (File?) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val dstFile = zipFileFor(application, comboKey, createDir = true) ?: run {
                withContext(Dispatchers.Main) { onResult(null) }
                return@launch
            }
            val tmpFile = File(dstFile.parentFile, "${dstFile.name}.tmp")
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 60_000
                    requestMethod = "GET"
                }
                val status = conn.responseCode
                if (status !in 200..299) {
                    conn.disconnect()
                    TJResourceLogger.w("(TJLabsMultiResourceManager) zip download HTTP $status")
                    withContext(Dispatchers.Main) { onResult(null) }
                    return@launch
                }
                conn.inputStream.use { input ->
                    FileOutputStream(tmpFile).use { output ->
                        input.copyTo(output, bufferSize = 32 * 1024)
                    }
                }
                conn.disconnect()
                if (dstFile.exists()) dstFile.delete()
                if (!tmpFile.renameTo(dstFile)) {
                    TJResourceLogger.w("(TJLabsMultiResourceManager) zip rename fail // ${tmpFile.absolutePath} -> ${dstFile.absolutePath}")
                    withContext(Dispatchers.Main) { onResult(null) }
                    return@launch
                }
                withContext(Dispatchers.Main) { onResult(dstFile) }
            } catch (e: Exception) {
                TJResourceLogger.w("(TJLabsMultiResourceManager) downloadZip exception // error=${e.localizedMessage}")
                runCatching { tmpFile.delete() }
                withContext(Dispatchers.Main) { onResult(null) }
            }
        }
    }

    // =========================================================================
    // Archive processing - 각 섹터를 TJLabsBundleDataManager 가 처리하도록 위임
    // =========================================================================

    private fun processArchive(
        resourceManager: TJLabsResourceManager,
        application: Application,
        archiveFile: File,
        sectorIds: List<Int>,
        versionId: String,
        isCached: Boolean,
        isVersionVerified: Boolean,
        input: MultiResourceLoadInput,
        imageLoadPolicy: ImageLoadPolicy,
        tStart: Long,
        metadataFetchMs: Long,
        zipDownloadMs: Long,
        completion: (MultiResourceLoadResult) -> Unit
    ) {
        latestArchive = archiveFile
        latestSectorIds = sectorIds
        latestVersionId = versionId

        CoroutineScope(Dispatchers.IO).launch {
            // 조합 archive 를 한 번만 extract. 섹터별로 bundle.json / 자원 파일을 꺼내 읽는다.
            // extractElapsedMs 는 모든 경로에서 수집 (track payload 용). Benchmark 모드는 추가로
            // nanoTime 수집해 수집기에 add — 두 측정은 서로 간섭 없이 공존.
            val extractStartMs = nowMs()
            val extractStartNs = if (TJLabsBundleDataManager.benchmarkTimingEnabled) System.nanoTime() else 0L
            val extractedRoot = extractArchive(archiveFile, application) ?: run {
                TJResourceLogger.e("(TJLabsMultiResourceManager) zip extract fail // archive=${archiveFile.absolutePath}")
                withContext(Dispatchers.Main) {
                    finishFatal(input, ResourceLoadStage.SECTOR_BUNDLE_DOWNLOAD, TJLabsResourceCode.UNCLASSIFIED_FAILURE_STATUS, versionId, completion)
                }
                return@launch
            }
            val extractElapsedMs = elapsedMs(extractStartMs)
            if (TJLabsBundleDataManager.benchmarkTimingEnabled) {
                TJLabsBundleDataManager.benchmarkStageTimings.addZipExtract((System.nanoTime() - extractStartNs) / 1_000_000)
            }
            val tPrepare = nowMs()
            val decodeStartNs = if (TJLabsBundleDataManager.benchmarkTimingEnabled) System.nanoTime() else 0L
            // 섹터별 병렬 처리 — 각 섹터는 자기 영역만 터치하고 bundleCache 저장을 완료한다.
            val sectorDeferreds = sectorIds.map { sectorId ->
                async {
                    bundleDataManager.processSectorFromArchive(
                        application = application,
                        sectorId = sectorId,
                        archiveFile = archiveFile,
                        extractedRoot = extractedRoot,
                        versionId = versionId,
                        imageLoadPolicy = imageLoadPolicy
                    )
                }
            }
            val sectorOutcomes = sectorDeferreds.awaitAll()
            if (TJLabsBundleDataManager.benchmarkTimingEnabled) {
                // bundle.json decode 는 parseBundleRaw 안에서 발생. 섹터 병렬 전체 wall-clock 에서
                // CSV parse 를 빼면 approx decode. 두 수가 중첩되면 pathCsvParse 가 더 큼. 보수적 lower bound.
                val parallelMs = (System.nanoTime() - decodeStartNs) / 1_000_000
                val csvAccumulated = TJLabsBundleDataManager.benchmarkStageTimings.pathCsvParseMs
                TJLabsBundleDataManager.benchmarkStageTimings.addBundleJsonDecode((parallelMs - csvAccumulated).coerceAtLeast(0))
            }
            TJResourceLogger.i(
                "(TJLabsMultiResourceManager) prepare (parallel) elapsedMs=${elapsedMs(tPrepare)} // sectors=$sectorIds"
            )

            val sectorResults = sectorIds.mapIndexed { idx, sid ->
                val outcome = sectorOutcomes[idx]
                SectorLoadResult(
                    sectorId = sid,
                    isSuccess = outcome.isSuccess,
                    failedStage = outcome.failures.firstOrNull { it.isCritical }?.stage,
                    failures = outcome.failures,
                    versionId = versionId,
                    isCached = isCached
                )
            }

            val allSuccess = sectorResults.all { it.isSuccess }
            val allFailures = sectorResults.flatMap { it.failures }
            val firstCriticalStage = allFailures.firstOrNull { it.isCritical }?.stage
            val eventCode = TJLabsResourceCode.loadResourcesEventCode(allSuccess)
            // 발화 경로 식별 — 로그 `mode=...` 라벨 + 소비자 (Jupiter) 서버 텔레메트리 payload 에 사용.
            val emitSource: EmitSource = when {
                !isVersionVerified -> EmitSource.FALLBACK  // 폴백 (meta/raw 실패 → 캐시 재사용)
                isCached -> EmitSource.MULTI_CACHED        // 조합 zip 캐시 재사용
                else -> EmitSource.MULTI_FRESH             // zip 새로 다운로드
            }
            val totalMs = elapsedMs(tStart)
            val message = "multiBundle: sectors=$sectorIds, versionId=$versionId, isCached=$isCached, versionVerified=$isVersionVerified, success=$allSuccess, emitSource=${emitSource.label}"
            val result = MultiResourceLoadResult(
                eventCode = eventCode,
                message = message,
                isSuccess = allSuccess,
                failedStage = if (allSuccess) null else firstCriticalStage,
                failures = allFailures,
                input = input,
                versionId = versionId,
                isCached = isCached,
                sectorResults = sectorResults,
                emitSource = emitSource,
                versionVerified = isVersionVerified,
                // stageTimings — iOS parity. 로더 상단에서 캡처한 metadata/zipDownload wall-clock
                // 을 processArchive 로 전달받아 전체 breakdown 완성 (iOS `(loadResources timing)` 매핑).
                // 캐시 HIT 경로는 zipDownloadMs=0, FALLBACK 경로는 metadataFetchMs 가 실패까지의 시간.
                stageTimings = MultiResourceStageTimings(
                    metadataFetchMs = metadataFetchMs,
                    zipDownloadMs = zipDownloadMs,
                    zipExtractMs = extractElapsedMs,
                    prepareParallelMs = elapsedMs(tPrepare),
                    totalMs = totalMs,
                ),
            )

            TJResourceLogger.i(
                "(TJLabsMultiResourceManager) TOTAL elapsedMs=$totalMs // $message"
            )

            withContext(Dispatchers.Main) {
                // 섹터별 BundleDataSnapshot 이 bundleCache 에 저장돼 있으므로, resourceManager.emitCachedSnapshot
                // 을 섹터 요청 순서대로 호출해 소비자 delegate 에 데이터 콜백을 발화한다 (iOS applyPreparedSector
                // + onBuildingsData/onTransitionData/... 매핑). 실패한 섹터는 emit skip 하되
                // onSectorResourceLoadFinished 는 항상 호출 (소비자가 섹터별 성공/실패 UI 를 그릴 수 있도록).
                // emitSource 를 명시해 postLoad timing 로그가 FRESH/CACHE/FALLBACK 을 구분할 수 있게 한다.
                sectorResults.forEach { sr ->
                    if (sr.isSuccess) {
                        resourceManager.emitCachedSnapshot(ResourceBundleType.JUPITER, sr.sectorId, emitSource)
                    }
                    resourceManager.delegate?.onSectorResourceLoadFinished(sr.sectorId, sr)
                }
                completion(result)
            }
        }
    }

    private fun finishFatal(
        input: MultiResourceLoadInput,
        stage: ResourceLoadStage,
        statusCode: Int,
        versionId: String?,
        completion: (MultiResourceLoadResult) -> Unit
    ) {
        val failure = ResourceLoadStageFailure(stage = stage, key = combinationKey(input.sectorIds), isCritical = true, statusCode = statusCode)
        // 실패는 아무 데이터도 발화 안 하지만, track payload 는 서버 추적에 필요하므로 명시.
        // emitSource=FRESH_LOAD (의미: 폴백 아니고 캐시 재사용도 아님 — 그냥 실패), versionVerified 는
        // true (서버 version 과 매칭 시도조차 못 함, "unverified" 상태 아님) 로 중립 세팅.
        val result = MultiResourceLoadResult(
            eventCode = TJLabsResourceCode.loadResourcesEventCode(false),
            message = "multiBundle failed: sectors=${input.sectorIds}, stage=$stage, status=$statusCode",
            isSuccess = false,
            failedStage = stage,
            failures = listOf(failure),
            input = input,
            versionId = versionId,
            isCached = false,
            sectorResults = emptyList(),
            emitSource = EmitSource.FRESH_LOAD,
            versionVerified = true,
            stageTimings = null,
        )
        CoroutineScope(Dispatchers.Main).launch { completion(result) }
    }

    // =========================================================================
    // Offline fallback - exact key → superset key
    // =========================================================================

    private fun tryFallback(
        resourceManager: TJLabsResourceManager,
        application: Application,
        reason: String,
        sectorIds: List<Int>,
        input: MultiResourceLoadInput,
        imageLoadPolicy: ImageLoadPolicy,
        tStart: Long,
        completion: (MultiResourceLoadResult) -> Unit
    ): Boolean {
        val fallback = findCachedArchive(application, sectorIds) ?: return false
        TJResourceLogger.w(
            "(TJLabsMultiResourceManager) fallback: $reason // comboKey=${fallback.comboKey} // version=${fallback.versionId}"
        )
        touchCachedZip(fallback.zipFile)
        // 폴백 경로는 metadata 요청 ~시도 중 실패까지의 wall-clock 을 metadataFetchMs 로 보존.
        // zipDownloadMs 는 다운로드 skip 이므로 0.
        processArchive(resourceManager, application, fallback.zipFile, sectorIds, fallback.versionId, isCached = true, isVersionVerified = false, input, imageLoadPolicy, tStart, metadataFetchMs = elapsedMs(tStart), zipDownloadMs = 0, completion)
        return true
    }

    private data class CachedArchive(val comboKey: String, val versionId: String, val zipFile: File)

    private fun findCachedArchive(application: Application, sectorIds: List<Int>): CachedArchive? {
        val exactKey = combinationKey(sectorIds)
        loadCachedVersion(application, exactKey)?.let { version ->
            loadCachedZipFile(application, exactKey)?.let { file ->
                return CachedArchive(exactKey, version, file)
            }
        }

        // 상위집합 폴백 — 요청 섹터 전부를 포함하는 다른 조합 중 mtime 최신 선택.
        val dir = multiBundleDir(application, createIfNeeded = false) ?: return null
        if (!dir.exists()) return null
        val requested = sectorIds.toSet()
        val candidates = (dir.listFiles() ?: emptyArray())
            .filter { it.extension == "zip" }
            .sortedByDescending { it.lastModified() }
        for (file in candidates) {
            val key = file.nameWithoutExtension
            val cachedIds = key.split("_").mapNotNull { it.toIntOrNull() }.toSet()
            if (cachedIds.isEmpty()) continue
            if (!requested.all { it in cachedIds }) continue
            val version = loadCachedVersion(application, key) ?: continue
            return CachedArchive(key, version, file)
        }
        return null
    }

    // =========================================================================
    // Combination cache lifecycle
    // =========================================================================

    /** 조합 키 생성 — 섹터 ID 를 숫자 오름차순 정렬 후 `_` 로 join. */
    fun combinationKey(sectorIds: List<Int>): String =
        sectorIds.sorted().joinToString("_")

    private fun multiBundleDir(application: Application, createIfNeeded: Boolean): File? {
        val root = File(application.cacheDir, "$CSV_DIR/$MULTI_DIR")
        if (createIfNeeded && !root.exists()) {
            if (!root.mkdirs()) return null
        }
        return root
    }

    private fun zipFileFor(application: Application, comboKey: String, createDir: Boolean): File? {
        val dir = multiBundleDir(application, createIfNeeded = createDir) ?: return null
        return File(dir, "$comboKey.zip")
    }

    private fun loadCachedZipFile(application: Application, comboKey: String): File? {
        val f = zipFileFor(application, comboKey, createDir = false) ?: return null
        return if (f.exists() && f.length() > 0) f else null
    }

    private fun loadCachedVersion(application: Application, comboKey: String): String? {
        val prefs = application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(PREF_VERSION_PREFIX + comboKey, null)
    }

    private fun saveCachedVersion(application: Application, comboKey: String, version: String) {
        application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_VERSION_PREFIX + comboKey, version)
            .apply()
    }

    /** mtime 을 현재로 갱신해 LRU 에서 최신으로 올림. */
    private fun touchCachedZip(file: File) {
        runCatching { file.setLastModified(System.currentTimeMillis()) }
    }

    private fun evictOldCombinations(application: Application, keep: String) {
        val dir = multiBundleDir(application, createIfNeeded = false) ?: return
        val zips = (dir.listFiles() ?: emptyArray()).filter { it.extension == "zip" }
        if (zips.size <= MAX_CACHED_COMBINATIONS) return
        val sorted = zips.sortedByDescending { it.lastModified() }
        for (file in sorted.drop(MAX_CACHED_COMBINATIONS)) {
            val key = file.nameWithoutExtension
            runCatching { file.delete() }
            application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(PREF_VERSION_PREFIX + key)
                .apply()
            TJResourceLogger.d("(TJLabsMultiResourceManager) evict // key=$key // path=${file.absolutePath}")
        }
    }

    // =========================================================================
    // Archive extraction (java.util.zip 로 전체 entry 를 디스크로 풀기)
    // =========================================================================

    /**
     * 조합 zip 을 공용 extracted 디렉토리로 풀고 그 File 을 돌려준다. 기존 extracted 디렉토리는
     * 통째로 삭제 후 재구성 — 조합 단위로 교체 (iOS 메모리 archive 보존 정책과 1:1 매핑).
     */
    private fun extractArchive(archiveFile: File, application: Application): File? {
        val extractRoot = File(application.cacheDir, "$CSV_DIR/$MULTI_DIR/extracted")
        runCatching { if (extractRoot.exists()) extractRoot.deleteRecursively() }
        if (!extractRoot.mkdirs() && !extractRoot.exists()) return null
        return try {
            java.util.zip.ZipFile(archiveFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    val outFile = File(extractRoot, entry.name)
                    outFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output, bufferSize = 32 * 1024)
                        }
                    }
                }
            }
            TJResourceLogger.i(
                "(TJLabsMultiResourceManager) extract done // archive=${archiveFile.name} // dst=${extractRoot.absolutePath}"
            )
            extractRoot
        } catch (e: Exception) {
            TJResourceLogger.e("(TJLabsMultiResourceManager) extract exception // error=${e.localizedMessage}")
            null
        }
    }

    // =========================================================================
    // Timing helpers
    // =========================================================================

    private fun nowMs(): Long = System.currentTimeMillis()
    private fun elapsedMs(start: Long): Long = nowMs() - start
}

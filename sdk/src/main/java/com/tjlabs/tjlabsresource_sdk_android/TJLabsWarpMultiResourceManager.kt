package com.tjlabs.tjlabsresource_sdk_android

import android.app.Application
import com.tjlabs.tjlabsresource_sdk_android.manager.TJLabsBundleDataManager
import com.tjlabs.tjlabsresource_sdk_android.util.TJResourceLogger
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

/**
 * Warp 멀티 섹터 번들 로드 (TJ-620, 2026-10-07+).
 *
 * `GET /{WARP_SECTOR_BUNDLE_SERVER_VERSION}/sectors/warp-bundle?sector_ids=X&sector_ids=Y&operating_system=Android`
 * 로 1~5개 섹터의 zip 번들을 한 번에 받고, 각 섹터 bundle.json 을 섹터별로 콜백으로 돌려준다.
 *
 * ── 왜 Jupiter multi 와 분리했나
 *  - Warp zip 안에는 `sectors/{id}/bundle.json` **단일 entry per sector** 만 있음 (iOS Notion
 *    스펙 기준). Jupiter V2 처럼 CSV/image/entrance_route 등을 디스크에 풀어둘 필요 없음 →
 *    `extract` 과정 전체 생략, ZipFile random-access 로 바로 추출.
 *  - Delegate 설계가 다름 — Jupiter 는 `onPathPixelData/onGeofenceData` 등 세분화,
 *    Warp 는 `WarpSectorOutput(buildings→levels→wards)` 통짜 트리.
 *  - On-prem (하나 PMS) 은 멀티 WARP endpoint 미지원 → cloud-only. OnPremRoutingState 분기
 *    없이 cloud 경로만 전담해서 분리성 유지.
 *
 * ── 저장
 *  - zip:  `cache/tj_bundle_csv/multiWarpBundle/{comboKey}.zip`  (LRU 최대 3 조합)
 *  - version: SharedPreferences `TJLabsWarpMultiBundlePrefs` / `TJLabsWarpMultiBundleVersion_{combo}`
 *  - `comboKey = sortedSectorIds.joinToString("_")` — Jupiter multi 와 동일 공식
 *
 * ── 안전성
 *  - [TJLabsBundleDataManager] 와 완전 분리된 object. 기존 single-sector / Jupiter multi 흐름에
 *    영향 없음.
 *  - [TJLabsResourceManager.loadWarpResource] (single) 와 공존 가능 — 서로의 캐시 디렉토리/
 *    SharedPreferences 가 겹치지 않도록 별도 네임스페이스 사용.
 */
object TJLabsWarpMultiResourceManager {

    /** 조합 캐시 디스크 유지 최대 개수. Jupiter multi 와 동일 정책. */
    private const val MAX_CACHED_COMBINATIONS = 3

    private const val CSV_DIR = "tj_bundle_csv"
    private const val MULTI_WARP_DIR = "multiWarpBundle"
    private const val PREF_NAME = "TJLabsWarpMultiBundlePrefs"
    private const val PREF_VERSION_PREFIX = "TJLabsWarpMultiBundleVersion_"

    /** parseWarpSector 재사용을 위한 BundleDataManager 인스턴스. 상태는 쓰지 않고 함수만 호출. */
    private val bundleDataManager = TJLabsBundleDataManager()

    /** 섹터별 콜백을 받고 싶으면 세팅. completion 만으로 집계 결과를 받으려면 null 로 둬도 됨. */
    @Volatile var delegate: TJLabsWarpMultiResourceManagerDelegate? = null

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 멀티 섹터 Warp 번들 로드. sectorIds 는 1~5개 (서버 제약).
     *
     * @param env 반드시 cloud auth 와 일치해야 (`setServerConfigForDevelopment` DEV 로 auth 받았으면 DEV).
     * @param forceUpdate true 면 version_id 가 같아도 디스크 캐시 무시하고 재다운로드.
     * @param completion 모든 섹터 처리 완료 후 호출. 각 섹터의 성공/실패와 데이터 포함.
     */
    fun loadResources(
        application: Application,
        provider: String,
        region: String,
        env: ResourceServerEnv = ResourceServerEnv.PROD,
        sectorIds: List<Int>,
        forceUpdate: Boolean = false,
        completion: (MultiWarpResourceLoadResult) -> Unit
    ) {
        val input = MultiWarpResourceLoadInput(
            provider = provider,
            region = region,
            env = env,
            sectorIds = sectorIds,
            forceUpdate = forceUpdate,
        )
        if (sectorIds.isEmpty() || sectorIds.size > 5) {
            delegate?.onWarpMultiError(null, ResourceError.Param)
            completion(
                MultiWarpResourceLoadResult(
                    isSuccess = false,
                    message = "sectorIds must have 1~5 entries (got ${sectorIds.size})",
                    input = input, versionId = null, isCached = false,
                    sectorResults = emptyList(),
                )
            )
            return
        }

        // Jupiter multi 와 동일한 provider/region 글로벌 세팅 (URL 조립 + FileDownloader).
        TJLabsResourceNetworkConstants.setServerURL(provider, region, env)
        TJLabsFileDownloader.provider = provider
        TJLabsFileDownloader.region = region

        val comboKey = combinationKey(sectorIds)
        val tStart = System.currentTimeMillis()
        val cachedVersion = loadCachedVersion(application, comboKey)

        TJResourceLogger.i(
            "(TJLabsWarpMultiResourceManager) loadResources start // sectorIds=$sectorIds // comboKey=$comboKey // cachedVersion=$cachedVersion // forceUpdate=$forceUpdate"
        )

        // 1) Metadata 요청 — version_id + signed zip URL
        requestMultiMeta(sectorIds) { metaStatus, meta, errMsg ->
            val metadataFetchMs = System.currentTimeMillis() - tStart
            if (meta == null) {
                TJResourceLogger.w("(TJLabsWarpMultiResourceManager) meta fail // status=$metaStatus // msg=$errMsg")
                delegate?.onWarpMultiError(null, ResourceError.Sector)
                completion(
                    MultiWarpResourceLoadResult(
                        isSuccess = false,
                        message = "metadata fetch failed: $errMsg",
                        input = input, versionId = null, isCached = false,
                        sectorResults = emptyList(),
                        metadataFetchMs = metadataFetchMs,
                        totalMs = System.currentTimeMillis() - tStart,
                    )
                )
                return@requestMultiMeta
            }

            // 2) 캐시 hit 여부 — version_id 가 같고 디스크 파일 존재하면 재다운로드 skip
            val cachedZip = loadCachedZipFile(application, comboKey)
            val useCache = !forceUpdate && cachedZip != null && cachedVersion == meta.version_id
            if (useCache) {
                TJResourceLogger.i(
                    "(TJLabsWarpMultiResourceManager) cache HIT // comboKey=$comboKey // versionId=${meta.version_id}"
                )
                processArchive(
                    application = application,
                    archiveFile = cachedZip!!,
                    sectorIds = sectorIds,
                    versionId = meta.version_id,
                    isCached = true,
                    input = input,
                    tStart = tStart,
                    metadataFetchMs = metadataFetchMs,
                    zipDownloadMs = 0L,
                    completion = completion,
                )
                return@requestMultiMeta
            }

            // 3) zip 다운로드
            val downloadStart = System.currentTimeMillis()
            val downloaded = downloadZip(application, comboKey, meta.url)
            val zipDownloadMs = System.currentTimeMillis() - downloadStart
            if (downloaded == null) {
                TJResourceLogger.w("(TJLabsWarpMultiResourceManager) zip download failed")
                delegate?.onWarpMultiError(null, ResourceError.Sector)
                completion(
                    MultiWarpResourceLoadResult(
                        isSuccess = false,
                        message = "zip download failed",
                        input = input, versionId = meta.version_id, isCached = false,
                        sectorResults = emptyList(),
                        metadataFetchMs = metadataFetchMs,
                        zipDownloadMs = zipDownloadMs,
                        totalMs = System.currentTimeMillis() - tStart,
                    )
                )
                return@requestMultiMeta
            }

            // 4) version 저장 + LRU prune
            saveCachedVersion(application, comboKey, meta.version_id)
            pruneLruCombinations(application)

            processArchive(
                application = application,
                archiveFile = downloaded,
                sectorIds = sectorIds,
                versionId = meta.version_id,
                isCached = false,
                input = input,
                tStart = tStart,
                metadataFetchMs = metadataFetchMs,
                zipDownloadMs = zipDownloadMs,
                completion = completion,
            )
        }
    }

    /** 모든 조합 zip 과 version prefs 삭제. */
    fun clearCache(application: Application) {
        val dir = multiBundleDir(application, createIfNeeded = false)
        if (dir?.isDirectory == true) dir.deleteRecursively()
        application.getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE)
            .edit().clear().apply()
        TJResourceLogger.d("(TJLabsWarpMultiResourceManager) clearCache done")
    }

    // ── Internals ─────────────────────────────────────────────────────────

    /** Metadata 요청 — signed zip URL + version_id. Jupiter multi 의 requestMultiMeta Warp 버전. */
    private fun requestMultiMeta(
        sectorIds: List<Int>,
        completion: (status: Int, meta: SectorBundleMetaOutput?, errMsg: String?) -> Unit
    ) {
        val serverVersion = TJLabsResourceNetworkConstants.getBundleServerVersion(ResourceBundleType.WARP)
        val baseUrl = TJLabsResourceNetworkConstants.getBaseUrl(ResourceBundleType.WARP)
        TJResourceLogger.d(
            "(TJLabsWarpMultiResourceManager) request meta // baseUrl=$baseUrl // version=$serverVersion // sectorIds=$sectorIds"
        )
        TJLabsResourceNetworkConstants.genRetrofit(baseUrl) { retrofit, authStatus, authMessage ->
            if (retrofit == null) {
                completion(authStatus, null, "auth: $authMessage")
                return@genRetrofit
            }
            val api = retrofit.create(PostInput::class.java)
            val call: Call<SectorBundleMetaOutput> =
                api.getSectorWarpBundle(serverVersion, sectorIds)
            call.enqueue(object : Callback<SectorBundleMetaOutput> {
                override fun onResponse(c: Call<SectorBundleMetaOutput>, resp: Response<SectorBundleMetaOutput>) {
                    val code = resp.code()
                    if (resp.isSuccessful && resp.body() != null) {
                        completion(code, resp.body(), null)
                    } else {
                        val err = resp.errorBody()?.string()?.take(200) ?: "http $code"
                        completion(code, null, err)
                    }
                }
                override fun onFailure(c: Call<SectorBundleMetaOutput>, t: Throwable) {
                    completion(0, null, "${t.javaClass.simpleName}: ${t.localizedMessage}")
                }
            })
        }
    }

    private fun downloadZip(application: Application, comboKey: String, url: String): File? {
        val target = zipFileFor(application, comboKey, createDir = true) ?: return null
        val tmp = File(target.parentFile, "${target.name}.part")
        return try {
            val req = Request.Builder().url(url).get().build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    TJResourceLogger.w("(TJLabsWarpMultiResourceManager) zip download HTTP ${'$'}{resp.code()}")
                    return null
                }
                val body = resp.body() ?: return null
                tmp.outputStream().use { out -> body.byteStream().use { it.copyTo(out) } }
                if (target.exists()) target.delete()
                if (!tmp.renameTo(target)) {
                    TJResourceLogger.w("(TJLabsWarpMultiResourceManager) zip rename fail")
                    return null
                }
                target
            }
        } catch (e: Exception) {
            TJResourceLogger.w("(TJLabsWarpMultiResourceManager) zip download exception: ${e.localizedMessage}")
            runCatching { tmp.delete() }
            null
        }
    }

    /** zip 안의 각 섹터 `sectors/{id}/bundle.json` 을 꺼내 WarpSectorOutput 로 파싱 → delegate/completion. */
    private fun processArchive(
        application: Application,
        archiveFile: File,
        sectorIds: List<Int>,
        versionId: String,
        isCached: Boolean,
        input: MultiWarpResourceLoadInput,
        tStart: Long,
        metadataFetchMs: Long,
        zipDownloadMs: Long,
        completion: (MultiWarpResourceLoadResult) -> Unit
    ) {
        val sectorResults = mutableListOf<WarpSectorLoadResult>()
        try {
            ZipFile(archiveFile).use { zf ->
                for (sid in sectorIds) {
                    val entryName = "sectors/$sid/bundle.json"
                    val entry = zf.getEntry(entryName)
                    if (entry == null) {
                        TJResourceLogger.w("(TJLabsWarpMultiResourceManager) zip entry missing: $entryName")
                        delegate?.onWarpMultiError(sid, ResourceError.Sector)
                        sectorResults.add(
                            WarpSectorLoadResult(sid, false, null, ResourceError.Sector, versionId, isCached)
                        )
                        continue
                    }
                    val jsonText = runCatching {
                        zf.getInputStream(entry).use { it.bufferedReader().readText() }
                    }.getOrNull()
                    if (jsonText.isNullOrBlank()) {
                        delegate?.onWarpMultiError(sid, ResourceError.Sector)
                        sectorResults.add(
                            WarpSectorLoadResult(sid, false, null, ResourceError.Sector, versionId, isCached)
                        )
                        continue
                    }
                    val parsed = runCatching {
                        val root = JSONObject(jsonText)
                        bundleDataManager.parseWarpSector(root)
                    }.getOrNull()
                    if (parsed == null) {
                        delegate?.onWarpMultiError(sid, ResourceError.Sector)
                        sectorResults.add(
                            WarpSectorLoadResult(sid, false, null, ResourceError.Sector, versionId, isCached)
                        )
                        continue
                    }
                    delegate?.onWarpMultiSectorData(sid, parsed)
                    sectorResults.add(
                        WarpSectorLoadResult(sid, true, parsed, null, versionId, isCached)
                    )
                }
            }
        } catch (e: Exception) {
            TJResourceLogger.e("(TJLabsWarpMultiResourceManager) processArchive fatal: ${e.localizedMessage}")
            delegate?.onWarpMultiError(null, ResourceError.Sector)
            completion(
                MultiWarpResourceLoadResult(
                    isSuccess = false,
                    message = "processArchive fatal: ${e.localizedMessage}",
                    input = input, versionId = versionId, isCached = isCached,
                    sectorResults = sectorResults,
                    metadataFetchMs = metadataFetchMs,
                    zipDownloadMs = zipDownloadMs,
                    totalMs = System.currentTimeMillis() - tStart,
                )
            )
            return
        }

        val allOk = sectorResults.isNotEmpty() && sectorResults.all { it.isSuccess }
        completion(
            MultiWarpResourceLoadResult(
                isSuccess = allOk,
                message = if (allOk) "ok" else "some sectors failed",
                input = input,
                versionId = versionId,
                isCached = isCached,
                sectorResults = sectorResults,
                metadataFetchMs = metadataFetchMs,
                zipDownloadMs = zipDownloadMs,
                totalMs = System.currentTimeMillis() - tStart,
            )
        )
    }

    // ── Combo key / version / LRU helpers (Jupiter multi 와 공식 동일, prefs 네임스페이스만 분리) ──

    private fun combinationKey(sectorIds: List<Int>): String =
        sectorIds.sorted().joinToString("_")

    private fun multiBundleDir(application: Application, createIfNeeded: Boolean): File? {
        val root = File(application.cacheDir, "$CSV_DIR/$MULTI_WARP_DIR")
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

    private fun loadCachedVersion(application: Application, comboKey: String): String? =
        application.getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE)
            .getString(PREF_VERSION_PREFIX + comboKey, null)

    private fun saveCachedVersion(application: Application, comboKey: String, versionId: String) {
        application.getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE)
            .edit().putString(PREF_VERSION_PREFIX + comboKey, versionId).apply()
    }

    /** mtime 가장 오래된 조합부터 삭제해 MAX_CACHED_COMBINATIONS 유지. */
    private fun pruneLruCombinations(application: Application) {
        val dir = multiBundleDir(application, createIfNeeded = false) ?: return
        val zips = dir.listFiles { f -> f.isFile && f.name.endsWith(".zip") }?.toList() ?: return
        if (zips.size <= MAX_CACHED_COMBINATIONS) return
        val sortedByOldest = zips.sortedBy { it.lastModified() }
        val toRemove = sortedByOldest.size - MAX_CACHED_COMBINATIONS
        val prefs = application.getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE)
        val editor = prefs.edit()
        sortedByOldest.take(toRemove).forEach { f ->
            val combo = f.nameWithoutExtension
            editor.remove(PREF_VERSION_PREFIX + combo)
            f.delete()
            TJResourceLogger.d("(TJLabsWarpMultiResourceManager) LRU prune // combo=$combo")
        }
        editor.apply()
    }
}

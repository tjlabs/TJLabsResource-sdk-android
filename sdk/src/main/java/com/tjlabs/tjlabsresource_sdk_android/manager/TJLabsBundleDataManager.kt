package com.tjlabs.tjlabsresource_sdk_android.manager

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.tjlabs.tjlabsresource_sdk_android.AffineTransParamOutput
import com.tjlabs.tjlabsresource_sdk_android.BuildingOutput
import com.tjlabs.tjlabsresource_sdk_android.Category
import com.tjlabs.tjlabsresource_sdk_android.CategoryData
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
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstants
import com.tjlabs.tjlabsresource_sdk_android.UnitData
import com.tjlabs.tjlabsresource_sdk_android.util.TJLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal data class BundleDataSnapshot(
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
    val entranceRouteUrlsByKey: Map<String, String>
)

internal class TJLabsBundleDataManager {
    companion object {
        private val bundleCache: MutableMap<Int, BundleDataSnapshot> = mutableMapOf()
        private const val PREF_NAME = "TJLabsResourcesPref"
        private const val CSV_DIR = "tj_bundle_csv"
        private const val PREF_BUNDLE_VERSION_PREFIX = "bundle_version_"
        private const val PREF_BUNDLE_URL_PREFIX = "bundle_url_"
        private const val PREF_PATH_VERSION_PREFIX = "graph_path_version_"
        private const val PREF_PATH_URL_PREFIX = "graph_path_url_"
        private const val PREF_PATH_FILE_PREFIX = "graph_path_file_"
        private const val PREF_ENTRANCE_VERSION_PREFIX = "entrance_route_version_"
        private const val PREF_ENTRANCE_URL_PREFIX = "entrance_route_url_"
        private const val PREF_ENTRANCE_FILE_PREFIX = "entrance_route_file_"
    }

    fun loadBundle(
        application: Application,
        sectorId: Int,
        completion: (Boolean, String, BundleDataSnapshot?) -> Unit
    ) {
        TJLogger.d("(TJLabsResource) loadBundle start // sectorId=$sectorId")
        requestBundleMeta(sectorId) { metaStatus, metaMsg, meta ->
            if ((metaStatus in 200 until 300) == false || meta == null) {
                TJLogger.d("(TJLabsResource) loadBundle failed@meta // status=$metaStatus // msg=$metaMsg // sectorId=$sectorId")
                completion(false, metaMsg, null)
                return@requestBundleMeta
            }

            val cached = bundleCache[sectorId]
            if (cached != null && cached.versionId == meta.version_id) {
                TJLogger.d("(TJLabsResource) loadBundle cache hit // sectorId=$sectorId // version=${meta.version_id}")
                completion(true, "(TJLabsResource) Success : use cached bundle", cached)
                return@requestBundleMeta
            }
            TJLogger.d("(TJLabsResource) loadBundle cache miss // sectorId=$sectorId // oldVersion=${cached?.versionId} // newVersion=${meta.version_id}")

            requestBundleRaw(meta.url) { rawStatus, rawMsg, raw ->
                if ((rawStatus in 200 until 300) == false || raw.isNullOrEmpty()) {
                    TJLogger.d("(TJLabsResource) loadBundle failed@bundleRaw // status=$rawStatus // msg=$rawMsg // url=${meta.url}")
                    completion(false, rawMsg, null)
                    return@requestBundleRaw
                }

                val parsed = parseBundleRaw(sectorId, meta, raw)
                if (parsed == null) {
                    TJLogger.d("(TJLabsResource) loadBundle failed@parseBundleRaw // sectorId=$sectorId // version=${meta.version_id} // url=${meta.url}")
                    completion(false, "(TJLabsResource) Error : parse bundle raw", null)
                    return@requestBundleRaw
                }

                enrichCsvData(application, sectorId, parsed) { csvSuccess, enriched ->
                    bundleCache[sectorId] = enriched
                    saveBundleMeta(application, sectorId, meta.version_id, meta.url)
                    TJLogger.d(
                        "(TJLabsResource) loadBundle done // sectorId=$sectorId // version=${meta.version_id} // csvSuccess=$csvSuccess"
                    )
                    completion(csvSuccess, "(TJLabsResource) Success : load bundle", enriched)
                }
            }
        }
    }

    fun testLoadBundle(
        sectorId: Int,
        completion: (Boolean, String, SectorBundleMetaOutput?, String?, SectorOutput?) -> Unit
    ) {
        requestBundleMeta(sectorId) { metaStatus, metaMsg, meta ->
            if ((metaStatus in 200 until 300) == false || meta == null) {
                completion(false, metaMsg, meta, null, null)
                return@requestBundleMeta
            }

            requestBundleRaw(meta.url) { rawStatus, rawMsg, raw ->
                if ((rawStatus in 200 until 300) == false || raw.isNullOrEmpty()) {
                    completion(false, rawMsg, meta, raw, null)
                    return@requestBundleRaw
                }

                val parsed = parseBundleRaw(sectorId, meta, raw)
                completion(
                    parsed != null,
                    if (parsed != null) "(TJLabsResource) Success : testLoadBundle" else "(TJLabsResource) Error : parse bundle raw",
                    meta,
                    raw,
                    parsed?.sectorData
                )
            }
        }
    }

    private fun requestBundleMeta(
        sectorId: Int,
        completion: (Int, String, SectorBundleMetaOutput?) -> Unit
    ) {
        TJLogger.d(
            "(TJLabsResource) request bundle meta // baseUrl=${TJLabsResourceNetworkConstants.getUserBaseURL()} // version=${TJLabsResourceNetworkConstants.getUserSectorBundleVersion()} // sectorId=$sectorId"
        )
        val retrofit = TJLabsResourceNetworkConstants.genRetrofit(TJLabsResourceNetworkConstants.getUserBaseURL())
        val api = retrofit.create(PostInput::class.java)
        api.getSectorBundle(
            TJLabsResourceNetworkConstants.getUserSectorBundleVersion(),
            sectorId
        ).enqueue(object : Callback<SectorBundleMetaOutput> {
            override fun onFailure(call: Call<SectorBundleMetaOutput>, t: Throwable) {
                TJLogger.d("(TJLabsResource) request bundle meta fail // sectorId=$sectorId // error=${t.localizedMessage}")
                completion(500, "(TJLabsResource) Failure : getSectorBundleMeta", null)
            }

            override fun onResponse(call: Call<SectorBundleMetaOutput>, response: Response<SectorBundleMetaOutput>) {
                val status = response.code()
                if (status in 200 until 300) {
                    val body = response.body()
                    TJLogger.d(
                        "(TJLabsResource) request bundle meta success // sectorId=$sectorId // status=$status // versionId=${body?.version_id} // url=${body?.url}"
                    )
                    completion(status, "(TJLabsResource) Success : getSectorBundleMeta", body)
                } else {
                    val errorBody = try { response.errorBody()?.string() } catch (_: Exception) { "read_error" }
                    TJLogger.d(
                        "(TJLabsResource) request bundle meta error // sectorId=$sectorId // status=$status // errorBody=$errorBody"
                    )
                    completion(status, "(TJLabsResource) Error : getSectorBundleMeta", null)
                }
            }
        })
    }

    private fun requestBundleRaw(
        bundleUrl: String,
        completion: (Int, String, String?) -> Unit
    ) {
        TJLogger.d("(TJLabsResource) request bundle raw // url=$bundleUrl")
        val retrofit = TJLabsResourceNetworkConstants.genRetrofit(TJLabsResourceNetworkConstants.getUserBaseURL())
        val api = retrofit.create(PostInput::class.java)
        api.getSectorBundleJsonRaw(bundleUrl).enqueue(object : Callback<okhttp3.ResponseBody> {
            override fun onFailure(call: Call<okhttp3.ResponseBody>, t: Throwable) {
                TJLogger.d("(TJLabsResource) request bundle raw fail // url=$bundleUrl // error=${t.localizedMessage}")
                completion(500, "(TJLabsResource) Failure : getSectorBundleJsonRaw", null)
            }

            override fun onResponse(call: Call<okhttp3.ResponseBody>, response: Response<okhttp3.ResponseBody>) {
                val status = response.code()
                if (status in 200 until 300) {
                    val raw = response.body()?.string()
                    TJLogger.d(
                        "(TJLabsResource) request bundle raw success // status=$status // url=$bundleUrl // rawSize=${raw?.length ?: 0}"
                    )
                    completion(status, "(TJLabsResource) Success : getSectorBundleJsonRaw", raw)
                } else {
                    val errorBody = try { response.errorBody()?.string() } catch (_: Exception) { "read_error" }
                    TJLogger.d(
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
        val hasPathUrls = snapshot.graphPathUrlsByKey.isNotEmpty()
        val hasEntranceUrls = snapshot.entranceRouteUrlsByKey.isNotEmpty()
        if (hasPathUrls == false && hasEntranceUrls == false) {
            TJLogger.d("(TJLabsResource) enrichCsvData skip // no csv urls")
            completion(true, snapshot)
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            TJLogger.d(
                "(TJLabsResource) enrichCsvData start // pathCsvCount=${snapshot.graphPathUrlsByKey.size} // entranceCsvCount=${snapshot.entranceRouteUrlsByKey.size}"
            )
            var isAllSuccess = true
            val pathPixelData = snapshot.pathPixelDataMap.toMutableMap()
            val entranceRouteData = snapshot.entranceRouteDataMap.toMutableMap()
            val imageData = snapshot.imageDataMap.toMutableMap()

            val pathTargets = snapshot.graphPathUrlsByKey.filterKeys { key ->
                val shouldLoad = key.contains("_D").not()
                if (!shouldLoad) {
                    TJLogger.d("(TJLabsResource) enrichCsvData skip@PathPixelCsv // key=$key // reason=level_name_contains__D")
                }
                shouldLoad
            }

            val pathResults = pathTargets.map { (key, url) ->
                async { key to fetchPathPixelData(application, sectorId, snapshot.versionId, key, url) }
            }.awaitAll()

            for ((key, parsed) in pathResults) {
                if (parsed != null) {
                    pathPixelData[key] = parsed
                } else {
                    isAllSuccess = false
                    TJLogger.d("(TJLabsResource) enrichCsvData failed@PathPixelCsv // key=$key // url=${snapshot.graphPathUrlsByKey[key]}")
                }
            }

            val entranceResults = snapshot.entranceRouteUrlsByKey.map { (key, url) ->
                async { key to fetchEntranceRouteData(application, sectorId, snapshot.versionId, key, url) }
            }.awaitAll()

            for ((key, parsed) in entranceResults) {
                if (parsed != null) {
                    entranceRouteData[key] = parsed
                } else {
                    isAllSuccess = false
                    TJLogger.d("(TJLabsResource) enrichCsvData failed@EntranceCsv // key=$key // url=${snapshot.entranceRouteUrlsByKey[key]}")
                }
            }

            val imageResults = snapshot.imageUrlsByKey.map { (key, url) ->
                async { key to fetchImageFromUrl(key, url) }
            }.awaitAll()

            for ((key, image) in imageResults) {
                if (image != null) {
                    imageData[key] = image
                } else {
                    TJLogger.d("(TJLabsResource) enrichCsvData failed@Image // key=$key // url=${snapshot.imageUrlsByKey[key]}")
                }
            }

            val enriched = snapshot.copy(
                pathPixelDataMap = pathPixelData,
                entranceRouteDataMap = entranceRouteData,
                imageDataMap = imageData
            )

            withContext(Dispatchers.Main) {
                TJLogger.d("(TJLabsResource) enrichCsvData done // success=$isAllSuccess")
                completion(isAllSuccess, enriched)
            }
        }
    }

    private fun fetchPathPixelData(
        application: Application,
        sectorId: Int,
        versionId: String,
        key: String,
        url: String
    ): PathPixelData? {
        TJLogger.d("(TJLabsResource) fetchPathPixelData start // key=$key // url=$url")
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
        ) ?: return null
        val parsed = parsePathPixelData(text)
        TJLogger.d(
            "(TJLabsResource) fetchPathPixelData success // key=$key // points=${parsed.road.firstOrNull()?.size ?: 0}"
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
        TJLogger.d("(TJLabsResource) fetchEntranceRouteData start // key=$key // url=$url")
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
        ) ?: return null
        val parsed = parseEntranceRouteData(text)
        TJLogger.d(
            "(TJLabsResource) fetchEntranceRouteData success // key=$key // routes=${parsed.route.size}"
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
        val versionKey = "${versionPrefix}${sectorId}_$key"
        val urlKey = "${urlPrefix}${sectorId}_$key"
        val fileKey = "${filePrefix}${sectorId}_$key"

        val savedVersion = prefs.getString(versionKey, null)
        val savedUrl = prefs.getString(urlKey, null)
        val savedPath = prefs.getString(fileKey, null)
        if (savedVersion == versionId && savedUrl == url && savedPath.isNullOrBlank().not()) {
            val cachedFile = File(savedPath!!)
            if (cachedFile.exists() && cachedFile.length() > 0) {
                try {
                    val cachedText = cachedFile.readText()
                    TJLogger.d(
                        "(TJLabsResource) csv cache hit // source=$source // key=$key // version=$versionId // path=${cachedFile.absolutePath} // bytes=${cachedText.length}"
                    )
                    return cachedText
                } catch (e: Exception) {
                    TJLogger.d(
                        "(TJLabsResource) csv cache read fail // source=$source // key=$key // path=${cachedFile.absolutePath} // error=${e.localizedMessage}"
                    )
                }
            } else {
                TJLogger.d(
                    "(TJLabsResource) csv cache stale // source=$source // key=$key // path=$savedPath"
                )
            }
        } else {
            TJLogger.d(
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
            val cacheDir = File(application.cacheDir, "$CSV_DIR/$sectorId")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            val fileName = "${md5(url)}.csv"
            val csvFile = File(cacheDir, fileName)
            csvFile.writeText(content)

            val prefs = application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val versionKey = "${versionPrefix}${sectorId}_$key"
            val urlKey = "${urlPrefix}${sectorId}_$key"
            val fileKey = "${filePrefix}${sectorId}_$key"
            prefs.edit()
                .putString(versionKey, versionId)
                .putString(urlKey, url)
                .putString(fileKey, csvFile.absolutePath)
                .apply()

            TJLogger.d(
                "(TJLabsResource) csv cache save // source=$source // key=$key // version=$versionId // path=${csvFile.absolutePath} // bytes=${content.length}"
            )
        } catch (e: Exception) {
            TJLogger.d(
                "(TJLabsResource) csv cache save fail // source=$source // key=$key // error=${e.localizedMessage}"
            )
        }
    }

    private fun saveBundleMeta(application: Application, sectorId: Int, versionId: String, bundleUrl: String) {
        val prefs = application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("$PREF_BUNDLE_VERSION_PREFIX$sectorId", versionId)
            .putString("$PREF_BUNDLE_URL_PREFIX$sectorId", bundleUrl)
            .apply()
    }

    private fun md5(value: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
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
                TJLogger.d(
                    "(TJLabsResource) fetchTextFromUrl http error // source=$source // url=$urlString // status=$status // error=${errorBody.take(300)}"
                )
                connection.disconnect()
                return null
            }

            val text = connection.inputStream.bufferedReader().use { it.readText() }
            TJLogger.d(
                "(TJLabsResource) fetchTextFromUrl http success // source=$source // status=$status // bytes=${text.length}"
            )
            connection.disconnect()
            text
        } catch (e: Exception) {
            TJLogger.d(
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
                TJLogger.d(
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
                TJLogger.d("(TJLabsResource) fetchImageFromUrl decode fail // key=$key // url=$urlString")
            } else {
                TJLogger.d("(TJLabsResource) fetchImageFromUrl success // key=$key // size=${bitmap.width}x${bitmap.height}")
            }
            bitmap
        } catch (e: Exception) {
            TJLogger.d(
                "(TJLabsResource) fetchImageFromUrl exception // key=$key // url=$urlString // error=${e.localizedMessage}"
            )
            null
        }
    }

    private fun parseBundleRaw(
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
                    levels.add(LevelOutput(id = levelId, name = levelName, image = imageUrl))
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

                    val graphObj = levelObj.optJSONObject("graph")
                    if (isDebugLevel.not() && graphObj != null) {
                        val nodes = parseGraphNodes(graphObj.optJSONArray("nodes"))
                        val links = parseGraphLinks(graphObj.optJSONArray("links"))
                        val linkGroups = parseGraphLinkGroups(graphObj.optJSONArray("link_groups"))

                        if (nodes != null && links != null) {
                            nodeMap[levelKey] = buildNodeDict(nodes)
                            linkMap[levelKey] = buildLinkDict(links, linkGroups ?: emptyList())
                        }

                        val pathUrl = graphObj.optJSONObject("path")?.optString("url").orEmpty()
                        if (pathUrl.isNotBlank()) {
                            graphPathUrls[levelKey] = pathUrl
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

            val sectorData = SectorOutput(
                id = root.optInt("id"),
                name = root.optString("name"),
                debug = root.optBoolean("debug"),
                buildings = buildings
            )

            BundleDataSnapshot(
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
                entranceRouteUrlsByKey = entranceRouteUrls
            )
        } catch (e: Exception) {
            TJLogger.d("(TJLabsResource) parseBundleRaw failed // sectorId=$sectorId // error=${e.localizedMessage}")
            null
        }
    }

    private fun parseAffine(obj: JSONObject?): AffineTransParamOutput? {
        if (obj == null) return null
        return AffineTransParamOutput(
            xx_scale = obj.optFloatOrDefault("xx_scale"),
            xy_shear = obj.optFloatOrDefault("xy_shear"),
            x_translation = obj.optFloatOrDefault("x_translation"),
            yx_shear = obj.optFloatOrDefault("yx_shear"),
            yy_scale = obj.optFloatOrDefault("yy_scale"),
            y_translation = obj.optFloatOrDefault("y_translation"),
            heading_offset = obj.optFloatOrDefault("heading_offset")
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
            TJLogger.d("(TJLabsResource) unknown category // raw=$raw")
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

    private fun JSONObject.optFloatOrNull(key: String): Float? {
        if (has(key) == false || isNull(key)) return null
        val value = opt(key)
        return when (value) {
            is Number -> value.toFloat()
            is String -> value.toFloatOrNull()
            else -> null
        }
    }
}

package com.tjlabs.tjlabsresource_sdk_android.manager

import android.app.Application
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.tjlabs.tjlabsresource_sdk_android.BuildingOutput
import com.tjlabs.tjlabsresource_sdk_android.GraphLevelLink
import com.tjlabs.tjlabsresource_sdk_android.GraphLevelLinkGroup
import com.tjlabs.tjlabsresource_sdk_android.GraphLevelNode
import com.tjlabs.tjlabsresource_sdk_android.GraphLevelPath
import com.tjlabs.tjlabsresource_sdk_android.GraphResourceType
import com.tjlabs.tjlabsresource_sdk_android.LevelIdOsInput
import com.tjlabs.tjlabsresource_sdk_android.TJLabsFileDownloader.downloadCSVFile
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstants
import com.tjlabs.tjlabsresource_sdk_android.util.TJLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

internal interface GraphsDelegate {
    fun onGraphNodesData(key: String, data: List<GraphLevelNode>)
    fun onGraphLinksData(key: String, data: List<GraphLevelLink>)
    fun onGraphLinkGroupsData(key: String, data: List<GraphLevelLinkGroup>)
    fun onGraphPathsData(key: String, data: List<GraphLevelPath>)
    fun onGraphError(key: String, type: GraphResourceType)
}

internal class TJLabsGraphsManager {
    private lateinit var application: Application
    private lateinit var sharedPrefs : SharedPreferences

    companion object {
        val graphNodesDataMap: MutableMap<String, List<GraphLevelNode>> = mutableMapOf()
        val graphLinksDataMap: MutableMap<String, List<GraphLevelLink>> = mutableMapOf()
        val graphLinkGroupsDataMap: MutableMap<String, List<GraphLevelLinkGroup>> = mutableMapOf()
        val graphPathsDataMap: MutableMap<String, List<GraphLevelPath>> = mutableMapOf()
    }

    var delegate: GraphsDelegate? = null

    fun init(application: Application, sharedPreferences: SharedPreferences) {
        this.application = application
        this.sharedPrefs = sharedPreferences
    }

    fun loadGraphs(
        sectorId: Int,
        buildingsData: List<BuildingOutput>,
        completion: (Boolean) -> Unit
    ) {
        val mainHandler = Handler(Looper.getMainLooper())

        val lock = Any()
        var isAllSuccess = true
        var hasAsyncWork = false

        fun updateSuccess(success: Boolean) {
            if (!success) {
                synchronized(lock) {
                    isAllSuccess = false
                }
            }
        }

        val tasks = mutableListOf<(CountDownLatch) -> Unit>()

        for (building in buildingsData) {
            for (level in building.levels) {
                if (level.name.contains("_D")) continue

                val key = "${sectorId}_${building.name}_${level.name}"

                // Nodes
                val cachedNodes = graphNodesDataMap[key]
                if (cachedNodes != null) {
                    delegate?.onGraphNodesData(key, cachedNodes)
                } else {
                    hasAsyncWork = true
                    tasks += { latch ->
                        updateLevelNodes(key, level.id) { success ->
                            updateSuccess(success)
                            latch.countDown()
                        }
                    }
                }

                // Links
                val cachedLinks = graphLinksDataMap[key]
                if (cachedLinks != null) {
                    delegate?.onGraphLinksData(key, cachedLinks)
                } else {
                    hasAsyncWork = true
                    tasks += { latch ->
                        updateLevelLinks(key, level.id) { success ->
                            updateSuccess(success)
                            latch.countDown()
                        }
                    }
                }

                // Link groups
                val cachedLinkGroups = graphLinkGroupsDataMap[key]
                if (cachedLinkGroups != null) {
                    delegate?.onGraphLinkGroupsData(key, cachedLinkGroups)
                } else {
                    hasAsyncWork = true
                    tasks += { latch ->
                        updateLevelLinkGroups(key, level.id) { success ->
                            updateSuccess(success)
                            latch.countDown()
                        }
                    }
                }

                // Paths
                val cachedPaths = graphPathsDataMap[key]
                if (cachedPaths != null) {
                    delegate?.onGraphPathsData(key, cachedPaths)
                } else {
                    hasAsyncWork = true
                    tasks += { latch ->
                        updateLevelPaths(key, level.id) { success ->
                            updateSuccess(success)
                            latch.countDown()
                        }
                    }
                }
            }
        }

        if (!hasAsyncWork) {
            completion(true)
            return
        }

        val latch = CountDownLatch(tasks.size)
        val executor = Executors.newFixedThreadPool(4)

        tasks.forEach { task ->
            executor.execute {
                task(latch)
            }
        }

        Executors.newSingleThreadExecutor().execute {
            latch.await()
            mainHandler.post {
                completion(isAllSuccess)
            }
        }
    }

    fun updateLevelNodes(
        key: String,
        levelId: Int,
        completion: (Boolean) -> Unit
    ) {
        val input = LevelIdOsInput(level_id = levelId)
        TJLabsResourceNetworkManager.getLevelNodes(
            TJLabsResourceNetworkConstants.getUserBaseURL(),
            input,
            TJLabsResourceNetworkConstants.getUserGraphsNodesServerVersion()
        ) { status, msg, result ->
            if (status != 200) {
                TJLogger.d(msg)
                delegate?.onGraphError(key, GraphResourceType.NODES)
                completion(false)
                return@getLevelNodes
            }
            if (result != null) {
                graphNodesDataMap[key] = result.nodes
                delegate?.onGraphNodesData(key, result.nodes)
                completion(true)
            } else {
                delegate?.onGraphError(key, GraphResourceType.NODES)
                completion(false)
            }
        }
    }

    fun updateLevelLinks(
        key: String,
        levelId: Int,
        completion: (Boolean) -> Unit
    ) {
        val input = LevelIdOsInput(level_id = levelId)
        TJLabsResourceNetworkManager.getLevelLinks(
            TJLabsResourceNetworkConstants.getUserBaseURL(),
            input,
            TJLabsResourceNetworkConstants.getUserGraphsLinksServerVersion()
        ) { status, msg, result ->
            if (status != 200) {
                TJLogger.d(msg)
                delegate?.onGraphError(key, GraphResourceType.LINKS)
                completion(false)
                return@getLevelLinks
            }
            if (result != null) {
                graphLinksDataMap[key] = result.links
                delegate?.onGraphLinksData(key, result.links)
                completion(true)
            } else {
                delegate?.onGraphError(key, GraphResourceType.LINKS)
                completion(false)
            }
        }
    }

    fun updateLevelLinkGroups(
        key: String,
        levelId: Int,
        completion: (Boolean) -> Unit
    ) {
        val input = LevelIdOsInput(level_id = levelId)
        TJLabsResourceNetworkManager.getLevelLinkGroups(
            TJLabsResourceNetworkConstants.getUserBaseURL(),
            input,
            TJLabsResourceNetworkConstants.getUserGraphsLinkGroupsServerVersion()
        ) { status, msg, result ->
            if (status != 200) {
                TJLogger.d(msg)
                delegate?.onGraphError(key, GraphResourceType.LINK_GROUPS)
                completion(false)
                return@getLevelLinkGroups
            }
            if (result != null) {
                graphLinkGroupsDataMap[key] = result.link_groups
                delegate?.onGraphLinkGroupsData(key, result.link_groups)
                completion(true)
            } else {
                delegate?.onGraphError(key, GraphResourceType.LINK_GROUPS)
                completion(false)
            }
        }
    }

    fun updateLevelPaths(
        key: String,
        levelId: Int,
        completion: (Boolean) -> Unit
    ) {
        val input = LevelIdOsInput(level_id = levelId)
        TJLabsResourceNetworkManager.getLevelPaths(
            TJLabsResourceNetworkConstants.getUserBaseURL(),
            input,
            TJLabsResourceNetworkConstants.getUserGraphsPathsServerVersion()
        ) { status, msg, result ->
            if (status != 200) {
                TJLogger.d(msg)
                delegate?.onGraphError(key, GraphResourceType.PATHS)
                completion(false)
                return@getLevelPaths
            }
            if (result == null) {
                delegate?.onGraphError(key, GraphResourceType.PATHS)
                completion(false)
                return@getLevelPaths
            }

            val pathsUrl = result.csv
            val cachedUrl = loadGraphPathsServerUrlFromCache(key)

            if (!cachedUrl.isNullOrEmpty() && cachedUrl == pathsUrl) {
                val cached = loadGraphPathsFileFromCache(key)
                if (cached != null) {
                    graphPathsDataMap[key] = cached
                    delegate?.onGraphPathsData(key, cached)
                    completion(true)
                    return@getLevelPaths
                }
            }

            val sectorId = key.substringBefore("_").toIntOrNull() ?: 0
            updateGraphPaths(key, sectorId, pathsUrl) { success ->
                completion(success)
            }
        }
    }

    private fun updateGraphPaths(
        key: String,
        sectorId: Int,
        graphPathsUrlFromServer: String,
        completion: (Boolean) -> Unit
    ) {
        val parsedUrl = try {
            URL(graphPathsUrlFromServer)
        } catch (e: Exception) {
            delegate?.onGraphError(key, GraphResourceType.PATHS)
            completion(false)
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val (file, dir, _) = downloadCSVFile(
                    application,
                    parsedUrl,
                    sectorId,
                    "${key}_paths.csv"
                )

                if (file != null) {
                    val fileText = file.readText()
                    val paths = parseGraphPathsCsv(fileText)

                    graphPathsDataMap[key] = paths
                    saveGraphPathsCacheDirToCache(key, dir)
                    saveGraphPathsUrlToCache(key, graphPathsUrlFromServer)

                    withContext(Dispatchers.Main) {
                        delegate?.onGraphPathsData(key, paths)
                    }
                    completion(true)
                } else {
                    withContext(Dispatchers.Main) {
                        delegate?.onGraphError(key, GraphResourceType.PATHS)
                    }
                    completion(false)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    delegate?.onGraphError(key, GraphResourceType.PATHS)
                }
                completion(false)
            }
        }
    }

    private fun loadGraphPathsServerUrlFromCache(key: String): String? {
        val cacheKey = "TJLabsGraphPathsURL_$key"
        return sharedPrefs.getString(cacheKey, null)
    }

    private fun saveGraphPathsUrlToCache(key: String, graphPathsUrlFromServer: String) {
        val cacheKey = "TJLabsGraphPathsURL_$key"
        sharedPrefs.edit().putString(cacheKey, graphPathsUrlFromServer).apply()
        TJLogger.d("Info: save $key GraphPaths URL $graphPathsUrlFromServer")
    }

    private fun loadGraphPathsCacheDirFromCache(key: String): String? {
        val cacheKey = "TJLabsGraphPathsDir_$key"
        return sharedPrefs.getString(cacheKey, null)
    }

    private fun saveGraphPathsCacheDirToCache(key: String, graphPathsDir: String) {
        val cacheKey = "TJLabsGraphPathsDir_$key"
        sharedPrefs.edit().putString(cacheKey, graphPathsDir).apply()
        TJLogger.d("Info: save $key GraphPaths Dir $graphPathsDir")
    }

    private fun loadGraphPathsFileFromCache(key: String): List<GraphLevelPath>? {
        val loadedLocalDir = loadGraphPathsCacheDirFromCache(key)
        if (!loadedLocalDir.isNullOrEmpty()) {
            val file = File(loadedLocalDir)
            if (file.exists()) {
                val text = file.readText()
                return parseGraphPathsCsv(text)
            }
        }
        return null
    }

    private fun parseGraphPathsCsv(data: String): List<GraphLevelPath> {
        val result = mutableListOf<GraphLevelPath>()
        val lines = data.lines()
        val bracketRegex = Regex("\\[[^\\]]*\\]")

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            if (line.contains("encoding=")) continue

            val match = bracketRegex.find(line)
            val headings = if (match != null) {
                match.value
                    .removePrefix("[")
                    .removeSuffix("]")
                    .split(",")
                    .mapNotNull { it.trim().toDoubleOrNull()?.toInt() }
            } else {
                emptyList()
            }

            val cleaned = if (match != null) line.replace(match.value, "") else line
            val parts = cleaned.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            if (parts.size < 3) {
                TJLogger.d("Invalid graph path line: $line")
                continue
            }

            val xVal = parts[0].toFloatOrNull()
            val yVal = parts[1].toFloatOrNull()
            val velocityVal = parts[2].toFloatOrNull()

            if (xVal == null || yVal == null || velocityVal == null) {
                TJLogger.d("Parse error: $line")
                continue
            }

            result.add(
                GraphLevelPath(
                    x = xVal,
                    y = yVal,
                    available_headings = headings,
                    velocity_scale = velocityVal
                )
            )
        }

        return result
    }
}

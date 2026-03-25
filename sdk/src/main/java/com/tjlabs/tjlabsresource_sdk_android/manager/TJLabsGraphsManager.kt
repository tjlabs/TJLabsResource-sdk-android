package com.tjlabs.tjlabsresource_sdk_android.manager

import android.app.Application
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.tjlabs.tjlabsresource_sdk_android.BuildingOutput
import com.tjlabs.tjlabsresource_sdk_android.GraphLevelLink
import com.tjlabs.tjlabsresource_sdk_android.GraphLevelLinkFeature
import com.tjlabs.tjlabsresource_sdk_android.GraphLevelLinkGroup
import com.tjlabs.tjlabsresource_sdk_android.GraphLevelNode
import com.tjlabs.tjlabsresource_sdk_android.GraphLevelPath
import com.tjlabs.tjlabsresource_sdk_android.GraphResourceType
import com.tjlabs.tjlabsresource_sdk_android.LevelIdOsInput
import com.tjlabs.tjlabsresource_sdk_android.LinkData
import com.tjlabs.tjlabsresource_sdk_android.NodeData
import com.tjlabs.tjlabsresource_sdk_android.NodeDirection
import com.tjlabs.tjlabsresource_sdk_android.NodeLinkType
import com.tjlabs.tjlabsresource_sdk_android.PathPixelData
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
    fun onGraphLinkFeatureData(key: String, data: List<GraphLevelLinkFeature>)
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
        val graphLinkFeaturesDataMap: MutableMap<String, List<GraphLevelLinkFeature>> = mutableMapOf()

        val graphPathsDataMap: MutableMap<String, List<GraphLevelPath>> = mutableMapOf()
        val nodeDataMap: MutableMap<String, Map<Int, NodeData>> = mutableMapOf()
        val linkDataMap: MutableMap<String, Map<Int, LinkData>> = mutableMapOf()
        val pathPixelDataMap: MutableMap<String, PathPixelData> = mutableMapOf()
    }

    var delegate: GraphsDelegate? = null
    var nodeLinkDelegate: NodeLinkDelegate? = null
    var pathPixelDelegate: PathPixelDelegate? = null

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

    fun loadNodeLinks(
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
                synchronized(lock) { isAllSuccess = false }
            }
        }

        val tasks = mutableListOf<(CountDownLatch) -> Unit>()

        for (building in buildingsData) {
            for (level in building.levels) {
                if (level.name.contains("_D")) continue

                val key = "${sectorId}_${building.name}_${level.name}"

                val cachedNode = nodeDataMap[key]
                val cachedLink = linkDataMap[key]
                if (cachedNode != null && cachedLink != null) {
                    mainHandler.post {
                        nodeLinkDelegate?.onNodeLinkData(key, NodeLinkType.NODE, cachedNode)
                        nodeLinkDelegate?.onNodeLinkData(key, NodeLinkType.LINK, cachedLink)
                    }
                    continue
                }

                hasAsyncWork = true

                tasks += { latch ->
                    ensureGraphData(key, level.id) { success ->
                        if (!success) {
                            mainHandler.post {
                                nodeLinkDelegate?.onNodeLinkError(key, NodeLinkType.NODE)
                                nodeLinkDelegate?.onNodeLinkError(key, NodeLinkType.LINK)
                            }
                            updateSuccess(false)
                            latch.countDown()
                            return@ensureGraphData
                        }

                        val nodeDict = buildNodeDictFromGraphs(key)
                        val linkDict = buildLinkDictFromGraphs(key)

                        mainHandler.post {
                            if (nodeDict != null && linkDict != null) {
                                nodeDataMap[key] = nodeDict
                                linkDataMap[key] = linkDict
                                // keep legacy maps in sync
                                TJLabsNodeLinkManager.nodeDataMap[key] = nodeDict
                                TJLabsNodeLinkManager.linkDataMap[key] = linkDict

                                nodeLinkDelegate?.onNodeLinkData(key, NodeLinkType.NODE, nodeDict)
                                nodeLinkDelegate?.onNodeLinkData(key, NodeLinkType.LINK, linkDict)
                                updateSuccess(true)
                            } else {
                                nodeLinkDelegate?.onNodeLinkError(key, NodeLinkType.NODE)
                                nodeLinkDelegate?.onNodeLinkError(key, NodeLinkType.LINK)
                                updateSuccess(false)
                            }
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
            executor.execute { task(latch) }
        }

        Executors.newSingleThreadExecutor().execute {
            latch.await()
            mainHandler.post { completion(isAllSuccess) }
        }
    }

    fun loadPathPixel(
        region: String,
        sectorId: Int,
        buildingsData: List<BuildingOutput>,
        completion: (Boolean) -> Unit
    ) {
        val mainHandler = Handler(Looper.getMainLooper())

        loadPathPixelUrl(sectorId, buildingsData) { pathPixelUrl ->
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

            for ((key, value) in pathPixelUrl) {
                val pathPixelUrlFromCache = loadPathPixelServerUrlFromCache(key)

                if (pathPixelUrlFromCache != null && pathPixelUrlFromCache == value) {
                    val ppData = loadPathPixelFileFromCache(key)
                    if (ppData != null) {
                        pathPixelDataMap[key] = ppData
                        TJLabsPathPixelManager.ppDataMap[key] = ppData
                        pathPixelDelegate?.onPathPixelData(key, ppData)
                        continue
                    }
                }

                hasAsyncWork = true
                tasks += { latch ->
                    updatePathPixel(key, sectorId, value) { isSuccess ->
                        updateSuccess(isSuccess)
                        latch.countDown()
                    }
                }
            }

            if (!hasAsyncWork) {
                completion(true)
                return@loadPathPixelUrl
            }

            val latch = CountDownLatch(tasks.size)
            val executor = Executors.newFixedThreadPool(4)

            tasks.forEach { task ->
                executor.execute { task(latch) }
            }

            Executors.newSingleThreadExecutor().execute {
                latch.await()
                mainHandler.post {
                    completion(isAllSuccess)
                }
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

    fun updateLevelLinkFeatures(
        key: String,
        levelId: Int,
        completion: (Boolean) -> Unit
    ) {
        val input = LevelIdOsInput(level_id = levelId)
        TJLabsResourceNetworkManager.getLevelLinkFeatures(
            TJLabsResourceNetworkConstants.getUserBaseURL(),
            input,
            TJLabsResourceNetworkConstants.getUserGraphsLinkFeaturesServerVersion()
        ) { status, msg, result ->
            if (status != 200) {
                TJLogger.d(msg)
                delegate?.onGraphError(key, GraphResourceType.LINK_GROUPS)
                completion(false)
                return@getLevelLinkFeatures
            }
            if (result != null) {
                graphLinkFeaturesDataMap[key] = result.link_features
                delegate?.onGraphLinkFeatureData(key, result.link_features)
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

    fun updateLevelPathPixel(
        key: String,
        sectorId: Int,
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
                pathPixelDelegate?.onPathPixelError(key)
                completion(false)
            }

            if (result != null) {
                val ppUrl = result.csv
                val pathPixelUrlFromCache = loadPathPixelServerUrlFromCache(key)
                if (pathPixelUrlFromCache != null) {
                    if (pathPixelUrlFromCache == ppUrl) {
                        val ppData = loadPathPixelFileFromCache(key)
                        if (ppData != null) {
                            pathPixelDataMap[key] = ppData
                            TJLabsPathPixelManager.ppDataMap[key] = ppData
                            pathPixelDelegate?.onPathPixelData(key, ppData)
                        } else {
                            updatePathPixel(key, sectorId, ppUrl) { isSuccess ->
                                completion(isSuccess)
                            }
                        }
                    } else {
                        updatePathPixel(key, sectorId, ppUrl) { isSuccess ->
                            completion(isSuccess)
                        }
                    }
                } else {
                    updatePathPixel(key, sectorId, ppUrl) { isSuccess ->
                        completion(isSuccess)
                    }
                }
            } else {
                pathPixelDelegate?.onPathPixelError(key)
                completion(false)
            }
        }
    }

    private fun ensureGraphData(key: String, levelId: Int, completion: (Boolean) -> Unit) {
        val cachedNodes = graphNodesDataMap[key]
        val cachedLinks = graphLinksDataMap[key]
        val cachedLinkGroups = graphLinkGroupsDataMap[key]

        if (cachedNodes != null && cachedLinks != null && cachedLinkGroups != null) {
            completion(true)
            return
        }

        val latch = CountDownLatch(3)
        var isAllSuccess = true
        val lock = Any()

        fun updateSuccess(success: Boolean) {
            if (!success) {
                synchronized(lock) {
                    isAllSuccess = false
                }
            }
        }

        updateLevelNodes(key, levelId) { success ->
            updateSuccess(success)
            latch.countDown()
        }
        updateLevelLinks(key, levelId) { success ->
            updateSuccess(success)
            latch.countDown()
        }
        updateLevelLinkGroups(key, levelId) { success ->
            updateSuccess(success)
            latch.countDown()
        }

        Executors.newSingleThreadExecutor().execute {
            latch.await()
            completion(isAllSuccess)
        }
    }

    internal fun buildNodeDictFromGraphs(key: String): Map<Int, NodeData>? {
        val nodes = graphNodesDataMap[key] ?: return null
        val result = mutableMapOf<Int, NodeData>()

        for (node in nodes) {
            val inHeadings = node.available_in_headings.toSet()
            val outHeadings = node.available_out_headings.toSet()
            val endOnlyHeadings = inHeadings.subtract(outHeadings)

            val mergedHeadings = LinkedHashSet<Int>()
            mergedHeadings.addAll(node.available_in_headings)
            mergedHeadings.addAll(node.available_out_headings)

            val directions = mutableListOf<NodeDirection>()
            for (heading in mergedHeadings) {
                directions.add(
                    NodeDirection(
                        heading = heading.toFloat(),
                        is_end = endOnlyHeadings.contains(heading)
                    )
                )
            }

            val connectedNodes = node.connected_nodes.map { it.number }
            val connectedLinks = node.connected_links.map { it.number }

            result[node.number] = NodeData(
                number = node.number,
                coords = listOf(node.x.toFloat(), node.y.toFloat()),
                directions = directions,
                connected_nodes = connectedNodes,
                connected_links = connectedLinks
            )
        }

        return result
    }

    internal fun buildLinkDictFromGraphs(key: String): Map<Int, LinkData>? {
        val links = graphLinksDataMap[key] ?: return null
        val linkGroups = graphLinkGroupsDataMap[key]

        val linkIdToGroupId = mutableMapOf<Int, Int>()
        if (linkGroups != null) {
            for (group in linkGroups) {
                for (item in group.links) {
                    linkIdToGroupId[item.number] = group.number
                }
            }
        }

        val result = mutableMapOf<Int, LinkData>()
        for (link in links) {
            val groupId = linkIdToGroupId[link.number] ?: -1
            result[link.number] = LinkData(
                number = link.number,
                start_node = link.node_a.number,
                end_node = link.node_b.number,
                distance = link.distance.toFloat(),
                included_heading = link.available_headings.map { it.toFloat() },
                group_id = groupId
            )
        }

        return result
    }

    private fun loadPathPixelUrl(
        sectorId: Int,
        buildingsData: List<BuildingOutput>,
        completion: (Map<String, String>) -> Unit
    ) {
        val pathPixelUrl = mutableMapOf<String, String>()
        val latch = CountDownLatch(buildingsData.sumOf { it.levels.count { lvl -> !lvl.name.contains("_D") } })

        for (building in buildingsData) {
            for (level in building.levels) {
                if (level.name.contains("_D")) continue

                val input = LevelIdOsInput(level_id = level.id)
                val key = "${sectorId}_${building.name}_${level.name}"

        TJLabsResourceNetworkManager.getLevelPaths(
            TJLabsResourceNetworkConstants.getUserBaseURL(),
            input,
            TJLabsResourceNetworkConstants.getUserGraphsPathsServerVersion()
        ) { status, msg, result ->
            try {
                if (status != 200) {
                    pathPixelDelegate?.onPathPixelError(key)
                }

                        if (result != null) {
                            savePathPixelUrlToCache(key, result.csv)
                            pathPixelUrl[key] = result.csv
                        } else {
                            pathPixelDelegate?.onPathPixelError(key)
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }
        }

        Executors.newSingleThreadExecutor().execute {
            latch.await()
            completion(pathPixelUrl)
        }
    }

    private fun updatePathPixel(
        key: String,
        sectorId: Int,
        pathPixelUrlFromServer: String,
        completion: (Boolean) -> Unit
    ) {
        val parsedUrl = try {
            URL(pathPixelUrlFromServer)
        } catch (e: Exception) {
            pathPixelDelegate?.onPathPixelError(key)
            completion(false)
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val (file, dir, _) = downloadCSVFile(
                    application,
                    parsedUrl,
                    sectorId,
                    "$key.csv"
                )

                if (file != null) {
                    val fileText = file.readText()
                    val ppData = parsePathPixelData(fileText)

                    pathPixelDataMap[key] = ppData
                    TJLabsPathPixelManager.ppDataMap[key] = ppData
                    savePathPixelCacheDirToCache(key, dir)
                    savePathPixelUrlToCache(key, pathPixelUrlFromServer)

                    withContext(Dispatchers.Main) {
                        pathPixelDelegate?.onPathPixelData(key, ppData)
                    }
                    completion(true)
                } else {
                    withContext(Dispatchers.Main) {
                        pathPixelDelegate?.onPathPixelError(key)
                    }
                    completion(false)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    pathPixelDelegate?.onPathPixelError(key)
                }
                completion(false)
            }
        }
    }

    private fun loadPathPixelServerUrlFromCache(key: String): String? {
        val keyPpURL = "TJLabsPathPixelURL_$key"
        return sharedPrefs.getString(keyPpURL, null)
    }

    private fun loadPathPixelFileFromCache(key: String): PathPixelData? {
        val loadedPpLocalDir = loadPathPixelCacheDirFromCache(key)
        if (!loadedPpLocalDir.isNullOrEmpty()) {
            val file = File(loadedPpLocalDir)
            if (file.exists()) {
                val text = file.readText()
                return parsePathPixelData(text)
            }
        }
        return null
    }

    private fun savePathPixelUrlToCache(key: String, pathPixelUrlFromServer: String) {
        val keyPpURL = "TJLabsPathPixelURL_$key"
        sharedPrefs.edit().putString(keyPpURL, pathPixelUrlFromServer).apply()
        TJLogger.d("Info: save $key Path-Pixel URL $pathPixelUrlFromServer")
    }

    private fun loadPathPixelCacheDirFromCache(key: String): String? {
        val keyPpURL = "TJLabsPathPixelDir_$key"
        return sharedPrefs.getString(keyPpURL, null)
    }

    private fun savePathPixelCacheDirToCache(key: String, pathPixelUrlFromServer: String) {
        val keyPpURL = "TJLabsPathPixelDir_$key"
        sharedPrefs.edit().putString(keyPpURL, pathPixelUrlFromServer).apply()
        TJLogger.d("Info: save $key Path-Pixel URL $pathPixelUrlFromServer")
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

            // New format: x, y, heading(s), scale
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

            if (parts.size < 4) {
                TJLogger.d("Invalid line format: $line")
                continue
            }

            val xVal = parts[0].toFloatOrNull()
            val yVal = parts[1].toFloatOrNull()
            val headingRaw = if (headingValues.isNotEmpty()) headingValues else parts[2]
            val scaleVal = parts[3].toFloatOrNull()

            if (xVal == null || yVal == null || scaleVal == null) {
                TJLogger.d("Parse error: $line")
                continue
            }

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
                    "${key}.csv"
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
            val velocityVal = parts[3].toFloatOrNull()

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

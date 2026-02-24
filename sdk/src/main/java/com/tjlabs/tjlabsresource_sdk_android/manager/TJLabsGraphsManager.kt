package com.tjlabs.tjlabsresource_sdk_android.manager

import android.os.Handler
import android.os.Looper
import com.tjlabs.tjlabsresource_sdk_android.BuildingOutput
import com.tjlabs.tjlabsresource_sdk_android.GraphLevelLink
import com.tjlabs.tjlabsresource_sdk_android.GraphLevelLinkGroup
import com.tjlabs.tjlabsresource_sdk_android.GraphLevelNode
import com.tjlabs.tjlabsresource_sdk_android.GraphLevelPath
import com.tjlabs.tjlabsresource_sdk_android.GraphResourceType
import com.tjlabs.tjlabsresource_sdk_android.LevelIdOsInput
import com.tjlabs.tjlabsresource_sdk_android.LinkData
import com.tjlabs.tjlabsresource_sdk_android.NodeData
import com.tjlabs.tjlabsresource_sdk_android.NodeLinkType
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceNetworkConstants
import com.tjlabs.tjlabsresource_sdk_android.manager.TJLabsNodeLinkManager.Companion
import com.tjlabs.tjlabsresource_sdk_android.util.TJLogger
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
    companion object {
        val graphNodesDataMap: MutableMap<String, List<GraphLevelNode>> = mutableMapOf()
        val graphLinksDataMap: MutableMap<String, List<GraphLevelLink>> = mutableMapOf()
        val graphLinkGroupsDataMap: MutableMap<String, List<GraphLevelLinkGroup>> = mutableMapOf()
        val graphPathsDataMap: MutableMap<String, List<GraphLevelPath>> = mutableMapOf()
    }

    var delegate: GraphsDelegate? = null

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
            if (result != null) {
                graphPathsDataMap[key] = result.paths
                delegate?.onGraphPathsData(key, result.paths)
                completion(true)
            } else {
                delegate?.onGraphError(key, GraphResourceType.PATHS)
                completion(false)
            }
        }
    }
}

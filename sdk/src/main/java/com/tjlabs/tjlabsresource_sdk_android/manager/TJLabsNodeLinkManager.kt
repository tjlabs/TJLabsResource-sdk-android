package com.tjlabs.tjlabsresource_sdk_android.manager

import android.os.Handler
import android.os.Looper
import com.tjlabs.tjlabsresource_sdk_android.BuildingOutput
import com.tjlabs.tjlabsresource_sdk_android.LinkData
import com.tjlabs.tjlabsresource_sdk_android.NodeData
import com.tjlabs.tjlabsresource_sdk_android.NodeDirection
import com.tjlabs.tjlabsresource_sdk_android.NodeLinkType
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors


internal interface NodeLinkDelegate {
    fun onNodeLinkData(nodeLinkKey : String, type : NodeLinkType, data: Any)
    fun onNodeLinkError(nodeLinkKey : String, type : NodeLinkType)
}


internal class TJLabsNodeLinkManager {

    companion object {
        val nodeDataMap: MutableMap<String, Map<Int, NodeData>> = mutableMapOf()
        val linkDataMap: MutableMap<String, Map<Int, LinkData>> = mutableMapOf()
    }

    var delegate: NodeLinkDelegate? = null
    private val graphsManager = TJLabsGraphsManager()

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

        // 비동기 작업 리스트
        val tasks = mutableListOf<() -> Unit>()

        for (building in buildingsData) {
            for (level in building.levels) {
                if (level.name.contains("_D")) continue

                val blKey = "${sectorId}_${building.name}_${level.name}"

                //cache 내 검사해서 없으면
                //서버엣 내려받기

                //있으면 그대로 사용
                // Cache hit
                val cachedNode = nodeDataMap[blKey]
                val cachedLink = linkDataMap[blKey]
                if (cachedNode != null && cachedLink != null) {
                    mainHandler.post {
                        delegate?.onNodeLinkData(blKey, NodeLinkType.NODE, cachedNode)
                        delegate?.onNodeLinkData(blKey, NodeLinkType.LINK, cachedLink)
                    }
                    continue
                }

                hasAsyncWork = true

                tasks += task@{
                    ensureGraphData(blKey, level.id) { success ->
                        if (!success) {
                            mainHandler.post {
                                delegate?.onNodeLinkError(blKey, NodeLinkType.NODE)
                                delegate?.onNodeLinkError(blKey, NodeLinkType.LINK)
                            }
                            updateSuccess(false)
                            return@ensureGraphData
                        }

                        val nodeDict = buildNodeDictFromGraphs(blKey)
                        val linkDict = buildLinkDictFromGraphs(blKey)

                        mainHandler.post {
                            if (nodeDict != null && linkDict != null) {
                                nodeDataMap[blKey] = nodeDict
                                linkDataMap[blKey] = linkDict

                                delegate?.onNodeLinkData(blKey, NodeLinkType.NODE, nodeDict)
                                delegate?.onNodeLinkData(blKey, NodeLinkType.LINK, linkDict)
                                updateSuccess(true)
                            } else {
                                delegate?.onNodeLinkError(blKey, NodeLinkType.NODE)
                                delegate?.onNodeLinkError(blKey, NodeLinkType.LINK)
                                updateSuccess(false)
                            }
                        }
                    }
                }
            }
        }

        // 전부 캐시 히트(or skip)면 바로 성공
        if (!hasAsyncWork) {
            completion(true)
            return
        }

        val latch = CountDownLatch(tasks.size)
        val executor = Executors.newFixedThreadPool(4)

        tasks.forEach { task ->
            executor.execute {
                try {
                    task()
                } finally {
                    latch.countDown()
                }
            }
        }

        Executors.newSingleThreadExecutor().execute {
            latch.await()
            mainHandler.post {
                completion(isAllSuccess)
            }
        }
    }

    private fun ensureGraphData(key: String, levelId: Int, completion: (Boolean) -> Unit) {
        val cachedNodes = TJLabsGraphsManager.graphNodesDataMap[key]
        val cachedLinks = TJLabsGraphsManager.graphLinksDataMap[key]
        val cachedLinkGroups = TJLabsGraphsManager.graphLinkGroupsDataMap[key]

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

        graphsManager.updateLevelNodes(key, levelId) { success ->
            updateSuccess(success)
            latch.countDown()
        }
        graphsManager.updateLevelLinks(key, levelId) { success ->
            updateSuccess(success)
            latch.countDown()
        }
        graphsManager.updateLevelLinkGroups(key, levelId) { success ->
            updateSuccess(success)
            latch.countDown()
        }

        Executors.newSingleThreadExecutor().execute {
            latch.await()
            completion(isAllSuccess)
        }
    }

    private fun buildNodeDictFromGraphs(key: String): Map<Int, NodeData>? {
        val nodes = TJLabsGraphsManager.graphNodesDataMap[key] ?: return null
        val result = mutableMapOf<Int, NodeData>()

        for (node in nodes) {
            val inHeadings = node.available_in_headings.toSet()
            val outHeadings = node.available_out_headings.toSet()
            val endOnlyHeadings = inHeadings.subtract(outHeadings)

            val mergedHeadings = LinkedHashSet<Int>()
            mergedHeadings.addAll(node.available_in_headings)
            mergedHeadings.addAll(node.available_out_headings)

            //is_end 조건은 In에만 있는 헤딩값임.
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

    private fun buildLinkDictFromGraphs(key: String): Map<Int, LinkData>? {
        val links = TJLabsGraphsManager.graphLinksDataMap[key] ?: return null
        val linkGroups = TJLabsGraphsManager.graphLinkGroupsDataMap[key]

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
}

package com.tjlabs.tjlabsresource_sdk_android.manager

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.tjlabs.tjlabsresource_sdk_android.BuildingOutput
import com.tjlabs.tjlabsresource_sdk_android.LandmarkData
import com.tjlabs.tjlabsresource_sdk_android.LinkData
import com.tjlabs.tjlabsresource_sdk_android.NodeData
import com.tjlabs.tjlabsresource_sdk_android.NodeDirection
import com.tjlabs.tjlabsresource_sdk_android.NodeLinkType
import com.tjlabs.tjlabsresource_sdk_android.SectorOutput
import com.tjlabs.tjlabsresource_sdk_android.util.TJLogger
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

    fun loadNodeLinks(
        context: Context,
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
                    val nodeFileName = "${blKey}_node.json"
                    val linkFileName = "${blKey}_link.json"

                    // Swift의 subdirectory="Data" 우선, 없으면 root fallback
                    fun readJsonWithFallback(fileName: String): String? {
                        return try {
                            context.assets.open("Data/$fileName").bufferedReader().use { it.readText() }
                        } catch (_: Exception) {
                            try {
                                context.assets.open(fileName).bufferedReader().use { it.readText() }
                            } catch (_: Exception) {
                                null
                            }
                        }
                    }

                    val nodeJsonString = readJsonWithFallback(nodeFileName)
                    if (nodeJsonString == null) {
                        mainHandler.post {
                            delegate?.onNodeLinkError(blKey, NodeLinkType.NODE)
                            updateSuccess(false)
                        }
                        return@task
                    }

                    val linkJsonString = readJsonWithFallback(linkFileName)
                    if (linkJsonString == null) {
                        mainHandler.post {
                            delegate?.onNodeLinkError(blKey, NodeLinkType.LINK)
                            updateSuccess(false)
                        }
                        return@task
                    }

                    processNodeLinkStrings(
                        nodeJsonString = nodeJsonString,
                        linkJsonString = linkJsonString,
                        key = blKey
                    ) { success, nodeDict, linkDict ->
                        mainHandler.post {
                            if (success && nodeDict != null && linkDict != null) {
                                // Cache
                                nodeDataMap[blKey] = nodeDict
                                linkDataMap[blKey] = linkDict

                                // Delegate notify
                                delegate?.onNodeLinkData(blKey, NodeLinkType.NODE, nodeDict)
                                delegate?.onNodeLinkData(blKey, NodeLinkType.LINK, linkDict)
                                updateSuccess(true)
                            } else {
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

    // ------------------------------------
    // Helper Methods
    // ------------------------------------
    private fun processNodeLinkStrings(
        nodeJsonString: String,
        linkJsonString: String,
        key: String,
        completion: (Boolean, Map<Int, NodeData>?, Map<Int, LinkData>?) -> Unit
    ) {
        try {
            val nodeDict = decodeNodeDict(nodeJsonString)
            if (nodeDict == null) {
                completion(false, null, null)
                return
            }

            val linkDict = decodeLinkDict(linkJsonString)
            if (linkDict == null) {
                completion(false, null, null)
                return
            }

            completion(true, nodeDict, linkDict)
        } catch (e: Exception) {
            TJLogger.d("[processNodeLinkFiles] File read/parse error for key=$key : ${e.localizedMessage}")
            completion(false, null, null)
        }
    }

    // ------------------------------------
    // Decoding
    //  - Swift Codable의 snake_case를 대비해 LOWER_CASE_WITH_UNDERSCORES 사용
    // ------------------------------------
    private val gson: Gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    // DTOs
    private data class NodeDataDTO(
        val id: Int,
        val coords: List<Float>,
        val directions: List<NodeDirection>,
        val connected_nodes: List<Int>,
        val connected_links: List<Int>
    ) {
        fun toDomain(): NodeData = NodeData(
            id = id,
            coords = coords,
            directions = directions,
            connected_nodes = connected_nodes,
            connected_links = connected_links
        )
    }

    private data class LinkDataDTO(
        val id: Int,
        val start_node: Int,
        val end_node: Int,
        val distance: Float,
        val included_heading: List<Float>,
        val group_id: Int
    ) {
        fun toDomain(): LinkData = LinkData(
            id = id,
            start_node = start_node,
            end_node = end_node,
            distance = distance,
            included_heading = included_heading,
            group_id = group_id
        )
    }

    private fun decodeNodeDict(jsonString: String): Map<Int, NodeData>? {
        // 1) 배열 형태
        try {
            val listType = object : TypeToken<List<NodeDataDTO>>() {}.type
            val arr: List<NodeDataDTO> = gson.fromJson(jsonString, listType)
            return arr.associate { it.id to it.toDomain() }
        } catch (_: Exception) {
        }

        // 2) Wrapper 형태
        return try {
            data class Wrapper(val nodes: List<NodeDataDTO>)
            val wrapped = gson.fromJson(jsonString, Wrapper::class.java)
            wrapped.nodes.associate { it.id to it.toDomain() }
        } catch (e: Exception) {
            TJLogger.d("[decodeNodeDict] Node decoding failed: ${e.localizedMessage}")
            null
        }
    }

    private fun decodeLinkDict(jsonString: String): Map<Int, LinkData>? {
        // 1) 배열 형태
        try {
            val listType = object : TypeToken<List<LinkDataDTO>>() {}.type
            val arr: List<LinkDataDTO> = gson.fromJson(jsonString, listType)
            return arr.associate { it.id to it.toDomain() }
        } catch (_: Exception) {
        }

        // 2) Wrapper 형태
        return try {
            data class Wrapper(val links: List<LinkDataDTO>)
            val wrapped = gson.fromJson(jsonString, Wrapper::class.java)
            wrapped.links.associate { it.id to it.toDomain() }
        } catch (e: Exception) {
            TJLogger.d("[decodeLinkDict] Link decoding failed: ${e.localizedMessage}")
            null
        }
    }
}
package com.tjlabs.tjlabsresource_sdk_android.manager

import com.tjlabs.tjlabsresource_sdk_android.BuildingOutput
import com.tjlabs.tjlabsresource_sdk_android.LinkData
import com.tjlabs.tjlabsresource_sdk_android.NodeData
import com.tjlabs.tjlabsresource_sdk_android.NodeLinkType


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
        graphsManager.nodeLinkDelegate = delegate
        graphsManager.loadNodeLinks(sectorId, buildingsData) { success ->
            nodeDataMap.clear()
            linkDataMap.clear()
            nodeDataMap.putAll(TJLabsGraphsManager.nodeDataMap)
            linkDataMap.putAll(TJLabsGraphsManager.linkDataMap)
            completion(success)
        }
    }
}

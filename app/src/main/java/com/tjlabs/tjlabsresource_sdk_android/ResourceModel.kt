package com.tjlabs.tjlabsresource_sdk_android

import android.graphics.Bitmap

data class PathPixelData(
    val roadType: List<Int> = listOf(),
    val nodeNumber: List<Int> = listOf(),
    val road: List<List<Float>> = listOf(),
    val roadScale: List<Float> = listOf(),
    val roadHeading: List<String> = listOf()
)

data class PathPixelDataIsLoaded(
    var isLoaded : Boolean = false,
    val url : String = ""
)

internal data class SectorIdInput(
    var sector_id: Int = 0
)

internal data class SectorInput(
    var sector_id: Int = 0,
    var operating_system: String = "Android"
)

internal data class PathPixel(
    val building_name: String = "",
    val level_name: String = "",
    val url : String = "",
    val OutputPathPixel : List<PathPixel> = listOf()
)

internal data class OutputPathPixel(
    val path_pixel_list : List<PathPixel> = listOf()
)

internal data class LevelOutput(
    val building_name: String = "",
    val level_name: String = ""
)

internal data class LevelOutputList(
    val level_list : List<LevelOutput> = listOf()
)


internal data class ScaleOutputList(
    val scale_list : List<ScaleOutput> = listOf()
)

internal data class ScaleOutput(
    val building_name : String = "",
    val level_name : String = "",
    val image_scale : List<Float> = listOf()
)

data class ResourceRegion(
    val KOREA : String,
    val US_EAST : String,
    val CANADA : String
) {
    companion object {
        val KOREA : String = "Korea"
        val US_EAST : String = "US_EAST"
        val CANADA : String = "Canada"
    }
}

enum class ResourceError {
    PathPixel,
    BuildingLevel,
    Image,
    Scale,
    Entrance,
    Unit,
    Param,
    Geo
}


interface TJLabsResourceManagerDelegate {
    fun onBuildingLevelData(isOn: Boolean, buildingLevelData: Map<String, List<String>>)
    fun onPathPixelData(isOn: Boolean, pathPixelKey: String, data : PathPixelData?)
    fun onBuildingLevelImageData(isOn: Boolean, imageKey: String, data : Bitmap?)
    fun onScaleOffsetData(isOn: Boolean, scaleKey: String, data : List<Float>)
    fun onEntranceRouteData(isOn: Boolean, entranceKey: String, data : EntranceRouteData?)
    fun onEntranceData(isOn: Boolean, entranceKey: String, data : EntranceData?)
    fun onUnitData(isOn: Boolean, unitKey: String, data : List<UnitData>?)
    fun onParamData(isOn: Boolean, data : ParameterData?)
    fun onGeofenceData(isOn : Boolean, key : String, data : GeofenceData?)
    fun onError(error: ResourceError)
}

data class EntranceData(
    var number: Int = 0,
    var networkStatus: Boolean = false,
    var velocityScale: Float = 0f,
    var innerWardId: String = "",
    var innerWardRssi: Float = 0f,
    var innerWardCoord: List<Int> = emptyList()
)

data class EntranceRouteData(
    var routeLevel: List<String> = emptyList(),
    var route: List<List<Float>> = listOf(emptyList())
)

data class EntranceRouteDataIsLoaded(
    var isLoaded: Boolean = false,
    var url: String = ""
)

data class EntranceRf(
    val id : String = "",
    val rss : Float = 0f,
    val pos : List<Int> = listOf(),
    val direction : Int = 0
)

data class Entrance(
    val spot_number: Int = 0,
    val outermost_ward_id: String = "",
    val scale : Float = 0f,
    val url : String = "",
    val network_status : Boolean = false,
    val innermost_ward : EntranceRf = EntranceRf()
)

data class EntranceList(
    val building_name: String = "",
    val level_name: String = "",
    val entrances : List<Entrance> = listOf()
)


data class EntranceOutputList(
    val entrance_list: List<EntranceList> = listOf()
)

data class UnitData(
    val category: Int = 0,
    val number: Int = 0,
    val name: String = "",
    val accessibility: String = "",
    val restriction: Boolean = false,
    val visibility: Boolean = false,
    val x: Float = 0f,
    val y: Float = 0f
)

data class UnitOutput (
    val building_name: String = "",
    val level_name: String = "",
    val units : List<UnitData> = listOf()
)

data class UnitOutputList (
    val unit_list : List<UnitOutput> = listOf()
)

data class ParameterData(
    val trajectory_length: Int = 0,
    val trajectory_diagonal: Int = 0,
    val debug :Boolean = false,
    val standard_rss : List<Int> = listOf()
)

data class Node(
    val number: Int = 0,
    val center_pos: List<Int> = listOf(),
    val direction_type: String = ""
)

data class DrModeArea(
    val number: Int = 0,
    val range : List<Int> = listOf(),
    val direction : Int = 0,
    val nodes : List<Node> = listOf()
)

data class Geofence(
    val building_name: String = "",
    val level_name: String = "",
    val entrance_area: List<List<Int>> = listOf(listOf(0, 0, 0, 0)),
    val entrance_matching_area: List<List<Int>> = listOf(listOf(0, 0, 0, 0)),
    val level_change_area: List<List<Int>> = listOf(listOf(0, 0, 0, 0)),
    val dr_mode_areas : List<DrModeArea> = listOf(DrModeArea())
)

data class OutputGeofence(
    val geofence_list : List<Geofence> = listOf()
)

data class GeofenceData(
    val entrance_area: List<List<Int>> = listOf(listOf(0, 0, 0, 0)),
    val entrance_matching_area: List<List<Int>> = listOf(listOf(0, 0, 0, 0)),
    val level_change_area: List<List<Int>> = listOf(listOf(0, 0, 0, 0)),
    val dr_mode_areas : List<DrModeArea> = listOf(DrModeArea())
)


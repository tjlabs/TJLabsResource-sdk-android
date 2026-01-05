package com.tjlabs.tjlabsresource_sdk_android

import android.graphics.Bitmap

enum class ResourceRegion(val value: String) {
    KOREA("Korea"),
    US_EAST("US_EAST"),
    CANADA("Canada")
}

data class PathPixelData(
    val roadType: List<Int> = listOf(),
    val nodeNumber: List<Int> = listOf(),
    val road: List<List<Float>> = listOf(),
    val roadMinMax: List<Float> = listOf(),
    val roadScale: List<Float> = listOf(),
    val roadHeading: List<String> = listOf()
)

data class EntranceData(
    var number: Int = 0,
    var networkStatus: Boolean = false,
    var velocityScale: Float = 0f,
    var innerWardId: String = "",
    var innerWardRssi: Float = 0f,
    var innerWardCoord: List<Float> = emptyList(),
    var outerWardId: String = ""
)


data class EntranceRouteData(
    var routeLevel: List<String> = emptyList(),
    var route: List<List<Float>> = listOf(emptyList())
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

data class ParameterData(
    val trajectory_length: Int = 0,
    val trajectory_diagonal: Int = 0,
    val debug: Boolean = false,
    val standard_rss: List<Int> = listOf()
)

data class GeofenceData(
    val entrance_area: List<List<Int>> = listOf(listOf(0, 0, 0, 0)),
    val entrance_matching_area: List<List<Int>> = listOf(listOf(0, 0, 0, 0)),
    val level_change_area: List<List<Int>> = listOf(listOf(0, 0, 0, 0)),
    val dr_mode_areas: List<DRModeArea> = listOf()
)

internal data class SectorIdInput(
    var sector_id: Int = 0
)

internal data class SectorIdOsInput(
    val sector_id: Int = 0,
    val operating_system: String = "Android"
)

internal data class LevelIdOsInput(
    var level_id: Int = 0,
    var operating_system: String = "Android"
)

data class SectorOutput(
    val id: Int,
    val name: String,
    val request_service: String,
    val debug: Boolean,
    val buildings: List<BuildingOutput>
)

data class BuildingOutput(
    val id: Int,
    val name: String,
    val levels: List<LevelOutput>
)

data class LevelOutput(
    val id: Int,
    val name: String,
    val image: String
)

// MARK: - PathPixel
data class PathPixelOutput(
    val csv: String
)

// MARK: - Entrance
data class InnermostWard(
    val name: String,
    val rssi: Float,
    val x: Int,
    val y: Int,
    val direction: Float
)

data class Entrance(
    val number: Int,
    val outermost_ward_name: String,
    val scale: Float,
    val csv: String,
    val network_status: Boolean,
    val innermost_ward: InnermostWard
)

data class EntranceOutput(
    val entrances: List<Entrance>
)

// MARK: - Scale Offset
data class ScaleOffsetOutput(
    val image_scale: List<Float>
)

// MARK: - Unit
data class UnitOutput(
    val units: List<UnitData>
)

// MARK: - Parameter
data class SectorParameterOutput(
    val standard_max_rssi: Int,
    val standard_min_rssi: Int
)

data class LevelParameterOutput(
    val trajectory_length: Float,
    val trajectory_diagonal: Float
)

// MARK: - Geofence
data class DRModeArea(
    var number: Int,
    var range: List<Float>,
    var direction: Float,
    var nodes: List<DRModeAreaNode>
)

data class DRModeAreaNode(
    var number: Int,
    var center_x: Float,
    var center_y: Float,
    var direction_type: String
)

data class LevelWardsOutput(
    var id : Int,
    val name : String,
    val wards : List<Ward>
)

data class AffineTransParamOutput(
    val xx_scale: Float = 0f,
    val xy_shear: Float = 0f,
    val x_translation: Float = 0f,
    val yx_shear: Float = 0f,
    val yy_scale: Float = 0f,
    val y_translation: Float = 0f,
    val heading_offset : Float = 0f
)

data class Ward (
    val id : Int,
    val name : String
)

data class SpotsOutput (
    val buildilng_level_tags: List<BuildingLevelTag>
)


data class  BuildingLevelTag(
    val id: Int,
    val name: String,
    val building_name: String,
    val level_name: String,
    val linked_level_name: String,
    val rssi: Int,
    val x: Int,
    val y: Int,
    val linked_links: List<Int>,
    val distance: Int
)


data class LandmarkData(
    val ward_id: String,
    val peaks: List<PeakData>,
)

data class PeakData (
    val x: Int,
    val y: Int,
    val rssi: Float
)

enum class SpotType {
    BUILDING_LEVEL_TAG, NONE
}

data class NodeData (
    val id: Int,
    val coords: List<Float>,
    val directions: List<NodeDirection>,
    val connected_nodes: List<Int>,
    val connected_links: List<Int>
)

data class NodeDirection (
    val heading: Float,
    val is_end: Boolean
)

data class LinkData (
    val id: Int,
    val start_node: Int,
    val end_node: Int,
    val distance: Float,
    val included_heading: List<Float>,
    val group_id: Int
)

enum class NodeLinkType {
    NODE, LINK, FILE
}

// MARK: - Resource Error
enum class ResourceError {
    Sector,
    PathPixel,
    BuildingLevel,
    LevelWards,
    Image,
    Scale,
    Entrance,
    Unit,
    Param,
    Geofence,
    Affine,
    Node,
    Link,
    Landmark,
    Spots
}

// MARK: - Delegate (protocol → interface)
// delegate 체인이 TJLabsBuildingsManager → TJLabsResourceManager → TJLabsResourceManagerDelegate 형태로 단일 경로
// manager 를 따로 입력받을 필요 없음. manager 입력은 어디서 호출했는지 확인하기 위함임

interface TJLabsResourceManagerDelegate {
    fun onSectorData(data: SectorOutput)
    fun onSectorError(error: ResourceError)
    fun onBuildingsData(data: List<BuildingOutput>)
    fun onLevelWardsData(levelKey: String, data : List<String>)
    fun onScaleOffsetData(scaleKey: String, data: List<Float>)
    fun onPathPixelData(pathPixelKey: String, data: PathPixelData)
    fun onGeofenceData(geofenceKey: String, data: GeofenceData)
    fun onEntranceData(entranceKey: String, data: EntranceData)
    fun onEntranceRouteData(entranceKey: String, data: EntranceRouteData)
    fun onSectorParamData(data: SectorParameterOutput)
    fun onLevelParamData(paramKey: String, data: LevelParameterOutput)
    fun onBuildingLevelImageData(imageKey: String, data: Bitmap?)
    fun onUnitData(unitKey: String, data: List<UnitData>?)
    fun onAffineData(sectorId : Int, data : AffineTransParamOutput)
    fun onLandmarkData(key : String, data : Map<String, LandmarkData>)
    fun onSpotsData(key: Int, type: SpotType, data: Any)
    fun onNodeLinkData(key: String, type: NodeLinkType, data: Any)
    fun onError(error: ResourceError, key: String)
}
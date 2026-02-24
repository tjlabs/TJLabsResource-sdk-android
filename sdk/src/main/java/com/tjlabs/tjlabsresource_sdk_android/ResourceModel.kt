package com.tjlabs.tjlabsresource_sdk_android

import android.graphics.Bitmap
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

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
    var velocityScale: Float = 0f,
    var innermost_ward: InnermostWard,
    var outermost_ward: OutermostWard
)


data class EntranceRouteData(
    var routeLevel: List<String> = emptyList(),
    var route: List<List<Float>> = listOf(emptyList())
)

//data class UnitData(
//    val category: Int = 0,
//    val number: Int = 0,
//    val name: String = "",
//    val accessibility: String = "",
//    val restriction: Boolean = false,
//    val visibility: Boolean = false,
//    val x: Float = 0f,
//    val y: Float = 0f
//)

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
data class OutermostWard(
    val id : Int,
    val name: String,
)


data class InnermostWard(
    val level : LevelOutput,
    val id : Int,
    val name: String,
    val x: Int,
    val y: Int,
    val is_turn : Boolean,
    val headings: List<Float>
)

data class Entrance(
    val id : Int,
    val number: Int,
    val scale: Float,
    val csv: String,
    val innermost_ward: InnermostWard,
    val outermost_ward : OutermostWard
)

data class EntranceOutput(
    val entrances: List<Entrance>
)

// MARK: - Scale Offset
data class ScaleOffsetOutput(
    val image_scale: List<Float>
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
    val rssi: Float,
    val matched_links : List<Int>
)

enum class SpotType {
    BUILDING_LEVEL_TAG, NONE
}

data class NodeData (
    val number: Int,
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
    val number: Int,
    val start_node: Int,
    val end_node: Int,
    val distance: Float,
    val included_heading: List<Float>,
    val group_id: Int
)

enum class NodeLinkType {
    NODE, LINK, FILE
}

// MARK: - Unit
internal data class LevelUnitsInput(
    var level_id: Int = 0,
    var category: Category? = null
)



data class LevelUnitsOutput(
    val id : Int,
    val units: List<UnitData>
)

data class UnitData(
    val id: Int,
    val category: Category,
    val name: String,
    val is_restricted: Boolean,
    val x: Float,   // Swift Double → Kotlin Float
    val y: Float,   // Swift Double → Kotlin Float
    val parking_space_code: String
)

@Serializable(with = CategorySerializer::class)
enum class Category {
    PARKING_SPACE,
    UNKNOWN
}

object CategorySerializer : KSerializer<Category> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Category", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Category {
        val raw = try {
            decoder.decodeString()
        } catch (e: Exception) {
            ""
        }
        return Category.values().find { it.name == raw } ?: Category.UNKNOWN
    }

    override fun serialize(encoder: Encoder, value: Category) {
        encoder.encodeString(value.name)
    }
}

// MARK: - Graph
data class ItemIdNumber (
    val id : Int,
    val number: Int
)

data class GraphLevelNodesOutput (
    val nodes : List<GraphLevelNode>
)

data class GraphLevelNode (
    val id : Int,
    val number : Int,
    val x : Int,
    val y : Int,
    val available_in_headings : List<Int>,
    val available_out_headings : List<Int>,
    val connected_links : List<ItemIdNumber>,
    val connected_nodes : List<ItemIdNumber>
)

data class GraphLevelLinksOutput (
    val links : List<GraphLevelLink>
)

data class GraphLevelLink (
    val id : Int,
    val number : Int,
    val node_a : ItemIdNumber,
    val node_b: ItemIdNumber,
    val available_headings : List<Int>,
    val distance : Int
)


data class GraphLevelLinksGroupsOutput (
    val link_groups : List<GraphLevelLinkGroup>
)

data class GraphLevelLinkGroup (
    val id : Int,
    val number : Int,
    val links : List<ItemIdNumber>,
)

data class GraphLevelPathsOutput (
    val paths : List<GraphLevelPath>
)

data class GraphLevelPath (
    val x : Int,
    val y : Int,
    val available_headings : List<Int>,
    val velocity_scale: Float
)

enum class GraphResourceType {
    NODES,
    LINKS,
    LINK_GROUPS,
    PATHS
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
    LevelUnits,
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
    fun onLevelUnitsData(unitKey: String, data: List<UnitData>?)
    fun onAffineData(sectorId : Int, data : AffineTransParamOutput)
    fun onLandmarkData(key : String, data : Map<String, LandmarkData>)
    fun onSpotsData(key: Int, type: SpotType, data: Any)
    fun onNodeLinkData(key: String, type: NodeLinkType, data: Any)
    fun onGraphNodesData(key: String, data: List<GraphLevelNode>)
    fun onGraphLinksData(key: String, data: List<GraphLevelLink>)
    fun onGraphLinkGroupsData(key: String, data: List<GraphLevelLinkGroup>)
    fun onGraphPathsData(key: String, data: List<GraphLevelPath>)
    fun onGraphError(key: String, type: GraphResourceType)
    fun onError(error: ResourceError, key: String)
}

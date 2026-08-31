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
    CANADA("Canada"),
    SAUDI("SAUDI")
}

enum class ServerProvider(val value: String) {
    AWS("aws"),
    GCP("gcp"),
}

enum class ResourceBundleType {
    JUPITER,
    VENUS,
    WARP
}

/**
 * Resource SDK 의 Jupiter (OLYMPUS) 도메인 환경.
 *
 * - [PROD] : 실 운영 서버 (`.jupiter.tjlabscorp.com`). 외부 앱 · 실 배포는 반드시 이 값.
 *   `TJLabsResourceNetworkConstants.setServerURL(...)` 를 env 인자 없이 호출하면 자동 PROD.
 *
 * - [DEV_TESTING_ONLY] : 개발 서버 (`.jupiter.tjlabs.dev`). **TJLabs 내부 개발 · QA 전용.**
 *   외부 프로덕션 앱에서 사용 금지 — 개발 서버는 SLA 없이 스키마 변경 · 다운타임이 발생한다.
 *
 * NOTE: 이 env 는 **OLYMPUS_SUFFIX (Jupiter 도메인) 에만** 적용된다.
 * WARP_SUFFIX 는 AWS 기반 별도 인프라라 env 스위칭 대상 아님.
 */
enum class ResourceServerEnv {
    PROD,
    DEV_TESTING_ONLY
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
    val buildings: List<BuildingOutput>,
    val default_position: DefaultPositionOutput? = null,
    val transitions: List<TransitionOutput> = emptyList()
)

data class DefaultPositionOutput(
    val building: DefaultPositionBuildingOutput
)

data class DefaultPositionBuildingOutput(
    val id: Int,
    val name: String,
    val level: DefaultPositionLevelOutput
)

data class DefaultPositionLevelOutput(
    val id: Int,
    val name: String,
    val x: Int,
    val y: Int,
    val heading: Float
)

// MARK: - Sector Bundle
data class SectorBundleMetaOutput(
    val url: String,
    val version_id: String
)

data class SectorBundleOutput(
    val id: Int,
    val name: String,
    val debug: Boolean,
    val default_position: DefaultPositionOutput?,
    val wgs84_transform: AffineTransParamOutput?,
    val buildings: List<SectorBundleBuildingOutput>
)

data class SectorBundleBuildingOutput(
    val id: Int,
    val name: String,
    val levels: List<SectorBundleLevelOutput>
)

data class SectorBundleLevelOutput(
    val id: Int,
    val name: String,
    val operating_system: String?,
    val map_image: SectorBundleMapImageOutput?,
    val geofence: GeofenceData?,
    val entrances: List<SectorBundleEntranceOutput>?,
    val units: List<UnitData>?,
    val graph: SectorBundleGraphOutput?,
    val wards: List<LevelLandmark>?,
    // 2026-08-28 스키마 신규. GeoJSON 피처 id ↔ 외부 업체 주차면 id 매핑 파일의 공개 URL.
    // 파일 미업로드 시 null. SDK 는 이 URL 을 다시 GET 해서 [ParkingMatchesData] 로 파싱한다.
    val parking_matches: SectorBundleParkingMatchesOutput? = null
)

// 2026-08-28 스키마 — 각 level 안에 실려오는 parking matches 참조.
// url 은 만료 없는 공개 URL (presigned 아님). 그대로 GET 하면 [ParkingMatchesData] JSON.
data class SectorBundleParkingMatchesOutput(
    val url: String
)

data class SectorBundleMapImageOutput(
    val url: String,
    val image_width: Int?,
    val image_height: Int?,
    val scale_x: Float?,
    val scale_y: Float?,
    val offset_x: Float?,
    val offset_y: Float?
)

data class SectorBundleEntranceOutput(
    val id: Int,
    val number: Int,
    val scale: Float,
    val url: String,
    val innermost_ward: InnermostWard,
    val outermost_ward: OutermostWard
)

data class SectorBundleGraphOutput(
    val nodes: List<GraphLevelNode>?,
    val links: List<GraphLevelLink>?,
    val link_features: List<GraphLevelLinkFeature>?,
    val link_groups: List<GraphLevelLinkGroup>?,
    val path: SectorBundleGraphPathOutput?
)

data class SectorBundleGraphPathOutput(
    val url: String
)

// MARK: - Warp / Venus Bundle
data class WarpSectorOutput(
    val id: Int,
    val name: String,
    val operating_system: String,
    val buildings: List<WarpBuildingOutput>
)

data class WarpBuildingOutput(
    val id: Int,
    val name: String,
    val levels: List<WarpLevelOutput>
)

data class WarpLevelOutput(
    val id: Int,
    val name: String,
    val map_image: SectorBundleMapImageOutput?,
    val wards: List<WarpWardOutput>
)

// 실 on-prem PMS Warp 번들은 Venus 와 동일한 스키마 (x/y pixel 좌표 + level.map_image) 에
// contents 만 추가된 형태로 응답한다. 소비자 (hana-sdk 등) 는 x/y 를 level.map_image 의
// scale/offset 을 적용해 미터 실좌표로 변환해서 사용한다.
data class WarpWardOutput(
    val id: Int,
    val name: String,
    val x: Int,
    val y: Int,
    val rssi: Float,
    val contents: List<WarpWardContentOutput>
)

data class WarpWardContentOutput(
    val number: Int,
    val description: String,
    val url: String,
)

data class VenusSectorOutput(
    val id: Int,
    val name: String,
    val operating_system: String,
    val buildings: List<VenusBuildingOutput>
)

data class VenusBuildingOutput(
    val id: Int,
    val name: String,
    val levels: List<VenusLevelOutput>
)

data class VenusLevelOutput(
    val id: Int,
    val name: String,
    val map_image: SectorBundleMapImageOutput?,
    val wards: List<VenusWardOutput>
)

data class VenusWardOutput(
    val id: Int,
    val name: String,
    val x: Int,
    val y: Int,
    val rssi: Float?
)

// MARK: - Simulations
data class SimulationItemOutput(
    val name: String,
    val url: String
)

data class SimulationBundleOutput(
    val vehicle: List<SimulationItemOutput>,
    val pdr: List<SimulationItemOutput>
)

data class BuildingOutput(
    val id: Int,
    val name: String,
    val levels: List<LevelOutput>
)

data class LevelOutput(
    val id: Int,
    val name: String,
    val image: String,
    // "floor" (일반 층) or "transition" (층이동 전이층). 층 선택 UI 는 "floor" 로 필터해서 쓸 것.
    // 측위·경로탐색은 전이층 포함해야 하므로 SDK 는 필터링 없이 그대로 전달한다.
    val type: String = "floor",
    // 2026-08-28 스키마 — GeoJSON 피처 id ↔ 외부 업체 주차면 id 매핑.
    // 파일이 업로드된 층만 채워진다. 미업로드 → null. 빈 배열도 가능 (matches: []).
    // 최종 소비자(VM/Jupiter/앱) 는 [id → matchingId] 또는 [matchingId → id] lookup 을
    // 필요에 맞게 이 리스트에서 만들어 쓴다.
    val parking_matches: List<ParkingMatch>? = null
)

// MARK: - Parking Matches (2026-08-28)
// level 하나에 대응하는 GeoJSON 피처 ↔ 외부 업체 주차면 ID 매핑 파일의 파싱 결과.
data class ParkingMatchesData(
    val matches: List<ParkingMatch>
)

// GeoJSON 피처 id (UUID 문자열) ↔ 외부 업체 시스템 주차면 ID (문자열, 숫자처럼 보여도 문자열).
// matchingId 를 Int 로 파싱하지 말 것 — 앞자리 0 이나 문자 포함 값이 들어올 수 있다.
// matchingId 가 null 인 케이스는 "지도에는 있지만 현장에 존재하지 않는 주차면" 을 의미한다
// (실제 현장 데이터와 지도 데이터 불일치). 소비자(VM) 는 이 항목의 GeoJSON id 를
// 프론트의 unavailableParkingLocationIdList 조회 응답에 채워준다.
data class ParkingMatch(
    val id: String,
    val matchingId: String?
)

// MARK: - Transitions (층이동 구간)
// 서버 스키마 2026-08-06+ 에서 sector 루트에 실려오는 층이동(계단·엘리베이터·에스컬레이터·램프) 정보.
// 구버전 응답에는 없으므로 기본값 emptyList 로 안전하게 fallback 된다.
data class TransitionOutput(
    val id: Int,
    val name: String,
    // 전이층 자신 (type == "transition"). 그래프·RF맵이 이 id 에 달려 있음.
    val level: TransitionLevelRef,
    // 물리적 상하 관계일 뿐 통행 방향이 아님. 일방통행은 전이층 그래프 heading 이 표현.
    val lower_level: TransitionLevelRef,
    val upper_level: TransitionLevelRef,
    // 지점 목록. 빈 배열일 수 있음(전이 행만 만들고 지점 미부착 상태).
    val points: List<TransitionPoint> = emptyList()
)

data class TransitionLevelRef(
    val id: Int,
    val name: String,
    val building_id: Int
)

data class TransitionPoint(
    val id: Int,
    // 앵커 좌표 — 미터, 양의 정수, 각 층 자기 좌표계.
    val lower_x: Int,
    val lower_y: Int,
    val upper_x: Int,
    val upper_y: Int,
    // "stair" | "elevator" | "escalator" | "ramp"
    val transition_type: String,
    // 보행/차량 배타 플래그. 길찾기 요청의 is_vehicle 과 일치하는 지점만 경로 후보.
    val is_vehicle: Boolean
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
    val xx_scale: Double = 0.0,
    val xy_shear: Double = 0.0,
    val x_translation: Double = 0.0,
    val yx_shear: Double = 0.0,
    val yy_scale: Double = 0.0,
    val y_translation: Double = 0.0,
    val heading_offset : Double = 0.0
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

// MARK: - Landmark API
data class LevelLandmarkLink(
    val id: Int,
    val number: Int
)

data class LevelLandmarkOutput(
    val dead_reckoning: String,
    val operating_system: String,
    val wards : List<LevelLandmark>
)


data class LevelLandmark(
    val id: Int,
    val name : String,
    val rf_landmarks : List<LandmarkInfo>
)

data class LandmarkInfo(
    val id : Int,
    val x: Int,
    val y: Int,
    val rssi: Float,
    val links: List<LevelLandmarkLink>
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
    val category: CategoryData,
    val name: String,
    val is_restricted: Boolean,
    val x: Float,   // Swift Double → Kotlin Float
    val y: Float,   // Swift Double → Kotlin Float
    val parking_space_code: String
)


data class CategoryData(
    val id : Int,
    val name : String,
    val key : Category
)

@Serializable(with = CategorySerializer::class)
enum class Category {
    PARKING_SPACE,
    ENTRANCE_EXIT,
    UNKNOWN;

    companion object {
        fun fromRaw(raw: String?): Category {
            val normalized = raw
                ?.trim()
                ?.replace("-", "_")
                ?.replace(" ", "_")
                ?.uppercase()
                .orEmpty()
            return when (normalized) {
                "PARKING_SPACE", "PARKING" -> PARKING_SPACE
                "ENTRANCE_EXIT", "ENTRANCE", "EXIT" -> ENTRANCE_EXIT
                else -> UNKNOWN
            }
        }
    }
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
        return Category.fromRaw(raw)
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

data class GraphLevelLinkFeaturesOutput (
    val link_features : List<GraphLevelLinkFeature>
)

data class GraphLevelLinkFeature (
    val id : Int,
    val link_id : Int,
    val operating_system: String,
    val point_a : List<Int>,
    val point_b : List<Int>,
    val velocity_scale : Int
)

data class GraphLevelPathsOutput (
    val csv: String
)

data class GraphLevelPath (
   val x : Float,
   val y : Float,
   val available_headings: List<Int>,
   val velocity_scale : Float
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
// delegate 체인은 TJLabsResourceManager → TJLabsResourceManagerDelegate 단일 경로

interface TJLabsResourceManagerDelegate {
    fun onSectorData(data: SectorOutput)
    fun onSectorError(error: ResourceError)
    fun onBuildingsData(data: List<BuildingOutput>)
    fun onLevelWardsData(levelKey: String, data : List<String>)
    fun onScaleOffsetData(scaleKey: String, data: List<Float>)
    // levelType: 해당 level 의 type ("floor" | "transition"). 2026-08-06+ 스키마에서 전이층의
    // path pixel 도 이 콜백으로 함께 전달되므로 수신 측이 여기서 구분할 수 있게 한다.
    // 구버전 응답에는 전이층 자체가 없으므로 항상 "floor" 만 전달된다.
    // 키 접미사 "_PDR" (PDR 경로) 도 base level 의 type 을 그대로 따라간다.
    fun onPathPixelData(pathPixelKey: String, levelType: String, data: PathPixelData)
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
    fun onError(error: ResourceError, key: String)

    // 층이동 구간 per-key 콜백. key 는 전이층 자체의 "${sectorId}_${bldg}_${전이층이름}" —
    // onGeofenceData / onNodeLinkData / onBuildingLevelImageData 등 다른 level-scope 콜백과
    // 동일한 형식이라 소비자는 같은 key 로 상관관계를 잡을 수 있다.
    // 서버 스키마 2026-08-06+ 에서만 발동. 기존 구현체는 override 없이 두면 무시.
    fun onTransitionData(transitionKey: String, data: TransitionOutput) {}
}

interface TJLabsWarpResourceManagerDelegate {
    fun onWarpSectorData(data: WarpSectorOutput)
    fun onWarpError(error: ResourceError)
}

interface TJLabsVenusResourceManagerDelegate {
    fun onVenusSectorData(data: VenusSectorOutput)
    fun onVenusError(error: ResourceError)
}

interface TJLabsSimulationResourceManagerDelegate {
    fun onSimulationData(sectorId: Int, data: SimulationBundleOutput)
}

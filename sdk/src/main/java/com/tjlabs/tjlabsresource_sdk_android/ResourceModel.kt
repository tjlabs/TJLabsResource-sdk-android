package com.tjlabs.tjlabsresource_sdk_android

import android.graphics.Bitmap

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

data class ParameterData(
    val trajectory_length: Int = 0,
    val trajectory_diagonal: Int = 0,
    val debug: Boolean = false,
    val standard_rss: List<Int> = listOf()
)

/**
 * 층 지오펜스. 2026-09-28+ 스키마에서 세 영역 타입마다 **다각형 목록** 을 담는다.
 * - 최상단 [List]: 이 영역 안의 다각형들.
 * - 다각형: 꼭짓점 [Pair] 형식의 [List<Int>] (크기 2, `[x, y]`) 을 시계·반시계 무관하게 나열한 것.
 *   꼭짓점 3개 이상, 마지막→첫 꼭짓점 자동 연결(닫힌 다각형), 오목 다각형 가능.
 *   서버가 저장 시 self-intersection 검증을 통과한 유효 폴리곤만 실린다.
 *
 * 각 필드는 서버가 항상 세 키 모두 실어 보내므로 부재하면 서버 응답 오류로 취급 — 기본값 emptyList.
 * 영역이 없는 타입은 빈 배열이다.
 *
 * 소비자 (jupiter-sdk 등) 는 이 데이터로 point-in-polygon 판정(ray casting 등) 을 수행해야 한다.
 * 이전 스키마 (`2026-09-10` 이하) 는 사각형 목록 `[[xMin, yMin, xMax, yMax], ...]` 이었다 —
 * 두 스키마는 상호 호환되지 않으므로 SDK 버전과 서버 버전을 함께 올려야 한다.
 */
data class GeofenceData(
    val entrance_area: List<List<List<Int>>> = emptyList(),
    val entrance_matching_area: List<List<List<Int>>> = emptyList(),
    val level_change_area: List<List<List<Int>>> = emptyList(),
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
    val building: DefaultPositionBuildingOutput,
    // iOS parity (2026-10-02 server) — 섹터별 지도 줌 레벨. 서버가 nullable 로 보낼 수 있어 Optional.
    // JSON key 는 `zoom_level`. 소비자 (지도 UI) 가 min/default/max 로 초기 줌 설정.
    val zoom_level: ZoomLevel? = null,
)

/**
 * 섹터별 지도 줌 레벨. iOS `ZoomLevel` struct 매핑.
 * - [min] : 최소 허용 줌
 * - [default_value] : 초기/기본 줌. JSON key 는 `default` (Kotlin 식별자로 사용 가능하지만
 *   Java interop + 명시성 위해 `default_value` 로 두고 Gson `@SerializedName("default")` 매핑).
 * - [max] : 최대 허용 줌
 */
data class ZoomLevel(
    val min: Double,
    @com.google.gson.annotations.SerializedName("default") val default_value: Double,
    val max: Double,
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
    val graph: SectorBundleGraphOutput?,
    // 2026-09-10 스키마: ward 와 RF 랜드마크 분리. wards 는 이 층에 설치된 ward 목록(id/name)만.
    // 이 층에서 잡히는 신호 중 다른 층 ward 소속 랜드마크는 [rf_landmarks] 에만 실린다.
    val wards: List<Ward>?,
    // 2026-09-10 스키마: level 직속 평면 랜드마크 목록. 각 항목은 자기 ward 를 그 자체로 포함
    // (id, name) — 다른 층 ward 일 수 있음. 소비자는 [wards] 에서 lookup 하지 말고 이 필드의
    // ward 정보를 그대로 사용해야 한다.
    val rf_landmarks: List<LandmarkInfo>?,
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
    val parking_matches: List<ParkingMatch>? = null,
    // parking_matches 파일 root 의 "level_match" 값 (사용자/호스트 앱이 인식하는 층 표기, 예: "3").
    // 파일이 업로드된 층에서만 채워지고, 파일 자체에 필드가 없으면 null.
    // building 단위로만 유일하므로 (building, level_match) 쌍으로 levelId 를 역인덱싱한다.
    val level_match: String? = null
)

// MARK: - Parking Matches (2026-08-28)
// level 하나에 대응하는 GeoJSON 피처 ↔ 외부 업체 주차면 ID 매핑 파일의 파싱 결과.
data class ParkingMatchesData(
    val matches: List<ParkingMatch>,
    // 파일 root 의 "level_match" (사용자/호스트 앱 관점의 층 표기). 없으면 null.
    val level_match: String? = null
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
    // 2026-09-10 스키마: 이 랜드마크가 속한 ward. 지금 level 의 wards[] 에 없을 수 있다
    // (다른 층에 설치된 ward 의 신호가 이 층에서 잡힌 케이스).
    val ward: Ward,
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

    /**
     * 멀티 섹터 로드 ([TJLabsMultiResourceManager.loadResources]) 전용 - 섹터별 처리가 끝날
     * 때마다 1회 호출된다 (요청 섹터 순서). 모든 data callback 이 전달된 뒤 발화되므로 소비자는
     * 이 시점에 해당 섹터의 상태가 반영 완료라고 가정 가능 (단, 네트워크 자원 — map image,
     * entrance route CSV — 는 비동기라 그 이후에 도착할 수 있음).
     * 단일 섹터 `loadJupiterResource` 흐름에서는 호출되지 않는다. 기본 구현이 비어 있어 override 선택.
     *
     * iOS `TJLabsResourceManagerDelegate.onSectorResourceLoadFinished(_:sectorId:result:)` 와 매핑.
     */
    fun onSectorResourceLoadFinished(sectorId: Int, result: SectorLoadResult) {}
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

// ===========================================================================
// Multi-sector bundle load (TJ-559 / TJ-580) - iOS parity 모델
// ===========================================================================
// 멀티 섹터 로드 (TJLabsMultiResourceManager) 가 소비자에게 전달하는 결과 타입들.
// 단일 섹터 loadJupiterResource 흐름은 영향 받지 않는다 — 추가 모델만 공존.

/**
 * 섹터 로드가 실패했을 때 어느 단계에서 끊겼는지. iOS `ResourceLoadStage` 와 1:1 매칭.
 */
enum class ResourceLoadStage {
    SECTOR_BUNDLE_METADATA,
    SECTOR_BUNDLE_DOWNLOAD,
    IMAGE,
    GEOFENCE,
    ENTRANCE,
    ENTRANCE_ROUTE,
    UNITS,
    NODE_LINK,
    PATH_PIXEL,
    WARDS,
    PARKING_MATCHES
}

/**
 * 섹터 로드 중 발생한 개별 실패 아이템. `isCritical=true` 이면 그 섹터는 실패로 집계된다
 * (iOS 와 동일 — DR path CSV 누락, 노드/링크 build 실패, bundle.json 누락 등).
 * optional 자원 (map image, entrance route CSV 등) 은 `isCritical=false` 로 수집만 되고
 * 소비자 사이드 UI 에 노출용으로 유용.
 */
data class ResourceLoadStageFailure(
    val stage: ResourceLoadStage,
    val key: String,
    val isCritical: Boolean,
    val statusCode: Int? = null
)

/**
 * 단일 섹터 로드 결과 (iOS `SectorLoadResult` 매핑). `versionId` 와 `isCached` 는 조합
 * zip 전체의 속성이므로 같은 로드의 모든 섹터가 공유.
 */
data class SectorLoadResult(
    val sectorId: Int,
    val isSuccess: Boolean,
    val failedStage: ResourceLoadStage?,
    val failures: List<ResourceLoadStageFailure>,
    val versionId: String,
    val isCached: Boolean
)

/**
 * 멀티 섹터 로드 입력 — telemetry 용으로 result 에 다시 담겨 반환된다.
 * 단일 섹터 호환성: `sectorId` 는 첫 번째 요청 섹터.
 */
data class MultiResourceLoadInput(
    val provider: String,
    val region: String,
    val env: ResourceServerEnv,
    val sectorIds: List<Int>,
    val forceUpdate: Boolean,
    val imageLoadPolicy: ImageLoadPolicy
) {
    val sectorId: Int get() = sectorIds.firstOrNull() ?: 0
}

/**
 * 멀티 섹터 로드 완료 콜백 결과 (iOS `ResourceLoadResult` 매핑).
 * 모든 섹터가 성공한 경우에만 `isSuccess=true`. 섹터별 상세는 `sectorResults`.
 */
data class MultiResourceLoadResult(
    val eventCode: Int,
    val message: String,
    val isSuccess: Boolean,
    val failedStage: ResourceLoadStage?,
    val failures: List<ResourceLoadStageFailure>,
    val input: MultiResourceLoadInput,
    val versionId: String?,
    val isCached: Boolean,
    val sectorResults: List<SectorLoadResult>,
    // ── 2026-10 track payload (문제 발생 시 서버 추적용) ───────────────────────────
    /** 발화 경로 (FRESH / MULTI_FRESH / MULTI_CACHED / FALLBACK / ...). 소비자가 서버 전송 시 label 포함. */
    val emitSource: EmitSource = EmitSource.MULTI_FRESH,
    /** `isCached=true, versionVerified=false` 조합이면 폴백 사용 — 서버 version 과 다를 수 있다는 신호. */
    val versionVerified: Boolean = true,
    /** 단계별 wall-clock (ms). 네트워크 jitter / extract / parse 를 분리해서 서버에서 bottleneck 분석 가능. */
    val stageTimings: MultiResourceStageTimings? = null,
)

/**
 * Multi 로더 단일 session 의 단계별 wall-clock 분해. `MultiResourceLoadResult.stageTimings` 로 함께 실려
 * 소비자가 서버 텔레메트리 payload 에 포함 가능. 모든 값 ms. 측정 안 된 단계는 0 (예: cache HIT 시
 * zipDownloadMs=0, FALLBACK 시 metadataFetchMs 는 실패까지의 시간, zipDownloadMs=0).
 */
data class MultiResourceStageTimings(
    val metadataFetchMs: Long,
    val zipDownloadMs: Long,
    val zipExtractMs: Long,
    val prepareParallelMs: Long,
    val totalMs: Long,
)

/**
 * Multi 로더 전용 — 각 섹터 처리 결과 ([TJLabsBundleDataManager.processSectorFromArchive]).
 * SDK internal 레벨에서만 사용.
 */
internal data class SectorProcessOutcome(
    val isSuccess: Boolean,
    val failures: List<ResourceLoadStageFailure>
)

/**
 * loadResource 성공/실패 이벤트 코드. iOS `TJLabsResourceCode` 와 동일한 값.
 */
object TJLabsResourceCode {
    const val LOAD_SUCCESS = 2102
    const val LOAD_FAILURE = 5101
    const val UNCLASSIFIED_FAILURE_STATUS = -1
    fun loadResourcesEventCode(isSuccess: Boolean): Int = if (isSuccess) LOAD_SUCCESS else LOAD_FAILURE
}

/**
 * Delegate 데이터 콜백 발화 경로 식별자. `(postLoad timing)` 로그와 소비자 (Jupiter 등) 가
 * 서버 텔레메트리에 포함할 때 "어느 경로로 데이터가 소비자에게 도달했는지" 를 track 가능.
 *
 *  - [FRESH_LOAD]  : bundle 을 서버에서 새로 받아 첫 발화. 단일 섹터 cold load.
 *  - [CACHE_HIT]   : 디스크/메모리 캐시에서 bundle 재사용. 단일 섹터 warm load.
 *  - [MULTI_FRESH] : Multi 로더가 조합 zip 을 새로 받아 섹터별 발화. `isCached=false, versionVerified=true`.
 *  - [MULTI_CACHED]: Multi 로더가 조합 zip 재사용 (버전 일치). `isCached=true, versionVerified=true`.
 *  - [FALLBACK]    : Phase 1 오프라인 폴백 (meta/raw 실패 → 디스크 bundle 재조립). `versionVerified=false`.
 *  - [SNAPSHOT_HIT]: 외부가 `emitCachedSnapshot(bundleType, sectorId)` 공개 API 로 재발화 요청.
 */
enum class EmitSource(val label: String) {
    FRESH_LOAD("FRESH"),
    CACHE_HIT("CACHE_HIT"),
    MULTI_FRESH("MULTI_FRESH"),
    MULTI_CACHED("MULTI_CACHED"),
    FALLBACK("FALLBACK"),
    SNAPSHOT_HIT("SNAPSHOT_HIT");
}

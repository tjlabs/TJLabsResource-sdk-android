package com.tjlabs.tjlabsresource_sdk_android

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

interface TJLabsResourceManagerDelegate

data class EntranceData(
    var number: Int = 0,
    var networkStatus: Boolean = false,
    var velocityScale: Float = 0f,
    var innerWardId: String = "",
    var innerWardRssi: Float = 0f,
    var innerWardCoord: List<Float> = emptyList()
)

data class EntranceRouteData(
    var routeLevel: List<String> = emptyList(),
    var route: List<List<Float>> = listOf(emptyList())
)

data class EntranceRouteDataIsLoaded(
    var isLoaded: Boolean = false,
    var url: String = ""
)
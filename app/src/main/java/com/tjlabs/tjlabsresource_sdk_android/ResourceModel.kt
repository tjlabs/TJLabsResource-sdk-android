package com.tjlabs.tjlabsresource_sdk_android

data class InputSector(
    var sector_id: Int = 0,
    var operating_system: String = "Android"
)

data class PathPixelData(
    val roadType: List<Int> = listOf(),
    val nodeNumber: List<Int> = listOf(),
    val road: List<List<Float>> = listOf(),
    val roadScale: List<Float> = listOf(),
    val roadHeading: List<String> = listOf()
)


data class PathPixel(
    val building_name: String = "",
    val level_name: String = "",
    val url : String = "",
    val OutputPathPixel : List<PathPixel> = listOf()
)

data class OutputPathPixel(
    val path_pixel_list : List<PathPixel> = listOf()
)
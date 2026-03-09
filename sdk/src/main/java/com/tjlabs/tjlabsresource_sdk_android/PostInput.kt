package com.tjlabs.tjlabsresource_sdk_android

import retrofit2.Call
import retrofit2.http.*

internal interface PostInput {
    @Headers(
        "accept: application/json",
        "content-type: application/json"
    )

    @GET("/{server_version}/sectors/{pk}")
    fun getSector(
        @Path("server_version") serverVersion: String,
        @Path("pk") pk: Int
    ): Call<SectorOutput>

    @GET("/{server_version}/levels/{pk}/entrances")
    fun getEntrance(
        @Path("server_version") serverVersion: String,
        @Path("pk") pk: Int,
        @Query("operating_system") os: String
    ): Call<EntranceOutput>

    @GET("/{server_version}/levels/{pk}/scale")
    fun getScaleOffset(
        @Path("server_version") serverVersion: String,
        @Path("pk") pk: Int,
        @Query("operating_system") os: String
    ): Call<ScaleOffsetOutput>

    @GET("/{server_version}/levels/{pk}/path")
    fun getPathPixel(
        @Path("server_version") serverVersion: String,
        @Path("pk") pk: Int,
        @Query("operating_system") os: String
    ): Call<PathPixelOutput>

    @GET("/{server_version}/levels/{pk}/geofence")
    fun getGeofence(
        @Path("server_version") serverVersion: String,
        @Path("pk") pk: Int
    ): Call<GeofenceData>

    //제거
    @GET("/{server_version}/sectors/{pk}/parameter")
    fun getSectorParam(
        @Path("server_version") serverVersion: String,
        @Path("pk") pk: Int,
        @Query("operating_system") os: String
    ): Call<SectorParameterOutput>

    //제거
    @GET("/{server_version}/levels/{pk}/parameter")
    fun getLevelParam(
        @Path("server_version") serverVersion: String,
        @Path("pk") pk: Int,
        @Query("operating_system") os: String
    ): Call<LevelParameterOutput>

    @GET("/{server_version}/levels/{pk}/units")
    fun getLevelUnits(
        @Path("server_version") serverVersion: String,
        @Path("pk") pk: Int,
        @Query("category") category: Category?
    ): Call<LevelUnitsOutput>

    @GET("/{server_version}/levels/{pk}")
    fun getLevelWards(
        @Path("server_version") serverVersion: String,
        @Path("pk") pk: Int
    ): Call<LevelWardsOutput>

    @GET("/{user_affine_server_version}/sectors/{sector_id}/wgs84-transform")
    fun getUserAffineTrans(
        @Path("user_affine_server_version") userAffineServerVersion: String,
        @Path("sector_id") sectorId: Int
    ): Call<AffineTransParamOutput>

    @GET("/{user_graph_nodes_server_version}/levels/{pk}/graphs/nodes")
    fun getLevelNodes(
        @Path("user_graph_nodes_server_version") serverVersion: String,
        @Path("pk") pk : Int,
    ): Call<GraphLevelNodesOutput>

    @GET("/{user_graph_links_server_version}/levels/{pk}/graphs/links")
    fun getLevelLinks(
        @Path("user_graph_links_server_version") serverVersion: String,
        @Path("pk") pk : Int,
    ): Call<GraphLevelLinksOutput>

    @GET("/{user_graph_link_groups_server_version}/levels/{pk}/graphs/link-groups")
    fun getLevelLinkGroups(
        @Path("user_graph_link_groups_server_version") serverVersion: String,
        @Path("pk") pk : Int,
    ): Call<GraphLevelLinksGroupsOutput>

    @GET("/{user_graph_path_server_version}/levels/{pk}/graphs/paths")
    fun getLevelPaths(
        @Path("user_graph_path_server_version") serverVersion: String,
        @Path("pk") pk : Int,
        @Query("operating_system") os : String = "Android"
    ): Call<GraphLevelPathsOutput>

    @GET("/{user_landmark_server_version}/levels/{pk}/maps/rf-landmarks")
    fun getLevelLandmarks(
        @Path("user_landmark_server_version") serverVersion: String,
        @Path("pk") pk : Int,
        @Query("dead_reckoning") dr : String = "DR",
        @Query("operating_system") os : String = "Android"
    ): Call<LevelLandmarkOutput>





}

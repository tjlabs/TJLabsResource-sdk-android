package com.tjlabs.tjlabsresource_sdk_android

import com.tjlabs.tjlabsauth_sdk_android.TJLabsAuthManager
import com.tjlabs.tjlabsauth_sdk_android.TokenResult
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

const val TIMEOUT_VALUE_PUT = 5L

internal object TJLabsResourceNetworkConstants {
    private var token = ""

    fun genRetrofit(url : String) : Retrofit {
        TJLabsAuthManager.getAccessToken() {
                tokenResult ->
            when(tokenResult) {
                is TokenResult.Success -> {
                    token = tokenResult.token
                }
                is TokenResult.Failure -> {

                }
            }
        }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_VALUE_PUT, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_VALUE_PUT, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_VALUE_PUT, TimeUnit.SECONDS)
            .addInterceptor(HeaderInterceptor(token))
            .build()

        return Retrofit.Builder()
            .baseUrl(url)
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
            .build()
    }

    // MARK: - VERSION
    private const val USER_SECTOR_SERVER_VERSION = "2025-03-26"
    private const val USER_PATH_PIXEL_SERVER_VERSION = "2025-03-27"
    private const val USER_SCALE_SERVER_VERSION = "2025-03-27"
    private const val USER_LEVEL_UNITS_SERVER_VERSION = "2026-01-27"
    private const val USER_UNIT_SERVER_VERSION = "2026-01-27"
    private const val USER_GEO_SERVER_VERSION = "2026-02-10"
    private const val USER_ENTRANCE_SERVER_VERSION = "2026-02-10"
    private const val USER_SECTOR_PARAM_SERVER_VERSION = "2025-03-31"
    private const val USER_LEVEL_PARAM_SERVER_VERSION = "2025-03-31"
    private const val USER_LEVEL_WARDS_SERVER_VERSION = "2025-04-17"
    private const val USER_AFFINE_SERVER_VERSION = "2025-08-25"
    private const val USER_GRAPHS_NODES_VERSION = "2026-02-19"
    private const val USER_GRAPHS_LINKS_VERSION = "2026-02-19"
    private const val USER_GRAPHS_LINK_GROUPS_VERSION = "2026-02-19"
    private const val USER_GRAPHS_PATHS_VERSION = "2026-02-19" //path pixel


    private const val HTTP_PREFIX = "https://"
    private var REGION_PREFIX = "ap-northeast-2."
    private const val OLYMPUS_SUFFIX = ".jupiter.tjlabs.dev"
    private var REGION_NAME = "Korea"

    private var USER_URL = HTTP_PREFIX + REGION_PREFIX + "user" + OLYMPUS_SUFFIX
    private var CSV_URL =  HTTP_PREFIX + REGION_PREFIX + "csv" + OLYMPUS_SUFFIX
    private var CLIENT_URL =  HTTP_PREFIX + REGION_PREFIX + "client" + OLYMPUS_SUFFIX

    fun setServerURL(region: String) {
        when (region) {
            ResourceRegion.KOREA.value -> {
                REGION_PREFIX = "ap-northeast-2."
                REGION_NAME = "Korea"
            }
            ResourceRegion.CANADA.value -> {
                REGION_PREFIX = "ca-central-1."
                REGION_NAME = "Canada"
            }
            ResourceRegion.US_EAST.value -> {
                REGION_PREFIX = "us-east-1."
                REGION_NAME = "US"
            }
            else -> {
                REGION_PREFIX = "ap-northeast-2."
                REGION_NAME = "Korea"
            }
        }

        USER_URL = HTTP_PREFIX + REGION_PREFIX + "user" + OLYMPUS_SUFFIX
        CSV_URL = HTTP_PREFIX + REGION_PREFIX + "csv" + OLYMPUS_SUFFIX
        CLIENT_URL =  HTTP_PREFIX + REGION_PREFIX + "client" + OLYMPUS_SUFFIX
    }

    fun getUserBaseURL() : String{
        return USER_URL
    }

    fun getUserSectorVersion() : String {
        return USER_SECTOR_SERVER_VERSION
    }

    fun getUserPathPixelServerVersion() : String {
        return USER_PATH_PIXEL_SERVER_VERSION
    }

    fun getUserScaleServerVersion() : String {
        return USER_SCALE_SERVER_VERSION
    }

    fun getUserLevelUnitsServerVersion() : String {
        return USER_LEVEL_UNITS_SERVER_VERSION
    }

    fun getUserGeoServerVersion() : String {
        return USER_GEO_SERVER_VERSION
    }

    fun getUserEntranceServerVersion() : String {
        return USER_ENTRANCE_SERVER_VERSION
    }

    fun getUserSectorParamVersion() : String {
        return USER_SECTOR_PARAM_SERVER_VERSION
    }

    fun getUserLevelParamVersion() : String {
        return USER_LEVEL_PARAM_SERVER_VERSION
    }

    fun getUserLevelWardsVersion() : String {
        return USER_LEVEL_WARDS_SERVER_VERSION
    }

    fun getUserAffineServerVersion() : String {
        return USER_AFFINE_SERVER_VERSION
    }

    fun getUserGraphsNodesServerVersion() : String {
        return USER_GRAPHS_NODES_VERSION
    }

    fun getUserGraphsLinksServerVersion() : String {
        return USER_GRAPHS_LINKS_VERSION
    }

    fun getUserGraphsLinkGroupsServerVersion() : String {
        return USER_GRAPHS_LINK_GROUPS_VERSION
    }

    fun getUserGraphsPathsServerVersion() : String {
        return USER_GRAPHS_PATHS_VERSION
    }

    class HeaderInterceptor (private val token: String) : Interceptor {
        @Throws(IOException::class)
        override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
            val token = "Bearer $token"
            val newRequest = chain.request().newBuilder()
                .addHeader("authorization", token)
                .build()
            return chain.proceed(newRequest)
        }
    }
}
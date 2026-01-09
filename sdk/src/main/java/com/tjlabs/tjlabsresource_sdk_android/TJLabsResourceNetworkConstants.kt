package com.tjlabs.tjlabsresource_sdk_android

import android.util.Log
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
    private const val USER_PATHPIXEL_SERVER_VERSION = "2025-03-27"
    private const val USER_SCALE_SERVER_VERSION = "2025-03-27"
    private const val USER_UNIT_SERVER_VERSIOM = "2025-03-28"
    private const val USER_GEO_SERVER_VERSION = "2025-03-28"
    private const val USER_ENTERANCE_SERVER_VERSION = "2025-03-31"
    private const val USER_SECTOR_PARAM_SERVER_VERSION = "2025-03-31"
    private const val USER_LEVEL_PARAM_SERVER_VERSION = "2025-03-31"
    private const val USER_LEVEL_WARDS_SERVER_VERSION = "2025-04-17"
    private const val USER_AFFINE_SERVER_VERSION = "2025-08-25"

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
        return USER_PATHPIXEL_SERVER_VERSION
    }

    fun getUserScaleServerVersion() : String {
        return USER_SCALE_SERVER_VERSION
    }

    fun getUserUnitServerVersion() : String {
        return USER_UNIT_SERVER_VERSIOM
    }

    fun getUserGeoServerVersion() : String {
        return USER_GEO_SERVER_VERSION
    }

    fun getUserEntranceServerVersion() : String {
        return USER_ENTERANCE_SERVER_VERSION
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
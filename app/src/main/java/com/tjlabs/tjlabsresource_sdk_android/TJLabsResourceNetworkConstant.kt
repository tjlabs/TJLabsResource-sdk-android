package com.tjlabs.tjlabsresource_sdk_android

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

const val TIMEOUT_VALUE_PUT = 5L

internal object TJLabsResourceNetworkConstant {
    fun genRetrofit(url : String) : Retrofit {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_VALUE_PUT, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_VALUE_PUT, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_VALUE_PUT, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(url)
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
            .build()
    }

    private const val PATHPIXEL_SERVER_VERSION = "2024-11-14"

    private const val HTTP_PREFIX = "https://"
    private var REGION_PREFIX = "ap-northeast-2."
    private const val OLYMPUS_SUFFIX = ".jupiter.tjlabs.dev"
    private var REGION_NAME = "Korea"

    private var USER_URL = HTTP_PREFIX + REGION_PREFIX + "user" + OLYMPUS_SUFFIX
    private var IMAGE_URL =  HTTP_PREFIX + REGION_PREFIX + "img" + OLYMPUS_SUFFIX
    private var CSV_URL =  HTTP_PREFIX + REGION_PREFIX + "csv" + OLYMPUS_SUFFIX
    private var CLIENT_URL =  HTTP_PREFIX + REGION_PREFIX + "client" + OLYMPUS_SUFFIX

    fun getUserBaseURL() : String{
        return USER_URL
    }

    fun getPathPixelServerVersion() : String {
        return PATHPIXEL_SERVER_VERSION
    }

    fun setServerURL(region: String) {
        when (region) {
            JupiterRegion.KOREA -> {
                REGION_PREFIX = "ap-northeast-2."
                REGION_NAME = "Korea"
            }
            JupiterRegion.CANADA -> {
                REGION_PREFIX = "ca-central-1."
                REGION_NAME = "Canada"
            }
            JupiterRegion.US_EAST -> {
                REGION_PREFIX = "us-east-1."
                REGION_NAME = "US"
            }
            else -> {
                REGION_PREFIX = "ap-northeast-2."
                REGION_NAME = "Korea"
            }
        }

        USER_URL = HTTP_PREFIX + REGION_PREFIX + "user" + OLYMPUS_SUFFIX
        IMAGE_URL = HTTP_PREFIX + REGION_PREFIX + "img" + OLYMPUS_SUFFIX
        CSV_URL = HTTP_PREFIX + REGION_PREFIX + "csv" + OLYMPUS_SUFFIX
        CLIENT_URL =  HTTP_PREFIX + REGION_PREFIX + "client" + OLYMPUS_SUFFIX
    }


}
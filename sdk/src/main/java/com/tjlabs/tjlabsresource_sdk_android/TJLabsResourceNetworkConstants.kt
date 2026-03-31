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

    private const val USER_SECTOR_BUNDLE_SERVER_VERSION = "2026-03-30"

    private const val HTTP_PREFIX = "https://"
    private var REGION_PREFIX = "ap-northeast-2."
    private const val OLYMPUS_SUFFIX = ".jupiter.tjlabs.dev"

    private var USER_URL = HTTP_PREFIX + REGION_PREFIX + "user" + OLYMPUS_SUFFIX

    fun setServerURL(region: String) {
        when (region) {
            ResourceRegion.KOREA.value -> {
                REGION_PREFIX = "ap-northeast-2."
            }
            ResourceRegion.CANADA.value -> {
                REGION_PREFIX = "ca-central-1."
            }
            ResourceRegion.US_EAST.value -> {
                REGION_PREFIX = "us-east-1."
            }
            else -> {
                REGION_PREFIX = "ap-northeast-2."
            }
        }

        USER_URL = HTTP_PREFIX + REGION_PREFIX + "user" + OLYMPUS_SUFFIX
    }

    fun getUserBaseURL() : String{
        return USER_URL
    }

    fun getUserSectorBundleVersion() : String {
        return USER_SECTOR_BUNDLE_SERVER_VERSION
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

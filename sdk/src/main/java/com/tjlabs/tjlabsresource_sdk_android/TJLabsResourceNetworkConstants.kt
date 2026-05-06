package com.tjlabs.tjlabsresource_sdk_android

import com.tjlabs.tjlabsauth_sdk_android.TJLabsAuthManager
import com.tjlabs.tjlabsauth_sdk_android.TokenResult
import com.tjlabs.tjlabsresource_sdk_android.util.TJResourceLogger
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

const val TIMEOUT_VALUE_PUT = 5L

internal object TJLabsResourceNetworkConstants {
    private fun buildRetrofit(url: String, token: String? = null): Retrofit {
        val okHttpBuilder = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_VALUE_PUT, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_VALUE_PUT, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_VALUE_PUT, TimeUnit.SECONDS)

        if (token.isNullOrBlank().not()) {
            okHttpBuilder.addInterceptor(HeaderInterceptor(token!!))
        }

        val okHttpClient = okHttpBuilder.build()

        return Retrofit.Builder()
            .baseUrl(url)
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
            .build()
    }

    fun genRetrofit(
        url: String,
        completion: (retrofit: Retrofit?, statusCode: Int, message: String) -> Unit
    ) {
        TJLabsAuthManager.getAccessToken { tokenResult ->

            TJResourceLogger.d(
                "(TJLabsResource) tokenResult : $tokenResult"
            )

            when (tokenResult) {
                is TokenResult.Success -> {

                    completion(buildRetrofit(url, tokenResult.token), 200, "ok")
                }
                is TokenResult.Failure -> {
                    val status = tokenResult.statusCode ?: 401
                    val msg = tokenResult.message ?: "getAccessToken failed: ${tokenResult.reason}"
                    completion(null, status, msg)
                }
            }
        }
    }

    fun genPlainRetrofit(url: String): Retrofit {
        return buildRetrofit(url)
    }

    private const val JUPITER_SECTOR_BUNDLE_SERVER_VERSION = "2026-04-27"
    private const val VENUS_SECTOR_BUNDLE_SERVER_VERSION = "2026-04-27"
    private const val WARP_SECTOR_BUNDLE_SERVER_VERSION = "2026-04-27"

    private const val HTTP_PREFIX = "https://"
    private var REGION_PREFIX = "ap-northeast-2."
    private const val OLYMPUS_SUFFIX = ".jupiter.tjlabs.dev"
    private const val WARP_SUFFIX = ".warp.tjlabs.dev"
    private var currentProvider : String = ServerProvider.AWS.value

    private var USER_URL = HTTP_PREFIX + REGION_PREFIX + "user" + OLYMPUS_SUFFIX
    private var WARP_USER_URL = HTTP_PREFIX + REGION_PREFIX + "user" + WARP_SUFFIX


    fun setServerURL(provider: String, region: String) {
        currentProvider = provider
        REGION_PREFIX = when (region) {
            ResourceRegion.KOREA.value -> {
                when (provider) {
                    ServerProvider.AWS.value -> "ap-northeast-2."
                    ServerProvider.GCP.value -> "asia-northeast3."
                    else -> {"ap-northeast-2."}
                }
            }

            ResourceRegion.CANADA.value -> "ca-central-1."
            ResourceRegion.US_EAST.value -> "us-east-1."
            ResourceRegion.SAUDI.value -> "me-central2."

            else -> "ap-northeast-2."
        }

        USER_URL = HTTP_PREFIX + REGION_PREFIX + "user" + OLYMPUS_SUFFIX
        WARP_USER_URL = HTTP_PREFIX + REGION_PREFIX + "user" + WARP_SUFFIX
    }

    fun getUserBaseURL(): String {
        return USER_URL
    }

    fun getBaseUrl(bundleType: ResourceBundleType): String {
        return when (bundleType) {
            ResourceBundleType.JUPITER, ResourceBundleType.VENUS -> USER_URL
            ResourceBundleType.WARP -> WARP_USER_URL
        }
    }

    fun getBundleServerVersion(bundleType: ResourceBundleType): String {
        return when (bundleType) {
            ResourceBundleType.JUPITER -> JUPITER_SECTOR_BUNDLE_SERVER_VERSION
            ResourceBundleType.VENUS -> VENUS_SECTOR_BUNDLE_SERVER_VERSION
            ResourceBundleType.WARP -> WARP_SECTOR_BUNDLE_SERVER_VERSION
        }
    }

    class HeaderInterceptor(private val token: String) : Interceptor {
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

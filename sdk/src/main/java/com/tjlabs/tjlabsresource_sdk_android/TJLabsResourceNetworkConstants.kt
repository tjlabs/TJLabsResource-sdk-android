package com.tjlabs.tjlabsresource_sdk_android

import com.tjlabs.tjlabsauth_sdk_android.TJLabsAuthManager
import com.tjlabs.tjlabsauth_sdk_android.TokenResult
import com.tjlabs.tjlabsresource_sdk_android.onprem.OnPremRoutingState
import com.tjlabs.tjlabsresource_sdk_android.util.TJResourceLogger
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection

const val TIMEOUT_VALUE_PUT = 5L

internal object TJLabsResourceNetworkConstants {
    private fun buildRetrofit(url: String, token: String? = null): Retrofit {
        val okHttpBuilder = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_VALUE_PUT, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_VALUE_PUT, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_VALUE_PUT, TimeUnit.SECONDS)
            .hostnameVerifier(onPremScopedHostnameVerifier())

        if (token.isNullOrBlank().not()) {
            okHttpBuilder.addInterceptor(HeaderInterceptor(token!!))
        }

        val okHttpClient = okHttpBuilder.build()

        // Retrofit 은 baseUrl 이 반드시 '/' 로 끝나야 relative endpoint path 를 append 함.
        // on-prem baseUrl 은 OnPremRoutingState.enable 에서 trailing / 를 잘라 저장하므로
        // 여기서 다시 붙여서 안전하게 처리.
        val normalized = if (url.endsWith("/")) url else "$url/"

        return Retrofit.Builder()
            .baseUrl(normalized)
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
            .build()
    }

    fun genRetrofit(
        url: String,
        completion: (retrofit: Retrofit?, statusCode: Int, message: String) -> Unit
    ) {
        // On-prem 모드는 TJLabsAuthManager 를 거치지 않는다. 토큰은 상위 계층
        // (jupiter-sdk) 이 tokenProvider 로 주입한 값을 매 요청마다 조회.
        if (OnPremRoutingState.isEnabled) {
            val token = OnPremRoutingState.currentToken()
            if (token.isBlank()) {
                completion(null, 401, "on-prem access token missing")
                return
            }
            completion(buildRetrofit(url, token), 200, "ok")
            return
        }

        val authStartMs = System.currentTimeMillis()
        TJLabsAuthManager.getAccessToken { tokenResult ->
            val authElapsed = System.currentTimeMillis() - authStartMs
            TJResourceLogger.d {
                "(TJLabsResource) perf getAccessToken // elapsedMs=$authElapsed // result=${tokenResult::class.simpleName}"
            }

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

    private const val JUPITER_SECTOR_BUNDLE_SERVER_VERSION = "2026-08-28"
    private const val VENUS_SECTOR_BUNDLE_SERVER_VERSION = "2026-04-27"
    private const val WARP_SECTOR_BUNDLE_SERVER_VERSION = "2026-04-27"

    private const val HTTP_PREFIX = "https://"
    private var REGION_PREFIX = "ap-northeast-2."
    // OLYMPUS (Jupiter 도메인) : env 로 PROD/DEV 스위칭.
    //  PROD : .jupiter.tjlabscorp.com — 실 운영
    //  DEV  : .jupiter.tjlabs.dev — 내부 테스트
    private const val OLYMPUS_SUFFIX_PROD = ".jupiter.tjlabscorp.com"
    private const val OLYMPUS_SUFFIX_DEV  = ".jupiter.tjlabs.dev"
    private var currentOlympusSuffix = OLYMPUS_SUFFIX_PROD  // 기본 PROD
    // WARP : AWS 기반 별도 인프라 — env 스위칭 대상 아님.
    private const val WARP_SUFFIX = ".warp.tjlabs.dev"
    private var currentProvider : String = ServerProvider.AWS.value
    private var currentRegion : String = ResourceRegion.KOREA.value
    // 마지막으로 세팅된 env. `setServerURL` 이 호출될 때마다 갱신되고, env 를 명시하지 않는
    // load* API 들이 default 로 이 값을 사용한다. 초기값 PROD 는 최초 config 이전 상태의
    // 안전 기본 (실 배포에서 실수로 DEV URL 이 조립되는 상황 예방).
    @Volatile
    private var currentEnv: ResourceServerEnv = ResourceServerEnv.PROD

    private var USER_URL = HTTP_PREFIX + REGION_PREFIX + "user" + currentOlympusSuffix
    private var WARP_USER_URL = HTTP_PREFIX + REGION_PREFIX + "user" + WARP_SUFFIX


    fun setServerURL(provider: String, region: String, env: ResourceServerEnv = ResourceServerEnv.PROD) {
        TJResourceLogger.d("(TJLabsResource) setServerURL provider : $provider // region : $region // env : $env")

        currentProvider = provider
        currentRegion = region
        currentEnv = env
        currentOlympusSuffix = when (env) {
            ResourceServerEnv.PROD -> OLYMPUS_SUFFIX_PROD
            ResourceServerEnv.DEV_TESTING_ONLY -> OLYMPUS_SUFFIX_DEV
        }
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

        USER_URL = HTTP_PREFIX + REGION_PREFIX + "user" + currentOlympusSuffix
        WARP_USER_URL = HTTP_PREFIX + REGION_PREFIX + "user" + WARP_SUFFIX
        TJResourceLogger.d("(TJLabsResource) USER_URL : $USER_URL")
        TJResourceLogger.d("(TJLabsResource) WARP_USER_URL : $WARP_USER_URL")


    }

    /**
     * 마지막으로 setServerURL 로 확정된 env. env 미지정 load* 호출이 여기에 의존한다.
     * 초기값은 [ResourceServerEnv.PROD].
     */
    fun getCurrentEnv(): ResourceServerEnv = currentEnv

    fun getCurrentProvider(): String = currentProvider

    fun getCurrentRegion(): String = currentRegion

    fun getUserBaseURL(): String {
        return USER_URL
    }

    fun getBaseUrl(bundleType: ResourceBundleType): String {
        // On-prem 모드는 세 서비스 모두 단일 base URL. cloud 의 Warp 만 별도 도메인
        // (.warp.tjlabs.dev) 로 분리하는 규칙도 여기선 적용하지 않는다 (on-prem 은
        // API·번들 파일·도면이 전부 같은 호스트에서 나옴).
        if (OnPremRoutingState.isEnabled) {
            return OnPremRoutingState.baseUrl
        }
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

    /**
     * on-prem 서버 host 에 한해서만 hostname 검증을 pass 시키는 HostnameVerifier.
     *
     * 사용 이유: 사설 인증서 (예: CN=`tjlabscorp.com`) 로 IP (예: `192.168.120.104`) 로
     * 접속할 때 Android 기본 검증은 hostname mismatch 로 거부. Trust chain 은 network
     * security config `<trust-anchors>` 로 host-scope 제한되어 이미 안전하므로, hostname
     * 검증만 on-prem host 한정으로 우회한다.
     *
     * 그 외 모든 호스트 (cloud, 외부 SaaS) 는 시스템 기본 verifier 로 위임 → 정상 검증.
     */
    private fun onPremScopedHostnameVerifier(): HostnameVerifier {
        val default = HttpsURLConnection.getDefaultHostnameVerifier()
        return HostnameVerifier { hostname, session ->
            if (OnPremRoutingState.isEnabled) {
                val onPremHost = runCatching { URL(OnPremRoutingState.baseUrl).host }.getOrDefault("")
                if (onPremHost.isNotEmpty() && hostname.equals(onPremHost, ignoreCase = true)) {
                    return@HostnameVerifier true
                }
            }
            default.verify(hostname, session)
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

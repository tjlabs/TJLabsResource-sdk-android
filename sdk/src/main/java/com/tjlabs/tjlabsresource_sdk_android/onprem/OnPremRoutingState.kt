package com.tjlabs.tjlabsresource_sdk_android.onprem

/**
 * On-prem 서버 라우팅 상태 (resource-sdk 내부 전용).
 *
 * 이 클래스는 **인증에 관여하지 않는다** — 토큰 캐시/갱신은 상위 계층 (jupiter-sdk 의
 * `OnPremAuthState`) 의 책임이고, resource-sdk 는 매 요청마다 [tokenProvider] 를 호출해
 * 최신 토큰을 조회해서 사용할 뿐이다.
 *
 * 외부에서 상태를 조작하려면 [TJLabsResourceManager.setOnPremConfig] /
 * [TJLabsResourceManager.clearOnPremConfig] 공개 API 를 사용한다.
 */
internal object OnPremRoutingState {

    @Volatile
    var baseUrl: String = ""
        private set

    @Volatile
    private var tokenProvider: (() -> String?)? = null

    val isEnabled: Boolean
        get() = baseUrl.isNotBlank()

    /**
     * @param baseUrl scheme + host + port. trailing `/` 제거됨.
     * @param tokenProvider 요청 시점마다 호출되어 최신 JWT 를 반환하는 클로저.
     *   토큰이 아직 없거나 만료된 상태면 `null` / 빈 문자열 반환 허용.
     */
    fun enable(baseUrl: String, tokenProvider: () -> String?) {
        val trimmed = baseUrl.trim().trimEnd('/')
        require(trimmed.isNotEmpty()) { "on-prem baseUrl must not be blank" }
        require(trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            "on-prem baseUrl must include scheme (http:// or https://)"
        }
        this.baseUrl = trimmed
        this.tokenProvider = tokenProvider
    }

    fun disable() {
        baseUrl = ""
        tokenProvider = null
    }

    /** 요청 시점의 최신 토큰. provider 미등록 시 빈 문자열. */
    fun currentToken(): String = tokenProvider?.invoke().orEmpty()
}

package com.tjlabs.sdk_sample_app

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.tjlabs.resource_sdk_sample_app.BuildConfig
import com.tjlabs.resource_sdk_sample_app.R
import com.tjlabs.tjlabsauth_sdk_android.AuthServerEnv
import com.tjlabs.tjlabsauth_sdk_android.TJLabsAuthManager
import com.tjlabs.tjlabsauth_sdk_android.TokenResult
import com.tjlabs.tjlabsresource_sdk_android.*
import com.tjlabs.tjlabsresource_sdk_android.util.TJResourceLogger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), TJLabsResourceManagerDelegate, TJLabsWarpResourceManagerDelegate, TJLabsVenusResourceManagerDelegate, TJLabsSimulationResourceManagerDelegate {
    private lateinit var authStatusText: TextView
    private lateinit var jupiterStatusText: TextView
    private lateinit var jupiterScopeText: TextView
    private lateinit var jupiterDetailText: TextView
    private lateinit var venusStatusText: TextView
    private lateinit var venusScopeText: TextView
    private lateinit var venusDetailText: TextView
    private lateinit var warpStatusText: TextView
    private lateinit var warpScopeText: TextView
    private lateinit var warpDetailText: TextView
    private lateinit var simulationStatusText: TextView
    private lateinit var simulationScopeText: TextView
    private lateinit var simulationDetailText: TextView
    private lateinit var callbackContainer: LinearLayout
    private lateinit var providerGroup: RadioGroup
    private lateinit var regionGroup: RadioGroup
    private lateinit var envGroup: RadioGroup
    private lateinit var currentEnvText: TextView
    private lateinit var runAllButton: Button
    private lateinit var testJupiterBundleButton: Button
    private lateinit var testVenusBundleButton: Button
    private lateinit var testWardBundleButton: Button
    private lateinit var loadSimulationDataButton: Button
    private lateinit var clearCacheButton: Button
    private lateinit var benchmarkJupiterButton: Button
    private lateinit var benchmarkResultText: TextView
    private lateinit var toggleVerboseButton: Button
    private lateinit var clearCallbackLogButton: Button

    private var jupiterLoadStartMs: Long = 0L
    // 같은 provider 에 대해 auth() 풀 로그인은 1회만 — getAccessToken 이 토큰 캐시로 동작하기 때문
    private val authenticatedProviders = mutableSetOf<String>()

    private val pathPixelSourceHint = mutableMapOf<String, String>()
    private val imageSourceHint = mutableMapOf<String, String>()
    private val imageUrlByKey = mutableMapOf<String, String>()
    private var logCount = 0
    private val maxLogCount = 200

    private var resourceManager: TJLabsResourceManager? = null
    // key 는 Region × Env 조합으로 매 요청마다 조회 (라디오 변경 시 즉시 반영).
    // BuildConfig 필드는 아래 authKeyPairForSelection() 참조.
    private lateinit var clientKey: String
    // 마지막으로 auth 성공한 (provider, region, env) 조합. 조합이 바뀌면 재-auth 필요.
    private var lastAuthedScope: Triple<String, String, AuthServerEnv>? = null
    private val sectorId = 20 // covensia : 20 // tips : 1

    // verbose OFF 이면 아래 whitelist 이벤트만 UI callback log 에 표시. 나머지 (개별 데이터
    // 콜백 - onScaleOffsetData, onPathPixelData 등) 은 logcat 만 흘려보내 UI 노이즈 감소.
    private var verboseCallbackLog = false
    private val criticalCallbackEvents = setOf(
        "auth",
        "loadJupiterResource", "loadVenusResource", "loadWarpResource", "loadSimulationData",
        "onSectorData", "onSectorError",
        "onWarpSectorData", "onWarpError",
        "onVenusSectorData", "onVenusError",
        "onSimulationData",
        "onError",
        "benchmark",
        "clearCache",
    )

    /**
     * secret 은 노출을 최소화하되, 다른 pair 와 구분 가능한 만큼 (앞 4자 + 뒤 4자) 만 표시.
     * accessKey 는 identifier 성격이라 별도 마스킹 없이 로그.
     */
    private fun maskSecret(secret: String): String {
        if (secret.isBlank()) return "(blank)"
        if (secret.length <= 8) return "***"
        return "${secret.take(4)}...${secret.takeLast(4)} (len=${secret.length})"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        clientKey = BuildConfig.AUTH_CLIENT_SECRET
        authStatusText = findViewById(R.id.textAuthStatus)
        jupiterStatusText = findViewById(R.id.textJupiterStatus)
        jupiterScopeText = findViewById(R.id.textJupiterScope)
        jupiterDetailText = findViewById(R.id.textJupiterDetail)
        venusStatusText = findViewById(R.id.textVenusStatus)
        venusScopeText = findViewById(R.id.textVenusScope)
        venusDetailText = findViewById(R.id.textVenusDetail)
        warpStatusText = findViewById(R.id.textWarpStatus)
        warpScopeText = findViewById(R.id.textWarpScope)
        warpDetailText = findViewById(R.id.textWarpDetail)
        simulationStatusText = findViewById(R.id.textSimulationStatus)
        simulationScopeText = findViewById(R.id.textSimulationScope)
        simulationDetailText = findViewById(R.id.textSimulationDetail)
        callbackContainer = findViewById(R.id.callbackContainer)
        toggleVerboseButton = findViewById(R.id.buttonToggleVerbose)
        clearCallbackLogButton = findViewById(R.id.buttonClearCallbackLog)
        providerGroup = findViewById(R.id.radioGroupProvider)
        regionGroup = findViewById(R.id.radioGroupRegion)
        envGroup = findViewById(R.id.radioGroupEnv)
        currentEnvText = findViewById(R.id.textCurrentEnv)
        val refreshEnvLabel = {
            val env = getSelectedEnv()
            val region = getSelectedRegion()
            val suffix = if (env == AuthServerEnv.PROD) ".jupiter.tjlabscorp.com" else ".jupiter.tjlabs.dev"
            val hasKey = authKeyPairForSelection().let { it.first.isNotBlank() && it.second.isNotBlank() }
            val keyStatus = if (hasKey) "keys OK" else "keys MISSING"
            currentEnvText.text = "Selected : $region / $env  (OLYMPUS suffix : $suffix, $keyStatus)"
        }
        refreshEnvLabel()
        val onScopeChanged = {
            refreshEnvLabel()
            // (provider, region, env) 중 하나라도 바뀌면 이전 auth 토큰은 유효하지 않으므로
            // 캐시 초기화 → 다음 호출에서 다시 auth
            authenticatedProviders.clear()
            lastAuthedScope = null
        }
        envGroup.setOnCheckedChangeListener { _, _ -> onScopeChanged() }
        regionGroup.setOnCheckedChangeListener { _, _ -> onScopeChanged() }
        providerGroup.setOnCheckedChangeListener { _, _ -> onScopeChanged() }
        runAllButton = findViewById(R.id.buttonRunAll)
        testJupiterBundleButton = findViewById(R.id.buttonTestJupiterBundle)
        testVenusBundleButton = findViewById(R.id.buttonTestVenusBundle)
        testWardBundleButton = findViewById(R.id.buttonTestWardBundle)
        loadSimulationDataButton = findViewById(R.id.buttonLoadSimulationData)
        clearCacheButton = findViewById(R.id.buttonClearCache)
        benchmarkJupiterButton = findViewById(R.id.buttonBenchmarkJupiter)
        benchmarkResultText = findViewById(R.id.textBenchmarkResult)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        if (clientKey.isBlank()) {
            val reason = "AUTH_CLIENT_SECRET is empty. Check local.properties."
            Log.e("CheckToken", reason)
            runOnUiThread {
                authStatusText.text = reason
                authStatusText.setTextColor(getColor(R.color.text_fail))
            }
            return
        }

        val manager = TJLabsResourceManager()
        resourceManager = manager
        manager.delegate = this
        manager.warpDelegate = this
        manager.venusDelegate = this
        manager.simulationDelegate = this
        manager.setDebugOption(true)

        runAllButton.setOnClickListener {
            runAllResources(manager, getSelectedProvider(), sectorId)
        }
        testJupiterBundleButton.setOnClickListener {
            runJupiterBundleTest(manager, getSelectedProvider(), sectorId)
        }
        testVenusBundleButton.setOnClickListener {
            runVenusBundleTest(manager, getSelectedProvider(), sectorId)
        }
        testWardBundleButton.setOnClickListener {
            runWardBundleTest(manager, getSelectedProvider(), sectorId)
        }
        loadSimulationDataButton.setOnClickListener {
            runSimulationDataLoad(manager, getSelectedProvider(), sectorId)
        }
        clearCacheButton.setOnClickListener {
            manager.clearCache(application, sectorId)
            appendCallbackLog("clearCache", "sectorId=$sectorId", "api")
            updateBenchmarkResult("cache cleared • ${nowText()}")
        }
        benchmarkJupiterButton.setOnClickListener {
            runJupiterColdWarmBenchmark(manager, getSelectedProvider(), sectorId)
        }
        toggleVerboseButton.setOnClickListener {
            verboseCallbackLog = !verboseCallbackLog
            toggleVerboseButton.text = if (verboseCallbackLog) "Hide verbose" else "Show verbose"
        }
        clearCallbackLogButton.setOnClickListener {
            callbackContainer.removeAllViews()
            logCount = 0
        }
    }

    private fun runJupiterColdWarmBenchmark(
        manager: TJLabsResourceManager,
        provider: String,
        sectorId: Int
    ) {
        updateBenchmarkResult("benchmark: starting • ${nowText()}")
        appendCallbackLog("benchmark", "start provider=$provider sectorId=$sectorId", "api")

        // 1. 캐시 초기화
        manager.clearCache(application, sectorId)
        appendCallbackLog("benchmark", "cache cleared", "api")

        // 2. Auth 시간 측정
        val authStart = System.currentTimeMillis()
        authenticate(provider) { authSuccess ->
            val authElapsed = System.currentTimeMillis() - authStart
            Log.d("BENCH", "auth elapsedMs=$authElapsed success=$authSuccess provider=$provider")
            appendCallbackLog("benchmark", "auth elapsed=${authElapsed}ms success=$authSuccess", "api")
            if (!authSuccess) {
                updateBenchmarkResult("benchmark: auth failed (${authElapsed}ms) • ${nowText()}")
                return@authenticate
            }

            // 3. Cold load
            val coldStart = System.currentTimeMillis()
            updateJupiterStatus(currentScopeLabel(provider), "cold sectorId=$sectorId • ${nowText()}", null)
            manager.loadJupiterResource(
                application = application,
                provider = provider,
                region = getSelectedRegion(),
                sectorId = sectorId,
                env = getSelectedResourceEnv(),
            ) { coldSuccess ->
                val coldElapsed = System.currentTimeMillis() - coldStart

                // 4. Warm load (캐시 적중 기대)
                val warmStart = System.currentTimeMillis()
                manager.loadJupiterResource(
                    application = application,
                    provider = provider,
                    region = getSelectedRegion(),
                    sectorId = sectorId
                ) { warmSuccess ->
                    val warmElapsed = System.currentTimeMillis() - warmStart
                    val saved = (coldElapsed - warmElapsed).coerceAtLeast(0)
                    val pct = if (coldElapsed > 0) 100.0 * saved / coldElapsed else 0.0
                    Log.i(
                        "TJLabsResource_BENCH",
                        "[$provider/$sectorId] auth=%4dms | cold=%4dms warm=%4dms saved=%4dms (%.1f%%↓) | ok=%s/%s"
                            .format(authElapsed, coldElapsed, warmElapsed, saved, pct, coldSuccess, warmSuccess)
                    )
                    appendCallbackLog(
                        "benchmark",
                        "auth=${authElapsed} cold=${coldElapsed} warm=${warmElapsed} saved=${saved}",
                        "api"
                    )
                    updateBenchmarkResult(
                        "Jupiter[$provider/$sectorId] auth=${authElapsed}ms cold=${coldElapsed}ms warm=${warmElapsed}ms saved=${saved}ms (${"%.1f".format(pct)}%↓) • ${nowText()}"
                    )
                    updateJupiterStatus(
                        currentScopeLabel(provider),
                        "cold+warm ${if (warmSuccess) "OK" else "FAIL"} • auth=${authElapsed}ms cold=${coldElapsed}ms warm=${warmElapsed}ms • ${nowText()}",
                        warmSuccess
                    )
                }
            }
        }
    }

    private fun updateBenchmarkResult(text: String) {
        runOnUiThread { benchmarkResultText.text = text }
    }

    /**
     * 통합 테스트: 매 클릭마다 auth 를 새로 발동한 뒤 Jupiter · Venus · Warp · Simulation 4개
     * 리소스를 병렬 호출. 카드 4개가 동시에 LOADING → 각자 콜백 시점에 SUCCESS / FAILED 로
     * 갱신되어 현재 라디오 선택 (env × region) 조합의 결과를 한눈에 비교.
     *
     * auth 정책: `runAllResources` 진입 시 in-mem 캐시 (`authenticatedProviders`,
     * `lastAuthedScope`) 를 초기화하고 `authenticate(forceFresh = true)` 로 호출 → Phase 1
     * (`getAccessToken` 프로브) 도 우회하고 곧장 `TJLabsAuthManager.auth()` 네트워크 발동.
     * 이후 각 runX 가 재호출하는 `authenticate` 는 forceFresh=false 라 첫 성공 이후 in-mem
     * 캐시 hit 로 조용히 통과 — 즉 Run All 1회 = auth 네트워크 1회.
     */
    private fun runAllResources(manager: TJLabsResourceManager, provider: String, sectorId: Int) {
        val scope = currentScopeLabel(provider)
        appendCallbackLog("benchmark", "runAll start scope=$scope sectorId=$sectorId (forceFresh auth)", "api")
        // 4개 카드를 즉시 LOADING 으로 세팅해 사용자가 이번 클릭이 어느 scope 를 대상으로 하는지
        // 시각적으로 즉시 반영. 이후 각 runX 가 성공/실패에 따라 개별 갱신.
        updateJupiterStatus(scope, "queued • ${nowText()}", null)
        updateVenusStatus(scope, "queued • ${nowText()}", null)
        updateWarpStatus(scope, "queued • ${nowText()}", null)
        updateSimulationStatus(scope, "queued • ${nowText()}", null)

        // Run All 은 "auth 부터 새로" 를 원칙으로 한다 — in-mem 캐시 초기화 후 forceFresh auth.
        authenticatedProviders.clear()
        lastAuthedScope = null
        authenticate(provider, forceFresh = true) { authSuccess ->
            if (!authSuccess) {
                val failDetail = "auth failed • ${nowText()}"
                updateJupiterStatus(scope, failDetail, false)
                updateVenusStatus(scope, failDetail, false)
                updateWarpStatus(scope, failDetail, false)
                updateSimulationStatus(scope, failDetail, false)
                appendCallbackLog("benchmark", "runAll aborted (auth failed) scope=$scope", "api")
                return@authenticate
            }
            appendCallbackLog("benchmark", "runAll auth ok — dispatching 4 loads scope=$scope", "api")
            // 이 시점 이후 lastAuthedScope 가 세팅돼 있으므로 각 runX 의 내부 authenticate 는
            // in-mem cache hit 로 즉시 통과 (실 네트워크 auth 재발동 없음).
            runJupiterBundleTest(manager, provider, sectorId)
            runVenusBundleTest(manager, provider, sectorId)
            runWardBundleTest(manager, provider, sectorId)
            runSimulationDataLoad(manager, provider, sectorId)
        }
    }

    private fun runJupiterBundleTest(manager: TJLabsResourceManager, provider: String, sectorId: Int) {
        jupiterLoadStartMs = System.currentTimeMillis()
        val scope = currentScopeLabel(provider)
        updateJupiterStatus(scope, "sectorId=$sectorId • ${nowText()}", null)
        appendCallbackLog("loadJupiterResource", "start sectorId=$sectorId scope=$scope", "api")
        authenticate(provider) { authSuccess ->
            if (!authSuccess) {
                updateJupiterStatus(scope, "auth failed • ${nowText()}", false)
                appendCallbackLog("loadJupiterResource", "auth failed scope=$scope", "api")
                return@authenticate
            }
            val loadCallStartMs = System.currentTimeMillis()
            val authElapsed = loadCallStartMs - jupiterLoadStartMs
            manager.loadJupiterResource(
                application = application,
                provider = provider,
                region = getSelectedRegion(),
                sectorId = sectorId,
                env = getSelectedResourceEnv(),
            ) { isSuccess ->
                val totalElapsed = System.currentTimeMillis() - jupiterLoadStartMs
                val sdkElapsed = System.currentTimeMillis() - loadCallStartMs
                Log.i(
                    "TJLabsResource_BENCH",
                    "[$provider/$sectorId] total=%4dms = auth %4dms + sdk %4dms | ok=%s"
                        .format(totalElapsed, authElapsed, sdkElapsed, isSuccess)
                )
                appendCallbackLog(
                    "loadJupiterResource",
                    "ok=$isSuccess total=${totalElapsed}ms (auth=${authElapsed} sdk=${sdkElapsed}) scope=$scope",
                    "api"
                )
                updateJupiterStatus(
                    scope,
                    "sectorId=$sectorId • total=${totalElapsed}ms (auth=${authElapsed} sdk=${sdkElapsed}) • ${nowText()}",
                    isSuccess
                )
            }
        }
    }

    private fun runVenusBundleTest(manager: TJLabsResourceManager, provider: String, sectorId: Int) {
        val scope = currentScopeLabel(provider)
        updateVenusStatus(scope, "sectorId=$sectorId • ${nowText()}", null)
        appendCallbackLog("loadVenusResource", "start sectorId=$sectorId scope=$scope", "api")
        authenticate(provider) { authSuccess ->
            if (!authSuccess) {
                updateVenusStatus(scope, "auth failed • ${nowText()}", false)
                appendCallbackLog("loadVenusResource", "auth failed scope=$scope", "api")
                return@authenticate
            }
            manager.loadVenusResource(
                application = application,
                provider = provider,
                region = getSelectedRegion(),
                sectorId = sectorId,
                env = getSelectedResourceEnv(),
            ) { isSuccess ->
                appendCallbackLog(
                    "loadVenusResource",
                    "success=$isSuccess sectorId=$sectorId scope=$scope",
                    "api"
                )
                updateVenusStatus(
                    scope,
                    "sectorId=$sectorId • ${nowText()}",
                    isSuccess
                )
            }
        }
    }

    private fun runWardBundleTest(manager: TJLabsResourceManager, provider: String, sectorId: Int) {
        val scope = currentScopeLabel(provider)
        updateWarpStatus(scope, "sectorId=$sectorId • ${nowText()}", null)
        appendCallbackLog("loadWarpResource", "start sectorId=$sectorId scope=$scope", "api")
        authenticate(provider) { authSuccess ->
            if (!authSuccess) {
                updateWarpStatus(scope, "auth failed • ${nowText()}", false)
                appendCallbackLog("loadWarpResource", "auth failed scope=$scope", "api")
                return@authenticate
            }
            manager.loadWarpResource(
                application = application,
                provider = provider,
                region = getSelectedRegion(),
                sectorId = sectorId
            ) { isSuccess ->
                appendCallbackLog(
                    "loadWarpResource",
                    "success=$isSuccess sectorId=$sectorId scope=$scope",
                    "api"
                )
                updateWarpStatus(
                    scope,
                    "sectorId=$sectorId • ${nowText()}",
                    isSuccess
                )
            }
        }
    }

    private fun runSimulationDataLoad(manager: TJLabsResourceManager, provider: String, sectorId: Int) {
        val scope = currentScopeLabel(provider)
        updateSimulationStatus(scope, "sectorId=$sectorId • ${nowText()}", null)
        appendCallbackLog("loadSimulationData", "start sectorId=$sectorId scope=$scope", "api")
        authenticate(provider) { authSuccess ->
            if (!authSuccess) {
                updateSimulationStatus(scope, "auth failed • ${nowText()}", false)
                appendCallbackLog("loadSimulationData", "auth failed scope=$scope", "api")
                return@authenticate
            }
            manager.loadSimulationData(
                application = application,
                provider = provider,
                region = getSelectedRegion(),
                sectorId = sectorId,
                env = getSelectedResourceEnv(),
            ) { isSuccess ->
                appendCallbackLog(
                    "loadSimulationData",
                    "success=$isSuccess sectorId=$sectorId scope=$scope",
                    "api"
                )
                updateSimulationStatus(
                    scope,
                    "sectorId=$sectorId • ${nowText()}",
                    isSuccess
                )
            }
        }
    }

    private fun nowText(): String {
        val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return formatter.format(Date())
    }

    /**
     * "Loading / Success / Failed" 세 상태를 크게 표시. scope 라인은 실행 당시 (provider,
     * region, env) 를 별도 라인으로 유지해 결과가 어느 env 를 대상으로 성공/실패했는지
     * 명확하게 볼 수 있다.
     */
    private data class ResourceCard(
        val statusText: TextView,
        val scopeText: TextView,
        val detailText: TextView,
    )

    private fun statusLabel(success: Boolean?): String = when (success) {
        true -> "SUCCESS"
        false -> "FAILED"
        null -> "LOADING"
    }

    private fun statusColorRes(success: Boolean?): Int = when (success) {
        true -> R.color.text_success
        false -> R.color.text_fail
        null -> R.color.text_pending
    }

    private fun updateCard(card: ResourceCard, scope: String, detail: String, success: Boolean?) {
        runOnUiThread {
            card.statusText.text = statusLabel(success)
            card.statusText.setTextColor(getColor(statusColorRes(success)))
            card.scopeText.text = "scope: $scope"
            card.detailText.text = detail
        }
    }

    /** delegate 콜백 시점에는 이미 이전 load 호출이 scope 를 세팅해뒀으므로 scope 는 유지. */
    private fun updateCardStatusOnly(card: ResourceCard, detail: String, success: Boolean?) {
        runOnUiThread {
            card.statusText.text = statusLabel(success)
            card.statusText.setTextColor(getColor(statusColorRes(success)))
            card.detailText.text = detail
        }
    }

    private fun currentScopeLabel(provider: String): String =
        "$provider / ${getSelectedRegion()} / ${getSelectedEnv()}"

    private val jupiterCard: ResourceCard
        get() = ResourceCard(jupiterStatusText, jupiterScopeText, jupiterDetailText)
    private val venusCard: ResourceCard
        get() = ResourceCard(venusStatusText, venusScopeText, venusDetailText)
    private val warpCard: ResourceCard
        get() = ResourceCard(warpStatusText, warpScopeText, warpDetailText)
    private val simulationCard: ResourceCard
        get() = ResourceCard(simulationStatusText, simulationScopeText, simulationDetailText)

    private fun updateJupiterStatus(scope: String, detail: String, success: Boolean?) {
        updateCard(jupiterCard, scope, detail, success)
    }

    private fun updateVenusStatus(scope: String, detail: String, success: Boolean?) {
        updateCard(venusCard, scope, detail, success)
    }

    private fun updateWarpStatus(scope: String, detail: String, success: Boolean?) {
        updateCard(warpCard, scope, detail, success)
    }

    private fun updateSimulationStatus(scope: String, detail: String, success: Boolean?) {
        updateCard(simulationCard, scope, detail, success)
    }

    private fun getSelectedProvider(): String {
        return when (providerGroup.checkedRadioButtonId) {
            R.id.radioGcp -> ServerProvider.GCP.value
            else -> ServerProvider.AWS.value
        }
    }

    /**
     * UI region RadioGroup 값 → Resource SDK region.
     */
    private fun getSelectedRegion(): String {
        return when (regionGroup.checkedRadioButtonId) {
            R.id.radioRegionSaudi -> ResourceRegion.SAUDI.value
            else -> ResourceRegion.KOREA.value
        }
    }

    /**
     * UI env RadioGroup 값 → Auth SDK env.
     * Resource SDK 의 [ResourceServerEnv] 는 같은 시각의 동일 선택으로 매핑된다.
     */
    private fun getSelectedEnv(): AuthServerEnv {
        return when (envGroup.checkedRadioButtonId) {
            R.id.radioEnvDev -> AuthServerEnv.DEV_TESTING_ONLY
            else -> AuthServerEnv.PROD
        }
    }

    private fun getSelectedResourceEnv(): ResourceServerEnv {
        return when (getSelectedEnv()) {
            AuthServerEnv.PROD -> ResourceServerEnv.PROD
            AuthServerEnv.DEV_TESTING_ONLY -> ResourceServerEnv.DEV_TESTING_ONLY
        }
    }

    /**
     * 현재 선택된 (region, env) 조합에 매칭되는 access key / secret pair. local.properties
     * 에 등록된 4쌍 중 하나. 빈 값이면 초기 초기화 로그로 안내되고 auth 는 실패로 이어진다.
     */
    private fun authKeyPairForSelection(): Pair<String, String> {
        val region = getSelectedRegion()
        val env = getSelectedEnv()
        val (accessKey, secretKey) = when (region) {
            ResourceRegion.KOREA.value -> when (env) {
                AuthServerEnv.PROD -> BuildConfig.AUTH_ACCESS_KEY_KOREA_PROD to BuildConfig.AUTH_SECRET_ACCESS_KEY_KOREA_PROD
                AuthServerEnv.DEV_TESTING_ONLY -> BuildConfig.AUTH_ACCESS_KEY_KOREA_DEV to BuildConfig.AUTH_SECRET_ACCESS_KEY_KOREA_DEV
            }
            ResourceRegion.SAUDI.value -> when (env) {
                AuthServerEnv.PROD -> BuildConfig.AUTH_ACCESS_KEY_SAUDI_PROD to BuildConfig.AUTH_SECRET_ACCESS_KEY_SAUDI_PROD
                AuthServerEnv.DEV_TESTING_ONLY -> BuildConfig.AUTH_ACCESS_KEY_SAUDI_DEV to BuildConfig.AUTH_SECRET_ACCESS_KEY_SAUDI_DEV
            }
            else -> "" to ""
        }
        return accessKey.trim() to secretKey.trim()
    }

    /**
     * @param forceFresh true 면 in-mem 캐시 · 디스크 토큰 (Phase 1) 을 모두 우회하고
     *   `TJLabsAuthManager.auth()` 네트워크 호출을 강제. Run All 처럼 "매번 처음부터" 를
     *   원하는 진입점에서 사용.
     */
    private fun authenticate(
        provider: String,
        forceFresh: Boolean = false,
        completion: (Boolean) -> Unit,
    ) {
        val authPhaseStart = System.currentTimeMillis()
        val region = getSelectedRegion()
        val env = getSelectedEnv()
        val scope = Triple(provider, region, env)
        val scopeLabel = "$provider/$region/$env"

        // 현재 scope 로 실제 사용될 (또는 캐시 히트 시 사용됐던) auth key pair 를 항상 로깅해
        // "지금 auth 가 어느 access key 로 진행되는지" UI · logcat 에서 즉시 확인 가능.
        val (accessKey, secretKey) = authKeyPairForSelection()
        val accessKeyForLog = if (accessKey.isBlank()) "(blank)" else accessKey
        val secretKeyForLog = maskSecret(secretKey)
        Log.i(
            "TJLabsResource_AUTH",
            "[$scopeLabel] auth key selected (forceFresh=$forceFresh) // accessKey=$accessKeyForLog // secretKey=$secretKeyForLog"
        )
        appendCallbackLog(
            "auth",
            "scope=$scopeLabel forceFresh=$forceFresh accessKey=$accessKeyForLog secretKey=$secretKeyForLog",
            "api"
        )

        // forceFresh=true 면 in-mem 캐시 검사 우회.
        if (!forceFresh && lastAuthedScope == scope && provider in authenticatedProviders) {
            val mem = System.currentTimeMillis() - authPhaseStart
            Log.i("TJLabsResource_AUTH", "[$scopeLabel] in-mem cache hit (${mem}ms)")
            runOnUiThread {
                authStatusText.text = "Auth($scopeLabel): Success (in-mem cached)"
                authStatusText.setTextColor(getColor(R.color.text_success))
            }
            completion(true)
            return
        }

        if (accessKey.isBlank() || secretKey.isBlank()) {
            val reason = "Auth keys missing for $scopeLabel. Check local.properties: AUTH_ACCESS_KEY_${region.uppercase()}_${env.name.substringBefore("_")}"
            Log.e("CheckToken", reason)
            runOnUiThread {
                authStatusText.text = reason
                authStatusText.setTextColor(getColor(R.color.text_fail))
            }
            completion(false)
            return
        }

        TJLabsAuthManager.setServerURL(
            provider = provider,
            region = region,
            env = env,
        )
        TJLabsAuthManager.setLogEnabled(true)
        TJLabsAuthManager.setClientSecret(application, clientKey)

        if (forceFresh) {
            // Phase 1 (getAccessToken 프로브) 우회하고 곧장 Phase 2 네트워크 auth 로 진입.
            Log.i("TJLabsResource_AUTH", "[$scopeLabel] forceFresh=true → skipping probe, going directly to auth()")
            performNetworkAuth(
                provider = provider,
                scope = scope,
                scopeLabel = scopeLabel,
                accessKey = accessKey,
                secretKey = secretKey,
                accessKeyForLog = accessKeyForLog,
                secretKeyForLog = secretKeyForLog,
                authPhaseStart = authPhaseStart,
                probeMs = 0L,
                completion = completion,
            )
            return
        }

        // Phase 1) 디스크에 영속된 토큰 lookup (=getAccessToken). 네트워크 없이 끝나면 ms 단위.
        val probeStart = System.currentTimeMillis()
        TJLabsAuthManager.getAccessToken { tokenResult ->
            val probeMs = System.currentTimeMillis() - probeStart
            when (tokenResult) {
                is TokenResult.Success -> {
                    val total = System.currentTimeMillis() - authPhaseStart
                    Log.i(
                        "TJLabsResource_AUTH",
                        "[$scopeLabel] probe=%4dms → token reused, auth() SKIPPED | total=%4dms"
                            .format(probeMs, total)
                    )
                    authenticatedProviders.add(provider)
                    lastAuthedScope = scope
                    runOnUiThread {
                        authStatusText.text = "Auth($scopeLabel): Success (token reused, ${probeMs}ms)"
                        authStatusText.setTextColor(getColor(R.color.text_success))
                    }
                    completion(true)
                }
                is TokenResult.Failure -> {
                    performNetworkAuth(
                        provider = provider,
                        scope = scope,
                        scopeLabel = scopeLabel,
                        accessKey = accessKey,
                        secretKey = secretKey,
                        accessKeyForLog = accessKeyForLog,
                        secretKeyForLog = secretKeyForLog,
                        authPhaseStart = authPhaseStart,
                        probeMs = probeMs,
                        completion = completion,
                    )
                }
            }
        }
    }

    /**
     * Phase 2: `TJLabsAuthManager.auth(...)` 강제 발동. probeMs 는 이번 호출에서 Phase 1 을
     * 수행했다면 그 소요시간, 스킵했다면 0 을 넘긴다 — 로그에 그대로 반영.
     */
    private fun performNetworkAuth(
        provider: String,
        scope: Triple<String, String, AuthServerEnv>,
        scopeLabel: String,
        accessKey: String,
        secretKey: String,
        accessKeyForLog: String,
        secretKeyForLog: String,
        authPhaseStart: Long,
        probeMs: Long,
        completion: (Boolean) -> Unit,
    ) {
        val authNetStart = System.currentTimeMillis()
        Log.i(
            "TJLabsResource_AUTH",
            "[$scopeLabel] full auth() network call // accessKey=$accessKeyForLog // secretKey=$secretKeyForLog"
        )
        appendCallbackLog(
            "auth",
            "network request accessKey=$accessKeyForLog secretKey=$secretKeyForLog scope=$scopeLabel",
            "api"
        )
        TJLabsAuthManager.auth(accessKey, secretKey) { code, success ->
            val authNetMs = System.currentTimeMillis() - authNetStart
            val total = System.currentTimeMillis() - authPhaseStart
            Log.i(
                "TJLabsResource_AUTH",
                "[$scopeLabel] probe=%4dms + auth()=%4dms | total=%4dms ok=%s code=%s"
                    .format(probeMs, authNetMs, total, success, code)
            )
            if (success) {
                authenticatedProviders.add(provider)
                lastAuthedScope = scope
            }
            runOnUiThread {
                authStatusText.text = if (success)
                    "Auth($scopeLabel): Success (probe=${probeMs} + auth=${authNetMs}ms)"
                else
                    "Auth($scopeLabel): Failed (code: $code)"
                authStatusText.setTextColor(
                    getColor(if (success) R.color.text_success else R.color.text_fail)
                )
            }
            completion(success)
        }
    }

    override fun onSectorData(data: SectorOutput) {
        TJResourceLogger.d("onSectorData : $data")
        populateSourceHints(data)
        appendCallbackLog("onSectorData", "sectorId=${data.id} buildings=${data.buildings.size}", "api")
        updateCardStatusOnly(jupiterCard, "sectorId=${data.id} buildings=${data.buildings.size} • ${nowText()}", true)
    }

    override fun onSectorError(error: ResourceError) {
        TJResourceLogger.d("onSectorError : $error")
        appendCallbackLog("onSectorError", "error=$error", "api")
        updateCardStatusOnly(jupiterCard, "error=$error • ${nowText()}", false)
    }

    override fun onBuildingsData(data: List<BuildingOutput>) {
        TJResourceLogger.d("onBuildingsData : $data")
        appendCallbackLog("onBuildingsData", "count=${data.size}", "api")
    }

    override fun onLevelWardsData(levelKey: String, data: List<String>) {
        TJResourceLogger.d("onLevelWardsData : $levelKey // data : $data")
        appendCallbackLog("onLevelWardsData", "key=$levelKey wards=${data.size}", "api")
    }

    override fun onScaleOffsetData(scaleKey: String, data: List<Float>) {
        TJResourceLogger.d("onScaleOffsetData : $scaleKey // data : $data")
        appendCallbackLog("onScaleOffsetData", "key=$scaleKey size=${data.size}", "api")
    }

    override fun onPathPixelData(pathPixelKey: String, data: PathPixelData) {
        TJResourceLogger.d("onPathPixelData : $pathPixelKey // data : ${data.road}")
        TJResourceLogger.d("onPathPixelData : $pathPixelKey // data : ${data.roadScale}")
        TJResourceLogger.d("onPathPixelData : $pathPixelKey // data : ${data.roadHeading}")

        val source = pathPixelSourceHint[pathPixelKey] ?: "api"
        appendCallbackLog(
            "onPathPixelData",
            "key=$pathPixelKey nodes=${data.road.size} roadPts=${data.road.firstOrNull()?.size ?: 0}",
            source
        )
    }

    override fun onGeofenceData(geofenceKey: String, data: GeofenceData) {
        TJResourceLogger.d("onGeofenceData : $geofenceKey // data : $data")
        appendCallbackLog("onGeofenceData", "key=$geofenceKey", "api")
    }

    override fun onEntranceData(entranceKey: String, data: EntranceData) {
        TJResourceLogger.d("onEntranceData : $entranceKey // data : $data")
        appendCallbackLog(
            "onEntranceData",
            "key=$entranceKey number=${data.number}",
            "api"
        )
    }

    override fun onEntranceRouteData(entranceKey: String, data: EntranceRouteData) {
        TJResourceLogger.d("onEntranceRouteData : $entranceKey // data : $data")
        appendCallbackLog(
            "onEntranceRouteData",
            "key=$entranceKey routeLevels=${data.routeLevel.size}",
            "api"
        )
    }

    override fun onSectorParamData(data: SectorParameterOutput) {
        TJResourceLogger.d("onSectorParamData data $data")
        appendCallbackLog("onSectorParamData", "min=${data.standard_min_rssi} max=${data.standard_max_rssi}", "api")
    }

    override fun onLevelParamData(paramKey: String, data: LevelParameterOutput) {
        TJResourceLogger.d("onLevelParamData type $paramKey // $data")
        appendCallbackLog("onLevelParamData", "key=$paramKey", "api")
    }

    override fun onBuildingLevelImageData(imageKey: String, data: Bitmap?) {
        TJResourceLogger.d("onBuildingLevelImageData imageKey $imageKey // $data")
        val source = imageSourceHint[imageKey] ?: "api"
        val url = imageUrlByKey[imageKey] ?: "unknown"
        val size = if (data != null) "${data.width}x${data.height}" else "null"
        appendCallbackLog(
            "onBuildingLevelImageData",
            "key=$imageKey size=$size url=$url",
            source
        )
    }

    override fun onLevelUnitsData(unitKey: String, data: List<UnitData>?) {
        if (data != null) {
            for (info in data) {
                TJResourceLogger.d("onUnitData unitKey $unitKey // data : $info")
            }
        }

        appendCallbackLog(
            "onLevelUnitsData",
            "key=$unitKey count=${data?.size ?: 0}",
            "api"
        )
    }

    override fun onAffineData(sectorId: Int, data: AffineTransParamOutput) {
        TJResourceLogger.d("onAffineData sectorId $sectorId // data : $data")
        appendCallbackLog("onAffineData", "sectorId=$sectorId", "api")
    }

    override fun onLandmarkData(key: String, data: Map<String, LandmarkData>) {
        TJResourceLogger.d("onLandmarkData key $key // data : $data")
        appendCallbackLog("onLandmarkData", "key=$key count=${data.size}", "asset")
    }

    override fun onSpotsData(key: Int, type: SpotType, data: Any) {
        TJResourceLogger.d("onSpotsData key $key //type : $type // data : $data")
        appendCallbackLog(
            "onSpotsData",
            "key=$key type=$type count=${describeSize(data)}",
            "asset"
        )
    }

    override fun onNodeLinkData(key: String, type: NodeLinkType, data: Any) {
        TJResourceLogger.d("onNodeLinkData key $key // type : $type // data : $data")
        appendCallbackLog(
            "onNodeLinkData",
            "key=$key type=$type count=${describeSize(data)}",
            "asset"
        )
    }

    override fun onError(error: ResourceError, key: String) {
        TJResourceLogger.d("onError : $error // key : $key")
        appendCallbackLog("onError", "error=$error key=$key", "api")
    }

    override fun onWarpSectorData(data: WarpSectorOutput) {
        val buildings = data.buildings.size
        val levels = data.buildings.sumOf { it.levels.size }
        val wards = data.buildings.sumOf { building -> building.levels.sumOf { it.wards.size } }
        appendCallbackLog(
            "onWarpSectorData",
            "sectorId=${data.id} buildings=$buildings levels=$levels wards=$wards os=${data.operating_system}",
            "api"
        )
        updateCardStatusOnly(warpCard, "sectorId=${data.id} buildings=$buildings wards=$wards • ${nowText()}", true)
    }

    override fun onWarpError(error: ResourceError) {
        appendCallbackLog("onWarpError", "error=$error", "api")
        updateCardStatusOnly(warpCard, "error=$error • ${nowText()}", false)
    }

    override fun onVenusSectorData(data: VenusSectorOutput) {
        val buildings = data.buildings.size
        val levels = data.buildings.sumOf { it.levels.size }
        val wards = data.buildings.sumOf { building -> building.levels.sumOf { it.wards.size } }
        appendCallbackLog(
            "onVenusSectorData",
            "sectorId=${data.id} buildings=$buildings levels=$levels wards=$wards os=${data.operating_system}",
            "api"
        )
        updateCardStatusOnly(venusCard, "sectorId=${data.id} buildings=$buildings wards=$wards • ${nowText()}", true)
    }

    override fun onVenusError(error: ResourceError) {
        appendCallbackLog("onVenusError", "error=$error", "api")
        updateCardStatusOnly(venusCard, "error=$error • ${nowText()}", false)
    }

    override fun onSimulationData(sectorId: Int, data: SimulationBundleOutput) {
        appendCallbackLog(
            "onSimulationData",
            "sectorId=$sectorId vehicle=${data.vehicle.size} pdr=${data.pdr.size}",
            "api"
        )
        updateCardStatusOnly(
            simulationCard,
            "sectorId=$sectorId vehicle=${data.vehicle.size} pdr=${data.pdr.size} • ${nowText()}",
            true
        )
    }

    private fun populateSourceHints(data: SectorOutput) {
        for (building in data.buildings) {
            for (level in building.levels) {
                if (level.name.contains("_D")) continue

                val key = "${data.id}_${building.name}_${level.name}"
                imageUrlByKey[key] = level.image
                imageSourceHint[key] = "bundle"
                pathPixelSourceHint[key] = "bundle"
            }
        }
    }

    private fun describeSize(data: Any): String {
        return when (data) {
            is Map<*, *> -> data.size.toString()
            is Collection<*> -> data.size.toString()
            else -> "1"
        }
    }

    private fun appendCallbackLog(event: String, detail: String, source: String) {
        val line = "[${nowText()}] $event - $detail - source=$source"
        // logcat 은 항상 전량 기록. UI 는 verbose OFF 인 경우 whitelist 만 노출해 노이즈 감소.
        Log.d("TJLabsResource_CB", line)
        val isCritical = event in criticalCallbackEvents
        if (!isCritical && !verboseCallbackLog) return
        runOnUiThread {
            val textView = TextView(this).apply {
                text = line
                textSize = 12f
                setTextColor(getColor(if (isCritical) R.color.black else R.color.text_muted))
            }
            callbackContainer.addView(textView)
            logCount += 1
            if (logCount > maxLogCount) {
                callbackContainer.removeViewAt(0)
                logCount -= 1
            }
        }
    }
}

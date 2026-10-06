package com.tjlabs.sdk_sample_app

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import java.io.File
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
    private lateinit var authOnlyButton: Button
    private lateinit var runAllButton: Button
    private lateinit var testJupiterBundleButton: Button
    private lateinit var testJupiterBundleV1Button: Button
    private lateinit var jupiterCompareText: TextView
    private lateinit var testVenusBundleButton: Button
    private lateinit var testWardBundleButton: Button
    private lateinit var loadSimulationDataButton: Button
    private lateinit var clearCacheButton: Button
    private lateinit var benchmarkJupiterButton: Button
    private lateinit var benchmarkResultText: TextView
    private lateinit var bundleReportButton: Button
    private lateinit var bundleReportText: TextView
    private lateinit var toggleVerboseButton: Button
    private lateinit var clearCallbackLogButton: Button
    // Multi-sector 로드 (TJLabsMultiResourceManager, TJ-559/580) 테스트 UI.
    // sectorIds EditText + 두 개 로드 버튼 (일반 / force) + clearCache + 결과 TextView.
    private lateinit var multiSectorIdsInput: EditText
    private lateinit var multiLoadButton: Button
    private lateinit var multiLoadForceButton: Button
    private lateinit var multiClearCacheButton: Button
    private lateinit var multiResultText: TextView
    // Sequential Load — 단일 섹터 loadJupiterResource N 번 순차 호출 (Multi vs 비교).
    private lateinit var seqSectorIdsInput: EditText
    private lateinit var runSeqLoadButton: Button
    private lateinit var seqResultText: TextView
    // Benchmark Suite — iOS 문서 §9 포맷 "legacy vs current" 비교.
    private lateinit var benchSectorIdsInput: EditText
    private lateinit var benchIterationsInput: EditText
    private lateinit var runBenchmarkButton: Button
    private lateinit var csvBenchResultText: TextView

    private var jupiterLoadStartMs: Long = 0L
    // v1 vs v2 벤치마크 결과 추적 — 각 endpoint SDK 소요시간(ms) 을 최근 5개까지 rolling window 로 저장.
    // 각 클릭이 한 샘플. 여러 번 눌러 min/median/max 로 편차를 확인하면 network jitter/CDN 웜업 노이즈를 완화.
    private val v2Samples: MutableList<Long> = mutableListOf()
    private val v1Samples: MutableList<Long> = mutableListOf()
    private val maxBenchSamples = 5

    /**
     * 버튼 클릭 자체를 [TJLabsResourceManager] 로그 태그에 남긴다. `logcat -s TJLabsResourceManager:D`
     * 로그를 볼 때 어떤 UI 액션이 어느 단계 로그를 낳았는지 즉시 상관관계를 잡을 수 있게 해준다.
     */
    private fun logButtonPress(button: String, extras: String = "") {
        val line = "[BUTTON_PRESS] $button${if (extras.isBlank()) "" else " // $extras"}"
        TJResourceLogger.i("(TJLabsResource) ================================================================")
        TJResourceLogger.i("(TJLabsResource) $line")
        TJResourceLogger.i("(TJLabsResource) ================================================================")
    }
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
    // 기본 섹터 ID — editSectorIdInput 이 비어있거나 파싱 실패 시 fallback.
    // UI 상 섹터 ID 는 editSectorIdInput EditText 값이 우선 (currentSectorId() 참고).
    private val defaultSectorId = 111 // covensia : 20 // tips : 1
    private lateinit var editSectorIdInput: android.widget.EditText

    /**
     * UI 로 입력한 섹터 ID. EditText 비어있거나 숫자가 아니면 [defaultSectorId] (111) fallback.
     * Load Jupiter Resource / Venus / Warp / Simulation / Clear Cache / Benchmark 등 모든 단일
     * 섹터 흐름이 이 함수로 매번 값을 조회 — 사용자가 로드 전 EditText 수정 즉시 반영된다.
     */
    private fun currentSectorId(): Int =
        editSectorIdInput.text?.toString()?.toIntOrNull() ?: defaultSectorId

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
        "onGeofenceData",  // 2026-09-28+ 스키마 검증에서 다각형/꼭짓점 수를 즉시 확인하려고 default 표시.
        "onTransitionData",
        "onSectorResourceLoadFinished",  // Multi 로드 섹터별 완료 콜백 — 테스트 시 로그에 바로 표시.
        "onError",
        "benchmark",
        "bundleReport",
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
        editSectorIdInput = findViewById(R.id.editSectorId)
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
        authOnlyButton = findViewById(R.id.buttonAuthOnly)
        runAllButton = findViewById(R.id.buttonRunAll)
        testJupiterBundleButton = findViewById(R.id.buttonTestJupiterBundle)
        testJupiterBundleV1Button = findViewById(R.id.buttonTestJupiterBundleV1)
        jupiterCompareText = findViewById(R.id.textJupiterCompare)
        testVenusBundleButton = findViewById(R.id.buttonTestVenusBundle)
        testWardBundleButton = findViewById(R.id.buttonTestWardBundle)
        loadSimulationDataButton = findViewById(R.id.buttonLoadSimulationData)
        clearCacheButton = findViewById(R.id.buttonClearCache)
        benchmarkJupiterButton = findViewById(R.id.buttonBenchmarkJupiter)
        benchmarkResultText = findViewById(R.id.textBenchmarkResult)
        bundleReportButton = findViewById(R.id.buttonBundleReport)
        bundleReportText = findViewById(R.id.textBundleReport)
        multiSectorIdsInput = findViewById(R.id.editMultiSectorIds)
        multiLoadButton = findViewById(R.id.buttonMultiLoad)
        multiLoadForceButton = findViewById(R.id.buttonMultiLoadForce)
        multiClearCacheButton = findViewById(R.id.buttonMultiClearCache)
        multiResultText = findViewById(R.id.textMultiResult)
        seqSectorIdsInput = findViewById(R.id.editSeqSectorIds)
        runSeqLoadButton = findViewById(R.id.buttonSeqLoad)
        seqResultText = findViewById(R.id.textSeqResult)
        benchSectorIdsInput = findViewById(R.id.editBenchSectorIds)
        benchIterationsInput = findViewById(R.id.editBenchIterations)
        runBenchmarkButton = findViewById(R.id.buttonRunBenchmark)
        csvBenchResultText = findViewById(R.id.textCsvBenchmarkResult)

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

        authOnlyButton.setOnClickListener {
            logButtonPress("Auth Only", "provider=${getSelectedProvider()} region=${getSelectedRegion()} env=${getSelectedEnv()}")
            runAuthOnly(getSelectedProvider())
        }
        runAllButton.setOnClickListener {
            val sid = currentSectorId()
            logButtonPress("Run All (Jupiter+Venus+Warp+Simulation)", "sectorId=$sid scope=${currentScopeLabel(getSelectedProvider())}")
            runAllResources(manager, getSelectedProvider(), sid)
        }
        testJupiterBundleButton.setOnClickListener {
            val sid = currentSectorId()
            logButtonPress("Load Jupiter Resource (v2 zip)", "sectorId=$sid scope=${currentScopeLabel(getSelectedProvider())}")
            runJupiterBundleTest(manager, getSelectedProvider(), sid)
        }
        testJupiterBundleV1Button.setOnClickListener {
            val sid = currentSectorId()
            logButtonPress("Load Jupiter v1 (2026-09-10 JSON)", "sectorId=$sid scope=${currentScopeLabel(getSelectedProvider())} legacy=true")
            runJupiterBundleV1Test(manager, getSelectedProvider(), sid)
        }
        testVenusBundleButton.setOnClickListener {
            val sid = currentSectorId()
            logButtonPress("Load Venus Resource", "sectorId=$sid scope=${currentScopeLabel(getSelectedProvider())}")
            runVenusBundleTest(manager, getSelectedProvider(), sid)
        }
        testWardBundleButton.setOnClickListener {
            val sid = currentSectorId()
            logButtonPress("Load Warp Resource", "sectorId=$sid scope=${currentScopeLabel(getSelectedProvider())}")
            runWardBundleTest(manager, getSelectedProvider(), sid)
        }
        loadSimulationDataButton.setOnClickListener {
            val sid = currentSectorId()
            logButtonPress("Load Simulation Data", "sectorId=$sid scope=${currentScopeLabel(getSelectedProvider())}")
            runSimulationDataLoad(manager, getSelectedProvider(), sid)
        }
        clearCacheButton.setOnClickListener {
            val sid = currentSectorId()
            logButtonPress("Clear Cache", "sectorId=$sid")
            manager.clearCache(application, sid)
            appendCallbackLog("clearCache", "sectorId=$sid", "api")
            updateBenchmarkResult("cache cleared • ${nowText()}")
            // 새 벤치 시작을 위해 rolling 샘플도 리셋.
            v1Samples.clear(); v2Samples.clear()
            updateJupiterCompare()
        }
        benchmarkJupiterButton.setOnClickListener {
            val sid = currentSectorId()
            logButtonPress("Benchmark Jupiter cold vs warm", "sectorId=$sid")
            runJupiterColdWarmBenchmark(manager, getSelectedProvider(), sid)
        }
        bundleReportButton.setOnClickListener {
            logButtonPress("Bundle Report (2026-09-28 zip)")
            runBundleReport(manager)
        }
        toggleVerboseButton.setOnClickListener {
            logButtonPress("Toggle Verbose Log")
            verboseCallbackLog = !verboseCallbackLog
            toggleVerboseButton.text = if (verboseCallbackLog) "Hide verbose" else "Show verbose"
        }
        clearCallbackLogButton.setOnClickListener {
            logButtonPress("Clear Callback Log")
            callbackContainer.removeAllViews()
            logCount = 0
        }

        multiLoadButton.setOnClickListener { runMultiLoad(forceUpdate = false) }
        multiLoadForceButton.setOnClickListener { runMultiLoad(forceUpdate = true) }
        multiClearCacheButton.setOnClickListener {
            logButtonPress("Multi Clear Cache")
            TJLabsMultiResourceManager.clearCache(application)
            multiResultText.text = "Multi cache cleared @ ${nowText()}"
            appendCallbackLog("clearCache", "multi", "api")
        }
        runBenchmarkButton.setOnClickListener { runBenchmarkSuite() }
        runSeqLoadButton.setOnClickListener { runSequentialLoad() }
    }

    /**
     * 단일 섹터 `loadJupiterResource` 를 EditText 섹터 리스트에 대해 **순차로** 호출.
     * 각 섹터는 clearCache + loadJupiterResource (forceUpdate 기본 false 지만 cache 비웠으니 cold)
     * 를 수행하고 wall-clock 측정. 전부 끝나면 각 섹터별 ms + 합산 ms + 전체 wall-clock 표시.
     *
     * **비교 사용 예**: `[111, 112, 20]` 로 Sequential Load → 합산 ms 와 Multi Load 결과의 TOTAL 비교.
     * 네트워크 jitter 영향을 줄이려면 Multi 테스트 직후 wifi 상태 그대로 Sequential 실행 추천.
     */
    private fun runSequentialLoad() {
        val raw = seqSectorIdsInput.text?.toString().orEmpty()
        val sectorIds = raw.split(",", " ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { it.toIntOrNull() }
        if (sectorIds.isEmpty()) {
            seqResultText.text = "Enter sectorIds (쉼표 구분)"
            return
        }
        val provider = getSelectedProvider()
        val region = getSelectedRegion()
        val env = getSelectedResourceEnv()
        logButtonPress("Sequential Load", "sectorIds=$sectorIds provider=$provider region=$region env=$env")
        val mgr = resourceManager ?: TJLabsResourceManager().also {
            it.delegate = this
            it.warpDelegate = this
            it.venusDelegate = this
            it.simulationDelegate = this
            resourceManager = it
        }
        seqResultText.text = "Sequential Load: authenticating..."
        authenticate(provider) { authSuccess ->
            if (!authSuccess) {
                runOnUiThread { seqResultText.text = "Sequential Load: auth failed" }
                return@authenticate
            }
            val results = mutableListOf<Triple<Int, Long, Boolean>>()  // sectorId, elapsedMs, success
            val overallStart = System.currentTimeMillis()

            // 재귀 콜백 — loadJupiterResource 는 메인 큐 콜백이라 코루틴 wrap 없이 순차 체인.
            fun loadNext(index: Int) {
                if (index >= sectorIds.size) {
                    val totalWallClock = System.currentTimeMillis() - overallStart
                    val sum = results.sumOf { it.second }
                    val sb = StringBuilder()
                    sb.append("Sequential Load 완료 @ ${nowText()}\n")
                    sb.append("섹터 수: ${sectorIds.size}  wall-clock: ${totalWallClock}ms  합산: ${sum}ms\n\n")
                    sb.append("섹터별 TOTAL:\n")
                    results.forEach { (sid, t, ok) ->
                        sb.append("  • sector=$sid  time=${t}ms  success=$ok\n")
                    }
                    sb.append("\n Multi 비교: 같은 섹터 리스트 (${sectorIds}) 로 Multi Load 실행 후 TOTAL 과 비교.")
                    runOnUiThread { seqResultText.text = sb.toString() }
                    appendCallbackLog(
                        "benchmark",
                        "sequential sectorIds=$sectorIds sum=${sum}ms wall=${totalWallClock}ms successCount=${results.count { it.third }}",
                        "api"
                    )
                    return
                }
                val sid = sectorIds[index]
                runOnUiThread { seqResultText.text = "Sequential Load: ${index + 1}/${sectorIds.size} — sector $sid in flight..." }
                // cold load 유도: 해당 섹터 캐시 완전 삭제 후 loadJupiterResource 호출.
                mgr.clearCache(application, sid)
                val sectorStart = System.currentTimeMillis()
                mgr.loadJupiterResource(
                    application = application,
                    provider = provider,
                    region = region,
                    sectorId = sid,
                    env = env,
                    imageLoadPolicy = ImageLoadPolicy.NONE,
                ) { success, _ ->
                    val elapsed = System.currentTimeMillis() - sectorStart
                    results.add(Triple(sid, elapsed, success))
                    loadNext(index + 1)
                }
            }
            loadNext(0)
        }
    }

    /**
     * iOS 문서 §9 "성능 측정" 포맷 매핑. LEGACY_REGEX 와 SINGLE_PASS 두 모드로 cold load N 회
     * 반복해 (forceUpdate=true, 매번 cache 삭제) 단계별 timing 평균/median/p95 를 뽑는다.
     *
     * 결과는 benchmarkResultText 에 monospace 표 형태로 출력. logcat 에는 iteration 당 1줄씩
     * (`(Benchmark) mode=... csvParse=...ms`) 요약도 남아 재분석 가능.
     */
    private fun runBenchmarkSuite() {
        val raw = benchSectorIdsInput.text?.toString().orEmpty()
        val sectorIds = raw.split(",", " ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { it.toIntOrNull() }
        if (sectorIds.isEmpty()) {
            csvBenchResultText.text = "Enter sectorIds (쉼표 구분)"
            return
        }
        val iterations = benchIterationsInput.text?.toString()?.toIntOrNull() ?: 10
        if (iterations <= 0) {
            csvBenchResultText.text = "iterations must be > 0"
            return
        }
        val provider = getSelectedProvider()
        val region = getSelectedRegion()
        val env = getSelectedResourceEnv()
        logButtonPress("Run Benchmark", "sectorIds=$sectorIds iterations=$iterations provider=$provider region=$region env=$env")
        val mgr = resourceManager ?: TJLabsResourceManager().also {
            it.delegate = this
            it.warpDelegate = this
            it.venusDelegate = this
            it.simulationDelegate = this
            resourceManager = it
        }
        csvBenchResultText.text = "Benchmark: authenticating..."
        // Auth 는 벤치 돌리기 전에 성공해야 함 — Multi 로드가 Bearer 토큰 필요.
        authenticate(provider) { authSuccess ->
            if (!authSuccess) {
                runOnUiThread { csvBenchResultText.text = "Benchmark: auth failed" }
                return@authenticate
            }
            csvBenchResultText.text = "Benchmark: running legacy (regex) ${iterations}x ..."
            TJLabsResourceBenchmark.runBenchmark(
                resourceManager = mgr,
                application = application,
                provider = provider,
                region = region,
                env = env,
                sectorIds = sectorIds,
                mode = TJLabsResourceBenchmark.CsvParserMode.LEGACY_REGEX,
                iterations = iterations,
                onProgress = { done, total ->
                    runOnUiThread { csvBenchResultText.text = "Benchmark: legacy progress $done/$total" }
                },
                onComplete = { legacyResult ->
                    runOnUiThread { csvBenchResultText.text = "Benchmark: running current (single-pass) ${iterations}x ..." }
                    TJLabsResourceBenchmark.runBenchmark(
                        resourceManager = mgr,
                        application = application,
                        provider = provider,
                        region = region,
                        env = env,
                        sectorIds = sectorIds,
                        mode = TJLabsResourceBenchmark.CsvParserMode.SINGLE_PASS,
                        iterations = iterations,
                        onProgress = { done, total ->
                            runOnUiThread { csvBenchResultText.text = "Benchmark: current progress $done/$total" }
                        },
                        onComplete = { currentResult ->
                            runOnUiThread {
                                csvBenchResultText.text = formatBenchmarkTable(legacyResult, currentResult)
                                appendCallbackLog(
                                    "benchmark",
                                    "legacy csv avg=${"%.1f".format(legacyResult.pathCsvAvgMs)}ms current csv avg=${"%.1f".format(currentResult.pathCsvAvgMs)}ms iterations=$iterations sectorIds=$sectorIds",
                                    "api"
                                )
                            }
                        }
                    )
                }
            )
        }
    }

    /**
     * iOS 문서 §9 포맷 테이블 생성. 모든 수치는 ms. 각 행은 `측정 단계 | 개선 전 | 개선 후` 매핑.
     * monospace 폰트 로 열 정렬.
     */
    private fun formatBenchmarkTable(
        legacy: TJLabsResourceBenchmark.SessionResult,
        current: TJLabsResourceBenchmark.SessionResult,
    ): String {
        fun pct(before: Double, after: Double): String {
            if (before <= 0.0) return "n/a"
            val ratio = (before - after) / before * 100.0
            return "%+.1f%%".format(-ratio)  // 음수 = 개선, 양수 = 악화
        }
        val sb = StringBuilder()
        sb.append("Benchmark 결과 — sectorIds=${legacy.sectorIds} iterations=${legacy.iterations}\n")
        sb.append("(각 모드로 cold load, forceUpdate=true)\n\n")
        sb.append("단계              | 개선 전 (legacy regex) | 개선 후 (single-pass) | Δ\n")
        sb.append("-----------------|-----------------------|-----------------------|------\n")
        sb.append("경로 CSV 파싱     | %18.1f ms | %18.1f ms | %s\n".format(
            legacy.pathCsvAvgMs, current.pathCsvAvgMs, pct(legacy.pathCsvAvgMs, current.pathCsvAvgMs)))
        sb.append("  (median)        | %18d ms | %18d ms |\n".format(
            legacy.pathCsvMedianMs, current.pathCsvMedianMs))
        sb.append("  (p95)           | %18d ms | %18d ms |\n".format(
            legacy.pathCsvP95Ms, current.pathCsvP95Ms))
        sb.append("bundle.json decode| %18.1f ms | %18.1f ms | %s\n".format(
            legacy.bundleDecodeAvgMs, current.bundleDecodeAvgMs, pct(legacy.bundleDecodeAvgMs, current.bundleDecodeAvgMs)))
        sb.append("압축 해제         | %18.1f ms | %18.1f ms | %s\n".format(
            legacy.zipExtractAvgMs, current.zipExtractAvgMs, pct(legacy.zipExtractAvgMs, current.zipExtractAvgMs)))
        sb.append("전체(메타 제외)   | %18.1f ms | %18.1f ms | %s\n".format(
            legacy.totalWithoutMetaAvgMs, current.totalWithoutMetaAvgMs, pct(legacy.totalWithoutMetaAvgMs, current.totalWithoutMetaAvgMs)))
        sb.append("end-to-end        | %18.1f ms | %18.1f ms | %s\n".format(
            legacy.totalEndToEndAvgMs, current.totalEndToEndAvgMs, pct(legacy.totalEndToEndAvgMs, current.totalEndToEndAvgMs)))
        sb.append("\n* Δ 는 (current - legacy) / legacy — 음수가 개선 (더 빠름).\n")
        sb.append("* end-to-end 는 네트워크 왕복 포함이라 jitter 큼. 파서 비교는 '경로 CSV 파싱' 행 참고.\n")
        return sb.toString()
    }

    /**
     * TJLabsMultiResourceManager.loadResources 테스트. 결과를 textMultiResult 에 요약하고,
     * 각 섹터의 onSectorResourceLoadFinished 콜백도 UI 로그에 흐른다 (delegate override 참고).
     *
     * **폴백 테스트 절차**:
     *   1) 네트워크 ON — Multi Load 1회 (cold, 다운로드 + 캐시 저장)
     *   2) airplane mode ON — Multi Load 1회 → "fallback" 메시지 + versionVerified=false 확인
     *   3) airplane mode OFF — Multi Load 1회 → 캐시 HIT (버전 동일) 확인
     *
     * **상위집합(superset) 폴백 테스트**:
     *   1) sectorIds=20,112,113 로 Multi Load → 조합 캐시 저장
     *   2) sectorIds=20,112 로 Multi Load (airplane mode 또는 서버 다운 시) → 상위집합 20_112_113 로 폴백
     */
    private fun runMultiLoad(forceUpdate: Boolean) {
        val raw = multiSectorIdsInput.text?.toString().orEmpty()
        val sectorIds = raw.split(",", " ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { it.toIntOrNull() }
        if (sectorIds.isEmpty()) {
            multiResultText.text = "Enter sectorIds (쉼표 구분), 예: 111 또는 20, 112, 113"
            return
        }
        val provider = getSelectedProvider()
        val region = getSelectedRegion()
        val env = getSelectedResourceEnv()
        val tag = if (forceUpdate) "Multi Load (force)" else "Multi Load"
        logButtonPress(tag, "sectorIds=$sectorIds provider=$provider region=$region env=$env")
        val mgr = resourceManager ?: TJLabsResourceManager().also {
            it.delegate = this
            it.warpDelegate = this
            it.venusDelegate = this
            it.simulationDelegate = this
            resourceManager = it
        }
        multiResultText.text = "Multi Load: authenticating..."
        // Multi 로더는 meta 요청에 Bearer 토큰이 필요한데, TJLabsAuthManager 에 key 쌍이 등록되지
        // 않으면 getAccessToken 이 401 "access_key/secret_access_key not stored" 로 즉시 실패한다.
        // 샘플 앱 다른 버튼들과 동일하게 authenticate() 로 key 를 선-등록한 뒤 로드를 진행.
        // (이미 같은 scope 로 auth 됐으면 in-memory 캐시 히트로 즉시 진행.)
        authenticate(provider) { authSuccess ->
            if (!authSuccess) {
                runOnUiThread {
                    multiResultText.text = "Multi Load: auth failed — local.properties 의 AUTH_ACCESS_KEY_${region.uppercase()}_${env.name.substringBefore("_")} 확인"
                }
                return@authenticate
            }
            multiResultText.text = "Multi Load: in flight — sectorIds=$sectorIds (forceUpdate=$forceUpdate)"
            val startMs = System.currentTimeMillis()
            TJLabsMultiResourceManager.loadResources(
                resourceManager = mgr,
                application = application,
                provider = provider,
                region = region,
                env = env,
                sectorIds = sectorIds,
                imageLoadPolicy = ImageLoadPolicy.NONE,
                forceUpdate = forceUpdate
            ) { result ->
                val elapsed = System.currentTimeMillis() - startMs
                val sb = StringBuilder()
                sb.append("Multi Load Done @ ${nowText()} — elapsedMs=$elapsed\n")
                sb.append("  success=${result.isSuccess} isCached=${result.isCached} version=${result.versionId}\n")
                sb.append("  stage=${result.failedStage ?: "-"} failures=${result.failures.size}\n")
                sb.append("  msg: ${result.message}\n")
                sb.append("sectorResults:\n")
                for (sr in result.sectorResults) {
                    sb.append("  • sector=${sr.sectorId} ok=${sr.isSuccess} stage=${sr.failedStage ?: "-"} failures=${sr.failures.size}\n")
                }
                runOnUiThread { multiResultText.text = sb.toString() }
                appendCallbackLog("loadJupiterResource",
                    "multi sectorIds=$sectorIds success=${result.isSuccess} isCached=${result.isCached} version=${result.versionId} elapsed=${elapsed}ms",
                    "api")
            }
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
            ) { coldSuccess, _ ->
                val coldElapsed = System.currentTimeMillis() - coldStart

                // 4. Warm load (캐시 적중 기대)
                val warmStart = System.currentTimeMillis()
                manager.loadJupiterResource(
                    application = application,
                    provider = provider,
                    region = getSelectedRegion(),
                    sectorId = sectorId
                ) { warmSuccess, _ ->
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

    private fun updateBundleReport(text: String) {
        runOnUiThread { bundleReportText.text = text }
    }

    /**
     * 2026-09-28+ zip 스키마 검증 진단 리포트.
     *
     * 이 버튼은 네트워크 호출을 발동시키지 않는다 — 마지막 loadJupiterResource 성공 후 SDK 가
     * 채운 in-memory 스냅샷 (`TJLabsResourceManager` 의 companion 캐시) 을 그대로 요약한다.
     * 아직 sector 를 로드하지 않았다면 "sector not loaded" 를 표시.
     *
     * 표시 항목 (신 스키마가 정상 동작할 때 기대치):
     *  1) `Sector`             : sectorData / buildings / levels / transitions 카운트 — 파싱 정상 여부
     *  2) `Geofence`           : level 수, 세 area 다각형 총합, 총 꼭짓점 수 — 다각형 스키마 파싱 검증
     *  3) `Geofence sample PIP`: 첫 다각형 centroid 에 대해 point-in-polygon 을 자체 계산해 소비자
     *                           측 유효성(ray casting) 을 미리 검증
     *  4) `ParkingMatches`     : level 수, 총 매치 수, level_match 인덱스 수 — zip entry read 검증
     *  5) `Simulations`        : vehicle/pdr 카운트, url 이 로컬 파일인지, 파일 존재 여부 및 총 바이트
     *  6) `Assets`             : pathPixel 엔트리 수, bitmap 로드 수, geofenceData 크기
     */
    private fun runBundleReport(manager: TJLabsResourceManager) {
        val sid = currentSectorId()
        val sector = manager.getSectorData(sid)
        if (sector == null) {
            updateBundleReport("Bundle Report: sector not loaded (sectorId=$sid). Load Jupiter Resource first.")
            appendCallbackLog("bundleReport", "no sector loaded", "api")
            return
        }

        val allLevels = sector.buildings.sumOf { it.levels.size }
        val floors = sector.buildings.sumOf { b -> b.levels.count { it.type == "floor" } }
        val transitionLevels = allLevels - floors

        // ── Geofence 다각형 통계
        val geofences = manager.getGeofenceData()
        val entrancePolys = geofences.values.sumOf { it.entrance_area.size }
        val matchingPolys = geofences.values.sumOf { it.entrance_matching_area.size }
        val levelChangePolys = geofences.values.sumOf { it.level_change_area.size }
        val totalPolys = entrancePolys + matchingPolys + levelChangePolys
        val totalVertices = geofences.values.sumOf { g ->
            g.entrance_area.sumOf { it.size } +
                g.entrance_matching_area.sumOf { it.size } +
                g.level_change_area.sumOf { it.size }
        }

        // ── Sample point-in-polygon 검증
        //   임의로 아무 area 안 첫 다각형 하나를 잡아 그 centroid 로 PIP 를 돌린다.
        //   convex/concave 무관하게 centroid 가 다각형 안쪽에 있을 확률이 높지만 항상은 아님 —
        //   결과가 false 여도 알고리즘 오류라기보다 오목 도형일 수 있으므로 "OK" 로 처리하고
        //   함수 자체가 예외 없이 실행됐는지만 확인한다.
        val pipSample = geofences.entries
            .firstNotNullOfOrNull { entry ->
                (entry.value.entrance_area + entry.value.entrance_matching_area + entry.value.level_change_area)
                    .firstOrNull()?.let { polygon -> entry.key to polygon }
            }
        val pipReport: String = if (pipSample == null) {
            "no polygons"
        } else {
            val (levelKey, polygon) = pipSample
            val centroid = polygonCentroid(polygon)
            val inside = pointInPolygon(centroid.first, centroid.second, polygon)
            "levelKey=$levelKey vertices=${polygon.size} centroid=(${centroid.first},${centroid.second}) inside=$inside"
        }

        // ── Parking matches
        val parkingByLevel = manager.getAllParkingMatches()
        val parkingLevelMatches = manager.getAllLevelMatches()
        val totalMatches = parkingByLevel.values.sumOf { it.size }

        // ── Simulations
        val simulation = manager.getSimulationData(sid)
        val simItems = simulation?.let { it.vehicle + it.pdr } ?: emptyList()
        val simLocalCount = simItems.count { it.url.startsWith("/") }
        val simExistsCount = simItems.count { runCatching { File(it.url).exists() && File(it.url).length() > 0 }.getOrDefault(false) }
        val simTotalBytes = simItems.sumOf { runCatching { File(it.url).length() }.getOrDefault(0L) }
        val simUrlSample = simItems.firstOrNull()?.url?.take(80) ?: "(no sim loaded)"

        // ── Assets
        val pathPixels = manager.getPathPixelData().size
        val bitmapCount = manager.getBuildingLevelImageData().size

        val report = buildString {
            appendLine("Bundle Report (sectorId=$sid)")
            appendLine(" Sector      : ${sector.name} • buildings=${sector.buildings.size} • levels=$allLevels (floor=$floors transition=$transitionLevels) • transitions=${sector.transitions.size}")
            appendLine(" Geofence    : ${geofences.size} levels • polys(E=$entrancePolys M=$matchingPolys L=$levelChangePolys total=$totalPolys) • vertices=$totalVertices")
            appendLine(" GeofencePIP : $pipReport")
            appendLine(" ParkingMatch: ${parkingByLevel.size} levels • matches=$totalMatches • levelMatch=${parkingLevelMatches.size}")
            appendLine(" Simulations : items=${simItems.size} (vehicle=${simulation?.vehicle?.size ?: 0} pdr=${simulation?.pdr?.size ?: 0}) • local=$simLocalCount • exists=$simExistsCount • bytes=$simTotalBytes")
            appendLine("   url sample: $simUrlSample")
            appendLine(" Assets      : pathPixel=$pathPixels • bitmaps=$bitmapCount")
            append(" Generated   : ${nowText()}")
        }
        updateBundleReport(report)
        appendCallbackLog(
            "bundleReport",
            "polys=$totalPolys verts=$totalVertices matches=$totalMatches sim=$simExistsCount/${simItems.size} bitmaps=$bitmapCount",
            "api"
        )
        Log.i("TJLabsResource_BUNDLE", report)
    }

    /** 다각형 꼭짓점 산술 평균. 오목 다각형이면 실제 내부가 아닐 수 있음. */
    private fun polygonCentroid(polygon: List<List<Int>>): Pair<Double, Double> {
        if (polygon.isEmpty()) return 0.0 to 0.0
        val sumX = polygon.sumOf { it.getOrNull(0)?.toDouble() ?: 0.0 }
        val sumY = polygon.sumOf { it.getOrNull(1)?.toDouble() ?: 0.0 }
        return sumX / polygon.size to sumY / polygon.size
    }

    /**
     * 표준 ray casting point-in-polygon. 다각형 꼭짓점은 `[x, y]` (List<Int>) 목록이고, 마지막
     * 꼭짓점과 첫 꼭짓점을 잇는 변까지 포함해 닫힌 도형으로 해석 (2026-09-28+ 스키마 사양).
     */
    private fun pointInPolygon(x: Double, y: Double, polygon: List<List<Int>>): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val xi = polygon[i].getOrNull(0)?.toDouble() ?: 0.0
            val yi = polygon[i].getOrNull(1)?.toDouble() ?: 0.0
            val xj = polygon[j].getOrNull(0)?.toDouble() ?: 0.0
            val yj = polygon[j].getOrNull(1)?.toDouble() ?: 0.0
            val intersects = ((yi > y) != (yj > y)) &&
                (x < (xj - xi) * (y - yi) / ((yj - yi).takeIf { it != 0.0 } ?: 1.0) + xi)
            if (intersects) inside = !inside
            j = i
        }
        return inside
    }

    /**
     * Auth 단독 실행. 리소스 로드 없이 access token 발급 경로만 검증한다 —
     * forceFresh=true 로 in-mem 캐시와 Phase 1 (getAccessToken 프로브) 를 우회하고
     * 곧장 `TJLabsAuthManager.auth()` 네트워크 호출을 발동. 어떤 access key 가 실제로
     * 나가는지, 응답 status/시간이 어떤지 authStatusText · callback log 로 즉시 확인.
     */
    private fun runAuthOnly(provider: String) {
        val scope = currentScopeLabel(provider)
        appendCallbackLog("auth", "authOnly click scope=$scope (forceFresh)", "api")
        runOnUiThread {
            authStatusText.text = "Auth($scope): running…"
            authStatusText.setTextColor(getColor(R.color.text_pending))
        }
        // (provider, region, env) 중 하나만 바뀌어도 새 auth 가 필요하므로 in-mem 캐시 비움.
        authenticatedProviders.clear()
        lastAuthedScope = null
        val startMs = System.currentTimeMillis()
        authenticate(provider, forceFresh = true) { success ->
            val elapsedMs = System.currentTimeMillis() - startMs
            appendCallbackLog(
                "auth",
                "authOnly done success=$success elapsed=${elapsedMs}ms scope=$scope",
                "api"
            )
        }
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
            ) { isSuccess, info ->
                val totalElapsed = System.currentTimeMillis() - jupiterLoadStartMs
                val sdkElapsed = System.currentTimeMillis() - loadCallStartMs
                Log.i(
                    "TJLabsResource_BENCH",
                    "[$provider/$sectorId] v2 total=%4dms = auth %4dms + sdk %4dms | ok=%s | version=%s cached=%s"
                        .format(totalElapsed, authElapsed, sdkElapsed, isSuccess, info?.versionId ?: "-", info?.fromCache ?: "-")
                )
                appendCallbackLog(
                    "loadJupiterResource",
                    "v2 ok=$isSuccess total=${totalElapsed}ms (auth=${authElapsed} sdk=${sdkElapsed}) version=${info?.versionId ?: "-"} cached=${info?.fromCache ?: "-"} scope=$scope",
                    "api"
                )
                updateJupiterStatus(
                    scope,
                    "v2 sectorId=$sectorId • total=${totalElapsed}ms (auth=${authElapsed} sdk=${sdkElapsed}) • ${nowText()}",
                    isSuccess
                )
                if (isSuccess) {
                    recordSample(v2Samples, sdkElapsed)
                    updateJupiterCompare()
                }
            }
        }
    }

    /**
     * v1 (2026-09-10 JSON) endpoint 강제 호출. `useLegacyEndpoint=true` 라 SDK 가
     *   - 프리즈된 legacy meta endpoint `/2026-09-10/sectors/{pk}/bundle` 를 호출하고
     *   - 응답 URL 의 JSON 파일을 통째로 다운로드해 파싱하며
     *   - 개별 자원(그래프 CSV, parking_matches JSON) 을 각각 HTTP GET (URL fetch 흐름)
     *   - v2 in-memory / disk 캐시를 오염시키지 않음 (매 호출마다 fresh fetch)
     * v2 흐름(zip 다운로드 + zip entry read) 대비 (a) 다운로드 크기 (b) SDK enrich 시간 을 비교하기 위한 벤치마크 전용.
     */
    private fun runJupiterBundleV1Test(manager: TJLabsResourceManager, provider: String, sectorId: Int) {
        val loadStartMs = System.currentTimeMillis()
        val scope = currentScopeLabel(provider) + " [v1]"
        updateJupiterStatus(scope, "v1 sectorId=$sectorId • ${nowText()}", null)
        appendCallbackLog("loadJupiterResource", "start v1 sectorId=$sectorId scope=$scope", "api")
        authenticate(provider) { authSuccess ->
            if (!authSuccess) {
                updateJupiterStatus(scope, "auth failed • ${nowText()}", false)
                appendCallbackLog("loadJupiterResource", "v1 auth failed scope=$scope", "api")
                return@authenticate
            }
            val loadCallStartMs = System.currentTimeMillis()
            val authElapsed = loadCallStartMs - loadStartMs
            manager.loadJupiterResource(
                application = application,
                provider = provider,
                region = getSelectedRegion(),
                sectorId = sectorId,
                env = getSelectedResourceEnv(),
                useLegacyEndpoint = true,
            ) { isSuccess, info ->
                val totalElapsed = System.currentTimeMillis() - loadStartMs
                val sdkElapsed = System.currentTimeMillis() - loadCallStartMs
                Log.i(
                    "TJLabsResource_BENCH",
                    "[$provider/$sectorId] v1 total=%4dms = auth %4dms + sdk %4dms | ok=%s | version=%s"
                        .format(totalElapsed, authElapsed, sdkElapsed, isSuccess, info?.versionId ?: "-")
                )
                appendCallbackLog(
                    "loadJupiterResource",
                    "v1 ok=$isSuccess total=${totalElapsed}ms (auth=${authElapsed} sdk=${sdkElapsed}) version=${info?.versionId ?: "-"} scope=$scope",
                    "api"
                )
                updateJupiterStatus(
                    scope,
                    "v1 sectorId=$sectorId • total=${totalElapsed}ms (auth=${authElapsed} sdk=${sdkElapsed}) • ${nowText()}",
                    isSuccess
                )
                if (isSuccess) {
                    recordSample(v1Samples, sdkElapsed)
                    updateJupiterCompare()
                }
            }
        }
    }

    /**
     * 최근 N개 샘플만 유지하는 rolling window 갱신.
     */
    private fun recordSample(bucket: MutableList<Long>, sample: Long) {
        bucket.add(sample)
        while (bucket.size > maxBenchSamples) bucket.removeAt(0)
    }

    private fun stats(samples: List<Long>): Triple<Long, Long, Long>? {
        if (samples.isEmpty()) return null
        val sorted = samples.sorted()
        val min = sorted.first()
        val max = sorted.last()
        val median = sorted[sorted.size / 2]
        return Triple(min, median, max)
    }

    /**
     * v1 vs v2 SDK 소요시간 비교 텍스트 갱신. rolling window (최근 [maxBenchSamples] 개) 의
     * min / median / max 를 함께 표시해 network jitter · CDN warmup 편차를 시각화한다.
     *
     * median 을 기준으로 대소 비교 — 최소·최댓값에 흔들리지 않게.
     */
    private fun updateJupiterCompare() {
        val v2s = stats(v2Samples)
        val v1s = stats(v1Samples)
        val text = buildString {
            appendLine("v1 vs v2 (last ${maxBenchSamples} samples)")
            append("  v2 = ")
            if (v2s == null) {
                append("대기중")
            } else {
                val (mn, md, mx) = v2s
                append("median=${md}ms  min=${mn}  max=${mx}  n=${v2Samples.size}  samples=${v2Samples}")
            }
            appendLine()
            append("  v1 = ")
            if (v1s == null) {
                append("대기중")
            } else {
                val (mn, md, mx) = v1s
                append("median=${md}ms  min=${mn}  max=${mx}  n=${v1Samples.size}  samples=${v1Samples}")
            }
            if (v2s != null && v1s != null) {
                appendLine()
                val diff = v1s.second - v2s.second  // v1.median - v2.median
                val pct = if (v2s.second > 0) 100.0 * diff / v2s.second else 0.0
                val verdict = when {
                    diff > 0 -> "v1 이 median ${diff}ms 느림 (${"%.1f".format(pct)}%↑)"
                    diff < 0 -> "v1 이 median ${-diff}ms 빠름 (${"%.1f".format(-pct)}%↓)"
                    else -> "median 동일"
                }
                append("  ▶ $verdict")
            }
        }
        runOnUiThread { jupiterCompareText.text = text }
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
            ) { isSuccess, info ->
                appendCallbackLog(
                    "loadVenusResource",
                    "success=$isSuccess sectorId=$sectorId version=${info?.versionId ?: "-"} cached=${info?.fromCache ?: "-"} scope=$scope",
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
            ) { isSuccess, info ->
                appendCallbackLog(
                    "loadWarpResource",
                    "success=$isSuccess sectorId=$sectorId version=${info?.versionId ?: "-"} cached=${info?.fromCache ?: "-"} scope=$scope",
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
        // 전체 SectorOutput toString 은 수 KB 라 요약만 남긴다. 필요하면 UI callback log 에서 확인.
        TJResourceLogger.d("onSectorData : id=${data.id} name=${data.name} buildings=${data.buildings.size} transitions=${data.transitions.size}")
        populateSourceHints(data)
        // 스키마 2026-08-06+ : level.type == "floor" 인 것만 사용자 층 선택 UI 후보.
        // 전이층("transition") 은 측위·경로탐색 대상이므로 SDK 는 필터링 없이 그대로 전달함.
        val allLevels = data.buildings.sumOf { it.levels.size }
        val floors = data.buildings.sumOf { b -> b.levels.count { it.type == "floor" } }
        val transitionLevels = allLevels - floors
        appendCallbackLog(
            "onSectorData",
            "sectorId=${data.id} buildings=${data.buildings.size} levels=$allLevels (floor=$floors transition=$transitionLevels) transitions=${data.transitions.size}",
            "api"
        )
        updateCardStatusOnly(
            jupiterCard,
            "sectorId=${data.id} buildings=${data.buildings.size} floors=$floors transitions=${data.transitions.size} • ${nowText()}",
            true
        )
    }

    override fun onSectorError(error: ResourceError) {
        TJResourceLogger.d("onSectorError : $error")
        appendCallbackLog("onSectorError", "error=$error", "api")
        updateCardStatusOnly(jupiterCard, "error=$error • ${nowText()}", false)
    }

    override fun onBuildingsData(data: List<BuildingOutput>) {
        TJResourceLogger.d("onBuildingsData : count=${data.size} levelsTotal=${data.sumOf { it.levels.size }}")
        appendCallbackLog("onBuildingsData", "count=${data.size}", "api")
    }

    override fun onLevelWardsData(levelKey: String, data: List<String>) {
        TJResourceLogger.d("onLevelWardsData : $levelKey // wards=${data.size}")
        appendCallbackLog("onLevelWardsData", "key=$levelKey wards=${data.size}", "api")
    }

    override fun onScaleOffsetData(scaleKey: String, data: List<Float>) {
        TJResourceLogger.d("onScaleOffsetData : $scaleKey // size=${data.size}")
        appendCallbackLog("onScaleOffsetData", "key=$scaleKey size=${data.size}", "api")
    }

    override fun onPathPixelData(pathPixelKey: String, levelType: String, data: PathPixelData) {
        // 전체 배열 dump 는 로그 폭주 → 카운트만 남긴다. 상세는 필요할 때만 소비자 측에서 dump.
        TJResourceLogger.d("onPathPixelData : $pathPixelKey // type=$levelType // nodes=${data.road.size} points=${data.road.firstOrNull()?.size ?: 0}")

        val source = pathPixelSourceHint[pathPixelKey] ?: "api"
        appendCallbackLog(
            "onPathPixelData",
            "key=$pathPixelKey type=$levelType nodes=${data.road.size} roadPts=${data.road.firstOrNull()?.size ?: 0}",
            source
        )
    }

    override fun onGeofenceData(geofenceKey: String, data: GeofenceData) {
        // 2026-09-28+ 스키마: 각 area 는 다각형 목록 (`List<List<List<Int>>>`).
        // 여기선 다각형 개수와 총 꼭짓점 수를 요약해 UI 로그로 흘려보내 스키마 마이그레이션 검증에 사용.
        val entrancePolys = data.entrance_area.size
        val matchingPolys = data.entrance_matching_area.size
        val levelChangePolys = data.level_change_area.size
        val totalVertices =
            data.entrance_area.sumOf { it.size } +
            data.entrance_matching_area.sumOf { it.size } +
            data.level_change_area.sumOf { it.size }
        TJResourceLogger.d(
            "onGeofenceData : $geofenceKey // polys(entrance=$entrancePolys matching=$matchingPolys levelChange=$levelChangePolys) vertices=$totalVertices"
        )
        appendCallbackLog(
            "onGeofenceData",
            "key=$geofenceKey polys(E=$entrancePolys M=$matchingPolys L=$levelChangePolys) vertices=$totalVertices",
            "api"
        )
    }

    override fun onEntranceData(entranceKey: String, data: EntranceData) {
        TJResourceLogger.d("onEntranceData : $entranceKey // number=${data.number}")
        appendCallbackLog(
            "onEntranceData",
            "key=$entranceKey number=${data.number}",
            "api"
        )
    }

    override fun onEntranceRouteData(entranceKey: String, data: EntranceRouteData) {
        TJResourceLogger.d("onEntranceRouteData : $entranceKey // levels=${data.routeLevel.size} routes=${data.route.size}")
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

    override fun onAffineData(sectorId: Int, data: AffineTransParamOutput) {
        TJResourceLogger.d("onAffineData sectorId $sectorId // data : $data")
        appendCallbackLog("onAffineData", "sectorId=$sectorId", "api")
    }

    override fun onLandmarkData(key: String, data: Map<String, LandmarkData>) {
        val peaksTotal = data.values.sumOf { it.peaks.size }
        TJResourceLogger.d("onLandmarkData key $key // wards=${data.size} peaks=$peaksTotal")
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
        TJResourceLogger.d("onNodeLinkData key $key // type : $type // count=${describeSize(data)}")
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

    override fun onTransitionData(transitionKey: String, data: TransitionOutput) {
        TJResourceLogger.d(
            "onTransitionData key=$transitionKey id=${data.id} name=${data.name} " +
                "level=${data.level.id} lower=${data.lower_level.id}(bldg=${data.lower_level.building_id}) " +
                "upper=${data.upper_level.id}(bldg=${data.upper_level.building_id}) points=${data.points.size}"
        )
        val typeCounts = data.points
            .groupingBy { it.transition_type }
            .eachCount()
            .entries
            .joinToString(",") { "${it.key}=${it.value}" }
            .ifEmpty { "none" }
        val vehiclePoints = data.points.count { it.is_vehicle }
        appendCallbackLog(
            "onTransitionData",
            "key=$transitionKey name=${data.name} points=${data.points.size} (vehicle=$vehiclePoints) types=[$typeCounts]",
            "api"
        )
    }

    /**
     * Multi 섹터 로드 완료 콜백 (iOS parity). TJLabsMultiResourceManager 가 각 섹터 처리 후 발화.
     * 단일 섹터 loadJupiterResource 흐름에서는 호출되지 않음.
     */
    override fun onSectorResourceLoadFinished(sectorId: Int, result: SectorLoadResult) {
        appendCallbackLog(
            "onSectorResourceLoadFinished",
            "sector=$sectorId ok=${result.isSuccess} stage=${result.failedStage ?: "-"} version=${result.versionId} isCached=${result.isCached}",
            "api"
        )
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
        // 2026-09-28+: SimulationItemOutput.url 은 원격 URL 이 아니라 zip 에서 풀어낸 로컬 파일 경로.
        //   - 절대 파일 경로 여부 (leading "/")
        //   - 실제 파일 존재 여부
        // 둘 다 카운트해서 zip → disk extract 흐름 검증 (`extractSimulationsFromZip`).
        val items = data.vehicle + data.pdr
        val localPathCount = items.count { it.url.startsWith("/") }
        val existsCount = items.count { runCatching { File(it.url).exists() && File(it.url).length() > 0 }.getOrDefault(false) }
        val totalBytes = items.sumOf { runCatching { File(it.url).length() }.getOrDefault(0L) }
        appendCallbackLog(
            "onSimulationData",
            "sectorId=$sectorId vehicle=${data.vehicle.size} pdr=${data.pdr.size} localPath=$localPathCount/${items.size} filesExist=$existsCount/${items.size} totalBytes=$totalBytes",
            "api"
        )
        updateCardStatusOnly(
            simulationCard,
            "sectorId=$sectorId v=${data.vehicle.size} p=${data.pdr.size} • files $existsCount/${items.size} (${totalBytes}B) • ${nowText()}",
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

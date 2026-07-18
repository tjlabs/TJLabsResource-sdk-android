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
    private lateinit var jupiterDetailText: TextView
    private lateinit var venusStatusText: TextView
    private lateinit var venusDetailText: TextView
    private lateinit var warpStatusText: TextView
    private lateinit var warpDetailText: TextView
    private lateinit var callbackContainer: LinearLayout
    private lateinit var providerGroup: RadioGroup
    private lateinit var envGroup: RadioGroup
    private lateinit var currentEnvText: TextView
    private lateinit var testJupiterBundleButton: Button
    private lateinit var testVenusBundleButton: Button
    private lateinit var testWardBundleButton: Button
    private lateinit var loadSimulationDataButton: Button
    private lateinit var clearCacheButton: Button
    private lateinit var benchmarkJupiterButton: Button
    private lateinit var benchmarkResultText: TextView

    private var jupiterLoadStartMs: Long = 0L
    // 같은 provider 에 대해 auth() 풀 로그인은 1회만 — getAccessToken 이 토큰 캐시로 동작하기 때문
    private val authenticatedProviders = mutableSetOf<String>()

    private val pathPixelSourceHint = mutableMapOf<String, String>()
    private val imageSourceHint = mutableMapOf<String, String>()
    private val imageUrlByKey = mutableMapOf<String, String>()
    private var logCount = 0
    private val maxLogCount = 200

    private var resourceManager: TJLabsResourceManager? = null
    private lateinit var accessKey: String
    private lateinit var accessSecretKey: String
    private lateinit var clientKey: String
    private val sectorId = 20 // covensia : 20 // tips : 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        accessKey = BuildConfig.AUTH_ACCESS_KEY
        accessSecretKey = BuildConfig.AUTH_SECRET_ACCESS_KEY
        clientKey = BuildConfig.AUTH_CLIENT_SECRET
        authStatusText = findViewById(R.id.textAuthStatus)
        jupiterStatusText = findViewById(R.id.textJupiterStatus)
        jupiterDetailText = findViewById(R.id.textJupiterDetail)
        venusStatusText = findViewById(R.id.textVenusStatus)
        venusDetailText = findViewById(R.id.textVenusDetail)
        warpStatusText = findViewById(R.id.textWarpStatus)
        warpDetailText = findViewById(R.id.textWarpDetail)
        callbackContainer = findViewById(R.id.callbackContainer)
        providerGroup = findViewById(R.id.radioGroupProvider)
        envGroup = findViewById(R.id.radioGroupEnv)
        currentEnvText = findViewById(R.id.textCurrentEnv)
        val refreshEnvLabel = {
            val env = getSelectedEnv()
            val suffix = if (env == AuthServerEnv.PROD) ".jupiter.tjlabscorp.com" else ".jupiter.tjlabs.dev"
            currentEnvText.text = "Selected env : $env  (OLYMPUS suffix : $suffix)"
        }
        refreshEnvLabel()
        envGroup.setOnCheckedChangeListener { _, _ ->
            refreshEnvLabel()
            // env 를 바꾸면 이전 auth 토큰은 유효하지 않으므로 캐시 초기화 → 다음 호출에서 다시 auth
            authenticatedProviders.clear()
        }
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

        if (accessKey.isBlank() || accessSecretKey.isBlank() || clientKey.isBlank()) {
            val reason = "Auth keys are empty. Check local.properties: access_key, access_secret_key, client_key"
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
            updateJupiterStatus("Loading (cold)...", "provider=$provider sectorId=$sectorId • ${nowText()}", null)
            manager.loadJupiterResource(
                application = application,
                provider = provider,
                region = ResourceRegion.KOREA.value,
                sectorId = sectorId,
                env = getSelectedResourceEnv(),
            ) { coldSuccess ->
                val coldElapsed = System.currentTimeMillis() - coldStart

                // 4. Warm load (캐시 적중 기대)
                val warmStart = System.currentTimeMillis()
                manager.loadJupiterResource(
                    application = application,
                    provider = provider,
                    region = ResourceRegion.KOREA.value,
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
                        if (warmSuccess) "Cold+Warm OK" else "Warm Fail",
                        "auth=${authElapsed}ms cold=${coldElapsed}ms warm=${warmElapsed}ms • ${nowText()}",
                        warmSuccess
                    )
                }
            }
        }
    }

    private fun updateBenchmarkResult(text: String) {
        runOnUiThread { benchmarkResultText.text = text }
    }

    private fun runJupiterBundleTest(manager: TJLabsResourceManager, provider: String, sectorId: Int) {
        jupiterLoadStartMs = System.currentTimeMillis()
        updateJupiterStatus("Loading...", "provider=$provider sectorId=$sectorId • ${nowText()}", null)
        appendCallbackLog("loadJupiterResource", "start sectorId=$sectorId", "api")
        authenticate(provider) { authSuccess ->
            if (!authSuccess) {
                updateJupiterStatus("Failed", "auth failed • ${nowText()}", false)
                appendCallbackLog("loadJupiterResource", "auth failed provider=$provider", "api")
                return@authenticate
            }
            val loadCallStartMs = System.currentTimeMillis()
            val authElapsed = loadCallStartMs - jupiterLoadStartMs
            manager.loadJupiterResource(
                application = application,
                provider = provider,
                region = ResourceRegion.KOREA.value,
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
                    "ok=$isSuccess total=${totalElapsed}ms (auth=${authElapsed} sdk=${sdkElapsed})",
                    "api"
                )
                updateJupiterStatus(
                    if (isSuccess) "Success" else "Failed",
                    "provider=$provider sectorId=$sectorId • total=${totalElapsed}ms (auth=${authElapsed} sdk=${sdkElapsed}) • ${nowText()}",
                    isSuccess
                )
            }
        }
    }

    private fun runVenusBundleTest(manager: TJLabsResourceManager, provider: String, sectorId: Int) {
        updateVenusStatus("Loading...", "provider=$provider sectorId=$sectorId • ${nowText()}", null)
        appendCallbackLog("loadVenusResource", "start sectorId=$sectorId", "api")
        authenticate(provider) { authSuccess ->
            if (!authSuccess) {
                updateVenusStatus("Failed", "auth failed • ${nowText()}", false)
                appendCallbackLog("loadVenusResource", "auth failed provider=$provider", "api")
                return@authenticate
            }
            manager.loadVenusResource(
                application = application,
                provider = provider,
                region = ResourceRegion.KOREA.value,
                sectorId = sectorId,
                env = getSelectedResourceEnv(),
            ) { isSuccess ->
                appendCallbackLog("loadVenusResource", "success=$isSuccess sectorId=$sectorId", "api")
                updateVenusStatus(
                    if (isSuccess) "Success" else "Failed",
                    "provider=$provider sectorId=$sectorId • ${nowText()}",
                    isSuccess
                )
            }
        }
    }

    private fun runWardBundleTest(manager: TJLabsResourceManager, provider: String, sectorId: Int) {
        updateWarpStatus("Loading...", "provider=$provider sectorId=$sectorId • ${nowText()}", null)
        appendCallbackLog("loadWarpResource", "start sectorId=$sectorId", "api")
        authenticate(provider) { authSuccess ->
            if (!authSuccess) {
                updateWarpStatus("Failed", "auth failed • ${nowText()}", false)
                appendCallbackLog("loadWarpResource", "auth failed provider=$provider", "api")
                return@authenticate
            }
            manager.loadWarpResource(
                application = application,
                provider = provider,
                region = ResourceRegion.KOREA.value,
                sectorId = sectorId
            ) { isSuccess ->
                appendCallbackLog("loadWarpResource", "success=$isSuccess sectorId=$sectorId", "api")
                updateWarpStatus(
                    if (isSuccess) "Success" else "Failed",
                    "provider=$provider sectorId=$sectorId • ${nowText()}",
                    isSuccess
                )
            }
        }
    }

    private fun runSimulationDataLoad(manager: TJLabsResourceManager, provider: String, sectorId: Int) {
        appendCallbackLog("loadSimulationData", "start sectorId=$sectorId", "api")
        authenticate(provider) { authSuccess ->
            if (!authSuccess) {
                appendCallbackLog("loadSimulationData", "auth failed provider=$provider", "api")
                return@authenticate
            }
            manager.loadSimulationData(
                application = application,
                provider = provider,
                region = ResourceRegion.KOREA.value,
                sectorId = sectorId
            ) { isSuccess ->
                appendCallbackLog("loadSimulationData", "success=$isSuccess sectorId=$sectorId", "api")
            }
        }
    }

    private fun nowText(): String {
        val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return formatter.format(Date())
    }

    private fun updateJupiterStatus(status: String, detail: String, success: Boolean?) {
        runOnUiThread {
            jupiterStatusText.text = status
            jupiterDetailText.text = detail
            val colorRes = when (success) {
                true -> R.color.text_success
                false -> R.color.text_fail
                null -> R.color.text_pending
            }
            jupiterStatusText.setTextColor(getColor(colorRes))
        }
    }

    private fun updateVenusStatus(status: String, detail: String, success: Boolean?) {
        runOnUiThread {
            venusStatusText.text = status
            venusDetailText.text = detail
            val colorRes = when (success) {
                true -> R.color.text_success
                false -> R.color.text_fail
                null -> R.color.text_pending
            }
            venusStatusText.setTextColor(getColor(colorRes))
        }
    }

    private fun updateWarpStatus(status: String, detail: String, success: Boolean?) {
        runOnUiThread {
            warpStatusText.text = status
            warpDetailText.text = detail
            val colorRes = when (success) {
                true -> R.color.text_success
                false -> R.color.text_fail
                null -> R.color.text_pending
            }
            warpStatusText.setTextColor(getColor(colorRes))
        }
    }

    private fun getSelectedProvider(): String {
        return when (providerGroup.checkedRadioButtonId) {
            R.id.radioGcp -> ServerProvider.GCP.value
            else -> ServerProvider.AWS.value
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

    private fun authenticate(provider: String, completion: (Boolean) -> Unit) {
        val authPhaseStart = System.currentTimeMillis()

        if (provider in authenticatedProviders) {
            val mem = System.currentTimeMillis() - authPhaseStart
            Log.i("TJLabsResource_AUTH", "[$provider] in-mem cache hit (${mem}ms) — both probe and auth() skipped")
            runOnUiThread {
                authStatusText.text = "Auth($provider): Success (in-mem cached)"
                authStatusText.setTextColor(getColor(R.color.text_success))
            }
            completion(true)
            return
        }

        TJLabsAuthManager.setServerURL(
            provider = provider,
            region = ResourceRegion.KOREA.value,
            env = getSelectedEnv(),
        )
        TJLabsAuthManager.setLogEnabled(true)
        TJLabsAuthManager.setClientSecret(application, clientKey)

        // Phase 1) 디스크에 영속된 토큰 lookup (=getAccessToken). 네트워크 없이 끝나면 ms 단위.
        val probeStart = System.currentTimeMillis()
        TJLabsAuthManager.getAccessToken { tokenResult ->
            val probeMs = System.currentTimeMillis() - probeStart
            when (tokenResult) {
                is TokenResult.Success -> {
                    val total = System.currentTimeMillis() - authPhaseStart
                    Log.i(
                        "TJLabsResource_AUTH",
                        "[$provider] probe=%4dms → token reused, auth() SKIPPED | total=%4dms"
                            .format(probeMs, total)
                    )
                    authenticatedProviders.add(provider)
                    runOnUiThread {
                        authStatusText.text = "Auth($provider): Success (token reused, ${probeMs}ms)"
                        authStatusText.setTextColor(getColor(R.color.text_success))
                    }
                    completion(true)
                }
                is TokenResult.Failure -> {
                    // Phase 2) 캐시된 토큰 없음/만료 → 풀 auth() 네트워크 호출.
                    val authNetStart = System.currentTimeMillis()
                    TJLabsAuthManager.auth(accessKey, accessSecretKey) { code, success ->
                        val authNetMs = System.currentTimeMillis() - authNetStart
                        val total = System.currentTimeMillis() - authPhaseStart
                        Log.i(
                            "TJLabsResource_AUTH",
                            "[$provider] probe=%4dms (miss) + auth()=%4dms | total=%4dms ok=%s code=%s"
                                .format(probeMs, authNetMs, total, success, code)
                        )
                        if (success) authenticatedProviders.add(provider)
                        runOnUiThread {
                            authStatusText.text = if (success)
                                "Auth($provider): Success (probe=${probeMs} + auth=${authNetMs}ms)"
                            else
                                "Auth($provider): Failed (code: $code)"
                            authStatusText.setTextColor(
                                getColor(if (success) R.color.text_success else R.color.text_fail)
                            )
                        }
                        completion(success)
                    }
                }
            }
        }
    }

    override fun onSectorData(data: SectorOutput) {
        TJResourceLogger.d("onSectorData : $data")
        populateSourceHints(data)
        appendCallbackLog("onSectorData", "sectorId=${data.id} buildings=${data.buildings.size}", "api")
        updateJupiterStatus("Success", "sectorId=${data.id} buildings=${data.buildings.size} • ${nowText()}", true)
    }

    override fun onSectorError(error: ResourceError) {
        TJResourceLogger.d("onSectorError : $error")
        appendCallbackLog("onSectorError", "error=$error", "api")
        updateJupiterStatus("Failed", "error=$error • ${nowText()}", false)
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
        updateWarpStatus("Success", "sectorId=${data.id} buildings=$buildings wards=$wards • ${nowText()}", true)
    }

    override fun onWarpError(error: ResourceError) {
        appendCallbackLog("onWarpError", "error=$error", "api")
        updateWarpStatus("Failed", "error=$error • ${nowText()}", false)
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
        updateVenusStatus("Success", "sectorId=${data.id} buildings=$buildings wards=$wards • ${nowText()}", true)
    }

    override fun onVenusError(error: ResourceError) {
        appendCallbackLog("onVenusError", "error=$error", "api")
        updateVenusStatus("Failed", "error=$error • ${nowText()}", false)
    }

    override fun onSimulationData(sectorId: Int, data: SimulationBundleOutput) {
        appendCallbackLog(
            "onSimulationData",
            "sectorId=$sectorId vehicle=${data.vehicle.size} pdr=${data.pdr.size}",
            "api"
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
        runOnUiThread {
            val line = "[${nowText()}] $event - $detail - source=$source"
            val textView = TextView(this).apply {
                text = line
                textSize = 12f
                setTextColor(getColor(R.color.black))
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

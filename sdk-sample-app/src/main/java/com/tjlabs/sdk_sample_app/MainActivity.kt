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
import com.tjlabs.tjlabsauth_sdk_android.TJLabsAuthManager
import com.tjlabs.tjlabsresource_sdk_android.*
import com.tjlabs.tjlabsresource_sdk_android.util.TJResourceLogger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), TJLabsResourceManagerDelegate, TJLabsWarpResourceManagerDelegate, TJLabsVenusResourceManagerDelegate {
    private lateinit var authStatusText: TextView
    private lateinit var jupiterStatusText: TextView
    private lateinit var jupiterDetailText: TextView
    private lateinit var venusStatusText: TextView
    private lateinit var venusDetailText: TextView
    private lateinit var warpStatusText: TextView
    private lateinit var warpDetailText: TextView
    private lateinit var callbackContainer: LinearLayout
    private lateinit var providerGroup: RadioGroup
    private lateinit var testJupiterBundleButton: Button
    private lateinit var testVenusBundleButton: Button
    private lateinit var testWardBundleButton: Button

    private val pathPixelSourceHint = mutableMapOf<String, String>()
    private val imageSourceHint = mutableMapOf<String, String>()
    private val imageUrlByKey = mutableMapOf<String, String>()
    private var logCount = 0
    private val maxLogCount = 200

    private var resourceManager: TJLabsResourceManager? = null
    private lateinit var accessKey: String
    private lateinit var accessSecretKey: String
    private lateinit var clientKey: String
    private val sectorId = 1 // covensia : 20 // tips : 1

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
        testJupiterBundleButton = findViewById(R.id.buttonTestJupiterBundle)
        testVenusBundleButton = findViewById(R.id.buttonTestVenusBundle)
        testWardBundleButton = findViewById(R.id.buttonTestWardBundle)

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
    }

    private fun runJupiterBundleTest(manager: TJLabsResourceManager, provider: String, sectorId: Int) {
        updateJupiterStatus("Loading...", "provider=$provider sectorId=$sectorId • ${nowText()}", null)
        appendCallbackLog("loadJupiterResource", "start sectorId=$sectorId", "api")
        authenticate(provider) { authSuccess ->
            if (!authSuccess) {
                updateJupiterStatus("Failed", "auth failed • ${nowText()}", false)
                appendCallbackLog("loadJupiterResource", "auth failed provider=$provider", "api")
                return@authenticate
            }
            manager.loadJupiterResource(
                application = application,
                provider = provider,
                region = ResourceRegion.KOREA.value,
                sectorId = sectorId
            ) { isSuccess ->
                appendCallbackLog("loadJupiterResource", "success=$isSuccess sectorId=$sectorId", "api")
                updateJupiterStatus(
                    if (isSuccess) "Success" else "Failed",
                    "provider=$provider sectorId=$sectorId • ${nowText()}",
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
                sectorId = sectorId
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

    private fun authenticate(provider: String, completion: (Boolean) -> Unit) {
        TJLabsAuthManager.setServerURL(provider = provider, region = ResourceRegion.KOREA.value)
        TJLabsAuthManager.setLogEnabled(true)
        TJLabsAuthManager.setClientSecret(application, clientKey)
        TJLabsAuthManager.auth(accessKey, accessSecretKey) { code, success ->
            Log.d("CheckToken", "code : $code // success : $success")
            runOnUiThread {
                authStatusText.text = if (success) "Auth($provider): Success" else "Auth($provider): Failed (code: $code)"
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

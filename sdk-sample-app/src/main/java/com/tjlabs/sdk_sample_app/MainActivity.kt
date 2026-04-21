package com.tjlabs.sdk_sample_app

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
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

class MainActivity : AppCompatActivity(), TJLabsResourceManagerDelegate {
    private lateinit var authStatusText: TextView
    private lateinit var jupiterStatusText: TextView
    private lateinit var jupiterDetailText: TextView
    private lateinit var mapStatusText: TextView
    private lateinit var mapDetailText: TextView
    private lateinit var callbackContainer: LinearLayout
    private lateinit var testBundleButton: Button

    private val pathPixelSourceHint = mutableMapOf<String, String>()
    private val imageSourceHint = mutableMapOf<String, String>()
    private val imageUrlByKey = mutableMapOf<String, String>()
    private var logCount = 0
    private val maxLogCount = 200

    private var resourceManager: TJLabsResourceManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val accessKey = BuildConfig.AUTH_ACCESS_KEY
        val accessSecretKey = BuildConfig.AUTH_SECRET_ACCESS_KEY
        val clientKey = BuildConfig.AUTH_CLIENT_SECRET
        val sectorId = 20 // covensia : 20
        authStatusText = findViewById(R.id.textAuthStatus)
        jupiterStatusText = findViewById(R.id.textJupiterStatus)
        jupiterDetailText = findViewById(R.id.textJupiterDetail)
        mapStatusText = findViewById(R.id.textMapStatus)
        mapDetailText = findViewById(R.id.textMapDetail)
        callbackContainer = findViewById(R.id.callbackContainer)
        testBundleButton = findViewById(R.id.buttonTestBundle)

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

        TJLabsAuthManager.setServerURL(provider = ServerProvider.GCP.value)
        TJLabsAuthManager.setLogEnabled(true)
        TJLabsAuthManager.setClientSecret(application, clientKey)
        TJLabsAuthManager.auth(accessKey, accessSecretKey) {
                code, success->
            Log.d("CheckToken", "code : $code // success : $success")

            runOnUiThread {
                authStatusText.text = if (success) "Auth: Success" else "Auth: Failed (code: $code)"
                authStatusText.setTextColor(
                    getColor(if (success) R.color.text_success else R.color.text_fail)
                )
            }

            if (success) {
                val tjlabsResourceManager = TJLabsResourceManager()
                resourceManager = tjlabsResourceManager
                tjlabsResourceManager.delegate = this
                tjlabsResourceManager.setDebugOption(true)

                testBundleButton.setOnClickListener {
                    runSectorBundleTest(tjlabsResourceManager, sectorId)
                }

                updateJupiterStatus("Loading...", "Sector $sectorId • ${nowText()}", null)
                updateMapStatus("Loading...", "Unified load • Sector $sectorId • ${nowText()}", null)

                tjlabsResourceManager.loadResource(application, ServerProvider.GCP.value, ResourceRegion.KOREA.value, sectorId) {
                    isSuccess ->
                    TJResourceLogger.d("loadResource : $isSuccess")
                    val detail = "Sector $sectorId • ${nowText()}"
                    updateJupiterStatus(
                        if (isSuccess) "Success" else "Failed",
                        detail,
                        isSuccess
                    )
                    updateMapStatus(
                        if (isSuccess) "Success" else "Failed",
                        "Unified load • $detail",
                        isSuccess
                    )
                }
            }
        }
    }

    private fun runSectorBundleTest(manager: TJLabsResourceManager, sectorId: Int) {
        appendCallbackLog("testLoadSectorBundle", "start sectorId=$sectorId", "api")
        manager.testLoadSectorBundle(
            application = application,
            region = ResourceRegion.KOREA.value,
            provider = ServerProvider.GCP.value,
            sectorId = sectorId
        ) { isSuccess, message, versionId, bundleUrl, mappedSector ->
            val detail = buildString {
                append("success=$isSuccess")
                append(" version=$versionId")
                append(" buildings=${mappedSector?.buildings?.size ?: 0}")
                append(" url=${bundleUrl ?: "null"}")
                append(" msg=$message")
            }
            appendCallbackLog("testLoadSectorBundle", detail, "api")
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

    private fun updateMapStatus(status: String, detail: String, success: Boolean?) {
        runOnUiThread {
            mapStatusText.text = status
            mapDetailText.text = detail
            val colorRes = when (success) {
                true -> R.color.text_success
                false -> R.color.text_fail
                null -> R.color.text_pending
            }
            mapStatusText.setTextColor(getColor(colorRes))
        }
    }

    override fun onSectorData(data: SectorOutput) {
        TJResourceLogger.d("onSectorData : $data")
        populateSourceHints(data)
        appendCallbackLog("onSectorData", "sectorId=${data.id} buildings=${data.buildings.size}", "api")

    }

    override fun onSectorError(error: ResourceError) {
        TJResourceLogger.d("onSectorError : $error")
        appendCallbackLog("onSectorError", "error=$error", "api")
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

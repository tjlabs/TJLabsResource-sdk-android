package com.tjlabs.sdk_sample_app

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.tjlabs.resource_sdk_sample_app.R
import com.tjlabs.tjlabsauth_sdk_android.TJLabsAuthManager
import com.tjlabs.tjlabsresource_sdk_android.*
import com.tjlabs.tjlabsresource_sdk_android.manager.TJLabsImageCacheManager
import com.tjlabs.tjlabsresource_sdk_android.util.TJLogger
import java.io.File
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

        val tenantId = "tjlabs"
        val tenantPw = "TJlabs0407@"
        val sectorId = 6

        authStatusText = findViewById(R.id.textAuthStatus)
        jupiterStatusText = findViewById(R.id.textJupiterStatus)
        jupiterDetailText = findViewById(R.id.textJupiterDetail)
        mapStatusText = findViewById(R.id.textMapStatus)
        mapDetailText = findViewById(R.id.textMapDetail)
        callbackContainer = findViewById(R.id.callbackContainer)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        TJLabsAuthManager.initialize(application)
        TJLabsAuthManager.auth(tenantId, tenantPw) {
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

                updateJupiterStatus("Loading...", "Sector $sectorId • ${nowText()}", null)
                updateMapStatus("Loading...", "Sector $sectorId • ${nowText()}", null)

                tjlabsResourceManager.loadJupiterResource(application, ResourceRegion.KOREA.value, sectorId) {
                        isSuccess ->
                    TJLogger.d("loadJupiterResource(jupiter): $isSuccess")
                    val detail = "Sector $sectorId • ${nowText()}"
                    updateJupiterStatus(
                        if (isSuccess) "Success" else "Failed",
                        detail,
                        isSuccess
                    )
                }

                tjlabsResourceManager.loadMapResource(application, ResourceRegion.KOREA.value, sectorId) {
                        isSuccess ->
                    TJLogger.d("loadMapResource(map) : $isSuccess")
                    val detail = "Sector $sectorId • ${nowText()}"
                    updateMapStatus(
                        if (isSuccess) "Success" else "Failed",
                        detail,
                        isSuccess
                    )
                }
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
        TJLogger.d("onSectorData : $data")
        populateSourceHints(data)
        appendCallbackLog("onSectorData", "sectorId=${data.id} buildings=${data.buildings.size}", "api")

    }

    override fun onSectorError(error: ResourceError) {
        TJLogger.d("onSectorError : $error")
        appendCallbackLog("onSectorError", "error=$error", "api")
    }

    override fun onBuildingsData(data: List<BuildingOutput>) {
        TJLogger.d("onBuildingsData : $data")
        appendCallbackLog("onBuildingsData", "count=${data.size}", "api")
    }

    override fun onLevelWardsData(levelKey: String, data: List<String>) {
        TJLogger.d("onLevelWardsData : $levelKey // data : $data")
        appendCallbackLog("onLevelWardsData", "key=$levelKey wards=${data.size}", "api")
    }

    override fun onScaleOffsetData(scaleKey: String, data: List<Float>) {
        TJLogger.d("onScaleOffsetData : $scaleKey // data : $data")
        appendCallbackLog("onScaleOffsetData", "key=$scaleKey size=${data.size}", "api")
    }

    override fun onPathPixelData(pathPixelKey: String, data: PathPixelData) {
        TJLogger.d("onPathPixelData : $pathPixelKey // data : $data")
        val source = pathPixelSourceHint[pathPixelKey] ?: "api"
        appendCallbackLog(
            "onPathPixelData",
            "key=$pathPixelKey nodes=${data.nodeNumber.size} roadPts=${data.road.firstOrNull()?.size ?: 0}",
            source
        )
    }

    override fun onGeofenceData(geofenceKey: String, data: GeofenceData) {
        TJLogger.d("onGeofenceData : $geofenceKey // data : $data")
        appendCallbackLog("onGeofenceData", "key=$geofenceKey", "api")
    }

    override fun onEntranceData(entranceKey: String, data: EntranceData) {
        TJLogger.d("onEntranceData : $entranceKey // data : $data")
        appendCallbackLog(
            "onEntranceData",
            "key=$entranceKey number=${data.number}",
            "api"
        )
    }

    override fun onEntranceRouteData(entranceKey: String, data: EntranceRouteData) {
        TJLogger.d("onEntranceRouteData : $entranceKey // data : $data")
        appendCallbackLog(
            "onEntranceRouteData",
            "key=$entranceKey routeLevels=${data.routeLevel.size}",
            "api"
        )
    }

    override fun onSectorParamData(data: SectorParameterOutput) {
        TJLogger.d("onSectorParamData data $data")
        appendCallbackLog("onSectorParamData", "min=${data.standard_min_rssi} max=${data.standard_max_rssi}", "api")
    }

    override fun onLevelParamData(paramKey: String, data: LevelParameterOutput) {
        TJLogger.d("onLevelParamData type $paramKey // $data")
        appendCallbackLog("onLevelParamData", "key=$paramKey", "api")
    }

    override fun onBuildingLevelImageData(imageKey: String, data: Bitmap?) {
        TJLogger.d("onBuildingLevelImageData imageKey $imageKey // $data")
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
        TJLogger.d("onUnitData unitKey $unitKey // data : $data")
        appendCallbackLog(
            "onLevelUnitsData",
            "key=$unitKey count=${data?.size ?: 0}",
            "api"
        )
    }

    override fun onAffineData(sectorId: Int, data: AffineTransParamOutput) {
        TJLogger.d("onAffineData sectorId $sectorId // data : $data")
        appendCallbackLog("onAffineData", "sectorId=$sectorId", "api")
    }

    override fun onLandmarkData(key: String, data: Map<String, LandmarkData>) {
        TJLogger.d("onLandmarkData key $key // data : $data")
        appendCallbackLog("onLandmarkData", "key=$key count=${data.size}", "asset")
    }

    override fun onSpotsData(key: Int, type: SpotType, data: Any) {
        TJLogger.d("onLandmarkData key $key //type : $type // data : $data")
        appendCallbackLog(
            "onSpotsData",
            "key=$key type=$type count=${describeSize(data)}",
            "asset"
        )
    }

    override fun onNodeLinkData(key: String, type: NodeLinkType, data: Any) {
        TJLogger.d("onLandmarkData key $key // type : $type // data : $data")
        appendCallbackLog(
            "onNodeLinkData",
            "key=$key type=$type count=${describeSize(data)}",
            "asset"
        )
    }

    override fun onGraphNodesData(key: String, data: List<GraphLevelNode>) {
        appendCallbackLog(
            "onGraphNodesData",
            "key=$key count=${data.size}",
            "api"
        )
    }

    override fun onGraphLinksData(key: String, data: List<GraphLevelLink>) {
        appendCallbackLog(
            "onGraphLinksData",
            "key=$key count=${data.size}",
            "api"
        )
    }

    override fun onGraphLinkGroupsData(key: String, data: List<GraphLevelLinkGroup>) {
        appendCallbackLog(
            "onGraphLinkGroupsData",
            "key=$key count=${data.size}",
            "api"
        )
    }

    override fun onGraphPathsData(key: String, data: List<GraphLevelPath>) {
        appendCallbackLog(
            "onGraphPathsData",
            "key=$key count=${data.size}",
            "api"
        )
    }

    override fun onGraphError(key: String, type: GraphResourceType) {
        appendCallbackLog(
            "onGraphError",
            "key=$key type=$type",
            "api"
        )
    }

    override fun onError(error: ResourceError, key: String) {
        TJLogger.d("onError : $error // key : $key")
        appendCallbackLog("onError", "error=$error key=$key", "api")
    }

    private fun populateSourceHints(data: SectorOutput) {
        for (building in data.buildings) {
            for (level in building.levels) {
                if (level.name.contains("_D")) continue

                val key = "${data.id}_${building.name}_${level.name}"
                imageUrlByKey[key] = level.image
                val imageCached = TJLabsImageCacheManager.getInstance().getBitmap(level.image) != null
                imageSourceHint[key] = if (imageCached) "cache(memory)" else "api"

                val pathPixelCached = hasPathPixelDiskCache(key)
                pathPixelSourceHint[key] = if (pathPixelCached) "cache(disk)" else "api"
            }
        }
    }

    private fun hasPathPixelDiskCache(key: String): Boolean {
        val prefs = getSharedPreferences("TJLabsResourcesPref", MODE_PRIVATE)
        val urlKey = "TJLabsPathPixelURL_$key"
        val dirKey = "TJLabsPathPixelDir_$key"
        val cachedUrl = prefs.getString(urlKey, null)
        val cachedDir = prefs.getString(dirKey, null)
        if (cachedUrl.isNullOrEmpty() || cachedDir.isNullOrEmpty()) return false
        val file = File(cachedDir)
        return file.exists() && file.length() > 0
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

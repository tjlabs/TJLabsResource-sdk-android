package com.tjlabs.sdk_sample_app

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.tjlabs.tjlabsauth_sdk_android.TJLabsAuthManager
import com.tjlabs.tjlabsresource_sdk_android.*
import com.tjlabs.tjlabsresource_sdk_android.util.TJLogger

class MainActivity : AppCompatActivity(), TJLabsResourceManagerDelegate {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val tenantId = "tjlabs"
        val tenantPw = "TJlabs0407@"
        val sectorId = 6

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        TJLabsAuthManager.initialize(application)
        TJLabsAuthManager.auth(tenantId, tenantPw) {
                code, success->
            Log.d("CheckToken", "code : $code // success : $success")

            if (success) {
                val tjlabsResourceManager = TJLabsResourceManager()
                tjlabsResourceManager.delegate = this
                tjlabsResourceManager.setDebugOption(true)
                tjlabsResourceManager.loadJupiterResource(application, ResourceRegion.KOREA.value, sectorId) {
                        isSuccess ->
                    TJLogger.d("loadJupiterResource(jupiter): $isSuccess")

                }
                tjlabsResourceManager.loadMapResource(application, ResourceRegion.KOREA.value, sectorId){
                        isSuccess ->
                    TJLogger.d("loadMapResource(map) : $isSuccess")
                }
            }
        }
    }

    override fun onSectorData(data: SectorOutput) {
        TJLogger.d("onSectorData : $data")

    }

    override fun onSectorError(error: ResourceError) {
        TJLogger.d("onSectorError : $error")
    }

    override fun onBuildingsData(data: List<BuildingOutput>) {
        TJLogger.d("onBuildingsData : $data")
    }

    override fun onLevelWardsData(levelKey: String, data: List<String>) {
        TJLogger.d("onLevelWardsData : $levelKey // data : $data")
    }

    override fun onScaleOffsetData(scaleKey: String, data: List<Float>) {
        TJLogger.d("onScaleOffsetData : $scaleKey // data : $data")
    }

    override fun onPathPixelData(pathPixelKey: String, data: PathPixelData) {
        TJLogger.d("onPathPixelData : $pathPixelKey // data : $data")
    }

    override fun onGeofenceData(geofenceKey: String, data: GeofenceData) {
        TJLogger.d("onGeofenceData : $geofenceKey // data : $data")
    }

    override fun onEntranceData(entranceKey: String, data: EntranceData) {
        TJLogger.d("onEntranceData : $entranceKey // data : $data")
    }

    override fun onEntranceRouteData(entranceKey: String, data: EntranceRouteData) {
        TJLogger.d("onEntranceRouteData : $entranceKey // data : $data")
    }

    override fun onSectorParamData(data: SectorParameterOutput) {
        TJLogger.d("onSectorParamData data $data")
    }

    override fun onLevelParamData(paramKey: String, data: LevelParameterOutput) {
        TJLogger.d("onLevelParamData type $paramKey // $data")
    }

    override fun onBuildingLevelImageData(imageKey: String, data: Bitmap?) {
        TJLogger.d("onBuildingLevelImageData imageKey $imageKey // $data")
    }

    override fun onUnitData(unitKey: String, data: List<UnitData>?) {
        TJLogger.d("onUnitData unitKey $unitKey // data : $data")
    }

    override fun onAffineData(sectorId: Int, data: AffineTransParamOutput) {
        TJLogger.d("onAffineData sectorId $sectorId // data : $data")
    }

    override fun onLandmarkData(key: String, data: Map<String, LandmarkData>) {
        TJLogger.d("onLandmarkData key $key // data : $data")
    }

    override fun onSpotsData(key: Int, type: SpotType, data: Any) {
        TJLogger.d("onLandmarkData key $key //type : $type // data : $data")
    }

    override fun onNodeLinkData(key: String, type: NodeLinkType, data: Any) {
        TJLogger.d("onLandmarkData key $key // type : $type // data : $data")
    }

    override fun onError(error: ResourceError, key: String) {
        TJLogger.d("onError : $error // key : $key")
    }
}
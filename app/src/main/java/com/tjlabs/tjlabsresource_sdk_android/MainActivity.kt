package com.tjlabs.tjlabsresource_sdk_android

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.tjlabs.tjlabsauth_sdk_android.TJLabsAuthManager
import com.tjlabs.tjlabsresource_sdk_android.manager.EntranceErrorType
import com.tjlabs.tjlabsresource_sdk_android.manager.ParamErrorType
import com.tjlabs.tjlabsresource_sdk_android.manager.TJLabsResourceManager
import com.tjlabs.tjlabsresource_sdk_android.util.Logger

class MainActivity : AppCompatActivity(), TJLabsResourceManagerDelegate {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val tenantId = "tjlabs"
        val tenantPw = "TJlabs0407@"

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // sector id 1 = Tips town
        // coex id 7
        TJLabsAuthManager.initialize(application)
        TJLabsAuthManager.setServerURL("jupiter")
        TJLabsAuthManager.auth(tenantId, tenantPw) {
                code, success->
            if (success) {
                val tjlabsResourceManager = TJLabsResourceManager()
                tjlabsResourceManager.delegate = this
                tjlabsResourceManager.setDebugOption(true)
//                tjlabsResourceManager.loadJupiterResource(application, ResourceRegion.KOREA.value, 7)

                tjlabsResourceManager.loadMapResource(application, ResourceRegion.KOREA.value, 7)
            }
        }
    }

    override fun onSectorData(data: SectorOutput) {
        Logger.d("onSectorData : $data")

    }

    override fun onSectorError() {
        Logger.d("onSectorError")

    }

    override fun onBuildingsData(data: List<BuildingOutput>) {
         Logger.d("onBuildingsData : $data")
    }

    override fun onScaleOffsetData(scaleKey: String, data: List<Float>) {
        Logger.d("onScaleOffsetData : $scaleKey // data : $data")
    }

    override fun onScaleOffsetError(scaleKey: String) {
        Logger.d("onScaleOffsetError : $scaleKey")
    }

    override fun onPathPixelData(pathPixelKey: String, data: PathPixelData) {
        Logger.d("onPathPixelData : $pathPixelKey // data : $data")
    }

    override fun onPathPixelError(pathPixelKey: String) {
        Logger.d("onPathPixelData : $pathPixelKey")
    }

    override fun onGeofenceData(geofenceKey: String, data: GeofenceData) {
        Logger.d("onGeofenceData : $geofenceKey // data : $data")
    }

    override fun onGeofenceError(geofenceKey: String) {
        Logger.d("onGeofenceError : $geofenceKey")
    }

    override fun onEntranceData(entranceKey: String, data: EntranceData) {
        Logger.d("onEntranceData : $entranceKey // data : $data")
    }

    override fun onEntranceRouteData(entranceKey: String, data: EntranceRouteData) {
        Logger.d("onEntranceRouteData : $entranceKey // data : $data")
    }

    override fun onEntranceError(type: EntranceErrorType, entranceKey: String) {
        Logger.d("onEntranceError type $type // $entranceKey")
    }

    override fun onSectorParamData(data: SectorParameterOutput) {
        Logger.d("onSectorParamData data $data")
    }

    override fun onLevelParamData(paramKey: String, data: LevelParameterOutput) {
        Logger.d("onLevelParamData type $paramKey // $data")
    }

    override fun onParamError(type: ParamErrorType, paramKey: String?) {
        Logger.d("onParamError type $type // $paramKey")
    }

    override fun onBuildingLevelImageData(imageKey: String, data: Bitmap?) {
        Logger.d("onBuildingLevelImageData imageKey $imageKey // $data")
    }

    override fun onBuildingLevelImageError(imageKey: String) {
        Logger.d("onBuildingLevelImageData imageKey $imageKey")
    }

    override fun onUnitData(unitKey: String, data: List<UnitData>?) {
        Logger.d("onUnitData unitKey $unitKey // data : $data")
    }

    override fun onUnitDataError(unitKey: String) {
        Logger.d("onUnitDataError unitKey $unitKey")
    }
}
package com.tjlabs.tjlabsresource_sdk_android

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.tjlabs.tjlabsauth_sdk_android.TJLabsAuthManager
import com.tjlabs.tjlabsresource_sdk_android.manager.EntranceErrorType
import com.tjlabs.tjlabsresource_sdk_android.manager.ParamErrorType
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
        // commiro id 58
        TJLabsAuthManager.initialize(application)
        TJLabsAuthManager.setServerURL("jupiter")
        TJLabsAuthManager.auth(tenantId, tenantPw) {
                code, success->
            if (success) {
                val tjlabsResourceManager = TJLabsResourceManager()
                tjlabsResourceManager.delegate = this
                tjlabsResourceManager.setDebugOption(true)
//                tjlabsResourceManager.loadJupiterResource(application, ResourceRegion.KOREA.value, 7)

                tjlabsResourceManager.loadMapResource(application, ResourceRegion.KOREA.value, 58)
            }
        }
    }

    override fun onSectorData(data: SectorOutput) {
        Logger.d("onSectorData : $data")

    }

    override fun onSectorError(error: ResourceError) {
        Logger.d("onSectorError : $error")
    }

    override fun onBuildingsData(data: List<BuildingOutput>) {
         Logger.d("onBuildingsData : $data")
    }

    override fun onScaleOffsetData(scaleKey: String, data: List<Float>) {
        Logger.d("onScaleOffsetData : $scaleKey // data : $data")
    }

    override fun onPathPixelData(pathPixelKey: String, data: PathPixelData) {
        Logger.d("onPathPixelData : $pathPixelKey // data : $data")
    }

    override fun onGeofenceData(geofenceKey: String, data: GeofenceData) {
        Logger.d("onGeofenceData : $geofenceKey // data : $data")
    }

    override fun onEntranceData(entranceKey: String, data: EntranceData) {
        Logger.d("onEntranceData : $entranceKey // data : $data")
    }

    override fun onEntranceRouteData(entranceKey: String, data: EntranceRouteData) {
        Logger.d("onEntranceRouteData : $entranceKey // data : $data")
    }

    override fun onSectorParamData(data: SectorParameterOutput) {
        Logger.d("onSectorParamData data $data")
    }

    override fun onLevelParamData(paramKey: String, data: LevelParameterOutput) {
        Logger.d("onLevelParamData type $paramKey // $data")
    }

    override fun onBuildingLevelImageData(imageKey: String, data: Bitmap?) {
        Logger.d("onBuildingLevelImageData imageKey $imageKey // $data")
    }

    override fun onUnitData(unitKey: String, data: List<UnitData>?) {
        Logger.d("onUnitData unitKey $unitKey // data : $data")
    }

    override fun onError(error: ResourceError, key: String) {
        Logger.d("onError : $error // key : $key")
    }
}
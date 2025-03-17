package com.tjlabs.tjlabsresource_sdk_android

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity(), TJLabsResourceManagerDelegate {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val tjlabsResourceManager = TJLabsResourceManager()
        tjlabsResourceManager.delegate = this
        tjlabsResourceManager.loadJupiterResources(application, ResourceRegion.KOREA, 6)
    }

    override fun onBuildingLevelData(
        isOn: Boolean,
        buildingLevelData: Map<String, List<String>>
    ) {

        Log.d(TAG, "isOn : $isOn // buildingLevelData : $buildingLevelData")

    }

    override fun onPathPixelData(
        isOn: Boolean,
        pathPixelKey: String,
        data: PathPixelData?
    ) {
        Log.d(TAG, "isOn : $isOn // pathPixelKey : $pathPixelKey")

    }

    override fun onBuildingLevelImageData(
        isOn: Boolean,
        imageKey: String,
        data: Bitmap?
    ) {
        Log.d(TAG, "isOn : $isOn // imageKey : $imageKey // data : $data")

    }

    override fun onScaleOffsetData(
        isOn: Boolean,
        scaleKey: String,
        data: List<Float>
    ) {
        Log.d(TAG, "isOn : $isOn // scaleKey : $scaleKey")

    }

    override fun onEntranceData(
        isOn: Boolean,
        entranceKey: String,
        data: EntranceRouteData?
    ) {
        Log.d(TAG, "isOn : $isOn // entranceKey : $entranceKey")

    }

    override fun onUnitData(isOn: Boolean, unitKey: String, data: List<UnitData>?) {
        Log.d(TAG, "isOn : $isOn // unit : $data")
    }

    override fun onParamData(isOn: Boolean, data: ParameterData?) {
        Log.d(TAG, "isOn : $isOn // param : $data")
    }

    override fun onEntranceAreaData(
        isOn: Boolean,
        key: String,
        data: List<List<Float>>?
    ) {
        Log.d(TAG, "isOn : $isOn // entrance : $data // key : $key")
    }

    override fun onEntranceMatchingAreaData(
        isOn: Boolean,
        key: String,
        data: List<List<Float>>?
    ) {
        Log.d(TAG, "isOn : $isOn // entrance matching : $data // key : $key")
    }

    override fun onLevelChangeArea(
        isOn: Boolean,
        key: String,
        data: List<List<Float>>?
    ) {
        Log.d(TAG, "isOn : $isOn // level change : $data // key :$key")
    }

    override fun onDrModeArea(isOn: Boolean, key: String, data: DrModeArea?) {
        Log.d(TAG, "isOn : $isOn // dr mode: $data // key : $key")
    }

    override fun onError(error: ResourceError) {
        Log.d(TAG, "error // error : $error")

    }

}
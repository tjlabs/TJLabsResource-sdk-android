package com.tjlabs.tjlabsresource_sdk_android

import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val tjLabsResourceManager = TJLabsResourceManager(application)
        tjLabsResourceManager.updateResources(TJLabsResourceManager.REGION_KOREA, 6) {
            isSucess, msg ->
            if (isSucess) {
                val temp = tjLabsResourceManager.returnPathPixelData()
                Log.d("PathPixelDataCheck", temp.keys.toString())
                Log.d("PathPixelDataCheck", temp.toString())
            }
        }


    }
}
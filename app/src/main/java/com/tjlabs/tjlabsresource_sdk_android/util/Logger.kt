package com.tjlabs.tjlabsresource_sdk_android.util

import android.util.Log

internal object Logger {
    private const val DEFAULT_TAG = "TJLabsResourceManager"
    private var isDebugOption = false

    fun setDebugOption(set : Boolean) {
        isDebugOption = set
    }

    fun d(message: String) {
        if (isDebugOption) {
            Log.d(DEFAULT_TAG, message)
        }
    }

    fun e(message: String) {
        if (isDebugOption) {
            Log.e(DEFAULT_TAG, message)
        }
    }

    fun i(message: String) {
        if (isDebugOption) {
            Log.i(DEFAULT_TAG, message)
        }
    }

    fun w(message: String) {
        if (isDebugOption) {
            Log.w(DEFAULT_TAG, message)
        }
    }
}
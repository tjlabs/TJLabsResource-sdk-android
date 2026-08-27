package com.tjlabs.tjlabsresource_sdk_android.util

import android.util.Log

object TJResourceLogger {
    @PublishedApi internal const val DEFAULT_TAG = "TJLabsResourceManager"
    @Volatile private var isDebugOption = false

    fun setDebugOption(set : Boolean) {
        isDebugOption = set
    }

    fun isDebugEnabled(): Boolean = isDebugOption

    fun d(message: String) {
        if (isDebugOption) {
            Log.d(DEFAULT_TAG, message)
        }
    }

    inline fun d(lazyMessage: () -> String) {
        if (isDebugEnabled()) {
            Log.d(DEFAULT_TAG, lazyMessage())
        }
    }

    fun e(message: String) {
        if (isDebugOption) {
            Log.e(DEFAULT_TAG, message)
        }
    }

    inline fun e(lazyMessage: () -> String) {
        if (isDebugEnabled()) {
            Log.e(DEFAULT_TAG, lazyMessage())
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

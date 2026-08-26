package com.tjlabs.tjlabsresource_sdk_android.util

import android.util.Log

object TJResourceLogger {
    @PublishedApi internal const val DEFAULT_TAG = "TJLabsResourceManager"
    @Volatile private var isDebugOption = false

    /** 외부 소비자 (예: hana-sdk 데모앱) 가 로그를 화면에 표시하기 위해 설치하는 sink. */
    fun interface Sink { fun onLog(level: String, tag: String, message: String) }
    @Volatile @PublishedApi internal var externalSink: Sink? = null

    fun setDebugOption(set : Boolean) {
        isDebugOption = set
    }

    /**
     * 로그 sink 등록. null 이면 해제. debug option 과 독립적으로 동작 —
     * 즉 setDebugOption(false) 여도 sink 는 여전히 로그를 받는다.
     */
    fun setSink(sink: Sink?) {
        externalSink = sink
    }

    fun isDebugEnabled(): Boolean = isDebugOption

    fun d(message: String) {
        if (isDebugOption) Log.d(DEFAULT_TAG, message)
        externalSink?.onLog("D", DEFAULT_TAG, message)
    }

    inline fun d(lazyMessage: () -> String) {
        if (isDebugEnabled() || externalSink != null) {
            val msg = lazyMessage()
            if (isDebugEnabled()) Log.d(DEFAULT_TAG, msg)
            externalSink?.onLog("D", DEFAULT_TAG, msg)
        }
    }

    fun e(message: String) {
        if (isDebugOption) Log.e(DEFAULT_TAG, message)
        externalSink?.onLog("E", DEFAULT_TAG, message)
    }

    inline fun e(lazyMessage: () -> String) {
        if (isDebugEnabled() || externalSink != null) {
            val msg = lazyMessage()
            if (isDebugEnabled()) Log.e(DEFAULT_TAG, msg)
            externalSink?.onLog("E", DEFAULT_TAG, msg)
        }
    }

    fun i(message: String) {
        if (isDebugOption) Log.i(DEFAULT_TAG, message)
        externalSink?.onLog("I", DEFAULT_TAG, message)
    }

    fun w(message: String) {
        if (isDebugOption) Log.w(DEFAULT_TAG, message)
        externalSink?.onLog("W", DEFAULT_TAG, message)
    }
}

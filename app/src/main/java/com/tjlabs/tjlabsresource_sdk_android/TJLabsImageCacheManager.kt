package com.tjlabs.tjlabsresource_sdk_android

import android.graphics.Bitmap
import android.util.LruCache

class TJLabsImageCacheManager private constructor() {

    companion object {
        @Volatile
        private var INSTANCE: TJLabsImageCacheManager? = null

        fun getInstance(): TJLabsImageCacheManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TJLabsImageCacheManager().also { INSTANCE = it }
            }
        }
    }

    private val cache: LruCache<String, Bitmap> = LruCache(50) // 최대 50개의 이미지를 캐싱

    fun putBitmap(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }

    fun getBitmap(key: String): Bitmap? {
        return cache.get(key)
    }
}
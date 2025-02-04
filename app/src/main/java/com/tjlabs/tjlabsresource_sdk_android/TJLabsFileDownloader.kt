package com.tjlabs.tjlabsresource_sdk_android

import android.app.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

internal object TJLabsFileDownloader {
    suspend fun downloadCSVFile(application: Application, url: URL, region : String, sectorId: Int, fileName: String): Triple<File?, String, Exception?> =
        withContext(
            Dispatchers.IO
        ) {
            lateinit var outputFile: File
            lateinit var output: OutputStream
            val exception: Exception?

            try {
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                val input: InputStream = connection.inputStream

                val subFolder = File(application.cacheDir, "${region}_$sectorId")
                if (!subFolder.exists()) {
                    subFolder.mkdirs() // 하위 폴더 생성
                }

                outputFile = File(subFolder, fileName) // 캐시에 파일 생성
                output = FileOutputStream(outputFile)

                input.use {
                    output.use { output ->
                        input.copyTo(output)
                    }
                }
                Triple(outputFile, "${application.cacheDir}/${region}_$sectorId/$fileName", null)
            } catch (e: IOException) {
                exception = e
                Triple(null, "", exception)
            } finally {
                try {
                    output.close()
                } catch (e: IOException) {
                    // ignore
                }
            }
        }
}
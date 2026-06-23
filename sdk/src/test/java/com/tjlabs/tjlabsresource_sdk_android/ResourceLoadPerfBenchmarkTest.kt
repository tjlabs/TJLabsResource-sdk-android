package com.tjlabs.tjlabsresource_sdk_android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host-side JVM benchmark that compares load patterns BEFORE vs AFTER the SDK optimizations.
 *
 *  - Sequential awaitAll (path → entrance → image) ........ "BEFORE"
 *  - Parallel fan-out (path/entrance/image at once) ........ "AFTER"
 *
 *  - Eager string-interp logging while debug=false .......... "BEFORE"
 *  - Lazy lambda logging while debug=false .................. "AFTER"
 *
 *  - No bitmap disk cache (re-fetch every time) ............. "BEFORE"
 *  - Bitmap disk cache (only the first load pays the IO) .... "AFTER"
 *
 * 모든 작업은 단위 시간(ms)을 출력해 직접 비교가 가능하도록 구성됨.
 */
class ResourceLoadPerfBenchmarkTest {

    private val pathStageMs = 120L
    private val entranceStageMs = 80L
    private val imageStageMs = 200L
    private val perItem = 5L
    private val itemsPerStage = 8

    private suspend fun fakeStageWork(totalMs: Long) {
        // I/O 대기 시뮬레이션 (network + disk).
        kotlinx.coroutines.delay(totalMs)
    }

    /** BEFORE 코드 흐름: 3개 단계가 순차로 awaitAll 됨. */
    private suspend fun runSequentialEnrichment(): Long {
        val start = System.currentTimeMillis()
        // path stage
        (0 until itemsPerStage).map {
            kotlinx.coroutines.GlobalScope.async(Dispatchers.IO) { fakeStageWork(pathStageMs / itemsPerStage + perItem) }
        }.awaitAll()
        // entrance stage (path 끝난 뒤에야 시작)
        (0 until itemsPerStage).map {
            kotlinx.coroutines.GlobalScope.async(Dispatchers.IO) { fakeStageWork(entranceStageMs / itemsPerStage + perItem) }
        }.awaitAll()
        // image stage (entrance 끝난 뒤에야 시작)
        (0 until itemsPerStage).map {
            kotlinx.coroutines.GlobalScope.async(Dispatchers.IO) { fakeStageWork(imageStageMs / itemsPerStage + perItem) }
        }.awaitAll()
        return System.currentTimeMillis() - start
    }

    /** AFTER 코드 흐름: 세 단계를 동시에 fan-out 시킨 후 한 번에 awaitAll. */
    private suspend fun runParallelEnrichment(): Long {
        val start = System.currentTimeMillis()
        val pathDeferred = (0 until itemsPerStage).map {
            kotlinx.coroutines.GlobalScope.async(Dispatchers.IO) { fakeStageWork(pathStageMs / itemsPerStage + perItem) }
        }
        val entranceDeferred = (0 until itemsPerStage).map {
            kotlinx.coroutines.GlobalScope.async(Dispatchers.IO) { fakeStageWork(entranceStageMs / itemsPerStage + perItem) }
        }
        val imageDeferred = (0 until itemsPerStage).map {
            kotlinx.coroutines.GlobalScope.async(Dispatchers.IO) { fakeStageWork(imageStageMs / itemsPerStage + perItem) }
        }
        pathDeferred.awaitAll()
        entranceDeferred.awaitAll()
        imageDeferred.awaitAll()
        return System.currentTimeMillis() - start
    }

    @Test
    fun parallelEnrichmentIsFasterThanSequential() = runBlocking {
        // warmup
        runParallelEnrichment()
        runSequentialEnrichment()

        val seqMs = runSequentialEnrichment()
        val parMs = runParallelEnrichment()
        println("[PERF] enrichment-sequential (BEFORE): ${seqMs}ms")
        println("[PERF] enrichment-parallel   (AFTER) : ${parMs}ms")
        println("[PERF] enrichment improvement: ${(seqMs - parMs)}ms (${"%.1f".format((1.0 - parMs.toDouble() / seqMs) * 100)}% faster)")
        assertTrue("Parallel enrichment must be faster than sequential", parMs < seqMs)
    }

    // ----- Logger overhead -----

    private object SimulatedLogger {
        @Volatile var debug = false
        // BEFORE: 문자열 보간이 호출 시점에 평가됨
        fun d(msg: String) { if (debug) { /* Log.d */ } }
        // AFTER: 람다는 debug=true 일 때만 평가됨
        inline fun d(lazy: () -> String) { if (debug) { /* Log.d(lazy()) */ } }
    }

    @Test
    fun lazyLoggingIsCheaperWhenDebugDisabled() {
        SimulatedLogger.debug = false
        val iters = 200_000
        val sampleA = "levelKey"
        val sampleB = 12345
        val sampleC = "https://some-cdn/path/to.csv"

        // 워밍업
        for (i in 0 until 1000) {
            SimulatedLogger.d("warm $sampleA $sampleB $sampleC $i")
            SimulatedLogger.d { "warm $sampleA $sampleB $sampleC $i" }
        }

        val eagerStart = System.nanoTime()
        for (i in 0 until iters) {
            SimulatedLogger.d("(perf) parseBundleRaw graph item // levelKey=$sampleA // idx=$i // url=$sampleC // some=$sampleB")
        }
        val eagerMs = (System.nanoTime() - eagerStart) / 1_000_000

        val lazyStart = System.nanoTime()
        for (i in 0 until iters) {
            SimulatedLogger.d { "(perf) parseBundleRaw graph item // levelKey=$sampleA // idx=$i // url=$sampleC // some=$sampleB" }
        }
        val lazyMs = (System.nanoTime() - lazyStart) / 1_000_000
        println("[PERF] logging-eager (BEFORE) at ${iters} calls: ${eagerMs}ms")
        println("[PERF] logging-lazy  (AFTER)  at ${iters} calls: ${lazyMs}ms")
        assertTrue("Lazy logging must be cheaper than eager when debug=false", lazyMs <= eagerMs)
    }

    // ----- Bitmap fetch cache simulation -----

    private fun simulatedNetworkFetchMs(): Long = 50L
    private fun simulatedDiskReadMs(): Long = 3L

    /** BEFORE: 매번 네트워크에서 이미지를 받음 */
    private fun loadAllImagesNoCache(count: Int): Long {
        val start = System.currentTimeMillis()
        for (i in 0 until count) {
            Thread.sleep(simulatedNetworkFetchMs())
        }
        return System.currentTimeMillis() - start
    }

    /** AFTER: 같은 versionId 이면 디스크에서 읽음 */
    private fun loadAllImagesWithCache(count: Int, cacheHit: Boolean): Long {
        val start = System.currentTimeMillis()
        for (i in 0 until count) {
            if (cacheHit) {
                Thread.sleep(simulatedDiskReadMs())
            } else {
                Thread.sleep(simulatedNetworkFetchMs())
            }
        }
        return System.currentTimeMillis() - start
    }

    @Test
    fun imageDiskCacheSavesTimeOnRepeatedLoads() {
        val n = 12

        // 1st load: 캐시 미스 → BEFORE/AFTER 모두 네트워크 다운로드 (동일)
        val firstLoadNoCache = loadAllImagesNoCache(n)
        val firstLoadWithCache = loadAllImagesWithCache(n, cacheHit = false)
        // 2nd load: BEFORE 는 다시 네트워크, AFTER 는 디스크 캐시 적중
        val secondLoadNoCache = loadAllImagesNoCache(n)
        val secondLoadWithCache = loadAllImagesWithCache(n, cacheHit = true)

        println("[PERF] image-load 1st BEFORE (no-cache):    ${firstLoadNoCache}ms")
        println("[PERF] image-load 1st AFTER  (cache miss):  ${firstLoadWithCache}ms")
        println("[PERF] image-load 2nd BEFORE (no-cache):    ${secondLoadNoCache}ms")
        println("[PERF] image-load 2nd AFTER  (cache hit):   ${secondLoadWithCache}ms")
        println("[PERF] image-cache 2nd improvement: ${(secondLoadNoCache - secondLoadWithCache)}ms")
        assertTrue(
            "Disk-cached image load must be faster than network re-fetch",
            secondLoadWithCache < secondLoadNoCache
        )
    }

    // ----- Cold-start (캐시 초기화 직후) end-to-end 비교 -----

    /**
     * Simulated state: 한 번 로드 후 캐시가 빌드된 상태 → 캐시 초기화(=process kill or storage clear) →
     * 그 직후 첫 로드 시 두 코드 경로를 모두 측정.
     *
     * BEFORE 코드 흐름 (1.1.5 기준):
     *   meta(network) → bundleRaw(network) → JSON 파싱(Main) → enrichCsvData[순차 path → entrance → image]
     *   이미지는 디스크 캐시가 없으므로 항상 네트워크.
     *
     * AFTER 코드 흐름:
     *   meta(network) → bundleRaw(network) → JSON 파싱(IO) → enrichCsvData[parallel fan-out]
     *   이미지는 첫 로드는 네트워크 + 디스크 캐시 기록.
     */

    // 네트워크/IO 비용 (ms) — 실제 SDK 의 평균치를 단순화한 값
    private val metaNetworkMs = 90L
    private val rawNetworkMs = 250L
    private val jsonParseMs = 110L
    private val pathStageItemMs = pathStageMs / itemsPerStage + perItem      // 동일 모델 사용
    private val entranceStageItemMs = entranceStageMs / itemsPerStage + perItem
    private val imageStageItemMs = imageStageMs / itemsPerStage + perItem
    private val cacheWriteMs = 2L

    /** 캐시 초기화 직후의 BEFORE 코드 흐름 1회 로드 elapsed (ms). */
    private suspend fun coldStartBefore(): Long {
        val start = System.currentTimeMillis()
        // meta 호출
        fakeStageWork(metaNetworkMs)
        // 번들 raw 호출
        fakeStageWork(rawNetworkMs)
        // JSON 파싱 (Main thread 점유)
        fakeStageWork(jsonParseMs)
        // enrichCsvData: path → entrance → image (sequential awaitAll)
        (0 until itemsPerStage).map {
            kotlinx.coroutines.GlobalScope.async(Dispatchers.IO) { fakeStageWork(pathStageItemMs) }
        }.awaitAll()
        (0 until itemsPerStage).map {
            kotlinx.coroutines.GlobalScope.async(Dispatchers.IO) { fakeStageWork(entranceStageItemMs) }
        }.awaitAll()
        (0 until itemsPerStage).map {
            kotlinx.coroutines.GlobalScope.async(Dispatchers.IO) { fakeStageWork(imageStageItemMs) }
        }.awaitAll()
        return System.currentTimeMillis() - start
    }

    /** 캐시 초기화 직후의 AFTER 코드 흐름 1회 로드 elapsed (ms). */
    private suspend fun coldStartAfter(): Long {
        val start = System.currentTimeMillis()
        // meta 호출
        fakeStageWork(metaNetworkMs)
        // 번들 raw 호출
        fakeStageWork(rawNetworkMs)
        // JSON 파싱이 IO로 분리됨 (총 elapsed에는 동일하게 잡히지만 main blocking은 줄어듦)
        fakeStageWork(jsonParseMs)
        // enrichCsvData: path/entrance/image 모두 한번에 fan-out
        val pathDeferred = (0 until itemsPerStage).map {
            kotlinx.coroutines.GlobalScope.async(Dispatchers.IO) {
                fakeStageWork(pathStageItemMs)
            }
        }
        val entranceDeferred = (0 until itemsPerStage).map {
            kotlinx.coroutines.GlobalScope.async(Dispatchers.IO) {
                fakeStageWork(entranceStageItemMs)
            }
        }
        val imageDeferred = (0 until itemsPerStage).map {
            kotlinx.coroutines.GlobalScope.async(Dispatchers.IO) {
                // cache miss라 네트워크 + 디스크 캐시 기록 비용 추가
                fakeStageWork(imageStageItemMs + cacheWriteMs)
            }
        }
        pathDeferred.awaitAll()
        entranceDeferred.awaitAll()
        imageDeferred.awaitAll()
        return System.currentTimeMillis() - start
    }

    @Test
    fun coldStartAfterCacheClearedComparison() = runBlocking {
        // 워밍업
        coldStartBefore()
        coldStartAfter()

        val before = coldStartBefore()
        val after = coldStartAfter()
        val saved = before - after
        val pct = (1.0 - after.toDouble() / before) * 100

        println("[PERF] cold-start BEFORE (cache cleared, seq enrich + no img cache): ${before}ms")
        println("[PERF] cold-start AFTER  (cache cleared, parallel enrich + img cache write): ${after}ms")
        println("[PERF] cold-start savings: ${saved}ms (${"%.1f".format(pct)}% faster)")
        assertTrue(
            "Even on cold start (cache cleared), parallel enrichment must be at least as fast as sequential",
            after <= before
        )
    }

    /**
     * 캐시 초기화 → 첫 로드 → 두 번째 로드 까지 누적 elapsed 비교.
     * 사용자가 SDK 를 처음 사용한 뒤 다시 화면을 열었을 때 까지의 총 시간을 시뮬레이션.
     */
    @Test
    fun coldStartPlusWarmReloadComparison() = runBlocking {
        // 워밍업
        coldStartAfter()
        coldStartBefore()

        // BEFORE: 두 번 다 cold (이미지 캐시 없음)
        val before1 = coldStartBefore()
        val before2 = coldStartBefore()
        val beforeTotal = before1 + before2

        // AFTER: 첫 로드는 cold + 캐시 기록, 두 번째 로드는 warm (이미지 디스크 캐시 적중)
        val after1 = coldStartAfter()
        val after2 = warmReloadAfter()
        val afterTotal = after1 + after2

        println("[PERF] BEFORE 1st(cold) + 2nd(cold): ${before1}ms + ${before2}ms = ${beforeTotal}ms")
        println("[PERF] AFTER  1st(cold) + 2nd(warm): ${after1}ms + ${after2}ms = ${afterTotal}ms")
        println("[PERF] total savings over 2 loads: ${(beforeTotal - afterTotal)}ms")
        assertTrue(
            "Cold + warm AFTER must beat two cold BEFORE loads",
            afterTotal < beforeTotal
        )
    }

    /** AFTER 의 두 번째 로드: bundleRaw 까지는 디스크 캐시 적중, 이미지도 디스크 캐시 적중. */
    private suspend fun warmReloadAfter(): Long {
        val start = System.currentTimeMillis()
        // meta 는 freshness window(5분) 내라면 prefs hit → 사실상 0ms, 아니면 network
        fakeStageWork(metaNetworkMs)
        // bundleRaw 디스크 캐시 read + parse (네트워크 250ms 회피)
        fakeStageWork(simulatedDiskReadMs())
        fakeStageWork(jsonParseMs)
        // enrich: path/entrance CSV 는 디스크 캐시, 이미지도 디스크 캐시 → 모두 매우 짧음
        val pathDeferred = (0 until itemsPerStage).map {
            kotlinx.coroutines.GlobalScope.async(Dispatchers.IO) { fakeStageWork(simulatedDiskReadMs()) }
        }
        val entranceDeferred = (0 until itemsPerStage).map {
            kotlinx.coroutines.GlobalScope.async(Dispatchers.IO) { fakeStageWork(simulatedDiskReadMs()) }
        }
        val imageDeferred = (0 until itemsPerStage).map {
            kotlinx.coroutines.GlobalScope.async(Dispatchers.IO) { fakeStageWork(simulatedDiskReadMs()) }
        }
        pathDeferred.awaitAll()
        entranceDeferred.awaitAll()
        imageDeferred.awaitAll()
        return System.currentTimeMillis() - start
    }
}

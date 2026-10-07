package com.tjlabs.tjlabsresource_sdk_android

import android.app.Application
import com.tjlabs.tjlabsresource_sdk_android.manager.TJLabsBundleDataManager
import com.tjlabs.tjlabsresource_sdk_android.util.TJResourceLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Resource SDK 성능 측정 — iOS 문서 §9 "성능 측정" 테이블과 동일한 포맷의 "개선 전/후" 수치를
 * **단일 APK 안에서** 얻기 위한 벤치 API.
 *
 * ── 측정 메커니즘
 * 1. [CsvParserMode.LEGACY_REGEX] 로 지정하면 Phase 4 이전의 regex-기반
 *    [TJLabsBundleDataManager.parsePathPixelDataLegacy] 를 호출하도록 전역 flag 를 세팅.
 *    SINGLE_PASS 는 현재 production 파서 [TJLabsBundleDataManager.parsePathPixelData].
 * 2. 벤치 시작 시 [BenchmarkTimingCollector] 를 enable 하고 Multi 로더가 각 단계 (zip extract,
 *    bundle.json decode, path CSV parse) 끝날 때마다 nanoTime 차이를 record. 모드 간 비교는
 *    같은 메서드/같은 sectorIds 로 **cold load N 회** 반복해 평균/median/p95/stddev 산출.
 * 3. 벤치 종료 후 flag 와 collector 를 reset. production 흐름 영향 없음.
 *
 * ── 중요
 *  - 네트워크 jitter 영향을 줄이려면 벤치 전 forceUpdate=true 로 cache 를 미리 채우고
 *    (metadata+zip 다운로드 비용 제외), cold load 는 extract/parse 비용에 집중.
 *  - 메인 스레드 점유 비교는 Android/iOS 스레딩 모델 차이로 1:1 매핑 불가 — Android 는
 *    "cold load wall-clock" + "stage breakdown" 조합으로 iOS 테이블과 대조.
 */
object TJLabsResourceBenchmark {

    /** 벤치가 토글하는 CSV 파서 선택지. iOS 테이블의 "개선 전/후" 축에 매핑. */
    enum class CsvParserMode { SINGLE_PASS, LEGACY_REGEX }

    /** 단일 cold-load 1회의 단계별 timing (iOS §9 테이블 row 매핑). 모든 값 ms. */
    data class StageTimings(
        /** zip 다운로드 끝난 후 메모리 extract 완료까지 (bundle.json 압축 해제). */
        val zipExtractMs: Long,
        /** 모든 섹터 bundle.json 디코딩 + sector/building/level 자료구조 build 완료까지. */
        val bundleJsonDecodeMs: Long,
        /** 모든 섹터의 path CSV (DR+PDR) 를 parse 하는 데 든 **누적** 시간. */
        val pathCsvParseMs: Long,
        /** 메타 요청 제외한 로드 전체 wall-clock (iOS 테이블 "전체 (메타 요청 제외)"). */
        val totalWithoutMetaMs: Long,
        /** 참고용 — 메타 요청 왕복까지 포함한 end-to-end. */
        val totalEndToEndMs: Long,
    )

    /** 벤치 1 session (여러 cold-load 반복) 결과. */
    data class SessionResult(
        val mode: CsvParserMode,
        val sectorIds: List<Int>,
        val iterations: Int,
        val samples: List<StageTimings>,
    ) {
        val pathCsvAvgMs get() = samples.map { it.pathCsvParseMs }.averageOrZero()
        val pathCsvMedianMs get() = samples.map { it.pathCsvParseMs }.medianOrZero()
        val pathCsvP95Ms get() = samples.map { it.pathCsvParseMs }.p95OrZero()
        val bundleDecodeAvgMs get() = samples.map { it.bundleJsonDecodeMs }.averageOrZero()
        val zipExtractAvgMs get() = samples.map { it.zipExtractMs }.averageOrZero()
        val totalWithoutMetaAvgMs get() = samples.map { it.totalWithoutMetaMs }.averageOrZero()
        val totalEndToEndAvgMs get() = samples.map { it.totalEndToEndMs }.averageOrZero()
    }

    /**
     * 벤치마크 실행. 지정한 모드로 **cold load (clearCache + loadResources) 를 N 번** 반복해
     * 단계별 timing 수집. forceUpdate=true 로 네트워크 왕복을 매번 재발생시킴 — jitter 영향을
     * 받으려면 외부 호출자가 샘플 수 (iterations) 를 키우거나 네트워크 안정 환경에서 측정.
     *
     * `onProgress` 는 iteration 종료마다 호출돼 UI 가 진행률을 보여줄 수 있다.
     */
    fun runBenchmark(
        resourceManager: TJLabsResourceManager,
        application: Application,
        provider: String,
        region: String,
        env: ResourceServerEnv,
        sectorIds: List<Int>,
        mode: CsvParserMode,
        iterations: Int,
        onProgress: ((Int, Int) -> Unit)? = null,
        onComplete: (SessionResult) -> Unit,
    ) {
        require(iterations > 0) { "iterations must be positive" }
        require(sectorIds.isNotEmpty()) { "sectorIds cannot be empty" }

        CoroutineScope(Dispatchers.IO).launch {
            val samples = mutableListOf<StageTimings>()
            TJLabsBundleDataManager.benchmarkCsvParserMode = mode
            TJLabsBundleDataManager.benchmarkTimingEnabled = true

            try {
                for (i in 0 until iterations) {
                    // Cold-load 를 강제하기 위해 조합 캐시 삭제. clearCache 는 메모리+디스크 둘 다 비움.
                    TJLabsMultiResourceManager.clearCache(application)
                    sectorIds.forEach { sid -> resourceManager.clearCache(application, sid) }

                    TJLabsBundleDataManager.benchmarkStageTimings.reset()
                    val endToEndStart = System.nanoTime()
                    val latch = kotlinx.coroutines.CompletableDeferred<MultiResourceLoadResult>()
                    TJLabsMultiResourceManager.loadResources(
                        resourceManager = resourceManager,
                        application = application,
                        provider = provider,
                        region = region,
                        env = env,
                        sectorIds = sectorIds,
                        imageLoadPolicy = ImageLoadPolicy.NONE,
                        forceUpdate = true,
                    ) { result -> latch.complete(result) }
                    val result = latch.await()
                    val endToEndMs = (System.nanoTime() - endToEndStart) / 1_000_000

                    val collector = TJLabsBundleDataManager.benchmarkStageTimings
                    samples.add(
                        StageTimings(
                            zipExtractMs = collector.zipExtractMs,
                            bundleJsonDecodeMs = collector.bundleJsonDecodeMs,
                            pathCsvParseMs = collector.pathCsvParseMs,
                            // "메타 제외" = end-to-end - meta. meta 는 result.message 에 안 들어있어
                            // Multi 로더 TOTAL 로그와 샘플 간 wall-clock 차이로만 근사. collector 가
                            // 메타 시작 시점을 캡처해두면 더 정확 — 지금은 end-to-end 대비 보수적.
                            totalWithoutMetaMs = (collector.zipExtractMs + collector.bundleJsonDecodeMs + collector.pathCsvParseMs),
                            totalEndToEndMs = endToEndMs,
                        )
                    )
                    withContext(Dispatchers.Main) { onProgress?.invoke(i + 1, iterations) }
                    TJResourceLogger.i(
                        "(Benchmark) mode=$mode iter=${i + 1}/$iterations csvParse=${collector.pathCsvParseMs}ms extract=${collector.zipExtractMs}ms decode=${collector.bundleJsonDecodeMs}ms endToEnd=${endToEndMs}ms success=${result.isSuccess}"
                    )
                }
            } finally {
                TJLabsBundleDataManager.benchmarkCsvParserMode = CsvParserMode.SINGLE_PASS
                TJLabsBundleDataManager.benchmarkTimingEnabled = false
                TJLabsBundleDataManager.benchmarkStageTimings.reset()
            }

            withContext(Dispatchers.Main) {
                onComplete(SessionResult(mode, sectorIds, iterations, samples))
            }
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────
    private fun List<Long>.averageOrZero(): Double =
        if (isEmpty()) 0.0 else sum().toDouble() / size

    private fun List<Long>.medianOrZero(): Long {
        if (isEmpty()) return 0
        val sorted = sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2 else sorted[mid]
    }

    private fun List<Long>.p95OrZero(): Long {
        if (isEmpty()) return 0
        val sorted = sorted()
        val idx = ((sorted.size - 1) * 0.95).toInt().coerceAtLeast(0)
        return sorted[idx]
    }
}

/**
 * Benchmark 전용 단계별 timing accumulator. [TJLabsBundleDataManager.benchmarkTimingEnabled]
 * 가 true 일 때만 TJLabsBundleDataManager 와 Multi 로더가 각 단계 끝에서 add 한다.
 * 모든 값 ms. iteration 간 reset 필수.
 */
internal class BenchmarkStageTimingCollector {
    @Volatile var zipExtractMs: Long = 0
    @Volatile var bundleJsonDecodeMs: Long = 0
    @Volatile var pathCsvParseMs: Long = 0

    @Synchronized fun addZipExtract(ms: Long) { zipExtractMs += ms }
    @Synchronized fun addBundleJsonDecode(ms: Long) { bundleJsonDecodeMs += ms }
    @Synchronized fun addPathCsvParse(ms: Long) { pathCsvParseMs += ms }

    @Synchronized fun reset() {
        zipExtractMs = 0
        bundleJsonDecodeMs = 0
        pathCsvParseMs = 0
    }
}

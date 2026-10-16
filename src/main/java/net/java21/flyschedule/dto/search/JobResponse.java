package net.java21.flyschedule.dto.search;

import net.java21.flyschedule.service.job.SearchJob;

import java.util.List;

/**
 * 검색 작업 상태 응답
 *
 * @param jobId   작업 ID
 * @param pattern 패턴 (coordinator, tool-calling, orchestrator)
 * @param status  RUNNING 또는 DONE
 * @param trace   지금까지 쌓인 실행 기록 (실행 중에도 늘어남)
 * @param result  끝났을 때의 검색 결과 (실행 중이면 null)
 */
public record JobResponse(String jobId, String pattern, String status, List<TraceStep> trace, SearchResponse result) {

    public static JobResponse of(SearchJob job) {
        SearchResponse result = job.result();
        List<TraceStep> trace = result != null ? result.trace() : job.trace().steps();
        return new JobResponse(job.id(), job.pattern(), job.status().name(), trace, result);
    }
}

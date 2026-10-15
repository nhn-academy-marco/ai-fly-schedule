package net.java21.flyschedule.dto.search;

import java.util.List;

/**
 * 세 가지 검색 패턴의 공통 응답
 *
 * @param pattern   실행한 패턴 (coordinator, tool-calling, orchestrator)
 * @param success   성공 여부
 * @param message   사용자에게 보여 줄 메시지 (LLM 답변 또는 안내 문구)
 * @param results   검색 결과 (Orchestrator-Workers는 하위 작업마다 하나)
 * @param trace     실행 기록
 * @param elapsedMs 전체 처리 시간(ms)
 */
public record SearchResponse(
        String pattern,
        boolean success,
        String message,
        List<TaskResult> results,
        List<TraceStep> trace,
        long elapsedMs
) {

    public static SearchResponse success(String pattern, String message, List<TaskResult> results,
                                         ExecutionTrace trace) {
        return new SearchResponse(pattern, true, message, results, trace.steps(), trace.elapsedMs());
    }

    public static SearchResponse fail(String pattern, String message, ExecutionTrace trace) {
        return new SearchResponse(pattern, false, message, List.of(), trace.steps(), trace.elapsedMs());
    }
}

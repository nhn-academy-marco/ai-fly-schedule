package net.java21.flyschedule.dto.search;

/**
 * 실행 기록 한 줄
 *
 * @param order     실행 순서 (1부터)
 * @param kind      누가 결정·실행했는가 (CODE, LLM, TOOL, WORKER)
 * @param actor     실행한 클래스 또는 Tool 이름
 * @param title     한 일
 * @param detail    입력·출력 요약
 * @param startMs   요청 시작부터 이 단계가 시작될 때까지 걸린 시간(ms)
 * @param durationMs 이 단계에 걸린 시간(ms)
 * @param thread    실행한 스레드 이름 (Worker 병렬 실행을 눈으로 확인하는 용도)
 */
public record TraceStep(
        int order,
        Kind kind,
        String actor,
        String title,
        String detail,
        long startMs,
        long durationMs,
        String thread
) {

    public enum Kind {
        /** 개발자가 작성한 코드가 순서를 정해 실행 */
        CODE,
        /** LLM 호출 (판단·계획·요약) */
        LLM,
        /** LLM이 호출을 요청해 실행된 Tool */
        TOOL,
        /** Orchestrator가 맡긴 하위 작업 */
        WORKER
    }
}

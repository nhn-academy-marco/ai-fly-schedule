package net.java21.flyschedule.dto.search;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 요청 하나의 실행 기록
 *
 * <p>각 패턴이 "누가 다음 단계를 결정했는지"를 화면에서 비교할 수 있도록 단계마다 기록을 남깁니다.
 * Orchestrator-Workers 방식에서는 Worker가 여러 스레드에서 동시에 기록하므로 메서드를 동기화합니다.
 */
public class ExecutionTrace {

    private final long startedAt = System.nanoTime();
    private final List<TraceStep> steps = new ArrayList<>();

    /**
     * 작업을 실행하고 걸린 시간과 결과 요약을 기록합니다.
     */
    public <T> T run(TraceStep.Kind kind, String actor, String title,
                     Supplier<T> action, Function<T, String> describe) {
        long start = System.nanoTime();
        try {
            T result = action.get();
            add(kind, actor, title, describe.apply(result), start, System.nanoTime());
            return result;
        } catch (RuntimeException e) {
            add(kind, actor, title, "실패: " + e.getMessage(), start, System.nanoTime());
            throw e;
        }
    }

    /**
     * 이미 끝난 일을 기록합니다. (예: LLM이 요청해 실행된 Tool 호출)
     */
    public void record(TraceStep.Kind kind, String actor, String title, String detail) {
        long now = System.nanoTime();
        add(kind, actor, title, detail, now, now);
    }

    /**
     * 시작 시각을 알고 있는 일을 끝난 뒤에 기록합니다. (결과를 보고 나서야 종류를 알 수 있을 때)
     */
    public void record(TraceStep.Kind kind, String actor, String title, String detail, long startNanos) {
        add(kind, actor, title, detail, startNanos, System.nanoTime());
    }

    /**
     * 시작·종료 시각({@link System#nanoTime()})을 알고 있는 일을 기록합니다. (예: Tool 실행 구간)
     */
    public void record(TraceStep.Kind kind, String actor, String title, String detail, long startNanos, long endNanos) {
        add(kind, actor, title, detail, startNanos, endNanos);
    }

    public synchronized List<TraceStep> steps() {
        return List.copyOf(steps);
    }

    public long elapsedMs() {
        return toMs(System.nanoTime() - startedAt);
    }

    private synchronized void add(TraceStep.Kind kind, String actor, String title, String detail,
                                  long start, long end) {
        steps.add(new TraceStep(steps.size() + 1, kind, actor, title, detail,
                toMs(start - startedAt), toMs(end - start), threadName()));
    }

    /**
     * 가상 스레드는 이름이 비어 있으므로 "virtual-{id}"로 표시합니다.
     */
    private static String threadName() {
        Thread thread = Thread.currentThread();
        return thread.isVirtual() ? "virtual-" + thread.threadId() : thread.getName();
    }

    private static long toMs(long nanos) {
        return nanos / 1_000_000;
    }
}

package net.java21.flyschedule.mcp;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * LLM이 요청해 실행된 Tool 호출을 기록하는 유틸리티
 *
 * <p>Tool Calling 방식에서는 LLM이 어떤 Tool을 어떤 순서로 부를지 결정합니다.
 * 각 Tool이 실행될 때 이름·인자·결과·실행 시각을 이곳에 남기면, ToolCallingSearchService가 LLM 호출이 끝난 뒤
 * 그 기록을 읽어 실행 기록(trace)과 화면용 결과를 만듭니다.
 *
 * <p>ThreadLocal을 쓰는 이유: Spring AI 1.1.4는 {@code ChatClient.call()}에서 Tool을 호출한 스레드에서
 * 순서대로 실행하므로, 요청마다 기록이 섞이지 않습니다. ({@code stream()}에서는 다른 스레드에서 실행되므로
 * 이 방식을 쓸 수 없습니다.)
 */
public final class ToolResultCapture {

    /**
     * Tool 호출 한 건
     *
     * @param result      Tool이 돌려준 값 (실패했으면 null)
     * @param error       실패했을 때의 메시지 (성공했으면 null)
     * @param startNanos  실행 시작 시각 ({@link System#nanoTime()})
     * @param endNanos    실행 종료 시각
     */
    public record ToolCall(String toolName, String arguments, Object result, String error,
                           long startNanos, long endNanos) {

        public boolean failed() {
            return error != null;
        }
    }

    private static final ThreadLocal<List<ToolCall>> CALLS = ThreadLocal.withInitial(ArrayList::new);

    private ToolResultCapture() {
    }

    /**
     * Tool 작업을 실행하면서 호출을 기록하고, 결과를 그대로 돌려줍니다.
     * 작업이 예외를 던지면 실패로 기록한 뒤 예외를 다시 던집니다. (Spring AI가 LLM에게 오류를 전달)
     */
    public static <T> T capture(String toolName, String arguments, Supplier<T> action) {
        long start = System.nanoTime();
        try {
            T result = action.get();
            CALLS.get().add(new ToolCall(toolName, arguments, result, null, start, System.nanoTime()));
            return result;
        } catch (RuntimeException e) {
            CALLS.get().add(new ToolCall(toolName, arguments, null, e.getMessage(), start, System.nanoTime()));
            throw e;
        }
    }

    /**
     * 지금까지의 호출 기록을 꺼내고 비웁니다. (호출 순서 유지)
     */
    public static List<ToolCall> getAndClear() {
        List<ToolCall> calls = List.copyOf(CALLS.get());
        CALLS.remove();
        return calls;
    }
}

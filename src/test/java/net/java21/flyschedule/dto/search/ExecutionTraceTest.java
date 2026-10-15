package net.java21.flyschedule.dto.search;

import net.java21.flyschedule.dto.search.TraceStep.Kind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ExecutionTrace 단위 테스트")
class ExecutionTraceTest {

    @Test
    @DisplayName("run()은 결과를 돌려주고 종류·실행자·결과 요약을 순서대로 기록한다")
    void runRecordsStepsInOrder() {
        ExecutionTrace trace = new ExecutionTrace();

        String code = trace.run(Kind.CODE, "AirportCodeAgent", "공항 코드 변환", () -> "NAARKJJ", c -> "광주 → " + c);
        trace.record(Kind.TOOL, "searchFlightsByAirline", "LLM이 Tool 호출", "광주 → 제주");

        assertThat(code).isEqualTo("NAARKJJ");
        assertThat(trace.steps())
                .extracting(TraceStep::order, TraceStep::kind, TraceStep::actor, TraceStep::detail)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, Kind.CODE, "AirportCodeAgent", "광주 → NAARKJJ"),
                        org.assertj.core.groups.Tuple.tuple(2, Kind.TOOL, "searchFlightsByAirline", "광주 → 제주"));
    }

    @Test
    @DisplayName("작업이 예외를 던지면 '실패'로 기록하고 예외를 다시 던진다")
    void runRecordsFailure() {
        ExecutionTrace trace = new ExecutionTrace();

        assertThatThrownBy(() -> trace.run(Kind.CODE, "AirportCodeAgent", "공항 코드 변환",
                () -> { throw new IllegalArgumentException("알 수 없는 공항입니다: 서울"); }, Object::toString))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(trace.steps()).singleElement()
                .extracting(TraceStep::detail)
                .isEqualTo("실패: 알 수 없는 공항입니다: 서울");
    }

    @Test
    @DisplayName("걸린 시간과 실행 스레드를 기록한다")
    void recordsDurationAndThread() {
        ExecutionTrace trace = new ExecutionTrace();

        trace.run(Kind.LLM, "ollama", "작업 계획", () -> {
            sleep(30);
            return "ok";
        }, r -> r);

        TraceStep step = trace.steps().get(0);
        assertThat(step.durationMs()).isGreaterThanOrEqualTo(25);
        assertThat(step.thread()).isEqualTo(Thread.currentThread().getName());
        assertThat(trace.elapsedMs()).isGreaterThanOrEqualTo(step.durationMs());
    }

    @Test
    @DisplayName("여러 스레드가 동시에 기록해도 빠짐없이, 순번이 겹치지 않게 기록한다")
    void threadSafe() {
        ExecutionTrace trace = new ExecutionTrace();

        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            List<CompletableFuture<Void>> futures = IntStream.range(0, 200)
                    .mapToObj(i -> CompletableFuture.runAsync(
                            () -> trace.record(Kind.WORKER, "worker", "작업 " + i, ""), executor))
                    .toList();
            futures.forEach(CompletableFuture::join);
        }

        assertThat(trace.steps()).hasSize(200);
        assertThat(trace.steps()).extracting(TraceStep::order).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("steps()는 수정할 수 없는 복사본을 돌려준다")
    void stepsIsImmutableCopy() {
        ExecutionTrace trace = new ExecutionTrace();
        trace.record(Kind.CODE, "a", "b", "c");

        List<TraceStep> steps = trace.steps();
        trace.record(Kind.CODE, "d", "e", "f");

        assertThat(steps).hasSize(1);
        assertThatThrownBy(() -> steps.add(null)).isInstanceOf(UnsupportedOperationException.class);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

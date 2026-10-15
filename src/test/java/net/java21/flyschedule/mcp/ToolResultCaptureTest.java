package net.java21.flyschedule.mcp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ToolResultCapture 단위 테스트")
class ToolResultCaptureTest {

    @AfterEach
    void clear() {
        ToolResultCapture.getAndClear();
    }

    @Test
    @DisplayName("작업 결과를 그대로 돌려주고, 호출 순서대로 기록한다")
    void capturesInOrder() {
        String code = ToolResultCapture.capture("getAirportCode", "광주", () -> "NAARKJJ");
        ToolResultCapture.capture("searchFlightsByAirline", "광주 → 제주", List::of);

        assertThat(code).isEqualTo("NAARKJJ");
        assertThat(ToolResultCapture.getAndClear())
                .extracting(ToolResultCapture.ToolCall::toolName)
                .containsExactly("getAirportCode", "searchFlightsByAirline");
    }

    @Test
    @DisplayName("실행 시작·종료 시각을 기록한다")
    void recordsTiming() {
        ToolResultCapture.capture("slowTool", "", () -> {
            sleep(20);
            return "ok";
        });

        ToolResultCapture.ToolCall call = ToolResultCapture.getAndClear().get(0);
        assertThat((call.endNanos() - call.startNanos()) / 1_000_000).isGreaterThanOrEqualTo(15);
    }

    @Test
    @DisplayName("작업이 실패하면 실패로 기록하고 예외를 그대로 다시 던진다 (Spring AI가 LLM에게 오류 전달)")
    void recordsFailure() {
        assertThatThrownBy(() -> ToolResultCapture.capture("getAirportCode", "서울",
                () -> { throw new IllegalArgumentException("알 수 없는 공항입니다: 서울"); }))
                .isInstanceOf(IllegalArgumentException.class);

        ToolResultCapture.ToolCall call = ToolResultCapture.getAndClear().get(0);
        assertThat(call.failed()).isTrue();
        assertThat(call.error()).contains("서울");
        assertThat(call.result()).isNull();
    }

    @Test
    @DisplayName("getAndClear() 뒤에는 기록이 비어 있다")
    void clearsAfterRead() {
        ToolResultCapture.capture("getAirlineList", "전체", List::of);
        ToolResultCapture.getAndClear();

        assertThat(ToolResultCapture.getAndClear()).isEmpty();
    }

    @Test
    @DisplayName("다른 스레드의 기록은 보이지 않는다 (요청끼리 섞이지 않음)")
    void isolatedPerThread() {
        CompletableFuture.runAsync(() -> ToolResultCapture.capture("getAirportList", "전체", List::of)).join();

        assertThat(ToolResultCapture.getAndClear()).isEmpty();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

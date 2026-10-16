package net.java21.flyschedule.service.job;

import net.java21.flyschedule.dto.search.ExecutionTrace;
import net.java21.flyschedule.dto.search.SearchResponse;
import net.java21.flyschedule.dto.search.TraceStep.Kind;
import net.java21.flyschedule.service.coordinator.FlightSearchCoordinator;
import net.java21.flyschedule.service.orchestrator.FlightSearchOrchestrator;
import net.java21.flyschedule.service.toolcalling.ToolCallingSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("SearchJobService 테스트")
class SearchJobServiceTest {

    private FlightSearchCoordinator coordinator;
    private ToolCallingSearchService toolCalling;
    private SearchJobService jobService;

    @BeforeEach
    void setUp() {
        coordinator = mock(FlightSearchCoordinator.class);
        toolCalling = mock(ToolCallingSearchService.class);
        jobService = new SearchJobService(coordinator, toolCalling, mock(FlightSearchOrchestrator.class));
    }

    @Test
    @DisplayName("작업은 바로 RUNNING으로 돌려주고, 실행 중에도 지금까지의 실행 기록을 볼 수 있다")
    void exposesProgressWhileRunning() throws Exception {
        CountDownLatch firstStepRecorded = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        when(toolCalling.search(anyString(), anyString(), any(ExecutionTrace.class))).thenAnswer(invocation -> {
            ExecutionTrace trace = invocation.getArgument(2);
            trace.record(Kind.TOOL, "searchFlightsByAirline", "LLM이 요청한 Tool 실행", "광주 → 제주");
            firstStepRecorded.countDown();
            finish.await(5, TimeUnit.SECONDS);
            return SearchResponse.success("tool-calling", "완료", List.of(), trace);
        });

        SearchJob job = jobService.start("tool-calling", "내일 광주에서 제주", "ollama");

        assertThat(firstStepRecorded.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(job.status()).isEqualTo(SearchJob.Status.RUNNING);
        assertThat(jobService.find(job.id()).orElseThrow().trace().steps()).hasSize(1);

        finish.countDown();
        await().atMost(Duration.ofSeconds(5)).until(() -> job.status() == SearchJob.Status.DONE);
        assertThat(job.result().message()).isEqualTo("완료");
    }

    @Test
    @DisplayName("패턴 서비스가 예상하지 못한 예외를 던져도 작업은 실패 결과로 끝난다 (RUNNING에 머물지 않음)")
    void unexpectedExceptionCompletesJob() {
        when(coordinator.coordinate(anyString(), anyString(), any(ExecutionTrace.class)))
                .thenThrow(new IllegalStateException("예상하지 못한 오류"));

        SearchJob job = jobService.start("coordinator", "내일 광주에서 제주", "ollama");

        await().atMost(Duration.ofSeconds(5)).until(() -> job.status() == SearchJob.Status.DONE);
        assertThat(job.result().success()).isFalse();
        assertThat(job.result().message()).contains("예상하지 못한 오류");
    }

    @Test
    @DisplayName("알 수 없는 패턴이면 작업을 만들지 않는다")
    void unknownPattern() {
        assertThatThrownBy(() -> jobService.start("a2a", "질문", "ollama"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("알 수 없는 패턴");
    }

    @Test
    @DisplayName("없는 작업 ID는 빈 값으로 돌려준다")
    void unknownJob() {
        assertThat(jobService.find("no-such-job")).isEmpty();
    }
}

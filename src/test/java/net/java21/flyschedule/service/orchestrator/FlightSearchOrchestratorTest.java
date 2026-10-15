package net.java21.flyschedule.service.orchestrator;

import net.java21.flyschedule.config.ChatClientProvider;
import net.java21.flyschedule.dto.search.ExecutionTrace;
import net.java21.flyschedule.dto.search.SearchCriteria;
import net.java21.flyschedule.dto.search.SearchResponse;
import net.java21.flyschedule.dto.search.TaskResult;
import net.java21.flyschedule.dto.search.TraceStep;
import net.java21.flyschedule.dto.search.TraceStep.Kind;
import net.java21.flyschedule.service.coordinator.FlightSearchCoordinator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Map;

import static net.java21.flyschedule.support.TestFlights.flight;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Orchestrator-Workers 패턴 테스트
 *
 * <p>계획(Planner)과 종합(Synthesizer)은 LLM을 쓰므로 가짜로 바꾸고,
 * Worker → Coordinator 검색 단계의 병렬 실행과 결과 조립을 검증합니다.
 */
@DisplayName("FlightSearchOrchestrator 테스트")
class FlightSearchOrchestratorTest {

    private static final long WORKER_DELAY_MS = 300;

    private FlightSearchPlanner planner;
    private ResultSynthesizer synthesizer;
    private FlightSearchCoordinator coordinator;
    private FlightSearchOrchestrator orchestrator;

    private final SearchCriteria gimpoFri = new SearchCriteria("김포", "제주", "2026-03-27", null, null, null, null);
    private final SearchCriteria gimpoSat = new SearchCriteria("김포", "제주", "2026-03-28", null, null, null, null);
    private final SearchCriteria gwangjuFri = new SearchCriteria("광주", "제주", "2026-03-27", null, null, null, null);
    private final SearchCriteria gwangjuSat = new SearchCriteria("광주", "제주", "2026-03-28", null, null, null, null);

    @BeforeEach
    void setUp() {
        planner = mock(FlightSearchPlanner.class);
        synthesizer = mock(ResultSynthesizer.class);
        coordinator = mock(FlightSearchCoordinator.class);
        ChatClientProvider provider = mock(ChatClientProvider.class);
        when(provider.get(anyString())).thenReturn(mock(ChatClient.class));

        // Coordinator 검색 단계는 작업마다 WORKER_DELAY_MS가 걸린다고 가정
        when(coordinator.search(any(), any())).thenAnswer(invocation -> {
            SearchCriteria task = invocation.getArgument(0);
            Thread.sleep(WORKER_DELAY_MS);
            int price = task.departure().equals("광주") ? 45000 : 52000;
            return TaskResult.of(task, Map.of("제주항공", List.of(flight("7C" + price, "제주항공", "1030", "1120", price))));
        });
        when(synthesizer.synthesize(anyString(), anyList(), any())).thenReturn("광주 출발이 가장 저렴합니다.");

        orchestrator = new FlightSearchOrchestrator(provider, planner, new FlightSearchWorker(coordinator), synthesizer);
    }

    @Test
    @DisplayName("LLM 계획 → Worker 실행 → LLM 종합 순서로 기록하고, 작업마다 결과를 하나씩 돌려준다")
    void planExecuteSynthesize() {
        when(planner.plan(anyString(), any())).thenReturn(new SearchPlan("2 × 2", List.of(gimpoFri, gwangjuFri)));

        SearchResponse response = orchestrator.orchestrate("금요일 김포나 광주에서 제주", "gemini");

        assertThat(response.success()).isTrue();
        assertThat(response.pattern()).isEqualTo("orchestrator");
        assertThat(response.message()).isEqualTo("광주 출발이 가장 저렴합니다.");
        assertThat(response.results()).extracting(TaskResult::criteria).containsExactly(gimpoFri, gwangjuFri);

        List<TraceStep> trace = response.trace();
        assertThat(trace.get(0).kind()).isEqualTo(Kind.LLM);
        assertThat(trace.get(0).title()).contains("작업 계획");
        assertThat(trace.get(trace.size() - 1).kind()).isEqualTo(Kind.LLM);
        assertThat(trace.get(trace.size() - 1).title()).contains("결과 종합");
        assertThat(trace).filteredOn(step -> step.kind() == Kind.WORKER).hasSize(2);
    }

    @Test
    @DisplayName("Worker는 서로 다른 스레드에서 병렬로 실행된다 (전체 시간 < 작업 시간의 합)")
    void workersRunInParallel() {
        when(planner.plan(anyString(), any()))
                .thenReturn(new SearchPlan("2 × 2", List.of(gimpoFri, gimpoSat, gwangjuFri, gwangjuSat)));

        SearchResponse response = orchestrator.orchestrate("금·토 김포·광주 → 제주", "ollama");

        List<TraceStep> workers = response.trace().stream().filter(s -> s.kind() == Kind.WORKER).toList();
        assertThat(workers).hasSize(4);
        assertThat(workers).extracting(TraceStep::thread).doesNotHaveDuplicates().allMatch(name -> name.startsWith("virtual-"));
        assertThat(response.elapsedMs()).isLessThan(WORKER_DELAY_MS * 4);
    }

    @Test
    @DisplayName("작업 하나가 실패해도 나머지 결과는 살리고, 실패한 작업은 빈 결과로 남긴다")
    void workerFailureIsolated() {
        when(planner.plan(anyString(), any())).thenReturn(new SearchPlan("2개", List.of(gimpoFri, gwangjuFri)));
        doThrow(new IllegalStateException("API 타임아웃")).when(coordinator).search(eq(gimpoFri), any());

        SearchResponse response = orchestrator.orchestrate("금요일 김포나 광주에서 제주", "ollama");

        assertThat(response.success()).isTrue();
        assertThat(response.results()).hasSize(2);
        assertThat(response.results().get(0).isEmpty()).isTrue();
        assertThat(response.results().get(1).totalCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("계획을 세우지 못하면 Worker와 종합 단계를 실행하지 않고 실패한다")
    void planFailure() {
        when(planner.plan(anyString(), any()))
                .thenThrow(new IllegalArgumentException("검색할 수 있는 작업이 없습니다."));

        SearchResponse response = orchestrator.orchestrate("아무거나", "ollama");

        assertThat(response.success()).isFalse();
        assertThat(response.message()).isEqualTo("검색할 수 있는 작업이 없습니다.");
        verify(coordinator, never()).search(any(), any());
        verify(synthesizer, never()).synthesize(anyString(), anyList(), any());
    }

    @Test
    @DisplayName("Worker는 Coordinator의 검색 단계에 같은 실행 기록(trace)을 넘긴다")
    void workerSharesTrace() {
        ExecutionTrace trace = new ExecutionTrace();

        new FlightSearchWorker(coordinator).execute(gwangjuFri, trace);

        verify(coordinator).search(gwangjuFri, trace);
        assertThat(trace.steps()).singleElement().satisfies(step -> {
            assertThat(step.kind()).isEqualTo(Kind.WORKER);
            assertThat(step.title()).isEqualTo(gwangjuFri.label());
            assertThat(step.detail()).contains("45,000원");
        });
    }
}

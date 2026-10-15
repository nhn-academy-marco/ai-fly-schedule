package net.java21.flyschedule.service.orchestrator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.java21.flyschedule.config.ChatClientProvider;
import net.java21.flyschedule.dto.search.ExecutionTrace;
import net.java21.flyschedule.dto.search.SearchCriteria;
import net.java21.flyschedule.dto.search.SearchResponse;
import net.java21.flyschedule.dto.search.TaskResult;
import net.java21.flyschedule.dto.search.TraceStep.Kind;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Orchestrator-Workers 패턴: LLM이 작업을 나누고, Worker가 병렬로 처리하고, LLM이 결과를 종합합니다.
 *
 * <pre>
 * 1. 계획 (LLM)     FlightSearchPlanner  요청 → 검색 작업 N개 (N은 LLM이 결정)
 * 2. 실행 (Worker)  FlightSearchWorker   작업마다 하나씩, 가상 스레드에서 병렬 실행
 * 3. 종합 (LLM)     ResultSynthesizer    작업별 결과 → 비교·추천 답변
 * </pre>
 *
 * <p>Coordinator 패턴과 달리 하위 작업의 개수와 내용이 미리 정해져 있지 않습니다.
 * Tool Calling 에이전트와 달리 LLM은 계획과 종합만 하고, 실행은 코드(Worker)가 병렬로 맡습니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlightSearchOrchestrator {

    public static final String PATTERN = "orchestrator";

    private final ChatClientProvider chatClientProvider;
    private final FlightSearchPlanner planner;
    private final FlightSearchWorker worker;
    private final ResultSynthesizer synthesizer;

    public SearchResponse orchestrate(String message, String model) {
        ExecutionTrace trace = new ExecutionTrace();
        String modelName = ChatClientProvider.normalize(model);

        try {
            ChatClient chatClient = chatClientProvider.get(model);

            // 1. 계획: LLM이 요청을 하위 작업으로 나눈다
            SearchPlan plan = trace.run(Kind.LLM, modelName, "작업 계획 (FlightSearchPlanner)",
                    () -> planner.plan(message, chatClient),
                    p -> p.tasks().size() + "개 작업: " + p.tasks().stream()
                            .map(SearchCriteria::label).collect(Collectors.joining(" / ")));

            // 2. 실행: Worker를 작업마다 가상 스레드에서 병렬로 실행한다
            List<TaskResult> results = runWorkers(plan.tasks(), trace);

            // 3. 종합: LLM이 결과를 비교해 추천한다
            String answer = trace.run(Kind.LLM, modelName, "결과 종합 (ResultSynthesizer)",
                    () -> synthesizer.synthesize(message, results, chatClient),
                    text -> text.length() + "자 답변");

            return SearchResponse.success(PATTERN, answer, results, trace);

        } catch (IllegalArgumentException e) {
            log.warn("Orchestrator 검색 실패: {}", e.getMessage());
            return SearchResponse.fail(PATTERN, e.getMessage(), trace);
        } catch (Exception e) {
            log.error("Orchestrator 검색 오류", e);
            return SearchResponse.fail(PATTERN, ChatClientProvider.describeError(e), trace);
        }
    }

    private List<TaskResult> runWorkers(List<SearchCriteria> tasks, ExecutionTrace trace) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<TaskResult>> futures = tasks.stream()
                    .map(task -> CompletableFuture.supplyAsync(() -> worker.execute(task, trace), executor))
                    .toList();
            return futures.stream().map(CompletableFuture::join).toList();
        }
    }
}

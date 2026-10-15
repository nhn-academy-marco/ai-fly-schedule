package net.java21.flyschedule.service.orchestrator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.java21.flyschedule.dto.search.ExecutionTrace;
import net.java21.flyschedule.dto.search.SearchCriteria;
import net.java21.flyschedule.dto.search.TaskResult;
import net.java21.flyschedule.dto.search.TraceStep.Kind;
import net.java21.flyschedule.service.coordinator.FlightSearchCoordinator;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Worker: Orchestrator가 맡긴 검색 작업 하나를 처리합니다.
 *
 * <p>실제 검색은 Step 9에서 만든 Coordinator의 검색 단계({@link FlightSearchCoordinator#search})를 재사용합니다.
 * 패턴은 서로 배타적이지 않습니다. 결정이 필요한 상위 단계는 LLM(Orchestrator)이 맡고,
 * 결과가 항상 같아야 하는 하위 단계는 코드(Coordinator)가 맡도록 조합할 수 있습니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlightSearchWorker {

    private final FlightSearchCoordinator coordinator;

    public TaskResult execute(SearchCriteria task, ExecutionTrace trace) {
        try {
            return trace.run(Kind.WORKER, "FlightSearchWorker", task.label(),
                    () -> coordinator.search(task, trace),
                    TaskResult::summaryLine);
        } catch (RuntimeException e) {
            // 작업 하나가 실패해도 나머지 작업 결과는 살린다
            log.warn("Worker 실패 ({}): {}", task.label(), e.getMessage());
            return TaskResult.of(task, Map.of());
        }
    }
}

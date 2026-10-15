package net.java21.flyschedule.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.java21.flyschedule.dto.request.SearchRequest;
import net.java21.flyschedule.dto.search.ExecutionTrace;
import net.java21.flyschedule.dto.search.SearchResponse;
import net.java21.flyschedule.service.coordinator.FlightSearchCoordinator;
import net.java21.flyschedule.service.orchestrator.FlightSearchOrchestrator;
import net.java21.flyschedule.service.toolcalling.ToolCallingSearchService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.function.BiFunction;

/**
 * 자연어 항공편 검색 API
 *
 * <p>같은 문장을 세 가지 패턴으로 처리해 비교할 수 있습니다.
 * <ul>
 *   <li>POST /api/search/coordinator  - Coordinator 패턴 (코드가 순서를 제어)</li>
 *   <li>POST /api/search/tool-calling - Tool Calling 에이전트 (LLM이 Tool 선택)</li>
 *   <li>POST /api/search/orchestrator - Orchestrator-Workers 패턴 (LLM이 작업 분해·종합)</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final FlightSearchCoordinator coordinator;
    private final ToolCallingSearchService toolCallingService;
    private final FlightSearchOrchestrator orchestrator;

    @PostMapping("/coordinator")
    public SearchResponse coordinator(@RequestBody SearchRequest request) {
        return handle(FlightSearchCoordinator.PATTERN, request, coordinator::coordinate);
    }

    @PostMapping("/tool-calling")
    public SearchResponse toolCalling(@RequestBody SearchRequest request) {
        return handle(ToolCallingSearchService.PATTERN, request, toolCallingService::search);
    }

    @PostMapping("/orchestrator")
    public SearchResponse orchestrator(@RequestBody SearchRequest request) {
        return handle(FlightSearchOrchestrator.PATTERN, request, orchestrator::orchestrate);
    }

    private SearchResponse handle(String pattern, SearchRequest request,
                                  BiFunction<String, String, SearchResponse> searcher) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            return SearchResponse.fail(pattern, "검색할 문장을 입력해 주세요.", new ExecutionTrace());
        }
        log.info("[{}] 검색 요청 (model={}): {}", pattern, request.model(), request.message());
        return searcher.apply(request.message().trim(), request.model());
    }
}

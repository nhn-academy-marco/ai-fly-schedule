package net.java21.flyschedule.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.java21.flyschedule.dto.request.SearchRequest;
import net.java21.flyschedule.dto.search.ExecutionTrace;
import net.java21.flyschedule.dto.search.JobResponse;
import net.java21.flyschedule.dto.search.SearchResponse;
import net.java21.flyschedule.service.coordinator.FlightSearchCoordinator;
import net.java21.flyschedule.service.job.SearchJob;
import net.java21.flyschedule.service.job.SearchJobService;
import net.java21.flyschedule.service.orchestrator.FlightSearchOrchestrator;
import net.java21.flyschedule.service.toolcalling.ToolCallingSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
 *   <li>POST /api/search/tool-calling - Tool Calling Agent (LLM이 Tool 선택)</li>
 *   <li>POST /api/search/orchestrator - Orchestrator-Workers 패턴 (LLM이 작업 분해·종합)</li>
 * </ul>
 *
 * <p>위 세 API는 검색이 끝날 때까지 기다렸다가 응답합니다. LLM 호출이 길어 프록시 타임아웃(nginx 기본 60초)에
 * 걸릴 수 있는 환경에서는 작업 API를 씁니다. 화면(index.html)도 작업 API를 사용합니다.
 * <ul>
 *   <li>POST /api/search/{pattern}/jobs - 검색을 백그라운드에서 시작하고 작업 ID를 바로 반환</li>
 *   <li>GET  /api/search/jobs/{jobId}   - 진행 상황(지금까지의 실행 기록)과 결과 조회</li>
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
    private final SearchJobService jobService;

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

    @PostMapping("/{pattern}/jobs")
    public ResponseEntity<JobResponse> startJob(@PathVariable String pattern, @RequestBody SearchRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new IllegalArgumentException("검색할 문장을 입력해 주세요.");
        }
        log.info("[{}] 검색 작업 시작 (model={}): {}", pattern, request.model(), request.message());
        SearchJob job = jobService.start(pattern, request.message().trim(), request.model());
        return ResponseEntity.accepted().body(JobResponse.of(job));
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<JobResponse> getJob(@PathVariable String jobId) {
        return jobService.find(jobId)
                .map(job -> ResponseEntity.ok(JobResponse.of(job)))
                .orElseGet(() -> ResponseEntity.notFound().build());
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

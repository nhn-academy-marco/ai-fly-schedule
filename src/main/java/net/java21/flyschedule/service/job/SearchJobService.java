package net.java21.flyschedule.service.job;

import lombok.extern.slf4j.Slf4j;
import net.java21.flyschedule.dto.search.ExecutionTrace;
import net.java21.flyschedule.dto.search.SearchResponse;
import net.java21.flyschedule.service.coordinator.FlightSearchCoordinator;
import net.java21.flyschedule.service.orchestrator.FlightSearchOrchestrator;
import net.java21.flyschedule.service.toolcalling.ToolCallingSearchService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 검색을 백그라운드에서 실행하고, 진행 상황을 조회할 수 있게 하는 서비스
 *
 * <p>왜 필요한가: LLM을 여러 번 호출하는 Tool Calling·Orchestrator 패턴은 응답에 1분 넘게 걸릴 수 있습니다.
 * 앞단의 프록시(nginx 기본 60초)가 그 전에 연결을 끊으면 504 오류가 납니다.
 * 요청은 작업 ID만 바로 돌려주고, 화면이 짧은 요청으로 상태를 여러 번 물어보면 이 제한에 걸리지 않습니다.
 * 덤으로 실행 기록이 한 단계씩 쌓이는 모습을 실시간으로 볼 수 있습니다.
 */
@Slf4j
@Service
public class SearchJobService {

    /** 끝난 작업을 보관하는 시간 */
    static final Duration RETENTION = Duration.ofMinutes(10);
    /** 동시에 보관하는 최대 작업 수 (메모리 보호) */
    static final int MAX_JOBS = 200;

    @FunctionalInterface
    interface TracedSearch {
        SearchResponse run(String message, String model, ExecutionTrace trace);
    }

    private final Map<String, TracedSearch> searches;
    private final Map<String, SearchJob> jobs = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public SearchJobService(FlightSearchCoordinator coordinator,
                            ToolCallingSearchService toolCallingService,
                            FlightSearchOrchestrator orchestrator) {
        this.searches = Map.of(
                FlightSearchCoordinator.PATTERN, coordinator::coordinate,
                ToolCallingSearchService.PATTERN, toolCallingService::search,
                FlightSearchOrchestrator.PATTERN, orchestrator::orchestrate);
    }

    /**
     * 검색 작업을 시작하고 바로 돌려줍니다.
     *
     * @throws IllegalArgumentException 알 수 없는 패턴이거나 작업이 너무 많을 때
     */
    public SearchJob start(String pattern, String message, String model) {
        TracedSearch search = searches.get(pattern);
        if (search == null) {
            throw new IllegalArgumentException("알 수 없는 패턴입니다: " + pattern);
        }
        removeExpired();
        if (jobs.size() >= MAX_JOBS) {
            throw new IllegalArgumentException("실행 중인 검색이 너무 많습니다. 잠시 후 다시 시도하세요.");
        }

        SearchJob job = new SearchJob(UUID.randomUUID().toString(), pattern);
        jobs.put(job.id(), job);
        executor.submit(() -> {
            try {
                job.complete(search.run(message, model, job.trace()));
            } catch (Exception e) {
                // 각 패턴이 예외를 응답으로 바꾸지만, 놓친 예외가 있어도 작업이 영원히 RUNNING으로 남지 않게 한다
                log.error("검색 작업 실패 ({})", job.id(), e);
                job.complete(SearchResponse.fail(pattern, "검색 중 오류가 발생했습니다: " + e.getMessage(), job.trace()));
            }
        });
        return job;
    }

    public Optional<SearchJob> find(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    @Scheduled(fixedDelay = 60_000)
    void removeExpired() {
        Instant limit = Instant.now().minus(RETENTION);
        jobs.values().removeIf(job -> job.status() == SearchJob.Status.DONE && job.createdAt().isBefore(limit));
    }
}

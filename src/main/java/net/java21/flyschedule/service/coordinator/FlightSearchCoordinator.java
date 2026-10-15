package net.java21.flyschedule.service.coordinator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.java21.flyschedule.config.ChatClientProvider;
import net.java21.flyschedule.dto.response.FlightInfoResponse;
import net.java21.flyschedule.dto.search.ExecutionTrace;
import net.java21.flyschedule.dto.search.SearchCriteria;
import net.java21.flyschedule.dto.search.SearchResponse;
import net.java21.flyschedule.dto.search.TaskResult;
import net.java21.flyschedule.dto.search.TraceStep.Kind;
import net.java21.flyschedule.service.agent.AirportCodeAgent;
import net.java21.flyschedule.service.agent.DateParserAgent;
import net.java21.flyschedule.service.agent.GroupingAgent;
import net.java21.flyschedule.service.agent.PriceFilterAgent;
import net.java21.flyschedule.service.agent.TimeFilterAgent;
import net.java21.flyschedule.service.ai.SearchCriteriaExtractor;
import net.java21.flyschedule.service.api.ApiClientService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Coordinator 패턴: 코드가 제어하는 순차 워크플로
 *
 * <p>처리 순서는 이 클래스의 코드로 고정되어 있습니다. LLM은 1단계(검색 조건 추출)에서
 * 정규식이 실패했을 때만 쓰이고, 다음에 무엇을 할지는 결정하지 않습니다.
 *
 * <pre>
 * 1. SearchCriteriaExtractor  자연어 → 검색 조건 (정규식, 실패 시 LLM)
 * 2. DateParserAgent          "2026-03-25" → "20260325"
 * 3. AirportCodeAgent         "광주" → "NAARKJJ"
 * 4. ApiClientService         공공데이터 포털 API 호출
 * 5. TimeFilterAgent          출발 시각 필터 (조건이 있을 때)
 * 6. PriceFilterAgent         가격 필터 (조건이 있을 때)
 * 7. GroupingAgent            항공사별 그룹핑
 * </pre>
 *
 * <p>2~7단계({@link #search})는 Orchestrator-Workers 패턴의 Worker가 그대로 재사용합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlightSearchCoordinator {

    public static final String PATTERN = "coordinator";

    private final ChatClientProvider chatClientProvider;
    private final SearchCriteriaExtractor criteriaExtractor;
    private final DateParserAgent dateParserAgent;
    private final AirportCodeAgent airportCodeAgent;
    private final ApiClientService apiClientService;
    private final TimeFilterAgent timeFilterAgent;
    private final PriceFilterAgent priceFilterAgent;
    private final GroupingAgent groupingAgent;

    public SearchResponse coordinate(String message, String model) {
        ExecutionTrace trace = new ExecutionTrace();
        try {
            // 1단계: 검색 조건 추출 (정규식이 실패하면 LLM)
            long start = System.nanoTime();
            SearchCriteriaExtractor.Extraction extraction;
            try {
                extraction = criteriaExtractor.extract(message, () -> chatClientProvider.get(model));
            } catch (RuntimeException e) {
                trace.record(Kind.CODE, "SearchCriteriaExtractor", "검색 조건 추출", "실패: " + e.getMessage(), start);
                throw e;
            }
            trace.record(extraction.usedLlm() ? Kind.LLM : Kind.CODE, "SearchCriteriaExtractor",
                    extraction.usedLlm() ? "LLM으로 검색 조건 추출" : "정규식으로 검색 조건 추출",
                    extraction.criteria().label(), start);

            // 2~7단계: 정해진 순서대로 전문 에이전트 호출
            TaskResult result = search(extraction.criteria(), trace);

            String summary = result.isEmpty() ? "조건에 맞는 항공편이 없습니다." : result.summaryLine();
            return SearchResponse.success(PATTERN, summary, List.of(result), trace);

        } catch (IllegalArgumentException e) {
            log.warn("Coordinator 검색 실패: {}", e.getMessage());
            return SearchResponse.fail(PATTERN, e.getMessage(), trace);
        } catch (Exception e) {
            log.error("Coordinator 검색 오류", e);
            return SearchResponse.fail(PATTERN, ChatClientProvider.describeError(e), trace);
        }
    }

    /**
     * 검색 조건 하나를 정해진 순서로 처리합니다. (2~7단계)
     */
    public TaskResult search(SearchCriteria criteria, ExecutionTrace trace) {
        String date = trace.run(Kind.CODE, "DateParserAgent", "날짜 변환",
                () -> dateParserAgent.parseDate(criteria.date()),
                parsed -> criteria.date() + " → " + parsed);

        String depAirportId = trace.run(Kind.CODE, "AirportCodeAgent", "출발 공항 코드 변환",
                () -> airportCodeAgent.getAirportCode(criteria.departure()),
                code -> criteria.departure() + " → " + code);
        String arrAirportId = trace.run(Kind.CODE, "AirportCodeAgent", "도착 공항 코드 변환",
                () -> airportCodeAgent.getAirportCode(criteria.arrival()),
                code -> criteria.arrival() + " → " + code);

        List<FlightInfoResponse> flights = trace.run(Kind.CODE, "ApiClientService", "공공데이터 포털 API 호출",
                () -> apiClientService.getFlightSchedule(depAirportId, arrAirportId, date),
                list -> list.size() + "편 조회");

        if (criteria.afterTime() != null) {
            List<FlightInfoResponse> source = flights;
            flights = trace.run(Kind.CODE, "TimeFilterAgent", criteria.afterTime() + " 이후 출발만 남기기",
                    () -> timeFilterAgent.filterAfterTime(source, timeFilterAgent.parseTime(criteria.afterTime())),
                    list -> source.size() + "편 → " + list.size() + "편");
        }
        if (criteria.beforeTime() != null) {
            List<FlightInfoResponse> source = flights;
            flights = trace.run(Kind.CODE, "TimeFilterAgent", criteria.beforeTime() + " 이전 출발만 남기기",
                    () -> timeFilterAgent.filterBeforeTime(source, timeFilterAgent.parseTime(criteria.beforeTime())),
                    list -> source.size() + "편 → " + list.size() + "편");
        }
        if (criteria.minPrice() != null || criteria.maxPrice() != null) {
            List<FlightInfoResponse> source = flights;
            flights = trace.run(Kind.CODE, "PriceFilterAgent", "가격 범위로 거르기",
                    () -> priceFilterAgent.filterByPriceRange(source, criteria.minPrice(), criteria.maxPrice()),
                    list -> source.size() + "편 → " + list.size() + "편");
        }

        List<FlightInfoResponse> filtered = flights;
        Map<String, List<FlightInfoResponse>> grouped = trace.run(Kind.CODE, "GroupingAgent", "항공사별 그룹핑",
                () -> groupingAgent.groupByAirline(filtered),
                map -> map.size() + "개 항공사");

        return TaskResult.of(criteria, grouped);
    }
}

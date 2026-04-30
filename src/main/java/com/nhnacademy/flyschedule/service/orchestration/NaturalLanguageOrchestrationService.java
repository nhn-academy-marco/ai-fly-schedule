package com.nhnacademy.flyschedule.service.orchestration;

import com.nhnacademy.flyschedule.dto.response.FlightInfoResponse;
import com.nhnacademy.flyschedule.service.agent.*;
import com.nhnacademy.flyschedule.service.ai.LlmAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 자연어 항공편 검색 오케스트레이션 서비스
 *
 * Coordinator 패턴으로 여러 A2A 에이전트들을 조율합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NaturalLanguageOrchestrationService {

    private final LlmAnalysisService llmAnalysisService;
    private final DateParserAgent dateParserAgent;
    private final AirportCodeAgent airportCodeAgent;
    private final FlightSearchAgent flightSearchAgent;
    private final TimeFilterAgent timeFilterAgent;
    private final PriceFilterAgent priceFilterAgent;
    private final GroupingAgent groupingAgent;

    /**
     * 자연어 메시지를 분석하여 항공편을 검색하고 결과를 반환합니다.
     *
     * @param message 사용어 자연어 메시지
     * @return 검색 결과 Map
     */
    public OrchestrationResult orchestrateFlightSearch(String message) {
        log.info("자연어 항공편 검색 오케스트레이션 시작: {}", message);

        // 1단계: LLM로 파라미터 추출
        log.info("단계 1: LLM 파라미터 추출");
        Map<String, Object> params = llmAnalysisService.extractFlightSearchParams(message);

        // 2단계: 파라미터 검증
        log.info("단계 2: 파라미터 검증");
        if (!params.containsKey("departure") || !params.containsKey("arrival")) {
            return OrchestrationResult.error("출발 공항과 도착 공항을 명확히 입력해주세요.");
        }

        // 3단계: 날짜 처리
        log.info("단계 3: 날짜 처리");
        String dateStr = (String) params.getOrDefault("date", LocalDate.now().toString());
        String parsedDate = dateParserAgent.parseDate(dateStr);
        log.info("날짜: {} -> {}", dateStr, parsedDate);

        // 4단계: 공항 코드 변환
        log.info("단계 4: 공항 코드 변환");
        String departure = (String) params.get("departure");
        String arrival = (String) params.get("arrival");
        String depAirportId = airportCodeAgent.getAirportCode(departure);
        String arrAirportId = airportCodeAgent.getAirportCode(arrival);
        log.info("공항 코드: {} -> {}, {} -> {}", departure, depAirportId, arrival, arrAirportId);

        // 5단계: 항공편 검색
        log.info("단계 5: 항공편 검색");
        Map<String, List<FlightInfoResponse>> flightsMap =
            flightSearchAgent.searchAndGroupByAirline(depAirportId, arrAirportId, parsedDate);
        List<FlightInfoResponse> flights = flightsMap.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
        log.info("검색된 항공편: {}편", flights.size());

        // 6단계: 시간 필터링 (이후)
        if (params.containsKey("afterTime")) {
            log.info("단계 6a: 시간 필터링 (이후)");
            String afterTime = (String) params.get("afterTime");
            LocalTime time = LocalTime.parse(afterTime);
            flights = timeFilterAgent.filterAfterTime(flights, time);
            log.info("{} 이후 필터링: {}편", afterTime, flights.size());
        }

        // 7단계: 시간 필터링 (이전)
        if (params.containsKey("beforeTime")) {
            log.info("단계 6b: 시간 필터링 (이전)");
            String beforeTime = (String) params.get("beforeTime");
            LocalTime time = LocalTime.parse(beforeTime);
            flights = timeFilterAgent.filterBeforeTime(flights, time);
            log.info("{} 이전 필터링: {}편", beforeTime, flights.size());
        }

        // 8단계: 가격 필터링
        if (params.containsKey("minPrice") || params.containsKey("maxPrice")) {
            log.info("단계 7: 가격 필터링");
            Integer minPrice = params.containsKey("minPrice") ?
                    Integer.parseInt((String) params.get("minPrice")) : null;
            Integer maxPrice = params.containsKey("maxPrice") ?
                    Integer.parseInt((String) params.get("maxPrice")) : null;
            flights = priceFilterAgent.filterByPriceRange(flights, minPrice, maxPrice);
            log.info("가격 필터링 ({}~{}): {}편", minPrice, maxPrice, flights.size());
        }

        // 9단계: 항공사별 그룹핑
        log.info("단계 8: 항공사별 그룹핑");
        Map<String, List<FlightInfoResponse>> groupedFlights = groupingAgent.groupByAirline(flights);
        log.info("그룹핑 완료: {}개 항공사", groupedFlights.size());

        // 10단계: 결과 변환
        log.info("단계 9: 결과 변환");
        Map<String, List<Map<String, Object>>> resultData = groupedFlights.entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> entry.getValue().stream()
                            .map(this::convertToMap)
                            .collect(Collectors.toList())
                ));

        log.info("오케스트레이션 완료");
        return OrchestrationResult.success(params, resultData);
    }

    /**
     * FlightInfoResponse를 Map으로 변환
     */
    private Map<String, Object> convertToMap(FlightInfoResponse flight) {
        return Map.of(
            "vihicleId", flight.getFlightId(),
            "airlineNm", flight.getAirlineName(),
            "depPlandTime", flight.getDepartureTime(),
            "arrPlandTime", flight.getArrivalTime(),
            "economyCharge", flight.getEconomyCharge(),
            "prestigeCharge", flight.getPrestigeCharge(),
            "depAirportNm", flight.getDepartureAirport(),
            "arrAirportNm", flight.getArrivalAirport()
        );
    }

    /**
     * 오케스트레이션 결과
     */
    public static class OrchestrationResult {
        private final boolean success;
        private final String message;
        private final Map<String, Object> extractedParams;
        private final Map<String, List<Map<String, Object>>> data;

        private OrchestrationResult(boolean success, String message,
                                  Map<String, Object> extractedParams,
                                  Map<String, List<Map<String, Object>>> data) {
            this.success = success;
            this.message = message;
            this.extractedParams = extractedParams;
            this.data = data;
        }

        public static OrchestrationResult success(Map<String, Object> params,
                                                   Map<String, List<Map<String, Object>>> data) {
            return new OrchestrationResult(true, "검색 완료", params, data);
        }

        public static OrchestrationResult error(String message) {
            return new OrchestrationResult(false, message, null, Map.of());
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public Map<String, Object> getExtractedParams() {
            return extractedParams;
        }

        public Map<String, List<Map<String, Object>>> getData() {
            return data;
        }

        public Map<String, Object> toResponse() {
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("message", message);
            if (extractedParams != null) {
                response.put("extractedParams", extractedParams);
            }
            response.put("data", data != null ? data : Map.of());
            return response;
        }
    }
}

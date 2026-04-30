package com.nhnacademy.flyschedule.mcp;

import com.nhnacademy.flyschedule.dto.response.FlightInfoResponse;
import com.nhnacademy.flyschedule.service.agent.FlightSearchAgent;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 항공편 검색 MCP Tool
 *
 * Spring AI의 @Tool annotation을 사용하여 AI 모델이 호출할 수 있는 도구를 정의합니다.
 * A2A 에이전트들에게 작업을 위임하여 결과를 반환합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlightSearchTool {

    private final FlightSearchAgent flightSearchAgent;

    /**
     * 항공편을 검색하여 항공사별로 정리합니다.
     *
     * <p>Spring AI MCP Tool 예시:
     * <pre>
     * 사용자: "내일 광주에서 제주로 가는 항공편 알려줘"
     * AI 모델: searchFlightsByAirline(departure="광주", arrival="제주", date="내일") 호출
     * </pre>
     *
     * @param departure 출발 공항 이름 (예: "광주", "김포", "제주")
     * @param arrival   도착 공항 이름 (예: "제주", "김포", "부산")
     * @param date      날짜 (예: "2025-03-09", "내일", "모레")
     * @return 항공사별로 그룹핑된 항공편 정보
     */
    @Tool(
            description = "항공편을 검색하여 항공사별로 그룹핑하여 반환합니다. " +
                    "출발 공항, 도착 공항, 날짜를 받아 항공사별로 정리된 항공편 목록을 제공합니다. " +
                    "날짜는 '내일', '모레', '2025-03-09' 형식을 지원합니다. " +
                    "빠른 응답을 위해 항공사별 최대 3편만 반환합니다."
    )
    public Map<String, List<FlightInfoResponse>> searchFlightsByAirline(
            @ToolParam(description = "출발 공항 이름 (예: 광주, 김포, 제주)") String departure,
            @ToolParam(description = "도착 공항 이름 (예: 제주, 김포, 부산)") String arrival,
            @ToolParam(description = "날짜 (예: 내일, 모레, 2025-03-09)") String date) {

        log.info("MCP Tool 호출: searchFlightsByAirline(departure={}, arrival={}, date={})",
                departure, arrival, date);

        // A2A: Coordinator → FlightSearchAgent
        Map<String, List<FlightInfoResponse>> allFlights = flightSearchAgent.searchAndGroupByAirline(departure, arrival, date);

        // 빠른 응답을 위해 항공사별 최대 3편만 반환 (LLM 타임아웃 방지)
        Map<String, List<FlightInfoResponse>> limitedFlights = new HashMap<>();
        allFlights.forEach((airline, flights) -> {
            if (flights.size() > 3) {
                limitedFlights.put(airline, flights.subList(0, 3));
            } else {
                limitedFlights.put(airline, flights);
            }
        });

        log.info("MCP Tool 응답: {}개 항공사, {}편 (제한됨)",
                limitedFlights.size(),
                limitedFlights.values().stream().mapToInt(List::size).sum());

        return limitedFlights;
    }

    /**
     * 특정 시간 이후 출발하는 항공편을 검색합니다.
     *
     * <p>Spring AI MCP Tool 예시:
     * <pre>
     * 사용자: "오후 2시 이후로 출발하는 광주에서 제주행 스케줄을 검색해줘"
     * AI 모델: searchFlightsAfterTime(departure="광주", arrival="제주", date="내일", afterTime="14:00") 호출
     * </pre>
     *
     * @param departure 출발 공항 이름
     * @param arrival   도착 공항 이름
     * @param date      날짜
     * @param afterTime 기준 시간 (예: "14:00", "오후 2시")
     * @return 항공사별로 그룹핑된 항공편 정보
     */
    @Tool(
            description = "특정 시간 이후 출발하는 항공편을 검색하여 항공사별로 그룹핑합니다. " +
                    "시간 조건을 만족하는 항공편만 필터링하여 제공합니다. " +
                    "시간 형식은 '14:00', '오후 2시' 등을 지원합니다."
    )
    public Map<String, List<FlightInfoResponse>> searchFlightsAfterTime(
            @ToolParam(description = "출발 공항 이름 (예: 광주, 김포, 제주)") String departure,
            @ToolParam(description = "도착 공항 이름 (예: 제주, 김포, 부산)") String arrival,
            @ToolParam(description = "날짜 (예: 내일, 모레, 2025-03-09)") String date,
            @ToolParam(description = "기준 시간 (예: 14:00, 오후 2시)") String afterTime) {

        log.info("MCP Tool 호출: searchFlightsAfterTime(departure={}, arrival={}, date={}, afterTime={})",
                departure, arrival, date, afterTime);

        // A2A: Coordinator → TimeFilterAgent → FlightSearchAgent → GroupingAgent
        return flightSearchAgent.searchWithTimeFilter(departure, arrival, date, afterTime);
    }

    /**
     * 특정 가격 범위 내의 항공편을 검색합니다.
     *
     * <p>Spring AI MCP Tool 예시:
     * <pre>
     * 사용자: "5만원에서 7만원 사이의 항공편 알려줘"
     * AI 모델: searchFlightsByPriceRange(departure="광주", arrival="제주", date="내일", minPrice=50000, maxPrice=70000) 호출
     * </pre>
     *
     * @param departure 출발 공항 이름
     * @param arrival   도착 공항 이름
     * @param date      날짜
     * @param minPrice  최소 가격 (선택)
     * @param maxPrice  최대 가격 (선택)
     * @return 항공사별로 그룹핑된 항공편 정보
     */
    @Tool(
            description = "특정 가격 범위 내의 항공편을 검색하여 항공사별로 그룹핑합니다. " +
                    "예산에 맞는 항공편만 필터링하여 제공합니다. " +
                    "최소 가격과 최대 가격은 선택 사항입니다."
    )
    public Map<String, List<FlightInfoResponse>> searchFlightsByPriceRange(
            @ToolParam(description = "출발 공항 이름 (예: 광주, 김포, 제주)") String departure,
            @ToolParam(description = "도착 공항 이름 (예: 제주, 김포, 부산)") String arrival,
            @ToolParam(description = "날짜 (예: 내일, 모레, 2025-03-09)") String date,
            @ToolParam(description = "최소 가격 (선택사항, 예: 30000)") Integer minPrice,
            @ToolParam(description = "최대 가격 (선택사항, 예: 70000)") Integer maxPrice) {

        log.info("MCP Tool 호출: searchFlightsByPriceRange(departure={}, arrival={}, date={}, minPrice={}, maxPrice={})",
                departure, arrival, date, minPrice, maxPrice);

        // A2A: Coordinator → PriceFilterAgent → FlightSearchAgent → GroupingAgent
        return flightSearchAgent.searchWithPriceFilter(departure, arrival, date, minPrice, maxPrice);
    }
}

package net.java21.flyschedule.mcp;

import net.java21.flyschedule.dto.response.FlightInfoResponse;
import net.java21.flyschedule.service.agent.FlightSearchAgent;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 항공편 검색 MCP Tool
 *
 * Spring AI의 @Tool annotation을 사용하여 AI 모델이 호출할 수 있는 도구를 정의합니다.
 * 실제 검색은 전문 Agent(FlightSearchAgent)에게 맡기고 결과를 반환합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlightSearchTool {

    static final int MAX_FLIGHTS_PER_AIRLINE = 3;

    private final FlightSearchAgent flightSearchAgent;

    /**
     * 항공편을 검색하여 항공사별로 정리합니다.
     *
     * <p>예시:
     * <pre>
     * 사용자: "내일 광주에서 제주로 가는 항공편 알려줘"
     * AI 모델: searchFlightsByAirline(departure="광주", arrival="제주", date="2026-03-25") 호출
     * </pre>
     *
     * @param departure 출발 공항 이름 (예: "광주", "김포", "제주")
     * @param arrival   도착 공항 이름 (예: "제주", "김포", "부산")
     * @param date      날짜 (YYYY-MM-DD, 예: "2026-03-25")
     * @return 항공사별로 그룹핑된 항공편 정보
     */
    @Tool(
            description = "항공편을 검색하여 항공사별로 그룹핑하여 반환합니다. " +
                    "출발 공항, 도착 공항, 날짜를 받아 항공사별로 정리된 항공편 목록을 제공합니다. " +
                    "날짜는 '2026-03-25'(YYYY-MM-DD) 형식으로 전달합니다. " +
                    "항공사별 최대 3편만 반환합니다."
    )
    public Map<String, List<FlightInfoResponse>> searchFlightsByAirline(
            @ToolParam(description = "출발 공항 이름 (예: 광주, 김포, 제주)") String departure,
            @ToolParam(description = "도착 공항 이름 (예: 제주, 김포, 부산)") String arrival,
            @ToolParam(description = "날짜, YYYY-MM-DD 형식 (예: 2026-03-25)") String date) {

        log.info("MCP Tool 호출: searchFlightsByAirline(departure={}, arrival={}, date={})",
                departure, arrival, date);

        return ToolResultCapture.capture("searchFlightsByAirline",
                "%s → %s, %s".formatted(departure, arrival, date),
                () -> limitPerAirline(flightSearchAgent.searchAndGroupByAirline(departure, arrival, date)));
    }

    /**
     * 특정 시간 이후 출발하는 항공편을 검색합니다.
     *
     * <p>예시:
     * <pre>
     * 사용자: "오후 2시 이후로 출발하는 광주에서 제주행 스케줄을 검색해줘"
     * AI 모델: searchFlightsAfterTime(departure="광주", arrival="제주", date="2026-03-25", afterTime="14:00") 호출
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
                    "시간 형식은 '14:00', '오후 2시' 등을 지원합니다. 날짜는 YYYY-MM-DD 형식이며, 항공사별 최대 3편만 반환합니다."
    )
    public Map<String, List<FlightInfoResponse>> searchFlightsAfterTime(
            @ToolParam(description = "출발 공항 이름 (예: 광주, 김포, 제주)") String departure,
            @ToolParam(description = "도착 공항 이름 (예: 제주, 김포, 부산)") String arrival,
            @ToolParam(description = "날짜, YYYY-MM-DD 형식 (예: 2026-03-25)") String date,
            @ToolParam(description = "기준 시간 (예: 14:00, 오후 2시)") String afterTime) {

        log.info("MCP Tool 호출: searchFlightsAfterTime(departure={}, arrival={}, date={}, afterTime={})",
                departure, arrival, date, afterTime);

        return ToolResultCapture.capture("searchFlightsAfterTime",
                "%s → %s, %s, %s 이후".formatted(departure, arrival, date, afterTime),
                () -> limitPerAirline(flightSearchAgent.searchWithTimeFilter(departure, arrival, date, afterTime)));
    }

    /**
     * 특정 가격 범위 내의 항공편을 검색합니다.
     *
     * <p>예시:
     * <pre>
     * 사용자: "5만원에서 7만원 사이의 항공편 알려줘"
     * AI 모델: searchFlightsByPriceRange(departure="광주", arrival="제주", date="2026-03-25", minPrice=50000, maxPrice=70000) 호출
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
                    "최소 가격과 최대 가격은 선택 사항입니다. 날짜는 YYYY-MM-DD 형식이며, 항공사별 최대 3편만 반환합니다."
    )
    public Map<String, List<FlightInfoResponse>> searchFlightsByPriceRange(
            @ToolParam(description = "출발 공항 이름 (예: 광주, 김포, 제주)") String departure,
            @ToolParam(description = "도착 공항 이름 (예: 제주, 김포, 부산)") String arrival,
            @ToolParam(description = "날짜, YYYY-MM-DD 형식 (예: 2026-03-25)") String date,
            @ToolParam(description = "최소 가격(원). 조건이 없으면 생략", required = false) Integer minPrice,
            @ToolParam(description = "최대 가격(원). 조건이 없으면 생략", required = false) Integer maxPrice) {

        log.info("MCP Tool 호출: searchFlightsByPriceRange(departure={}, arrival={}, date={}, minPrice={}, maxPrice={})",
                departure, arrival, date, minPrice, maxPrice);

        return ToolResultCapture.capture("searchFlightsByPriceRange",
                "%s → %s, %s, %s".formatted(departure, arrival, date, priceRange(minPrice, maxPrice)),
                () -> limitPerAirline(flightSearchAgent.searchWithPriceFilter(departure, arrival, date, minPrice, maxPrice)));
    }

    private static String priceRange(Integer minPrice, Integer maxPrice) {
        if (minPrice != null && maxPrice != null) {
            return "%,d~%,d원".formatted(minPrice, maxPrice);
        }
        if (minPrice != null) {
            return "%,d원 이상".formatted(minPrice);
        }
        return maxPrice != null ? "%,d원 이하".formatted(maxPrice) : "가격 조건 없음";
    }

    /**
     * LLM에게 돌려줄 결과가 너무 길어지지 않도록 항공사별로 최대 {@value #MAX_FLIGHTS_PER_AIRLINE}편만 남깁니다.
     * (소형 모델은 컨텍스트가 짧아 결과가 길면 응답이 느려지거나 잘립니다.)
     */
    private static Map<String, List<FlightInfoResponse>> limitPerAirline(Map<String, List<FlightInfoResponse>> grouped) {
        Map<String, List<FlightInfoResponse>> limited = new LinkedHashMap<>();
        grouped.forEach((airline, flights) ->
                limited.put(airline, flights.subList(0, Math.min(flights.size(), MAX_FLIGHTS_PER_AIRLINE))));
        log.info("MCP Tool 응답: {}개 항공사, {}편 (항공사별 최대 {}편)", limited.size(),
                limited.values().stream().mapToInt(List::size).sum(), MAX_FLIGHTS_PER_AIRLINE);
        return limited;
    }
}

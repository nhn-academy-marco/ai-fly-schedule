package net.java21.flyschedule.service.agent;

import net.java21.flyschedule.dto.response.FlightInfoResponse;
import net.java21.flyschedule.service.api.ApiClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.*;
import java.util.function.UnaryOperator;

/**
 * 항공편 검색 전문 Agent
 *
 * 여러 하위 Agent를 조율하여 항공편 검색 작업을 수행합니다.
 * - DateParserAgent: 날짜 파싱
 * - AirportCodeAgent: 공항 코드 변환
 * - TimeFilterAgent: 시간 필터링
 * - PriceFilterAgent: 가격 필터링
 * - GroupingAgent: 항공사별 그룹핑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlightSearchAgent {

    private final ApiClientService apiClientService;
    private final DateParserAgent dateParserAgent;
    private final AirportCodeAgent airportCodeAgent;
    private final TimeFilterAgent timeFilterAgent;
    private final PriceFilterAgent priceFilterAgent;
    private final GroupingAgent groupingAgent;

    /**
     * 항공편을 검색하여 항공사별로 그룹핑합니다.
     *
     * @param departure 출발 공항 이름
     * @param arrival   도착 공항 이름
     * @param date      날짜
     * @return 항공사별로 그룹핑된 항공편
     */
    public Map<String, List<FlightInfoResponse>> searchAndGroupByAirline(
            String departure,
            String arrival,
            String date) {

        log.info("FlightSearchAgent: 항공편 검색 시작");

        // 1. DateParserAgent: 날짜 파싱
        String parsedDate = dateParserAgent.parseDate(date);
        log.info("DateParserAgent: {} → {}", date, parsedDate);

        // 2. AirportCodeAgent: 공항 코드 변환
        String depAirportId = airportCodeAgent.getAirportCode(departure);
        String arrAirportId = airportCodeAgent.getAirportCode(arrival);
        log.info("AirportCodeAgent: {} → {}, {} → {}", departure, depAirportId, arrival, arrAirportId);

        // 3. API 호출
        List<FlightInfoResponse> flights = apiClientService.getFlightSchedule(
                depAirportId, arrAirportId, parsedDate);

        // 4. GroupingAgent: 항공사별 그룹핑
        Map<String, List<FlightInfoResponse>> grouped = groupingAgent.groupByAirline(flights);
        log.info("GroupingAgent: {}개 항공사로 그룹핑 완료", grouped.size());

        return grouped;
    }

    /**
     * 시간 필터를 적용하여 항공편을 검색합니다.
     *
     * @param departure 출발 공항 이름
     * @param arrival   도착 공항 이름
     * @param date      날짜
     * @param afterTime 기준 시간
     * @return 항공사별로 그룹핑된 항공편
     */
    public Map<String, List<FlightInfoResponse>> searchWithTimeFilter(
            String departure,
            String arrival,
            String date,
            String afterTime) {

        log.info("FlightSearchAgent: 시간 필터링 검색 시작");

        // 기본 검색 수행
        Map<String, List<FlightInfoResponse>> grouped = searchAndGroupByAirline(departure, arrival, date);

        // TimeFilterAgent: 시간 필터링
        LocalTime filterTime = timeFilterAgent.parseTime(afterTime);
        log.info("TimeFilterAgent: {} 이후 출발 항공편 필터링", filterTime);

        // 각 항공사별로 필터링 적용
        return filterEachAirline(grouped, flights -> timeFilterAgent.filterAfterTime(flights, filterTime));
    }

    /**
     * 가격 필터를 적용하여 항공편을 검색합니다.
     *
     * @param departure 출발 공항 이름
     * @param arrival   도착 공항 이름
     * @param date      날짜
     * @param minPrice  최소 가격
     * @param maxPrice  최대 가격
     * @return 항공사별로 그룹핑된 항공편
     */
    public Map<String, List<FlightInfoResponse>> searchWithPriceFilter(
            String departure,
            String arrival,
            String date,
            Integer minPrice,
            Integer maxPrice) {

        log.info("FlightSearchAgent: 가격 필터링 검색 시작");

        // 기본 검색 수행
        Map<String, List<FlightInfoResponse>> grouped = searchAndGroupByAirline(departure, arrival, date);

        // PriceFilterAgent: 가격 필터링
        log.info("PriceFilterAgent: {}원 ~ {}원 범위 필터링", minPrice, maxPrice);

        // 각 항공사별로 필터링 적용
        return filterEachAirline(grouped, flights -> priceFilterAgent.filterByPriceRange(flights, minPrice, maxPrice));
    }

    /**
     * 항공사별 목록에 필터를 적용하고, 남은 항공편이 없는 항공사는 결과에서 뺍니다.
     */
    private Map<String, List<FlightInfoResponse>> filterEachAirline(
            Map<String, List<FlightInfoResponse>> grouped,
            UnaryOperator<List<FlightInfoResponse>> filter) {
        Map<String, List<FlightInfoResponse>> filtered = new TreeMap<>();
        grouped.forEach((airline, flights) -> {
            List<FlightInfoResponse> remaining = filter.apply(flights);
            if (!remaining.isEmpty()) {
                filtered.put(airline, remaining);
            }
        });
        return filtered;
    }
}

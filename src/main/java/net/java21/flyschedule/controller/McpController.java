package net.java21.flyschedule.controller;

import net.java21.flyschedule.dto.response.ApiResponse;
import net.java21.flyschedule.dto.response.FlightInfoResponse;
import net.java21.flyschedule.mcp.AirlineInfoTool;
import net.java21.flyschedule.mcp.AirportInfoTool;
import net.java21.flyschedule.mcp.FlightSearchTool;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * MCP 컨트롤러
 *
 * AI 모델이 호출하는 REST API 엔드포인트를 제공합니다.
 * 이 컨트롤러는 MCP Tool들을 노출하는 역할을 합니다.
 */
@RestController
@RequestMapping("/api/mcp")
@RequiredArgsConstructor
public class McpController {

    private final FlightSearchTool flightSearchTool;
    private final AirportInfoTool airportInfoTool;
    private final AirlineInfoTool airlineInfoTool;

    /**
     * 항공편 검색 (항공사별 그룹핑)
     *
     * @param departure 출발 공항 이름
     * @param arrival   도착 공항 이름
     * @param date      날짜
     * @return 항공사별로 그룹핑된 항공편
     */
    @GetMapping("/flights/search")
    public ApiResponse<Map<String, List<FlightInfoResponse>>> searchFlights(
            @RequestParam String departure,
            @RequestParam String arrival,
            @RequestParam String date) {

        Map<String, List<FlightInfoResponse>> result =
                flightSearchTool.searchFlightsByAirline(departure, arrival, date);

        return ApiResponse.success(result);
    }

    /**
     * 시간 필터 항공편 검색
     *
     * @param departure 출발 공항 이름
     * @param arrival   도착 공항 이름
     * @param date      날짜
     * @param afterTime 기준 시간
     * @return 항공사별로 그룹핑된 항공편
     */
    @GetMapping("/flights/search/time")
    public ApiResponse<Map<String, List<FlightInfoResponse>>> searchFlightsWithTime(
            @RequestParam String departure,
            @RequestParam String arrival,
            @RequestParam String date,
            @RequestParam String afterTime) {

        Map<String, List<FlightInfoResponse>> result =
                flightSearchTool.searchFlightsAfterTime(departure, arrival, date, afterTime);

        return ApiResponse.success(result);
    }

    /**
     * 가격 필터 항공편 검색
     *
     * @param departure 출발 공항 이름
     * @param arrival   도착 공항 이름
     * @param date      날짜
     * @param minPrice  최소 가격
     * @param maxPrice  최대 가격
     * @return 항공사별로 그룹핑된 항공편
     */
    @GetMapping("/flights/search/price")
    public ApiResponse<Map<String, List<FlightInfoResponse>>> searchFlightsWithPrice(
            @RequestParam String departure,
            @RequestParam String arrival,
            @RequestParam String date,
            @RequestParam(required = false) Integer minPrice,
            @RequestParam(required = false) Integer maxPrice) {

        Map<String, List<FlightInfoResponse>> result =
                flightSearchTool.searchFlightsByPriceRange(departure, arrival, date, minPrice, maxPrice);

        return ApiResponse.success(result);
    }

    /**
     * 공항 목록 조회
     *
     * @return 공항 정보 목록
     */
    @GetMapping("/airports")
    public ApiResponse<List<Map<String, String>>> getAirports() {
        // 공항 정보를 간단한 Map 형태로 변환
        List<Map<String, String>> airports = airportInfoTool.getAirportList().stream()
                .map(airport -> Map.of(
                        "airportId", airport.getAirportId(),
                        "airportName", airport.getAirportName()
                ))
                .toList();

        return ApiResponse.success(airports);
    }

    /**
     * 공항 코드 조회
     *
     * @param airportName 공항 이름
     * @return 공항 코드
     */
    @GetMapping("/airports/code")
    public ApiResponse<Map<String, String>> getAirportCode(@RequestParam String airportName) {
        String code = airportInfoTool.getAirportCode(airportName);
        return ApiResponse.success(Map.of("airportName", airportName, "airportCode", code));
    }

    /**
     * 항공사 목록 조회
     *
     * @return 항공사 정보 목록
     */
    @GetMapping("/airlines")
    public ApiResponse<List<Map<String, String>>> getAirlines() {
        // 항공사 정보를 간단한 Map 형태로 변환
        List<Map<String, String>> airlines = airlineInfoTool.getAirlineList().stream()
                .map(airline -> Map.of(
                        "airlineId", airline.getAirlineId(),
                        "airlineName", airline.getAirlineName()
                ))
                .toList();

        return ApiResponse.success(airlines);
    }

    /**
     * 항공사 ID 조회
     *
     * @param airlineName 항공사 이름
     * @return 항공사 ID
     */
    @GetMapping("/airlines/id")
    public ApiResponse<Map<String, String>> getAirlineId(@RequestParam String airlineName) {
        String id = airlineInfoTool.getAirlineId(airlineName);
        return ApiResponse.success(Map.of("airlineName", airlineName, "airlineId", id));
    }
}

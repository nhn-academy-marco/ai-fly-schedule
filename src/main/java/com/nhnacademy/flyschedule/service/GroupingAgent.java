package com.nhnacademy.flyschedule.service;

import com.nhnacademy.flyschedule.dto.response.FlightInfoResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 그룹핑 A2A 에이전트
 *
 * 항공편을 항공사별로 그룹핑합니다.
 */
@Slf4j
@Service
public class GroupingAgent {

    /**
     * 항공편을 항공사별로 그룹핑합니다.
     *
     * @param flights 전체 항공편 목록
     * @return 항공사명을 키로, 항공편 목록을 값으로 하는 Map
     */
    public Map<String, List<FlightInfoResponse>> groupByAirline(List<FlightInfoResponse> flights) {
        if (flights == null || flights.isEmpty()) {
            return new TreeMap<>();
        }

        Map<String, List<FlightInfoResponse>> grouped = flights.stream()
                .collect(Collectors.groupingBy(
                        flight -> {
                            String airlineName = flight.getAirlineName();
                            // 항공사명이 null이거나 빈 값일 경우 처리
                            if (airlineName == null || airlineName.trim().isEmpty()) {
                                // 항공편 ID에서 항공사 코드 추출 (예: OZ8141 -> OZ)
                                String flightId = flight.getFlightId();
                                if (flightId != null && flightId.length() >= 2) {
                                    String airlineCode = flightId.substring(0, 2);
                                    log.warn("항공사명 없음, 항공편 ID에서 추출: {} -> {}", flightId, airlineCode);
                                    return airlineCode;
                                }
                                log.warn("항공사명 없음, 기본값 사용: {}", flightId);
                                return "알 수 없는 항공사";
                            }
                            return airlineName;
                        },
                        TreeMap::new, // 정렬 순서 유지
                        Collectors.toList()
                ));

        log.info("그룹핑 완료: {}개 항공사, {}개 항공편", grouped.size(), flights.size());

        // 각 항공사별 항공편 수 로그 출력
        grouped.forEach((airlineName, flightList) -> {
            log.info("  - {}: {}편", airlineName, flightList.size());
        });

        return grouped;
    }

    /**
     * 항공편을 출발 시간대별로 그룹핑합니다.
     *
     * @param flights 전체 항공편 목록
     * @return 시간대를 키로, 항공편 목록을 값으로 하는 Map
     */
    public Map<String, List<FlightInfoResponse>> groupByTimeRange(List<FlightInfoResponse> flights) {
        if (flights == null || flights.isEmpty()) {
            return new TreeMap<>();
        }

        Map<String, List<FlightInfoResponse>> grouped = flights.stream()
                .collect(Collectors.groupingBy(
                        flight -> getTimeRange(flight.getDepartureTime()),
                        TreeMap::new,
                        Collectors.toList()
                ));

        return grouped;
    }

    /**
     * 출발 시간 문자열에서 시간대를 반환합니다.
     *
     * @param departureTime 출발 시간 (예: "202503090955")
     * @return 시간대 (아침, 점심, 오후, 저녁)
     */
    private String getTimeRange(String departureTime) {
        if (departureTime == null || departureTime.length() < 12) {
            return "알 수 없음";
        }

        String timePart = departureTime.substring(8, 12);
        int hour = Integer.parseInt(timePart.substring(0, 2));

        if (hour >= 6 && hour < 12) {
            return "아침 (06:00-12:00)";
        } else if (hour >= 12 && hour < 14) {
            return "점심 (12:00-14:00)";
        } else if (hour >= 14 && hour < 18) {
            return "오후 (14:00-18:00)";
        } else if (hour >= 18 && hour < 22) {
            return "저녁 (18:00-22:00)";
        } else {
            return "밤 (22:00-06:00)";
        }
    }
}

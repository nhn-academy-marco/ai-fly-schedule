package com.nhnacademy.flyschedule.service.agent;

import com.nhnacademy.flyschedule.dto.response.AirlineInfoResponse;
import com.nhnacademy.flyschedule.service.api.ApiClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 항공사 목록 조회 A2A 에이전트
 *
 * 항공사 정보를 조회하고 항공사 코드를 매핑합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AirlineListAgent {

    private final ApiClientService apiClientService;

    /**
     * 전체 항공사 목록을 조회합니다.
     *
     * @return 항공사 정보 목록
     */
    public List<AirlineInfoResponse> getAirlineList() {
        log.info("AirlineListAgent: 항공사 목록 조회");

        List<AirlineInfoResponse> airlines = apiClientService.getAirlineList();

        log.info("항공사 {}개 조회 완료", airlines.size());

        return airlines;
    }

    /**
     * 항공사 이름으로 항공사 ID를 조회합니다.
     *
     * @param airlineName 항공사 이름
     * @return 항공사 ID
     */
    public String getAirlineId(String airlineName) {
        log.info("AirlineListAgent: 항공사 ID 조회 - {}", airlineName);

        // 항공사 목록에서 검색
        List<AirlineInfoResponse> airlines = apiClientService.getAirlineList();

        return airlines.stream()
                .filter(airline -> airline.getAirlineName().contains(airlineName))
                .findFirst()
                .map(AirlineInfoResponse::getAirlineId)
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 항공사입니다: " + airlineName));
    }
}

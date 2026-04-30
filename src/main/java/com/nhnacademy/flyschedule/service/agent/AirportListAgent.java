package com.nhnacademy.flyschedule.service.agent;

import com.nhnacademy.flyschedule.dto.response.AirportInfoResponse;
import com.nhnacademy.flyschedule.service.api.ApiClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 공항 목록 조회 A2A 에이전트
 *
 * 공항 정보를 조회하고 공항 코드를 매핑합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AirportListAgent {

    private final ApiClientService apiClientService;
    private final AirportCodeAgent airportCodeAgent;

    /**
     * 전체 공항 목록을 조회합니다.
     *
     * @return 공항 정보 목록
     */
    public List<AirportInfoResponse> getAirportList() {
        log.info("AirportListAgent: 공항 목록 조회");

        List<AirportInfoResponse> airports = apiClientService.getAirportList();

        log.info("공항 {}개 조회 완료", airports.size());

        return airports;
    }

    /**
     * 공항 이름으로 공항 코드를 조회합니다.
     *
     * @param airportName 공항 이름
     * @return 공항 코드
     */
    public String getAirportCode(String airportName) {
        log.info("AirportListAgent: 공항 코드 조회 - {}", airportName);

        return airportCodeAgent.getAirportCode(airportName);
    }
}

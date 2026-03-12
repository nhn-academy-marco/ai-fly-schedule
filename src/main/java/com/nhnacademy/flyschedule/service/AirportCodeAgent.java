package com.nhnacademy.flyschedule.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 공항 코드 매핑 A2A 에이전트
 *
 * 공항 이름을 공항 코드로 변환합니다.
 */
@Slf4j
@Service
public class AirportCodeAgent {

    private static final Map<String, String> AIRPORT_CODE_MAP = new HashMap<>();

    static {
        // 수도권
        AIRPORT_CODE_MAP.put("김포", "NAARKSS");
        AIRPORT_CODE_MAP.put("김포공항", "NAARKSS");
        AIRPORT_CODE_MAP.put("인천", "NAARKSI");
        AIRPORT_CODE_MAP.put("인천공항", "NAARKSI");

        // 부산/경남권
        AIRPORT_CODE_MAP.put("김해", "NAARKJB");
        AIRPORT_CODE_MAP.put("김해공항", "NAARKJB");
        AIRPORT_CODE_MAP.put("부산", "NAARKJB");
        AIRPORT_CODE_MAP.put("사천", "NAARKPS");
        AIRPORT_CODE_MAP.put("사천공항", "NAARKPS");
        AIRPORT_CODE_MAP.put("울산", "NAARKNU");
        AIRPORT_CODE_MAP.put("울산공항", "NAARKNU");

        // 호남권
        AIRPORT_CODE_MAP.put("광주", "NAARKJJ");
        AIRPORT_CODE_MAP.put("광주공항", "NAARKJJ");
        AIRPORT_CODE_MAP.put("여수", "NAARKJY");
        AIRPORT_CODE_MAP.put("여수공항", "NAARKJY");
        AIRPORT_CODE_MAP.put("무안", "NAARKJJ");
        AIRPORT_CODE_MAP.put("무안공항", "NAARKJJ");

        // 영남권
        AIRPORT_CODE_MAP.put("대구", "NAARKTN");
        AIRPORT_CODE_MAP.put("대구공항", "NAARKTN");
        AIRPORT_CODE_MAP.put("포항", "NAARKPK");
        AIRPORT_CODE_MAP.put("포항공항", "NAARKPK");

        // 충청/강원권
        AIRPORT_CODE_MAP.put("청주", "NAARKNJ");
        AIRPORT_CODE_MAP.put("청주공항", "NAARKNJ");
        AIRPORT_CODE_MAP.put("양양", "NAARKNY");
        AIRPORT_CODE_MAP.put("양양공항", "NAARKNY");

        // 제주권
        AIRPORT_CODE_MAP.put("제주", "NAARKPC");
        AIRPORT_CODE_MAP.put("제주공항", "NAARKPC");
    }

    /**
     * 공항 이름을 공항 코드로 변환합니다.
     *
     * @param airportName 공항 이름 (예: "광주", "김포", "제주") 또는 공항 코드 (예: "NAARKJJ")
     * @return 공항 코드 (예: "NAARKJJ", "NAARKSS", "NAARKPC")
     * @throws IllegalArgumentException 알 수 없는 공항
     */
    public String getAirportCode(String airportName) {
        if (airportName == null || airportName.isBlank()) {
            throw new IllegalArgumentException("공항 이름을 입력해주세요.");
        }

        String normalized = airportName.trim();

        // 이미 공항 코드 형식인 경우 (NAARKSS, NAARKJJ 등)
        if (normalized.matches("NAARK[A-Z]{2}")) {
            log.info("공항 코드 입력됨: {}", normalized);
            return normalized;
        }

        String code = AIRPORT_CODE_MAP.get(normalized);

        if (code == null) {
            log.warn("알 수 없는 공항: {}", airportName);
            throw new IllegalArgumentException("알 수 없는 공항입니다: " + airportName);
        }

        return code;
    }

    /**
     * 공항 코드가 유효한지 확인합니다.
     *
     * @param airportName 공항 이름
     * @return 유효 여부
     */
    public boolean isValidAirport(String airportName) {
        if (airportName == null || airportName.isBlank()) {
            return false;
        }
        return AIRPORT_CODE_MAP.containsKey(airportName.trim());
    }
}

package net.java21.flyschedule.service.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 공항 코드 매핑 전문 Agent
 *
 * 공항 이름을 공항 코드로 변환합니다.
 */
@Slf4j
@Service
public class AirportCodeAgent {

    /**
     * 공항 이름 → 공항 코드
     *
     * <p>코드는 공공데이터 포털 국내항공운항정보 API의 공항 목록(GetArprtList) 기준입니다.
     * "광주"와 "광주공항"처럼 "공항"이 붙은 이름도 함께 등록합니다.
     */
    private static final Map<String, String> AIRPORT_CODE_MAP = new HashMap<>();

    static {
        // 수도권
        register("NAARKSS", "김포");
        register("NAARKSI", "인천");

        // 부산/경남권
        register("NAARKPK", "김해", "부산");
        register("NAARKPS", "사천");
        register("NAARKPU", "울산");

        // 호남권
        register("NAARKJJ", "광주");
        register("NAARKJY", "여수");
        register("NAARKJB", "무안");
        register("NAARKJK", "군산");

        // 영남권
        register("NAARKTN", "대구");
        register("NAARKTH", "포항", "포항경주");

        // 충청/강원권
        register("NAARKTU", "청주");
        register("NAARKNY", "양양");
        register("NAARKNW", "원주");

        // 제주권
        register("NAARKPC", "제주");
    }

    private static void register(String code, String... names) {
        for (String name : names) {
            AIRPORT_CODE_MAP.put(name, code);
            AIRPORT_CODE_MAP.put(name + "공항", code);
            AIRPORT_CODE_MAP.put(name + "국제공항", code);
        }
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

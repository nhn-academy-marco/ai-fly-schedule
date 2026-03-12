package com.nhnacademy.flyschedule.llm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 항공편 추천 결과 (Structured Output용 Record)
 *
 * LLM이 생성한 추천을 Java 객체로 매핑합니다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightRecommendation {

    /**
     * 추천 사유
     */
    private String reason;

    /**
     * 추천 항공편 목록
     */
    private List<RecommendedFlight> flights;

    /**
     * 요약
     */
    private String summary;

    /**
     * 추천 항공편
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendedFlight {
        private String flightId;
        private String airlineName;
        private String departureTime;
        private String arrivalTime;
        private Integer price;
        private String recommendation;
    }
}

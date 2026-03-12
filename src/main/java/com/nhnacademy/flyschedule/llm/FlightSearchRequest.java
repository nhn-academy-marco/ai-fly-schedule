package com.nhnacademy.flyschedule.llm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 항공편 검색 요청 (Structured Output용 Record)
 *
 * LLM의 응답을 Java 객체로 매핑하기 위한 DTO입니다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightSearchRequest {

    /**
     * 출발 공항
     */
    private String departure;

    /**
     * 도착 공항
     */
    private String arrival;

    /**
     * 날짜
     */
    private String date;

    /**
     * 기준 시간 (선택)
     */
    private String afterTime;

    /**
     * 최소 가격 (선택)
     */
    private Integer minPrice;

    /**
     * 최대 가격 (선택)
     */
    private Integer maxPrice;

    /**
     * 검색 의도
     */
    private String intent;
}

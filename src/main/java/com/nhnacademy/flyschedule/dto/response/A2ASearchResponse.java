package com.nhnacademy.flyschedule.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * A2A 검색 응답 DTO (API 최종 응답용)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class A2ASearchResponse {

    private boolean success;
    private String message;
    private Map<String, List<FlightInfoResponse>> data;

    public static A2ASearchResponse success(String message,
                                             Map<String, List<FlightInfoResponse>> data) {
        return A2ASearchResponse.builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    public static A2ASearchResponse fail(String message) {
        return A2ASearchResponse.builder()
                .success(false)
                .message(message)
                .data(Map.of())
                .build();
    }
}

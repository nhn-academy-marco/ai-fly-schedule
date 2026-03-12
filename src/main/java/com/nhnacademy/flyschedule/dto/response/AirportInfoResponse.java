package com.nhnacademy.flyschedule.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 공항 정보 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AirportInfoResponse {

    @JsonProperty("airportId")
    private String airportId;

    @JsonProperty("airportNm")
    private String airportName;

    @JsonProperty("region")
    private String region;
}

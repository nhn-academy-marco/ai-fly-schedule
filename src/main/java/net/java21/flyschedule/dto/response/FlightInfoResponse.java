package net.java21.flyschedule.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 항공편 정보 응답 DTO
 *
 * 공공데이터 포털 API 응답을 매핑합니다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightInfoResponse {

    /**
     * 항공편명 (예: OZ8141)
     */
    @JsonProperty("vihicleId")
    private String flightId;

    /**
     * 항공사명 (예: 아시아나항공)
     */
    @JsonProperty("airlineNm")
    private String airlineName;

    /**
     * 출발시간 (예: 202503090955)
     */
    @JsonProperty("depPlandTime")
    private String departureTime;

    /**
     * 도착시간 (예: 202503091045)
     */
    @JsonProperty("arrPlandTime")
    private String arrivalTime;

    /**
     * 일반석 운임
     */
    @JsonProperty("economyCharge")
    private Integer economyCharge;

    /**
     * 비즈니스석 운임
     */
    @JsonProperty("prestigeCharge")
    private Integer prestigeCharge;

    /**
     * 출발공항명
     */
    @JsonProperty("depAirportNm")
    private String departureAirport;

    /**
     * 도착공항명
     */
    @JsonProperty("arrAirportNm")
    private String arrivalAirport;

    /**
     * 항공사 ID
     */
    @JsonProperty("airlineId")
    private String airlineId;
}

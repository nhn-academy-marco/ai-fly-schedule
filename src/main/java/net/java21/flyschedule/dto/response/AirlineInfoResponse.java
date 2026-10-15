package net.java21.flyschedule.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 항공사 정보 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AirlineInfoResponse {

    @JsonProperty("airlineId")
    private String airlineId;

    @JsonProperty("airlineNm")
    private String airlineName;
}

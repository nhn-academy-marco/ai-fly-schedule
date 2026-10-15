package net.java21.flyschedule.integration;

import net.java21.flyschedule.service.*;
import net.java21.flyschedule.service.agent.AirportCodeAgent;
import net.java21.flyschedule.service.agent.DateParserAgent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring Boot 통합 테스트
 *
 * 개별 서비스 빈 동작을 검증합니다.
 */
@DisplayName("서비스 단위 테스트")
class ServiceIntegrationTest {

    @Test
    @DisplayName("DateParserAgent가 정상적으로 생성되어야 한다")
    void dateParserAgent_instantiated() {
        // Given
        DateParserAgent dateParserAgent = new DateParserAgent();

        // When
        String result = dateParserAgent.parseDate("내일");

        // Then
        assertThat(result).hasSize(8); // YYYYMMDD
    }

    @Test
    @DisplayName("AirportCodeAgent가 정상적으로 생성되어야 한다")
    void airportCodeAgent_instantiated() {
        // Given
        AirportCodeAgent airportCodeAgent = new AirportCodeAgent();

        // When
        String result = airportCodeAgent.getAirportCode("광주");

        // Then
        assertThat(result).isEqualTo("NAARKJJ");
    }

    @Test
    @DisplayName("AirportCodeAgent의 유효성 검증이 정상 동작해야 한다")
    void airportCodeAgent_validationWorks() {
        // Given
        AirportCodeAgent airportCodeAgent = new AirportCodeAgent();

        // When
        boolean valid = airportCodeAgent.isValidAirport("광주");
        boolean invalid = airportCodeAgent.isValidAirport("파리");

        // Then
        assertThat(valid).isTrue();
        assertThat(invalid).isFalse();
    }
}

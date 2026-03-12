package com.nhnacademy.flyschedule.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AirportCodeAgent 단위 테스트
 *
 * 공항 코드 매핑 기능을 검증합니다.
 */
@DisplayName("AirportCodeAgent 단위 테스트")
class AirportCodeAgentTest {

    private final AirportCodeAgent airportCodeAgent = new AirportCodeAgent();

    @ParameterizedTest
    @CsvSource({
            "'김포', 'NAARKSS'",
            "'김포공항', 'NAARKSS'",
            "'인천', 'NAARKSI'",
            "'인천공항', 'NAARKSI'",
            "'광주', 'NAARKJJ'",
            "'광주공항', 'NAARKJJ'",
            "'제주', 'NAARKPC'",
            "'제주공항', 'NAARKPC'",
            "'부산', 'NAARKJB'",
            "'김해', 'NAARKJB'",
            "'대구', 'NAARKTN'",
            "'청주', 'NAARKNJ'"
    })
    @DisplayName("공항 이름을 정확한 코드로 변환해야 한다")
    void getAirportCode_validAirports_returnsCorrectCode(String airportName, String expectedCode) {
        // When
        String result = airportCodeAgent.getAirportCode(airportName);

        // Then
        assertThat(result).isEqualTo(expectedCode);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("null 또는 빈 입력은 예외를 발생시켜야 한다")
    void getAirportCode_nullOrEmptyInput_throwsException(String airportName) {
        // When & Then
        assertThatThrownBy(() -> airportCodeAgent.getAirportCode(airportName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("공항 이름을 입력해주세요");
    }

    @Test
    @DisplayName("알 수 없는 공항 이름은 예외를 발생시켜야 한다")
    void getAirportCode_unknownAirport_throwsException() {
        // When & Then
        assertThatThrownBy(() -> airportCodeAgent.getAirportCode("파리"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("알 수 없는 공항입니다");
    }

    @ParameterizedTest
    @CsvSource({
            "'김포', true",
            "'인천', true",
            "'제주', true",
            "'파리', false",
            "'도쿄', false",
            "'', false"
    })
    @DisplayName("공항 코드 유효성을 정확히 검증해야 한다")
    void isValidAirport_variousInputs(String airportName, boolean expected) {
        // When
        boolean result = airportCodeAgent.isValidAirport(airportName);

        // Then
        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("null 입력에 대한 유효성 검증은 false를 반환해야 한다")
    void isValidAirport_nullInput_returnsFalse() {
        // When
        boolean result = airportCodeAgent.isValidAirport(null);

        // Then
        assertThat(result).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"무안", "무안공항", "여수", "여수공항", "양양", "양양공항", "포항", "포항공항"})
    @DisplayName("지방 공항 코드도 정확히 매핑되어야 한다")
    void getAirportCode_regionalAirports_returnsCorrectCode(String airportName) {
        // When
        String result = airportCodeAgent.getAirportCode(airportName);

        // Then
        assertThat(result).hasSize(7); // All airport codes are 7 characters
        assertThat(result).startsWith("NAARK");
    }

    @Test
    @DisplayName("공백이 포함된 공항 이름을 정확히 처리해야 한다")
    void getAirportCode_airportNameWithWhitespace() {
        // When
        String result = airportCodeAgent.getAirportCode("  김포  ");

        // Then
        assertThat(result).isEqualTo("NAARKSS");
    }
}

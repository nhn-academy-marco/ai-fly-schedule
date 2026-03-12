package com.nhnacademy.flyschedule.service;

import com.nhnacademy.flyschedule.dto.response.FlightInfoResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TimeFilterAgent 단위 테스트
 *
 * 시간 필터링 기능을 검증합니다.
 */
@DisplayName("TimeFilterAgent 단위 테스트")
class TimeFilterAgentTest {

    private TimeFilterAgent timeFilterAgent;

    @BeforeEach
    void setUp() {
        timeFilterAgent = new TimeFilterAgent();
    }

    @Test
    @DisplayName("HH:mm 형식의 시간을 정확히 파싱해야 한다")
    void parseTime_validFormat_returnsLocalTime() {
        // When
        LocalTime result = timeFilterAgent.parseTime("14:30");

        // Then
        assertThat(result).isEqualTo(LocalTime.of(14, 30));
    }

    @Test
    @DisplayName("HHmm 형식의 시간을 정확히 파싱해야 한다")
    void parseTime_withoutColon_returnsLocalTime() {
        // When
        LocalTime result = timeFilterAgent.parseTime("1430");

        // Then
        assertThat(result).isEqualTo(LocalTime.of(14, 30));
    }

    @Test
    @DisplayName("'오후 N시' 형식을 정확히 파싱해야 한다 (공백 포함)")
    void parseTime_afternoonFormatWithSpace_returnsLocalTime() {
        // When - 공백이 있어야 정상 작동함
        LocalTime result = timeFilterAgent.parseTime("오후 2시");

        // Then
        assertThat(result).isEqualTo(LocalTime.of(14, 0));
    }

    @Test
    @DisplayName("'오전 N시' 형식을 정확히 파싱해야 한다 (공백 포함)")
    void parseTime_morningFormatWithSpace_returnsLocalTime() {
        // When - 공백이 있어야 정상 작동함
        LocalTime result = timeFilterAgent.parseTime("오전 9시");

        // Then
        assertThat(result).isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    @DisplayName("'오후 12시'는 12시여야 한다")
    void parseTime_afternoonNoon_returnsNoon() {
        // When
        LocalTime result = timeFilterAgent.parseTime("오후 12시");

        // Then
        assertThat(result).isEqualTo(LocalTime.of(12, 0));
    }

    @Test
    @DisplayName("'오전 12시'는 자정이어야 한다")
    void parseTime_morningMidnight_returnsMidnight() {
        // When
        LocalTime result = timeFilterAgent.parseTime("오전 12시");

        // Then
        assertThat(result).isEqualTo(LocalTime.of(0, 0));
    }

    @Test
    @DisplayName("null 입력은 예외를 발생시켜야 한다")
    void parseTime_nullInput_throwsException() {
        // When & Then
        assertThatThrownBy(() -> timeFilterAgent.parseTime(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("시간을 입력해주세요");
    }

    @Test
    @DisplayName("빈 문자열은 예외를 발생시켜야 한다")
    void parseTime_blankInput_throwsException() {
        // When & Then
        assertThatThrownBy(() -> timeFilterAgent.parseTime("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("시간을 입력해주세요");
    }

    @Test
    @DisplayName("잘못된 시간 형식은 예외를 발생시켜야 한다")
    void parseTime_invalidFormat_throwsException() {
        // When & Then
        assertThatThrownBy(() -> timeFilterAgent.parseTime("invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("시간 형식이 올바르지 않습니다");
    }

    @Test
    @DisplayName("기준 시간 이후 항공편만 필터링해야 한다")
    void filterAfterTime_filtersCorrectly() {
        // Given
        List<FlightInfoResponse> flights = List.of(
                FlightInfoResponse.builder()
                        .flightId("7C1402")
                        .departureTime("202503090955") // 09:55
                        .build(),
                FlightInfoResponse.builder()
                        .flightId("OZ8141")
                        .departureTime("202503091400") // 14:00
                        .build(),
                FlightInfoResponse.builder()
                        .flightId("KE1234")
                        .departureTime("202503091630") // 16:30
                        .build()
        );

        // When
        LocalTime cutoffTime = LocalTime.of(14, 0);
        List<FlightInfoResponse> result = timeFilterAgent.filterAfterTime(flights, cutoffTime);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result)
                .allMatch(flight -> {
                    String time = flight.getDepartureTime();
                    return time.equals("202503091400") || time.equals("202503091630");
                });
    }

    @Test
    @DisplayName("빈 목록 필터링은 빈 목록을 반환해야 한다")
    void filterAfterTime_emptyList_returnsEmpty() {
        // When
        List<FlightInfoResponse> result = timeFilterAgent.filterAfterTime(
                List.of(),
                LocalTime.of(12, 0)
        );

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("null 목록 필터링은 빈 목록을 반환해야 한다")
    void filterAfterTime_nullList_returnsEmpty() {
        // When
        List<FlightInfoResponse> result = timeFilterAgent.filterAfterTime(
                null,
                LocalTime.of(12, 0)
        );

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("잘못된 형식의 출발 시간을 가진 항공편은 필터링에서 제외되어야 한다")
    void filterAfterTime_invalidDepartureTime_excludesInvalid() {
        // Given
        List<FlightInfoResponse> flights = List.of(
                FlightInfoResponse.builder()
                        .flightId("7C1402")
                        .departureTime("202503090955")
                        .build(),
                FlightInfoResponse.builder()
                        .flightId("OZ8141")
                        .departureTime("invalid")
                        .build()
        );

        // When
        List<FlightInfoResponse> result = timeFilterAgent.filterAfterTime(
                flights,
                LocalTime.of(0, 0)
        );

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFlightId()).isEqualTo("7C1402");
    }
}

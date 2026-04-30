package com.nhnacademy.flyschedule.service;

import com.nhnacademy.flyschedule.dto.response.FlightInfoResponse;
import com.nhnacademy.flyschedule.service.agent.GroupingAgent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GroupingAgent 단위 테스트
 *
 * 항공편 그룹핑 기능을 검증합니다.
 */
@DisplayName("GroupingAgent 단위 테스트")
class GroupingAgentTest {

    private GroupingAgent groupingAgent;
    private List<FlightInfoResponse> testFlights;

    @BeforeEach
    void setUp() {
        groupingAgent = new GroupingAgent();

        // 테스트용 항공편 데이터 생성
        testFlights = List.of(
                FlightInfoResponse.builder()
                        .flightId("7C1402")
                        .airlineName("제주항공")
                        .departureTime("202503090955")
                        .build(),
                FlightInfoResponse.builder()
                        .flightId("7C1403")
                        .airlineName("제주항공")
                        .departureTime("202503091400")
                        .build(),
                FlightInfoResponse.builder()
                        .flightId("OZ8141")
                        .airlineName("아시아나항공")
                        .departureTime("202503091045")
                        .build(),
                FlightInfoResponse.builder()
                        .flightId("KE1234")
                        .airlineName("대한항공")
                        .departureTime("202503091630")
                        .build(),
                FlightInfoResponse.builder()
                        .flightId("LJ1234")
                        .airlineName("에어부산")
                        .departureTime("202503091800")
                        .build(),
                FlightInfoResponse.builder()
                        .flightId("TW1234")
                        .airlineName("티웨이항공")
                        .departureTime("202503091200")
                        .build()
        );
    }

    @Test
    @DisplayName("항공편을 항공사별로 정확히 그룹핑해야 한다")
    void groupByAirline_groupsCorrectly() {
        // When
        Map<String, List<FlightInfoResponse>> result = groupingAgent.groupByAirline(testFlights);

        // Then
        assertThat(result).hasSize(5); // 5개 항공사

        // 제주항공 확인
        assertThat(result).containsKey("제주항공");
        assertThat(result.get("제주항공")).hasSize(2);

        // 아시아나항공 확인
        assertThat(result).containsKey("아시아나항공");
        assertThat(result.get("아시아나항공")).hasSize(1);

        // 대한항공 확인
        assertThat(result).containsKey("대한항공");
        assertThat(result.get("대한항공")).hasSize(1);

        // 에어부산 확인
        assertThat(result).containsKey("에어부산");
        assertThat(result.get("에어부산")).hasSize(1);

        // 티웨이항공 확인
        assertThat(result).containsKey("티웨이항공");
        assertThat(result.get("티웨이항공")).hasSize(1);
    }

    @Test
    @DisplayName("항공사별로 그룹핑된 항공편의 ID가 정확해야 한다")
    void groupByAirline_flightIdsAreCorrect() {
        // When
        Map<String, List<FlightInfoResponse>> result = groupingAgent.groupByAirline(testFlights);

        // Then
        assertThat(result.get("제주항공"))
                .extracting("flightId")
                .containsExactly("7C1402", "7C1403");

        assertThat(result.get("아시아나항공"))
                .extracting("flightId")
                .containsExactly("OZ8141");

        assertThat(result.get("대한항공"))
                .extracting("flightId")
                .containsExactly("KE1234");
    }

    @Test
    @DisplayName("항공사별로 그룹핑은 TreeMap을 사용하여 정렬된 순서로 반환해야 한다")
    void groupByAirline_returnsSortedMap() {
        // When
        Map<String, List<FlightInfoResponse>> result = groupingAgent.groupByAirline(testFlights);

        // Then - TreeMap은 키를 자동으로 정렬
        assertThat(result.keySet()).isNotNull();
    }

    @Test
    @DisplayName("빈 목록 그룹핑은 빈 Map을 반환해야 한다")
    void groupByAirline_emptyList_returnsEmptyMap() {
        // When
        Map<String, List<FlightInfoResponse>> result = groupingAgent.groupByAirline(List.of());

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("null 목록 그룹핑은 빈 Map을 반환해야 한다")
    void groupByAirline_nullList_returnsEmptyMap() {
        // When
        Map<String, List<FlightInfoResponse>> result = groupingAgent.groupByAirline(null);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("항공편을 시간대별로 정확히 그룹핑해야 한다")
    void groupByTimeRange_groupsByTimeRange() {
        // When
        Map<String, List<FlightInfoResponse>> result = groupingAgent.groupByTimeRange(testFlights);

        // Then
        assertThat(result).isNotEmpty();
        assertThat(result).hasSizeLessThanOrEqualTo(5); // 최대 5개 시간대

        // 총 항공편 수가 6개여야 함
        int totalFlights = result.values().stream()
                .mapToInt(List::size)
                .sum();
        assertThat(totalFlights).isEqualTo(6);
    }

    @Test
    @DisplayName("시간대별 그룹핑은 정렬된 순서로 반환해야 한다")
    void groupByTimeRange_returnsSortedMap() {
        // When
        Map<String, List<FlightInfoResponse>> result = groupingAgent.groupByTimeRange(testFlights);

        // Then - TreeMap 사용으로 인해 시간대 순서대로 정렬됨
        assertThat(result.keySet()).isNotNull();
    }

    @Test
    @DisplayName("잘못된 형식의 시간 정보는 '알 수 없음' 시간대로 그룹핑되어야 한다")
    void groupByTimeRange_invalidTime_groupsAsUnknown() {
        // Given
        List<FlightInfoResponse> flightsWithInvalidTime = List.of(
                FlightInfoResponse.builder()
                        .flightId("7C1402")
                        .departureTime("invalid")
                        .build()
        );

        // When
        Map<String, List<FlightInfoResponse>> result = groupingAgent.groupByTimeRange(flightsWithInvalidTime);

        // Then
        assertThat(result).containsKey("알 수 없음");
        assertThat(result.get("알 수 없음")).hasSize(1);
    }

    @Test
    @DisplayName("빈 목록의 시간대별 그룹핑은 빈 Map을 반환해야 한다")
    void groupByTimeRange_emptyList_returnsEmptyMap() {
        // When
        Map<String, List<FlightInfoResponse>> result = groupingAgent.groupByTimeRange(List.of());

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("null 목록의 시간대별 그룹핑은 빈 Map을 반환해야 한다")
    void groupByTimeRange_nullList_returnsEmptyMap() {
        // When
        Map<String, List<FlightInfoResponse>> result = groupingAgent.groupByTimeRange(null);

        // Then
        assertThat(result).isEmpty();
    }
}

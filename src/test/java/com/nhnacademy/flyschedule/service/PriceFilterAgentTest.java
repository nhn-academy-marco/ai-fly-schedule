package com.nhnacademy.flyschedule.service;

import com.nhnacademy.flyschedule.dto.response.FlightInfoResponse;
import com.nhnacademy.flyschedule.service.agent.PriceFilterAgent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PriceFilterAgent 단위 테스트
 *
 * 가격 필터링 기능을 검증합니다.
 */
@DisplayName("PriceFilterAgent 단위 테스트")
class PriceFilterAgentTest {

    private PriceFilterAgent priceFilterAgent;
    private List<FlightInfoResponse> testFlights;

    @BeforeEach
    void setUp() {
        priceFilterAgent = new PriceFilterAgent();

        // 테스트용 항공편 데이터 생성
        testFlights = List.of(
                FlightInfoResponse.builder()
                        .flightId("7C1402")
                        .airlineName("제주항공")
                        .economyCharge(45000)
                        .build(),
                FlightInfoResponse.builder()
                        .flightId("OZ8141")
                        .airlineName("아시아나항공")
                        .economyCharge(57900)
                        .build(),
                FlightInfoResponse.builder()
                        .flightId("KE1234")
                        .airlineName("대한항공")
                        .economyCharge(65000)
                        .build(),
                FlightInfoResponse.builder()
                        .flightId("LJ1234")
                        .airlineName("에어부산")
                        .economyCharge(38000)
                        .build(),
                FlightInfoResponse.builder()
                        .flightId("TW1234")
                        .airlineName("티웨이항공")
                        .economyCharge(72000)
                        .build()
        );
    }

    @Test
    @DisplayName("최소 가격 필터링이 정확해야 한다")
    void filterByPriceRange_minPriceOnly_returnsFilteredFlights() {
        // When
        List<FlightInfoResponse> result = priceFilterAgent.filterByPriceRange(testFlights, 50000, null);

        // Then
        assertThat(result).hasSize(3);
        assertThat(result)
                .allMatch(flight -> flight.getEconomyCharge() >= 50000);
    }

    @Test
    @DisplayName("최대 가격 필터링이 정확해야 한다")
    void filterByPriceRange_maxPriceOnly_returnsFilteredFlights() {
        // When
        List<FlightInfoResponse> result = priceFilterAgent.filterByPriceRange(testFlights, null, 60000);

        // Then
        assertThat(result).hasSize(3);
        assertThat(result)
                .allMatch(flight -> flight.getEconomyCharge() <= 60000);
    }

    @Test
    @DisplayName("최소/최대 가격 범위 필터링이 정확해야 한다")
    void filterByPriceRange_bothPrices_returnsFilteredFlights() {
        // When
        List<FlightInfoResponse> result = priceFilterAgent.filterByPriceRange(testFlights, 40000, 60000);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result)
                .allMatch(flight -> flight.getEconomyCharge() >= 40000
                                 && flight.getEconomyCharge() <= 60000);
    }

    @Test
    @DisplayName("가격 정보가 없는 항공편은 필터링에서 제외되어야 한다")
    void filterByPriceRange_flightsWithoutPrice_excluded() {
        // Given
        List<FlightInfoResponse> flightsWithNullPrice = List.of(
                FlightInfoResponse.builder()
                        .flightId("7C1402")
                        .economyCharge(45000)
                        .build(),
                FlightInfoResponse.builder()
                        .flightId("OZ8141")
                        .economyCharge(null)
                        .build(),
                FlightInfoResponse.builder()
                        .flightId("KE1234")
                        .economyCharge(0)
                        .build()
        );

        // When
        List<FlightInfoResponse> result = priceFilterAgent.filterByPriceRange(flightsWithNullPrice, null, null);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFlightId()).isEqualTo("7C1402");
    }

    @Test
    @DisplayName("빈 목록 필터링은 빈 목록을 반환해야 한다")
    void filterByPriceRange_emptyList_returnsEmpty() {
        // When
        List<FlightInfoResponse> result = priceFilterAgent.filterByPriceRange(List.of(), null, null);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("null 목록 필터링은 빈 목록을 반환해야 한다")
    void filterByPriceRange_nullList_returnsEmpty() {
        // When
        List<FlightInfoResponse> result = priceFilterAgent.filterByPriceRange(null, null, null);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("조건에 맞는 항공편이 없으면 빈 목록을 반환해야 한다")
    void filterByPriceRange_noMatchingFlights_returnsEmpty() {
        // When
        List<FlightInfoResponse> result = priceFilterAgent.filterByPriceRange(testFlights, 100000, null);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("최저가 항공편을 정확히 찾아야 한다")
    void findCheapest_returnsCheapestFlight() {
        // When
        FlightInfoResponse result = priceFilterAgent.findCheapest(testFlights);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getFlightId()).isEqualTo("LJ1234");
        assertThat(result.getEconomyCharge()).isEqualTo(38000);
    }

    @Test
    @DisplayName("최고가 항공편을 정확히 찾아야 한다")
    void findMostExpensive_returnsMostExpensiveFlight() {
        // When
        FlightInfoResponse result = priceFilterAgent.findMostExpensive(testFlights);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getFlightId()).isEqualTo("TW1234");
        assertThat(result.getEconomyCharge()).isEqualTo(72000);
    }

    @Test
    @DisplayName("평균 가격을 정확히 계산해야 한다")
    void calculateAveragePrice_returnsCorrectAverage() {
        // When
        double average = priceFilterAgent.calculateAveragePrice(testFlights);

        // Then
        // (45000 + 57900 + 65000 + 38000 + 72000) / 5 = 277900 / 5 = 55580
        assertThat(average).isEqualTo(55580.0);
    }

    @Test
    @DisplayName("빈 목록의 평균 가격은 0이어야 한다")
    void calculateAveragePrice_emptyList_returnsZero() {
        // When
        double average = priceFilterAgent.calculateAveragePrice(List.of());

        // Then
        assertThat(average).isZero();
    }

    @Test
    @DisplayName("가격이 null인 항공편은 평균 계산에서 제외되어야 한다")
    void calculateAveragePrice_withNullPrices_excludesNullPrices() {
        // Given
        List<FlightInfoResponse> flightsWithNullPrice = List.of(
                FlightInfoResponse.builder()
                        .economyCharge(50000)
                        .build(),
                FlightInfoResponse.builder()
                        .economyCharge(null)
                        .build(),
                FlightInfoResponse.builder()
                        .economyCharge(70000)
                        .build()
        );

        // When
        double average = priceFilterAgent.calculateAveragePrice(flightsWithNullPrice);

        // Then
        assertThat(average).isEqualTo(60000.0);
    }
}

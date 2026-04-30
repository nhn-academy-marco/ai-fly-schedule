package com.nhnacademy.flyschedule.service;

import com.nhnacademy.flyschedule.dto.response.FlightInfoResponse;
import com.nhnacademy.flyschedule.service.agent.*;
import com.nhnacademy.flyschedule.service.api.ApiClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * FlightSearchAgent 통합 테스트
 *
 * 항공편 검색 기능의 통합을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FlightSearchAgent 통합 테스트")
class FlightSearchAgentTest {

    @Mock
    private ApiClientService apiClientService;

    @Mock
    private DateParserAgent dateParserAgent;

    @Mock
    private AirportCodeAgent airportCodeAgent;

    @Mock
    private TimeFilterAgent timeFilterAgent;

    @Mock
    private PriceFilterAgent priceFilterAgent;

    @Mock
    private GroupingAgent groupingAgent;

    @InjectMocks
    private FlightSearchAgent flightSearchAgent;

    private List<FlightInfoResponse> mockFlights;

    @BeforeEach
    void setUp() {
        // Mock 데이터 생성
        mockFlights = List.of(
                FlightInfoResponse.builder()
                        .flightId("7C1402")
                        .airlineName("제주항공")
                        .economyCharge(45000)
                        .departureTime("202503090955")
                        .arrivalTime("202503091045")
                        .build(),
                FlightInfoResponse.builder()
                        .flightId("OZ8141")
                        .airlineName("아시아나항공")
                        .economyCharge(57900)
                        .departureTime("202503091400")
                        .arrivalTime("202503091550")
                        .build()
        );
    }

    @Test
    @DisplayName("기본 검색이 정상 동작해야 한다")
    void searchAndGroupByAirline_basicSearch_returnsGroupedFlights() {
        // Given
        when(dateParserAgent.parseDate("내일")).thenReturn("20250310");
        when(airportCodeAgent.getAirportCode("광주")).thenReturn("NAARKJJ");
        when(airportCodeAgent.getAirportCode("제주")).thenReturn("NAARKPC");
        when(apiClientService.getFlightSchedule("NAARKJJ", "NAARKPC", "20250310"))
                .thenReturn(mockFlights);
        when(groupingAgent.groupByAirline(mockFlights))
                .thenReturn(Map.of(
                        "제주항공", List.of(mockFlights.get(0)),
                        "아시아나항공", List.of(mockFlights.get(1))
                ));

        // When
        Map<String, List<FlightInfoResponse>> result = flightSearchAgent.searchAndGroupByAirline(
                "광주", "제주", "내일");

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).containsKey("제주항공");
        assertThat(result).containsKey("아시아나항공");
    }

    @Test
    @DisplayName("시간 필터 검색이 정상 동작해야 한다")
    void searchWithTimeFilter_filtersByTime() {
        // Given
        when(dateParserAgent.parseDate("내일")).thenReturn("20250310");
        when(airportCodeAgent.getAirportCode("광주")).thenReturn("NAARKJJ");
        when(airportCodeAgent.getAirportCode("제주")).thenReturn("NAARKPC");
        when(apiClientService.getFlightSchedule(anyString(), anyString(), anyString()))
                .thenReturn(mockFlights);
        when(groupingAgent.groupByAirline(mockFlights))
                .thenReturn(Map.of(
                        "제주항공", List.of(mockFlights.get(0)),
                        "아시아나항공", List.of(mockFlights.get(1))
                ));
        when(timeFilterAgent.parseTime("14:00"))
                .thenReturn(java.time.LocalTime.of(14, 0));
        when(timeFilterAgent.filterAfterTime(anyList(), any()))
                .thenReturn(List.of(mockFlights.get(1))); // OZ8141만 통과

        // When
        Map<String, List<FlightInfoResponse>> result = flightSearchAgent.searchWithTimeFilter(
                "광주", "제주", "내일", "14:00");

        // Then
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("가격 필터 검색이 정상 동작해야 한다")
    void searchWithPriceFilter_filtersByPrice() {
        // Given
        when(dateParserAgent.parseDate("내일")).thenReturn("20250310");
        when(airportCodeAgent.getAirportCode("광주")).thenReturn("NAARKJJ");
        when(airportCodeAgent.getAirportCode("제주")).thenReturn("NAARKPC");
        when(apiClientService.getFlightSchedule(anyString(), anyString(), anyString()))
                .thenReturn(mockFlights);
        when(groupingAgent.groupByAirline(mockFlights))
                .thenReturn(Map.of(
                        "제주항공", List.of(mockFlights.get(0)),
                        "아시아나항공", List.of(mockFlights.get(1))
                ));
        when(priceFilterAgent.filterByPriceRange(anyList(), any(), any()))
                .thenReturn(List.of(mockFlights.get(0))); // 7C1402만 통과

        // When
        Map<String, List<FlightInfoResponse>> result = flightSearchAgent.searchWithPriceFilter(
                "광주", "제주", "내일", 40000, 50000);

        // Then
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("빈 결과에 대한 그룹핑 처리가 정상이어야 한다")
    void searchAndGroupByAirline_emptyResults_returnsEmptyMap() {
        // Given
        when(dateParserAgent.parseDate("내일")).thenReturn("20250310");
        when(airportCodeAgent.getAirportCode("광주")).thenReturn("NAARKJJ");
        when(airportCodeAgent.getAirportCode("제주")).thenReturn("NAARKPC");
        when(apiClientService.getFlightSchedule("NAARKJJ", "NAARKPC", "20250310"))
                .thenReturn(List.of());
        when(groupingAgent.groupByAirline(List.of()))
                .thenReturn(Map.of());

        // When
        Map<String, List<FlightInfoResponse>> result = flightSearchAgent.searchAndGroupByAirline(
                "광주", "제주", "내일");

        // Then
        assertThat(result).isEmpty();
    }
}

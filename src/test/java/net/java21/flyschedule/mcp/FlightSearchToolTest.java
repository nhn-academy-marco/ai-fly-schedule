package net.java21.flyschedule.mcp;

import net.java21.flyschedule.dto.response.FlightInfoResponse;
import net.java21.flyschedule.service.agent.FlightSearchAgent;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * FlightSearchTool 단위 테스트
 *
 * MCP Tool 동작을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FlightSearchTool 단위 테스트")
class FlightSearchToolTest {

    @Mock
    private FlightSearchAgent flightSearchAgent;

    @InjectMocks
    private FlightSearchTool flightSearchTool;

    private Map<String, List<FlightInfoResponse>> mockGroupedFlights;

    @BeforeEach
    void setUp() {
        mockGroupedFlights = Map.of(
                "제주항공", List.of(
                        FlightInfoResponse.builder()
                                .flightId("7C1402")
                                .airlineName("제주항공")
                                .economyCharge(45000)
                                .build()
                ),
                "아시아나항공", List.of(
                        FlightInfoResponse.builder()
                                .flightId("OZ8141")
                                .airlineName("아시아나항공")
                                .economyCharge(57900)
                                .build()
                )
        );
    }

    @Test
    @DisplayName("기본 항공편 검색이 정상 동작해야 한다")
    void searchFlightsByAirline_basicSearch_returnsGroupedFlights() {
        // Given
        when(flightSearchAgent.searchAndGroupByAirline("광주", "제주", "내일"))
                .thenReturn(mockGroupedFlights);

        // When
        Map<String, List<FlightInfoResponse>> result = flightSearchTool.searchFlightsByAirline(
                "광주", "제주", "내일");

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).containsKey("제주항공");
        assertThat(result).containsKey("아시아나항공");
    }

    @Test
    @DisplayName("시간 필터 항공편 검색이 정상 동작해야 한다")
    void searchFlightsAfterTime_withTimeFilter_returnsFilteredFlights() {
        // Given
        when(flightSearchAgent.searchWithTimeFilter("광주", "제주", "내일", "14:00"))
                .thenReturn(mockGroupedFlights);

        // When
        Map<String, List<FlightInfoResponse>> result = flightSearchTool.searchFlightsAfterTime(
                "광주", "제주", "내일", "14:00");

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("가격 필터 항공편 검색이 정상 동작해야 한다")
    void searchFlightsByPriceRange_withPriceFilter_returnsFilteredFlights() {
        // Given
        when(flightSearchAgent.searchWithPriceFilter("광주", "제주", "내일", 30000, 60000))
                .thenReturn(mockGroupedFlights);

        // When
        Map<String, List<FlightInfoResponse>> result = flightSearchTool.searchFlightsByPriceRange(
                "광주", "제주", "내일", 30000, 60000);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("최소 가격만 지정한 필터링이 정상 동작해야 한다")
    void searchFlightsByPriceRange_minPriceOnly_returnsFilteredFlights() {
        // Given
        when(flightSearchAgent.searchWithPriceFilter("광주", "제주", "내일", 50000, null))
                .thenReturn(mockGroupedFlights);

        // When
        Map<String, List<FlightInfoResponse>> result = flightSearchTool.searchFlightsByPriceRange(
                "광주", "제주", "내일", 50000, null);

        // Then
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("최대 가격만 지정한 필터링이 정상 동작해야 한다")
    void searchFlightsByPriceRange_maxPriceOnly_returnsFilteredFlights() {
        // Given
        when(flightSearchAgent.searchWithPriceFilter("광주", "제주", "내일", null, 50000))
                .thenReturn(mockGroupedFlights);

        // When
        Map<String, List<FlightInfoResponse>> result = flightSearchTool.searchFlightsByPriceRange(
                "광주", "제주", "내일", null, 50000);

        // Then
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("가격 필터를 지정하지 않은 검색이 정상 동작해야 한다")
    void searchFlightsByPriceRange_noPriceFilter_returnsAllFlights() {
        // Given
        when(flightSearchAgent.searchWithPriceFilter("광주", "제주", "내일", null, null))
                .thenReturn(mockGroupedFlights);

        // When
        Map<String, List<FlightInfoResponse>> result = flightSearchTool.searchFlightsByPriceRange(
                "광주", "제주", "내일", null, null);

        // Then
        assertThat(result).isNotNull();
    }
}

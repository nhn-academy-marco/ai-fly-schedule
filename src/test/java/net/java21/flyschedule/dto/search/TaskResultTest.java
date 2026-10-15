package net.java21.flyschedule.dto.search;

import net.java21.flyschedule.dto.response.FlightInfoResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static net.java21.flyschedule.support.TestFlights.flight;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TaskResult / FlightView / SearchCriteria 단위 테스트")
class TaskResultTest {

    private final SearchCriteria criteria =
            new SearchCriteria("광주", "제주", "2026-03-25", "14:00", null, null, 60000);

    @Test
    @DisplayName("항공사별 결과를 화면용으로 바꾸고 전체 편수와 최저가를 계산한다")
    void ofCalculatesCountAndCheapest() {
        Map<String, List<FlightInfoResponse>> grouped = new LinkedHashMap<>();
        grouped.put("아시아나항공", List.of(flight("OZ8141", "아시아나항공", "0955", "1045", 57900)));
        grouped.put("제주항공", List.of(
                flight("7C1402", "제주항공", "1030", "1120", 45000),
                flight("7C1404", "제주항공", "1530", "1620", null)));

        TaskResult result = TaskResult.of(criteria, grouped);

        assertThat(result.totalCount()).isEqualTo(3);
        assertThat(result.cheapest().flightId()).isEqualTo("7C1402");
        assertThat(result.flightsByAirline()).containsOnlyKeys("아시아나항공", "제주항공");
        assertThat(result.label()).isEqualTo("광주 → 제주 · 2026-03-25 · 14:00 이후 · 6만원 이하");
    }

    @Test
    @DisplayName("API가 운임 대신 0을 주면 가격 없음으로 보고 최저가 계산에서 뺀다")
    void zeroPriceMeansUnknown() {
        TaskResult result = TaskResult.of(criteria, Map.of("제주항공", List.of(
                flight("7C301", "제주항공", "0945", "1045", 0),
                flight("7C303", "제주항공", "1245", "1345", 52000))));

        assertThat(result.flightsByAirline().get("제주항공").get(0).price()).isNull();
        assertThat(result.cheapest().flightId()).isEqualTo("7C303");
    }

    @Test
    @DisplayName("label()은 최소·최대 가격을 만원 단위로, 나누어떨어지지 않으면 원 단위로 보여 준다")
    void labelWithPrices() {
        SearchCriteria range = new SearchCriteria("김포", "제주", "2026-03-25", null, "18:00", 30000, 55500);

        assertThat(range.label()).isEqualTo("김포 → 제주 · 2026-03-25 · 18:00 이전 · 3만원 이상 · 55,500원 이하");
    }

    @Test
    @DisplayName("결과가 없으면 비어 있고 최저가도 없다")
    void emptyResult() {
        TaskResult result = TaskResult.of(criteria, Map.of());

        assertThat(result.isEmpty()).isTrue();
        assertThat(result.cheapest()).isNull();
        assertThat(result.summaryLine()).endsWith("항공편 없음");
    }

    @Test
    @DisplayName("요약 한 줄에는 편수와 최저가 항공편이 들어간다")
    void summaryLine() {
        TaskResult result = TaskResult.of(criteria,
                Map.of("제주항공", List.of(flight("7C1402", "제주항공", "1030", "1120", 45000))));

        assertThat(result.summaryLine())
                .contains("1편")
                .contains("제주항공 7C1402 10:30 출발 45,000원");
    }

    @Test
    @DisplayName("FlightView는 '202603250955'를 '09:55'로 바꾸고, 형식이 짧으면 null로 둔다")
    void flightViewClock() {
        FlightView view = FlightView.from(flight("OZ8141", "아시아나항공", "0955", "1045", 57900));
        FlightInfoResponse broken = FlightInfoResponse.builder().flightId("X").departureTime("2026").build();

        assertThat(view.departureTime()).isEqualTo("09:55");
        assertThat(view.arrivalTime()).isEqualTo("10:45");
        assertThat(FlightView.from(broken).departureTime()).isNull();
    }

    @Test
    @DisplayName("SearchCriteria.hasRoute()는 출발·도착이 모두 있을 때만 true")
    void hasRoute() {
        assertThat(criteria.hasRoute()).isTrue();
        assertThat(new SearchCriteria("광주", " ", null, null, null, null, null).hasRoute()).isFalse();
        assertThat(new SearchCriteria(null, "제주", null, null, null, null, null).hasRoute()).isFalse();
    }
}

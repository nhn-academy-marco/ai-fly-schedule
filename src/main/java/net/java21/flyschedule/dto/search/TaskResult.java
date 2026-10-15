package net.java21.flyschedule.dto.search;

import net.java21.flyschedule.dto.response.FlightInfoResponse;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 검색 조건 하나에 대한 결과 (항공사별 항공편 목록)
 *
 * <p>Coordinator와 Tool Calling 방식은 결과가 하나이고,
 * Orchestrator-Workers 방식은 Worker마다 하나씩 여러 개가 만들어집니다.
 */
public record TaskResult(
        String label,
        SearchCriteria criteria,
        Map<String, List<FlightView>> flightsByAirline,
        int totalCount,
        FlightView cheapest
) {

    public static TaskResult of(SearchCriteria criteria, Map<String, List<FlightInfoResponse>> grouped) {
        Map<String, List<FlightView>> views = new LinkedHashMap<>();
        grouped.forEach((airline, flights) ->
                views.put(airline, flights.stream().map(FlightView::from).toList()));

        List<FlightView> all = views.values().stream().flatMap(List::stream).toList();
        FlightView cheapest = all.stream()
                .filter(flight -> flight.price() != null)
                .min(Comparator.comparingInt(FlightView::price))
                .orElse(null);

        String label = criteria != null && criteria.hasRoute() ? criteria.label() : "검색 결과";
        return new TaskResult(label, criteria, views, all.size(), cheapest);
    }

    public TaskResult withLabel(String newLabel) {
        return new TaskResult(newLabel, criteria, flightsByAirline, totalCount, cheapest);
    }

    public boolean isEmpty() {
        return totalCount == 0;
    }

    /**
     * LLM에게 결과를 요약해 줄 때 쓰는 한 줄 설명
     */
    public String summaryLine() {
        if (isEmpty()) {
            return label + ": 항공편 없음";
        }
        String cheapestText = Objects.isNull(cheapest) ? "가격 정보 없음"
                : "%s %s %s 출발 %,d원".formatted(cheapest.airline(), cheapest.flightId(), cheapest.departureTime(), cheapest.price());
        return "%s: %d편, 최저가 %s".formatted(label, totalCount, cheapestText);
    }
}

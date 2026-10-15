package net.java21.flyschedule.dto.search;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * 항공편 검색 조건
 *
 * <p>세 가지 패턴(Coordinator, Tool Calling, Orchestrator-Workers)이 모두 이 레코드로 검색 조건을 주고받습니다.
 * LLM의 Structured Output 대상이기도 하므로 각 필드에 설명을 붙여 둡니다.
 */
public record SearchCriteria(
        @JsonPropertyDescription("출발 공항 한글 이름 (예: 광주, 김포, 제주)")
        String departure,

        @JsonPropertyDescription("도착 공항 한글 이름 (예: 제주, 김포, 김해)")
        String arrival,

        @JsonPropertyDescription("출발 날짜, YYYY-MM-DD 형식")
        String date,

        @JsonPropertyDescription("이 시각 이후 출발, HH:mm 형식. 조건이 없으면 null")
        String afterTime,

        @JsonPropertyDescription("이 시각 이전 출발, HH:mm 형식. 조건이 없으면 null")
        String beforeTime,

        @JsonPropertyDescription("최소 가격(원). 조건이 없으면 null")
        Integer minPrice,

        @JsonPropertyDescription("최대 가격(원). 조건이 없으면 null")
        Integer maxPrice
) {

    public boolean hasRoute() {
        return departure != null && !departure.isBlank() && arrival != null && !arrival.isBlank();
    }

    /**
     * 화면과 로그에 쓰는 짧은 이름 (예: "광주 → 제주 · 2026-03-25")
     */
    public String label() {
        StringBuilder label = new StringBuilder(departure + " → " + arrival);
        if (date != null) {
            label.append(" · ").append(date);
        }
        if (afterTime != null) {
            label.append(" · ").append(afterTime).append(" 이후");
        }
        if (beforeTime != null) {
            label.append(" · ").append(beforeTime).append(" 이전");
        }
        if (minPrice != null) {
            label.append(" · ").append(won(minPrice)).append(" 이상");
        }
        if (maxPrice != null) {
            label.append(" · ").append(won(maxPrice)).append(" 이하");
        }
        return label.toString();
    }

    private static String won(int price) {
        return price % 10000 == 0 ? (price / 10000) + "만원" : "%,d원".formatted(price);
    }
}

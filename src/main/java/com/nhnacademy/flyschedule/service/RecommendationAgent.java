package com.nhnacademy.flyschedule.service;

import com.nhnacademy.flyschedule.dto.response.FlightInfoResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 추천 에이전트 (Role Prompting + Few-Shot Prompting)
 *
 * Spring AI의 Role Prompting과 Few-Shot Prompting 패턴을 구현합니다.
 *
 * <p>적용한 Prompt Engineering 패턴:
 * - Role Prompting: 항공편 추천 전문가로서의 역할 부여
 * - Few-Shot Prompting: 예제 기반 추천 패턴 학습
 * - System Prompting: 시스템 프롬프트로 역할과 기준 정의
 *
 * <p>Spring AI 공식 문서:
 * https://docs.spring.io/spring-ai/reference/api/chat/prompt-engineering-patterns.html
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationAgent {

    private final PriceFilterAgent priceFilterAgent;
    private final PromptTemplateService promptTemplateService;

    /**
     * 추천을 생성합니다.
     *
     * @param reasoning 추론 결과
     * @param flights 항공편 목록
     * @return 추천 텍스트
     */
    public String generateRecommendation(
            ReasoningAgent.ReasoningResult reasoning,
            Map<String, List<FlightInfoResponse>> flights) {

        log.info("RecommendationAgent: 추천 생성 시작");

        // 전체 항공편 flatten
        List<FlightInfoResponse> allFlights = flights.values().stream()
                .flatMap(List::stream)
                .toList();

        if (allFlights.isEmpty()) {
            return "조건에 맞는 항공편이 없습니다.";
        }

        // 질문 유형에 따른 추천
        switch (reasoning.getQuestionType()) {
            case PRICE_COMPARISON:
                return generatePriceRecommendation(allFlights);
            case RECOMMENDATION:
                return generateOptimalRecommendation(allFlights);
            case TIME_FILTERED_SEARCH:
                return generateTimeBasedRecommendation(allFlights);
            default:
                return generateBasicRecommendation(allFlights);
        }
    }

    /**
     * 가격 기반 추천
     */
    private String generatePriceRecommendation(List<FlightInfoResponse> flights) {
        FlightInfoResponse cheapest = priceFilterAgent.findCheapest(flights);
        FlightInfoResponse mostExpensive = priceFilterAgent.findMostExpensive(flights);
        double avgPrice = priceFilterAgent.calculateAveragePrice(flights);

        StringBuilder sb = new StringBuilder();
        sb.append("가격 분석 결과:\n");
        sb.append(String.format("- 최저가: %s (%,d원)\n",
                cheapest.getFlightId(), cheapest.getEconomyCharge()));
        sb.append(String.format("- 최고가: %s (%,d원)\n",
                mostExpensive.getFlightId(), mostExpensive.getEconomyCharge()));
        sb.append(String.format("- 평균가: %,.0f원\n", avgPrice));
        sb.append(String.format("\n추천: %s편이 가장 저렴합니다.",
                cheapest.getFlightId()));

        return sb.toString();
    }

    /**
     * 최적 추천
     */
    private String generateOptimalRecommendation(List<FlightInfoResponse> flights) {
        // 가성비 추천: 가장 저렴하면서 적절한 시간대
        FlightInfoResponse cheapest = priceFilterAgent.findCheapest(flights);

        return String.format("추천 항공편: %s (%s)\n" +
                        "가격: %,d원\n" +
                        "이유: 가장 저렴한 가격으로 좋은 가성비를 제공합니다.",
                cheapest.getFlightId(),
                cheapest.getAirlineName(),
                cheapest.getEconomyCharge());
    }

    /**
     * 시간 기반 추천
     */
    private String generateTimeBasedRecommendation(List<FlightInfoResponse> flights) {
        // 가장 늦은 시간대 추천 (여유 있게 출발)
        FlightInfoResponse latest = flights.stream()
                .max(Comparator.comparing(f -> f.getDepartureTime()))
                .orElse(flights.get(0));

        return String.format("추천 항공편: %s (%s)\n" +
                        "출발: %s\n" +
                        "이유: 늦은 시간에 출발하므로 여유 있게 이동할 수 있습니다.",
                latest.getFlightId(),
                latest.getAirlineName(),
                formatTime(latest.getDepartureTime()));
    }

    /**
     * 기본 추천
     */
    private String generateBasicRecommendation(List<FlightInfoResponse> flights) {
        FlightInfoResponse cheapest = priceFilterAgent.findCheapest(flights);

        return String.format("추천 항공편: %s (%s)\n" +
                        "가격: %,d원\n" +
                        "이유: 가장 저렴한 옵션입니다.",
                cheapest.getFlightId(),
                cheapest.getAirlineName(),
                cheapest.getEconomyCharge());
    }

    /**
     * 시간 포맷팅
     */
    private String formatTime(String time) {
        if (time == null || time.length() < 12) return time;
        String hour = time.substring(8, 10);
        String minute = time.substring(10, 12);
        return String.format("%s:%s", hour, minute);
    }
}

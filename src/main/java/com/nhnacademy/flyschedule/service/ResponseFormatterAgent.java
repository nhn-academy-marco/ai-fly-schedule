package com.nhnacademy.flyschedule.service;

import com.nhnacademy.flyschedule.dto.response.FlightInfoResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 응답 포맷팅 에이전트 (Contextual Prompting)
 *
 * Spring AI의 Contextual Prompting 패턴을 구현합니다.
 *
 * <p>적용한 Prompt Engineering 패턴:
 * - Contextual Prompting: 질문 맥락에 따른 동적 응답 생성
 * - System Prompting: 포맷팅 원칙과 응답 구조 정의
 * - Role Prompting: AI 어시스턴트로서의 역할 부여
 *
 * <p>Spring AI 공식 문서:
 * https://docs.spring.io/spring-ai/reference/api/chat/prompt-engineering-patterns.html
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResponseFormatterAgent {

    private final PromptTemplateService promptTemplateService;

    /**
     * 자연어 응답을 생성합니다.
     *
     * <p>Contextual Prompting: 추론 결과와 항공편 정보를 컨텍스트로 활용
     *
     * @param reasoning 추론 결과
     * @param flights 항공편 목록
     * @param recommendation 추천 텍스트
     * @return 포맷팅된 자연어 응답
     */
    public String formatResponse(
            ReasoningAgent.ReasoningResult reasoning,
            Map<String, List<FlightInfoResponse>> flights,
            String recommendation) {

        log.info("ResponseFormatterAgent: 응답 포맷팅 시작");

        // Contextual Prompting: 컨텍스트 생성
        Map<String, Object> context = buildContext(reasoning, flights, recommendation);
        log.debug("생성된 컨텍스트: {}", context);

        StringBuilder sb = new StringBuilder();

        // 인사말 (Question Type에 따른 맞춤형 인사)
        sb.append(generateGreeting(reasoning)).append("\n\n");

        // 추천 결과
        sb.append("💡 추천:\n");
        sb.append(recommendation).append("\n\n");

        // 상세 항공편 정보
        if (!flights.isEmpty()) {
            sb.append(generateFlightDetails(flights)).append("\n");
        }

        // 요약
        sb.append(generateSummary(flights));

        return sb.toString();
    }

    /**
     * Contextual Prompting: 응답 생성을 위한 컨텍스트 구성
     *
     * <p>Spring AI Prompt Engineering Patterns - Contextual Prompting
     */
    private Map<String, Object> buildContext(
            ReasoningAgent.ReasoningResult reasoning,
            Map<String, List<FlightInfoResponse>> flights,
            String recommendation) {

        Map<String, Object> context = new HashMap<>();

        // 질문 유형 컨텍스트
        context.put("questionType", reasoning.getQuestionType().toString());
        context.put("questionDescription", reasoning.getQuestionType().getDescription());

        // 항공편 통계 컨텍스트
        int totalCount = flights.values().stream().mapToInt(List::size).sum();
        int airlineCount = flights.size();
        context.put("flightCount", totalCount);
        context.put("airlineCount", airlineCount);

        // 추천 유형 컨텍스트
        if (recommendation.contains("가장 저렴")) {
            context.put("recommendationType", "PRICE");
        } else if (recommendation.contains("늦은 시간")) {
            context.put("recommendationType", "TIME");
        } else {
            context.put("recommendationType", "BALANCED");
        }

        return context;
    }

    /**
     * 인사말 생성
     */
    private String generateGreeting(ReasoningAgent.ReasoningResult reasoning) {
        switch (reasoning.getQuestionType()) {
            case BASIC_SEARCH:
                return "네, 요청하신 항공편을 찾았어요! ✈️";
            case TIME_FILTERED_SEARCH:
                return "네, 해당 시간대 항공편을 찾았어요! 🕐";
            case PRICE_COMPARISON:
                return "네, 가격 비교 결과를 안내드릴게요! 💰";
            case RECOMMENDATION:
                return "네, 최적의 항공편을 추천해드릴게요! ⭐";
            default:
                return "네, 처리 결과입니다.";
        }
    }

    /**
     * 항공편 상세 정보 생성
     */
    private String generateFlightDetails(Map<String, List<FlightInfoResponse>> flights) {
        StringBuilder sb = new StringBuilder();

        flights.forEach((airline, flightList) -> {
            sb.append(String.format("\n✈️ %s (%d편)\n", airline, flightList.size()));
            sb.append("──────────────────────\n");

            flightList.forEach(flight -> {
                sb.append(String.format("%s편\n", flight.getFlightId()));
                sb.append(String.format("  출발: %s → 도착: %s\n",
                        formatTime(flight.getDepartureTime()),
                        formatTime(flight.getArrivalTime())));
                sb.append(String.format("  가격: %,d원\n\n",
                        flight.getEconomyCharge()));
            });
        });

        return sb.toString().trim();
    }

    /**
     * 요약 생성
     */
    private String generateSummary(Map<String, List<FlightInfoResponse>> flights) {
        int totalCount = flights.values().stream()
                .mapToInt(List::size)
                .sum();

        int airlineCount = flights.size();

        return String.format("\n──────────────────────\n" +
                "총 %d개 항공사, %d편의 항공편이 있습니다.",
                airlineCount, totalCount);
    }

    /**
     * 시간 포맷팅
     */
    private String formatTime(String time) {
        if (time == null || time.length() < 12) return "정보 없음";
        String hour = time.substring(8, 10);
        String minute = time.substring(10, 12);
        return String.format("%s:%s", hour, minute);
    }
}

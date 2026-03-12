package com.nhnacademy.flyschedule.service;

import com.nhnacademy.flyschedule.dto.response.FlightInfoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 멀티 에이전트 오케스트레이터
 *
 * Spring AI의 Agent Utils 패턴을 구현합니다.
 * 여러 전문 에이전트를 조율하여 복잡한 작업을 수행합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultiAgentOrchestrator {

    private final ReasoningAgent reasoningAgent;
    private final FlightSearchAgent flightSearchAgent;
    private final RecommendationAgent recommendationAgent;
    private final ResponseFormatterAgent responseFormatterAgent;

    /**
     * 자연어 질문을 처리하여 최적의 항공편을 추천합니다.
     *
     * <p>Multi-Agent Orchestration Pattern:
     * 1. ReasoningAgent - 질문 분석 및 의도 파악
     * 2. FlightSearchAgent - 항공편 검색
     * 3. RecommendationAgent - 추천 생성
     * 4. ResponseFormatterAgent - 자연어 응답 생성
     *
     * <p>이 메서드는 @Tool annotation을 통해 LLM이 직접 호출할 수 있습니다.
     *
     * @param query 사용자의 자연어 질문
     *            (예: "내일 광주에서 제주로 가는 가장 저렴한 항공편 추천해줘")
     * @return 추천 결과
     */
    @Tool(
            description = "사용자의 자연어 질문을 분석하여 항공편을 검색하고 추천합니다. " +
                    "질문에서 출발 공항, 도착 공항, 날짜, 시간 조건, 가격 조건 등을 추출합니다. " +
                    "지원하는 질문 예시: " +
                    "- '내일 광주에서 제주로 가는 항공편 알려줘' " +
                    "- '오후 2시 이후 출발하는 김포-제주 항공편 검색해줘' " +
                    "- '5만원에서 7만원 사이의 대구-제주 항공편 추천해줘' " +
                    "- '가장 저렴한 광주-제주 항공편 추천해줘'"
    )
    public String processFlightQuery(
            @ToolParam(description = "사용자의 자연어 질문 (예: 내일 광주에서 제주로 가는 항공편 알려줘)") String query
    ) {
        log.info("MultiAgentOrchestrator: 질문 처리 시작 - {}", query);

        // 단계 1: 질문 분석 (Reasoning Agent)
        log.info("단계 1: 질문 분석");
        ReasoningAgent.ReasoningResult reasoning = reasoningAgent.analyzeQuery(query);

        // 질문 유형에 따른 처리
        if (reasoning.getQuestionType() == ReasoningAgent.QuestionType.AIRPORT_LIST) {
            return processAirportListQuery();
        }

        if (reasoning.getQuestionType() == ReasoningAgent.QuestionType.AIRLINE_LIST) {
            return processAirlineListQuery();
        }

        if (!reasoning.isValid()) {
            return reasoning.getConclusion();
        }

        // 단계 2: 항공편 검색 (Flight Search Agent)
        log.info("단계 2: 항공편 검색");
        Map<String, List<FlightInfoResponse>> flights = searchFlights(reasoning);

        // 단계 3: 추천 생성 (Recommendation Agent)
        log.info("단계 3: 추천 생성");
        String recommendation = recommendationAgent.generateRecommendation(
                reasoning, flights);

        // 단계 4: 응답 포맷팅 (Response Formatter Agent)
        log.info("단계 4: 응답 포맷팅");
        String formattedResponse = responseFormatterAgent.formatResponse(
                reasoning, flights, recommendation);

        log.info("MultiAgentOrchestrator: 처리 완료");
        return formattedResponse;
    }

    /**
     * 항공편 검색 실행
     */
    private Map<String, List<FlightInfoResponse>> searchFlights(
            ReasoningAgent.ReasoningResult reasoning) {

        ReasoningAgent.QueryParameters params = reasoning.getParameters();
        String departure = params.getDeparture();
        String arrival = params.getArrival();
        String date = params.getDate();
        String afterTime = params.getAfterTime();
        Integer minPrice = params.getMinPrice();
        Integer maxPrice = params.getMaxPrice();

        // 조건에 따라 적절한 검색 메서드 호출
        if (afterTime != null) {
            log.info("시간 조건 검색: {}", afterTime);
            return flightSearchAgent.searchWithTimeFilter(departure, arrival, date, afterTime);
        }

        if (minPrice != null || maxPrice != null) {
            log.info("가격 조건 검색: {} ~ {}", minPrice, maxPrice);
            return flightSearchAgent.searchWithPriceFilter(departure, arrival, date, minPrice, maxPrice);
        }

        log.info("기본 검색");
        return flightSearchAgent.searchAndGroupByAirline(departure, arrival, date);
    }

    /**
     * 공항 목록 질문 처리
     */
    private String processAirportListQuery() {
        // 공항 목록은 다른 에이전트에 위임
        return "공항 목록은 /api/mcp/airports 엔드포인트를 통해 확인하세요.";
    }

    /**
     * 항공사 목록 질문 처리
     */
    private String processAirlineListQuery() {
        // 항공사 목록은 다른 에이전트에 위임
        return "항공사 목록은 /api/mcp/airlines 엔드포인트를 통해 확인하세요.";
    }
}

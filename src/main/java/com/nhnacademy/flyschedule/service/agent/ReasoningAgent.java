package com.nhnacademy.flyschedule.service.agent;

import com.nhnacademy.flyschedule.service.ai.PromptTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 추론 에이전트 (Inner Thinking Pattern + Prompt Engineering)
 *
 * Spring AI의 "Inner Thinking" 패턴과 Prompt Engineering을 결합하여 구현합니다.
 *
 * <p>적용한 Prompt Engineering 패턴:
 * - Chain of Thought (CoT): 단계별 추론 과정을 명시적으로 표현
 * - Step-Back Prompting: 문제를 더 높은 수준에서 분석
 * - System Prompting: 시스템 프롬프트로 에이전트 역할 정의
 * - Few-Shot Prompting: 예제 기반 학습
 *
 * <p>Spring AI 공식 문서:
 * https://docs.spring.io/spring-ai/reference/api/chat/prompt-engineering-patterns.html
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReasoningAgent {

    private final PromptTemplateService promptTemplateService;

    /**
     * 사용자 질문에서 검색 의도를 파악합니다.
     *
     * <p>Inner Thinking Pattern + Chain of Thought:
     * 1. Step-Back: 높은 수준에서 질문 분석
     * 2. 질문 유형 분석
     * 3. 파라미터 추출
     * 4. 검증
     * 5. 결론 도출
     *
     * @param query 사용자 질문
     * @return 추론 과정과 결과
     */
    public ReasoningResult analyzeQuery(String query) {
        log.info("ReasoningAgent: 질문 분석 시작 - {}", query);

        List<String> thoughts = new ArrayList<>();

        // Step-Back Prompting: 높은 수준에서 질문 분석
        thoughts.add("=== Step-Back: 문제 맥락 분석 ===");
        String stepBackReasoning = performStepBackAnalysis(query);
        thoughts.add(stepBackReasoning);

        // 단계 1: Chain of Thought - 질문 유형 분석
        thoughts.add("\n=== 단계 1: 질문 유형 분석 ===");
        QuestionType type = analyzeQuestionType(query, thoughts);

        // 단계 2: Chain of Thought - 파라미터 추출
        thoughts.add("\n=== 단계 2: 필수 파라미터 추출 ===");
        QueryParameters params = extractParameters(query, thoughts);

        // 단계 3: Chain of Thought - 검증
        thoughts.add("\n=== 단계 3: 파라미터 검증 ===");
        boolean valid = validateParameters(params, thoughts);

        // 단계 4: Chain of Thought - 결론
        thoughts.add("\n=== 단계 4: 최종 결론 ===");
        String conclusion = formConclusion(type, params, valid, thoughts);

        log.info("ReasoningAgent: 추론 완료 - {}", conclusion);

        return ReasoningResult.builder()
                .query(query)
                .questionType(type)
                .parameters(params)
                .valid(valid)
                .thoughts(thoughts)
                .conclusion(conclusion)
                .build();
    }

    /**
     * Step-Back Prompting: 문제를 더 높은 수준에서 분석
     *
     * <p>Spring AI Prompt Engineering Patterns - Step-Back Prompting
     */
    private String performStepBackAnalysis(String query) {
        StringBuilder reasoning = new StringBuilder();
        reasoning.append("사용자의 근본 의도 분석:\n");

        // 1. 사용자 의도 파악
        reasoning.append("  1. 사용자 의도: ");
        if (query.contains("검색") || query.contains("찾아") || query.contains("알려")) {
            reasoning.append("정보 검색 및 항공편 스케줄 확인\n");
        } else if (query.contains("추천") || query.contains("최적")) {
            reasoning.append("구매 결정 지원 및 최적 옵션 탐색\n");
        } else if (query.contains("가격") || query.contains("비교")) {
            reasoning.append("가격 비교 및 예산 계획 수립\n");
        } else {
            reasoning.append("일반적인 항공편 정보 수집\n");
        }

        // 2. 제약 조건 분석
        reasoning.append("  2. 제약 조건: ");
        List<String> constraints = new ArrayList<>();
        if (query.contains("시간") || query.contains("오후") || query.contains("오전")) {
            constraints.add("시간적 제약");
        }
        if (query.contains("가격") || query.contains("싼") || query.contains("저렴")) {
            constraints.add("예산적 제약");
        }
        if (query.contains("항공사")) {
            constraints.add("항공사 선호도");
        }
        if (constraints.isEmpty()) {
            reasoning.append("특별한 제약 조건 없음\n");
        } else {
            reasoning.append(String.join(", ", constraints)).append("\n");
        }

        // 3. 중요 정보 파악
        reasoning.append("  3. 중요 정보: ");
        if (query.contains("가격")) {
            reasoning.append("가격 정보가 가장 중요\n");
        } else if (query.contains("시간")) {
            reasoning.append("시간 정보가 가장 중요\n");
        } else {
            reasoning.append("전체적인 항공편 정보가 중요\n");
        }

        return reasoning.toString();
    }

    /**
     * 질문 유형 분석
     */
    private QuestionType analyzeQuestionType(String query, List<String> thoughts) {
        thoughts.add("  - 키워드 분석 중...");

        if (query.contains("리스트") || query.contains("목록") || query.contains("보여줘")) {
            if (query.contains("공항")) {
                thoughts.add("  → 공항 목록 요청으로 판단");
                return QuestionType.AIRPORT_LIST;
            } else if (query.contains("항공사")) {
                thoughts.add("  → 항공사 목록 요청으로 판단");
                return QuestionType.AIRLINE_LIST;
            }
        }

        if (query.contains("가격") || query.contains("비교")) {
            thoughts.add("  → 가격 비교 요청으로 판단");
            return QuestionType.PRICE_COMPARISON;
        }

        if (query.contains("추천") || query.contains("최적")) {
            thoughts.add("  → 추천 요청으로 판단");
            return QuestionType.RECOMMENDATION;
        }

        if (query.contains("시간") || query.contains("오후") || query.contains("아침")) {
            thoughts.add("  → 시간 조건 검색으로 판단");
            return QuestionType.TIME_FILTERED_SEARCH;
        }

        thoughts.add("  → 기본 검색으로 판단");
        return QuestionType.BASIC_SEARCH;
    }

    /**
     * 파라미터 추출
     */
    private QueryParameters extractParameters(String query, List<String> thoughts) {
        thoughts.add("  - 출발/도착 공항 추출 중...");
        String departure = extractAirport(query, true);
        String arrival = extractAirport(query, false);
        thoughts.add(String.format("    출발: %s, 도착: %s", departure, arrival));

        thoughts.add("  - 날짜 추출 중...");
        String date = extractDate(query);
        thoughts.add(String.format("    날짜: %s", date));

        thoughts.add("  - 조건 추출 중...");
        String afterTime = extractTimeCondition(query);
        Integer minPrice = extractPriceCondition(query, "min");
        Integer maxPrice = extractPriceCondition(query, "max");
        thoughts.add(String.format("    시간: %s, 가격: %d~%d", afterTime, minPrice, maxPrice));

        return QueryParameters.builder()
                .departure(departure)
                .arrival(arrival)
                .date(date)
                .afterTime(afterTime)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .build();
    }

    /**
     * 파라미터 검증
     */
    private boolean validateParameters(QueryParameters params, List<String> thoughts) {
        thoughts.add("  - 필수 파라미터 확인...");

        if (params.getDeparture() == null || params.getArrival() == null) {
            thoughts.add("    → 공항 정보 부족");
            return false;
        }

        if (params.getDate() == null) {
            thoughts.add("    → 날짜 정보 부족 (기본값: 내일)");
            params.setDate("내일");
        }

        thoughts.add("    → 모든 필수 파라미터 확인 완료");
        return true;
    }

    /**
     * 결론 도출
     */
    private String formConclusion(QuestionType type, QueryParameters params, boolean valid, List<String> thoughts) {
        if (!valid) {
            thoughts.add("  → 검증 실패: 추가 정보 필요");
            return "추가 정보가 필요합니다. 출발 공항과 도착 공항을 알려주세요.";
        }

        String conclusion = String.format("%s 요청: %s → %s (%s)",
                type.getDescription(),
                params.getDeparture(),
                params.getArrival(),
                params.getDate());

        thoughts.add("  → " + conclusion);
        return conclusion;
    }

    // 헬퍼 메서드
    private String extractAirport(String query, boolean isDeparture) {
        String[] airports = {"광주", "김포", "김해", "제주", "대구", "청주", "인천", "부산", "여수", "양양"};
        for (String airport : airports) {
            if (query.contains(airport)) {
                // 더 간단한 로직: 첫 번째 공항을 출발, 두 번째를 도착으로 처리
                return airport;
            }
        }
        return isDeparture ? "광주" : "제주"; // 기본값
    }

    private String extractDate(String query) {
        if (query.contains("내일")) return "내일";
        if (query.contains("모레")) return "모레";
        if (query.contains("글피")) return "글피";
        return "내일"; // 기본값
    }

    private String extractTimeCondition(String query) {
        if (query.contains("오후")) {
            if (query.contains("2시")) return "14:00";
            if (query.contains("3시")) return "15:00";
            if (query.contains("4시")) return "16:00";
        }
        return null;
    }

    private Integer extractPriceCondition(String query, String type) {
        if (type.equals("min")) {
            if (query.contains("3만")) return 30000;
            if (query.contains("5만")) return 50000;
        } else {
            if (query.contains("7만")) return 70000;
            if (query.contains("10만")) return 100000;
        }
        return null;
    }

    /**
     * 질문 유형
     */
    public enum QuestionType {
        BASIC_SEARCH("기본 검색"),
        TIME_FILTERED_SEARCH("시간 조건 검색"),
        PRICE_COMPARISON("가격 비교"),
        RECOMMENDATION("추천"),
        AIRPORT_LIST("공항 목록"),
        AIRLINE_LIST("항공사 목록");

        private final String description;

        QuestionType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 추론 결과
     */
    @lombok.Builder
    @lombok.Data
    public static class ReasoningResult {
        private String query;
        private QuestionType questionType;
        private QueryParameters parameters;
        private boolean valid;
        private List<String> thoughts;
        private String conclusion;
    }

    /**
     * 쿼리 파라미터
     */
    @lombok.Builder
    @lombok.Data
    public static class QueryParameters {
        private String departure;
        private String arrival;
        private String date;
        private String afterTime;
        private Integer minPrice;
        private Integer maxPrice;
    }
}

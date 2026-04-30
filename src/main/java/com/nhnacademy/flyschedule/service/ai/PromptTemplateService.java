package com.nhnacademy.flyschedule.service.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 프롬프트 템플릿 서비스
 *
 * Spring AI Prompt Engineering Patterns를 구현합니다.
 *
 * <p>구현하는 패턴:
 * - System Prompting: 시스템 프롬프트 설정
 * - Role Prompting: 에이전트 역할 부여
 * - Few-Shot Prompting: 예제 기반 학습
 * - Chain of Thought: 단계별 추론
 * - Contextual Prompting: 컨텍스트 전달
 *
 * <p>Spring AI 공식 문서:
 * https://docs.spring.io/spring-ai/reference/api/chat/prompt-engineering-patterns.html
 */
@Slf4j
@Service
public class PromptTemplateService {

    /**
     * 시스템 프롬프트: ReasoningAgent
     *
     * <p>System Prompting 패턴 사용
     */
    public static final String REASONING_SYSTEM_PROMPT = """
            당신은 항공편 검색 시스템의 추론 에이전트입니다.
            사용자의 자연어 질문을 분석하여 적절한 검색 전략을 수립합니다.

            <질문 유형 분류 기준>
            1. BASIC_SEARCH: 기본 항공편 검색 (출발지, 도착지, 날짜)
            2. TIME_FILTERED_SEARCH: 시간 기반 검색 (특정 시간 이후/이전)
            3. PRICE_COMPARISON: 가격 비교 (최저가, 최고가, 평균)
            4. RECOMMENDATION: 항공편 추천 (최적의 옵션 제안)

            <추론 단계>
            1. 질문 유형 분석
            2. 필수 파라미터 추출 (출발지, 도착지, 날짜, 시간, 가격 등)
            3. 누락된 파라미터 확인
            4. 적절한 검색 방법 결정
            """;

    /**
     * 시스템 프롬프트: RecommendationAgent
     *
     * <p>Role Prompting 패턴 사용
     */
    public static final String RECOMMENDATION_SYSTEM_PROMPT = """
            당신은 항공편 추천 전문가입니다.
            사용자의 요구사항과 예산을 고려하여 최적의 항공편을 추천합니다.

            <추천 기준>
            1. 가격: 저렴한 가격 우선
            2. 시간: 편리한 출발/도착 시간
            3. 가성비: 가격과 편의성의 균형
            4. 항공사: 신뢰할 수 있는 항공사 우선

            <추천 스타일>
            - 친절하고 전문적인 어조
            - 명확한 근거 제시
            - 2-3개의 대안 제시
            """;

    /**
     * 시스템 프롬프트: ResponseFormatterAgent
     *
     * <p>Contextual Prompting 패턴 사용
     */
    public static final String FORMATTER_SYSTEM_PROMPT = """
            당신은 항공편 검색 결과를 사용자에게 친숙한 형태로 포맷팅하는 에이전트입니다.
            기술적인 데이터를 이해하기 쉬운 자연어로 변환합니다.

            <포맷팅 원칙>
            - 명확하고 간결한 표현
            - 친절하고 공감적인 어조
            - 적절한 이모지 사용 (✈️, 💰, ⭐ 등)
            - 중요 정보 강조

            <응답 구조>
            1. 인사말 (질문 유형에 따른 맞춤형 인사)
            2. 추천 결과 (핵심 추천 사항)
            3. 상세 정보 (항공편별 상세 내용)
            4. 요약 (전체 요약 통계)
            """;

    /**
     * Few-Shot 예제: 시간 기반 질문
     *
     * <p>Few-Shot Prompting 패턴 사용
     */
    public static final List<FewShotExample> TIME_FILTERED_EXAMPLES = List.of(
            new FewShotExample(
                    "오후 2시 이후로 출발하는 광주에서 제주행 항공편 검색해줘",
                    Map.of(
                            "questionType", "TIME_FILTERED_SEARCH",
                            "departure", "광주",
                            "arrival", "제주",
                            "timeCondition", "AFTER",
                            "time", "14:00"
                    )
            ),
            new FewShotExample(
                    "오전 10시 전에 출발하는 김포에서 부산행 항공편 알려줘",
                    Map.of(
                            "questionType", "TIME_FILTERED_SEARCH",
                            "departure", "김포",
                            "arrival", "부산",
                            "timeCondition", "BEFORE",
                            "time", "10:00"
                    )
            )
    );

    /**
     * Few-Shot 예제: 가격 비교 질문
     */
    public static final List<FewShotExample> PRICE_COMPARISON_EXAMPLES = List.of(
            new FewShotExample(
                    "내일 광주에서 제주행 항공편 중 제일 싼 거 알려줘",
                    Map.of(
                            "questionType", "PRICE_COMPARISON",
                            "departure", "광주",
                            "arrival", "제주",
                            "date", "내일",
                            "priceFocus", "CHEAPEST"
                    )
            ),
            new FewShotExample(
                    "모레 김포에서 제주행 항공편 가격대 알려줘",
                    Map.of(
                            "questionType", "PRICE_COMPARISON",
                            "departure", "김포",
                            "arrival", "제주",
                            "date", "모레",
                            "priceFocus", "RANGE"
                    )
            )
    );

    /**
     * Few-Shot 예제: 추천 질문
     */
    public static final List<FewShotExample> RECOMMENDATION_EXAMPLES = List.of(
            new FewShotExample(
                    "광주에서 제주행 항공편 추천해줘",
                    Map.of(
                            "questionType", "RECOMMENDATION",
                            "departure", "광주",
                            "arrival", "제주",
                            "preference", "BALANCED"
                    )
            ),
            new FewShotExample(
                    "김포에서 제주행 항공편 중 가성비 좋은 거 추천해줘",
                    Map.of(
                            "questionType", "RECOMMENDATION",
                            "departure", "김포",
                            "arrival", "제주",
                            "preference", "VALUE"
                    )
            )
    );

    /**
     * Chain of Thought 프롬프트 생성
     *
     * <p>Chain of Thought 패턴 사용
     */
    public String generateChainOfThoughtPrompt(String query) {
        return String.format("""
                사용자 질문: %s

                단계별로 생각해 봅시다:

                단계 1: 질문 유형 분석
                - 이 질문이 어떤 유형의 질문인가요? (기본 검색, 시간 필터, 가격 비교, 추천)

                단계 2: 필수 파라미터 추출
                - 출발 공항: ?
                - 도착 공항: ?
                - 날짜: ?
                - 시간 조건: ?
                - 가격 조건: ?

                단계 3: 누락된 파라미터 확인
                - 어떤 파라미터가 누락되었나요?
                - 기본값을 적용할 수 있나요?

                단계 4: 검색 방법 결정
                - 어떤 검색 방법을 사용할까요?
                - 어떤 순서로 검색을 진행할까요?
                """, query);
    }

    /**
     * Few-Shot 프롬프트 생성
     *
     * <p>Few-Shot Prompting 패턴 사용
     */
    public String generateFewShotPrompt(
            String query,
            List<FewShotExample> examples
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("다음 예제들을 참고하여 질문을 분석하세요:\n\n");

        for (int i = 0; i < examples.size(); i++) {
            FewShotExample example = examples.get(i);
            sb.append(String.format("예제 %d:\n", i + 1));
            sb.append(String.format("질문: %s\n", example.query()));
            sb.append("분석 결과:\n");
            example.parameters().forEach((key, value) ->
                    sb.append(String.format("  - %s: %s\n", key, value))
            );
            sb.append("\n");
        }

        sb.append(String.format("\n이제 다음 질문을 분석하세요:\n질문: %s\n", query));

        return sb.toString();
    }

    /**
     * Step-Back 프롬프트 생성
     *
     * <p>Step-Back Prompting 패턴 사용
     */
    public String generateStepBackPrompt(String query) {
        return String.format("""
                사용자 질문: %s

                먼저 한 단계 뒤로 물러나서, 항공편 검색 시스템의 관점에서 이 질문을 분석해 봅시다:

                1. 사용자의 근본 의도는 무엇인가요?
                   - 단순 정보 검색?
                   - 구매 결정 지원?
                   - 일정 계획 수립?

                2. 어떤 제약 조건이 있나요?
                   - 시간적 제약?
                   - 예산적 제약?
                   - 항공사 선호도?

                3. 어떤 정보가 가장 중요한가요?
                   - 가격?
                   - 시간?
                   - 편의성?

                이러한 배경 지식을 바탕으로 최적의 검색 전략을 수립해 봅시다.
                """, query);
    }

    /**
     * Contextual 프롬프트 생성
     *
     * <p>Contextual Prompting 패턴 사용
     */
    public String generateContextualPrompt(
            String basePrompt,
            Map<String, Object> context
    ) {
        StringBuilder sb = new StringBuilder(basePrompt);
        sb.append("\n\n컨텍스트:\n");

        context.forEach((key, value) ->
                sb.append(String.format("- %s: %s\n", key, value))
        );

        return sb.toString();
    }

    /**
     * Few-Shot 예제 레코드
     */
    public record FewShotExample(
            String query,
            Map<String, Object> parameters
    ) {
        @Override
        public String toString() {
            return String.format("Query: %s, Parameters: %s", query, parameters);
        }
    }
}

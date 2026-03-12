# Spring AI 학습 가이드

이 문서는 항공 스케줄 MCP 프로젝트에서 구현한 Spring AI 패턴과 추가 학습 리소스를 안내합니다.

## 📚 공식 학습 리소스

### Spring AI 공식 문서
- **Spring AI Project**: https://spring.io/projects/spring-ai
- **Spring AI Reference Documentation**: https://docs.spring.io/spring-ai/reference/
- **Spring AI API Documentation**: https://docs.spring.io/spring-ai/api/

### Awesome Spring AI (커뮤니티 관련 리소스)
- **GitHub Repository**: https://github.com/spring-ai-community/awesome-spring-ai
- 50개 이상의 튜토리얼, 예제, 블로그 글, 비디오 강의 포함

## 🎯 이 프로젝트에서 구현한 Spring AI 패턴

### 1. Model Context Protocol (MCP)

#### 핵심 컴포넌트
- **@Tool Annotation**: LLM이 호출할 수 있는 도구로 메서드 등록
- **@ToolParam Annotation**: 도구 파라미터에 대한 설명 제공
- **McpConfig**: MCP 서버 프로토콜 설정

#### 구현 파일
```
src/main/java/com/nhnacademy/flyschedule/
├── config/McpConfig.java                          # MCP 서버 설정
└── mcp/
    ├── FlightSearchTool.java                      # 항공편 검색 도구
    ├── AirportInfoTool.java                       # 공항 정보 도구
    └── AirlineInfoTool.java                       # 항공사 정보 도구
```

#### 예시 코드
```java
@Tool(description = "항공편을 검색하여 항공사별로 그룹핑합니다")
public Map<String, List<FlightInfoResponse>> searchFlightsByAirline(
    @ToolParam(description = "출발 공항 이름") String departure,
    @ToolParam(description = "도착 공항 이름") String arrival,
    @ToolParam(description = "날짜 (예: 내일, 모레, 2025-03-09)") String date
) {
    // 구현 로직
}
```

> **참고**: 이 프로젝트는 Spring AI의 공식 `org.springframework.ai.tool.annotation.Tool` 및 `@ToolParam` 어노테이션을 사용합니다.

### 2. Prompt Engineering Patterns

#### 2.1 System Prompting
**용도**: 시스템의 전체 동작과 역할 정의

**구현 위치**: `PromptTemplateService.java`

```java
public static final String REASONING_SYSTEM_PROMPT = """
    당신은 항공편 검색 시스템의 추론 에이전트입니다.
    사용자의 자연어 질문을 분석하여 적절한 검색 전략을 수립합니다.
    ...
    """;
```

**적용 에이전트**:
- ReasoningAgent: 추론 및 질문 분석
- RecommendationAgent: 항공편 추천
- ResponseFormatterAgent: 응답 포맷팅

#### 2.2 Role Prompting
**용도**: AI 모델에게 특정 역할이나 페르소나 부여

**구현 위치**: `PromptTemplateService.java`, `RecommendationAgent.java`

```java
public static final String RECOMMENDATION_SYSTEM_PROMPT = """
    당신은 항공편 추천 전문가입니다.
    사용자의 요구사항과 예산을 고려하여 최적의 항공편을 추천합니다.

    <추천 기준>
    1. 가격: 저렴한 가격 우선
    2. 시간: 편리한 출발/도착 시간
    3. 가성비: 가격과 편의성의 균형
    4. 항공사: 신뢰할 수 있는 항공사 우선
    ...
    """;
```

#### 2.3 Few-Shot Prompting
**용도**: 모델이 패턴을 학습할 수 있도록 예제 제공

**구현 위치**: `PromptTemplateService.java`

```java
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
    // 추가 예제...
);
```

#### 2.4 Chain of Thought (CoT) Prompting
**용도**: 복잡한 문제를 단계별로 추론하여 정확도 향상

**구현 위치**: `ReasoningAgent.java`

```java
public ReasoningResult analyzeQuery(String query) {
    List<String> thoughts = new ArrayList<>();

    // 단계 1: 질문 유형 분석
    thoughts.add("=== 단계 1: 질문 유형 분석 ===");
    QuestionType type = analyzeQuestionType(query, thoughts);

    // 단계 2: 필수 파라미터 추출
    thoughts.add("\n=== 단계 2: 필수 파라미터 추출 ===");
    QueryParameters params = extractParameters(query, thoughts);

    // 단계 3: 파라미터 검증
    thoughts.add("\n=== 단계 3: 파라미터 검증 ===");
    boolean valid = validateParameters(params, thoughts);

    // 단계 4: 최종 결론
    thoughts.add("\n=== 단계 4: 최종 결론 ===");
    String conclusion = formConclusion(type, params, valid, thoughts);

    return ReasoningResult.builder()
        .thoughts(thoughts)
        .conclusion(conclusion)
        .build();
}
```

#### 2.5 Step-Back Prompting
**용도**: 문제를 더 높은 수준에서 분석하여 배경 지식 활용

**구현 위치**: `ReasoningAgent.java`

```java
private String performStepBackAnalysis(String query) {
    StringBuilder reasoning = new StringBuilder();
    reasoning.append("사용자의 근본 의도 분석:\n");

    // 1. 사용자 의도 파악
    reasoning.append("  1. 사용자 의도: ");
    // ... 의도 분석 로직

    // 2. 제약 조건 분석
    reasoning.append("  2. 제약 조건: ");
    // ... 제약 조건 분석 로직

    // 3. 중요 정보 파악
    reasoning.append("  3. 중요 정보: ");
    // ... 중요 정보 파악 로직

    return reasoning.toString();
}
```

#### 2.6 Contextual Prompting
**용도**: 추가 배경 정보를 제공하여 관련성 높은 응답 생성

**구현 위치**: `ResponseFormatterAgent.java`

```java
private Map<String, Object> buildContext(
    ReasoningAgent.ReasoningResult reasoning,
    Map<String, List<FlightInfoResponse>> flights,
    String recommendation
) {
    Map<String, Object> context = new HashMap<>();

    // 질문 유형 컨텍스트
    context.put("questionType", reasoning.getQuestionType().toString());

    // 항공편 통계 컨텍스트
    context.put("flightCount", flights.values().stream().mapToInt(List::size).sum());

    // 추천 유형 컨텍스트
    context.put("recommendationType", determineRecommendationType(recommendation));

    return context;
}
```

### 3. Agent-to-Agent (A2A) Orchestration

#### 에이전트 아키텍처
```
┌─────────────────────────────────────────────────────────┐
│                  MultiAgentOrchestrator                 │
│            (다중 에이전트 조율 및 조정)                   │
└──────────────────────┬──────────────────────────────────┘
                       │
        ┌──────────────┼──────────────┬──────────────┐
        │              │              │              │
┌───────▼──────┐ ┌────▼─────┐ ┌────▼─────┐ ┌─────▼──────┐
│ReasoningAgent│ │Flight    │ │Recommend │ │Response    │
│              │ │Search    │ │ationAgent│ │Formatter   │
│(추론 및 의도 │ │Agent     │ │          │ │Agent       │
│  파악)       │ │(항공편   │ │(최적     │ │(자연어     │
│              │ │검색)     │ │항공편    │ │변환)       │
│              │ │          │ │추천)     │ │            │
└──────────────┘ └──────────┘ └──────────┘ └────────────┘
```

#### 전문화된 서브 에이전트
1. **DateParserAgent**: 자연어 날짜 파싱 ("내일" → "20260310")
2. **AirportCodeAgent**: 공항 이름 → 코드 매핑
3. **TimeFilterAgent**: 시간 기반 필터링
4. **PriceFilterAgent**: 가격 기반 필터링 및 분석
5. **GroupingAgent**: 항공사별 그룹핑
6. **AirportListAgent**: 공항 목록 조회
7. **AirlineListAgent**: 항공사 목록 조회

## 📖 추천 학습 경로

### 1단계: Spring AI 기초
1. **Spring AI 공식 문서**: 기본 개념 이해
2. **Prompt Engineering Patterns**: 프롬프트 기법 학습
3. **MCP Getting Started Guide**: MCP 프로토콜 이해

### 2단계: 실습 프로젝트
1. 이 프로젝트 코드 분석
2. 각 에이전트의 역할과 패턴 이해
3. MCP 툴 구현 방법 학습

### 3단계: 심화 학습
1. **Vector Stores**: 시맨틱 검색 구현
2. **Structured Outputs**: BeanOutputConverter 활용
3. **RAG (Retrieval Augmented Generation)**: 문서 기반 QA

### 4단계: 고급 패턴
1. **ReAct Pattern**: 추론 + 행동 결합
2. **Multi-Agent Orchestration**: 복잡한 작업 분할
3. **Self-Consistency**: 다중 추론 앙상블

## 🎓 Udemy 강의 추천

### 초급
1. **"Build AI Apps with Spring AI, OpenAI, Ollama & SpringBoot"**
   - Spring AI 기초부터 실습까지

### 중급
2. **"From Java Dev to AI Engineer: Spring AI Fast Track"**
   - RAG, MCP, 테스트, 관찰 가능성 포함

### 고급
3. **"In-Depth: Developing Generative AI Applications with Spring"**
   - 엔터프라이즈급 AI 애플리케이션 개발

## 📺 추천 YouTube 채널 및 영상

### 공식 채널
1. **Spring AI Tutorial - SivaLabs**
   - 포괄적인 Spring AI 튜토리얼 시리즈

2. **Telusko Spring AI Tutorial Playlist**
   - OpenAI, Anthropic, Ollama 통합

### 추천 영상
1. **"The State of the Art of Spring AI" by Josh Long @ GOTO 2025**
   - Spring AI의 현재 상태와 로드맵

2. **"Practical Agentic RAG with Spring AI" by Dan Dobrin @ Spring I/O 2025**
   - 엔터프라이즈 RAG 구현

3. **"Modular RAG Architectures" by Thomas Vitale @ Spring I/O 2025**
   - 모듈식 RAG 아키텍처

## 🔧 유용한 도구 및 라이브러리

### 개발 도구
1. **Arconia Ollama Dev Service**
   - 로컬 LLM 개발을 위한 Ollama 자동 관리

2. **Spring AI HTMX MCP**
   - HTMX를 활용한 현대적 UI 구현

3. **DocumentGPT**
   - RAG 기반 문서 쿼리 시스템

### 벤치마크
1. **OpenLLM Leaderboard**
   - LLM 성능 비교

## 🌟 커뮤니티 리더

팔로우 추천:
- **Christian Tzolov**: Spring AI 핵심 개발자
- **Josh Long**: Spring 공신 evangelist
- **Dan Vega**: Spring 개발자 advocate
- **Thomas Vitale**: Spring AI 전문가
- **Dan Dobrin**: Spring AI 엔터프라이즈 전문가
- **Marcus Hellberg**: Vaadin + Spring AI 전문가

## 📝 블로그 글 추천

1. **"Your First Spring AI 1.0 Application"** (May 2025)
   - RAG, MCP, Vector Stores, PostgreSQL 활용

2. **"Building AI-Native APIs with Spring AI and MCP"** (September 2025)
   - MCP 서버 구현 가이드

3. **"Mastering Spring AI: Concepts Tutorial"** (Updated August 2025)
   - Spring AI 1.0 GA 기반 개념 튜토리얼

4. **"Spring AI Concepts Tutorial With Examples"** (March 2025)
   - ChatModel/ChatClient, Advisors, Chatbot 예제

## 🔗 추가 리소스

### Podcasts
1. **This Day in AI**
2. **Practical AI from Changelog**
3. **Spring Office Hours**
4. **Bootiful Podcast**

### 코드 예제
1. **Spring AI Samples by Thomas Vitale**
   - 50개 이상의 Spring AI 예제

2. **Spring AI Examples by Craig Walls**
   - "Spring AI in Action" 저자의 예제 코드

3. **Spring AI Showcase by Piotr Minkowski**
   - 다중 제공자 지원 모듈식 데모

## 🚀 다음 단계

이 프로젝트를 마스터한 후:
1. **Vector Store 통합**: PostgreSQL + pgvector로 시맨틱 검색 추가
2. **RAG 구현**: 항공편 가이드북 PDF 기반 QA 시스템
3. **멀티모달**: 항공기 이미지 분석 기능
4. **스트리밍 응답**: 실시간 항공편 업데이트

---

**참고**: 이 문서는 https://github.com/spring-ai-community/awesome-spring-ai 리포지토리를 기반으로 작성되었습니다.

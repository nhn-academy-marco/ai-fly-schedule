# 항공 스케줄 API 프로젝트

**MCP & A2A 오케스트레이션**+ **Spring AI 1.1.4**학습을 위한 Spring Boot 실습 프로젝트

---

## MCP & A2A란?

### MCP (Model Context Protocol)
- AI 모델이 **외부 도구(Tools)**와 **데이터 소스**에 접근하는 표준 프로토콜
- AI 모델이 검색, 계산, API 호출 등을 할 수 있게 해줌
- Spring AI의 **@Tool**어노테이션으로 쉽게 구현 가능

### A2A (Agent-to-Agent)
- 여러 **전문화된 에이전트**가 협력하여 복잡한 작업을 해결하는 패턴
- 각 에이전트가 특정 역할을 맡고 서로 통신하며 문제 해결

---

## 데모 사이트

실제 동작하는 데모 사이트에서 Spring AI & A2A 오케스트레이션을 체험해보세요!

**[https://fly.java21.net](https://fly.java21.net)**

- 자연어 항공편 검색
- MCP Tool 호출 데모
- A2A 오케스트레이션 실시간 확인
- LLM 기반 파라미터 추출

---

## 4주 완성 Step별 학습 경로

이 프로젝트는 **11개의 Step**으로 구성된 단계별 교재입니다.

| 주차 | Step | 주제 | 학습 시간 |
|------|------|------|----------|
| **1주차**| Step 1-2 | Spring AI 기초 | 7-9시간 |
| **2주차**| Step 3-5 | Function Calling & MCP Tool | 13-16시간 |
| **3주차**| Step 6-8 | 항공편 Tool & A2A 기초 | 13-17시간 |
| **4주차**| Step 9-11 | A2A 심화 & 배포 | 15-19시간 |

---

## Step별 문서

### Step 1: Spring AI 환경 설정
**[step-1-spring-ai-settings.md](./step-1-spring-ai-settings.md)**

- Spring AI 1.1.4 의존성 설정
- Ollama 로컬 LLM 연동
- Google Gemini API 연동
- application.yml을 통한 LLM 설정

---

### Step 2: ChatClient 기초와 LLM 연동
**[step-2-chatclient-basics.md](./step-2-chatclient-basics.md)**

- ChatClient, ChatModel 사용법
- Ollama와 Gemini 모델 선택적 사용
- ChatClient.Builder로 ChatClient 생성
- 간단한 질문-답변 구현

---

### Step 3: Function Calling 기초
**[step-3-function-calling.md](./step-3-function-calling.md)**

- **@Tool**, **@ToolParam**annotation 사용
- 첫 번째 Function Call 구현
- LLM이 자바 메서드를 호출하는 과정 이해
- Tool 등록 방법

---

### Step 4: Tool Callback과 실행 과정 이해
**[step-4-tool-callback.md](./step-4-tool-callback.md)**

- ToolCallingCallback 구현
- Function Calling 실행 과정 로깅
- LLM의 Tool 선택 과정 이해
- 디버깅 및 성능 최적화

---

### Step 5: 다중 Function Calling과 MCP Tool 패턴
**[step-5-mcp-tool-pattern.md](./step-5-mcp-tool-pattern.md)**

- 여러 Tool을 효과적으로 관리
- MCP Tool 설계 패턴 적용
- Tool 간의 관계와 책임 분리
- 재사용 가능한 Tool 구조 설계

---

### Step 6: 항공편 검색 Tool 구현
**[step-6-flight-search-tool.md](./step-6-flight-search-tool.md)**

- RestClient로 외부 API 호출
- 공공데이터 포털 API 연동
- 항공사별 그룹핑 구현
- 시간/가격 필터링 구현

---

### Step 7: 공항/항공사 정보 Tool 구현
**[step-7-airport-info-tool.md](./step-7-airport-info-tool.md)**

- 공항 목록 조회 Tool 구현
- 공항 코드 조회 Tool 구현
- 항공사 목록 조회 Tool 구현
- 항공사 ID 조회 Tool 구현
- 캐싱 전략 적용

---

### Step 8: A2A 개념과 단일 에이전트 구현
**[step-8-a2a-single-agent.md](./step-8-a2a-single-agent.md)**

- A2A 패턴과 MCP Tool의 차이 이해
- 전문화된 단일 에이전트 구현
- 에이전트 간 통신 구현
- 단일 책임 원칙 적용

---

### Step 9: Coordinator 패턴과 에이전트 통신
**[step-9-coordinator-pattern.md](./step-9-coordinator-pattern.md)**

- Coordinator 패턴 구현
- 에이전트 간 통신 구현
- 작업 흐름 제어
- 에러 처리 및 재시도

---

### Step 10: 복합 A2A 시스템과 자연어 검색
**[step-10-complex-a2a-system.md](./step-10-complex-a2a-system.md)**

- LLM을 통한 파라미터 추출
- A2A 오케스트레이션과 LLM 통합
- 자연어 검색 시스템 구현
- 복잡한 필터링 처리

---

### Step 11: 테스트와 배포
**[step-11-testing-deployment.md](./step-11-testing-deployment.md)**

- Spring AI 단위 테스트 작성
- 통합 테스트 구현
- LLM 호출 Mock 처리
- Docker를 이용한 배포

---

## 학습 목표

이 프로젝트를 통해 다음을 학습할 수 있습니다:

### 기본 기능
-  **Spring AI 1.1.2**: 최신 Spring AI 프레임워크 활용
-  **ChatClient & ChatModel**: LLM과 대화하는 인터페이스
-  **Function Calling**: LLM이 자바 메서드를 호출
-  **MCP Tool**: AI 모델이 사용할 수 있는 도구 구현
-  **A2A 오케스트레이션**: 전문화된 에이전트 간 협업
-  **RestClient**: Spring 6.1+ Modern HTTP Client

### Spring AI 1.1.2 핵심 기술
-  **@Tool / @ToolParam**: 공식 Spring AI Tool Annotation
-  **ToolCallingCallback**: Function Calling 실행 과정 모니터링
-  **다중 LLM 지원**: Ollama, Gemini 동시 사용
-  **ChatClient.Builder**: 유창한 API로 LLM 통합

---

## 시나리오 예시

### 예시 1: 기본 검색 (항공사별 그룹핑)

**입력 (자연어)**
```
"내일 광주에서 제주로 가는 항공편 알려줘"
```

**처리 과정 (A2A 오케스트레이션)**
```
[Coordinator Agent]
    ↓ 사용자 의도 파악

[Date Parser Agent]
    ↓ "내일" → 2026-03-11 변환

[Airport Code Agent]
    ↓ "광주" → NAARKJJ
    ↓ "제주" → NAARKPC

[Flight Search Agent]
    ↓ 공공데이터 API 호출
    ↓ 항공편 목록 조회

[Grouping Agent]
    ↓ 항공사별로 그룹핑

[Response Formatter Agent]
    ↓ 결과를 자연어로 변환
```

**출력 (자연어)**
```
네, 내일 광주에서 제주로 가는 항공편을 찾았어요!

 항공사별 정렬된 결과입니다.

──────────────────────
 아시아나항공 (2편)
──────────────────────

1. OZ8141편
   출발: 09:55 → 도착: 11:25
   가격: 57,900원

2. OZ8143편
   출발: 14:30 → 도착: 16:00
   가격: 62,000원

──────────────────────
 제주항공 (3편)
──────────────────────

1. 7C1402편
   출발: 10:30 → 도착: 12:00
   가격: 45,000원

...

총 6편의 항공편이 있으며,
가장 저렴한 항공편은 제주항공 7C1402편으로 45,000원입니다.
```

---

## API 정보

| 항목 | 내용 |
|------|------|
| 제공기관 | 국토교통부 |
| API 유형 | REST |
| 기본 URL | `https://apis.data.go.kr/1613000/DmstcFlightNvgInfo` |
| 데이터 포맷 | JSON, XML |
| 비용 | 무료 |

---

## 기술 스택

### 핵심 기술
- **Java 21**
- **Spring Boot 3.3**(Spring AI 1.1.2 호환)
- **Spring Web MVC**
- **RestClient**(Spring 6.1+ Modern HTTP Client)
- **Thymeleaf**(템플릿 엔진)
- **Lombok**(롬복)
- **Maven**

### AI/LLM
- **Ollama**- 로컬/원격 LLM 서버
- **Qwen 2.5**- 자연어 분석 모델
- **Gemini 2.5 Flash**- Google GenAI API
- **Spring AI 1.1.2**- AI 통합 프레임워크

### Spring AI 핵심 기능
- **@Tool / @ToolParam**: 공식 Spring AI Tool Annotation
- **ToolCallingCallback**: Function Calling 실행 과정 모니터링
- **ChatClient.Builder**: 유창한 API로 LLM 통합

### UI
- **Bootstrap 5.0**
- **Tabler.io Template**
- **Fetch API**(비동기 AJAX)

---

## 사전 요구사항

1. **Java 21**설치
2. **IntelliJ IDEA**(또는 Eclipse)
3. **Maven**빌드 도구
4. **공공데이터 포털 인증키**(무료 발급)
5. **Google API Key**(Gemini 사용 시, 유료지만 저렴)

---

## 관련 링크

### 공식 문서
- [공공데이터포털](https://www.data.go.kr/)
- [국내항공운항정보 API](https://www.data.go.kr/data/15098526/openapi.do)
- [Spring Boot 공식 문서](https://spring.io/projects/spring-boot)
- [Spring AI 공식 문서](https://docs.spring.io/spring-ai/reference/)

### 커뮤니티 리소스
- [Awesome Spring AI](https://github.com/spring-ai-community/awesome-spring-ai)

---

## 라이선스

이 프로젝트는 학습 목적으로 제작되었습니다.

---

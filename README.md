# 항공 스케줄 A2A 오케스트레이션 프로젝트

**Spring AI 1.1.4**와 **MCP(Model Context Protocol)**를 활용한 지능형 항공편 검색 시스템입니다.
이 프로젝트는 **A2A(Agent-to-Agent) 오케스트레이션**패턴을 통해 복잡한 자연어 질문을 처리하는 과정을 단계별로 학습할 수 있도록 구성되어 있습니다.

---

## 🚀 주요 기능

- **자연어 항공편 검색**: "내일 오후 2시 이후 광주에서 제주로 가는 저렴한 항공편 알려줘"와 같은 복잡한 요청 처리
- **MCP Tool 통합**: 공공데이터 포털 API를 LLM의 도구(Tool)로 연동
- **A2A 오케스트레이션**: 날짜 파싱, 공항 코드 변환, 항공편 검색, 결과 필터링 및 추천을 담당하는 전문 에이전트들의 협업
- **다중 LLM 지원**: 로컬 LLM(Ollama)과 클라우드 LLM(Google Gemini) 선택적 사용
- **실시간 응답 생성**: 항공사별 그룹핑 및 사용자 맞춤형 추천 결과 제공

---

## 🛠 기술 스택

- **Java 21**
- **Spring Boot 3.5.11**
- **Spring AI 1.1.4**
- **Ollama / Google Gemini (LLM)**
- **Thymeleaf / Bootstrap 5**
- **Maven**

---

## 📚 학습 경로 (4주 완성)

이 프로젝트는 [docs](./docs) 디렉토리에 상세한 단계별 학습 문서를 포함하고 있습니다.

### 1주차: Spring AI 기초
- [Step 1: 환경 설정](./docs/step-1-spring-ai-settings.md) (Ollama, Gemini 연동)
- [Step 2: ChatClient 기초](./docs/step-2-chatclient-basics.md) (Fluent API 사용법)

### 2주차: Function Calling & MCP
- [Step 3: Function Calling 기초](./docs/step-3-function-calling.md) (@Tool 사용법)
- [Step 4: Tool Callback](./docs/step-4-tool-callback.md) (실행 과정 모니터링)
- [Step 5: MCP Tool 패턴](./docs/step-5-mcp-tool-pattern.md) (도구 설계 원칙)

### 3주차: 항공편 검색 실무 & A2A 기초
- [Step 6: 항공편 검색 Tool](./docs/step-6-flight-search-tool.md) (외부 API 연동)
- [Step 7: 공항/항공사 Tool](./docs/step-7-airport-info-tool.md) (데이터 관리)
- [Step 8: A2A 단일 에이전트](./docs/step-8-a2a-single-agent.md) (에이전트 구현)

### 4주차: A2A 심화 & 배포
- [Step 9: Coordinator 패턴](./docs/step-9-coordinator-pattern.md) (워크플로우 제어)
- [Step 10: 복합 A2A 시스템](./docs/step-10-complex-a2a-system.md) (자연어 검색 통합)
- [Step 11: 테스트와 배포](./docs/step-11-testing-deployment.md) (단위/통합 테스트, Docker)

---

## 🏁 시작하기

### 1. 사전 요구사항
- **Java 21**설치
- **Ollama**설치 (로컬 모델 실행 시)
- **Google Gemini API Key**(Gemini 사용 시)
- **공공데이터 포털 서비스 키**(항공편 검색 API용)

### 2. 설정 (application.yml)
`src/main/resources/application.yml` 파일에서 API 키를 설정합니다.
```yaml
data-go-kr:
  api:
    service-key: "YOUR_SERVICE_KEY"

spring.ai.google.genai:
  api-key: "YOUR_GEMINI_API_KEY"
```

### 3. 빌드 및 실행
```bash
mvn clean install
mvn spring-boot:run
```

---

## 🔗 관련 문서
- [프로젝트 개요 (docs)](./docs/README.md)
- [공공데이터포털 - 국내항공운항정보](https://www.data.go.kr/data/15098526/openapi.do)
- [Spring AI 공식 문서](https://docs.spring.io/spring-ai/reference/)

---

## 📝 라이선스
이 프로젝트는 학습 및 교육 목적으로 제작되었습니다.

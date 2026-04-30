# Step 11: 테스트와 배포

## 학습 목표
이 단계에서는 Spring AI 애플리케이션을 **테스트**하고 **배포**하는 방법을 학습합니다.

**학습 완료 후 할 수 있는 것:**
- Spring AI 단위 테스트 작성
- 통합 테스트 구현
- LLM 호출 Mock 처리
- Docker를 이용한 배포

---

## 테스트 전략

### 테스트 피라미드

```
        ┌──────────────┐
        │  E2E Tests   │  ← 전체 시스템 테스트 (소수)
        │  (브라우저)   │
        └──────────────┘
              ↓
        ┌──────────────┐
        │Integration   │  ← 컴포넌트 간 통합 테스트
        │   Tests      │
        └──────────────┘
              ↓
        ┌──────────────┐
        │  Unit Tests  │  ← 개별 컴포넌트 테스트 (다수)
        └──────────────┘
```

---

## Step 11-1: 단위 테스트

### DateParserAgent 테스트

```java
package com.nhnacademy.flyschedule.service.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

class DateParserAgentTest {

    private final DateParserAgent dateParser = new DateParserAgent();

    @Test
    @DisplayName("내일 파싱")
    void parseTomorrow() {
        String result = dateParser.parseDate("내일");
        assertNotNull(result);
        assertTrue(result.matches("\\d{8}"));
    }

    @Test
    @DisplayName("모레 파싱")
    void parseDayAfterTomorrow() {
        String result = dateParser.parseDate("모레");
        assertNotNull(result);
        assertTrue(result.matches("\\d{8}"));
    }

    @Test
    @DisplayName("특정 날짜 파싱")
    void parseSpecificDate() {
        String result = dateParser.parseDate("2026-03-10");
        assertEquals("20260310", result);
    }

    @Test
    @DisplayName("잘못된 날짜 형식 예외")
    void parseInvalidDate() {
        assertThrows(IllegalArgumentException.class, () -> {
            dateParser.parseDate("2026/03/10");
        });
    }
}
```

### AirportCodeAgent 테스트

```java
package com.nhnacademy.flyschedule.service.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

class AirportCodeAgentTest {

    private final AirportCodeAgent airportAgent = new AirportCodeAgent();

    @Test
    @DisplayName("광주 공항 코드 변환")
    void getGwangjuCode() {
        String code = airportAgent.getAirportCode("광주");
        assertEquals("NAARKJJ", code);
    }

    @Test
    @DisplayName("제주 공항 코드 변환")
    void getJejuCode() {
        String code = airportAgent.getAirportCode("제주");
        assertEquals("NAARKPC", code);
    }

    @Test
    @DisplayName("이미 공항 코드인 경우")
    void alreadyAirportCode() {
        String code = airportAgent.getAirportCode("NAARKJJ");
        assertEquals("NAARKJJ", code);
    }

    @Test
    @DisplayName("알 수 없는 공항 예외")
    void unknownAirport() {
        assertThrows(IllegalArgumentException.class, () -> {
            airportAgent.getAirportCode("화성");
        });
    }
}
```

---

## Step 11-2: Spring AI 통합 테스트

### ChatClient 테스트 (Mock 사용)

```java
package com.nhnacademy.flyschedule.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
class LlmAnalysisServiceTest {

    @Autowired
    private LlmAnalysisService llmAnalysisService;

    @MockBean
    private ChatModel mockChatModel;

    @Test
    @DisplayName("LLM 파라미터 추출 테스트")
    void extractFlightSearchParams() {
        // Given
        String message = "내일 광주에서 제주로 가는 항공편 알려줘";

        // Mock 응답 설정
        ChatResponse mockResponse = ChatResponse.builder()
                .generation(Generation.builder()
                        .content("{\"departure\":\"광주\",\"arrival\":\"제주\",\"date\":\"내일\"}")
                        .build())
                .build();

        when(mockChatModel.call(any())).thenReturn(mockResponse);

        // When
        Map<String, Object> params = llmAnalysisService.extractFlightSearchParams(message);

        // Then
        assertNotNull(params);
        assertEquals("광주", params.get("departure"));
        assertEquals("제주", params.get("arrival"));
        assertEquals("내일", params.get("date"));
    }
}
```

### OrchestrationService 통합 테스트

```java
package com.nhnacademy.flyschedule.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class NaturalLanguageOrchestrationServiceTest {

    @Autowired
    private NaturalLanguageOrchestrationService orchestrationService;

    @Test
    @DisplayName("기본 검색 오케스트레이션")
    void orchestrateBasicSearch() {
        // Given
        String message = "내일 광주에서 제주로 가는 항공편 알려줘";

        // When
        NaturalLanguageOrchestrationService.OrchestrationResult result =
                orchestrationService.orchestrateFlightSearch(message);

        // Then
        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
        assertNotNull(result.getExtractedParams());
    }

    @Test
    @DisplayName("파라미터 누락 시 에러")
    void orchestrateWithMissingParams() {
        // Given
        String message = "항공편 알려줘";  // 출발/도착 공항 누락

        // When
        NaturalLanguageOrchestrationService.OrchestrationResult result =
                orchestrationService.orchestrateFlightSearch(message);

        // Then
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("공항"));
    }
}
```

---

## Step 11-3: REST API 테스트

### MockMvc 테스트

```java
package com.nhnacademy.flyschedule.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class NaturalLanguageSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("자연어 검색 API 테스트")
    void testNaturalLanguageSearch() throws Exception {
        // Given
        String requestBody = """
            {"message": "내일 광주에서 제주로 가는 항공편 알려줘"}
            """;

        // When & Then
        mockMvc.perform(post("/api/nl-search/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    @DisplayName("메시지 누락 시 400 에러")
    void testMissingMessage() throws Exception {
        // Given
        String requestBody = "{}";

        // When & Then
        mockMvc.perform(post("/api/nl-search/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }
}
```

---

## Step 11-4: LLM 호출 Mock 전략

### 왜 Mock이 필요한가?

| 이유 | 설명 |
|------|------|
| **비용 절감**| 실제 LLM API 호출 비용 절감 |
| **속도**| LLM 호출은 느림 (초~분 단위) |
| **일관성**| 항상 같은 응답 보장 |
| **오프라인**| 인터넷 없이 테스트 가능 |

### MockChatModel 구현

```java
package com.nhnacademy.flyschedule.test;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ChatPrompt;
import org.springframework.ai.chat.model.Generation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;

@Configuration
@Profile("test")
class TestChatConfig {

    @Bean
    public ChatClient.Builder testChatClientBuilder() {
        MockChatModel mockModel = new MockChatModel();
        return ChatClient.builder(mockModel);
    }

    static class MockChatModel implements ChatModel {

        @Override
        public String getName() {
            return "mock-model";
        }

        @Override
        public ChatResponse call(ChatPrompt prompt) {
            // 테스트용 가짜 응답
            String mockResponse = """
                {
                    "departure": "광주",
                    "arrival": "제주",
                    "date": "내일"
                }
                """;

            return ChatResponse.builder()
                    .generation(Generation.builder()
                            .content(mockResponse)
                            .build())
                    .build();
        }
    }
}
```

---

## Step 11-5: Docker 배포

### Dockerfile 작성

```dockerfile
# OpenJDK 21 기반 이미지
FROM openjdk:21-slim

# 작업 디렉토리 설정
WORKDIR /app

# JAR 파일 복사
COPY target/flyschedule-0.0.1-SNAPSHOT.jar app.jar

# 실행 권한 설정
RUN chmod +x app.jar

# 포트 노출
EXPOSE 8080

# JVM 옵션 설정
ENV JAVA_OPTS="-Xms512m -Xmx1024m"

# 애플리케이션 실행
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

### docker-compose.yml 작성

```yaml
version: '3.8'

services:
  flyschedule:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=production
      - GEMINI_API_KEY=${GEMINI_API_KEY}
      - DATA_GO_KR_SERVICE_KEY=${DATA_GO_KR_SERVICE_KEY}
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s
```

### Docker 실행

```bash
# 빌드
mvn clean package

# Docker 이미지 빌드
docker build -t flyschedule:latest .

# Docker 컨테이너 실행
docker run -d \
  -p 8080:8080 \
  -e GEMINI_API_KEY=your_key_here \
  -e DATA_GO_KR_SERVICE_KEY=your_key_here \
  --name flyschedule \
  flyschedule:latest

# Docker Compose로 실행
docker-compose up -d
```

---

## Step 11-6: 모니터링과 로깅

### Actuator 설정

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
```

### 헬스 체크

```bash
curl http://localhost:8080/actuator/health
```

**응답:**
```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "diskSpace": { "status": "UP" },
    "ping": { "status": "UP" }
  }
}
```

---

## Step 11-7: 배포 전 체크리스트

### 코드 품질

- [ ] 모든 단위 테스트 통과
- [ ] 통합 테스트 통과
- [ ] 코드 커버리지 70% 이상
- [ ] 불필요한 로그 제거
- [ ] 하드코딩된 값 제거

### 설정

- [ ] 환경 변수로 API 키 관리
- [ ] Production 프로필 설정
- [ ] 로그 레벨 조정 (INFO → WARN)
- [ ] 타임아웃 설정

### 보안

- [ ] API 키가 .gitignore에 추가됨
- [ ] 민감 정보 로그 제거
- [ ] CORS 설정 확인
- [ ] 인증/인가 구현 (필요시)

### 성능

- [ ] 응답 시간 5초 이내
- [ ] 메모리 사용량 모니터링
- [ ] 캐싱 전략 확인
- [ ] DB Connection Pool 확인

---

## 체크리스트

- [ ] 단위 테스트 작성 완료
- [ ] 통합 테스트 작성 완료
- [ ] Mock 테스트 구현 완료
- [ ] Dockerfile 작성 완료
- [ ] docker-compose.yml 작성 완료
- [ ] Actuator 설정 완료
- [ ] 배포 전 체크리스트 완료

---

##  축하합니다!

4주간의 Spring AI & A2A 오케스트레이션 학습을 완료하셨습니다!

### 배운 것들

1. **Spring AI 기초**
   - ChatClient, ChatModel 사용법
   - Ollama, Gemini 연동

2. **Function Calling**
   - @Tool, @ToolParam 사용
   - LLM이 자바 메서드 호출

3. **MCP Tool 구현**
   - 항공편 검색 Tool
   - 공항/항공사 정보 Tool

4. **A2A 오케스트레이션**
   - 전문화된 에이전트 구현
   - Coordinator 패턴
   - 에이전트 간 통신

5. **자연어 검색 시스템**
   - LLM 파라미터 추출
   - A2A + LLM 통합

6. **테스트와 배포**
   - 단위 테스트, 통합 테스트
   - Docker 배포

---

## 추가 학습 주제

1. **LangChain4j**- Java용 LLM 프레임워크
2. **Vector Database**- RAG 구현
3. **Prompt Engineering**- 프롬프트 최적화
4. **Multi-Agent Systems**- 더 복잡한 에이전트 시스템
5. **Observability**- LLM 애플리케이션 모니터링

---

## 참고 자료

- [Spring AI 공식 문서](https://docs.spring.io/spring-ai/reference/)
- [Testing Spring Boot](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing)
- [Docker Spring Boot](https://spring.io/guides/topicals/spring-boot-docker/)
- [A2A Patterns](https://www.microsoft.com/en-us/research/grouping/agentic-systems/)

# Step 2: ChatClient 기초와 LLM 연동

## 학습 목표
이 단계에서는 Spring AI의 **ChatClient**를 사용하여 LLM과 대화하는 방법을 학습합니다.

**학습 완료 후 할 수 있는 것:**
- ChatClient로 간단한 질문-답변 구현
- Ollama와 Gemini 모델 선택적으로 사용
- ChatClient.Builder로 ChatClient 생성
- ChatModel 빈 주입 및 설정

---

## ChatClient란?

**ChatClient**는 Spring AI에서 LLM과 대화하기 위한 **유창한 API(Fluent API)**를 제공하는 인터페이스입니다.

### ChatClient의 필요성

**LLM SDK의 문제점:**

각 LLM 제공자(Ollama, Gemini, OpenAI 등)는 자신만의 SDK를 제공합니다:

```java
// Ollama SDK
OllamaApi ollama = new OllamaApi("http://localhost:11434");
OllamaResult result = ollama.generate("모델명", "프롬프트");

// Gemini SDK
GenerativeModel model = new GenerativeModel("gemini-pro", apiKey);
GenerateContentResponse response = model.generateContent("프롬프트");

// OpenAI SDK
OpenAiService service = new OpenAiService(apiKey);
ChatCompletion result = service.createChatCompletion(...);
```

**문제점:**
- 각 SDK마다 사용법이 다름
- 모델 교체 시 코드 대폭 수정
- 학습 곡선이 높음
- 코드 중복 발생

**ChatClient의 해결책:**

```java
// 통일된 API
String response = chatClient.prompt()
    .user("프롬프트")
    .call()
    .content();

// 모델 교체도 설정만 변경
// 코드는 그대로!
```

### ChatClient의 설계 원칙

**1. 유창한 API (Fluent API)**

메서드 체이닝으로 직관적인 코드 작성:

```java
chatClient
    .prompt()                              // 1. 프롬프트 시작
    .system("너는 항공 도우미야")           // 2. 시스템 프롬프트
    .user("내일 광주에서 제주로")          // 3. 사용자 메시지
    .advisors(specs)                        // 4. 어드바이저 적용
    .call()                                 // 5. 호출
    .content();                             // 6. 응답 반환
```

**2. 빌더 패턴 (Builder Pattern)**

```java
ChatClient client = ChatClient.builder(chatModel)
    .defaultOptions(options)        // 기본 옵션
    .defaultAdvisors(advisors)      // 기본 어드바이저
    .defaultFunctions(functions)    // 기본 함수
    .build();
```

**3. 불변성 (Immutability)**

각 호출은 독립적이고 부작용이 없음:

```java
ChatClient client1 = builder.build();
ChatClient client2 = builder.defaultSystem("다른 역할").build();

// client1과 client2는 서로 영향 없음
```

### ChatClient의 특징

| 특징 | 설명 |
|------|------|
| **빌더 패턴**| 메서드 체이닝으로 직관적인 코드 작성 |
| **다중 LLM 지원**| Ollama, Gemini, OpenAI 등 다양한 모델 사용 |
| **Function Calling**| LLM이 자바 메서드를 호출할 수 있게 함 |
| **타입 안전성**| 컴파일 타임에 타입 체크 |

### ChatClient vs ChatModel

```
┌─────────────────────────────────────────┐
│         ChatClient                      │
│  (LLM과 대화하는 API)                   │
│                                         │
│  .prompt()                              │
│    .user("질문")                        │
│    .call()                              │
│    .content()                           │
└─────────────────────────────────────────┘
           ↓ 사용
┌─────────────────────────────────────────┐
│         ChatModel                       │
│  (실제 LLM 제공자와 연결)               │
│                                         │
│  ┌────────────┐    ┌────────────┐     │
│  │  Ollama    │    │  Gemini    │     │
│  │ChatModel   │    │ChatModel   │     │
│  └────────────┘    └────────────┘     │
└─────────────────────────────────────────┘
```

**요약:**
- **ChatClient**: LLM과 대화하는 방법을 정의하는 API
- **ChatModel**: 실제 LLM(Ollama, Gemini 등)과 연결되는 구현체

---

## Step 2-1: ChatClientConfig 설정

먼저 두 가지 LLM(Ollama, Gemini)을 선택적으로 사용할 수 있도록 설정 클래스를 만듭니다.

### ChatClientConfig.java 생성

Spring에서 `@Configuration`과 `@Bean`을 사용하여 설정 클래스를 만드는 방법은 이미 익숙하실 겁니다. Spring AI도 동일한 방식을 사용합니다.

```java
package com.nhnacademy.flyschedule.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration  // Spring 설정 클래스임을 명시 (익숙한 어노테이션!)
public class ChatClientConfig {

    /**
     * Ollama ChatClient.Builder (기본)
     *
     * Spring AI가 application.yml 설정을 보고 자동으로 생성한 Ollama ChatModel을
     * @Qualifier("ollamaChatModel")로 주입받아 ChatClient.Builder를 생성합니다.
     *
     * 이미 알고 있는 방식과 비교:
     * @Service
     * public class UserService {
     *     private final UserRepository repo;  // @Autowired 생략 가능
     * }
     *
     * 여기서도 동일하게 생성자 주입을 사용합니다!
     */
    @Bean
    @Primary  // 여러 빈 중 기본 빈으로 설정 (ChatClient.Builder 주입 시 자동 선택)
    public ChatClient.Builder ollamaChatClientBuilder(
            @Qualifier("ollamaChatModel") ChatModel ollamaChatModel) {

        // ChatClient.Builder는 LLM과 대화하는 ChatClient를 만드는 빌더
        // Spring의 Builder 패턴과 유사합니다
        return ChatClient.builder(ollamaChatModel);
    }

    /**
     * Gemini ChatClient.Builder
     *
     * Gemini를 사용할 때는 이 빈을 주입받으면 됩니다.
     * @Qualifier로 빈 이름을 명시하여 원하는 모델을 선택할 수 있습니다.
     */
    @Bean
    public ChatClient.Builder geminiChatClientBuilder(
            @Qualifier("googleGenAiChatModel") ChatModel geminiChatModel) {

        return ChatClient.builder(geminiChatModel);
    }
}
```

### 코드 상세 설명

**1. @Configuration**

```java
@Configuration  // 이 클래스는 Bean을 정의하는 설정 클래스
public class ChatClientConfig { ... }
```

- Spring 3.0부터 사용하는 설정 방식
- XML 설정(`<beans>`)을 자바 코드로 대체
- `@Bean` 메서드에서 반환하는 객체들을 Spring 컨테이너가 관리

**2. @Bean**

```java
@Bean  // 반환된 객체를 Spring 컨테이너에 등록
public ChatClient.Builder ollamaChatClientBuilder(...) { ... }
```

- `@Service`, `@Component`와 동일하게 Spring Bean을 등록
- 차이점: 개발자가 직접 객체 생성 (외부 라이브러리 등)
- Spring이 이 메서드를 호출하여 반환된 객체를 관리

**3. @Qualifier**

```java
@Qualifier("ollamaChatModel")  // 이름으로 특정 Bean 선택
ChatModel ollamaChatModel
```

- 같은 타입의 Bean이 여러 개일 때 사용
- 예시: `ChatModel` 타입의 Bean이 2개 (Ollama, Gemini)
- 이름으로 구분하여 원하는 Bean 주입

**4. @Primary**

```java
@Primary  // ChatClient.Builder 타입 주입 시 기본으로 사용
public ChatClient.Builder ollamaChatClientBuilder(...) { ... }
```

- `@Qualifier` 없이 주입할 때 자동 선택
- 주로 기본값으로 사용할 Bean에 지정

### 코드 설명

| 요소 | 설명 |
|------|------|
| `@Configuration` | Spring 설정 클래스임을 명시 |
| `@Bean` | Spring 컨테이너가 관리하는 빈 등록 |
| `@Primary` | 기본 빈으로 설정 (주입 시 우선 사용) |
| `@Qualifier("ollamaChatModel")` | 특정 빈 이름으로 주입 |
| `ChatClient.builder()` | ChatClient를 생성하는 빌더 패턴 |

### Spring AI가 자동으로 생성하는 빈

`application.yml` 설정에 따라 Spring AI가 자동으로 다음 빈들을 생성합니다:

| 빈 이름 | 타입 | 설명 |
|---------|------|------|
| `ollamaChatModel` | `OllamaChatModel` | Ollama LLM 연결 |
| `googleGenAiChatModel` | `GoogleGenAiChatModel` | Gemini LLM 연결 |

---

##  Step 2-2: 첫 번째 대화 서비스 구현

### SimpleChatService.java 생성

이제 실제로 LLM과 대화하는 Service를 만들어 보겠습니다. `@Service`와 생성자 주입은 이미 익숙하시겠죠?

```java
package com.nhnacademy.flyschedule.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

@Service  // Spring이 이 클래스를 Bean으로 자동 관리
public class SimpleChatService {

    // final: 생성자 주입 후 변경 불가 (불변성 보장)
    private final ChatClient ollamaChatClient;
    private final ChatClient geminiChatClient;

    /**
     * 생성자 주입 (Constructor Injection)
     *
     * Spring 4.3+부터 @Autowired 없이 가능
     * Lombok의 @RequiredArgsConstructor로 더 간결하게 작성 가능
     *
     * ChatClient.Builder → ChatClient 빌드 과정:
     * 1. Spring이 ChatClient.Builder Bean을 찾음
     * 2. @Primary가 붙은 ollamaChatClientBuilder를 자동 주입
     * 3. builder.build()로 ChatClient 생성
     */
    public SimpleChatService(
            ChatClient.Builder ollamaChatClientBuilder,
            ChatClient.Builder geminiChatClientBuilder) {

        // Builder 패턴으로 ChatClient 생성
        this.ollamaChatClient = ollamaChatClientBuilder.build();
        this.geminiChatClient = geminiChatClientBuilder.build();
    }

    /**
     * Ollama로 질문하기
     *
     * @param question 사용자 질문
     * @return LLM의 답변
     */
    public String askOllama(String question) {
        return ollamaChatClient.prompt()        // 1. 프롬프트 빌더 시작
                .user(question)                  // 2. 사용자 질문 설정
                .call()                          // 3. LLM 호출 (동기, blocking)
                .content();                      // 4. 응답 내용 반환 (String)
    }

    /**
     * Gemini로 질문하기
     *
     * Ollama와 동일한 API를 사용!
     * 모델만 바꾸면 코드는 그대로 사용 가능
     */
    public String askGemini(String question) {
        return geminiChatClient.prompt()
                .user(question)
                .call()
                .content();
    }
}
```

### ChatClient API 상세 분석

**메서드 체이닝 (Method Chaining)**

```java
chatClient.prompt().user("질문").call().content();
```

각 메서드가 무엇을 하는지 하나씩 뜯어보겠습니다:

```java
// 1단계: prompt() - 프롬프트 빌더 시작
ChatClient.PromptUserSpec promptSpec = chatClient.prompt();
// → 사용자 입력을 구성하는 빌더 객체 반환

// 2단계: user() - 사용자 질문 설정
ChatClient.PromptUserSpec promptSpec2 = promptSpec.user("스프링이 뭐야?");
// → 질문 내용을 설정, 연속 체이닝 가능

// 3단계: call() - LLM 호출
ChatResponse response = promptSpec2.call();
// → LLM에 요청 전송, 응답 대기 (동기 호출)

// 4단계: content() - 응답 내용 추출
String answer = response.content();
// → ChatResponse에서 텍스트 내용만 추출

// 전체: 한 줄로 표현
String answer = chatClient.prompt().user("스프링이 뭐야?").call().content();
```

**왜 이렇게 설계했을까요?**

```
장점 1: 가독성
chatClient.prompt().user("질문").call().content();
→ 무슨 일을 하는지 한눈에 이해 가능

장점 2: 유연성
chatClient.prompt()
    .system("너는 도우미야")
    .user("질문")
    .advisors(specs)
    .call()
    .content();
→ 필요에 따라 중간 단계 추가 가능

장점 3: 타입 안전성
String content = chatClient.prompt()...content();
Map<String, Object> entity = chatClient.prompt()...entity(Map.class);
→ 반환 타입을 컴파일 타임에 체크
```

### ChatClient API 상세

```
ChatClient.prompt()
    ↓
.user("질문 내용")           // 사용자 질문 설정
    ↓
.call()                     // LLM 호출 및 응답 대기
    ↓
.content()                  // 응답 내용 반환 (String)
```

| 메서드 | 반환 타입 | 설명 |
|--------|----------|------|
| `prompt()` | `ChatClient.PromptUserSpec` | 프롬프트 빌더 시작 |
| `user(String)` | `ChatClient.PromptUserSpec` | 사용자 질문 설정 |
| `call()` | `ChatResponse` | LLM 호출 (동기) |
| `content()` | `String` | 응답 내용 반환 |

---

## Step 2-3: 테스트 컨트롤러 구현

### ChatTestController.java 생성

```java
package com.nhnacademy.flyschedule.controller;

import com.nhnacademy.flyschedule.service.SimpleChatService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatTestController {

    private final SimpleChatService chatService;

    public ChatTestController(SimpleChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * Ollama로 질문하기
     * GET /api/chat/ollama?question=안녕
     */
    @GetMapping("/ollama")
    public String askOllama(@RequestParam String question) {
        long startTime = System.currentTimeMillis();

        String response = chatService.askOllama(question);

        long endTime = System.currentTimeMillis();
        String duration = (endTime - startTime) / 1000.0 + "초";

        return "[Ollama 응답 (" + duration + ")]\n" + response;
    }

    /**
     * Gemini로 질문하기
     * GET /api/chat/gemini?question=안녕
     */
    @GetMapping("/gemini")
    public String askGemini(@RequestParam String question) {
        long startTime = System.currentTimeMillis();

        String response = chatService.askGemini(question);

        long endTime = System.currentTimeMillis();
        String duration = (endTime - startTime) / 1000.0 + "초";

        return "[Gemini 응답 (" + duration + ")]\n" + response;
    }
}
```

---

## Step 2-4: 실행 및 테스트

### 1. 애플리케이션 실행

```bash
mvn spring-boot:run
```

### 2. Ollama 테스트

```bash
curl "http://localhost:8080/api/chat/ollama?question=안녕하세요 반갑습니다"
```

**예상 응답:**
```
[Ollama 응답 (35.2초)]
안녕하세요! 반갑습니다. 무엇을 도와드릴까요?
```

### 3. Gemini 테스트

```bash
curl "http://localhost:8080/api/chat/gemini?question=안녕하세요 반갑습니다"
```

**예상 응답:**
```
[Gemini 응답 (3.5초)]
안녕하세요! 반갑습니다. 무엇을 도와드릴까요?
```

### 브라우저 테스트

다음 URL로 직접 접속해보세요:

- Ollama: `http://localhost:8080/api/chat/ollama?question=스프링이란?`
- Gemini: `http://localhost:8080/api/chat/gemini?question=스프링이란?`

---

## Step 2-5: ChatClient 고급 기능

### 시스템 프롬프트 설정

LLM의 역할과 동작 방식을 정의할 수 있습니다.

```java
public String askWithSystemPrompt(String question) {
    return ollamaChatClient.prompt()
            .system("너는 항공편 검색 도우미야. 친절하게 답변해줘.")  // 시스템 프롬프트
            .user(question)
            .call()
            .content();
}
```

### 대화 기록 유지 (Conversational Memory)

```java
public String chatWithMemory(String userMessage, List<Message> history) {
    return ollamaChatClient.prompt()
            .messages(history)  // 이전 대화 기록
            .user(userMessage)
            .call()
            .content();
}
```

### 스트리밍 응답

```java
public void streamChat(String question) {
    ollamaChatClient.prompt()
            .user(question)
            .stream()  // 스트리밍 모드
            .content()
            .forEach(chunk -> {
                System.out.print(chunk);  // 토큰 단위 출력
            });
}
```

### 구조화된 응답 (Entity)

```java
public FlightSuggestion suggestFlight(String requirement) {
    return ollamaChatClient.prompt()
            .user(requirement)
            .call()
            .entity(FlightSuggestion.class);  // JSON → 자바 객체
}
```

---

## Step 2-6: 디버깅과 모니터링

### 로깅 설정

`application.yml`에 Spring AI 로그 레벨 설정:

```yaml
logging:
  level:
    org.springframework.ai: DEBUG
    org.springframework.ai.chat.client: DEBUG
```

### 로그 예시

```
DEBUG o.s.ai.chat.client.ChatClient    : Sending prompt: 안녕하세요
DEBUG o.s.a.c.m.OllamaChatModel        : Calling Ollama API
DEBUG o.s.a.c.m.OllamaChatModel        : Received response in 35234ms
```

---

## 일반적인 문제 해결

### 문제 1: No qualifying bean of type ChatClient

**에러 메시지:**
```
No qualifying bean of type 'org.springframework.ai.chat.client.ChatClient'
```

**원인:**`ChatClient` 빈이 생성되지 않음

**해결 방법:**
1. `ChatClientConfig.java`에 `@Bean` 메서드가 있는지 확인
2. `ChatClient.Builder`를 주입받는지 확인
3. `@ComponentScan`이 설정 패키지를 포함하는지 확인

### 문제 2: Ollama 연결 실패

**에러 메시지:**
```
Connection refused: ollama.java21.net
```

**해결 방법:**
1. Ollama 서버 실행 중인지 확인
2. `application.yml`의 `base-url` 확인
3. 로컬 Ollama 사용 시: `http://localhost:11434`

### 문제 3: Gemini API 키 오류

**에러 메시지:**
```
401 Unauthorized
```

**해결 방법:**
1. `GEMINI_API_KEY` 환경 변수 설정 확인
2. API 키 유효성 확인
3. 할당량(Quota) 초과 여부 확인

### 문제 4: 응답 시간이 너무 김

**해결 방법:**
1. Ollama → Gemini로 변경 (속도 향상)
2. `max-output-tokens` 줄이기
3. `temperature` 0.0으로 설정 (응답 일관성)

---

## Ollama vs Gemini 실제 성능 비교

| 항목 | Ollama (Qwen 2.5) | Gemini (2.5 Flash) |
|------|------------------|-------------------|
| **평균 응답 시간**| 30~60초 | 3~5초 |
| **한국어 질문**| "스프링이란?" | "스프링이란?" |
| **응답 품질**| 상세하지만 느림 | 간결하고 빠름 |
| **추천 용도**| 개발, 테스트 | 실시간 서비스 |

---

## ChatClient 패턴 이해

### 패턴 1: 빌더 패턴 (Builder Pattern)

```java
ChatClient.builder(chatModel)
    .defaultFunctions(functions)      // 기본 함수 등록
    .defaultAdvisors(advisors)        // 기본 어드바이저
    .build();                         // ChatClient 생성
```

### 패턴 2: 유창한 API (Fluent API)

```java
chatClient
    .prompt()                         // 프롬프트 시작
    .system("시스템 프롬프트")        // 시스템 역할
    .user("사용자 질문")              // 사용자 질문
    .advisors(어드바이저리스트)       // 어드바이저 적용
    .call()                           // 호출
    .content();                       // 결과 반환
```

---

## 체크리스트

- [ ] `ChatClientConfig.java`가 생성됨
- [ ] `SimpleChatService.java`가 생성됨
- [ ] `ChatTestController.java`가 생성됨
- [ ] Ollama로 질문-답변이 동작함
- [ ] Gemini로 질문-답변이 동작함
- [ ] 응답 시간 차이를 확인함 (Ollama vs Gemini)
- [ ] 콘솔에서 Spring AI 로그 확인됨

---

## 다음 단계

**Step 3: Function Calling 기초**에서 LLM이 자바 메서드를 호출하는 방법을 학습합니다.

---

## 참고 자료

- [Spring AI ChatClient 공식 문서](https://docs.spring.io/spring-ai/reference/api/chatclient.html)
- [ChatClient.Builder API](https://docs.spring.io/spring-ai/reference/api/chatclient.html#_the_chatclient_builder)
- [Ollama 모델 목록](https://ollama.com/models)
- [Gemini 모델 비교](https://ai.google.dev/gemini-api/docs/models/gemini)

---

## 심화 학습

### 왜 ChatClient.Builder를 빈으로 등록하나요?

1. **유연성**: 필요할 때마다 새 ChatClient 생성 가능
2. **설정 재사용**: `defaultTools`, `defaultAdvisors`를 한 번만 설정
3. **테스트 용이성**: Mock ChatClient로 쉽게 교체 가능

### @Primary 빈의 역할

```java
@Bean
@Primary  // 기본 빈
public ChatClient.Builder ollamaChatClientBuilder(...) {
    return ChatClient.builder(ollamaChatModel);
}

// 사용 시 ChatClient.Builder만 주입하면 Ollama가 자동 선택됨
public MyService(ChatClient.Builder builder) {  // @Primary 빈 주입
    this.chatClient = builder.build();
}
```

# Step 2: ChatClient 기초와 LLM 연동

## 학습 목표

이 단계를 마치면 다음을 할 수 있습니다:

- Spring AI의 `ChatClient`를 사용하여 LLM과 간단한 질문-답변을 구현할 수 있습니다
- `@Configuration` 클래스에서 모델별 `ChatClient`를 Bean으로 등록할 수 있습니다
- `@Qualifier`와 `ChatClientProvider`를 활용하여 요청마다 Ollama와 Gemini를 선택할 수 있습니다
- `.content()`와 `.entity(Class)`로 LLM 응답을 문자열 또는 자바 객체로 받을 수 있습니다
- Controller에서 사용자의 질문을 받아 LLM의 답변을 반환하는 전체 흐름을 이해할 수 있습니다

---

> **핵심 개념**: ChatClient는 Spring AI에서 LLM과 대화하기 위한 통일된 API입니다. 쉽게 말해, `RestTemplate`이나 `WebClient`로 외부 API를 호출하듯, ChatClient로 AI 모델을 호출한다고 생각하면 됩니다. Ollama든 Gemini든 ChatClient 하나로 동일한 코드로 대화할 수 있다는 것이 핵심입니다.

---

## 전체 흐름

이번 단계에서 구현할 전체 흐름은 다음과 같습니다:

```text
[사용자] → POST /api/search/coordinator  {"message": "...", "model": "ollama"}
    ↓
[@RestController  SearchController.coordinator()]
    ↓
[@Service  FlightSearchCoordinator.coordinate(message, model)]
    ↓
[@Service  SearchCriteriaExtractor.extract(message, () -> chatClientProvider.get(model))]
    ↓  정규식으로 안 되면 model 값에 따라 ollamaChatClient 또는 geminiChatClient 선택
[ChatClient.prompt().system(...).user(message).call().entity(SearchCriteria.class)]
    ↓
[ChatModel (OllamaChatModel 또는 GoogleGenAiChatModel)]
    ↓
[AI 모델이 응답 생성]
    ↓
[SearchResponse(JSON)로 사용자에게 응답 전달]
```

> 이 단계에서는 흐름 가운데 **ChatClient를 만들고 고르고 호출하는 부분**만 다룹니다. `FlightSearchCoordinator`가 검색 조건을 받은 뒤 무엇을 하는지는 Step 9에서 배웁니다.

기존 Spring Boot의 Controller → Service → Repository 구조와 비교하면, Repository 대신 ChatClient가 들어간 형태입니다:

```text
기존: [Controller] → [Service] → [Repository] → [Database]
AI:   [Controller] → [Service] → [ChatClient] → [AI 모델(LLM)]
```

---

## ChatClient란?

**ChatClient**는 Spring AI에서 LLM과 대화하기 위한 **유창한 API**(Fluent API)를 제공하는 인터페이스입니다.

### ChatClient의 필요성

**LLM SDK의 문제점:**

각 LLM 제공자(Ollama, Gemini, OpenAI 등)는 자신만의 SDK를 제공합니다:

```java
// 개념 설명용 의사 코드 (실제 SDK API와 다름)
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
    .advisors(specs)                        // 4. Advisor 적용
    .call()                                 // 5. 호출
    .content();                             // 6. 응답 반환
```

**2. 빌더 패턴 (Builder Pattern)**

```java
ChatClient client = ChatClient.builder(chatModel)
    .defaultOptions(options)        // 기본 옵션
    .defaultAdvisors(advisors)      // 기본 Advisor
    .defaultTools(tools)            // 기본 Tool (Step 3, 이 프로젝트는 사용하지 않음)
    .build();
```

**3. 요청 단위의 독립성**

`prompt()`는 호출할 때마다 기본 설정을 복사한 새 요청 스펙을 만들므로, 한 요청에서 지정한 `system()`, `user()`, `advisors()`는 다른 요청에 영향을 주지 않습니다. 다만 `ChatClient.Builder`는 변경 가능한(mutable) 객체이므로, 기존 설정을 바탕으로 다른 ChatClient를 만들 때는 `mutate()`로 복사본을 만들어 사용합니다:

```java
ChatClient client1 = builder.build();
ChatClient client2 = client1.mutate()       // client1 설정을 복사한 새 Builder
        .defaultSystem("다른 역할")
        .build();

// client2의 설정 변경은 client1에 영향 없음
// 주의: 같은 builder에 defaultSystem()을 호출한 뒤 다시 build()하면
//       이미 만든 client1도 같은 기본 설정을 공유하므로 영향을 받습니다
```

### ChatClient의 특징

| 특징 | 설명 |
|------|------|
| **빌더 패턴** | 메서드 체이닝으로 직관적인 코드 작성 |
| **다중 LLM 지원** | Ollama, Gemini, OpenAI 등 다양한 모델 사용 |
| **Function Calling** | LLM이 자바 메서드를 호출할 수 있게 함 |
| **구조화된 출력** | `entity(Class)`로 응답을 자바 객체로 변환 |

### ChatClient vs ChatModel

```text
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
│  ┌────────────┐    ┌────────────┐       │
│  │  Ollama    │    │  Gemini    │       │
│  │ChatModel   │    │ChatModel   │       │
│  └────────────┘    └────────────┘       │
└─────────────────────────────────────────┘
```

**요약:**
- **ChatClient**: LLM과 대화하는 방법을 정의하는 API
- **ChatModel**: 실제 LLM(Ollama, Gemini 등)과 연결되는 구현체

---

## Step 2-1: ChatClientConfig 설정

### 왜 이렇게 할까요?

Step 1에서 `application.yml`에 Ollama와 Gemini 설정을 추가했습니다. Spring AI는 이 설정을 읽어서 자동으로 `OllamaChatModel`과 `GoogleGenAiChatModel`이라는 Bean을 생성합니다. 하지만 우리가 Service에서 직접 사용할 `ChatClient`는 아직 존재하지 않습니다. 따라서 `@Configuration` 클래스에서 모델마다 `ChatClient`를 하나씩 만들어 Bean으로 등록합니다.

기존 Spring Boot에서 데이터베이스 설정을 `DataSourceConfig` 클래스에서 했던 것과 동일한 패턴입니다. Spring AI에서는 AI 모델 설정을 `ChatClientConfig` 클래스에서 합니다.

> **Spring Boot 복습**: `@Configuration` 클래스는 Spring 컨테이너에 Bean을 수동으로 등록할 때 사용합니다. 외부 라이브러리의 객체는 `@Component`를 붙일 수 없으므로, `@Bean` 메서드로 직접 생성하여 등록합니다. `ChatClient`도 Spring AI 라이브러리의 객체이므로 `@Bean`으로 등록합니다.

### ChatClientConfig.java

실제 프로젝트의 `src/main/java/net/java21/flyschedule/config/ChatClientConfig.java` 전체입니다.

```java
package net.java21.flyschedule.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatClient 설정 (Spring Boot 3.5.11 + Spring AI 1.1.4)
 *
 * <p>Ollama와 Gemini ChatClient를 하나씩 만듭니다.
 * Tool은 여기서 기본값(defaultTools)으로 등록하지 않습니다.
 * Tool이 필요한 곳(ToolCallingSearchService)에서만 요청마다 {@code .tools(...)}로 넘겨,
 * 파라미터 추출·계획·요약처럼 Tool이 필요 없는 LLM 호출이 엉뚱한 Tool을 부르지 않게 합니다.
 */
@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient ollamaChatClient(@Qualifier("ollamaChatModel") ChatModel ollamaChatModel) {
        return ChatClient.builder(ollamaChatModel)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }

    @Bean
    public ChatClient geminiChatClient(@Qualifier("googleGenAiChatModel") ChatModel geminiChatModel) {
        return ChatClient.builder(geminiChatModel)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }
}
```

### 코드 상세 설명

**1. @Bean 메서드 이름이 곧 Bean 이름**

```java
@Bean
public ChatClient ollamaChatClient(...) { ... }   // Bean 이름: "ollamaChatClient"
```

- `@Service`, `@Component`와 동일하게 Spring Bean을 등록합니다
- 차이점: 개발자가 직접 객체를 생성합니다 (외부 라이브러리 객체)
- 같은 `ChatClient` 타입 Bean이 2개이므로, 주입받는 쪽에서 `@Qualifier("ollamaChatClient")`처럼 이름으로 구분합니다

**2. @Qualifier**

```java
@Qualifier("ollamaChatModel")  // 이름으로 특정 Bean 선택
ChatModel ollamaChatModel
```

- 같은 타입의 Bean이 여러 개일 때 사용합니다
- `ChatModel` 타입의 Bean이 2개(Ollama, Gemini)이므로 이름으로 구분해 원하는 Bean을 주입합니다

**3. ChatClient.builder(...).build()**

- `ChatClient.builder(chatModel)`로 빌더를 만들고, 모든 요청에 공통으로 적용할 기본값을 지정한 뒤 `build()`로 ChatClient를 완성합니다
- `defaultAdvisors(new SimpleLoggerAdvisor())`: 모든 요청과 응답을 DEBUG 로그로 남깁니다 (Step 4에서 자세히 다룹니다)
- `ChatClient`는 한 번 만들면 여러 스레드에서 함께 써도 안전하므로, 싱글톤 Bean으로 등록해 재사용합니다

**4. 왜 `ChatClient.Builder`가 아니라 `ChatClient`를 Bean으로 등록할까요?**

`ChatClient.Builder`를 Bean으로 등록하고 쓰는 쪽에서 `build()`하는 방법도 있습니다. 하지만 이 프로젝트는 모델마다 설정이 하나뿐이고, 여러 서비스가 같은 설정을 공유합니다. 완성된 `ChatClient`를 Bean으로 두면 서비스마다 `build()`를 반복하지 않아도 되고, "어떤 기본값이 붙어 있는가"를 `ChatClientConfig` 한 곳에서 확인할 수 있습니다.

**5. 왜 `defaultTools`를 등록하지 않을까요?**

`ChatClient.builder()`에는 `defaultTools(...)`로 Tool을 기본 등록하는 기능도 있습니다(Step 3). 그러나 기본으로 등록하면 이 ChatClient를 쓰는 **모든** LLM 호출이 Tool 목록을 받게 됩니다. 이 프로젝트에는 검색 조건 추출(`SearchCriteriaExtractor`), 작업 계획(`FlightSearchPlanner`), 결과 요약(`ResultSynthesizer`)처럼 Tool이 필요 없는 호출이 많습니다. 이런 호출에 Tool 목록이 붙어 있으면 LLM이 엉뚱하게 Tool을 부를 수 있으므로, Tool은 필요한 곳(`ToolCallingSearchService`)에서만 요청마다 `.tools(...)`로 넘깁니다.

### 코드 설명

| 요소 | 설명 |
|------|------|
| `@Configuration` | Spring 설정 클래스임을 명시 |
| `@Bean` | Spring 컨테이너가 관리하는 빈 등록 (메서드 이름이 빈 이름) |
| `@Qualifier("ollamaChatModel")` | 특정 빈 이름으로 주입 |
| `ChatClient.builder()` | ChatClient를 생성하는 빌더 |
| `defaultAdvisors(...)` | 모든 요청에 적용할 Advisor 등록 |

### Spring AI가 자동으로 생성하는 빈

`application.yml` 설정에 따라 Spring AI가 자동으로 다음 빈들을 생성합니다:

| 빈 이름 | 타입 | 설명 |
|---------|------|------|
| `ollamaChatModel` | `OllamaChatModel` | Ollama LLM 연결 |
| `googleGenAiChatModel` | `GoogleGenAiChatModel` | Gemini LLM 연결 |

여기에 `ChatClientConfig`가 `ollamaChatClient`, `geminiChatClient` 두 개의 `ChatClient` 빈을 더합니다.

---

## Step 2-2: 모델 선택과 첫 번째 LLM 호출

### 왜 이렇게 할까요?

지금까지 데이터베이스에서 데이터를 조회할 때 `UserService`가 `UserRepository`를 주입받아 사용했던 것을 떠올려 보세요. AI 모델과 대화할 때도 동일한 패턴을 사용합니다. 다만 이 프로젝트는 요청마다 사용자가 Ollama와 Gemini 중 하나를 고를 수 있으므로, 모델 이름을 받아 알맞은 `ChatClient`를 돌려주는 작은 컴포넌트 `ChatClientProvider`를 둡니다.

Controller에서 직접 ChatClient를 호출해도 되지만, Service 계층을 분리하면 비즈니스 로직(예: 질문 전처리, 응답 후처리)을 한 곳에서 관리할 수 있어 유지보수가 쉬워집니다. 이것은 Spring Boot에서 배운 "관심사의 분리(Separation of Concerns)" 원칙과 동일합니다.

> **Spring Boot 복습**: `@Service`와 `@Component`는 Spring이 자동으로 Bean으로 등록하는 클래스에 붙입니다. 생성자 주입(Constructor Injection)을 사용하면 `final` 필드로 불변성을 보장할 수 있습니다. Spring 4.3부터는 생성자가 하나뿐이면 `@Autowired`를 생략할 수 있습니다.

### ChatClientProvider: 모델 이름으로 ChatClient 고르기

`src/main/java/net/java21/flyschedule/config/ChatClientProvider.java` (주석 일부 생략)

```java
@Component
public class ChatClientProvider {

    public static final String OLLAMA = "ollama";
    public static final String GEMINI = "gemini";

    /** application.yml에서 GOOGLE_GENAI_API_KEY가 없을 때 쓰는 기본값 */
    static final String GEMINI_KEY_NOT_SET = "not-set";

    private final ChatClient ollamaChatClient;
    private final ChatClient geminiChatClient;
    private final boolean geminiConfigured;

    public ChatClientProvider(@Qualifier("ollamaChatClient") ChatClient ollamaChatClient,
                              @Qualifier("geminiChatClient") ChatClient geminiChatClient,
                              @Value("${spring.ai.google.genai.api-key:" + GEMINI_KEY_NOT_SET + "}") String geminiApiKey) {
        this.ollamaChatClient = ollamaChatClient;
        this.geminiChatClient = geminiChatClient;
        this.geminiConfigured = !geminiApiKey.isBlank() && !GEMINI_KEY_NOT_SET.equals(geminiApiKey);
    }

    public ChatClient get(String model) {
        if (!GEMINI.equalsIgnoreCase(model)) {
            return ollamaChatClient;               // "gemini"가 아니면(생략 포함) Ollama
        }
        if (!geminiConfigured) {
            throw new IllegalArgumentException(
                    "Gemini API 키가 설정되지 않았습니다. 환경 변수 GOOGLE_GENAI_API_KEY를 설정하거나 Ollama를 선택하세요.");
        }
        return geminiChatClient;
    }

    public static String describeError(Exception e) {
        // ExternalApiException(공공데이터 포털 오류) → 메시지를 그대로 반환
        // 그 밖에는 원인(cause)을 따라가며 연결 실패 → "LLM 서버에 연결할 수 없습니다. ..."
        //                     "API key not valid" → "Gemini API 키가 올바르지 않습니다. ..."
        // ... 생략
    }

    public static String normalize(String model) {
        return GEMINI.equalsIgnoreCase(model) ? GEMINI : OLLAMA;
    }
}
```

| 메서드 | 역할 |
|--------|------|
| `get(model)` | `"gemini"`면 Gemini, 그 밖(생략 포함)에는 Ollama `ChatClient`를 돌려줍니다. Gemini 키가 `not-set`이면 안내 문장을 담은 `IllegalArgumentException`을 던집니다 |
| `describeError(e)` | 예외를 사용자에게 보여 줄 문장으로 바꿉니다. 공공데이터 포털 오류(`ExternalApiException`, Step 6)는 메시지를 그대로, LLM 오류는 연결 실패·잘못된 키를 구분해 안내합니다 |
| `normalize(model)` | 실행 기록에 남길 모델 이름을 `"ollama"`/`"gemini"`로 맞춥니다 |

**왜 이렇게 설계했을까요?** 서비스마다 `"gemini".equals(model) ? ... : ...`를 반복하면, 키 검사나 오류 문구가 서비스마다 달라지기 쉽습니다. 모델 선택 규칙을 한 클래스에 모아 두면 세 가지 검색 패턴(Step 9~11)이 모두 같은 방식으로 모델을 고르고, 같은 문장으로 오류를 안내합니다.

### 첫 번째 LLM 호출: SearchCriteriaExtractor

이 프로젝트에서 가장 단순한 LLM 호출은 `SearchCriteriaExtractor`에 있습니다. 사용자 문장에서 출발 공항, 도착 공항, 날짜 같은 검색 조건을 뽑는 클래스로, 정규식으로 먼저 시도하고 실패할 때만 LLM을 부릅니다.

`src/main/java/net/java21/flyschedule/service/ai/SearchCriteriaExtractor.java` (일부)

```java
public Extraction extract(String message, Supplier<ChatClient> chatClient) {
    Optional<SearchCriteria> parsed = parseWithRegex(message);
    if (parsed.isPresent()) {
        return new Extraction(parsed.get(), false);    // 정규식으로 충분하면 LLM을 부르지 않음
    }

    SearchCriteria criteria = chatClient.get().prompt()
            .system("""
                    항공편 검색 문장에서 검색 조건을 추출합니다.
                    오늘 날짜는 %s입니다. "내일", "다음 주 금요일" 같은 표현은 이 날짜를 기준으로 YYYY-MM-DD로 바꿉니다.
                    공항은 공항 코드가 아니라 한글 이름(광주, 김포, 제주 등)으로 적습니다.
                    문장에 없는 조건은 null로 둡니다.
                    """.formatted(LocalDate.now()))
            .user(message)
            .call()
            .entity(SearchCriteria.class);              // JSON 응답 → 자바 record
    // ... 생략 (공항을 찾지 못하면 IllegalArgumentException)
    return new Extraction(criteria, true);
}
```

- `prompt()` → `system(...)` → `user(...)` → `call()`까지는 모든 LLM 호출이 같습니다
- 마지막을 `.content()`로 끝내면 문자열을, `.entity(SearchCriteria.class)`로 끝내면 자바 객체를 받습니다 (Structured Output)
- `ChatClient`를 직접 받지 않고 `Supplier<ChatClient>`로 받는 이유: 정규식으로 끝나는 요청에서는 `chatClientProvider.get(model)`을 부르지 않으므로, Gemini 키가 없어도 Gemini를 선택한 요청이 정규식 경로로는 성공합니다

`SearchCriteria`는 다음과 같은 record이며, 필드마다 붙인 `@JsonPropertyDescription`이 LLM에게 전달되는 JSON 스키마의 설명이 됩니다.

```java
public record SearchCriteria(
        @JsonPropertyDescription("출발 공항 한글 이름 (예: 광주, 김포, 제주)")
        String departure,
        @JsonPropertyDescription("도착 공항 한글 이름 (예: 제주, 김포, 김해)")
        String arrival,
        @JsonPropertyDescription("출발 날짜, YYYY-MM-DD 형식")
        String date,
        // ... 생략 (afterTime, beforeTime, minPrice, maxPrice)
) { ... }
```

### ChatClient API 상세 분석

**메서드 체이닝 (Method Chaining)**

```java
chatClient.prompt().user("질문").call().content();
```

각 메서드가 무엇을 하는지 하나씩 뜯어보겠습니다:

```java
// 1단계: prompt() - 프롬프트 빌더 시작
ChatClient.ChatClientRequestSpec requestSpec = chatClient.prompt();
// → 요청(system, user, advisors 등)을 구성하는 빌더 객체 반환

// 2단계: user() - 사용자 질문 설정
requestSpec = requestSpec.user("스프링이 뭐야?");
// → 질문 내용을 설정, 연속 체이닝 가능

// 3단계: call() - LLM 호출
ChatClient.CallResponseSpec responseSpec = requestSpec.call();
// → 동기(blocking) 호출 방식의 응답 스펙 반환

// 4단계: content() - 응답 내용 추출
String answer = responseSpec.content();
// → 응답에서 텍스트 내용만 추출 (전체 응답은 chatResponse()로 조회)

// 전체: 한 줄로 표현
String oneLine = chatClient.prompt().user("스프링이 뭐야?").call().content();
```

**왜 이렇게 설계했을까요?**

```text
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

장점 3: 원하는 형태로 결과 받기
String content = chatClient.prompt()...content();
SearchCriteria criteria = chatClient.prompt()...entity(SearchCriteria.class);
→ 문자열 또는 자바 객체로 바로 변환 (JSON 변환은 실행 시점에 수행)
```

### ChatClient API 상세

```text
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
| `prompt()` | `ChatClient.ChatClientRequestSpec` | 프롬프트 빌더 시작 |
| `user(String)` | `ChatClient.ChatClientRequestSpec` | 사용자 질문 설정 |
| `call()` | `ChatClient.CallResponseSpec` | LLM 호출 (동기) |
| `content()` | `String` | 응답 내용 반환 |
| `entity(Class)` | 지정한 타입 | 응답 JSON을 자바 객체로 변환 |
| `chatResponse()` | `ChatResponse` | 메타데이터를 포함한 전체 응답 반환 |

---

## Step 2-3: 실제 컨트롤러 구현

### 왜 이렇게 할까요?

Service 계층을 만들었지만, 사용자가 직접 자바 코드를 실행할 수는 없습니다. HTTP 요청을 받아 Service를 호출하고 결과를 반환하는 Controller가 필요합니다. 이것은 여러분이 이미 알고 있는 `@RestController`의 역할과 완전히 동일합니다.

테스트할 때는 `model` 값을 바꿔 가며 응답 시간을 함께 확인해 보세요. Ollama와 Gemini의 속도 차이를 직접 체감해 보는 것이 학습에 도움이 됩니다.

> **Spring Boot 복습**: `@RestController`는 `@Controller` + `@ResponseBody`의 조합으로, 메서드 반환값을 JSON으로 자동 변환합니다. `@PostMapping`은 HTTP POST 요청을 매핑하고, `@RequestBody`는 요청 본문 JSON을 자바 객체로 바꿔 줍니다.

### SearchController

이 프로젝트는 같은 문장을 세 가지 패턴으로 처리해 비교할 수 있도록 엔드포인트 세 개를 둡니다. 각 패턴은 Step 9~11에서 차례로 배우고, 이 단계에서는 요청이 어떻게 들어와 서비스로 넘어가는지만 봅니다.

`src/main/java/net/java21/flyschedule/controller/SearchController.java` (주석 생략)

```java
@Slf4j
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final FlightSearchCoordinator coordinator;
    private final ToolCallingSearchService toolCallingService;
    private final FlightSearchOrchestrator orchestrator;

    @PostMapping("/coordinator")
    public SearchResponse coordinator(@RequestBody SearchRequest request) {
        return handle(FlightSearchCoordinator.PATTERN, request, coordinator::coordinate);
    }

    @PostMapping("/tool-calling")
    public SearchResponse toolCalling(@RequestBody SearchRequest request) {
        return handle(ToolCallingSearchService.PATTERN, request, toolCallingService::search);
    }

    @PostMapping("/orchestrator")
    public SearchResponse orchestrator(@RequestBody SearchRequest request) {
        return handle(FlightSearchOrchestrator.PATTERN, request, orchestrator::orchestrate);
    }

    private SearchResponse handle(String pattern, SearchRequest request,
                                  BiFunction<String, String, SearchResponse> searcher) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            return SearchResponse.fail(pattern, "검색할 문장을 입력해 주세요.", new ExecutionTrace());
        }
        log.info("[{}] 검색 요청 (model={}): {}", pattern, request.model(), request.message());
        return searcher.apply(request.message().trim(), request.model());
    }
}
```

요청 본문은 `SearchRequest` record로 받습니다. `model`을 생략하면 `null`이 넘어가고, `ChatClientProvider.get()`이 Ollama를 고릅니다.

```java
public record SearchRequest(String message, String model) {
}
```

| 엔드포인트 | 패턴 | 다음 단계를 누가 정하는가 | 학습 단계 |
|------------|------|---------------------------|-----------|
| `POST /api/search/coordinator` | Coordinator | 코드 | Step 9 |
| `POST /api/search/tool-calling` | Tool Calling 에이전트 | LLM | Step 10 |
| `POST /api/search/orchestrator` | Orchestrator-Workers | LLM(계획·종합) + 코드(실행) | Step 11 |

---

## Step 2-4: 실행 및 테스트

### 1. 애플리케이션 실행

```bash
./mvnw spring-boot:run
```

### 2. Ollama로 테스트

공항 이름이 두 개 들어 있으면 정규식으로 처리되어 LLM을 부르지 않습니다. LLM 호출을 직접 보려면 정규식이 인식하지 못하는 문장(예: 영어)을 보내 보세요.

```bash
# 정규식으로 처리 (LLM 호출 없음)
curl -X POST http://localhost:8080/api/search/coordinator \
  -H "Content-Type: application/json" \
  -d '{"message": "내일 광주에서 제주로 가는 항공편 알려줘"}'

# 공항 이름을 찾지 못해 LLM(.entity)으로 처리
curl -X POST http://localhost:8080/api/search/coordinator \
  -H "Content-Type: application/json" \
  -d '{"message": "flights from Gwangju to Jeju tomorrow", "model": "ollama"}' \
  -w "\n응답 시간: %{time_total}초\n"
```

### 3. Gemini로 테스트

```bash
curl -X POST http://localhost:8080/api/search/coordinator \
  -H "Content-Type: application/json" \
  -d '{"message": "flights from Gwangju to Jeju tomorrow", "model": "gemini"}' \
  -w "\n응답 시간: %{time_total}초\n"
```

`GOOGLE_GENAI_API_KEY`를 설정하지 않았다면 `success: false`와 함께 "Gemini API 키가 설정되지 않았습니다..."라는 안내가 돌아옵니다.

**예상 응답 (형태, 일부 생략):**
```json
{
  "pattern": "coordinator",
  "success": true,
  "message": "광주 → 제주 · 2026-10-16: 12편, 최저가 진에어 LJ0571 07:10 출발 45,900원",
  "results": [
    {
      "label": "광주 → 제주 · 2026-10-16",
      "flightsByAirline": { "아시아나항공": [ ... ], "진에어": [ ... ] },
      "totalCount": 12,
      "cheapest": { "flightId": "LJ0571", "airline": "진에어", "departureTime": "07:10", "price": 45900, ... }
    }
  ],
  "trace": [
    { "order": 1, "kind": "LLM", "actor": "SearchCriteriaExtractor", "title": "LLM으로 검색 조건 추출", ... },
    { "order": 2, "kind": "CODE", "actor": "DateParserAgent", "title": "날짜 변환", ... }
  ],
  "elapsedMs": 4210
}
```

`trace`의 첫 단계 `kind`가 `CODE`(정규식)인지 `LLM`인지 비교해 보세요. 항공편 번호와 가격은 실행하는 날의 실제 운항 정보에 따라 달라집니다.

### 브라우저 테스트

이 엔드포인트는 POST 요청이므로 브라우저 주소창으로는 호출할 수 없습니다. 대신 `http://localhost:8080`에 접속하면 문장과 모델을 고르고 세 패턴을 실행해 결과와 실행 기록(타임라인)을 비교하는 화면을 쓸 수 있습니다.

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
            .content()  // Flux<String>
            .doOnNext(chunk -> System.out.print(chunk))  // 토큰 단위 출력
            .blockLast();  // 예제에서는 스트림이 끝날 때까지 대기
}
```

### 구조화된 응답 (Entity)

```java
public SearchCriteria extractCriteria(String message) {
    return ollamaChatClient.prompt()
            .user(message)
            .call()
            .entity(SearchCriteria.class);  // JSON → 자바 객체 (net.java21.flyschedule.dto.search)
}
```

실제 프로젝트에서는 `SearchCriteriaExtractor`(Step 2-2)와 `FlightSearchPlanner`(`.entity(SearchPlan.class)`, Step 11)가 이 기능을 사용합니다.

---

## Step 2-6: 디버깅과 모니터링

### 로깅 설정

`application.yml`에 Spring AI 로그 레벨 설정:

```yaml
logging:
  level:
    org.springframework.ai: DEBUG
```

로그 레벨만 올린다고 프롬프트와 응답 내용이 출력되지는 않습니다. 요청/응답 내용은 ChatClient에 등록한 `SimpleLoggerAdvisor`가 DEBUG 레벨로 남깁니다. 이 프로젝트는 `ChatClientConfig`에서 이미 등록해 두었으므로, `org.springframework.ai.chat.client.advisor` 로거를 DEBUG로 올리면 바로 볼 수 있습니다(Step 4에서 학습).

### 로그 예시

`SimpleLoggerAdvisor`를 등록하면 DEBUG 레벨로 다음과 같은 로그가 출력됩니다 (줄여서 표시):

```text
DEBUG o.s.a.c.c.advisor.SimpleLoggerAdvisor - request: ChatClientRequest[prompt=Prompt{messages=[UserMessage{content='안녕하세요', ...}], ...}, context={}]
DEBUG o.s.a.c.c.advisor.SimpleLoggerAdvisor - response: { "result" : { "output" : { "text" : "안녕하세요! 무엇을 도와드릴까요?", ... } } }
```

---

## Ollama vs Gemini 실제 성능 비교

| 항목 | Ollama (Qwen 2.5) | Gemini (2.5 Flash) |
|------|------------------|-------------------|
| **평균 응답 시간** | 30~60초 | 3~5초 |
| **한국어 질문** | "스프링이란?" | "스프링이란?" |
| **응답 품질** | 상세하지만 느림 | 간결하고 빠름 |
| **추천 용도** | 개발, 테스트 | 실시간 서비스 |

---

## ChatClient 패턴 이해

### 패턴 1: 빌더 패턴 (Builder Pattern)

```java
ChatClient.builder(chatModel)
    .defaultAdvisors(new SimpleLoggerAdvisor())  // 모든 요청에 적용할 기본 Advisor
    .build();                                    // ChatClient 생성
// defaultTools(...)로 Tool을 기본 등록할 수도 있지만, 이 프로젝트는 요청마다 .tools(...)를 씁니다 (Step 3)
```

### 패턴 2: 유창한 API (Fluent API)

```java
chatClient
    .prompt()                         // 프롬프트 시작
    .system("시스템 프롬프트")        // 시스템 역할
    .user("사용자 질문")              // 사용자 질문
    .advisors(advisors)               // Advisor 적용
    .call()                           // 호출
    .content();                       // 결과 반환
```

---

## 일반적인 문제 해결

### 문제 1: No qualifying bean of type ChatClient

**에러 메시지:**
```text
No qualifying bean of type 'org.springframework.ai.chat.client.ChatClient'
```

**원인:** `ChatClient` 빈이 생성되지 않았거나, 같은 타입 빈이 2개인데 이름을 지정하지 않음

**해결 방법:**
1. `ChatClientConfig.java`에 `@Bean` 메서드가 있는지 확인
2. 주입받는 쪽에 `@Qualifier("ollamaChatClient")`처럼 빈 이름을 지정했는지 확인 (`expected single matching bean but found 2` 오류라면 이것이 원인)
3. `@ComponentScan`이 설정 패키지를 포함하는지 확인

### 문제 2: Ollama 연결 실패

**에러 메시지:**
```text
Connection refused: ollama.java21.net
```

화면과 API 응답에는 `ChatClientProvider.describeError()`가 바꾼 문장 "LLM 서버에 연결할 수 없습니다. Ollama가 실행 중인지(spring.ai.ollama.base-url) 확인하거나 다른 모델을 선택하세요."가 `message`로 담깁니다.

**해결 방법:**
1. Ollama 서버 실행 중인지 확인
2. `application.yml`의 `base-url` 확인
3. 로컬 Ollama 사용 시: `http://localhost:11434`

### 문제 3: Gemini API 키 오류

**에러 메시지 (예시):**
```text
400 Bad Request: API key not valid. Please pass a valid API key.
```

응답 `message`에는 "Gemini API 키가 올바르지 않습니다. GOOGLE_GENAI_API_KEY를 확인하세요."가 담깁니다. 키를 아예 설정하지 않았다면 LLM을 호출하기 전에 `ChatClientProvider.get()`이 "Gemini API 키가 설정되지 않았습니다..."라고 안내합니다.

**해결 방법:**
1. `GOOGLE_GENAI_API_KEY` 환경 변수 설정 확인
2. API 키 유효성 확인
3. 할당량(Quota) 초과 여부 확인

### 문제 4: 응답 시간이 너무 김

**해결 방법:**
1. Ollama → Gemini로 변경 (속도 향상)
2. `max-tokens`(Ollama) 또는 `max-output-tokens`(Gemini) 줄이기
3. 더 작은 모델 사용 또는 `num-ctx` 조정 (Ollama)

---

## 실습 과제

1. **(기본)** `curl`로 `POST /api/search/coordinator`에 한국어 문장과 영어 문장을 각각 보내고, 응답 `trace`의 첫 단계 `kind`가 `CODE`와 `LLM` 중 무엇인지, `elapsedMs`가 얼마나 차이 나는지 비교해 보세요.
2. **(기본)** `GOOGLE_GENAI_API_KEY` 없이 `"model": "gemini"`로 위 두 문장을 보내 보세요. 한국어 문장은 성공하고 영어 문장은 안내 메시지가 나오는 이유를 `SearchCriteriaExtractor.extract()`가 `Supplier<ChatClient>`를 받는다는 점과 연결해 설명하세요.
3. **(도전)** `ChatClientProviderTest`를 참고해, 대문자 `"GEMINI"`나 알 수 없는 값(`"gpt"`)을 넘겼을 때 `get()`과 `normalize()`가 어떻게 동작하는지 확인하는 테스트를 추가하세요.
4. **(도전)** `SearchCriteria`의 `date` 필드 설명을 바꾸거나 지워 보고, 영어 문장을 보냈을 때 LLM이 돌려주는 날짜 형식이 어떻게 달라지는지 관찰하세요. `@JsonPropertyDescription`이 Structured Output에서 하는 역할을 정리하세요.

---

## 요약

이번 단계에서는 Spring AI의 ChatClient를 사용하여 LLM과 대화하는 전체 흐름을 구현했습니다:

1. **ChatClientConfig**: `@Configuration` 클래스에서 `ollamaChatClient`, `geminiChatClient` 두 `ChatClient`를 Bean으로 등록했습니다. Tool은 기본 등록하지 않습니다
2. **ChatClientProvider**: 요청의 `model` 값으로 ChatClient를 고르고, Gemini 키가 없거나 LLM 서버에 연결할 수 없을 때 안내 문장을 만듭니다
3. **SearchCriteriaExtractor**: `prompt().system().user().call().entity(SearchCriteria.class)`로 LLM 응답을 자바 record로 받았습니다
4. **SearchController**: `POST /api/search/{coordinator|tool-calling|orchestrator}`로 요청을 받아 각 패턴의 서비스에 넘깁니다
5. **핵심 인사이트**: Ollama와 Gemini 모두 동일한 ChatClient API로 사용할 수 있어, 모델을 교체해도 코드 변경이 거의 없습니다

다음 단계에서는 LLM이 자바 메서드를 직접 호출할 수 있는 "Function Calling" 기능을 배우겠습니다.

---

## 체크리스트

- [ ] `ChatClientConfig.java`의 두 `ChatClient` 빈과 `defaultTools`를 쓰지 않는 이유를 설명할 수 있음
- [ ] `ChatClientProvider.get()`이 모델을 고르는 규칙을 이해함
- [ ] `.content()`와 `.entity(Class)`의 차이를 이해함
- [ ] `POST /api/search/coordinator`를 curl로 호출해 봄
- [ ] Ollama로 자연어 검색이 동작함
- [ ] Gemini로 자연어 검색이 동작함 (키를 설정한 경우)
- [ ] 응답 시간 차이를 확인함 (Ollama vs Gemini)
- [ ] 콘솔에서 Spring AI 로그 확인됨

---

## 다음 단계

[Step 3: Function Calling 기초](step-3-function-calling.md)에서 LLM이 자바 메서드를 호출하는 방법을 학습합니다.

---

## 참고 자료

- [Spring AI ChatClient 공식 문서](https://docs.spring.io/spring-ai/reference/api/chatclient.html)
- [ChatClient 생성하기](https://docs.spring.io/spring-ai/reference/api/chatclient.html#_creating_a_chatclient)
- [Ollama 모델 목록](https://ollama.com/models)
- [Gemini 모델 비교](https://ai.google.dev/gemini-api/docs/models)

---

## 심화 학습

### ChatClient.Builder를 빈으로 등록하는 방법과 비교

`ChatClient.Builder`를 Bean으로 등록하고 서비스마다 `build()`하는 방법도 있습니다.

```java
@Bean
public ChatClient.Builder ollamaChatClientBuilder(@Qualifier("ollamaChatModel") ChatModel model) {
    return ChatClient.builder(model);
}

// 서비스마다 필요한 기본값을 더해 직접 만든다
public MyService(@Qualifier("ollamaChatClientBuilder") ChatClient.Builder builder) {
    this.chatClient = builder.defaultSystem("서비스별 역할").build();
}
```

| 방식 | 장점 | 주의할 점 |
|------|------|-----------|
| `ChatClient` 빈 (이 프로젝트) | 설정이 한 곳에 모이고, 서비스는 바로 사용 | 서비스별 기본값이 필요하면 `mutate()`로 복사본을 만듦 |
| `ChatClient.Builder` 빈 | 서비스마다 다른 기본값을 붙이기 쉬움 | Builder는 변경 가능한 객체라, 같은 Builder를 여러 곳에서 고치면 설정이 섞일 수 있음 |

이 프로젝트는 서비스마다 다른 점이 시스템 프롬프트와 Tool 정도이고, 둘 다 요청마다 `.system(...)`, `.tools(...)`로 넘기므로 `ChatClient` 빈 방식을 택했습니다.

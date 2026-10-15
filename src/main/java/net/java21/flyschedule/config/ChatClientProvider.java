package net.java21.flyschedule.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 요청에서 고른 모델 이름("ollama", "gemini")으로 ChatClient를 돌려줍니다.
 */
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

    /**
     * @throws IllegalArgumentException Gemini를 골랐는데 API 키가 설정되지 않은 경우
     */
    public ChatClient get(String model) {
        if (!GEMINI.equalsIgnoreCase(model)) {
            return ollamaChatClient;
        }
        if (!geminiConfigured) {
            throw new IllegalArgumentException(
                    "Gemini API 키가 설정되지 않았습니다. 환경 변수 GOOGLE_GENAI_API_KEY를 설정하거나 Ollama를 선택하세요.");
        }
        return geminiChatClient;
    }

    /**
     * LLM 호출 예외를 사용자에게 보여 줄 문장으로 바꿉니다.
     */
    public static String describeError(Exception e) {
        if (e instanceof net.java21.flyschedule.service.api.ExternalApiException) {
            return e.getMessage(); // 공공데이터 포털 오류는 LLM 오류와 구분해 그대로 보여 준다
        }
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof org.springframework.web.client.ResourceAccessException
                    || cause instanceof java.net.ConnectException) {
                return "LLM 서버에 연결할 수 없습니다. Ollama가 실행 중인지(spring.ai.ollama.base-url) 확인하거나 다른 모델을 선택하세요.";
            }
            String message = cause.getMessage();
            if (message != null && message.contains("API key not valid")) {
                return "Gemini API 키가 올바르지 않습니다. GOOGLE_GENAI_API_KEY를 확인하세요.";
            }
            cause = cause.getCause();
        }
        return "LLM 호출 중 오류가 발생했습니다: " + e.getMessage();
    }

    public static String normalize(String model) {
        return GEMINI.equalsIgnoreCase(model) ? GEMINI : OLLAMA;
    }
}

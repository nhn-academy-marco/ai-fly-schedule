package net.java21.flyschedule.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DisplayName("ChatClientProvider 테스트")
class ChatClientProviderTest {

    private final ChatClient ollama = mock(ChatClient.class);
    private final ChatClient gemini = mock(ChatClient.class);

    @Test
    @DisplayName("'gemini'면 Gemini, 그 밖의 값(null 포함)은 Ollama를 돌려준다")
    void selectsByModelName() {
        ChatClientProvider provider = new ChatClientProvider(ollama, gemini, "AIza-test-key");

        assertThat(provider.get("gemini")).isSameAs(gemini);
        assertThat(provider.get("GEMINI")).isSameAs(gemini);
        assertThat(provider.get("ollama")).isSameAs(ollama);
        assertThat(provider.get(null)).isSameAs(ollama);
    }

    @Test
    @DisplayName("Gemini 키가 없으면 Gemini를 고를 때 안내 메시지와 함께 실패하고, Ollama는 그대로 쓸 수 있다")
    void geminiWithoutKey() {
        ChatClientProvider provider = new ChatClientProvider(ollama, gemini, ChatClientProvider.GEMINI_KEY_NOT_SET);

        assertThatThrownBy(() -> provider.get("gemini"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GOOGLE_GENAI_API_KEY");
        assertThat(provider.get("ollama")).isSameAs(ollama);
    }

    @Test
    @DisplayName("연결 실패와 잘못된 키는 원인을 알 수 있는 문장으로 바꾼다")
    void describeError() {
        Exception connection = new org.springframework.web.client.ResourceAccessException(
                "I/O error", new java.net.ConnectException());
        Exception badKey = new RuntimeException("Failed to generate content",
                new IllegalStateException("400 . API key not valid. Please pass a valid API key."));

        assertThat(ChatClientProvider.describeError(connection)).contains("LLM 서버에 연결할 수 없습니다");
        assertThat(ChatClientProvider.describeError(badKey)).contains("GOOGLE_GENAI_API_KEY");
        assertThat(ChatClientProvider.describeError(new IllegalStateException("기타"))).endsWith("기타");
        // 공공데이터 포털 연결 실패는 "LLM 서버" 문제로 잘못 안내하지 않는다
        Exception apiFailure = new net.java21.flyschedule.service.api.ExternalApiException(
                "공공데이터 포털 API를 호출하지 못했습니다.", connection);
        assertThat(ChatClientProvider.describeError(apiFailure)).startsWith("공공데이터 포털");
    }

    @Test
    @DisplayName("normalize()는 응답·기록에 쓸 모델 이름을 'ollama' 또는 'gemini'로 맞춘다")
    void normalize() {
        assertThat(ChatClientProvider.normalize("Gemini")).isEqualTo("gemini");
        assertThat(ChatClientProvider.normalize(null)).isEqualTo("ollama");
        assertThat(ChatClientProvider.normalize("gpt")).isEqualTo("ollama");
    }
}

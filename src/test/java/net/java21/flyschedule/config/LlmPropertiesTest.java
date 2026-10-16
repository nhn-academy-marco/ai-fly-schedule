package net.java21.flyschedule.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * application.yml의 LLM 설정이 의도대로인지 확인합니다. (값을 바꾸면 이 테스트와 교재 1장을 함께 고칩니다)
 */
@DisplayName("LLM 설정(application.yml) 테스트")
class LlmPropertiesTest {

    private static Properties yaml() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application.yml"));
        return factory.getObject();
    }

    @Test
    @DisplayName("Gemini: 무료 등급 3.5 Flash-Lite, thinking 최소, temperature는 기본값(1.0)을 쓰도록 지정하지 않는다")
    void geminiOptions() {
        Properties props = yaml();

        assertThat(props.getProperty("spring.ai.google.genai.chat.options.model")).isEqualTo("gemini-3.5-flash-lite");
        assertThat(props.getProperty("spring.ai.google.genai.chat.options.thinking-level")).isEqualTo("minimal");
        assertThat(props.getProperty("spring.ai.google.genai.chat.options.temperature")).isNull();
        assertThat(props.getProperty("spring.ai.google.genai.api-key")).isEqualTo("${GOOGLE_GENAI_API_KEY:not-set}");
    }

    @Test
    @DisplayName("Ollama: temperature 0, 출력 토큰 상한, num-thread는 Ollama가 정하도록 지정하지 않는다")
    void ollamaOptions() {
        Properties props = yaml();

        assertThat(props.getProperty("spring.ai.ollama.chat.options.temperature")).isEqualTo("0.0");
        assertThat(props.getProperty("spring.ai.ollama.chat.options.max-tokens")).isEqualTo("512");
        assertThat(props.getProperty("spring.ai.ollama.chat.options.num-thread")).isNull();
    }

    @Test
    @DisplayName("API 키는 저장소에 넣지 않고 환경 변수에서 읽는다")
    void keysFromEnvironment() {
        assertThat(yaml().getProperty("data-go-kr.api.service-key")).isEqualTo("${DATA_GO_KR_SERVICE_KEY:}");
    }
}

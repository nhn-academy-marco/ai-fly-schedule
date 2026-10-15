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

package com.nhnacademy.flyschedule.config;

import com.nhnacademy.flyschedule.mcp.AirlineInfoTool;
import com.nhnacademy.flyschedule.mcp.AirportInfoTool;
import com.nhnacademy.flyschedule.mcp.FlightSearchTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * ChatClient Configuration for Spring AI
 *
 * Spring Boot 3.5.11 + Spring AI 1.1.2 호환성 확보
 * MCP Tools를 ChatClient에 명시적으로 등록
 * Ollama와 Gemini(Google GenAI)를 선택적으로 사용 가능
 */
@Configuration
public class ChatClientConfig {

    /**
     * Ollama ChatClient.Builder (기본)
     * Spring AI가 자동으로 생성한 Ollama ChatModel 사용
     */
    @Bean
    @Primary
    public ChatClient.Builder ollamaChatClientBuilder(
            @Qualifier("ollamaChatModel") ChatModel ollamaChatModel,
            FlightSearchTool flightSearchTool,
            AirportInfoTool airportInfoTool,
            AirlineInfoTool airlineInfoTool) {

        return ChatClient.builder(ollamaChatModel)
                .defaultTools(flightSearchTool, airportInfoTool, airlineInfoTool);
    }

    /**
     * Gemini ChatClient.Builder
     * Spring AI가 자동으로 생성한 Gemini ChatModel 사용
     */
    @Bean
    public ChatClient.Builder geminiChatClientBuilder(
            @Qualifier("googleGenAiChatModel") ChatModel geminiChatModel,
            FlightSearchTool flightSearchTool,
            AirportInfoTool airportInfoTool,
            AirlineInfoTool airlineInfoTool) {

        return ChatClient.builder(geminiChatModel)
                .defaultTools(flightSearchTool, airportInfoTool, airlineInfoTool);
    }
}

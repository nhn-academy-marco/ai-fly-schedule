package com.nhnacademy.flyschedule.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP 설정
 *
 * Spring AI MCP 서버 설정을 정의합니다.
 *
 * <p>Spring AI 공식 문서:
 * https://docs.spring.io/spring-ai/reference/guides/getting-started-mcp.html
 */
@Configuration
public class McpConfig {

    /**
     * MCP 서버 프로토콜 설정
     *
     * <p>지원하는 프로토콜:
     * - STREAMABLE: Streamable HTTP (권장)
     * - STDIO: Standard Input/Output
     * - SSE: Server-Sent Events
     *
     * <p>application.yml 설정 예시:
     * <pre>
     * spring:
     *   ai:
     *     mcp:
     *       server:
     *         protocol: STREAMABLE
     * </pre>
     */
    @Bean
    public McpServerProperties mcpServerProperties() {
        return new McpServerProperties();
    }

    /**
     * MCP 서버 프로퍼티
     */
    public static class McpServerProperties {
        private String protocol = "STREAMABLE";
        private boolean stdioEnabled = false;
        private boolean sseEnabled = false;

        public String getProtocol() {
            return protocol;
        }

        public void setProtocol(String protocol) {
            this.protocol = protocol;
        }

        public boolean isStdioEnabled() {
            return stdioEnabled;
        }

        public void setStdioEnabled(boolean stdioEnabled) {
            this.stdioEnabled = stdioEnabled;
        }

        public boolean isSseEnabled() {
            return sseEnabled;
        }

        public void setSseEnabled(boolean sseEnabled) {
            this.sseEnabled = sseEnabled;
        }
    }
}

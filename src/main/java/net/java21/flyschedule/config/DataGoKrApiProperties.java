package net.java21.flyschedule.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 공공데이터 포털 API 설정 프로퍼티
 *
 * application.yml의 설정 값을 매핑합니다.
 */
@Data
@Component
@ConfigurationProperties(prefix = "data-go-kr.api")
public class DataGoKrApiProperties {

    /**
     * API 기본 URL
     */
    private String url;

    /**
     * 인증키
     */
    private String serviceKey;
}

package net.java21.flyschedule.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 공공데이터 포털 API 응답 래퍼
 *
 * API 응답의 전체 구조를 매핑합니다.
 */
@Data
public class ApiResponseWrapper {

    @JsonProperty("response")
    private Response response;

    @Data
    public static class Response {
        @JsonProperty("header")
        private Header header;

        @JsonProperty("body")
        private Body body;
    }

    @Data
    public static class Header {
        @JsonProperty("resultCode")
        private String resultCode;

        @JsonProperty("resultMsg")
        private String resultMessage;
    }

    @Data
    public static class Body {
        @JsonProperty("items")
        private Items items;

        @JsonProperty("numOfRows")
        private Integer numOfRows;

        @JsonProperty("pageNo")
        private Integer pageNo;

        @JsonProperty("totalCount")
        private Integer totalCount;
    }

    @Data
    public static class Items {
        @JsonProperty("item")
        private List<FlightInfoResponse> item;
    }

    /**
     * 결과 코드 가져오기 (편의 메서드)
     */
    public String getResultCode() {
        return response != null && response.header != null
                ? response.header.getResultCode()
                : null;
    }

    /**
     * 결과 메시지 가져오기 (편의 메서드)
     */
    public String getResultMessage() {
        return response != null && response.header != null
                ? response.header.getResultMessage()
                : null;
    }

    /**
     * 전체 카운트 가져오기 (편의 메서드)
     */
    public Integer getTotalCount() {
        return response != null && response.body != null
                ? response.body.getTotalCount()
                : null;
    }

    /**
     * 아이템 목록 가져오기 (편의 메서드)
     */
    public List<FlightInfoResponse> getItems() {
        return response != null && response.body != null && response.body.items != null
                ? response.body.items.getItem()
                : null;
    }
}

package com.nhnacademy.flyschedule.service.api;

import com.nhnacademy.flyschedule.config.DataGoKrApiProperties;
import com.nhnacademy.flyschedule.dto.response.AirlineInfoResponse;
import com.nhnacademy.flyschedule.dto.response.AirportInfoResponse;
import com.nhnacademy.flyschedule.dto.response.ApiResponseWrapper;
import com.nhnacademy.flyschedule.dto.response.FlightInfoResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * API 클라이언트 서비스
 *
 * 공공데이터 포털 API를 RestClient로 호출합니다.
 */
@Slf4j
@Service
public class ApiClientService {

    private final DataGoKrApiProperties properties;
    private final RestClient restClient;

    public ApiClientService(DataGoKrApiProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.create();
    }

    /**
     * 파라미터 URL 인코딩
     */
    private String encodeParam(String param) {
        try {
            return URLEncoder.encode(param, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("파라미터 인코딩 실패: {}", param);
            return param;
        }
    }

    /**
     * 항공운항정보 조회
     *
     * @param depAirportId 출발공항ID (예: NAARKJJ - 광주)
     * @param arrAirportId 도착공항ID (예: NAARKPC - 제주)
     * @param depPlandTime 출발일 (예: 20250309)
     * @return 항공편 정보 목록
     */
    public List<FlightInfoResponse> getFlightSchedule(
            String depAirportId,
            String arrAirportId,
            String depPlandTime) {

        // Service Key는 인코딩하지 않고 그대로 사용 (공공데이터 포털 요구사항)
        String url = properties.getUrl() + "/GetFlightOpratInfoList" +
                "?serviceKey=" + properties.getServiceKey() +
                "&pageNo=1" +
                "&numOfRows=100" +
                "&_type=json" +
                "&depAirportId=" + encodeParam(depAirportId) +
                "&arrAirportId=" + encodeParam(arrAirportId) +
                "&depPlandTime=" + encodeParam(depPlandTime);

        log.info("항공편 API 호출 URL: {}", url);

        ApiResponseWrapper response = restClient.get()
                .uri(url)
                .retrieve()
                .body(ApiResponseWrapper.class);

        if (response != null && "00".equals(response.getResultCode())) {
            log.info("항공편 {}건 조회 완료", response.getTotalCount());
            return response.getItems() != null ? response.getItems() : Collections.emptyList();
        }

        log.warn("API 호출 실패: {}", response != null ? response.getResultMessage() : "null");
        return Collections.emptyList();
    }

    /**
     * 공항 목록 조회
     *
     * @return 공항 정보 목록
     */
    public List<AirportInfoResponse> getAirportList() {
        // Service Key는 인코딩하지 않고 그대로 사용
        // _type=json 파라미터 제거 (공항 목록 API는 JSON이 기본)
        String url = properties.getUrl() + "/GetArprtList" +
                "?serviceKey=" + properties.getServiceKey();

        log.info("공항 목록 API 호출");

        AirportListResponse response = restClient.get()
                .uri(url)
                .retrieve()
                .body(AirportListResponse.class);

        if (response != null && "00".equals(response.getResultCode())) {
            return response.getItems() != null ? response.getItems() : Collections.emptyList();
        }

        return Collections.emptyList();
    }

    /**
     * 항공사 목록 조회
     *
     * @return 항공사 정보 목록
     */
    public List<AirlineInfoResponse> getAirlineList() {
        // Service Key는 인코딩하지 않고 그대로 사용
        String url = properties.getUrl() + "/GetAirmanList" +
                "?serviceKey=" + properties.getServiceKey() +
                "&_type=json";

        log.info("항공사 목록 API 호출");

        AirlineListResponse response = restClient.get()
                .uri(url)
                .retrieve()
                .body(AirlineListResponse.class);

        if (response != null && "00".equals(response.getResultCode())) {
            return response.getItems() != null ? response.getItems() : Collections.emptyList();
        }

        return Collections.emptyList();
    }

    /**
     * 공항 목록 응답 래퍼 (내부 클래스)
     */
    @lombok.Data
    private static class AirportListResponse {
        private Response response;

        @lombok.Data
        public static class Response {
            private Header header;
            private Body body;
        }

        @lombok.Data
        public static class Header {
            private String resultCode;
            private String resultMsg;
        }

        @lombok.Data
        public static class Body {
            private Items items;
        }

        @lombok.Data
        public static class Items {
            private List<AirportInfoResponse> item;
        }

        public String getResultCode() {
            return response != null && response.header != null ? response.header.getResultCode() : null;
        }

        public List<AirportInfoResponse> getItems() {
            return response != null && response.body != null && response.body.items != null
                    ? response.body.items.getItem()
                    : null;
        }
    }

    /**
     * 항공사 목록 응답 래퍼 (내부 클래스)
     */
    @lombok.Data
    private static class AirlineListResponse {
        private Response response;

        @lombok.Data
        public static class Response {
            private Header header;
            private Body body;
        }

        @lombok.Data
        public static class Header {
            private String resultCode;
            private String resultMsg;
        }

        @lombok.Data
        public static class Body {
            private Items items;
        }

        @lombok.Data
        public static class Items {
            private List<AirlineInfoResponse> item;
        }

        public String getResultCode() {
            return response != null && response.header != null ? response.header.getResultCode() : null;
        }

        public List<AirlineInfoResponse> getItems() {
            return response != null && response.body != null && response.body.items != null
                    ? response.body.items.getItem()
                    : null;
        }
    }
}

package net.java21.flyschedule.service.api;

import net.java21.flyschedule.config.DataGoKrApiProperties;
import net.java21.flyschedule.dto.response.AirlineInfoResponse;
import net.java21.flyschedule.dto.response.AirportInfoResponse;
import net.java21.flyschedule.dto.response.ApiResponseWrapper;
import net.java21.flyschedule.dto.response.FlightInfoResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
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

    /**
     * 한 번에 받을 최대 건수. 김포→제주처럼 하루 100편이 넘는 노선이 있으므로(2026-10 기준 118편)
     * 100으로 두면 뒤쪽 항공편이 잘립니다. 국내선 한 노선의 하루 운항 편수보다 넉넉하게 잡습니다.
     */
    static final int MAX_ROWS = 500;

    private final DataGoKrApiProperties properties;
    private final RestClient restClient;

    /**
     * Spring Boot가 만든 RestClient.Builder를 사용해야 application.yml의
     * spring.http.client.* (연결·읽기 타임아웃) 설정이 적용됩니다. RestClient.create()는 이 설정을 쓰지 않습니다.
     */
    public ApiClientService(DataGoKrApiProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    /**
     * GET 요청을 보내고 응답을 변환합니다. 서비스 키가 없거나 호출이 실패하면 ExternalApiException을 던집니다.
     */
    private <T> T get(String url, Class<T> type) {
        if (properties.getServiceKey() == null || properties.getServiceKey().isBlank()) {
            throw new ExternalApiException("공공데이터 포털 서비스 키가 설정되지 않았습니다. 환경 변수 DATA_GO_KR_SERVICE_KEY를 설정하세요.");
        }
        try {
            return restClient.get().uri(url).retrieve().body(type);
        } catch (RestClientException e) {
            log.warn("공공데이터 포털 API 호출 실패: {}", e.getMessage());
            throw new ExternalApiException("공공데이터 포털 API를 호출하지 못했습니다. 잠시 후 다시 시도하세요.", e);
        }
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
                "&numOfRows=" + MAX_ROWS +
                "&_type=json" +
                "&depAirportId=" + encodeParam(depAirportId) +
                "&arrAirportId=" + encodeParam(arrAirportId) +
                "&depPlandTime=" + encodeParam(depPlandTime);

        log.info("항공편 API 호출: dep={}, arr={}, date={}", depAirportId, arrAirportId, depPlandTime);

        ApiResponseWrapper response = get(url, ApiResponseWrapper.class);
        if (response == null || !"00".equals(response.getResultCode())) {
            String reason = response != null ? response.getResultMessage() : "응답 없음";
            log.warn("항공편 API 오류: {}", reason);
            throw new ExternalApiException("공공데이터 포털 API 오류: " + reason);
        }

        log.info("항공편 {}건 조회 완료", response.getTotalCount());
        return response.getItems() != null ? response.getItems() : Collections.emptyList();
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

        AirportListResponse response = get(url, AirportListResponse.class);
        if (response == null || !"00".equals(response.getResultCode())) {
            throw new ExternalApiException("공공데이터 포털 API 오류: 공항 목록을 조회하지 못했습니다.");
        }
        return response.getItems() != null ? response.getItems() : Collections.emptyList();
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

        AirlineListResponse response = get(url, AirlineListResponse.class);
        if (response == null || !"00".equals(response.getResultCode())) {
            throw new ExternalApiException("공공데이터 포털 API 오류: 항공사 목록을 조회하지 못했습니다.");
        }
        return response.getItems() != null ? response.getItems() : Collections.emptyList();
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

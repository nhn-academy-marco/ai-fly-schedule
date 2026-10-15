package net.java21.flyschedule.service.api;

import net.java21.flyschedule.config.DataGoKrApiProperties;
import net.java21.flyschedule.dto.response.FlightInfoResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * ApiClientService 테스트
 *
 * <p>MockRestServiceServer로 공공데이터 포털 API를 흉내 내어, 요청 URL과 응답 변환을 검증합니다.
 */
@DisplayName("ApiClientService 테스트")
class ApiClientServiceTest {

    private MockRestServiceServer server;
    private ApiClientService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();

        DataGoKrApiProperties properties = new DataGoKrApiProperties();
        properties.setUrl("https://apis.data.go.kr/1613000/DmstcFlightNvgInfo");
        properties.setServiceKey("test-key");
        service = new ApiClientService(properties, builder);
    }

    @Test
    @DisplayName("하루 100편이 넘는 노선도 잘리지 않도록 한 번에 MAX_ROWS건을 요청한다")
    void requestsEnoughRows() {
        server.expect(requestTo(allOf(
                        containsString("/GetFlightOpratInfoList"),
                        containsString("numOfRows=" + ApiClientService.MAX_ROWS),
                        containsString("depAirportId=NAARKSS"),
                        containsString("arrAirportId=NAARKPC"),
                        containsString("depPlandTime=20261016"))))
                .andRespond(withSuccess("""
                        {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},
                         "body":{"items":{"item":[
                           {"vihicleId":"7C125","airlineNm":"제주항공","depPlandTime":202610161425,
                            "arrPlandTime":202610161535,"economyCharge":51400,"depAirportNm":"김포","arrAirportNm":"제주"}
                         ]},"numOfRows":500,"pageNo":1,"totalCount":1}}}
                        """, MediaType.APPLICATION_JSON));

        List<FlightInfoResponse> flights = service.getFlightSchedule("NAARKSS", "NAARKPC", "20261016");

        assertThat(ApiClientService.MAX_ROWS).isGreaterThan(118); // 김포→제주 하루 118편 (2026-10 실측)
        assertThat(flights).singleElement().satisfies(flight -> {
            assertThat(flight.getFlightId()).isEqualTo("7C125");
            assertThat(flight.getEconomyCharge()).isEqualTo(51400);
        });
        server.verify();
    }

    @Test
    @DisplayName("HTTP 오류는 '항공편 없음'으로 숨기지 않고 ExternalApiException으로 알린다")
    void serverError() {
        server.expect(requestTo(containsString("/GetFlightOpratInfoList"))).andRespond(withServerError());

        assertThatThrownBy(() -> service.getFlightSchedule("NAARKJJ", "NAARKPC", "20261016"))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("호출하지 못했습니다");
    }

    @Test
    @DisplayName("API가 오류 코드(resultCode != 00)를 돌려주면 그 이유를 담아 알린다")
    void resultCodeError() {
        server.expect(requestTo(containsString("/GetFlightOpratInfoList"))).andRespond(withSuccess("""
                {"response":{"header":{"resultCode":"30","resultMsg":"SERVICE_KEY_IS_NOT_REGISTERED_ERROR"}}}
                """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.getFlightSchedule("NAARKJJ", "NAARKPC", "20261016"))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("SERVICE_KEY_IS_NOT_REGISTERED_ERROR");
    }

    @Test
    @DisplayName("서비스 키가 없으면 API를 호출하지 않고 설정 방법을 안내한다")
    void missingServiceKey() {
        DataGoKrApiProperties noKey = new DataGoKrApiProperties();
        noKey.setUrl("https://apis.data.go.kr/1613000/DmstcFlightNvgInfo");
        noKey.setServiceKey("");

        assertThatThrownBy(() -> new ApiClientService(noKey, RestClient.builder()).getAirportList())
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("DATA_GO_KR_SERVICE_KEY");
    }
}

package net.java21.flyschedule.service.coordinator;

import net.java21.flyschedule.config.ChatClientProvider;
import net.java21.flyschedule.dto.response.FlightInfoResponse;
import net.java21.flyschedule.dto.search.ExecutionTrace;
import net.java21.flyschedule.dto.search.SearchCriteria;
import net.java21.flyschedule.dto.search.SearchResponse;
import net.java21.flyschedule.dto.search.TaskResult;
import net.java21.flyschedule.dto.search.TraceStep;
import net.java21.flyschedule.dto.search.TraceStep.Kind;
import net.java21.flyschedule.service.agent.AirportCodeAgent;
import net.java21.flyschedule.service.agent.DateParserAgent;
import net.java21.flyschedule.service.agent.GroupingAgent;
import net.java21.flyschedule.service.agent.PriceFilterAgent;
import net.java21.flyschedule.service.agent.TimeFilterAgent;
import net.java21.flyschedule.service.ai.SearchCriteriaExtractor;
import net.java21.flyschedule.service.api.ApiClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static net.java21.flyschedule.support.TestFlights.flight;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Coordinator 패턴 테스트
 *
 * <p>외부 API(ApiClientService)와 LLM(ChatClient)만 가짜로 바꾸고, 전문 Agent는 실제 객체를 사용합니다.
 */
@DisplayName("FlightSearchCoordinator 테스트")
class FlightSearchCoordinatorTest {

    private ApiClientService apiClientService;
    private ChatClient chatClient;
    private FlightSearchCoordinator coordinator;

    private final List<FlightInfoResponse> apiFlights = List.of(
            flight("OZ8141", "아시아나항공", "0955", "1045", 57900),
            flight("7C1402", "제주항공", "1030", "1120", 45000),
            flight("7C1406", "제주항공", "1530", "1620", 72000),
            flight("KE1903", "대한항공", "1830", "1920", 89000));

    @BeforeEach
    void setUp() {
        apiClientService = mock(ApiClientService.class);
        chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        ChatClientProvider provider = mock(ChatClientProvider.class);
        when(provider.get(anyString())).thenReturn(chatClient);
        when(apiClientService.getFlightSchedule(anyString(), anyString(), anyString())).thenReturn(apiFlights);

        coordinator = new FlightSearchCoordinator(provider, new SearchCriteriaExtractor(),
                new DateParserAgent(), new AirportCodeAgent(), apiClientService,
                new TimeFilterAgent(), new PriceFilterAgent(), new GroupingAgent());
    }

    @Test
    @DisplayName("코드가 정한 순서대로 Agent를 호출하고, 모든 단계가 CODE로 기록된다")
    void fixedOrderAllCode() {
        SearchResponse response = coordinator.coordinate("내일 광주에서 제주로 가는 항공편", "ollama");

        assertThat(response.success()).isTrue();
        assertThat(response.pattern()).isEqualTo("coordinator");
        assertThat(response.trace()).extracting(TraceStep::actor).containsExactly(
                "SearchCriteriaExtractor", "DateParserAgent", "AirportCodeAgent", "AirportCodeAgent",
                "ApiClientService", "GroupingAgent");
        assertThat(response.trace()).extracting(TraceStep::kind).containsOnly(Kind.CODE);
        assertThat(response.results()).singleElement()
                .satisfies(result -> assertThat(result.totalCount()).isEqualTo(4));
    }

    @Test
    @DisplayName("변환된 날짜·공항 코드로 공공데이터 포털 API를 호출한다")
    void callsApiWithConvertedValues() {
        coordinator.coordinate("내일 광주에서 제주로 가는 항공편", "ollama");

        String tomorrow = LocalDate.now().plusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE);
        verify(apiClientService).getFlightSchedule("NAARKJJ", "NAARKPC", tomorrow);
    }

    @Test
    @DisplayName("시간·가격 조건이 있을 때만 필터 단계가 추가된다")
    void filtersOnlyWhenRequested() {
        SearchResponse response = coordinator.coordinate("내일 광주에서 제주 오전 10시 이후 8만원 이하", "ollama");

        assertThat(response.trace()).extracting(TraceStep::actor)
                .contains("TimeFilterAgent", "PriceFilterAgent");
        TaskResult result = response.results().get(0);
        // 10:00 이후 → 7C1402, 7C1406, KE1903 / 8만원 이하 → 7C1402, 7C1406
        assertThat(result.totalCount()).isEqualTo(2);
        assertThat(result.cheapest().flightId()).isEqualTo("7C1402");
        assertThat(result.flightsByAirline()).containsOnlyKeys("제주항공");
    }

    @Test
    @DisplayName("정규식이 실패하면 1단계만 LLM으로 처리하고, 나머지 순서는 그대로다")
    void llmOnlyForExtraction() {
        when(chatClient.prompt().system(anyString()).user(anyString()).call().entity(SearchCriteria.class))
                .thenReturn(new SearchCriteria("김포", "제주", "2026-03-27", null, null, null, null));

        SearchResponse response = coordinator.coordinate("이번 주 금요일 서울에서 제주도", "gemini");

        assertThat(response.success()).isTrue();
        assertThat(response.trace().get(0).kind()).isEqualTo(Kind.LLM);
        assertThat(response.trace().subList(1, response.trace().size())).extracting(TraceStep::kind).containsOnly(Kind.CODE);
        verify(apiClientService).getFlightSchedule("NAARKSS", "NAARKPC", "20260327");
    }

    @Test
    @DisplayName("정규식으로 처리되는 문장은 Gemini 키가 없어도 검색된다 (LLM 설정을 확인하지 않음)")
    void regexPathIgnoresModelConfig() {
        ChatClientProvider noGemini = new ChatClientProvider(chatClient, chatClient, "not-set");
        FlightSearchCoordinator withoutGemini = new FlightSearchCoordinator(noGemini, new SearchCriteriaExtractor(),
                new DateParserAgent(), new AirportCodeAgent(), apiClientService,
                new TimeFilterAgent(), new PriceFilterAgent(), new GroupingAgent());

        assertThat(withoutGemini.coordinate("내일 광주에서 제주", "gemini").success()).isTrue();
        assertThat(withoutGemini.coordinate("제주 가는 비행기", "gemini").message()).contains("GOOGLE_GENAI_API_KEY");
    }

    @Test
    @DisplayName("알 수 없는 공항이면 실패 응답을 돌려주고 API를 호출하지 않는다")
    void unknownAirport() {
        ExecutionTrace trace = new ExecutionTrace();
        SearchCriteria criteria = new SearchCriteria("서울", "제주", "2026-03-25", null, null, null, null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> coordinator.search(criteria, trace))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(trace.steps()).last().extracting(TraceStep::detail).asString().startsWith("실패");
        verify(apiClientService, never()).getFlightSchedule(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("LLM도 공항을 찾지 못하면 안내 메시지와 함께 실패한다")
    void failResponse() {
        when(chatClient.prompt().system(anyString()).user(anyString()).call().entity(SearchCriteria.class))
                .thenReturn(new SearchCriteria(null, null, null, null, null, null, null));

        SearchResponse response = coordinator.coordinate("비행기 표 알려줘", "ollama");

        assertThat(response.success()).isFalse();
        assertThat(response.message()).contains("출발 공항과 도착 공항");
        assertThat(response.results()).isEmpty();
        assertThat(response.trace()).singleElement()
                .extracting(TraceStep::detail).asString().startsWith("실패");
    }

    @Test
    @DisplayName("API 결과가 없으면 성공이지만 '항공편이 없다'고 안내한다")
    void emptyResult() {
        when(apiClientService.getFlightSchedule(anyString(), anyString(), anyString())).thenReturn(List.of());

        SearchResponse response = coordinator.coordinate("내일 광주에서 제주", "ollama");

        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("조건에 맞는 항공편이 없습니다.");
        assertThat(response.results().get(0).isEmpty()).isTrue();
    }
}

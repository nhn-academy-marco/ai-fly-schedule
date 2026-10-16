package net.java21.flyschedule.service.toolcalling;

import net.java21.flyschedule.config.ChatClientProvider;
import net.java21.flyschedule.dto.search.SearchResponse;
import net.java21.flyschedule.dto.search.TraceStep;
import net.java21.flyschedule.dto.search.TraceStep.Kind;
import net.java21.flyschedule.mcp.AirlineInfoTool;
import net.java21.flyschedule.mcp.AirportInfoTool;
import net.java21.flyschedule.mcp.FlightSearchTool;
import net.java21.flyschedule.mcp.ToolResultCapture;
import net.java21.flyschedule.service.agent.AirlineListAgent;
import net.java21.flyschedule.service.agent.AirportListAgent;
import net.java21.flyschedule.service.agent.FlightSearchAgent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static net.java21.flyschedule.support.TestFlights.flight;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tool Calling Agent 테스트
 *
 * <p>실제 LLM 대신, {@code call().content()}가 호출될 때 "LLM이 Tool을 요청한 것처럼" Tool 메서드를 직접 실행하는
 * 가짜 응답을 만듭니다. 이렇게 하면 Tool 호출 기록과 결과 처리 로직을 LLM 없이 검증할 수 있습니다.
 */
@DisplayName("ToolCallingSearchService 테스트")
class ToolCallingSearchServiceTest {

    private FlightSearchAgent flightSearchAgent;
    private AirportListAgent airportListAgent;
    private FlightSearchTool flightSearchTool;
    private AirportInfoTool airportInfoTool;
    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec callSpec;
    private ToolCallingSearchService service;

    @BeforeEach
    void setUp() {
        flightSearchAgent = mock(FlightSearchAgent.class);
        airportListAgent = mock(AirportListAgent.class);
        flightSearchTool = new FlightSearchTool(flightSearchAgent);
        airportInfoTool = new AirportInfoTool(airportListAgent);

        ChatClient chatClient = mock(ChatClient.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class, RETURNS_SELF);
        callSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);

        ChatClientProvider provider = mock(ChatClientProvider.class);
        when(provider.get(anyString())).thenReturn(chatClient);

        service = new ToolCallingSearchService(provider, flightSearchTool);
    }

    @AfterEach
    void clear() {
        ToolResultCapture.getAndClear();
    }

    /** LLM이 Tool을 호출한 뒤 answer를 답한 것처럼 동작하게 한다 */
    private void llmCallsToolsThenAnswers(Supplier<String> llmTurn) {
        when(callSpec.content()).thenAnswer(invocation -> llmTurn.get());
    }

    @Test
    @DisplayName("Tool은 실행되는 즉시 TOOL 단계로 기록되고, 그 전체를 감싸는 LLM 구간이 마지막에 기록된다")
    void recordsToolCalls() {
        when(airportListAgent.getAirportCode("광주")).thenReturn("NAARKJJ");
        when(flightSearchAgent.searchAndGroupByAirline("광주", "제주", "2026-03-25"))
                .thenReturn(Map.of("제주항공", List.of(flight("7C1402", "제주항공", "1030", "1120", 45000))));
        llmCallsToolsThenAnswers(() -> {
            airportInfoTool.getAirportCode("광주");
            flightSearchTool.searchFlightsByAirline("광주", "제주", "2026-03-25");
            return "제주항공 7C1402편이 45,000원으로 가장 저렴합니다.";
        });

        SearchResponse response = service.search("3월 25일 광주에서 제주", "gemini");

        assertThat(response.success()).isTrue();
        assertThat(response.pattern()).isEqualTo("tool-calling");
        assertThat(response.message()).contains("7C1402");
        assertThat(response.trace()).extracting(TraceStep::kind)
                .containsExactly(Kind.TOOL, Kind.TOOL, Kind.LLM);
        assertThat(response.trace()).extracting(TraceStep::actor)
                .containsExactly("getAirportCode", "searchFlightsByAirline", "gemini");
        // Tool 실행 구간은 LLM 호출 구간 안에 있다 (타임라인에서 LLM 막대 안에 Tool 막대가 그려짐)
        TraceStep llm = response.trace().get(2);
        assertThat(response.trace().subList(0, 2)).allSatisfy(tool -> {
            assertThat(tool.startMs()).isGreaterThanOrEqualTo(llm.startMs());
            assertThat(tool.startMs() + tool.durationMs()).isLessThanOrEqualTo(llm.startMs() + llm.durationMs());
        });
        assertThat(response.results()).singleElement().satisfies(result -> {
            assertThat(result.label()).isEqualTo("광주 → 제주, 2026-03-25");
            assertThat(result.cheapest().flightId()).isEqualTo("7C1402");
        });
    }

    @Test
    @DisplayName("항공편 검색 Tool만 요청마다 전달하고, 시스템 프롬프트에 오늘 날짜를 넣는다")
    void passesToolsPerRequest() {
        llmCallsToolsThenAnswers(() -> {
            flightSearchTool.searchFlightsByAirline("광주", "제주", "내일");
            return "답변";
        });
        when(flightSearchAgent.searchAndGroupByAirline(anyString(), anyString(), anyString())).thenReturn(Map.of());

        service.search("내일 광주에서 제주", "ollama");

        // 항공편 검색 Tool만 넘긴다 (공항·항공사 조회 Tool은 넘기지 않아 LLM이 읽을 Tool 설명을 줄인다)
        verify(requestSpec).tools(flightSearchTool);
        verify(requestSpec).system(org.mockito.ArgumentMatchers.contains(java.time.LocalDate.now().toString()));
    }

    @Test
    @DisplayName("여러 검색 Tool을 호출했다면 마지막 항공편 검색 결과를 쓴다")
    void usesLastFlightResult() {
        when(flightSearchAgent.searchAndGroupByAirline(anyString(), anyString(), anyString()))
                .thenReturn(Map.of("대한항공", List.of(flight("KE1901", "대한항공", "0800", "0850", 90000))));
        when(flightSearchAgent.searchWithTimeFilter(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Map.of("제주항공", List.of(flight("7C1406", "제주항공", "1530", "1620", 72000))));
        llmCallsToolsThenAnswers(() -> {
            flightSearchTool.searchFlightsByAirline("광주", "제주", "내일");
            flightSearchTool.searchFlightsAfterTime("광주", "제주", "내일", "14:00");
            return "답변";
        });

        SearchResponse response = service.search("내일 광주에서 제주 오후 2시 이후", "ollama");

        assertThat(response.results().get(0).flightsByAirline()).containsOnlyKeys("제주항공");
    }

    @Test
    @DisplayName("항공사별로 최대 3편만 LLM에 넘긴다 (프롬프트 길이 제한)")
    void limitsThreePerAirline() {
        when(flightSearchAgent.searchAndGroupByAirline(anyString(), anyString(), anyString()))
                .thenReturn(Map.of("제주항공", List.of(
                        flight("7C1", "제주항공", "0700", "0750", 40000),
                        flight("7C2", "제주항공", "0800", "0850", 41000),
                        flight("7C3", "제주항공", "0900", "0950", 42000),
                        flight("7C4", "제주항공", "1000", "1050", 43000))));
        llmCallsToolsThenAnswers(() -> {
            flightSearchTool.searchFlightsByAirline("광주", "제주", "내일");
            return "답변";
        });

        SearchResponse response = service.search("내일 광주에서 제주", "ollama");

        assertThat(response.results().get(0).totalCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("LLM이 Tool을 하나도 호출하지 않으면 실패로 처리하고 LLM의 답을 보여 준다")
    void noToolCall() {
        llmCallsToolsThenAnswers(() -> "어느 공항에서 출발하시나요?");

        SearchResponse response = service.search("제주 가고 싶어", "ollama");

        assertThat(response.success()).isFalse();
        assertThat(response.message()).isEqualTo("어느 공항에서 출발하시나요?");
        assertThat(response.trace()).extracting(TraceStep::kind).containsExactly(Kind.LLM);
    }


    @Test
    @DisplayName("Tool이 실패하면 실패한 Tool 호출도 기록에 남는다 (LLM은 오류를 받고 다른 시도를 할 수 있음)")
    void failedToolIsRecorded() {
        when(airportListAgent.getAirportCode("서울")).thenThrow(new IllegalArgumentException("알 수 없는 공항입니다: 서울"));
        when(flightSearchAgent.searchAndGroupByAirline(anyString(), anyString(), anyString())).thenReturn(Map.of());
        llmCallsToolsThenAnswers(() -> {
            try {
                airportInfoTool.getAirportCode("서울");
            } catch (IllegalArgumentException e) {
                // Spring AI가 오류 메시지를 LLM에게 돌려주고, LLM이 김포로 다시 시도했다고 가정
            }
            flightSearchTool.searchFlightsByAirline("김포", "제주", "2026-03-25");
            return "서울은 김포공항으로 검색했습니다.";
        });

        SearchResponse response = service.search("서울에서 제주", "ollama");

        assertThat(response.trace()).extracting(TraceStep::title)
                .containsExactly("Tool 실행 실패", "LLM이 요청한 Tool 실행", "Tool 선택과 답변 작성");
        assertThat(response.trace().get(0).detail()).contains("실패: 알 수 없는 공항입니다: 서울");
    }

    @Test
    @DisplayName("LLM 호출이 실패하면 실패 응답을 주고, 그때까지 실행된 Tool은 기록한 뒤 ThreadLocal을 비운다")
    void llmFailure() {
        llmCallsToolsThenAnswers(() -> {
            flightSearchTool.searchFlightsByAirline("광주", "제주", "내일");
            throw new IllegalStateException("Ollama 연결 실패");
        });
        when(flightSearchAgent.searchAndGroupByAirline(anyString(), anyString(), anyString())).thenReturn(Map.of());

        SearchResponse response = service.search("내일 광주에서 제주", "ollama");

        assertThat(response.success()).isFalse();
        assertThat(response.message()).contains("Ollama 연결 실패"); // 알 수 없는 오류는 원래 메시지를 보여 준다
        // 실패 전까지 실행된 Tool과, 실패한 LLM 구간이 모두 남는다
        assertThat(response.trace()).extracting(TraceStep::actor).containsExactly("searchFlightsByAirline", "ollama");
        assertThat(response.trace().get(1).detail()).startsWith("실패");
        assertThat(ToolResultCapture.getAndClear()).isEmpty();
    }

    @Test
    @DisplayName("Tool 단계는 LLM 호출이 끝나기 전에 이미 실행 기록에 나타난다 (화면 실시간 표시)")
    void toolStepsAppearWhileLlmIsRunning() {
        when(flightSearchAgent.searchAndGroupByAirline(anyString(), anyString(), anyString())).thenReturn(Map.of());
        net.java21.flyschedule.dto.search.ExecutionTrace trace = new net.java21.flyschedule.dto.search.ExecutionTrace();
        java.util.List<Integer> stepsSeenBeforeAnswer = new java.util.ArrayList<>();
        llmCallsToolsThenAnswers(() -> {
            flightSearchTool.searchFlightsByAirline("광주", "제주", "2026-03-25");
            stepsSeenBeforeAnswer.add(trace.steps().size()); // LLM이 답변을 쓰기 전 시점
            return "답변";
        });

        service.search("광주에서 제주", "ollama", trace);

        assertThat(stepsSeenBeforeAnswer).containsExactly(1);
        assertThat(trace.steps()).extracting(TraceStep::kind).containsExactly(Kind.TOOL, Kind.LLM);
    }

    @Test
    @DisplayName("이전 요청에서 남은 Tool 기록은 새 요청에 섞이지 않는다")
    void clearsLeftovers() {
        ToolResultCapture.capture("getAirportList", "전체", List::of);
        llmCallsToolsThenAnswers(() -> "Tool 없이 답변");

        SearchResponse response = service.search("안녕", "ollama");

        assertThat(response.trace()).noneMatch(step -> step.actor().equals("getAirportList"));
    }
}

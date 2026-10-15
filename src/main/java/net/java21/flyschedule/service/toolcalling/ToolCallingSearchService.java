package net.java21.flyschedule.service.toolcalling;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.java21.flyschedule.config.ChatClientProvider;
import net.java21.flyschedule.dto.response.FlightInfoResponse;
import net.java21.flyschedule.dto.search.ExecutionTrace;
import net.java21.flyschedule.dto.search.SearchResponse;
import net.java21.flyschedule.dto.search.TaskResult;
import net.java21.flyschedule.dto.search.TraceStep.Kind;
import net.java21.flyschedule.mcp.AirlineInfoTool;
import net.java21.flyschedule.mcp.AirportInfoTool;
import net.java21.flyschedule.mcp.FlightSearchTool;
import net.java21.flyschedule.mcp.ToolResultCapture;
import net.java21.flyschedule.mcp.ToolResultCapture.ToolCall;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Tool Calling 에이전트: LLM이 제어하는 검색
 *
 * <p>코드는 LLM에게 질문과 사용할 수 있는 Tool 목록만 넘깁니다.
 * 어떤 Tool을 어떤 인자로 몇 번 부를지는 LLM이 결정하고, Spring AI가 그 요청대로 Tool을 실행합니다.
 * LLM 하나가 Tool을 쓰는 단일 에이전트이며, 여러 에이전트가 서로 통신하는 구조(A2A 프로토콜)와는 다릅니다.
 *
 * <pre>
 * ChatClient.call()
 *   └ LLM: "searchFlightsByAirline(광주, 제주, 2026-03-25)를 호출해 줘"
 *       └ Spring AI가 FlightSearchTool 실행 → 결과를 LLM에게 전달
 *   └ LLM: 결과를 보고 답변 작성 (또는 다른 Tool을 더 호출)
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolCallingSearchService {

    public static final String PATTERN = "tool-calling";

    private final ChatClientProvider chatClientProvider;
    private final FlightSearchTool flightSearchTool;
    private final AirportInfoTool airportInfoTool;
    private final AirlineInfoTool airlineInfoTool;

    public SearchResponse search(String message, String model) {
        ExecutionTrace trace = new ExecutionTrace();
        ToolResultCapture.getAndClear(); // 이전 요청의 기록이 남아 있지 않도록 비운다

        try {
            long start = System.nanoTime();
            String answer = chatClientProvider.get(model).prompt()
                    .system(systemPrompt())
                    .user(message)
                    .tools(flightSearchTool, airportInfoTool, airlineInfoTool)
                    .call()
                    .content();

            // LLM 호출 구간을 먼저, 그 안에서 실행된 Tool 호출을 이어서 기록한다
            List<ToolCall> calls = ToolResultCapture.getAndClear();
            trace.record(Kind.LLM, ChatClientProvider.normalize(model),
                    "Tool 선택과 답변 작성", "Tool " + calls.size() + "회 호출 후 답변", start);
            recordToolCalls(calls, trace);

            if (calls.isEmpty()) {
                return SearchResponse.fail(PATTERN,
                        StringUtils.hasText(answer) ? answer : "LLM이 Tool을 호출하지 않았습니다.", trace);
            }

            TaskResult result = lastFlightResult(calls);
            String text = StringUtils.hasText(answer) ? answer : "검색을 마쳤습니다.";
            return SearchResponse.success(PATTERN, text, result == null ? List.of() : List.of(result), trace);

        } catch (IllegalArgumentException e) {
            ToolResultCapture.getAndClear();
            return SearchResponse.fail(PATTERN, e.getMessage(), trace);
        } catch (Exception e) {
            log.error("Tool Calling 검색 실패 (model={})", model, e);
            recordToolCalls(ToolResultCapture.getAndClear(), trace); // 실패 전까지 실행된 Tool도 남기고, ThreadLocal을 비운다
            return SearchResponse.fail(PATTERN, ChatClientProvider.describeError(e), trace);
        }
    }

    private static void recordToolCalls(List<ToolCall> calls, ExecutionTrace trace) {
        calls.forEach(call -> trace.record(Kind.TOOL, call.toolName(),
                call.failed() ? "Tool 실행 실패" : "LLM이 요청한 Tool 실행",
                call.arguments() + " → " + (call.failed() ? "실패: " + call.error() : describe(call.result())),
                call.startNanos(), call.endNanos()));
    }

    private String systemPrompt() {
        return """
                당신은 국내선 항공편 검색 도우미입니다. 반드시 한국어로 답변하세요.
                항공편 정보는 반드시 Tool을 호출해서 얻고, Tool 결과에 없는 내용은 지어내지 마세요.
                오늘 날짜: %s
                규칙:
                - 날짜는 "2026-06-25" 형식으로 Tool에 전달합니다.
                - 시간은 24시간 형식("14:00")으로 전달합니다.
                - 공항은 한글 이름 그대로 전달합니다. (예: "광주", "제주")
                - 답변은 항공편 수와 가장 저렴한 항공편을 중심으로 3~4문장으로 요약합니다.
                """.formatted(LocalDate.now());
    }

    /**
     * 가장 마지막에 실행된 항공편 검색 Tool의 결과를 화면용 결과로 바꿉니다.
     * 공항 코드 조회처럼 항공편 목록이 아닌 결과는 건너뜁니다.
     */
    @SuppressWarnings("unchecked")
    private TaskResult lastFlightResult(List<ToolCall> calls) {
        for (int i = calls.size() - 1; i >= 0; i--) {
            if (calls.get(i).result() instanceof Map<?, ?> map) {
                return TaskResult.of(null, (Map<String, List<FlightInfoResponse>>) map)
                        .withLabel(calls.get(i).arguments());
            }
        }
        return null;
    }

    private static String describe(Object result) {
        if (result instanceof Map<?, ?> map) {
            int count = map.values().stream().mapToInt(v -> v instanceof List<?> list ? list.size() : 0).sum();
            return map.size() + "개 항공사, " + count + "편";
        }
        if (result instanceof List<?> list) {
            return list.size() + "건";
        }
        return String.valueOf(result);
    }
}

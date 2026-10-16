package net.java21.flyschedule.service.toolcalling;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.java21.flyschedule.config.ChatClientProvider;
import net.java21.flyschedule.dto.response.FlightInfoResponse;
import net.java21.flyschedule.dto.search.ExecutionTrace;
import net.java21.flyschedule.dto.search.SearchResponse;
import net.java21.flyschedule.dto.search.TaskResult;
import net.java21.flyschedule.dto.search.TraceStep.Kind;
import net.java21.flyschedule.mcp.FlightSearchTool;
import net.java21.flyschedule.mcp.ToolResultCapture;
import net.java21.flyschedule.mcp.ToolResultCapture.ToolCall;
import net.java21.flyschedule.service.ai.DateContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * Tool Calling Agent: LLM이 제어하는 검색
 *
 * <p>코드는 LLM에게 질문과 사용할 수 있는 Tool 목록만 넘깁니다.
 * 어떤 Tool을 어떤 인자로 몇 번 부를지는 LLM이 결정하고, Spring AI가 그 요청대로 Tool을 실행합니다.
 * LLM 하나가 Tool을 쓰는 단일 Agent이며, 여러 Agent가 서로 통신하는 구조(A2A 프로토콜)와는 다릅니다.
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

    public SearchResponse search(String message, String model) {
        return search(message, model, new ExecutionTrace());
    }

    /**
     * 실행 기록(trace)을 밖에서 넘겨받는 버전. 실행 중에도 trace를 읽어 진행 상황을 보여 줄 수 있습니다. (SearchJobService)
     */
    public SearchResponse search(String message, String model, ExecutionTrace trace) {
        ToolResultCapture.getAndClear(); // 이전 요청의 기록이 남아 있지 않도록 비운다
        ToolResultCapture.bind(trace);   // Tool이 실행되는 즉시 TOOL 단계로 기록 (화면에서 실시간으로 보임)

        long start = System.nanoTime();
        try {
            String answer = chatClientProvider.get(model).prompt()
                    .system(systemPrompt())
                    .user(DateContext.annotate(message)) // 날짜 표현 옆에 코드가 계산한 날짜를 붙여 보낸다
                    // 항공편 검색 Tool 3개만 넘긴다. 공항·항공사 조회 Tool(AirportInfoTool, AirlineInfoTool)은
                    // 검색 Tool이 한글 공항 이름을 직접 받으므로 필요 없고, 넘기면 LLM이 매번 읽어야 할 Tool 설명만 늘어난다
                    .tools(flightSearchTool)
                    .call()
                    .content();

            // Tool 단계는 실행될 때 이미 기록됐다. LLM 호출 구간(Tool 실행을 감싸는 전체 구간)을 마지막에 남긴다
            List<ToolCall> calls = ToolResultCapture.getAndClear();
            trace.record(Kind.LLM, ChatClientProvider.normalize(model),
                    "Tool 선택과 답변 작성", "Tool " + calls.size() + "회 호출 후 답변", start);

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
            ToolResultCapture.getAndClear(); // 실패 전까지 실행된 Tool은 이미 기록됐다. ThreadLocal만 비운다
            trace.record(Kind.LLM, ChatClientProvider.normalize(model), "Tool 선택과 답변 작성",
                    "실패: " + ChatClientProvider.describeError(e), start);
            return SearchResponse.fail(PATTERN, ChatClientProvider.describeError(e), trace);
        }
    }


    private String systemPrompt() {
        return """
                당신은 국내선 항공편 검색 도우미입니다. 반드시 한국어로 답변하세요.
                항공편 정보는 반드시 Tool을 호출해서 얻고, Tool 결과에 없는 내용은 지어내지 마세요.
                규칙:
                - 날짜는 "2026-06-25" 형식으로 Tool에 전달합니다.
                - 시간은 24시간 형식("14:00")으로 전달합니다.
                - 공항은 한글 이름 그대로 전달합니다. (예: "광주", "제주")
                - "내일", "이번 주 금요일" 같은 날짜는 계산하지 말고 아래 날짜표에서 찾습니다.
                - 답변은 항공편 수와 가장 저렴한 항공편을 중심으로 2~3문장으로 짧게 요약합니다. 항공편 목록을 나열하지 마세요.

                %s
                """.formatted(DateContext.describe());
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

}

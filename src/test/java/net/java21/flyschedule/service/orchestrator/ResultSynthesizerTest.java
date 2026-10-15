package net.java21.flyschedule.service.orchestrator;

import net.java21.flyschedule.dto.search.SearchCriteria;
import net.java21.flyschedule.dto.search.TaskResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Map;

import static net.java21.flyschedule.support.TestFlights.flight;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ResultSynthesizer 테스트")
class ResultSynthesizerTest {

    private final ResultSynthesizer synthesizer = new ResultSynthesizer();

    private final TaskResult gimpo = TaskResult.of(
            new SearchCriteria("김포", "제주", "2026-03-27", null, null, null, null),
            Map.of("대한항공", List.of(flight("KE1201", "대한항공", "0800", "0905", 52000))));
    private final TaskResult gwangju = TaskResult.of(
            new SearchCriteria("광주", "제주", "2026-03-27", null, null, null, null),
            Map.of("제주항공", List.of(flight("7C1402", "제주항공", "1030", "1120", 45000))));
    private final TaskResult empty = TaskResult.of(
            new SearchCriteria("여수", "제주", "2026-03-27", null, null, null, null), Map.of());

    @Test
    @DisplayName("LLM에게는 작업별 요약 한 줄씩만 넘기고, LLM의 답을 그대로 돌려준다")
    void sendsSummariesToLlm() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec spec = mock(ChatClient.ChatClientRequestSpec.class, RETURNS_SELF);
        ChatClient.CallResponseSpec call = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(spec);
        when(spec.call()).thenReturn(call);
        when(call.content()).thenReturn("광주 출발 7C1402편을 추천합니다.");

        String answer = synthesizer.synthesize("금요일 김포나 광주에서 제주", List.of(gimpo, gwangju), chatClient);

        assertThat(answer).isEqualTo("광주 출발 7C1402편을 추천합니다.");
        ArgumentCaptor<String> userMessage = ArgumentCaptor.forClass(String.class);
        verify(spec).user(userMessage.capture());
        assertThat(userMessage.getValue())
                .contains(gimpo.summaryLine())
                .contains(gwangju.summaryLine())
                .doesNotContain("OZ"); // 넘기지 않은 데이터는 없다
    }

    @Test
    @DisplayName("LLM 호출이 실패하면 코드로 만든 요약(최저가)을 돌려준다")
    void fallbackOnFailure() {
        ChatClient chatClient = mock(ChatClient.class);
        when(chatClient.prompt()).thenThrow(new IllegalStateException("연결 실패"));

        String answer = synthesizer.synthesize("질문", List.of(gimpo, gwangju, empty), chatClient);

        assertThat(answer).startsWith("3개 조건을 검색했습니다.").contains("7C1402");
    }

    @Test
    @DisplayName("모든 작업 결과가 비어 있으면 '항공편이 없다'고 요약한다")
    void fallbackWhenAllEmpty() {
        assertThat(synthesizer.fallbackSummary(List.of(empty)))
                .isEqualTo("1개 조건을 검색했지만 조건에 맞는 항공편이 없습니다.");
    }
}

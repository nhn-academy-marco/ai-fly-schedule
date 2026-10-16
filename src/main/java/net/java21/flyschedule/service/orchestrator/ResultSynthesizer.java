package net.java21.flyschedule.service.orchestrator;

import lombok.extern.slf4j.Slf4j;
import net.java21.flyschedule.dto.search.TaskResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Orchestrator의 종합 단계: Worker 결과를 모아 LLM이 비교·추천 답변을 씁니다.
 *
 * <p>LLM에게는 항공편 전체 목록이 아니라 작업별 요약 한 줄씩만 넘겨 프롬프트를 짧게 유지합니다.
 * LLM 호출이 실패하면 코드로 만든 요약을 대신 돌려줍니다.
 */
@Slf4j
@Component
public class ResultSynthesizer {

    public String synthesize(String message, List<TaskResult> results, ChatClient chatClient) {
        String summaries = results.stream()
                .map(TaskResult::summaryLine)
                .collect(Collectors.joining("\n- ", "- ", ""));

        try {
            String answer = chatClient.prompt()
                    .system("""
                            당신은 항공편 검색 결과를 비교해 추천하는 도우미입니다. 한국어로 3~4문장으로 답하세요.
                            아래 검색 결과에 있는 내용만 사용하고, 없는 항공편이나 가격을 지어내지 마세요.
                            """)
                    .user("""
                            사용자 요청: %s

                            작업별 검색 결과:
                            %s
                            """.formatted(message, summaries))
                    .call()
                    .content();
            if (StringUtils.hasText(answer)) {
                return answer;
            }
        } catch (Exception e) {
            log.warn("결과 종합 LLM 호출 실패, 코드 요약 사용: {}", e.getMessage());
        }
        return fallbackSummary(results);
    }

    /**
     * LLM 없이 만드는 요약: 전체 작업 중 가장 저렴한 항공편을 알려 줍니다.
     */
    String fallbackSummary(List<TaskResult> results) {
        return results.stream()
                .filter(result -> result.cheapest() != null)
                .min(Comparator.comparingInt(result -> result.cheapest().price()))
                .map(best -> "%d개 조건을 검색했습니다. 가장 저렴한 항공편은 %s"
                        .formatted(results.size(), best.summaryLine()))
                .orElse(results.size() + "개 조건을 검색했지만 조건에 맞는 항공편이 없습니다.");
    }
}

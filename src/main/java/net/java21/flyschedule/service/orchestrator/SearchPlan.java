package net.java21.flyschedule.service.orchestrator;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import net.java21.flyschedule.dto.search.SearchCriteria;

import java.util.List;

/**
 * Orchestrator(LLM)가 만든 작업 계획 (Structured Output 대상)
 */
public record SearchPlan(
        @JsonPropertyDescription("요청을 이렇게 나눈 이유를 한 문장으로")
        String reasoning,

        @JsonPropertyDescription("Worker에게 맡길 검색 작업 목록. 검색 조건이 하나면 1개")
        List<SearchCriteria> tasks
) {
}

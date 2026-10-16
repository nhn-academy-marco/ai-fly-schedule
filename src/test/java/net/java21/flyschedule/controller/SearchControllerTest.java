package net.java21.flyschedule.controller;

import net.java21.flyschedule.dto.search.ExecutionTrace;
import net.java21.flyschedule.dto.search.SearchResponse;
import net.java21.flyschedule.service.coordinator.FlightSearchCoordinator;
import net.java21.flyschedule.service.job.SearchJob;
import net.java21.flyschedule.service.job.SearchJobService;
import net.java21.flyschedule.service.orchestrator.FlightSearchOrchestrator;
import net.java21.flyschedule.service.toolcalling.ToolCallingSearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchController.class)
@DisplayName("SearchController 테스트")
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FlightSearchCoordinator coordinator;
    @MockitoBean
    private ToolCallingSearchService toolCallingService;
    @MockitoBean
    private FlightSearchOrchestrator orchestrator;
    @MockitoBean
    private SearchJobService jobService;

    private static SearchJob job(String id, String pattern, SearchResponse result) {
        SearchJob job = mock(SearchJob.class);
        when(job.id()).thenReturn(id);
        when(job.pattern()).thenReturn(pattern);
        when(job.trace()).thenReturn(new ExecutionTrace());
        when(job.result()).thenReturn(result);
        when(job.status()).thenReturn(result == null ? SearchJob.Status.RUNNING : SearchJob.Status.DONE);
        return job;
    }

    private static SearchResponse ok(String pattern) {
        return SearchResponse.success(pattern, "완료", List.of(), new ExecutionTrace());
    }

    @Test
    @DisplayName("POST /api/search/coordinator는 문장과 모델을 Coordinator에 넘긴다")
    void coordinator() throws Exception {
        when(coordinator.coordinate("내일 광주에서 제주", "ollama")).thenReturn(ok("coordinator"));

        mockMvc.perform(post("/api/search/coordinator").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"  내일 광주에서 제주  \",\"model\":\"ollama\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pattern").value("coordinator"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.trace").isArray());
    }

    @Test
    @DisplayName("POST /api/search/tool-calling은 Tool Calling 서비스로 간다")
    void toolCalling() throws Exception {
        when(toolCallingService.search(anyString(), anyString())).thenReturn(ok("tool-calling"));

        mockMvc.perform(post("/api/search/tool-calling").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"내일 광주에서 제주\",\"model\":\"gemini\"}"))
                .andExpect(jsonPath("$.pattern").value("tool-calling"));
        verify(toolCallingService).search("내일 광주에서 제주", "gemini");
    }

    @Test
    @DisplayName("POST /api/search/orchestrator는 Orchestrator로 간다")
    void orchestrator() throws Exception {
        when(orchestrator.orchestrate(anyString(), anyString())).thenReturn(ok("orchestrator"));

        mockMvc.perform(post("/api/search/orchestrator").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"금·토 김포·광주 → 제주\",\"model\":\"ollama\"}"))
                .andExpect(jsonPath("$.pattern").value("orchestrator"));
    }

    @Test
    @DisplayName("POST /api/search/{pattern}/jobs는 202와 작업 ID를 바로 돌려준다")
    void startJob() throws Exception {
        SearchJob job = job("job-1", "orchestrator", null);
        when(jobService.start("orchestrator", "금·토 김포·광주 → 제주", "ollama")).thenReturn(job);

        mockMvc.perform(post("/api/search/orchestrator/jobs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"금·토 김포·광주 → 제주\",\"model\":\"ollama\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value("job-1"))
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    @DisplayName("GET /api/search/jobs/{id}는 끝난 작업의 결과를, 없는 작업은 404를 돌려준다")
    void getJob() throws Exception {
        SearchJob job = job("job-2", "coordinator", ok("coordinator"));
        when(jobService.find("job-2")).thenReturn(Optional.of(job));
        when(jobService.find("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/search/jobs/job-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.result.pattern").value("coordinator"));
        mockMvc.perform(get("/api/search/jobs/missing")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("빈 문장이면 서비스를 호출하지 않고 안내 메시지로 실패한다")
    void blankMessage() throws Exception {
        mockMvc.perform(post("/api/search/coordinator").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"   \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("검색할 문장을 입력해 주세요."));
        verifyNoInteractions(coordinator, toolCallingService, orchestrator);
    }
}

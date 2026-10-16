package net.java21.flyschedule.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WebController.class)
@DisplayName("WebController 테스트")
class WebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("패턴 비교 화면을 보여 주고, GitHub 저장소와 교재 링크가 있다")
    void indexHasRepositoryLinks() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Agent 패턴 비교 실습")))
                .andExpect(content().string(containsString("href=\"https://github.com/nhn-academy-marco/ai-fly-schedule\"")))
                .andExpect(content().string(containsString("docs/README.md")))
                // 세 패턴 모두 작업 API로 실행한다 (프록시 타임아웃 회피)
                .andExpect(content().string(containsString("/api/search/${pattern}/jobs")));
    }
}

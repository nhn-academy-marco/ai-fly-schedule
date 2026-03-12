package com.nhnacademy.flyschedule.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 웹 컨트롤러
 *
 * Thymeleaf 템플릿을 렌더링하는 UI를 제공합니다.
 */
@Controller
public class WebController {

    /**
     * 메인 페이지
     *
     * @return 뷰 이름
     */
    @GetMapping("/")
    public String index() {
        return "index";
    }

    /**
     * MCP 테스트 페이지
     *
     * @return 뷰 이름
     */
    @GetMapping("/mcp-test")
    public String mcpTest() {
        return "mcp-test";
    }

    /**
     * 자연어 검색 페이지
     *
     * @return 뷰 이름
     */
    @GetMapping("/nl-search")
    public String nlSearch() {
        return "nl-search";
    }
}

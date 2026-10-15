package net.java21.flyschedule.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 웹 화면 컨트롤러
 */
@Controller
public class WebController {

    /**
     * 패턴 비교 화면
     */
    @GetMapping("/")
    public String index() {
        return "index";
    }
}

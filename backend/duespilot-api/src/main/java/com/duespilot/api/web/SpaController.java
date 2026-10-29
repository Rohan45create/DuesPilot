package com.duespilot.api.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller to forward client-side SPA routes to index.html
 * so HTML5 client routing works seamlessly on page refreshes.
 */
@Controller
public class SpaController {

    @GetMapping(value = {
        "/cases",
        "/cases/**",
        "/review",
        "/review/**",
        "/benchmarks",
        "/benchmarks/**",
        "/models",
        "/models/**"
    })
    public String forwardSpaRoutes() {
        return "forward:/index.html";
    }
}

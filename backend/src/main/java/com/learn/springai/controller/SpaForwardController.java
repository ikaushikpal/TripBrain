package com.learn.springai.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller to support Angular Single Page Application (SPA) HTML5 routing.
 * Direct requests to client-side routes (like /login, /dashboard) without file extensions
 * are forwarded to index.html so Angular router handles the route.
 */
@Controller
public class SpaForwardController {

    @GetMapping(value = {"/{path:[^\\.]*}", "/*/{path:[^\\.]*}"})
    public String forward() {
        return "forward:/index.html";
    }
}

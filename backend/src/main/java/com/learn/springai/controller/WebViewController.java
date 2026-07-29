package com.learn.springai.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class WebViewController {

    @RequestMapping(value = {
        "/",
        "/dashboard",
        "/gallery",
        "/admin",
        "/auth/**",
        "/share/**"
    })
    public String forward() {
        return "forward:/index.html";
    }
}

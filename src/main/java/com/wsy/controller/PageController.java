package com.wsy.controller;

import com.wsy.common.AuthSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.HttpServletRequest;

@Controller
public class PageController {

    @Autowired
    private AuthSupport authSupport;

    @GetMapping("/")
    public String index() {
        return "forward:/index.html";
    }

    @GetMapping("/main")
    public String main(HttpServletRequest request) {
        try {
            authSupport.requireLogin(request);
        } catch (Exception ex) {
            return "redirect:/index.html";
        }
        return "forward:/console.html";
    }
}

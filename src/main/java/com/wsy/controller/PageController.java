package com.wsy.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

@Controller
public class PageController {

    @GetMapping("/")
    public String index() {
        return "forward:/index.html";
    }

    @GetMapping("/main")
    public String main(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        boolean hasLoginCookie = false;
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("wms_user_id".equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                    hasLoginCookie = true;
                    break;
                }
            }
        }
        if (!hasLoginCookie) {
            return "redirect:/index.html";
        }
        return "forward:/console.html";
    }
}

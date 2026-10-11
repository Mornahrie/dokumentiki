package com.example.kursach.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebController {

    @GetMapping("/login")
    public String loginPage() {
        return "login"; // templates/login.html
    }

    @GetMapping({"/", "/main"})
    public String mainPage() {
        return "main"; // templates/main.html
    }

    @GetMapping("/documents/create")
    public String createDocumentPage() {
        return "create-document"; // templates/create-document.html
    }
}
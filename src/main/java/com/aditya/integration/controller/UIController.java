package com.aditya.integration.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class UIController {

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("message", "Welcome to the Integration Tool");
        return "index";
    }

}
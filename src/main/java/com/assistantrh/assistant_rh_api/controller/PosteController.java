package com.assistantrh.assistant_rh_api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PosteController {

    @GetMapping("/api/test")
    public String test() {
        return "Assistant RH API fonctionne !";
    }
}
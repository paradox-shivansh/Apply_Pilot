package org.dreamers.aipilot.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/health")
@RestController
public class health_controller {
    @GetMapping
    public String health_check() {
        return "OK";
    }
}

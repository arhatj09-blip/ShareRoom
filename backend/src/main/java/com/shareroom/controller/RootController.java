package com.shareroom.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class RootController {
    @GetMapping("/")
    public Map<String, String> root() {
        return Map.of(
                "service", "ShareRoom API",
                "status", "running",
                "health", "/api/rooms/health",
                "frontend", "Use the deployed Vercel URL to open ShareRoom."
        );
    }
}

package com.example.demo.dto;

public class AuthDTO {
    public record LoginRequest(String username, String password) {}
    public record LoginResponse(String message, Long userId, String username) {}
}

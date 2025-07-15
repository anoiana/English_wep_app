package com.example.demo.controllers;

import com.example.demo.dto.AuthDTO;
import com.example.demo.entities.User;
import com.example.demo.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody User user) {
        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            return ResponseEntity.badRequest().body("Username already exists");
        }
        userRepository.save(user);
        return ResponseEntity.ok("Registration successful");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthDTO.LoginRequest loginRequest) {
        User user = userRepository.findByUsername(loginRequest.username())
                .orElse(null);
        if (user == null || !loginRequest.password().equals(user.getPassword())) {
            return ResponseEntity.status(401).body("Invalid credentials");
        }
        AuthDTO.LoginResponse response = new AuthDTO.LoginResponse("Login successful", user.getId(), user.getUsername());
        return ResponseEntity.ok(response);
    }
}
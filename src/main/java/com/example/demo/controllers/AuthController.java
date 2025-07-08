package com.example.demo.controllers;

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

    // --- CÁC RECORD ĐỂ TRUYỀN DỮ LIỆU ---
    // Dữ liệu nhận vào khi login
    record LoginRequest(String username, String password) {}
    // Dữ liệu trả về khi login thành công
    record LoginResponse(String message, Long userId, String username) {}

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody User user) {
        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            return ResponseEntity.badRequest().body("Username already exists");
        }
        userRepository.save(user);
        return ResponseEntity.ok("Registration successful");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        User user = userRepository.findByUsername(loginRequest.username())
                .orElse(null);

        // Nếu không tìm thấy user hoặc sai mật khẩu
        if (user == null || !loginRequest.password().equals(user.getPassword())) {
            return ResponseEntity.status(401).body("Invalid credentials");
        }

        // Đăng nhập thành công, tạo response chứa userId và username
        LoginResponse response = new LoginResponse("Login successful", user.getId(), user.getUsername());
        return ResponseEntity.ok(response);
    }
}
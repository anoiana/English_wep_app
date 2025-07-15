package com.example.demo.controllers;

// src/main/java/com/example/demo/controllers/TranslationController.java

import com.example.demo.services.TranslationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/translate")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class TranslationController {

    @Autowired
    private TranslationService translationService;

    @GetMapping("/{word}")
    public ResponseEntity<String> getTranslation(@PathVariable String word) {
        try {
            String translation = translationService.translateWord(word);
            return ResponseEntity.ok("\"" + translation + "\"");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("\"Lỗi khi dịch từ.\"");
        }
    }
}
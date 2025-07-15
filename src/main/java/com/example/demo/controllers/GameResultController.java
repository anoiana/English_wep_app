package com.example.demo.controllers;

import com.example.demo.dto.GameDTO;
import com.example.demo.entities.GameResult;
import com.example.demo.repositories.GameResultRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;



@RestController
@RequestMapping("/api/game-results")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class GameResultController {

    @Autowired
    private GameResultRepository gameResultRepository;

    @PutMapping("/{id}")
    public ResponseEntity<GameResult> updateGameResult(@PathVariable Long id, @RequestBody GameDTO.GameResultUpdateDTO resultDTO) {
        GameResult existing = gameResultRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Game result not found"));
        existing.setCorrectCount(resultDTO.correctCount());
        existing.setWrongCount(resultDTO.wrongCount());
        existing.setWrongAnswers(resultDTO.wrongAnswers());
        return ResponseEntity.ok(gameResultRepository.save(existing));
    }

    @GetMapping("/wrong/{userId}")
    public List<GameResult> getWrongAnswers(@PathVariable Long userId) {
        return gameResultRepository.findAll().stream()
                .filter(r -> r.getUserId().equals(userId) && r.getWrongCount() > 0)
                .collect(Collectors.toList());
    }

}
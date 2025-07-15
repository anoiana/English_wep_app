package com.example.demo.controllers;

import com.example.demo.dto.VocabularyDTO.*;
import com.example.demo.entities.Definition;
import com.example.demo.entities.Folder;
import com.example.demo.entities.Meaning;
import com.example.demo.entities.Vocabulary;
import com.example.demo.repositories.DefinitionRepository;
import com.example.demo.repositories.FolderRepository;
import com.example.demo.repositories.MeaningRepository;
import com.example.demo.repositories.VocabularyRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/vocabularies")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class VocabularyController {

    @Autowired private VocabularyRepository vocabularyRepository;
    @Autowired private FolderRepository folderRepository;
    @Autowired private MeaningRepository meaningRepository;
    @Autowired private DefinitionRepository definitionRepository;


    @PostMapping
    @Transactional
    public ResponseEntity<?> createVocabulary(@RequestBody DictionaryEntryDTO dto) {
        // KIỂM TRA GIỚI HẠN
        if (vocabularyRepository.countByFolderId(dto.folderId()) >= 100) {
            return ResponseEntity.badRequest().body("Lỗi: Mỗi thư mục chỉ được chứa tối đa 100 từ vựng.");
        }

        // ... Code tạo từ vựng còn lại giữ nguyên ...
        // 1. Tìm Folder để gán từ vựng vào
        Folder folder = folderRepository.findById(dto.folderId())
                .orElseThrow(() -> new RuntimeException("Folder not found with id: " + dto.folderId()));
        // ... (phần còn lại của hàm)
        Vocabulary vocab = new Vocabulary();
        vocab.setWord(dto.word());
        vocab.setPhoneticText(dto.phoneticText());
        vocab.setAudioUrl(dto.audioUrl());
        vocab.setFolder(folder);
        vocab.setUserDefinedMeaning(dto.userDefinedMeaning());
        vocab.setUserImageBase64(dto.userImageBase64 ());
        vocab.setMeanings(new ArrayList<>());

        Vocabulary savedVocab = vocabularyRepository.save(vocab);

        if (dto.meanings() != null) {
            for (MeaningDTO meaningDto : dto.meanings()) {
                Meaning meaning = new Meaning();
                meaning.setPartOfSpeech(meaningDto.partOfSpeech());
                meaning.setSynonyms(meaningDto.synonyms());
                meaning.setAntonyms(meaningDto.antonyms());
                meaning.setVocabulary(savedVocab);
                meaning.setDefinitions(new ArrayList<>());
                Meaning savedMeaning = meaningRepository.save(meaning);

                if (meaningDto.definitions() != null) {
                    for (DefinitionDTO defDto : meaningDto.definitions()) {
                        Definition definition = new Definition();
                        definition.setDefinition(defDto.definition());
                        definition.setExample(defDto.example());
                        definition.setMeaning(savedMeaning);
                        definitionRepository.save(definition);
                    }
                }
            }
        }
        return ResponseEntity.ok(vocabularyRepository.findById(savedVocab.getId()).get());
    }

    @GetMapping("/folder/{folderId}")
    public Page<Vocabulary> getVocabulariesByFolder(
            @PathVariable Long folderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(defaultValue = "") String search) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("word").ascending());
        return vocabularyRepository.findByFolderIdAndWordContainingIgnoreCase(folderId, search, pageable);
    }


    @PutMapping("/{vocabularyId}/image")
    public ResponseEntity<Vocabulary> updateVocabularyImage(
            @PathVariable Long vocabularyId,
            @RequestBody String imageUrl) {

        return vocabularyRepository.findById(vocabularyId)
                .map(existingVocab -> {
                    String cleanImageUrl = imageUrl.replaceAll("^\"|\"$", "");
                    existingVocab.setUserImageBase64(cleanImageUrl);
                    Vocabulary updatedVocab = vocabularyRepository.save(existingVocab);
                    return ResponseEntity.ok(updatedVocab);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{vocabularyId}")
    public ResponseEntity<Vocabulary> updateVocabulary(
            @PathVariable Long vocabularyId,
            @RequestBody VocabularyUserUpdateDTO updateDTO) {

        return vocabularyRepository.findById(vocabularyId)
                .map(existingVocab -> {
                    existingVocab.setUserDefinedMeaning(updateDTO.userDefinedMeaning());
                    existingVocab.setUserImageBase64(updateDTO.userImageBase64());
                    Vocabulary updatedVocab = vocabularyRepository.save(existingVocab);
                    return ResponseEntity.ok(updatedVocab);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{vocabularyId}")
    public ResponseEntity<String> deleteVocabulary(@PathVariable Long vocabularyId) {
        if (!vocabularyRepository.existsById(vocabularyId)) {
            return ResponseEntity.status(404).body("Vocabulary not found with id: " + vocabularyId);
        }
        vocabularyRepository.deleteById(vocabularyId);
        return ResponseEntity.ok("Vocabulary with id " + vocabularyId + " has been deleted successfully.");
    }

    @PostMapping("/batch-delete")
    @Transactional
    public ResponseEntity<String> deleteBatchVocabularies(@RequestBody BatchDeleteRequestDTO request) {
        List<Long> vocabularyIds = request.vocabularyIds();
        if (vocabularyIds == null || vocabularyIds.isEmpty()) {
            return ResponseEntity.badRequest().body("Vocabulary IDs list cannot be empty.");
        }
        List<Long> meaningIds = meaningRepository.findMeaningIdsByVocabularyIds(vocabularyIds);
        if (!meaningIds.isEmpty()) {
            definitionRepository.deleteByMeaningIds(meaningIds);
            meaningRepository.deleteAllByIdInBatch(meaningIds);
        }
        vocabularyRepository.deleteAllByIdInBatch(vocabularyIds);

        return ResponseEntity.ok("Successfully deleted " + vocabularyIds.size() + " vocabularies.");
    }

    @PutMapping("/batch-move")
    @Transactional
    public ResponseEntity<String> moveBatchVocabularies(@RequestBody BatchMoveRequestDTO request) {
        if (request.vocabularyIds() == null || request.vocabularyIds().isEmpty()) {
            return ResponseEntity.badRequest().body("Vocabulary IDs list cannot be empty.");
        }
        Folder targetFolder = folderRepository.findById(request.targetFolderId())
                .orElseThrow(() -> new RuntimeException("Target folder not found with id: " + request.targetFolderId()));
        List<Vocabulary> vocabulariesToMove = vocabularyRepository.findAllById(request.vocabularyIds());
        for (Vocabulary vocab : vocabulariesToMove) {
            vocab.setFolder(targetFolder);
        }
        vocabularyRepository.saveAll(vocabulariesToMove);
        return ResponseEntity.ok("Successfully moved " + vocabulariesToMove.size() + " vocabularies.");
    }
}
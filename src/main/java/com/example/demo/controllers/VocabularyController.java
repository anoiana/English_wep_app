package com.example.demo.controllers;

// Import các DTO từ package dto
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

    // DTO cho việc cập nhật chỉ các trường người dùng định nghĩa


    // === API 1: TẠO TỪ VỰNG MỚI TỪ DỮ LIỆU TỪ ĐIỂN ===
    @PostMapping
    @Transactional // Bắt buộc khi thao tác trên nhiều bảng liên quan
    public ResponseEntity<Vocabulary> createVocabulary(@RequestBody DictionaryEntryDTO dto) {
        // 1. Tìm Folder để gán từ vựng vào
        Folder folder = folderRepository.findById(dto.folderId())
                .orElseThrow(() -> new RuntimeException("Folder not found with id: " + dto.folderId()));

        // 2. Tạo đối tượng Vocabulary gốc
        Vocabulary vocab = new Vocabulary();
        vocab.setWord(dto.word());
        vocab.setPhoneticText(dto.phoneticText());
        vocab.setAudioUrl(dto.audioUrl());
        vocab.setFolder(folder);
        vocab.setUserDefinedMeaning(dto.userDefinedMeaning()); // Lưu nghĩa người dùng nhập
        vocab.setUserImageBase64(dto.userImageBase64 ()); // Lưu ảnh người dùng nhập
        vocab.setMeanings(new ArrayList<>()); // Khởi tạo list trước khi lưu

        // Lưu Vocabulary để lấy ID cho các mối quan hệ
        Vocabulary savedVocab = vocabularyRepository.save(vocab);

        // 3. Lặp qua các Meanings từ DTO để tạo và lưu entity Meaning
        if (dto.meanings() != null) {
            for (MeaningDTO meaningDto : dto.meanings()) {
                Meaning meaning = new Meaning();
                meaning.setPartOfSpeech(meaningDto.partOfSpeech());
                meaning.setSynonyms(meaningDto.synonyms());
                meaning.setAntonyms(meaningDto.antonyms());
                meaning.setVocabulary(savedVocab); // Thiết lập quan hệ ngược
                meaning.setDefinitions(new ArrayList<>());
                Meaning savedMeaning = meaningRepository.save(meaning);

                // 4. Lặp qua các Definitions để tạo và lưu entity Definition
                if (meaningDto.definitions() != null) {
                    for (DefinitionDTO defDto : meaningDto.definitions()) {
                        Definition definition = new Definition();
                        definition.setDefinition(defDto.definition());
                        definition.setExample(defDto.example());
                        definition.setMeaning(savedMeaning); // Thiết lập quan hệ ngược
                        definitionRepository.save(definition);
                    }
                }
            }
        }

        // Trả về đối tượng Vocabulary đã được lưu đầy đủ
        return ResponseEntity.ok(vocabularyRepository.findById(savedVocab.getId()).get());
    }

    // === API 2: LẤY TẤT CẢ TỪ VỰNG CỦA MỘT FOLDER ===
    @GetMapping("/folder/{folderId}")
    public List<Vocabulary> getVocabulariesByFolder(@PathVariable Long folderId) {
        // Trả về trực tiếp List<Vocabulary>. Jackson sẽ xử lý việc chuyển sang JSON
        // và các annotation @Json...Reference sẽ chống lỗi đệ quy.
        // Tên phương thức đúng trong repository phải là findByFolder_Id
        return vocabularyRepository.findByFolderId(folderId);
    }


    @PutMapping("/{vocabularyId}/image")
    public ResponseEntity<Vocabulary> updateVocabularyImage(
            @PathVariable Long vocabularyId,
            @RequestBody String imageUrl) {

        return vocabularyRepository.findById(vocabularyId)
                .map(existingVocab -> {
                    // Xóa dấu " ở đầu và cuối chuỗi nếu có
                    String cleanImageUrl = imageUrl.replaceAll("^\"|\"$", "");
                    existingVocab.setUserImageBase64(cleanImageUrl);

                    Vocabulary updatedVocab = vocabularyRepository.save(existingVocab);
                    return ResponseEntity.ok(updatedVocab);
                })
                .orElse(ResponseEntity.notFound().build());
    }
//    zxczxc
    // === API 3: SỬA THÔNG TIN DO NGƯỜI DÙNG ĐỊNH NGHĨA ===
    @PutMapping("/{vocabularyId}")
    public ResponseEntity<Vocabulary> updateVocabulary(
            @PathVariable Long vocabularyId,
            @RequestBody VocabularyUserUpdateDTO updateDTO) {

        return vocabularyRepository.findById(vocabularyId)
                .map(existingVocab -> {
                    // Chỉ cập nhật các trường do người dùng định nghĩa
                    existingVocab.setUserDefinedMeaning(updateDTO.userDefinedMeaning());
                    existingVocab.setUserImageBase64(updateDTO.userImageBase64());

                    Vocabulary updatedVocab = vocabularyRepository.save(existingVocab);
                    return ResponseEntity.ok(updatedVocab);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // === API 4: XÓA MỘT TỪ VỰNG ===
    @DeleteMapping("/{vocabularyId}")
    public ResponseEntity<String> deleteVocabulary(@PathVariable Long vocabularyId) {
        if (!vocabularyRepository.existsById(vocabularyId)) {
            return ResponseEntity.status(404).body("Vocabulary not found with id: " + vocabularyId);
        }

        // Nhờ `cascade = CascadeType.ALL`, các Meaning và Definition liên quan sẽ tự động bị xóa.
        vocabularyRepository.deleteById(vocabularyId);

        return ResponseEntity.ok("Vocabulary with id " + vocabularyId + " has been deleted successfully.");
    }

    @PostMapping("/batch-delete")
    @Transactional // Rất quan trọng! Đảm bảo tất cả các thao tác xóa diễn ra trong cùng 1 giao dịch
    public ResponseEntity<String> deleteBatchVocabularies(@RequestBody BatchDeleteRequestDTO request) {
        List<Long> vocabularyIds = request.vocabularyIds();

        if (vocabularyIds == null || vocabularyIds.isEmpty()) {
            return ResponseEntity.badRequest().body("Vocabulary IDs list cannot be empty.");
        }

        // Bước 1: Tìm tất cả các ID của Meaning liên quan đến các Vocabulary sắp xóa.
        List<Long> meaningIds = meaningRepository.findMeaningIdsByVocabularyIds(vocabularyIds);

        // Chỉ thực hiện xóa các bảng con nếu có meaning liên quan
        if (!meaningIds.isEmpty()) {
            // Bước 2: Dùng các ID Meaning tìm được để xóa tất cả các Definition liên quan.
            // Đây là việc xóa tầng "con cháu" (Definition) trước.
            definitionRepository.deleteByMeaningIds(meaningIds);

            // Bước 3: Sau khi "con cháu" đã được xóa, tiến hành xóa tầng "con" (Meaning).
            // Chúng ta dùng deleteAllByIdInBatch vì nó hiệu quả và không cần cascade nữa.
            meaningRepository.deleteAllByIdInBatch(meaningIds);
        }

        // Bước 4: Cuối cùng, sau khi tất cả các bảng phụ thuộc đã được dọn dẹp,
        // xóa tầng "cha" (Vocabulary).
        vocabularyRepository.deleteAllByIdInBatch(vocabularyIds);

        return ResponseEntity.ok("Successfully deleted " + vocabularyIds.size() + " vocabularies.");
    }

    // === API 6: CHUYỂN HÀNG LOẠT TỪ VỰNG SANG FOLDER KHÁC ===
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
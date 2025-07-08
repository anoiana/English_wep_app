package com.example.demo.controllers;

import com.example.demo.dto.FolderResponseDTO;
import com.example.demo.entities.Folder;
import com.example.demo.entities.User;
import com.example.demo.repositories.FolderRepository;
import com.example.demo.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.example.demo.dto.FolderDTO.*;


@RestController
@RequestMapping("/api/folders")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class FolderController {

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private UserRepository userRepository;

    // 1. API Tạo Folder (POST) - Sử dụng DTO đã import
    @PostMapping
    public ResponseEntity<FolderResponseDTO> createFolder(@RequestBody FolderCreationDTO folderDTO) { // Không cần thay đổi ở đây
        User user = userRepository.findById(folderDTO.userId())
                .orElseThrow(() -> new RuntimeException("Error: User not found with id " + folderDTO.userId()));

        Folder newFolder = new Folder();
        newFolder.setName(folderDTO.name());
        newFolder.setUser(user);

        Folder savedFolder = folderRepository.save(newFolder);

        // Khi tạo mới, chưa có từ vựng nào nên count là 0
        FolderResponseDTO responseDTO = new FolderResponseDTO(
                savedFolder.getId(),
                savedFolder.getName(),
                savedFolder.getUser().getId(),
                0L // count là 0
        );

        return ResponseEntity.ok(responseDTO);
    }

    // 2. API Lấy Folders theo User (GET) - Sử dụng DTO đã import
    @GetMapping("/user/{userId}")
    public List<FolderResponseDTO> getFoldersByUser(@PathVariable Long userId) { // Không cần thay đổi ở đây
        return folderRepository.findFoldersWithVocabularyCountByUserId(userId);
    }

    // === 3. API Sửa tên Folder (PUT) - Sử dụng DTO đã import ===
    @PutMapping("/{folderId}")
    public ResponseEntity<FolderResponseDTO> updateFolder(
                                                           @PathVariable Long folderId,
                                                           @RequestBody FolderUpdateDTO updateDTO) {

        return folderRepository.findById(folderId)
                .map(existingFolder -> {
                    existingFolder.setName(updateDTO.newName());
                    Folder updatedFolder = folderRepository.save(existingFolder);

                    // Lấy số lượng từ vựng hiện có của folder này
                    long vocabCount = existingFolder.getVocabularies() != null ? existingFolder.getVocabularies().size() : 0;

                    FolderResponseDTO responseDTO = new FolderResponseDTO(
                            updatedFolder.getId(),
                            updatedFolder.getName(),
                            updatedFolder.getUser().getId(),
                            vocabCount
                    );
                    return ResponseEntity.ok(responseDTO);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // === 4. API Xóa Folder (DELETE) - Không cần thay đổi ===
    @DeleteMapping("/{folderId}")
    public ResponseEntity<String> deleteFolder(@PathVariable Long folderId) {
        if (!folderRepository.existsById(folderId)) {
            return ResponseEntity.notFound().build();
        }
        folderRepository.deleteById(folderId);
        return ResponseEntity.ok("Folder with id " + folderId + " has been deleted successfully.");
    }
}
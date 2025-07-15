package com.example.demo.controllers;

import com.example.demo.dto.FolderResponseDTO;
import com.example.demo.entities.Folder;
import com.example.demo.entities.User;
import com.example.demo.repositories.FolderRepository;
import com.example.demo.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

    @PostMapping
    public ResponseEntity<?> createFolder(@RequestBody FolderCreationDTO folderDTO) {
        if (folderRepository.countByUserId(folderDTO.userId()) >= 100) {
            return ResponseEntity.badRequest().body("Lỗi: Mỗi người dùng chỉ được tạo tối đa 100 thư mục.");
        }
        User user = userRepository.findById(folderDTO.userId())
                .orElseThrow(() -> new RuntimeException("Error: User not found with id " + folderDTO.userId()));
        Folder newFolder = new Folder();
        newFolder.setName(folderDTO.name());
        newFolder.setUser(user);
        Folder savedFolder = folderRepository.save(newFolder);
        FolderResponseDTO responseDTO = new FolderResponseDTO(
                savedFolder.getId(),
                savedFolder.getName(),
                savedFolder.getUser().getId(),
                0L
        );
        return ResponseEntity.ok(responseDTO);
    }

    @GetMapping("/user/{userId}")
    public Page<FolderResponseDTO> getFoldersByUser(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(defaultValue = "") String search) { // Thêm param tìm kiếm
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        return folderRepository.findWithSearchAndPagination(userId, search, pageable);
    }



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

    @DeleteMapping("/{folderId}")
    public ResponseEntity<String> deleteFolder(@PathVariable Long folderId) {
        if (!folderRepository.existsById(folderId)) {
            return ResponseEntity.notFound().build();
        }
        folderRepository.deleteById(folderId);
        return ResponseEntity.ok("Folder with id " + folderId + " has been deleted successfully.");
    }
}
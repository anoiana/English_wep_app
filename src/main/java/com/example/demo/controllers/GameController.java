// Đường dẫn file: src/main/java/com/example/demo/controllers/GameController.java

package com.example.demo.controllers;

import com.example.demo.dto.VocabularyDTO;
import com.example.demo.entities.GameResult;
import com.example.demo.entities.Meaning;
import com.example.demo.entities.Vocabulary;
import com.example.demo.repositories.GameResultRepository;
import com.example.demo.repositories.VocabularyRepository;
import com.example.demo.services.GrammarService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/game")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class GameController {

    // === CÁC DTO ĐƯỢC ĐỊNH NGHĨA BÊN TRONG CONTROLLER ĐỂ DỄ QUẢN LÝ ===

    /**
     * Dữ liệu client gửi lên để bắt đầu một lượt chơi.
     */
    public record GameStartRequestDTO(Long userId, Long folderId, String gameType, String subType) {}
    public record SentenceCheckRequestDTO(Integer vocabularyId, String userAnswer) {}

    // === RESPONSE DTOs CHO CÁC LOẠI GAME SESSION ===
    public record GameSessionDTO(Long gameResultId, List<VocabularyDetailDTO> vocabularies) {}
    public record QuizSessionDTO(Long gameResultId, List<QuizQuestionDTO> questions) {}
    public record ReverseQuizSessionDTO(Long gameResultId, List<ReverseQuizQuestionDTO> questions) {}
    public record SentenceCheckResponseDTO(boolean isCorrect, String feedback) {}

    // === DTOs CHO CÁC THÀNH PHẦN BÊN TRONG SESSION ===

    /**
     * DTO cho một câu hỏi trắc nghiệm Anh -> Việt.
     * Chứa đầy đủ thông tin cần thiết để hiển thị.
     */
    public record QuizQuestionDTO(
            Long vocabularyId,
            String word,
            String phoneticText,
            String partOfSpeech,
            List<String> options,
            String correctAnswer,
            String userImageBase64
    ) {}

    /**
     * DTO cho một câu hỏi trắc nghiệm Việt -> Anh.
     * Chứa đầy đủ thông tin cần thiết để hiển thị.
     */
    public record ReverseQuizQuestionDTO(
            Long vocabularyId,
            String userDefinedMeaning,
            String phoneticText,
            String partOfSpeech,
            List<String> options,
            String correctAnswer,
            String userImageBase64
    ) {}

    /**
     * DTO chứa thông tin chi tiết đầy đủ của một từ vựng,
     * dùng cho các game như Flashcard, Writing, Sentence.
     */
    public record VocabularyDetailDTO(
            Long id,
            String word,
            String phoneticText,
            String audioUrl,
            String userDefinedMeaning,
            String userImageBase64,
            List<Meaning> meanings
    ) {}

    @Autowired private VocabularyRepository vocabularyRepository;
    @Autowired private GameResultRepository gameResultRepository;
    @Autowired private GrammarService grammarService;

    /**
     * Endpoint để bắt đầu một lượt chơi mới.
     * Dựa vào 'gameType' để trả về cấu trúc dữ liệu phù hợp.
     * Đối với Flashcard, sẽ trả về dữ liệu chi tiết.
     */
    @PostMapping("/start")
    public ResponseEntity<?> startGame(@RequestBody GameStartRequestDTO request) {
        List<Vocabulary> vocabularies = vocabularyRepository.findByFolderId(request.folderId());

        if (vocabularies.isEmpty()) {
            return ResponseEntity.badRequest().body("Thư mục này không có từ vựng nào.");
        }
        if ("quiz".equals(request.gameType()) && vocabularies.size() < 4) {
            return ResponseEntity.badRequest().body("Cần ít nhất 4 từ vựng trong thư mục để chơi trắc nghiệm.");
        }

        // Tạo hoặc reset một bản ghi GameResult
        String fullGameType = request.gameType() + (request.subType() != null ? "_" + request.subType() : "");
        GameResult gameResult = gameResultRepository.findByUserIdAndFolderIdAndGameType(request.userId(), request.folderId(), fullGameType)
                .orElse(new GameResult());
        gameResult.setUserId(request.userId());
        gameResult.setFolderId(request.folderId());
        gameResult.setGameType(fullGameType);
        gameResult.setCorrectCount(0);
        gameResult.setWrongCount(0);
        gameResult.setWrongAnswers("[]");
        GameResult savedGameResult = gameResultRepository.save(gameResult);

        // Phân loại game để trả về đúng cấu trúc dữ liệu
        if ("quiz".equals(request.gameType())) {
            // Trả về dữ liệu cho game trắc nghiệm
            if ("vi_en".equals(request.subType())) {
                return ResponseEntity.ok(new ReverseQuizSessionDTO(savedGameResult.getId(), createReverseQuizQuestions(vocabularies)));
            } else {
                return ResponseEntity.ok(new QuizSessionDTO(savedGameResult.getId(), createQuizQuestions(vocabularies)));
            }
        } else {
            // Trả về dữ liệu chi tiết cho Flashcard và các game khác
            List<VocabularyDetailDTO> vocabDTOs = vocabularies.stream()
                    .map(this::convertToDetailDTO)
                    .collect(Collectors.toList());
            Collections.shuffle(vocabDTOs);
            return ResponseEntity.ok(new GameSessionDTO(savedGameResult.getId(), vocabDTOs));
        }
    }

    /**
     * Endpoint để chơi lại các câu đã trả lời sai ở lượt chơi trước.
     */
    @PostMapping("/retry-wrong")
    @Transactional
    public ResponseEntity<?> retryWrongAnswers(@RequestBody VocabularyDTO.GameRetryRequestDTO request) throws Exception {
        GameResult previousResult = gameResultRepository.findById(request.gameResultId())
                .orElseThrow(() -> new RuntimeException("GameResult not found with id: " + request.gameResultId()));

        ObjectMapper mapper = new ObjectMapper();
        List<Long> wrongVocabIds = mapper.readValue(previousResult.getWrongAnswers(), new TypeReference<>() {});

        if (wrongVocabIds.isEmpty()) {
            return ResponseEntity.badRequest().body("Không có từ nào sai để ôn tập lại.");
        }

        List<Vocabulary> wrongVocabularies = vocabularyRepository.findAllById(wrongVocabIds);

        // Xác định loại game gốc (loại bỏ các tiền tố "retry_")
        String baseGameType = getOriginalGameType(previousResult.getGameType());
        String originalGameType = baseGameType.split("_")[0];

        // Nếu là game trắc nghiệm và số từ sai < 4, thêm các từ khác để đủ lựa chọn
        if ("quiz".equals(originalGameType) && wrongVocabularies.size() < 4) {
            List<Vocabulary> allVocabInFolder = vocabularyRepository.findByFolderId(previousResult.getFolderId());
            allVocabInFolder.removeAll(wrongVocabularies);
            Collections.shuffle(allVocabInFolder);
            int needed = 4 - wrongVocabularies.size();
            for (int i = 0; i < needed && i < allVocabInFolder.size(); i++) {
                wrongVocabularies.add(allVocabInFolder.get(i));
            }
        }

        GameResult savedRetryResult = createNewRetryGameResult(previousResult);

        // Trả về dữ liệu tương ứng với loại game gốc
        if ("quiz".equals(originalGameType)) {
            if (baseGameType.endsWith("vi_en")) {
                return ResponseEntity.ok(new ReverseQuizSessionDTO(savedRetryResult.getId(), createReverseQuizQuestions(wrongVocabularies)));
            }
            return ResponseEntity.ok(new QuizSessionDTO(savedRetryResult.getId(), createQuizQuestions(wrongVocabularies)));
        } else {
            // Trả về dữ liệu chi tiết cho Flashcard và các game khác
            List<VocabularyDetailDTO> vocabDTOs = wrongVocabularies.stream()
                    .map(this::convertToDetailDTO)
                    .collect(Collectors.toList());
            Collections.shuffle(vocabDTOs);
            return ResponseEntity.ok(new GameSessionDTO(savedRetryResult.getId(), vocabDTOs));
        }
    }

    /**
     * Endpoint để kiểm tra ngữ pháp của một câu do người dùng nhập.
     */
    @PostMapping("/check-sentence")
    public ResponseEntity<?> checkWritingSentence(@RequestBody SentenceCheckRequestDTO request) {
        Vocabulary vocab = vocabularyRepository.findById(Long.valueOf(request.vocabularyId()))
                .orElseThrow(() -> new RuntimeException("Không tìm thấy từ vựng với id: " + request.vocabularyId()));

        String correctWord = vocab.getWord().toLowerCase();
        String userAnswer = request.userAnswer();

        // Logic kiểm tra ngữ pháp...
        if (!userAnswer.toLowerCase().contains(correctWord)) {
            return ResponseEntity.ok(new SentenceCheckResponseDTO(false, "Câu của bạn phải chứa từ khóa '" + vocab.getWord() + "'."));
        }
        if (!grammarService.isACompleteSentence(userAnswer)) {
            return ResponseEntity.ok(new SentenceCheckResponseDTO(false, "Đây dường như không phải là một câu hoàn chỉnh. Một câu cần có động từ."));
        }
        List<String> grammarErrors = grammarService.getGrammarMistakes(userAnswer);
        if (!grammarErrors.isEmpty()) {
            String feedback = "Câu có vẻ đúng cấu trúc nhưng vẫn còn lỗi ngữ pháp. Gợi ý: " + grammarErrors.get(0);
            return ResponseEntity.ok(new SentenceCheckResponseDTO(false, feedback));
        }

        return ResponseEntity.ok(new SentenceCheckResponseDTO(true, "Tuyệt vời! Câu của bạn rất hay."));
    }

    // === CÁC HÀM HELPER ===

    /**
     * Chuyển đổi một Vocabulary Entity sang VocabularyDetailDTO để trả về cho client.
     */
    private VocabularyDetailDTO convertToDetailDTO(Vocabulary vocab) {
        return new VocabularyDetailDTO(
                vocab.getId(),
                vocab.getWord(),
                vocab.getPhoneticText(),
                vocab.getAudioUrl(),
                vocab.getUserDefinedMeaning(),
                vocab.getUserImageBase64(),
                vocab.getMeanings() // @Transactional sẽ đảm bảo meanings được load
        );
    }

    /**
     * Lấy ra loại game gốc bằng cách loại bỏ các tiền tố "retry_".
     * Ví dụ: "retry_retry_quiz_en_vi" -> "quiz_en_vi"
     */
    private String getOriginalGameType(String fullGameType) {
        String baseGameType = fullGameType;
        while (baseGameType.startsWith("retry_")) {
            baseGameType = baseGameType.substring("retry_".length());
        }
        return baseGameType;
    }

    /**
     * Tạo một bản ghi GameResult mới cho lượt chơi lại.
     */
    private GameResult createNewRetryGameResult(GameResult previousResult) {
        GameResult retryGameResult = new GameResult();
        retryGameResult.setUserId(previousResult.getUserId());
        retryGameResult.setFolderId(previousResult.getFolderId());
        retryGameResult.setGameType("retry_" + previousResult.getGameType());
        retryGameResult.setCorrectCount(0);
        retryGameResult.setWrongCount(0);
        retryGameResult.setWrongAnswers("[]");
        return gameResultRepository.save(retryGameResult);
    }

    /**
     * Tạo danh sách câu hỏi cho game trắc nghiệm Anh -> Việt.
     */
    private List<QuizQuestionDTO> createQuizQuestions(List<Vocabulary> allVocabularies) {
        List<Vocabulary> shuffledList = new ArrayList<>(allVocabularies);
        Collections.shuffle(shuffledList);

        List<String> allMeanings = shuffledList.stream()
                .map(Vocabulary::getUserDefinedMeaning)
                .filter(m -> m != null && !m.isEmpty())
                .collect(Collectors.toList());

        return shuffledList.stream().map(correctVocab -> {
            List<String> options = new ArrayList<>();
            options.add(correctVocab.getUserDefinedMeaning());

            List<String> wrongMeanings = new ArrayList<>(allMeanings);
            wrongMeanings.remove(correctVocab.getUserDefinedMeaning());
            Collections.shuffle(wrongMeanings);

            for (int i = 0; i < 3 && i < wrongMeanings.size(); i++) {
                options.add(wrongMeanings.get(i));
            }
            Collections.shuffle(options);
            String partOfSpeech = correctVocab.getMeanings().isEmpty() ? "" : correctVocab.getMeanings().get(0).getPartOfSpeech();
            return new QuizQuestionDTO(
                    correctVocab.getId(),
                    correctVocab.getWord(),
                    correctVocab.getPhoneticText(), // <-- GÁN DỮ LIỆU
                    partOfSpeech,                  // <-- GÁN DỮ LIỆU
                    options,
                    correctVocab.getUserDefinedMeaning(),
                    correctVocab.getUserImageBase64()
            );
        }).collect(Collectors.toList());
    }

    /**
     * Tạo danh sách câu hỏi cho game trắc nghiệm Việt -> Anh.
     */
    private List<ReverseQuizQuestionDTO> createReverseQuizQuestions(List<Vocabulary> allVocabularies) {
        List<Vocabulary> shuffledList = new ArrayList<>(allVocabularies);
        Collections.shuffle(shuffledList);

        List<String> allWords = shuffledList.stream().map(Vocabulary::getWord).collect(Collectors.toList());

        return shuffledList.stream()
                .filter(v -> v.getUserDefinedMeaning() != null && !v.getUserDefinedMeaning().isEmpty())
                .map(correctVocab -> {
                    List<String> options = new ArrayList<>();
                    options.add(correctVocab.getWord());

                    List<String> wrongWords = new ArrayList<>(allWords);
                    wrongWords.remove(correctVocab.getWord());
                    Collections.shuffle(wrongWords);

                    for (int i = 0; i < 3 && i < wrongWords.size(); i++) {
                        options.add(wrongWords.get(i));
                    }
                    Collections.shuffle(options);
                    String partOfSpeech = correctVocab.getMeanings().isEmpty() ? "" : correctVocab.getMeanings().get(0).getPartOfSpeech();
                    return new ReverseQuizQuestionDTO(
                            correctVocab.getId(),
                            correctVocab.getUserDefinedMeaning(),
                            correctVocab.getPhoneticText(), // <-- GÁN DỮ LIỆU
                            partOfSpeech,                  // <-- GÁN DỮ LIỆU
                            options,
                            correctVocab.getWord(),
                            correctVocab.getUserImageBase64()
                    );
                }).collect(Collectors.toList());
    }
}
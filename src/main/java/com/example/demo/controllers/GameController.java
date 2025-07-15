// Đường dẫn file: src/main/java/com/example/demo/controllers/GameController.java

package com.example.demo.controllers;

import com.example.demo.dto.GameDTO;
import com.example.demo.dto.VocabularyDTO;
import com.example.demo.entities.GameResult;
import com.example.demo.entities.ReadingContentCache;
import com.example.demo.entities.Vocabulary;
import com.example.demo.repositories.GameResultRepository;
import com.example.demo.repositories.ReadingContentCacheRepository;
import com.example.demo.repositories.VocabularyRepository;
import com.example.demo.services.GrammarService;
import com.example.demo.services.ReadingGenerationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/game")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class GameController {

    @Autowired
    private VocabularyRepository vocabularyRepository;

    @Autowired
    private GameResultRepository gameResultRepository;

    @Autowired
    private GrammarService grammarService;

    @Autowired
    private ReadingGenerationService readingGenerationService;

    @Autowired
    private ReadingContentCacheRepository cacheRepository;


    @PostMapping("/generate-reading")
    @Transactional
    public ResponseEntity<?> generateReading(@RequestBody GameDTO.ReadingRequestDTO request) {
        final ObjectMapper mapper = new ObjectMapper();
        Optional<ReadingContentCache> cachedContentOpt = cacheRepository.findByFolderIdAndLevelAndTopic(request.folderId(), request.level(), request.topic());
        if (cachedContentOpt.isPresent()) {
            System.out.println("Reading content found in cache for folderId: " + request.folderId() + ", level: " + request.level() + ", topic: " + request.topic());
            try {
                String cachedJson = cachedContentOpt.get().getQuestionsJson();
                JsonNode questionsNode = mapper.readTree(cachedJson);
                return ResponseEntity.ok(new GameDTO.ReadingResponseDTO(cachedContentOpt.get().getStory(), questionsNode));
            } catch (Exception e) {
                System.err.println("Error parsing cached JSON: " + e.getMessage() + ". Will generate new content.");
            }
        }
        List<Vocabulary> vocabularies = vocabularyRepository.findByFolderId(request.folderId());
        if (vocabularies.isEmpty()) {
            return ResponseEntity.badRequest().body("Thư mục này không có từ vựng nào.");
        }
        List<String> vocabWords = vocabularies.stream().map(Vocabulary::getWord).collect(Collectors.toList());
        try {
            System.out.println("Generating new reading content with Groq for topic: " + request.topic());
            ReadingGenerationService.ReadingContent generatedContent = readingGenerationService.generateReadingPassage(vocabWords, request.level(), request.topic());
            System.out.println("Groq succeeded.");
            String questionsAsJsonString = mapper.writeValueAsString(generatedContent.questions());
            ReadingContentCache newCacheEntry = new ReadingContentCache();
            newCacheEntry.setFolderId(request.folderId());
            newCacheEntry.setLevel(request.level());
            newCacheEntry.setTopic(request.topic());
            newCacheEntry.setStory(generatedContent.story());
            newCacheEntry.setQuestionsJson(questionsAsJsonString);
            cacheRepository.save(newCacheEntry);
            JsonNode questionsNode = mapper.readTree(questionsAsJsonString);
            return ResponseEntity.ok(new GameDTO.ReadingResponseDTO(generatedContent.story(), questionsNode));
        } catch (Exception e) {
            System.err.println("Groq API failed or JSON processing failed: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Dịch vụ AI hiện đang gặp sự cố. Vui lòng thử lại sau.");
        }
    }

    @PostMapping("/start")
    public ResponseEntity<?> startGame(@RequestBody GameDTO.GameStartRequestDTO request) {
        List<Vocabulary> vocabularies = vocabularyRepository.findByFolderId(request.folderId());
        if (vocabularies.isEmpty()) {
            return ResponseEntity.badRequest().body("Thư mục này không có từ vựng nào.");
        }
        if ("quiz".equals(request.gameType()) && vocabularies.size() < 4) {
            return ResponseEntity.badRequest().body("Cần ít nhất 4 từ vựng trong thư mục để chơi trắc nghiệm.");
        }
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
        if ("quiz".equals(request.gameType())) {
            if ("vi_en".equals(request.subType())) {
                return ResponseEntity.ok(new GameDTO.ReverseQuizSessionDTO(savedGameResult.getId(), createReverseQuizQuestions(vocabularies)));
            } else {
                return ResponseEntity.ok(new GameDTO.QuizSessionDTO(savedGameResult.getId(), createQuizQuestions(vocabularies)));
            }
        } else {
            List<GameDTO.VocabularyDetailDTO> vocabDTOs = vocabularies.stream()
                    .map(this::convertToDetailDTO)
                    .collect(Collectors.toList());
            Collections.shuffle(vocabDTOs);
            return ResponseEntity.ok(new GameDTO.GameSessionDTO(savedGameResult.getId(), vocabDTOs));
        }
    }

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
        String baseGameType = getOriginalGameType(previousResult.getGameType());
        String originalGameType = baseGameType.split("_")[0];
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
        if ("quiz".equals(originalGameType)) {
            if (baseGameType.endsWith("vi_en")) {
                return ResponseEntity.ok(new GameDTO.ReverseQuizSessionDTO(savedRetryResult.getId(), createReverseQuizQuestions(wrongVocabularies)));
            }
            return ResponseEntity.ok(new GameDTO.QuizSessionDTO(savedRetryResult.getId(), createQuizQuestions(wrongVocabularies)));
        } else {
            List<GameDTO.VocabularyDetailDTO> vocabDTOs = wrongVocabularies.stream()
                    .map(this::convertToDetailDTO)
                    .collect(Collectors.toList());
            Collections.shuffle(vocabDTOs);
            return ResponseEntity.ok(new GameDTO.GameSessionDTO(savedRetryResult.getId(), vocabDTOs));
        }
    }

    @PostMapping("/check-sentence")
    public ResponseEntity<?> checkWritingSentence(@RequestBody GameDTO.SentenceCheckRequestDTO request) {
        Vocabulary vocab = vocabularyRepository.findById(Long.valueOf(request.vocabularyId()))
                .orElseThrow(() -> new RuntimeException("Không tìm thấy từ vựng với id: " + request.vocabularyId()));

        String correctWord = vocab.getWord().toLowerCase();
        String userAnswer = request.userAnswer();

        if (!userAnswer.toLowerCase().contains(correctWord)) {
            return ResponseEntity.ok(new GameDTO.SentenceCheckResponseDTO(false, "Câu của bạn phải chứa từ khóa '" + vocab.getWord() + "'."));
        }
        if (!grammarService.isACompleteSentence(userAnswer)) {
            return ResponseEntity.ok(new GameDTO.SentenceCheckResponseDTO(false, "Đây dường như không phải là một câu hoàn chỉnh. Một câu cần có động từ."));
        }
        List<String> grammarErrors = grammarService.getGrammarMistakes(userAnswer);
        if (!grammarErrors.isEmpty()) {
            String feedback = "Câu có vẻ đúng cấu trúc nhưng vẫn còn lỗi ngữ pháp. Gợi ý: " + grammarErrors.get(0);
            return ResponseEntity.ok(new GameDTO.SentenceCheckResponseDTO(false, feedback));
        }
        return ResponseEntity.ok(new GameDTO.SentenceCheckResponseDTO(true, "Tuyệt vời! Câu của bạn rất hay."));
    }

    private GameDTO.VocabularyDetailDTO convertToDetailDTO(Vocabulary vocab) {
        return new GameDTO.VocabularyDetailDTO(
                vocab.getId(),
                vocab.getWord(),
                vocab.getPhoneticText(),
                vocab.getAudioUrl(),
                vocab.getUserDefinedMeaning(),
                vocab.getUserImageBase64(),
                vocab.getMeanings()
        );
    }

    private String getOriginalGameType(String fullGameType) {
        String baseGameType = fullGameType;
        while (baseGameType.startsWith("retry_")) {
            baseGameType = baseGameType.substring("retry_".length());
        }
        return baseGameType;
    }

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

    private List<GameDTO.QuizQuestionDTO> createQuizQuestions(List<Vocabulary> allVocabularies) {
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
            return new GameDTO.QuizQuestionDTO(
                    correctVocab.getId(),
                    correctVocab.getWord(),
                    correctVocab.getPhoneticText(),
                    partOfSpeech,
                    options,
                    correctVocab.getUserDefinedMeaning(),
                    correctVocab.getUserImageBase64()
            );
        }).collect(Collectors.toList());
    }

    private List<GameDTO.ReverseQuizQuestionDTO> createReverseQuizQuestions(List<Vocabulary> allVocabularies) {
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
                    return new GameDTO.ReverseQuizQuestionDTO(
                            correctVocab.getId(),
                            correctVocab.getUserDefinedMeaning(),
                            correctVocab.getPhoneticText(),
                            partOfSpeech,
                            options,
                            correctVocab.getWord(),
                            correctVocab.getUserImageBase64()
                    );
                }).collect(Collectors.toList());
    }
}
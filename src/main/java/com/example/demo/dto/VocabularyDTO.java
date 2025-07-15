package com.example.demo.dto;

import java.util.List;

public class VocabularyDTO {
    public record DictionaryEntryDTO(
            String word,
            String phoneticText,
            String audioUrl,
            List<MeaningDTO> meanings,
            Long folderId,
            String userDefinedMeaning,
            String userImageBase64
    ) {}
    public record MeaningDTO(
            String partOfSpeech,
            List<DefinitionDTO> definitions,
            List<String> synonyms,
            List<String> antonyms
    ) {}
    public record DefinitionDTO(
            String definition,
            String example
    ) {}
    public record VocabularyUserUpdateDTO(
            String userDefinedMeaning,
            String userImageBase64
    ) {}
    public record GameRetryRequestDTO(Long gameResultId) {}
    public record BatchDeleteRequestDTO(List<Long> vocabularyIds) {}
    public record BatchMoveRequestDTO(List<Long> vocabularyIds, Long targetFolderId) {}
}
package com.example.demo.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.tokenize.SimpleTokenizer;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GrammarService {

    private final POSTaggerME posTagger;
    private static final Map<String, String> CONTRACTIONS_MAP = createContractionsMap();

    private static final String LANGUAGETOOL_API_URL = "https://api.languagetool.org/v2/check";
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public GrammarService() {
        // Khởi tạo OpenNLP
        try (InputStream modelIn = getClass().getResourceAsStream("/model/en-pos-maxent.bin")) {
            if (modelIn == null) {
                throw new RuntimeException("Không thể tìm thấy model OpenNLP POS tagger.");
            }
            POSModel posModel = new POSModel(modelIn);
            this.posTagger = new POSTaggerME(posModel);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi tải model OpenNLP.", e);
        }

        // Khởi tạo các công cụ cho LanguageTool API
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * === PHƯƠNG THỨC ĐƯỢC NÂNG CẤP ===
     * Mở rộng các từ viết tắt tiếng Anh, bao gồm cả các trường hợp [danh từ]'s.
     * @param text Chuỗi đầu vào có thể chứa từ viết tắt.
     * @return Chuỗi đã được mở rộng.
     */
    private String expandContractions(String text) {
        String result = text;

        // 1. Xử lý các từ viết tắt cố định từ map
        for (Map.Entry<String, String> entry : CONTRACTIONS_MAP.entrySet()) {
            result = Pattern.compile("\\b" + entry.getKey() + "\\b", Pattern.CASE_INSENSITIVE)
                    .matcher(result)
                    .replaceAll(entry.getValue());
        }

        // 2. Xử lý các trường hợp [danh từ]'s (ví dụ: lion's, a cat's)
        // Biểu thức chính quy này tìm một từ (\w+) theo sau là 's
        // và không phải là các từ đã xử lý ở trên (it's, he's, she's)
        Pattern nounS_Pattern = Pattern.compile("(\\b\\w+\\b)('s)\\b", Pattern.CASE_INSENSITIVE);
        Matcher matcher = nounS_Pattern.matcher(result);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String word = matcher.group(1).toLowerCase();
            // Chỉ thay thế nếu nó không phải là các trường hợp đã có trong map (he, she, it)
            if (!word.equals("it") && !word.equals("he") && !word.equals("she") && !word.equals("that") && !word.equals("what")) {
                matcher.appendReplacement(sb, matcher.group(1) + " is");
            }
        }
        matcher.appendTail(sb);

        return sb.toString();
    }


    /**
     * Tầng 2: Kiểm tra xem chuỗi có phải là một câu hoàn chỉnh không (bằng cách tìm động từ).
     */
    public boolean isACompleteSentence(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        String expandedText = expandContractions(text);

        String[] tokens = SimpleTokenizer.INSTANCE.tokenize(expandedText);
        String[] tags = posTagger.tag(tokens);
        for (String tag : tags) {
            if (tag.startsWith("VB")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tầng 3: Kiểm tra các lỗi ngữ pháp và chính tả chi tiết bằng LanguageTool API.
     */
    public List<String> getGrammarMistakes(String sentence) {
        List<String> errorMessages = new ArrayList<>();

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(LANGUAGETOOL_API_URL)
                .queryParam("text", sentence)
                .queryParam("language", "en-US")
                // Tắt quy tắc kiểm tra viết hoa đầu câu
                .queryParam("disabledRules", "UPPERCASE_SENTENCE_START")
                // === THAY ĐỔI: YÊU CẦU API KIỂM TRA Ở MỨC ĐỘ KHÓ HƠN ===
                .queryParam("level", "picky");

        URI uri = builder.build().toUri();
        try {
            String response = restTemplate.getForObject(uri, String.class);
            JsonNode root = objectMapper.readTree(response);
            JsonNode matches = root.path("matches");
            if (matches.isArray()) {
                for (JsonNode match : matches) {
                    errorMessages.add(match.path("message").asText());
                }
            }
        } catch (Exception e) {
            System.err.println("Lỗi khi gọi LanguageTool API: " + e.getMessage());
            errorMessages.add("Không thể kiểm tra ngữ pháp vào lúc này.");
        }
        return errorMessages;
    }

    // Helper để tạo map các từ viết tắt
    private static Map<String, String> createContractionsMap() {
        return Map.ofEntries(
                Map.entry("i'm", "i am"),
                Map.entry("you're", "you are"),
                Map.entry("he's", "he is"),
                Map.entry("she's", "she is"),
                Map.entry("it's", "it is"),
                Map.entry("we're", "we are"),
                Map.entry("they're", "they are"),
                Map.entry("that's", "that is"),
                Map.entry("what's", "what is"),
                Map.entry("i've", "i have"),
                Map.entry("you've", "you have"),
                Map.entry("we've", "we have"),
                Map.entry("they've", "they have"),
                Map.entry("i'd", "i would"),
                Map.entry("you'd", "you would"),
                Map.entry("he'd", "he would"),
                Map.entry("she'd", "she would"),
                Map.entry("we'd", "we would"),
                Map.entry("they'd", "they would"),
                Map.entry("i'll", "i will"),
                Map.entry("you'll", "you will"),
                Map.entry("he'll", "he will"),
                Map.entry("she'll", "she will"),
                Map.entry("we'll", "we will"),
                Map.entry("they'll", "they will"),
                Map.entry("isn't", "is not"),
                Map.entry("aren't", "are not"),
                Map.entry("wasn't", "was not"),
                Map.entry("weren't", "were not"),
                Map.entry("haven't", "have not"),
                Map.entry("hasn't", "has not"),
                Map.entry("hadn't", "had not"),
                Map.entry("won't", "will not"),
                Map.entry("wouldn't", "would not"),
                Map.entry("don't", "do not"),
                Map.entry("doesn't", "does not"),
                Map.entry("didn't", "did not"),
                Map.entry("can't", "cannot"),
                Map.entry("couldn't", "could not"),
                Map.entry("shouldn't", "should not"),
                Map.entry("mightn't", "might not"),
                Map.entry("mustn't", "must not")
        );
    }
}
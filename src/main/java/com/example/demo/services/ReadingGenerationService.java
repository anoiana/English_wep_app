// src/main/java/com/example/demo/services/ReadingGenerationService.java
package com.example.demo.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class ReadingGenerationService {

    @Value("${groq.api.key}")
    private String groqApiKey;

    // Tăng timeout để xử lý các yêu cầu phức tạp
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS) // Tăng thời gian chờ đọc phản hồi
            .build();

    // DTO chứa kết quả (không đổi)
    public record ReadingContent(String story, JsonNode questions) {}

    /**
     * Phương thức chính để tạo bài đọc.
     */
    public ReadingContent generateReadingPassage(List<String> vocabulary, int level, String topic) throws IOException {
        String prompt = createPrompt(vocabulary, level, topic);
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode payload = objectMapper.createObjectNode();

        // Sử dụng model mạnh hơn một chút có thể cho kết quả JSON ổn định hơn
        payload.put("model", "llama3-70b-8192");
        payload.putObject("response_format").put("type", "json_object");

        ObjectNode message = payload.putArray("messages").addObject();
        message.put("role", "user");
        message.put("content", prompt);

        String jsonPayload = objectMapper.writeValueAsString(payload);
        RequestBody body = RequestBody.create(jsonPayload, MediaType.get("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url("https://api.groq.com/openai/v1/chat/completions")
                .header("Authorization", "Bearer " + groqApiKey)
                .post(body)
                .build();

        // Thêm cơ chế thử lại để tăng độ tin cậy
        int maxRetries = 2;
        for (int i = 0; i < maxRetries; i++) {
            try (Response response = client.newCall(request).execute()) {
                String responseBody = response.body().string();

                if (!response.isSuccessful()) {
                    System.err.println("Groq Error Response Body (Attempt " + (i + 1) + "): " + responseBody);
                    if (i == maxRetries - 1) { // Nếu là lần thử cuối cùng thì mới throw lỗi
                        throw new IOException("Unexpected code from Groq after " + maxRetries + " attempts. Response: " + response);
                    }
                    continue; // Thử lại nếu chưa phải lần cuối
                }

                JsonNode rootNode = objectMapper.readTree(responseBody);
                String contentString = rootNode.path("choices").get(0).path("message").path("content").asText();

                // Thêm một bước kiểm tra xem contentString có phải là JSON hợp lệ không trước khi parse
                JsonNode contentJson = objectMapper.readTree(contentString);

                return new ReadingContent(contentJson.path("story").asText(), contentJson.path("questions"));

            } catch (Exception e) {
                System.err.println("Error on attempt " + (i + 1) + ": " + e.getMessage());
                if (i == maxRetries - 1) {
                    throw new IOException("Failed to generate and parse content after " + maxRetries + " attempts.", e);
                }
            }
        }
        // Sẽ không bao giờ tới đây nếu logic đúng
        throw new IOException("Failed to generate content after all retries.");
    }

    /**
     * Hàm helper để tạo prompt, được thiết kế lại hoàn toàn để tăng độ chính xác.
     */
    private String createPrompt(List<String> vocabulary, int level, String topic) {
        String difficultyDescription;
        switch (level) {
            case 1: difficultyDescription = "at a simple A2 (Elementary) English level"; break;
            case 2: difficultyDescription = "at a B1 (Intermediate) English level"; break;
            case 3: difficultyDescription = "at a B2 (Upper-Intermediate) English level"; break;
            case 4: difficultyDescription = "at a C1 (Advanced) English level"; break;
            case 5: difficultyDescription = "at a C2 (Proficient) English level"; break;
            default: difficultyDescription = "at a B1 (Intermediate) English level";
        }

        String vocabList = String.join(", ", vocabulary);

        // Cấu trúc prompt được thiết kế lại hoàn toàn
        return String.format(
                """
                You are a helpful AI assistant that generates English learning materials.
                Your task is to create a short story and 3 multiple-choice questions based on it.
                
                **CRITICAL INSTRUCTIONS:**
                1.  Your ENTIRE response MUST be a single, valid JSON object.
                2.  Do NOT include any text, explanation, or markdown formatting (like ```json) before or after the JSON object.
                3.  The JSON structure MUST EXACTLY match the format specified in the "PERFECT RESPONSE EXAMPLE" below.

                **CONTENT CONSTRAINTS:**
                -   **Topic:** The story must be about "%s".
                -   **Vocabulary:** The story must naturally include these words: %s.
                -   **Difficulty:** The story and questions must be written %s.
                -   **Questions:** Create exactly 3 multiple-choice questions. Each question must have exactly 3 options. The "answer" field must be the full text of the correct option.

                **PERFECT RESPONSE EXAMPLE (Follow this structure precisely):**
                ```json
                {
                  "story": "A sample story about a brave cat who loved to explore the mysterious, old house. The cat was very curious and often found hidden treasures.",
                  "questions": [
                    {
                      "question": "What were the cat's main personality traits mentioned in the story?",
                      "options": [
                        "Shy and timid",
                        "Brave and curious",
                        "Lazy and sleepy"
                      ],
                      "answer": "Brave and curious"
                    },
                    {
                      "question": "Where did the cat love to explore?",
                      "options": [
                        "The sunny garden",
                        "The mysterious, old house",
                        "The busy city streets"
                      ],
                      "answer": "The mysterious, old house"
                    },
                    {
                      "question": "What did the cat often find during its explorations?",
                      "options": [
                        "Other friendly cats",
                        "Bowls of milk",
                        "Hidden treasures"
                      ],
                      "answer": "Hidden treasures"
                    }
                  ]
                }
                ```

                Now, generate the content based on all the constraints.
                """,
                topic, vocabList, difficultyDescription
        );
    }
}
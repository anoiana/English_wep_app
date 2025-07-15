package com.example.demo.services;

// src/main/java/com/example/demo/services/TranslationService.java

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class TranslationService {

    // Đọc các giá trị từ application.properties
    @Value("${translation.api.key}")
    private String apiKey;

    @Value("${translation.api.host}")
    private String apiHost;

    @Value("${translation.api.endpoint}")
    private String apiEndpoint;

    @Value("${translation.proxy.url}")
    private String proxyUrl;

    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String translateWord(String word) throws IOException {
        // Phần tạo Request Body và Request không thay đổi, vì nó đã đúng
        MediaType mediaType = MediaType.parse("application/x-www-form-urlencoded");
        RequestBody body = RequestBody.create(
                "text=" + word + "&target=vi&source=en",
                mediaType
        );

        Request request = new Request.Builder()
                .url(proxyUrl)
                .post(body)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("x-apihub-key", apiKey)
                .addHeader("x-apihub-host", apiHost)
                .addHeader("x-apihub-endpoint", apiEndpoint)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body().string();

            if (!response.isSuccessful()) {
                System.err.println("Translation API Error Body: " + responseBody);
                throw new IOException("Unexpected code from Translation API: " + response.code());
            }

            System.out.println("Translation API Response: " + responseBody); // Dòng này vẫn hữu ích để debug

            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(responseBody);

            // <<< THAY ĐỔI QUAN TRỌNG: ĐỌC ĐÚNG ĐƯỜNG DẪN JSON >>>
            // Đi vào object "result", sau đó lấy giá trị của trường "translation"
            JsonNode translationNode = rootNode.path("result").path("translation");

            // Kiểm tra xem node có tồn tại và có giá trị không
            if (translationNode.isMissingNode() || translationNode.isNull()) {
                // Nếu không tìm thấy, có thể API trả về lỗi hoặc cấu trúc khác
                // Thử lấy trường "translation" ở cấp cao nhất làm phương án B
                JsonNode topLevelTranslation = rootNode.path("translation");
                if (topLevelTranslation.isMissingNode() || topLevelTranslation.isNull()) {
                    return "Không có bản dịch."; // Trả về nếu không tìm thấy ở đâu cả
                }
                return topLevelTranslation.asText();
            }

            return translationNode.asText();
        }
    }
}
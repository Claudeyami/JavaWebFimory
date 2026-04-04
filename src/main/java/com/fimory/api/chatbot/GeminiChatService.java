package com.fimory.api.chatbot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fimory.api.config.IntegrationProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GeminiChatService {

    private static final List<String> API_VERSIONS = List.of("v1", "v1beta");
    private static final List<String> MODEL_CANDIDATES = List.of(
            "gemini-2.5-flash",
            "gemini-2.0-flash",
            "gemini-1.5-flash-latest",
            "gemini-1.5-flash",
            "gemini-pro"
    );

    private final IntegrationProperties integrationProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GeminiChatService(IntegrationProperties integrationProperties, ObjectMapper objectMapper) {
        this.integrationProperties = integrationProperties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public JsonNode generateContent(List<Map<String, Object>> contents) throws IOException, InterruptedException {
        String apiKey = integrationProperties.getGemini().getChatApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_CHAT_KEY chua duoc cau hinh.");
        }
        if (contents == null || contents.isEmpty()) {
            throw new IllegalArgumentException("Noi dung hoi thoai trong.");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("contents", contents);
        String requestBody = objectMapper.writeValueAsString(body);

        List<String> errors = new ArrayList<>();

        for (String apiVersion : API_VERSIONS) {
            for (String model : MODEL_CANDIDATES) {
                String endpoint = "https://generativelanguage.googleapis.com/"
                        + apiVersion
                        + "/models/"
                        + model
                        + ":generateContent?key="
                        + apiKey;

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .timeout(Duration.ofSeconds(45))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                JsonNode responseJson;

                try {
                    responseJson = objectMapper.readTree(response.body());
                } catch (Exception parseEx) {
                    errors.add(apiVersion + "/" + model + ": invalid json response");
                    continue;
                }

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return responseJson;
                }

                String message = responseJson.path("error").path("message").asText();
                if (message == null || message.isBlank()) {
                    message = "HTTP " + response.statusCode();
                }
                errors.add(apiVersion + "/" + model + ": " + message);
            }
        }

        String detail = errors.isEmpty() ? "No model attempt succeeded." : String.join(" | ", errors);
        throw new IllegalStateException("Gemini request failed. " + detail);
    }
}

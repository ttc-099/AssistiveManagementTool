package com.example.app.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class GroqAgentService {

    private static final String GROQ_API_KEY = "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";
    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";

    private final HttpClient httpClient = HttpClient.newHttpClient();

    public CompletableFuture<Map<String, String>> suggestCorrections(Map<String, String> rawRow) {

        try {
            // FIX: Formulate request body. mapToJson handles escaping internal components safely.
            String systemPrompt = "Analyze the geographic fields (Country, Region, State, City, Postcode, Nationality, Home Address). Identify misplaced or incorrectly formatted cells (e.g., a state listed as a country) and return a clean, corrected JSON object containing ONLY the modified keys. If nothing is wrong, return an empty JSON object {}. Do not include markdown formatting code blocks.";
            String userContent = mapToJson(rawRow);

            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"model\": \"llama-3.1-8b-instant\",");
            sb.append("\"messages\": [");
            sb.append("{\"role\": \"system\", \"content\": \"").append(escapeJson(systemPrompt)).append("\"},");
            sb.append("{\"role\": \"user\", \"content\": \"").append(escapeJson(userContent)).append("\"}");
            sb.append("],");
            sb.append("\"response_format\": {\"type\": \"json_object\"},");
            sb.append("\"temperature\": 0.0"); // Zero temperature forces deterministic, accurate normalization
            sb.append("}");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GROQ_API_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + GROQ_API_KEY)
                    .POST(HttpRequest.BodyPublishers.ofString(sb.toString()))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() != 200) {
                            throw new RuntimeException("Groq API error (" + response.statusCode() + "): " + response.body());
                        }
                        return parseResponseContent(response.body());
                    });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private String mapToJson(Map<String, String> map) {
        StringBuilder sb = new StringBuilder("{");
        map.forEach((k, v) -> sb.append("\"").append(escapeJson(k)).append("\":\"").append(escapeJson(v)).append("\","));
        if (sb.length() > 1) sb.setLength(sb.length() - 1);
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private Map<String, String> parseResponseContent(String body) {
        try {
            // Find the start of the message content value block inside standard OpenAI/Groq response formatting
            String marker = "\"content\":";
            int markerIndex = body.indexOf(marker);
            if (markerIndex == -1) return Map.of();

            // Locate opening quote of content string
            int startQuote = body.indexOf("\"", markerIndex + marker.length());
            if (startQuote == -1) return Map.of();

            // Safely locate the closing quote loop bypassing escaped internal quotes
            int endQuote = body.indexOf("\"", startQuote + 1);
            while (endQuote != -1 && body.charAt(endQuote - 1) == '\\') {
                endQuote = body.indexOf("\"", endQuote + 1);
            }
            if (endQuote == -1) return Map.of();

            // Unescape raw payload string from JSON carrier structure
            String rawJsonContent = body.substring(startQuote + 1, endQuote)
                    .replace("\\n", "")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");

            // Build map directly out of target raw output tokens
            Map<String, String> result = new HashMap<>();
            rawJsonContent = rawJsonContent.trim();
            if (rawJsonContent.startsWith("{")) rawJsonContent = rawJsonContent.substring(1);
            if (rawJsonContent.endsWith("}")) rawJsonContent = rawJsonContent.substring(0, rawJsonContent.length() - 1);

            // Simple regex split to safely segment keys out
            String[] pairs = rawJsonContent.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
            for (String pair : pairs) {
                String[] kv = pair.split(":(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                if (kv.length == 2) {
                    String k = kv[0].trim().replace("\"", "");
                    String v = kv[1].trim().replace("\"", "");
                    if (!k.isEmpty()) {
                        result.put(k, v);
                    }
                }
            }
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return Map.of();
        }
    }
}
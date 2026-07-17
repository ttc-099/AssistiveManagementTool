package com.example.app.service.aiUsages;

import de.kherud.llama.InferenceParameters;
import de.kherud.llama.LlamaModel;
import de.kherud.llama.LlamaOutput;
import de.kherud.llama.ModelParameters;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Local Qwen AI Service for data correction and chat interactions.
 * Replaces the cloud-based Groq API with local Qwen model.
 */
public class QwenAgentService {

    private static final String MODEL_PATH = "app_models/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf";

    private LlamaModel model;
    private boolean isModelLoaded = false;
    private boolean isLoading = false;

    public QwenAgentService() {
        loadModel();
        registerCleanupHook();
    }

    private void registerCleanupHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("🧹 Cleaning up Qwen model...");
            unloadModel();
        }));
    }

    private void loadModel() {
        if (isLoading) return;
        isLoading = true;

        try {
            File modelFile = new File(MODEL_PATH);
            if (!modelFile.exists()) {
                System.err.println("⚠️ Qwen model not found at: " + MODEL_PATH);
                System.err.println("   Please ensure the file exists at: " + new File(MODEL_PATH).getAbsolutePath());
                System.err.println("   Download from: https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF");
                isLoading = false;
                return;
            }

            System.out.println("🔄 Loading Qwen model from: " + modelFile.getAbsolutePath());
            System.out.println("   File size: " + (modelFile.length() / 1024 / 1024) + " MB");

            ModelParameters modelParams = new ModelParameters();
            modelParams.setModel(modelFile.getAbsolutePath());
            modelParams.setCtxSize(8192);
            modelParams.setThreads(6);
            modelParams.setBatchSize(512);

            long startTime = System.currentTimeMillis();
            model = new LlamaModel(modelParams);
            isModelLoaded = true;
            long loadTime = System.currentTimeMillis() - startTime;

            System.out.println("✅ Qwen model loaded successfully in " + loadTime + "ms!");

        } catch (Exception e) {
            System.err.println("❌ Failed to load Qwen model: " + e.getMessage());
            e.printStackTrace();
            isModelLoaded = false;
        } finally {
            isLoading = false;
        }
    }

    /**
     * Chat with Qwen using a user message and optional data context
     */
    public CompletableFuture<String> chat(String userMessage, String dataContext) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isModelLoaded || model == null) {
                return "⚠️ Qwen model is not loaded. Please check the model file path and try again.";
            }

            try {
                String systemPrompt = "You are Qwen, an AI data assistant for a university admissions system. " +
                        "You help users analyze and clean their data. " +
                        "Be helpful, concise, and practical. " +
                        "When asked about data, use the provided context to give specific answers. " +
                        "If you don't know something, say so clearly. " +
                        "Format your responses with clear sections using emojis where appropriate.";

                String fullPrompt = "<|im_start|>system\n" + systemPrompt + "<|im_end|>\n";

                if (dataContext != null && !dataContext.isEmpty()) {
                    fullPrompt += "<|im_start|>user\nContext about the data:\n" + dataContext + "\n\n" +
                            "User question: " + userMessage + "<|im_end|>\n";
                } else {
                    fullPrompt += "<|im_start|>user\n" + userMessage + "<|im_end|>\n";
                }

                fullPrompt += "<|im_start|>assistant\n";

                System.out.println("🔄 Chat request to Qwen: " + userMessage.substring(0, Math.min(50, userMessage.length())) + "...");
                long startTime = System.currentTimeMillis();

                String response = callQwen(fullPrompt);

                long responseTime = System.currentTimeMillis() - startTime;
                System.out.println("✅ Qwen chat response received in " + responseTime + "ms");

                return response.trim();

            } catch (Exception e) {
                System.err.println("❌ Error during Qwen chat: " + e.getMessage());
                e.printStackTrace();
                return "❌ I encountered an error: " + e.getMessage();
            }
        });
    }

    /**
     * Suggest corrections for a single row of data using Qwen
     */
    public CompletableFuture<Map<String, String>> suggestCorrections(Map<String, String> rawRow) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isModelLoaded || model == null) {
                System.err.println("⚠️ Qwen model not loaded. Returning original row.");
                return new HashMap<>(rawRow);
            }

            try {
                String systemPrompt = "You are a data correction assistant. " +
                        "Analyze the geographic fields (Country, Region, State, City, Postcode, Nationality, Home Address). " +
                        "Identify misplaced or incorrectly formatted cells (e.g., a state listed as a country). " +
                        "Return a clean, corrected JSON object containing ONLY the modified keys. " +
                        "If nothing is wrong, return an empty JSON object {}. " +
                        "Do not include markdown formatting code blocks. " +
                        "Return ONLY valid JSON.";

                String userContent = mapToJson(rawRow);

                String prompt = "<|im_start|>system\n" + systemPrompt + "<|im_end|>\n" +
                        "<|im_start|>user\n" + userContent + "<|im_end|>\n" +
                        "<|im_start|>assistant\n";

                System.out.println("🔄 Sending correction request to Qwen...");
                long startTime = System.currentTimeMillis();

                String response = callQwen(prompt);

                long responseTime = System.currentTimeMillis() - startTime;
                System.out.println("✅ Qwen correction response received in " + responseTime + "ms");

                String jsonContent = extractJsonFromResponse(response);
                Map<String, String> corrections = parseJsonToMap(jsonContent);

                if (corrections.isEmpty()) {
                    System.out.println("   No corrections needed.");
                } else {
                    System.out.println("   Found " + corrections.size() + " corrections: " + corrections);
                }

                return corrections;

            } catch (Exception e) {
                System.err.println("❌ Error during Qwen correction: " + e.getMessage());
                e.printStackTrace();
                return new HashMap<>(rawRow);
            }
        });
    }

    /**
     * Call Qwen model with the given prompt
     */
    private String callQwen(String prompt) {
        if (model == null) {
            throw new RuntimeException("Qwen model is null");
        }

        try {
            InferenceParameters inferParams = new InferenceParameters(prompt)
                    .setTemperature(0.1F)
                    .setTopP(0.9F)
                    .setRepeatPenalty(1.1F)
                    .setStopStrings("<|im_end|>", "<|endoftext|>", "```")
                    .setNPredict(512);

            StringBuilder response = new StringBuilder();
            for (LlamaOutput output : model.generate(inferParams)) {
                response.append(output.toString());
            }

            return response.toString();

        } catch (Exception e) {
            throw new RuntimeException("Qwen inference failed: " + e.getMessage(), e);
        }
    }

    private String mapToJson(Map<String, String> map) {
        StringBuilder sb = new StringBuilder("{");
        int count = 0;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (count > 0) sb.append(",");
            String key = escapeJson(entry.getKey());
            String value = entry.getValue() != null ? escapeJson(entry.getValue()) : "";
            sb.append("\"").append(key).append("\":\"").append(value).append("\"");
            count++;
        }
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

    private String extractJsonFromResponse(String response) {
        if (response == null || response.trim().isEmpty()) return "{}";

        int startIndex = response.indexOf("{");
        int endIndex = response.lastIndexOf("}");

        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            return response.substring(startIndex, endIndex + 1);
        }

        startIndex = response.indexOf("[");
        endIndex = response.lastIndexOf("]");
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            return response.substring(startIndex, endIndex + 1);
        }

        return "{}";
    }

    private Map<String, String> parseJsonToMap(String jsonContent) {
        Map<String, String> result = new HashMap<>();

        if (jsonContent == null || jsonContent.trim().isEmpty() || jsonContent.equals("{}")) {
            return result;
        }

        try {
            String content = jsonContent.trim();
            if (content.startsWith("{")) content = content.substring(1);
            if (content.endsWith("}")) content = content.substring(0, content.length() - 1);

            if (content.trim().isEmpty()) return result;

            String[] pairs = content.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
            for (String pair : pairs) {
                String[] kv = pair.split(":(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                if (kv.length == 2) {
                    String key = kv[0].trim().replaceAll("^\"|\"$", "");
                    String value = kv[1].trim().replaceAll("^\"|\"$", "");
                    if (!key.isEmpty() && !value.equalsIgnoreCase("null")) {
                        result.put(key, value);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ Error parsing JSON: " + e.getMessage());
        }

        return result;
    }

    // ========== STATUS METHODS ==========

    public boolean isAvailable() {
        return isModelLoaded && model != null;
    }

    public String getStatus() {
        if (isLoading) return "⏳ Loading Qwen model...";
        if (isAvailable()) return "✅ Qwen model ready";
        return "❌ Qwen model not available";
    }

    public void unloadModel() {
        if (model != null) {
            try {
                model.close();
                model = null;
                isModelLoaded = false;
                System.out.println("✅ Qwen model unloaded successfully");
            } catch (Exception e) {
                System.err.println("⚠️ Error unloading Qwen model: " + e.getMessage());
            }
        }
    }

    public void reloadModel() {
        unloadModel();
        loadModel();
    }

    public String getModelInfo() {
        if (!isAvailable()) return "No model loaded";
        return "Qwen2.5-1.5B-Instruct (GGUF Q4_K_M)\n" +
                "Context size: 8192 tokens\n" +
                "Location: " + MODEL_PATH;
    }
}
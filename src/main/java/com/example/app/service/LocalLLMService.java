package com.example.app.service;

import de.kherud.llama.InferenceParameters;
import de.kherud.llama.LlamaModel;
import de.kherud.llama.LlamaOutput;
import de.kherud.llama.ModelParameters;

import java.io.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocalLLMService {

    private static final String MODEL_PATH = "app_models/7B/Llama-3.2-3B-Instruct-Q4_K_M.gguf";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private LlamaModel model;
    private boolean isModelLoaded = false;

    public LocalLLMService() {
        loadModel();
        registerCleanupHook();
    }

    private void registerCleanupHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("🧹 Cleaning up native libraries...");
            try {
                // Unload model first
                unloadModel();

                // Clean up jllama.dll and related files
                String tempDir = System.getProperty("java.io.tmpdir");
                File tempDirFile = new File(tempDir);
                File[] jllamaFiles = tempDirFile.listFiles((dir, name) ->
                        name.startsWith("jllama") && (name.endsWith(".dll") || name.endsWith(".so") || name.endsWith(".dylib")));

                if (jllamaFiles != null) {
                    for (File f : jllamaFiles) {
                        if (f.exists()) {
                            boolean deleted = f.delete();
                            if (deleted) {
                                System.out.println("✅ Deleted: " + f.getName());
                            } else {
                                f.deleteOnExit();
                                System.out.println("⚠️ Scheduled for deletion on exit: " + f.getName());
                            }
                        }
                    }
                }
                System.out.println("✅ Native library cleanup complete");
            } catch (Exception e) {
                System.err.println("⚠️ Cleanup error: " + e.getMessage());
            }
        }));
    }

    private void loadModel() {
        try {
            File modelFile = new File(MODEL_PATH);
            if (!modelFile.exists()) {
                System.err.println("⚠️ Llama model not found at: " + MODEL_PATH);
                return;
            }

            System.out.println("🔄 Loading Llama model from: " + MODEL_PATH);

            ModelParameters modelParams = new ModelParameters();
            modelParams.setModel(modelFile.getAbsolutePath());
            modelParams.setCtxSize(4096);
            modelParams.setThreads(6);
            model = new LlamaModel(modelParams);
            isModelLoaded = true;
            System.out.println("✅ Llama model loaded successfully!");

        } catch (Exception e) {
            System.err.println("❌ Failed to load Llama model: " + e.getMessage());
            e.printStackTrace();
            isModelLoaded = false;
        }
    }

    public CompletableFuture<List<Map<String, String>>> processUAListWithLLM(
            List<Map<String, String>> uaList,
            List<String> headers,
            String intake) {

        return CompletableFuture.supplyAsync(() -> {
            if (!isModelLoaded || model == null) {
                System.err.println("⚠️ Model not loaded. Skipping LLM processing.");
                return uaList;
            }

            System.out.println("🔄 Starting LLM processing for " + uaList.size() + " records...");
            long startTime = System.currentTimeMillis();

            // Build the rules ONCE
            String systemPrompt = buildSystemPrompt(intake);

            // Split into batches of 26 to balance speed and quietness
            List<Map<String, String>> processedRecords = new ArrayList<>();
            int batchSize = 5;

            for (int i = 0; i < uaList.size(); i += batchSize) {
                int end = Math.min(i + batchSize, uaList.size());
                List<Map<String, String>> batch = uaList.subList(i, end);

                System.out.println("🔄 Processing batch " + ((i / batchSize) + 1) + " (" + batch.size() + " records)...");

                // Convert batch to JSON Array
                StringBuilder jsonArray = new StringBuilder("[");
                for (int j = 0; j < batch.size(); j++) {
                    if (j > 0) jsonArray.append(",");
                    jsonArray.append(convertToJson(batch.get(j)));
                }
                jsonArray.append("]");

                String userPrompt = "<|user|>\nCorrect this array of " + batch.size() + " student records. Return the ENTIRE array of corrected JSON objects in the exact same order. Do not skip any. Array:\n" + jsonArray.toString() + "\n\nCorrected JSON Array:\n<|assistant|>\n";

                String response = callLLM(systemPrompt + userPrompt);
                String correctedJsonArray = extractJsonFromResponse(response);

                List<Map<String, String>> correctedBatch = parseJsonArray(correctedJsonArray);

                // If parsing failed, fallback to original batch
                if (correctedBatch.isEmpty() || correctedBatch.size() != batch.size()) {
                    System.err.println("⚠️ Batch " + ((i / batchSize) + 1) + " parsing failed, using original data.");
                    processedRecords.addAll(batch);
                } else {
                    processedRecords.addAll(correctedBatch);
                    System.out.println("✅ Batch " + ((i / batchSize) + 1) + " processed successfully.");
                }
            }

            long endTime = System.currentTimeMillis();
            System.out.println("✅ LLM processing completed in " + (endTime - startTime) + "ms");
            return processedRecords;
        }, executor);
    }

    private String buildSystemPrompt(String intake) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("<|system|>\n");
        prompt.append("You are a data correction assistant for a university admissions system.\n");
        prompt.append("Your task is to clean and correct student records in JSON format.\n\n");
        prompt.append("CORRECTION RULES BY COLUMN:\n\n");

        // Student ID
        prompt.append("1. 'Student ID':\n");
        prompt.append("   - Format: 8-digit number (e.g., '38550873')\n");
        prompt.append("   - Remove any leading/trailing spaces or special characters\n\n");

        // Intake
        prompt.append("2. 'Intake':\n");
        prompt.append("   - Should match pattern: 'Month Year' (e.g., 'July 2026')\n");
        prompt.append("   - Current intake is: ").append(intake).append("\n\n");

        // Title
        prompt.append("3. 'Title':\n");
        prompt.append("   - Use standard titles: Mr, Ms, Mrs, Dr\n");
        prompt.append("   - Correct capitalization (e.g., 'Mr' not 'mr' or 'MR')\n\n");

        // Surname and Given Name
        prompt.append("4. 'Surname' and 'Given Name':\n");
        prompt.append("   - Use Proper Case capitalization (e.g., 'Zaini' not 'ZAINI' or 'zaini')\n");
        prompt.append("   - Surname should be the family name only\n");
        prompt.append("   - Given Name should include first and middle names\n");
        prompt.append("   - Handle prefixes like 'Bin', 'Binti', 'A/L', 'A/P' appropriately\n");
        prompt.append("   - For 'Surname': Extract the family name from the full name\n");
        prompt.append("   - For 'Given Name': Use the given names (first + middle)\n\n");

        // Name (Full Name)
        prompt.append("5. 'Name':\n");
        prompt.append("   - Format: 'Surname, Given Name' (e.g., 'Zaini, Emre Farihin Hakeem Bin')\n");
        prompt.append("   - Use Proper Case for all parts\n\n");

        // File Name
        prompt.append("6. 'File Name':\n");
        prompt.append("   - Format: 'StudentID_Surname, Given Name' (e.g., '38550873_Zaini, Emre Farihin Hakeem Bin')\n");
        prompt.append("   - No spaces after underscore\n\n");

        // Name (according to IC/Passport)
        prompt.append("7. 'Name (according to IC/Passport)':\n");
        prompt.append("   - Full name as it appears on official documents\n");
        prompt.append("   - Use Proper Case\n");
        prompt.append("   - Include all parts: Given Name + Surname (e.g., 'Emre Farihin Hakeem Bin Zaini')\n\n");

        // Date of Birth
        prompt.append("8. 'Date of Birth':\n");
        prompt.append("   - FORMAT: 'YYYY-MM-DD' ONLY (e.g., '2008-02-07')\n");
        prompt.append("   - IMPORTANT: Remove any time stamps like '00:00:00'\n");
        prompt.append("   - No other date formats allowed (not 'DD/MM/YYYY' or 'MM/DD/YYYY')\n\n");

        // Age
        prompt.append("9. 'Age':\n");
        prompt.append("   - Calculate as integer based on Date of Birth and intake date\n");
        prompt.append("   - For July intake: Age = Year(intake year) - Year(DOB) - (if Month(DOB) > 7 then 1 else 0)\n");
        prompt.append("   - DO NOT use Excel formulas - calculate the actual number\n\n");

        // Nationality
        prompt.append("10. 'Nationality':\n");
        prompt.append("    - Use standard country names (e.g., 'Malaysia', 'Indonesia', 'China')\n");
        prompt.append("    - Correct capitalization (e.g., 'Malaysia' not 'malaysia' or 'MALAYSIA')\n\n");

        // Nationality 2
        prompt.append("11. 'Nationality 2':\n");
        prompt.append("    - Should be 'Malaysian' for Malaysian citizens\n");
        prompt.append("    - For other nationalities, use correct demonym (e.g., 'Indonesian', 'Chinese')\n");
        prompt.append("    - Correct capitalization\n\n");

        // NRIC / Passport No
        prompt.append("12. 'NRIC / Passport No':\n");
        prompt.append("    - Malaysian IC format: 'YYMMDD-XX-NNNN' (e.g., '080207-10-0651')\n");
        prompt.append("    - For IC: Format as 6-digit DOB + '-' + 2-digit + '-' + 4-digit\n");
        prompt.append("    - For passport: Use standard format with letters and numbers\n");
        prompt.append("    - IMPORTANT: Do not use slashes '/', use hyphens '-'\n");
        prompt.append("    - Standardize Malaysian IC to 'YYMMDD-XX-NNNN' format\n\n");

        // Home Address
        prompt.append("13. 'Home Address':\n");
        prompt.append("    - Proper capitalization (e.g., 'No 21, Jalan Desa 4 Taman Universiti')\n");
        prompt.append("    - Ensure consistent address format\n\n");

        // City
        prompt.append("14. 'City':\n");
        prompt.append("    - Proper capitalization (e.g., 'Kajang', 'Kuala Lumpur')\n\n");

        // State
        prompt.append("15. 'State':\n");
        prompt.append("    - Use full state names (e.g., 'Selangor' not 'SELANGOR')\n");
        prompt.append("    - Correct abbreviations: 'W.Perskutuan(KL)' -> 'Kuala Lumpur'\n\n");

        // Postcode
        prompt.append("16. 'Postcode':\n");
        prompt.append("    - 5-digit Malaysian postcode (e.g., '43000')\n");
        prompt.append("    - Remove any non-numeric characters\n\n");

        // Country
        prompt.append("17. 'Country':\n");
        prompt.append("    - Use standard country names (e.g., 'Malaysia')\n");
        prompt.append("    - Proper capitalization\n\n");

        // Primary Contact (Phone)
        prompt.append("18. 'Primary Contact':\n");
        prompt.append("    - Format: Start with '0' for Malaysian numbers\n");
        prompt.append("    - Remove spaces, brackets, and dashes (e.g., '0196186202')\n");
        prompt.append("    - Standard format: 10-11 digits starting with 0\n");
        prompt.append("    - For numbers with country code: '60' should be replaced with '0'\n\n");

        // Email
        prompt.append("19. 'Email':\n");
        prompt.append("    - Convert to lowercase\n");
        prompt.append("    - Ensure proper email format: username@domain.com\n");
        prompt.append("    - Remove spaces in email addresses\n\n");

        // Academic Qualification
        prompt.append("20. 'Academic Qualification':\n");
        prompt.append("    - Use standard abbreviations (e.g., 'SPM', 'IGCSE', 'STPM')\n");
        prompt.append("    - Keep as is, only correct capitalization\n\n");

        // Scholarship
        prompt.append("21. 'Scholarship':\n");
        prompt.append("    - If value is a number with RM, keep format: 'RM 8,000'\n");
        prompt.append("    - If empty, leave as empty string\n");
        prompt.append("    - Do not change numeric values\n\n");

        // Decision
        prompt.append("22. 'Decision':\n");
        prompt.append("    - Valid values: 'UA', 'CA', 'N/A'\n");
        prompt.append("    - Keep as is if valid\n\n");

        // Date Pushed UA
        prompt.append("23. 'Date Pushed UA':\n");
        prompt.append("    - FORMAT: 'YYYY-MM-DD' ONLY\n");
        prompt.append("    - Remove time stamps (e.g., '2026-06-29' not '2026-06-29 00:00:00')\n\n");

        // Visa status
        prompt.append("24. 'Visa status':\n");
        prompt.append("    - Should be empty if no visa information\n");
        prompt.append("    - Keep existing valid entries\n\n");

        // Welcome message
        prompt.append("25. 'Welcome message':\n");
        prompt.append("    - Valid values: 'Yes', 'Done', or empty\n");
        prompt.append("    - Keep existing valid entries\n\n");

        // Infopack
        prompt.append("26. 'Infopack':\n");
        prompt.append("    - Valid values: 'Done' or empty\n");
        prompt.append("    - Keep existing valid entries\n\n");

        // User ID
        prompt.append("27. 'User ID':\n");
        prompt.append("    - Format: 5-6 alphanumeric characters (e.g., 'efhz1n26')\n");
        prompt.append("    - Keep as is\n\n");

        // Online Enrolment
        prompt.append("28. 'Online Enrolment':\n");
        prompt.append("    - Valid values: 'EN-Enrolled in the University', 'EL-Eligible to enrol/register'\n");
        prompt.append("    - Keep as is if valid\n\n");

        // Induction Attendance
        prompt.append("29. 'Induction Attendance':\n");
        prompt.append("    - Valid values: 'Yes', 'No', 'Beh', or empty\n");
        prompt.append("    - Keep as is\n\n");

        // System
        prompt.append("30. 'System':\n");
        prompt.append("    - Valid values: 'Done', 'aini', or empty\n");
        prompt.append("    - Keep as is\n\n");

        prompt.append("GENERAL INSTRUCTIONS:\n");
        prompt.append("1. For 'Date of Birth' and 'Date Pushed UA': Remove any time stamps (00:00:00). Format as YYYY-MM-DD\n");
        prompt.append("2. For 'NRIC / Passport No': Convert to 'YYMMDD-XX-NNNN' format with hyphens, no slashes\n");
        prompt.append("3. For 'Name' fields: Use Proper Case (first letter of each word capitalized)\n");
        prompt.append("4. For 'Email': Convert to lowercase, remove spaces\n");
        prompt.append("5. For 'Primary Contact': Remove spaces, brackets, dashes; keep only digits starting with 0\n");
        prompt.append("6. For 'State': Use full names, not abbreviations\n");
        prompt.append("7. DO NOT change: Student ID, Scholarship amounts, User IDs, Academic Qualifications\n");
        prompt.append("8. If a field appears to contain an Excel formula or error (#VALUE!, #N/A), replace it with the correct calculated value\n");
        prompt.append("9. If you see date values with '00:00:00', remove the time part and keep only 'YYYY-MM-DD'\n");
        prompt.append("10. IMPORTANT: Return ONLY valid JSON with the same structure. If no changes are needed, return the original JSON exactly.\n");
        prompt.append("11. Do not add any explanation or extra text outside the JSON.\n");
        prompt.append("12. All fields should be properly formatted according to the rules above.\n");
        prompt.append("</s>\n\n");

        return prompt.toString();
    }

    private String callLLM(String prompt) {
        if (model == null) return "{}";
        try {
            // OPTIMIZATION: Limit CPU threads to 6 so it doesn't scream, and cap tokens
            String[] stopSequences = new String[]{"</s>", "```"};

            InferenceParameters inferParams = new InferenceParameters(prompt)
                    .setTemperature(0.1F)
                    .setTopP(0.9F)
                    .setRepeatPenalty(1.1F)
                    .setStopStrings(stopSequences)
                    .setNPredict(400);



            StringBuilder response = new StringBuilder();
            for (LlamaOutput output : model.generate(inferParams)) {
                response.append(output.toString());
            }
            return response.toString();
        } catch (Exception e) {
            System.err.println("Error calling LLM: " + e.getMessage());
            return "{}";
        }
    }

    private String convertToJson(Map<String, String> record) {
        StringBuilder json = new StringBuilder("{");
        int count = 0;
        for (Map.Entry<String, String> entry : record.entrySet()) {
            if (count > 0) json.append(",");
            String key = escapeJson(entry.getKey());
            String value = escapeJson(entry.getValue() != null ? entry.getValue() : "");
            json.append("\"").append(key).append("\":\"").append(value).append("\"");
            count++;
        }
        json.append("}");
        return json.toString();
    }

    // Parse a JSON Array into a List of Maps
    private List<Map<String, String>> parseJsonArray(String jsonArrayStr) {
        List<Map<String, String>> list = new ArrayList<>();
        if (jsonArrayStr == null || jsonArrayStr.trim().isEmpty() || jsonArrayStr.equals("[]")) {
            return list;
        }
        int start = jsonArrayStr.indexOf("[");
        int end = jsonArrayStr.lastIndexOf("]");
        if (start == -1 || end == -1) return list;

        String content = jsonArrayStr.substring(start + 1, end);
        String[] objects = content.split("\\},\\{");

        for (String obj : objects) {
            if (!obj.startsWith("{")) obj = "{" + obj;
            if (!obj.endsWith("}")) obj = obj + "}";
            Map<String, String> parsed = parseJson(obj);
            if (!parsed.isEmpty()) {
                list.add(parsed);
            }
        }
        return list;
    }

    private Map<String, String> parseJson(String jsonLine) {
        Map<String, String> result = new LinkedHashMap<>();
        if (jsonLine == null || jsonLine.trim().isEmpty() || jsonLine.equals("{}")) return result;

        try {
            int startIndex = jsonLine.indexOf("{");
            int endIndex = jsonLine.lastIndexOf("}");
            if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
                jsonLine = jsonLine.substring(startIndex, endIndex + 1);
            } else {
                return result;
            }

            String content = jsonLine.trim();
            if (content.startsWith("{")) content = content.substring(1);
            if (content.endsWith("}")) content = content.substring(0, content.length() - 1);

            if (content.trim().isEmpty()) return result;

            content = content.replace("\\n", "\\\\n");
            String[] pairs = content.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
            for (String pair : pairs) {
                String[] kv = pair.split(":(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                if (kv.length == 2) {
                    String key = kv[0].trim().replaceAll("^\"|\"$", "");
                    String value = kv[1].trim().replaceAll("^\"|\"$", "");
                    if (!key.isEmpty()) {
                        result.put(key, value);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error parsing JSON: " + e.getMessage());
        }
        return result;
    }

    private String extractJsonFromResponse(String response) {
        if (response == null || response.trim().isEmpty()) return "{}";
        int startIndex = response.indexOf("[");
        int endIndex = response.lastIndexOf("]");
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            return response.substring(startIndex, endIndex + 1);
        }
        // Fallback to object
        startIndex = response.indexOf("{");
        endIndex = response.lastIndexOf("}");
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            return response.substring(startIndex, endIndex + 1);
        }
        return "{}";
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public boolean isAvailable() { return isModelLoaded && model != null; }
    public String getStatus() { return isAvailable() ? "✅ Model ready" : "⚠️ Model not available"; }
    public void unloadModel() {
        if (model != null) {
            try {
                model.close();
                model = null;
                isModelLoaded = false;
                System.out.println("✅ Model unloaded successfully");
            } catch (Exception e) {
                System.err.println("Error unloading model: " + e.getMessage());
            }
        }
    }
}
package com.example.app.service.aiUsages;

import com.example.app.db.DatabaseSetupService;
import de.kherud.llama.InferenceParameters;
import de.kherud.llama.LlamaModel;
import de.kherud.llama.LlamaOutput;
import de.kherud.llama.ModelParameters;
import com.example.app.db.DatabaseManager;
import com.example.app.service.excel.ExcelWorkbookService;
import com.example.app.service.excel.SheetDataWrapper;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QwenAgentService {

    private static final String MODEL_PATH = "app_models/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf";
    private static final String DB_PATH = "app_data/bistro_temp.db";

    private LlamaModel model;
    private boolean isModelLoaded = false;
    private boolean isLoading = false;
    private boolean awaitingConfirmation = false;
    private String pendingUpdateSQL = "";
    private String pendingUpdateDescription = "";

    // Track what's in the database
    private String trackedFileName = "";
    private int totalSheets = 0;
    private Map<String, Integer> sheetRowCounts = new HashMap<>();

    private DatabaseManager dbManager;

    // ===== TERMINATE CONTROL =====
    private volatile boolean shouldStop = false;
    private Thread currentThread = null;

    // ===== CACHED PROMPTS =====
    private String cachedSystemPrompt = null;
    private String cachedCleaningPrompt = null;

    // ===== RESULT HOLDER FOR TABLE DATA =====
    public static class QueryResult {
        private final List<String> columns;
        private final List<Map<String, Object>> rows;
        private final String naturalLanguageResponse;
        private final String sqlExecuted;

        public QueryResult(List<String> columns, List<Map<String, Object>> rows,
                           String naturalLanguageResponse, String sqlExecuted) {
            this.columns = columns;
            this.rows = rows;
            this.naturalLanguageResponse = naturalLanguageResponse;
            this.sqlExecuted = sqlExecuted;
        }

        public List<String> getColumns() { return columns; }
        public List<Map<String, Object>> getRows() { return rows; }
        public String getNaturalLanguageResponse() { return naturalLanguageResponse; }
        public String getSqlExecuted() { return sqlExecuted; }
        public boolean hasData() { return rows != null && !rows.isEmpty(); }
    }

    // ===== BATCH CLEANING INNER CLASSES =====
    public static class RowData {
        public final int rowId;
        public final String value;
        public RowData(int rowId, String value) {
            this.rowId = rowId;
            this.value = value != null ? value : "";
        }
    }

    public static class BatchChange {
        public final int rowId;
        public final String original;
        public final String cleaned;
        public BatchChange(int rowId, String original, String cleaned) {
            this.rowId = rowId;
            this.original = original;
            this.cleaned = cleaned;
        }
        @Override
        public String toString() {
            return "Row " + rowId + ": \"" + original + "\" → \"" + cleaned + "\"";
        }
    }

    public static class BatchCleanResult {
        public final int totalRowsProcessed;
        public final int totalBatchesProcessed;
        public final List<BatchChange> changes;
        public BatchCleanResult(int totalRowsProcessed, int totalBatchesProcessed, List<BatchChange> changes) {
            this.totalRowsProcessed = totalRowsProcessed;
            this.totalBatchesProcessed = totalBatchesProcessed;
            this.changes = changes;
        }
        public boolean hasChanges() { return !changes.isEmpty(); }
        public int getChangeCount() { return changes.size(); }
    }

    public interface ProgressCallback {
        void onProgress(double progress, String message);
    }

    // ===== COLUMN TYPE ENUM =====
    public enum ColumnType {
        COUNTRY, STATE, CITY, NAME, EMAIL, PHONE, NRIC, ADDRESS, POSTCODE, TEXT, UNKNOWN
    }

    // ===== COLUMN CLEANING RESULT =====
    public static class CleanColumnResult {
        private final String columnName;
        private final int totalRows;
        private final int rowsChanged;
        private final List<RowChange> changes;
        private final long processingTimeMs;

        public CleanColumnResult(String columnName, int totalRows, int rowsChanged,
                                 List<RowChange> changes, long processingTimeMs) {
            this.columnName = columnName;
            this.totalRows = totalRows;
            this.rowsChanged = rowsChanged;
            this.changes = changes != null ? changes : new ArrayList<>();
            this.processingTimeMs = processingTimeMs;
        }

        public String getColumnName() { return columnName; }
        public int getTotalRows() { return totalRows; }
        public int getRowsChanged() { return rowsChanged; }
        public List<RowChange> getChanges() { return changes; }
        public long getProcessingTimeMs() { return processingTimeMs; }
        public boolean hasChanges() { return !changes.isEmpty(); }
    }

    // ===== ROW CHANGE =====
    public static class RowChange {
        private final int rowId;
        private final String originalValue;
        private final String cleanedValue;

        public RowChange(int rowId, String originalValue, String cleanedValue) {
            this.rowId = rowId;
            this.originalValue = originalValue != null ? originalValue : "";
            this.cleanedValue = cleanedValue != null ? cleanedValue : "";
        }

        public int getRowId() { return rowId; }
        public String getOriginalValue() { return originalValue; }
        public String getCleanedValue() { return cleanedValue; }

        public boolean hasChanged() {
            return !originalValue.equals(cleanedValue);
        }
    }

    // ===== CONSTRUCTOR =====
    public QwenAgentService() {
        loadModel();
        dbManager = new DatabaseManager(DB_PATH);
        tryToLoadExistingDatabaseInfo();
        registerCleanupHook();
        buildCachedPrompts();
    }

    // ===== STOP CONTROL METHODS =====
    /**
     * Request to stop the current cleaning operation
     */
    public void requestStop() {
        shouldStop = true;
        if (currentThread != null) {
            currentThread.interrupt();
        }
        System.out.println("⏹️ Stop requested for cleaning operation");
    }

    /**
     * Check if stop has been requested
     */
    public boolean isStopRequested() {
        return shouldStop;
    }

    /**
     * Reset stop flag
     */
    public void resetStopFlag() {
        shouldStop = false;
        currentThread = null;
    }

    // ===== BUILD CACHED PROMPTS =====
    private void buildCachedPrompts() {
        cachedSystemPrompt = buildSystemPrompt();
        cachedCleaningPrompt = buildCleaningPrompt();
    }

    // ===== REGISTER CLEANUP HOOK =====
    private void registerCleanupHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("🧹 Cleaning up Qwen model...");
            unloadModel();
        }));
    }

    // ===== LOAD MODEL =====
    private void loadModel() {
        if (isLoading) return;
        isLoading = true;

        try {
            File modelFile = new File(MODEL_PATH);
            if (!modelFile.exists()) {
                System.err.println("⚠️ Qwen model not found at: " + MODEL_PATH);
                isLoading = false;
                return;
            }

            System.out.println("🔄 Loading Qwen model from: " + modelFile.getAbsolutePath());

            ModelParameters modelParams = new ModelParameters();
            modelParams.setModel(modelFile.getAbsolutePath());
            modelParams.setCtxSize(8192);
            modelParams.setThreads(4);
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

    // ===== UNLOAD MODEL =====
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

    public boolean isAvailable() {
        return isModelLoaded && model != null;
    }

    // ===== BUILD SYSTEM PROMPT (cached once) =====
    private String buildSystemPrompt() {
        String schemaInfo = getDatabaseSchemaInfo();
        return "<|im_start|>system\n" +
                "You are Qwen, a friendly data assistant that answers questions about data in a SQLite database.\n\n" +
                "IMPORTANT BEHAVIOR RULES:\n" +
                "1. Respond in natural language first - explain what you found in plain English.\n" +
                "2. Then provide the SQL query that generated your answer.\n" +
                "3. NEVER ask for confirmation on SELECT queries - just run them and show results.\n" +
                "4. ONLY ask for confirmation when the user explicitly says 'update', 'change', 'fix', or 'correct'.\n" +
                "5. If the user asks to 'summarize', 'show', 'list', 'find', or 'get' - use SELECT.\n" +
                "6. DO NOT fabricate data. Only use columns that exist in the schema.\n" +
                "7. Be conversational and helpful - explain what the data shows.\n\n" +
                "CRITICAL SQL SYNTAX RULES:\n" +
                "1. NEVER use 'database.table' format. Use only table names like 'Sheet1'.\n" +
                "2. Column names are case-sensitive: use EXACTLY as shown in schema.\n" +
                "3. Use double quotes around table/column names if they have spaces.\n" +
                "4. For text values, use single quotes: 'value'\n\n" +
                "EXAMPLE RESPONSE FORMAT:\n" +
                "User: 'show me all names'\n" +
                "You: \"I found 5 students in the dataset. Here are their names:\"\n" +
                "```sql\n" +
                "SELECT Surname, Given_Name FROM Sheet1;\n" +
                "```\n\n" +
                "User: 'update the country column'\n" +
                "You: \"I need to confirm this update before proceeding.\"\n" +
                "CONFIRM_UPDATE: Updating Country column...\n" +
                "```sql\n" +
                "UPDATE Sheet1 SET Country = 'Malaysia' WHERE Country = 'MY';\n" +
                "```\n\n" +
                "DATABASE SCHEMA:\n" +
                schemaInfo + "\n\n" +
                "Remember: Be natural and helpful! Answer the user's question first in plain English.\n" +
                "<|im_end|>\n";
    }

    private String buildCleaningPrompt() {
        return "<|im_start|>system\n" +
                "You are a data cleaner. Clean the values I give you.\n" +
                "Rules:\n" +
                "- ADDRESS: 'No X, Jalan Name, Taman Name' format, add 'No' prefix\n" +
                "- CITY: Proper case, fix 'Johor Bahru', 'Kuala Lumpur', 'Iskandar Puteri'\n" +
                "- NAME: Proper case, fix 'ONG RONG ZHE' → 'Ong Rong Zhe'\n" +
                "- NRIC: 'YYMMDD-XX-NNNN' format\n" +
                "- PHONE: Malaysian format '0XXXXXXXXX', remove +60\n" +
                "- EMAIL: lowercase\n" +
                "- POSTCODE: 5 digits\n" +
                "- TEXT: proper case, remove extra spaces\n" +
                "Return ONLY JSON array of strings: [\"val1\", \"val2\", ...]\n" +
                "NO explanations, NO extra text, NO markdown blocks.\n" +
                "<|im_end|>\n";
    }

    // ===== CALL QWEN WITH CACHED SYSTEM PROMPT =====
    private String callQwen(String userPrompt) {
        if (model == null) {
            throw new RuntimeException("Qwen model is null");
        }

        try {
            String fullPrompt = cachedSystemPrompt + userPrompt;

            InferenceParameters inferParams = new InferenceParameters(fullPrompt)
                    .setTemperature(0.1F)
                    .setTopP(0.9F)
                    .setRepeatPenalty(1.1F)
                    .setStopStrings("<|im_end|>", "<|endoftext|>")
                    .setNPredict(1024);

            StringBuilder response = new StringBuilder();
            for (LlamaOutput output : model.generate(inferParams)) {
                response.append(output.toString());
            }

            String result = response.toString().trim();
            if (result.isEmpty()) {
                return "I couldn't generate a response.";
            }
            return result;

        } catch (Exception e) {
            throw new RuntimeException("Qwen inference failed: " + e.getMessage(), e);
        }
    }

    // ===== RAW QWEN CALL (no system prompt prepend) =====
    private String callQwenRaw(String fullPrompt) {
        if (model == null) {
            throw new RuntimeException("Qwen model is null");
        }

        try {
            InferenceParameters inferParams = new InferenceParameters(fullPrompt)
                    .setTemperature(0.1F)
                    .setTopP(0.9F)
                    .setRepeatPenalty(1.1F)
                    .setStopStrings("<|im_end|>", "<|endoftext|>")
                    .setNPredict(1024);

            StringBuilder response = new StringBuilder();
            for (LlamaOutput output : model.generate(inferParams)) {
                response.append(output.toString());
            }

            return response.toString().trim();

        } catch (Exception e) {
            throw new RuntimeException("Qwen inference failed: " + e.getMessage(), e);
        }
    }

    // ===== BATCH CLEANING WITH CACHED CLEANING PROMPT =====
    public BatchCleanResult cleanColumnInBatches(
            String tableName,
            String columnName,
            String columnType,
            ProgressCallback callback) {

        // ===== RESET AND STORE THREAD =====
        shouldStop = false;
        currentThread = Thread.currentThread();

        System.out.println("🧹 Cleaning: " + tableName + "." + columnName);
        System.out.println("   Type: " + columnType);

        List<BatchChange> allChanges = new ArrayList<>();
        int totalRowsProcessed = 0;
        int totalBatchesProcessed = 0;
        long startTime = System.currentTimeMillis();

        try (Connection conn = dbManager.getConnection()) {
            String selectSql = "SELECT _row_id, \"" + columnName + "\" FROM \"" + tableName + "\"" +
                    " WHERE \"" + columnName + "\" IS NOT NULL AND \"" + columnName + "\" != ''";

            List<RowData> allRows = new ArrayList<>();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(selectSql)) {
                while (rs.next()) {
                    allRows.add(new RowData(rs.getInt("_row_id"), rs.getString(columnName)));
                }
            }

            if (allRows.isEmpty()) {
                return new BatchCleanResult(0, 0, allChanges);
            }

            int totalRows = allRows.size();
            int totalBatches = (int) Math.ceil((double) totalRows / 20);
            int currentIndex = 0;
            int batchNumber = 0;

            while (currentIndex < totalRows) {
                // ===== CHECK FOR STOP =====
                if (shouldStop || Thread.currentThread().isInterrupted()) {
                    System.out.println("⏹️ Batch cleaning stopped by user at row " + currentIndex + "/" + totalRows);
                    if (callback != null) {
                        callback.onProgress((double) currentIndex / totalRows,
                                "⏹️ Stopped by user (" + allChanges.size() + " changes applied)");
                    }
                    return new BatchCleanResult(totalRowsProcessed, totalBatchesProcessed, allChanges);
                }

                batchNumber++;
                int endIndex = Math.min(currentIndex + 20, totalRows);
                List<RowData> batch = allRows.subList(currentIndex, endIndex);

                if (callback != null) {
                    callback.onProgress((double) currentIndex / totalRows,
                            "Batch " + batchNumber + "/" + totalBatches);
                }

                StringBuilder userPrompt = new StringBuilder();
                userPrompt.append("<|im_start|>user\n");
                userPrompt.append("Column: ").append(columnName).append("\n");
                userPrompt.append("Type: ").append(columnType).append("\n");
                userPrompt.append("Clean these ").append(batch.size()).append(" values:\n");
                for (int i = 0; i < batch.size(); i++) {
                    userPrompt.append(i + 1).append(": ").append(batch.get(i).value).append("\n");
                }
                userPrompt.append("Return JSON array.\n");
                userPrompt.append("<|im_end|>\n");
                userPrompt.append("<|im_start|>assistant\n");

                String fullPrompt = cachedCleaningPrompt + userPrompt;
                String response = callQwenRaw(fullPrompt);
                List<String> cleanedValues = parseCleanedBatch(response, batch.size());

                if (!cleanedValues.isEmpty()) {
                    List<BatchChange> batchChanges = applyBatchChanges(
                            conn, tableName, columnName, batch, cleanedValues
                    );
                    allChanges.addAll(batchChanges);
                }

                totalRowsProcessed += batch.size();
                totalBatchesProcessed++;
                currentIndex = endIndex;

                if (callback != null) {
                    callback.onProgress((double) currentIndex / totalRows,
                            "Batch " + batchNumber + "/" + totalBatches + " done - " +
                                    allChanges.size() + " changes");
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            System.out.println("✅ Complete: " + allChanges.size() + " changes in " +
                    totalBatchesProcessed + " batches (" + duration + "ms)");

            return new BatchCleanResult(totalRowsProcessed, totalBatchesProcessed, allChanges);

        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
            return new BatchCleanResult(totalRowsProcessed, totalBatchesProcessed, allChanges);
        }
    }

    // ===== APPLY BATCH CHANGES =====
    private List<BatchChange> applyBatchChanges(
            Connection conn,
            String tableName,
            String columnName,
            List<RowData> batch,
            List<String> cleanedValues) throws SQLException {

        List<BatchChange> changes = new ArrayList<>();

        if (cleanedValues.size() != batch.size()) {
            System.err.println("   ⚠️ Size mismatch: " + cleanedValues.size() + " vs " + batch.size());
            return changes;
        }

        conn.setAutoCommit(false);
        String updateSql = "UPDATE \"" + tableName + "\" SET \"" + columnName + "\" = ? WHERE _row_id = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(updateSql)) {
            for (int i = 0; i < batch.size(); i++) {
                RowData row = batch.get(i);
                String cleaned = cleanedValues.get(i).trim();

                if (!cleaned.isEmpty() && !cleaned.equals(row.value)) {
                    pstmt.setString(1, cleaned);
                    pstmt.setInt(2, row.rowId);
                    pstmt.addBatch();
                    changes.add(new BatchChange(row.rowId, row.value, cleaned));
                }
            }

            if (!changes.isEmpty()) {
                pstmt.executeBatch();
                conn.commit();
                System.out.println("   ✅ " + changes.size() + " changes");
            }
        }

        return changes;
    }

    // ===== PARSE CLEANED BATCH =====
    private List<String> parseCleanedBatch(String response, int expectedSize) {
        List<String> values = new ArrayList<>();
        if (response == null || response.isEmpty()) return values;

        try {
            int start = response.indexOf('[');
            int end = response.lastIndexOf(']');
            if (start == -1 || end == -1 || end <= start) {
                Pattern p = Pattern.compile("\"([^\"]*)\"");
                Matcher m = p.matcher(response);
                while (m.find()) values.add(m.group(1));
                return values;
            }

            String json = response.substring(start + 1, end);
            if (json.trim().isEmpty()) return values;

            String[] parts = json.split(",");
            for (String part : parts) {
                String cleaned = part.trim().replaceAll("^\"|\"$", "").trim();
                if (!cleaned.isEmpty() || values.size() < expectedSize) {
                    values.add(cleaned);
                }
            }

        } catch (Exception e) {
            System.err.println("   ⚠️ Parse error: " + e.getMessage());
        }

        while (values.size() < expectedSize) values.add("");
        return values;
    }

    // ================================================================
    // ===== COLUMN TYPE DETECTION =====
    // ================================================================

    public ColumnType detectColumnType(String columnName, List<String> sampleValues) {
        String lower = columnName.toLowerCase();

        if (lower.contains("country") || lower.contains("nationality")) {
            return ColumnType.COUNTRY;
        }
        if (lower.contains("state") || lower.contains("region") || lower.contains("province")) {
            return ColumnType.STATE;
        }
        if (lower.contains("city") || lower.contains("town") || lower.contains("suburb")) {
            return ColumnType.CITY;
        }
        if (lower.contains("surname") || lower.contains("given name") ||
                (lower.contains("name") && !lower.contains("country") && !lower.contains("city"))) {
            return ColumnType.NAME;
        }
        if (lower.contains("email") || lower.contains("e-mail")) {
            return ColumnType.EMAIL;
        }
        if (lower.contains("phone") || lower.contains("mobile") || lower.contains("tel") ||
                (lower.contains("contact") && !lower.contains("email"))) {
            return ColumnType.PHONE;
        }
        if (lower.contains("nric") || lower.contains("ic") || lower.contains("passport") ||
                lower.contains("id number") || lower.contains("identification")) {
            return ColumnType.NRIC;
        }
        if (lower.contains("address") || lower.contains("street") || lower.contains("jalan") ||
                lower.contains("taman") || lower.contains("lot")) {
            return ColumnType.ADDRESS;
        }
        if (lower.contains("postcode") || lower.contains("zip") || lower.contains("postal") ||
                lower.contains("post code")) {
            return ColumnType.POSTCODE;
        }

        if (sampleValues != null && !sampleValues.isEmpty()) {
            boolean allEmail = sampleValues.stream().limit(10).allMatch(
                    v -> v != null && v.contains("@") && v.contains("."));
            if (allEmail) return ColumnType.EMAIL;

            boolean allPhone = sampleValues.stream().limit(10).allMatch(
                    v -> v != null && v.matches("^[0-9\\+\\s\\-()]+$"));
            if (allPhone) return ColumnType.PHONE;

            boolean allNric = sampleValues.stream().limit(10).allMatch(
                    v -> v != null && (v.matches("^\\d{12}$") || v.matches("^\\d{6}-\\d{2}-\\d{4}$")));
            if (allNric) return ColumnType.NRIC;

            boolean allPostcode = sampleValues.stream().limit(10).allMatch(
                    v -> v != null && v.matches("^\\d{5}$"));
            if (allPostcode) return ColumnType.POSTCODE;
        }

        return ColumnType.TEXT;
    }

    // ================================================================
    // ===== BUILD COLUMN CLEANING PROMPT =====
    // ================================================================

    private String buildColumnCleaningPrompt(ColumnType type, String columnName, List<RowValue> batch) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("<|im_start|>system\n");
        prompt.append("You are a data cleaner. Clean the values I give you based on the column type.\n\n");
        prompt.append("Column: ").append(columnName).append("\n");
        prompt.append("Type: ").append(type.name()).append("\n\n");

        switch (type) {
            case COUNTRY:
                prompt.append("Rules for COUNTRY column:\n");
                prompt.append("1. Convert to Proper Case: \"malaysia\" → \"Malaysia\"\n");
                prompt.append("2. Fix abbreviations: \"USA\" → \"United States\", \"UK\" → \"United Kingdom\"\n");
                prompt.append("3. Fix common names: \"uae\" → \"United Arab Emirates\"\n");
                prompt.append("4. Fix spacing: \"sri lanka\" → \"Sri Lanka\"\n");
                prompt.append("5. Fix special cases: \"MY\" → \"Malaysia\"\n");
                break;
            case STATE:
                prompt.append("Rules for STATE/REGION column:\n");
                prompt.append("1. Convert to Proper Case: \"selangor\" → \"Selangor\"\n");
                prompt.append("2. Fix common names: \"KL\" → \"Kuala Lumpur\"\n");
                prompt.append("3. Fix abbreviations: \"SEL\" → \"Selangor\"\n");
                break;
            case CITY:
                prompt.append("Rules for CITY column:\n");
                prompt.append("1. Convert to Proper Case: \"johor bahru\" → \"Johor Bahru\"\n");
                prompt.append("2. Fix common names: \"KL\" → \"Kuala Lumpur\"\n");
                prompt.append("3. Fix multi-word cities: \"kuala lumpur\" → \"Kuala Lumpur\"\n");
                break;
            case NAME:
                prompt.append("Rules for NAME column:\n");
                prompt.append("1. Convert to Proper Case: \"ong rong zhe\" → \"Ong Rong Zhe\"\n");
                prompt.append("2. Fix ALL CAPS: \"ONG RONG ZHE\" → \"Ong Rong Zhe\"\n");
                prompt.append("3. Remove extra spaces\n");
                break;
            case EMAIL:
                prompt.append("Rules for EMAIL column:\n");
                prompt.append("1. Convert to lowercase\n");
                prompt.append("2. Remove spaces\n");
                prompt.append("3. Fix common typos (e.g., \"gmial.com\" → \"gmail.com\")\n");
                break;
            case PHONE:
                prompt.append("Rules for PHONE column:\n");
                prompt.append("1. Malaysian format: \"0123456789\"\n");
                prompt.append("2. Remove +60 prefix: \"+60123456789\" → \"0123456789\"\n");
                prompt.append("3. Remove spaces, brackets, hyphens\n");
                prompt.append("4. Ensure starts with 0\n");
                break;
            case NRIC:
                prompt.append("Rules for NRIC column:\n");
                prompt.append("1. Format: \"YYMMDD-XX-NNNN\"\n");
                prompt.append("2. If 12 digits without hyphens, add them\n");
                break;
            case ADDRESS:
                prompt.append("Rules for ADDRESS column:\n");
                prompt.append("1. Format: \"No X, Jalan Name, Taman Name\"\n");
                prompt.append("2. Add 'No' prefix if missing\n");
                break;
            case POSTCODE:
                prompt.append("Rules for POSTCODE column:\n");
                prompt.append("1. Ensure 5 digits\n");
                prompt.append("2. Remove any non-numeric characters\n");
                break;
            case TEXT:
            case UNKNOWN:
            default:
                prompt.append("Rules for TEXT column:\n");
                prompt.append("1. Convert to Proper Case\n");
                prompt.append("2. Remove extra spaces\n");
                break;
        }

        prompt.append("\nReturn ONLY a JSON array of cleaned strings: [\"val1\", \"val2\", ...]\n");
        prompt.append("NO explanations, NO extra text, NO markdown code blocks.\n");
        prompt.append("<|im_end|>\n");

        prompt.append("<|im_start|>user\n");
        prompt.append("Clean these ").append(batch.size()).append(" values:\n");

        for (int i = 0; i < batch.size(); i++) {
            prompt.append(i + 1).append(": ").append(batch.get(i).value).append("\n");
        }

        prompt.append("Return ONLY JSON array.\n");
        prompt.append("<|im_end|>\n");
        prompt.append("<|im_start|>assistant\n");

        return prompt.toString();
    }

    // ================================================================
    // ===== COLUMN CLEANING METHOD WITH STOP SUPPORT =====
    // ================================================================

    public CompletableFuture<CleanColumnResult> cleanColumn(
            String tableName,
            String columnName,
            int batchSize,
            ProgressCallback callback) {

        return CompletableFuture.supplyAsync(() -> {
            // ===== RESET AND STORE THREAD =====
            shouldStop = false;
            currentThread = Thread.currentThread();

            long startTime = System.currentTimeMillis();
            List<RowChange> allChanges = new ArrayList<>();

            try (Connection conn = dbManager.getConnection()) {
                String selectSql = "SELECT _row_id, \"" + columnName + "\" FROM \"" + tableName + "\"" +
                        " WHERE \"" + columnName + "\" IS NOT NULL AND \"" + columnName + "\" != ''";

                List<RowValue> allRows = new ArrayList<>();
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(selectSql)) {
                    while (rs.next()) {
                        allRows.add(new RowValue(rs.getInt("_row_id"), rs.getString(columnName)));
                    }
                }

                int totalRows = allRows.size();
                if (totalRows == 0) {
                    return new CleanColumnResult(columnName, 0, 0, allChanges,
                            System.currentTimeMillis() - startTime);
                }

                List<String> samples = allRows.stream().limit(10)
                        .map(r -> r.value)
                        .filter(v -> v != null && !v.isEmpty())
                        .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

                ColumnType type = detectColumnType(columnName, samples);
                System.out.println("🔍 Detected column type: " + type + " for: " + columnName);

                int totalBatches = (int) Math.ceil((double) totalRows / batchSize);
                int currentIndex = 0;
                int batchNumber = 0;
                int totalChanges = 0;

                while (currentIndex < totalRows) {
                    // ===== CHECK FOR STOP =====
                    if (shouldStop || Thread.currentThread().isInterrupted()) {
                        System.out.println("⏹️ Cleaning stopped by user at row " + currentIndex + "/" + totalRows);
                        if (callback != null) {
                            callback.onProgress((double) currentIndex / totalRows,
                                    "⏹️ Stopped by user (" + allChanges.size() + " changes applied)");
                        }
                        return new CleanColumnResult(columnName, totalRows, allChanges.size(),
                                allChanges, System.currentTimeMillis() - startTime);
                    }

                    batchNumber++;
                    int endIndex = Math.min(currentIndex + batchSize, totalRows);
                    List<RowValue> batch = allRows.subList(currentIndex, endIndex);

                    if (callback != null) {
                        double progress = (double) currentIndex / totalRows;
                        callback.onProgress(progress,
                                "Batch " + batchNumber + "/" + totalBatches + " - " +
                                        totalChanges + " changes so far");
                    }

                    String prompt = buildColumnCleaningPrompt(type, columnName, batch);
                    String response = callQwenRaw(prompt);

                    List<String> cleanedValues = parseCleanedBatch(response, batch.size());

                    List<RowChange> batchChanges = new ArrayList<>();
                    for (int i = 0; i < batch.size(); i++) {
                        RowValue row = batch.get(i);
                        String cleaned = cleanedValues.get(i).trim();

                        if (!cleaned.isEmpty() && !cleaned.equals(row.value)) {
                            RowChange change = new RowChange(row.rowId, row.value, cleaned);
                            batchChanges.add(change);
                            allChanges.add(change);
                        }
                    }

                    if (!batchChanges.isEmpty()) {
                        applyRowChanges(conn, tableName, columnName, batchChanges);
                        totalChanges += batchChanges.size();
                        System.out.println("   ✅ Batch " + batchNumber + ": " +
                                batchChanges.size() + " changes applied");
                    }

                    currentIndex = endIndex;
                }

                long duration = System.currentTimeMillis() - startTime;
                System.out.println("✅ Column cleaning complete: " +
                        totalChanges + " changes in " + totalRows + " rows (" + duration + "ms)");

                return new CleanColumnResult(columnName, totalRows, totalChanges,
                        allChanges, duration);

            } catch (SQLException e) {
                System.err.println("❌ Database error: " + e.getMessage());
                e.printStackTrace();
                return new CleanColumnResult(columnName, 0, 0, allChanges,
                        System.currentTimeMillis() - startTime);
            } catch (Exception e) {
                System.err.println("❌ Error cleaning column: " + e.getMessage());
                e.printStackTrace();
                return new CleanColumnResult(columnName, 0, 0, allChanges,
                        System.currentTimeMillis() - startTime);
            }
        });
    }

    // ================================================================
    // ===== APPLY ROW CHANGES =====
    // ================================================================

    private void applyRowChanges(Connection conn, String tableName, String columnName,
                                 List<RowChange> changes) throws SQLException {
        if (changes.isEmpty()) return;

        String updateSql = "UPDATE \"" + tableName + "\" SET \"" + columnName + "\" = ? WHERE _row_id = ?";

        conn.setAutoCommit(false);
        try (PreparedStatement pstmt = conn.prepareStatement(updateSql)) {
            for (RowChange change : changes) {
                pstmt.setString(1, change.getCleanedValue());
                pstmt.setInt(2, change.getRowId());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        }
    }

    // ================================================================
    // ===== ROW VALUE HELPER CLASS =====
    // ================================================================

    private static class RowValue {
        final int rowId;
        final String value;

        RowValue(int rowId, String value) {
            this.rowId = rowId;
            this.value = value != null ? value : "";
        }
    }

    // ================================================================
    // ===== CHAT METHOD =====
    // ================================================================

    public CompletableFuture<QueryResult> chat(String userMessage, String dataContext) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isModelLoaded || model == null) {
                return new QueryResult(
                        Collections.emptyList(),
                        Collections.emptyList(),
                        "⚠️ Qwen model is not loaded.",
                        ""
                );
            }

            try {
                if (awaitingConfirmation && isConfirmation(userMessage)) {
                    String result = executePendingUpdate();
                    return new QueryResult(
                            Collections.emptyList(),
                            Collections.emptyList(),
                            result,
                            pendingUpdateSQL
                    );
                }

                if (awaitingConfirmation && isCancellation(userMessage)) {
                    awaitingConfirmation = false;
                    pendingUpdateSQL = "";
                    pendingUpdateDescription = "";
                    return new QueryResult(
                            Collections.emptyList(),
                            Collections.emptyList(),
                            "✅ Update cancelled. No changes were made.",
                            ""
                    );
                }

                String prompt = cachedSystemPrompt +
                        "<|im_start|>user\n" +
                        userMessage + "\n\n" +
                        "Generate a SQL query to answer this request, AND provide a brief natural language summary of what the data shows.\n" +
                        "Format your response as:\n" +
                        "1. A brief natural language summary (1-2 sentences)\n" +
                        "2. The SQL query in a ```sql code block\n" +
                        "<|im_end|>\n" +
                        "<|im_start|>assistant\n";

                String response = callQwen(prompt);

                if (response.contains("CONFIRM_UPDATE:") && !isUpdateRequest(userMessage)) {
                    String sqlQuery = extractSQL(response);
                    if (sqlQuery != null && !sqlQuery.isEmpty()) {
                        String upperSql = sqlQuery.toUpperCase().trim();
                        if (upperSql.startsWith("SELECT") || upperSql.startsWith("WITH") || upperSql.contains("SELECT")) {
                            System.out.println("⚠️ Qwen incorrectly asked for confirmation on SELECT - executing directly");
                            try {
                                return executeAndFormatQuery(sqlQuery, userMessage);
                            } catch (Exception e) {
                                String betterSql = extractSQLFromText(response);
                                if (betterSql != null && !betterSql.isEmpty()) {
                                    try {
                                        return executeAndFormatQuery(betterSql, userMessage);
                                    } catch (Exception ex) {
                                        // Ignore
                                    }
                                }
                                return new QueryResult(
                                        Collections.emptyList(),
                                        Collections.emptyList(),
                                        "I couldn't generate a valid query. Please rephrase your question.",
                                        ""
                                );
                            }
                        }
                    }
                }

                if (response.contains("CONFIRM_UPDATE:")) {
                    String updateSQL = extractSQL(response);
                    String description = extractDescription(response);

                    if (!updateSQL.isEmpty() && isUpdateRequest(userMessage)) {
                        String preview = getUpdatePreview(updateSQL);
                        pendingUpdateSQL = updateSQL;
                        pendingUpdateDescription = description;
                        awaitingConfirmation = true;

                        String confirmMsg = "⚠️ **I need your confirmation before making changes.**\n\n" +
                                "**What I plan to do:**\n" + description + "\n\n" +
                                "**Preview of changes:**\n" + preview + "\n\n" +
                                "**Affected rows:** " + getAffectedRowCount(updateSQL) + "\n\n" +
                                "Type **\"Yes, apply changes\"** to confirm, or **\"Cancel\"** to abort.";

                        return new QueryResult(
                                Collections.emptyList(),
                                Collections.emptyList(),
                                confirmMsg,
                                updateSQL
                        );
                    } else {
                        String sqlQuery = extractSQL(response);
                        if (sqlQuery != null && !sqlQuery.isEmpty()) {
                            try {
                                return executeAndFormatQuery(sqlQuery, userMessage);
                            } catch (Exception e) {
                                String betterSql = extractSQLFromText(response);
                                if (betterSql != null && !betterSql.isEmpty()) {
                                    try {
                                        return executeAndFormatQuery(betterSql, userMessage);
                                    } catch (Exception ex) {
                                        // Ignore
                                    }
                                }
                            }
                        }
                    }
                }

                String sqlQuery = extractSQL(response);
                String naturalResponse = extractNaturalResponse(response);

                if (sqlQuery == null || sqlQuery.isEmpty()) {
                    sqlQuery = extractSQLFromText(response);
                }

                if (sqlQuery != null && !sqlQuery.isEmpty()) {
                    try {
                        return executeAndFormatQuery(sqlQuery, userMessage, naturalResponse);
                    } catch (Exception e) {
                        String betterSql = extractSQLFromText(response);
                        if (betterSql != null && !betterSql.isEmpty() && !betterSql.equals(sqlQuery)) {
                            try {
                                return executeAndFormatQuery(betterSql, userMessage, naturalResponse);
                            } catch (Exception ex) {
                                // Ignore
                            }
                        }
                        return new QueryResult(
                                Collections.emptyList(),
                                Collections.emptyList(),
                                "❌ I encountered an error: " + e.getMessage(),
                                sqlQuery
                        );
                    }
                }

                return new QueryResult(
                        Collections.emptyList(),
                        Collections.emptyList(),
                        response,
                        ""
                );

            } catch (Exception e) {
                System.err.println("❌ Error during chat: " + e.getMessage());
                e.printStackTrace();
                return new QueryResult(
                        Collections.emptyList(),
                        Collections.emptyList(),
                        "❌ I encountered an error: " + e.getMessage(),
                        ""
                );
            }
        });
    }

    // ===== SUGGEST CORRECTIONS =====
    public CompletableFuture<Map<String, String>> suggestCorrections(Map<String, String> rawRow) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isModelLoaded || model == null) {
                System.err.println("⚠️ Qwen model not loaded. Returning original row.");
                return new HashMap<>(rawRow);
            }

            try {
                String systemPrompt = "<|im_start|>system\n" +
                        "You are a data correction assistant. " +
                        "Analyze the geographic and demographic fields in the row. " +
                        "Fields to check: Country, Region, State, City, Postcode, Nationality, Home Address, " +
                        "Surname, Given Name, Name, Title, Email, Primary Contact, NRIC / Passport No.\n\n" +
                        "CORRECTION RULES:\n" +
                        "1. If a state is in the Country column, move it to State\n" +
                        "2. If a country is in the State column, move it to Country\n" +
                        "3. Names should be Proper Case (e.g., 'John Doe' not 'john doe' or 'JOHN DOE')\n" +
                        "4. Email addresses should be lowercase\n" +
                        "5. Phone numbers should be digits only, starting with 0\n" +
                        "6. NRIC should be in format: YYMMDD-XX-NNNN\n" +
                        "7. Date of Birth should be YYYY-MM-DD format\n" +
                        "8. Country names should be Proper Case\n\n" +
                        "Return a clean, corrected JSON object containing ONLY the modified keys. " +
                        "If nothing is wrong, return an empty JSON object {}.\n" +
                        "Do not include markdown formatting code blocks.\n" +
                        "Return ONLY valid JSON.\n" +
                        "<|im_end|>\n";

                String userContent = mapToJson(rawRow);

                String prompt = systemPrompt +
                        "<|im_start|>user\n" +
                        "Correct this row of data:\n" +
                        userContent + "\n" +
                        "Return ONLY the corrected fields as JSON.\n" +
                        "<|im_end|>\n" +
                        "<|im_start|>assistant\n";

                System.out.println("🔄 Sending correction request to Qwen...");
                long startTime = System.currentTimeMillis();

                String response = callQwen(prompt);

                long responseTime = System.currentTimeMillis() - startTime;
                System.out.println("✅ Qwen correction response received in " + responseTime + "ms");

                String jsonContent = extractJsonFromResponse(response);
                Map<String, String> corrections = parseJsonToMap(jsonContent);

                if (corrections.isEmpty()) {
                    System.out.println("   No corrections needed for this row.");
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

    // ================================================================
    // ===== HELPER METHODS =====
    // ================================================================

    private QueryResult executeAndFormatQuery(String sql, String userMessage) {
        return executeAndFormatQuery(sql, userMessage, null);
    }

    private QueryResult executeAndFormatQuery(String sql, String userMessage, String naturalResponse) {
        sql = cleanSQL(sql);
        List<Map<String, Object>> results = executeSQLQuery(sql);

        if (results.isEmpty()) {
            return new QueryResult(
                    Collections.emptyList(),
                    Collections.emptyList(),
                    "No results found for your query.",
                    sql
            );
        }

        List<String> columns = new ArrayList<>(results.get(0).keySet());

        String summary = naturalResponse;
        if (summary == null || summary.isEmpty()) {
            summary = generateNaturalSummary(results, columns, userMessage);
        }

        return new QueryResult(columns, results, summary, sql);
    }

    private String generateNaturalSummary(List<Map<String, Object>> results, List<String> columns, String userMessage) {
        StringBuilder summary = new StringBuilder();
        int rowCount = results.size();

        if (rowCount == 1) {
            summary.append("Found 1 record");
        } else {
            summary.append("Found ").append(rowCount).append(" records");
        }

        if (columns.size() == 1) {
            summary.append(" with 1 column: ").append(columns.get(0));
        } else {
            summary.append(" with ").append(columns.size()).append(" columns");
        }

        if (rowCount <= 5 && columns.size() >= 2) {
            summary.append("\n\n");
            for (Map<String, Object> row : results) {
                summary.append("• ");
                int i = 0;
                for (String col : columns) {
                    if (i > 0) summary.append(", ");
                    Object val = row.get(col);
                    summary.append(col).append(": ").append(val == null ? "null" : val.toString());
                    i++;
                    if (i >= 3) break;
                }
                summary.append("\n");
            }
        }

        return summary.toString();
    }

    private List<Map<String, Object>> executeSQLQuery(String sql) {
        List<Map<String, Object>> results = new ArrayList<>();
        sql = cleanSQL(sql);

        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();
            int rowLimit = 100;

            while (rs.next() && results.size() < rowLimit) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= colCount; i++) {
                    String colName = meta.getColumnName(i);
                    row.put(colName, rs.getObject(i));
                }
                results.add(row);
            }

        } catch (SQLException e) {
            System.err.println("❌ SQL Error: " + e.getMessage());
            System.err.println("   SQL: " + sql);
            throw new RuntimeException("Failed to execute SQL: " + e.getMessage());
        }
        return results;
    }

    private String cleanSQL(String sql) {
        if (sql == null) return sql;
        String cleaned = sql.replaceAll("(?i)bistro_temp\\.db\\.", "");
        cleaned = cleaned.replaceAll("(?i)[a-zA-Z0-9_]+\\.db\\.", "");
        cleaned = cleaned.replaceAll("(?i)[a-zA-Z0-9_]+\\.[a-zA-Z0-9_]+\\.", "");
        cleaned = cleaned.replaceAll("\\.\\s+", ".");
        cleaned = cleaned.replaceAll(";+$", ";");
        return cleaned;
    }

    private String extractSQL(String response) {
        int start = response.indexOf("```sql");
        if (start != -1) {
            start += 6;
            int end = response.indexOf("```", start);
            if (end != -1) {
                return cleanSQL(response.substring(start, end).trim());
            }
        }
        String[] keywords = {"SELECT", "UPDATE", "INSERT", "DELETE", "WITH"};
        for (String keyword : keywords) {
            int idx = response.toUpperCase().indexOf(keyword);
            if (idx != -1) {
                return cleanSQL(response.substring(idx).trim());
            }
        }
        return cleanSQL(response.trim());
    }

    private String extractSQLFromText(String response) {
        if (response == null) return null;

        Pattern selectPattern = Pattern.compile("SELECT\\s+.*?(?:FROM\\s+[^;]+)?;?", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher matcher = selectPattern.matcher(response);
        if (matcher.find()) {
            return cleanSQL(matcher.group().trim());
        }

        Pattern withPattern = Pattern.compile("WITH\\s+.*?SELECT\\s+.*?;?", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        matcher = withPattern.matcher(response);
        if (matcher.find()) {
            return cleanSQL(matcher.group().trim());
        }

        Pattern countPattern = Pattern.compile("COUNT\\s*\\(\\s*\\*\\s*\\)\\s*FROM\\s+[^;]+;?", Pattern.CASE_INSENSITIVE);
        matcher = countPattern.matcher(response);
        if (matcher.find()) {
            return cleanSQL("SELECT " + matcher.group().trim());
        }

        return null;
    }

    private String extractNaturalResponse(String response) {
        if (response == null) return "";
        String cleaned = response.replaceAll("```sql.*?```", "");
        cleaned = cleaned.replaceAll("```.*?```", "");
        cleaned = cleaned.replaceAll("CONFIRM_UPDATE:.*?\\n", "");
        cleaned = cleaned.trim();

        String upper = cleaned.toUpperCase();
        if (upper.startsWith("SELECT") || upper.startsWith("WITH") || upper.startsWith("UPDATE")) {
            return "";
        }
        return cleaned;
    }

    private String extractDescription(String response) {
        int start = response.indexOf("CONFIRM_UPDATE:");
        if (start != -1) {
            start += 15;
            int end = response.indexOf("\n", start);
            if (end == -1) end = response.length();
            return response.substring(start, end).trim();
        }
        return "Update data in the database";
    }

    private boolean isUpdateRequest(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase();
        return lower.contains("update") || lower.contains("change") || lower.contains("fix") ||
                lower.contains("correct") || lower.contains("replace") || lower.contains("modify") ||
                lower.contains("set") || lower.contains("overwrite") || lower.contains("amend") ||
                lower.contains("revise");
    }

    private boolean isConfirmation(String message) {
        String lower = message.toLowerCase();
        return lower.contains("yes") || lower.contains("confirm") || lower.contains("apply") ||
                lower.contains("proceed") || lower.contains("do it") || lower.contains("go ahead") ||
                lower.contains("ok") || lower.contains("sure") || lower.contains("yeah");
    }

    private boolean isCancellation(String message) {
        String lower = message.toLowerCase();
        return lower.contains("cancel") || lower.contains("no") || lower.contains("stop") ||
                lower.contains("abort") || lower.contains("nevermind") || lower.contains("nope") ||
                lower.contains("forget");
    }

    private String executePendingUpdate() {
        if (pendingUpdateSQL.isEmpty()) {
            return "❌ No pending update to execute.";
        }

        try {
            int affected = executeUpdateSQL(pendingUpdateSQL);
            awaitingConfirmation = false;
            String description = pendingUpdateDescription;
            pendingUpdateSQL = "";
            pendingUpdateDescription = "";

            return "✅ **Update completed successfully!**\n\n" +
                    "**Action:** " + description + "\n" +
                    "**Rows affected:** " + affected + "\n\n" +
                    "The database has been updated. You can verify the changes by asking a query.";
        } catch (SQLException e) {
            awaitingConfirmation = false;
            pendingUpdateSQL = "";
            pendingUpdateDescription = "";
            return "❌ Failed to execute update: " + e.getMessage();
        }
    }

    private String getUpdatePreview(String updateSQL) {
        try {
            String selectSQL = convertUpdateToSelect(updateSQL);
            if (selectSQL == null) return "Preview not available for this type of update.";
            List<Map<String, Object>> results = executeSQLQuery(selectSQL + " LIMIT 5");
            if (results.isEmpty()) return "No rows will be affected.";

            StringBuilder preview = new StringBuilder();
            preview.append("First 5 rows that will be affected:\n\n");
            for (Map<String, Object> row : results) {
                preview.append(row.toString()).append("\n");
            }
            return preview.toString();
        } catch (Exception e) {
            return "Preview not available: " + e.getMessage();
        }
    }

    private int getAffectedRowCount(String updateSQL) {
        try {
            String countSQL = convertUpdateToCount(updateSQL);
            if (countSQL == null) return 0;
            List<Map<String, Object>> results = executeSQLQuery(countSQL);
            if (results.isEmpty()) return 0;
            return ((Number) results.get(0).values().iterator().next()).intValue();
        } catch (Exception e) {
            return 0;
        }
    }

    private String convertUpdateToSelect(String updateSQL) {
        String upper = updateSQL.toUpperCase();
        if (!upper.startsWith("UPDATE")) return null;

        String afterUpdate = updateSQL.substring(6).trim();
        int setIndex = afterUpdate.toUpperCase().indexOf("SET");
        if (setIndex == -1) return null;

        String tableName = afterUpdate.substring(0, setIndex).trim();
        String afterSet = afterUpdate.substring(setIndex + 3).trim();
        int whereIndex = afterSet.toUpperCase().indexOf("WHERE");

        String whereClause = "";
        if (whereIndex != -1) {
            whereClause = afterSet.substring(whereIndex).trim();
        }

        return "SELECT * FROM " + tableName + " " + whereClause;
    }

    private String convertUpdateToCount(String updateSQL) {
        String selectSQL = convertUpdateToSelect(updateSQL);
        if (selectSQL == null) return null;
        return selectSQL.replace("*", "COUNT(*)");
    }

    private int executeUpdateSQL(String sql) throws SQLException {
        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement()) {
            conn.setAutoCommit(false);
            int affected = stmt.executeUpdate(sql);
            conn.commit();
            return affected;
        }
    }

    // ===== JSON HELPERS =====
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

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // ===== GET DATABASE SCHEMA INFO =====
    private String getDatabaseSchemaInfo() {
        StringBuilder schema = new StringBuilder();
        schema.append("Available tables (use these table names exactly):\n");

        try (Connection conn = dbManager.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            ResultSet tables = meta.getTables(null, null, "%", new String[]{"TABLE"});

            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                if (tableName.startsWith("sqlite_")) continue;

                schema.append("\nTABLE: \"").append(tableName).append("\"\n");
                schema.append("  Rows: ").append(sheetRowCounts.getOrDefault(tableName, 0)).append("\n");
                schema.append("  Columns:\n");

                ResultSet cols = meta.getColumns(null, null, tableName, null);
                while (cols.next()) {
                    String colName = cols.getString("COLUMN_NAME");
                    String colType = cols.getString("TYPE_NAME");
                    schema.append("    - \"").append(colName).append("\" (").append(colType).append(")\n");
                }
                cols.close();
                schema.append("  Example query: SELECT * FROM \"").append(tableName).append("\" LIMIT 1;\n");
            }
            tables.close();
        } catch (SQLException e) {
            return "Error reading schema: " + e.getMessage();
        }

        return schema.toString();
    }

    // ===== INGEST EXCEL TO DATABASE =====
    public CompletableFuture<Void> ingestExcelToDatabase(File excelFile) {
        return CompletableFuture.runAsync(() -> {
            try {
                System.out.println("📊 Importing Excel to SQLite: " + excelFile.getName());
                ExcelWorkbookService excelService = new ExcelWorkbookService();
                DatabaseSetupService dbSetup = new DatabaseSetupService(dbManager);

                List<SheetDataWrapper> sheets = excelService.parseExcelFile(excelFile);
                dbSetup.dropAllTables();
                dbSetup.createTables(sheets);

                for (SheetDataWrapper sheet : sheets) {
                    dbSetup.importSheet(sheet);
                    sheetRowCounts.put(sheet.getSheetName(), sheet.getRowCount());
                }

                this.trackedFileName = excelFile.getName();
                this.totalSheets = sheets.size();
                saveDatabaseInfo();
                buildCachedPrompts();

                System.out.println("✅ Excel imported: " + sheets.size() + " sheets, " +
                        sheetRowCounts.values().stream().mapToInt(Integer::intValue).sum() + " rows");

            } catch (Exception e) {
                System.err.println("❌ Failed to import Excel: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    // ===== SAVE DATABASE INFO =====
    private void saveDatabaseInfo() {
        Properties props = new Properties();
        props.setProperty("fileName", trackedFileName);
        props.setProperty("totalSheets", String.valueOf(totalSheets));
        for (Map.Entry<String, Integer> entry : sheetRowCounts.entrySet()) {
            props.setProperty("sheet." + entry.getKey(), String.valueOf(entry.getValue()));
        }
        try (FileOutputStream out = new FileOutputStream("app_data/database_info.properties")) {
            props.store(out, "Database Info");
        } catch (IOException e) {
            System.err.println("⚠️ Could not save database info: " + e.getMessage());
        }
        buildCachedPrompts();
    }

    private void tryToLoadExistingDatabaseInfo() {
        File infoFile = new File("app_data/database_info.properties");
        if (!infoFile.exists()) return;

        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(infoFile)) {
            props.load(in);
            trackedFileName = props.getProperty("fileName", "");
            totalSheets = Integer.parseInt(props.getProperty("totalSheets", "0"));
            for (String key : props.stringPropertyNames()) {
                if (key.startsWith("sheet.")) {
                    String sheetName = key.substring(6);
                    sheetRowCounts.put(sheetName, Integer.parseInt(props.getProperty(key)));
                }
            }
            System.out.println("📂 Loaded database info: " + trackedFileName);
        } catch (Exception e) {
            System.err.println("⚠️ Could not load database info: " + e.getMessage());
        }
    }

    // ===== GETTERS =====
    public DatabaseManager getDatabaseManager() {
        return dbManager;
    }

    public boolean hasCachedData() {
        return !sheetRowCounts.isEmpty();
    }

    public String getTrackedFileName() {
        return trackedFileName;
    }

    public int getTotalRowsCached() {
        return sheetRowCounts.values().stream().mapToInt(Integer::intValue).sum();
    }

    public Map<String, Integer> getSheetRowCounts() {
        return sheetRowCounts;
    }

    public String getStatus() {
        if (isLoading) return "⏳ Loading Qwen model...";
        if (isAvailable()) return "✅ Qwen model ready";
        return "❌ Qwen model not available";
    }

    public void ingestDatasetSnapshot(String fileName, List<Map<String, String>> datasetRows) {
        if (datasetRows != null && !datasetRows.isEmpty()) {
            this.trackedFileName = fileName;
            this.totalSheets = 1;
            this.sheetRowCounts.clear();
            this.sheetRowCounts.put("Data", datasetRows.size());
            System.out.println("📦 Dataset cached: " + fileName + " (" + datasetRows.size() + " rows)");
        }
    }
}
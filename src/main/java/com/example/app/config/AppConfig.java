package com.example.app.config;

/**
 * Application configuration constants
 */
public class AppConfig {

    // Database configuration
    public static final String APP_DATA_DIR = "app_data";
    public static final String DATABASE_FILE = APP_DATA_DIR + "/bistro_temp.db";
    public static final String CACHE_DIR = APP_DATA_DIR + "/knowledge_base";
    public static final String CACHE_FILE = CACHE_DIR + "/active_matrix.json";

    // Model configuration
    public static final String MODEL_DIR = "app_models";
    public static final String QWEN_MODEL = MODEL_DIR + "/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf";

    // SQLite configuration
    public static final int SQLITE_BATCH_SIZE = 500;
    public static final int SQLITE_CACHE_SIZE = 10000;

    // AI configuration
    public static final int AI_CONTEXT_SIZE = 8192;
    public static final int AI_MAX_TOKENS = 512;
    public static final float AI_TEMPERATURE = 0.1f;
    public static final float AI_TOP_P = 0.9f;
    public static final float AI_REPEAT_PENALTY = 1.1f;

    // Chat configuration
    public static final int MAX_CONTEXT_ROWS = 40;
    public static final int SAMPLE_ROWS = 5;

    // File configuration
    public static final String[] SUPPORTED_EXTENSIONS = {".csv", ".xlsx", ".xls"};

    /**
     * Get the absolute path for a relative path
     */
    public static String getAbsolutePath(String relativePath) {
        String workingDir = System.getProperty("user.dir");
        return workingDir + "/" + relativePath;
    }

    /**
     * Get the database URL
     */
    public static String getDatabaseUrl() {
        return "jdbc:sqlite:" + DATABASE_FILE;
    }

    private AppConfig() {
        // Private constructor to prevent instantiation
    }
}
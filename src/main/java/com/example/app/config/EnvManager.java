package com.example.app.config;

import java.io.*;
import java.util.Properties;

/**
 * Environment variable manager for secure configuration
 */
public class EnvManager {

    private static final String ENV_FILE = "app_data/.env";
    private static final Properties props = new Properties();

    static {
        loadEnvFile();
    }

    /**
     * Load environment variables from .env file
     */
    private static void loadEnvFile() {
        File envFile = new File(ENV_FILE);
        if (envFile.exists()) {
            try (FileInputStream fis = new FileInputStream(envFile)) {
                props.load(fis);
                System.out.println("📄 Loaded environment variables from: " + ENV_FILE);
            } catch (IOException e) {
                System.err.println("⚠️ Could not load .env file: " + e.getMessage());
            }
        } else {
            // Create default .env file if it doesn't exist
            createDefaultEnvFile();
        }
    }

    /**
     * Create a default .env file
     */
    private static void createDefaultEnvFile() {
        File envFile = new File(ENV_FILE);
        File parentDir = envFile.getParentFile();

        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        try (FileOutputStream fos = new FileOutputStream(envFile)) {
            props.setProperty("APP_MODE", "development");
            props.setProperty("LOG_LEVEL", "INFO");
            props.setProperty("MAX_ROWS", "100000");
            props.setProperty("ENABLE_TELEMETRY", "false");

            // For future use - API keys (commented out by default)
            // props.setProperty("OPENAI_API_KEY", "");
            // props.setProperty("ANTHROPIC_API_KEY", "");

            props.store(fos, "Application Environment Variables");
            System.out.println("📄 Created default .env file: " + ENV_FILE);
        } catch (IOException e) {
            System.err.println("⚠️ Could not create .env file: " + e.getMessage());
        }
    }

    /**
     * Get an environment variable
     */
    public static String get(String key) {
        // Check system properties first
        String value = System.getProperty(key);
        if (value != null && !value.isEmpty()) {
            return value;
        }

        // Then check .env file
        value = props.getProperty(key);
        if (value != null && !value.isEmpty()) {
            return value;
        }

        // Finally check environment variables
        value = System.getenv(key);
        return value != null ? value : "";
    }

    /**
     * Get an environment variable with a default value
     */
    public static String get(String key, String defaultValue) {
        String value = get(key);
        return value.isEmpty() ? defaultValue : value;
    }

    /**
     * Get an integer environment variable
     */
    public static int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(get(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Get a boolean environment variable
     */
    public static boolean getBoolean(String key, boolean defaultValue) {
        String value = get(key, String.valueOf(defaultValue));
        return Boolean.parseBoolean(value);
    }

    /**
     * Set an environment variable (runtime only)
     */
    public static void set(String key, String value) {
        props.setProperty(key, value);
    }

    /**
     * Save environment variables to .env file
     */
    public static void save() {
        File envFile = new File(ENV_FILE);
        try (FileOutputStream fos = new FileOutputStream(envFile)) {
            props.store(fos, "Application Environment Variables");
        } catch (IOException e) {
            System.err.println("⚠️ Could not save .env file: " + e.getMessage());
        }
    }

    /**
     * Check if running in development mode
     */
    public static boolean isDevelopmentMode() {
        return get("APP_MODE", "development").equalsIgnoreCase("development");
    }

    /**
     * Check if running in production mode
     */
    public static boolean isProductionMode() {
        return get("APP_MODE", "development").equalsIgnoreCase("production");
    }

    // ===== Convenience methods for common configuration =====

    public static int getMaxRows() {
        return getInt("MAX_ROWS", 100000);
    }

    public static String getLogLevel() {
        return get("LOG_LEVEL", "INFO");
    }

    public static boolean isTelemetryEnabled() {
        return getBoolean("ENABLE_TELEMETRY", false);
    }

    // ===== API Keys (future use) =====

    public static String getOpenAIKey() {
        return get("OPENAI_API_KEY");
    }

    public static String getAnthropicKey() {
        return get("ANTHROPIC_API_KEY");
    }
}
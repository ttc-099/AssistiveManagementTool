package com.example.app.db;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages SQLite database connections
 */
public class DatabaseManager {

    private final String dbPath;

    public DatabaseManager(String dbPath) {
        this.dbPath = dbPath;
        initializeDatabase();
    }

    /**
     * Initialize the database directory and connection
     */
    private void initializeDatabase() {
        try {
            // Ensure directory exists
            File dbFile = new File(dbPath);
            File parentDir = dbFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            // Load SQLite JDBC driver (connections open per-call in getConnection())
            Class.forName("org.sqlite.JDBC");

            System.out.println("📂 SQLite database initialized: " + dbPath);

        } catch (Exception e) {
            System.err.println("❌ Failed to initialize SQLite database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get a database connection.
     * Returns a FRESH connection each call: callers own it (try-with-resources) and
     * a shared cached connection caused races between the UI thread and background
     * AI tasks, plus leaked setAutoCommit(false) state across features.
     * WAL mode keeps concurrent readers safe.
     */
    public Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
            stmt.execute("PRAGMA journal_mode = WAL");
            stmt.execute("PRAGMA synchronous = NORMAL");
            stmt.execute("PRAGMA temp_store = MEMORY");
            stmt.execute("PRAGMA cache_size = 10000");
        }
        return conn;
    }

    /**
     * Close the legacy cached connection (kept for API compatibility; no-op now)
     */
    public void closeConnection() {
        // connections are opened and closed per-call by their owners now
    }

    /**
     * Check if a table exists
     */
    public boolean tableExists(String tableName) {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            String sql = "SELECT name FROM sqlite_master WHERE type='table' AND name='" + tableName + "'";
            var rs = stmt.executeQuery(sql);
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Get the database path
     */
    public String getDbPath() {
        return dbPath;
    }

    /**
     * Check if database has any tables
     */
    public boolean hasTables() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            var rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' LIMIT 1");
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
    }
}
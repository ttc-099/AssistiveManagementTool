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
    private Connection connection;

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

            // Load SQLite JDBC driver
            Class.forName("org.sqlite.JDBC");

            // Get connection
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);

            // Enable foreign keys
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON");
                stmt.execute("PRAGMA journal_mode = WAL");
                stmt.execute("PRAGMA synchronous = NORMAL");
                stmt.execute("PRAGMA temp_store = MEMORY");
                stmt.execute("PRAGMA cache_size = 10000");
            }

            System.out.println("📂 SQLite database initialized: " + dbPath);

        } catch (Exception e) {
            System.err.println("❌ Failed to initialize SQLite database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get a database connection
     */
    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        }
        return connection;
    }

    /**
     * Close the database connection
     */
    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("📂 SQLite connection closed");
            }
        } catch (SQLException e) {
            System.err.println("⚠️ Error closing SQLite connection: " + e.getMessage());
        }
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
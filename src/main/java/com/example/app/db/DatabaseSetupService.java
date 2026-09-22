package com.example.app.db;

import com.example.app.service.excel.SheetDataWrapper;

import java.sql.*;
import java.util.*;

public class DatabaseSetupService {

    private static final String DEBUG_PREFIX = "🗄️ [DatabaseSetupService]";
    private final DatabaseManager dbManager;

    public DatabaseSetupService(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public void dropAllTables() {
        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement()) {

            var rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'");
            List<String> tables = new ArrayList<>();
            while (rs.next()) {
                tables.add(rs.getString("name"));
            }

            for (String table : tables) {
                stmt.execute("DROP TABLE IF EXISTS \"" + table + "\"");
                System.out.println(DEBUG_PREFIX + " Dropped table: " + table);
            }

        } catch (SQLException e) {
            System.err.println(DEBUG_PREFIX + " Error dropping tables: " + e.getMessage());
        }
    }

    public void createTables(List<SheetDataWrapper> sheets) {
        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement()) {

            for (SheetDataWrapper sheet : sheets) {
                String tableName = sanitizeTableName(sheet.getSheetName());
                List<String> headers = sheet.getHeaders();

                if (headers.isEmpty()) {
                    System.out.println(DEBUG_PREFIX + " ⚠️ Sheet '" + sheet.getSheetName() + "' has no headers, skipping");
                    continue;
                }

                // ===== FIX: Handle duplicate column names =====
                Map<String, Integer> columnCounts = new HashMap<>();
                List<String> uniqueHeaders = new ArrayList<>();

                for (String header : headers) {
                    String sanitized = sanitizeColumnName(header);

                    // Check for duplicates
                    if (columnCounts.containsKey(sanitized)) {
                        int count = columnCounts.get(sanitized) + 1;
                        columnCounts.put(sanitized, count);
                        // Append number to make it unique
                        String uniqueName = sanitized + "_" + count;
                        uniqueHeaders.add(uniqueName);
                        System.out.println(DEBUG_PREFIX + "   ⚠️ Duplicate column: '" + header + "' → '" + uniqueName + "'");
                    } else {
                        columnCounts.put(sanitized, 1);
                        uniqueHeaders.add(sanitized);
                    }
                }

                // Build CREATE TABLE statement with unique column names
                StringBuilder createSQL = new StringBuilder();
                createSQL.append("CREATE TABLE IF NOT EXISTS \"").append(tableName).append("\" (");
                createSQL.append("_row_id INTEGER PRIMARY KEY AUTOINCREMENT, ");

                for (int i = 0; i < uniqueHeaders.size(); i++) {
                    String colName = uniqueHeaders.get(i);
                    createSQL.append("\"").append(colName).append("\" TEXT");
                    if (i < uniqueHeaders.size() - 1) {
                        createSQL.append(", ");
                    }
                }
                createSQL.append(")");

                stmt.execute(createSQL.toString());
                System.out.println(DEBUG_PREFIX + " Created table: " + tableName + " (" + uniqueHeaders.size() + " columns)");
            }

        } catch (SQLException e) {
            System.err.println(DEBUG_PREFIX + " Error creating tables: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void importSheet(SheetDataWrapper sheet) {
        String tableName = sanitizeTableName(sheet.getSheetName());
        List<String> headers = sheet.getHeaders();
        List<Map<String, String>> rows = sheet.getRows();

        if (headers.isEmpty() || rows.isEmpty()) {
            System.out.println(DEBUG_PREFIX + " ⚠️ No data to import for sheet: " + sheet.getSheetName());
            return;
        }

        try (Connection conn = dbManager.getConnection()) {
            conn.setAutoCommit(false);

            // ===== FIX: Use unique column names for INSERT =====
            Map<String, Integer> columnCounts = new HashMap<>();
            List<String> uniqueHeaders = new ArrayList<>();

            for (String header : headers) {
                String sanitized = sanitizeColumnName(header);
                if (columnCounts.containsKey(sanitized)) {
                    int count = columnCounts.get(sanitized) + 1;
                    columnCounts.put(sanitized, count);
                    uniqueHeaders.add(sanitized + "_" + count);
                } else {
                    columnCounts.put(sanitized, 1);
                    uniqueHeaders.add(sanitized);
                }
            }

            // Build INSERT statement
            StringBuilder insertSQL = new StringBuilder();
            insertSQL.append("INSERT INTO \"").append(tableName).append("\" (");

            for (int i = 0; i < uniqueHeaders.size(); i++) {
                insertSQL.append("\"").append(uniqueHeaders.get(i)).append("\"");
                if (i < uniqueHeaders.size() - 1) {
                    insertSQL.append(", ");
                }
            }
            insertSQL.append(") VALUES (");

            for (int i = 0; i < uniqueHeaders.size(); i++) {
                insertSQL.append("?");
                if (i < uniqueHeaders.size() - 1) {
                    insertSQL.append(", ");
                }
            }
            insertSQL.append(")");

            try (PreparedStatement pstmt = conn.prepareStatement(insertSQL.toString())) {
                int batchSize = 0;
                int totalRows = rows.size();

                for (Map<String, String> row : rows) {
                    for (int i = 0; i < uniqueHeaders.size(); i++) {
                        String header = (i < headers.size()) ? headers.get(i) : uniqueHeaders.get(i);
                        String value = row.getOrDefault(header, "");
                        pstmt.setString(i + 1, value);
                    }
                    pstmt.addBatch();
                    batchSize++;

                    if (batchSize >= 500) {
                        pstmt.executeBatch();
                        batchSize = 0;
                    }
                }

                if (batchSize > 0) {
                    pstmt.executeBatch();
                }
            }

            conn.commit();
            System.out.println(DEBUG_PREFIX + " ✅ Imported " + rows.size() + " rows to " + tableName);

        } catch (SQLException e) {
            System.err.println(DEBUG_PREFIX + " Error importing sheet '" + sheet.getSheetName() + "': " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void importAllSheets(List<SheetDataWrapper> sheets) {
        System.out.println(DEBUG_PREFIX + " Importing " + sheets.size() + " sheets...");
        int totalRows = 0;
        for (SheetDataWrapper sheet : sheets) {
            importSheet(sheet);
            totalRows += sheet.getRowCount();
        }
        System.out.println(DEBUG_PREFIX + " ✅ Imported " + totalRows + " total rows across " + sheets.size() + " sheets");
    }

    private String sanitizeTableName(String name) {
        String sanitized = name.trim()
                .replaceAll("[^a-zA-Z0-9_]", "_")
                .replaceAll("_+", "_");
        if (sanitized.matches("^\\d.*")) {
            sanitized = "sheet_" + sanitized;
        }
        if (sanitized.length() > 60) {
            sanitized = sanitized.substring(0, 60);
        }
        return sanitized;
    }

    private String sanitizeColumnName(String name) {
        String sanitized = name.trim()
                .replaceAll("[^a-zA-Z0-9_]", "_")
                .replaceAll("_+", "_");
        if (sanitized.matches("^\\d.*")) {
            sanitized = "col_" + sanitized;
        }
        if (sanitized.length() > 60) {
            sanitized = sanitized.substring(0, 60);
        }
        return sanitized;
    }
}
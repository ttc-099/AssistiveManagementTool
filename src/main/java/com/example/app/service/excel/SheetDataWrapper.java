package com.example.app.service.excel;

import java.util.*;

/**
 * Wrapper class for Excel sheet data
 */
public class SheetDataWrapper {

    private String sheetName;
    private List<String> headers = new ArrayList<>();
    private List<Map<String, String>> rows = new ArrayList<>();
    private int rowCount = 0;

    public SheetDataWrapper() {
        // Default constructor
    }

    public SheetDataWrapper(String sheetName, List<String> headers, List<Map<String, String>> rows) {
        this.sheetName = sheetName;
        this.headers = headers != null ? headers : new ArrayList<>();
        this.rows = rows != null ? rows : new ArrayList<>();
        this.rowCount = this.rows.size();
    }

    // ===== GETTERS AND SETTERS =====

    public String getSheetName() {
        return sheetName;
    }

    public void setSheetName(String sheetName) {
        this.sheetName = sheetName;
    }

    public List<String> getHeaders() {
        return headers;
    }

    public void setHeaders(List<String> headers) {
        this.headers = headers != null ? headers : new ArrayList<>();
    }

    public List<Map<String, String>> getRows() {
        return rows;
    }

    public void setRows(List<Map<String, String>> rows) {
        this.rows = rows != null ? rows : new ArrayList<>();
        this.rowCount = this.rows.size();
    }

    public int getRowCount() {
        return rowCount;
    }

    public void setRowCount(int rowCount) {
        this.rowCount = rowCount;
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    public int getColumnCount() {
        return headers.size();
    }

    @Override
    public String toString() {
        return "SheetDataWrapper{" +
                "sheetName='" + sheetName + '\'' +
                ", headers=" + headers +
                ", rowCount=" + rowCount +
                '}';
    }
}
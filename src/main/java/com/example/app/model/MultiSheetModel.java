package com.example.app.model;

import java.util.*;

public class MultiSheetModel {
    private final Map<String, SheetData> sheets = new LinkedHashMap<>();
    private String activeSheet;

    public static class SheetData {
        private final List<String> headers;
        private final List<Map<String, String>> rows;

        public SheetData(List<String> headers, List<Map<String, String>> rows) {
            this.headers = headers != null ? headers : new ArrayList<>();
            this.rows = rows != null ? rows : new ArrayList<>();
        }

        public List<String> getHeaders() { return headers; }
        public List<Map<String, String>> getRows() { return rows; }
        public int getRowCount() { return rows.size(); }
        public int getColumnCount() { return headers.size(); }
    }

    public void addSheet(String name, List<String> headers, List<Map<String, String>> rows) {
        sheets.put(name, new SheetData(headers, rows));
        if (activeSheet == null) {
            activeSheet = name;
        }
    }

    public SheetData getSheet(String name) {
        return sheets.get(name);
    }

    public SheetData getActiveSheet() {
        return sheets.get(activeSheet);
    }

    public List<String> getSheetNames() {
        return new ArrayList<>(sheets.keySet());
    }

    public void setActiveSheet(String name) {
        if (sheets.containsKey(name)) {
            this.activeSheet = name;
        }
    }

    public boolean isEmpty() {
        return sheets.isEmpty();
    }

    public int getSheetCount() {
        return sheets.size();
    }

    public List<Map<String, String>> getAllRows() {
        List<Map<String, String>> allRows = new ArrayList<>();
        for (SheetData sheet : sheets.values()) {
            allRows.addAll(sheet.getRows());
        }
        return allRows;
    }
}
package com.example.app.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.IndexedColors;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class UAListUpdaterService {

    public static class UAUpdateResult {
        private final List<Map<String, String>> newUAStudents;
        private final List<Map<String, String>> existingUAStudents;
        private final List<Map<String, String>> allUAStudents;
        private final List<String> headers;
        private final List<String> newAdditions;
        private final int totalNew;
        private final int totalExisting;
        private final String intake;

        public UAUpdateResult(List<Map<String, String>> newUAStudents,
                              List<Map<String, String>> existingUAStudents,
                              List<Map<String, String>> allUAStudents,
                              List<String> headers,
                              List<String> newAdditions,
                              String intake) {
            this.newUAStudents = newUAStudents;
            this.existingUAStudents = existingUAStudents;
            this.allUAStudents = allUAStudents;
            this.headers = headers;
            this.newAdditions = newAdditions;
            this.totalNew = newUAStudents.size();
            this.totalExisting = existingUAStudents.size();
            this.intake = intake;
        }

        public List<Map<String, String>> getNewUAStudents() { return newUAStudents; }
        public List<Map<String, String>> getExistingUAStudents() { return existingUAStudents; }
        public List<Map<String, String>> getAllUAStudents() { return allUAStudents; }
        public List<String> getHeaders() { return headers; }
        public List<String> getNewAdditions() { return newAdditions; }
        public int getTotalNew() { return totalNew; }
        public int getTotalExisting() { return totalExisting; }
        public String getIntake() { return intake; }
    }

    public UAUpdateResult processFiles(File mainDatabase, File existingUAList, String intake) throws IOException {
        // Load main database
        List<Map<String, String>> mainData = loadExcelFile(mainDatabase);
        List<String> mainHeaders = getHeaders(mainDatabase);

        // Load existing UA list
        List<Map<String, String>> existingUAData = loadExcelFile(existingUAList);
        List<String> existingHeaders = getHeaders(existingUAList);

        // Find the Intake and Decision columns in main database
        String intakeColumn = findColumn(mainHeaders, "Intake");
        String decisionColumn = findColumn(mainHeaders, "Decision");
        String studentIdColumn = findColumn(mainHeaders, "Student ID");
        String surnameColumn = findColumn(mainHeaders, "Surname");
        String givenNameColumn = findColumn(mainHeaders, "Given Name");

        if (intakeColumn == null || decisionColumn == null) {
            throw new IOException("Required columns 'Intake' and/or 'Decision' not found in Main Database");
        }

        System.out.println("\n========== UA LIST UPDATER DEBUG ==========");
        System.out.println("Selected Intake: '" + intake + "'");
        System.out.println("Intake Column: '" + intakeColumn + "'");
        System.out.println("Decision Column: '" + decisionColumn + "'");
        System.out.println("Total rows in Main Database: " + mainData.size());

        // Show sample intake values from main database
        System.out.println("\nSample Intake values from Main Database:");
        mainData.stream().limit(10).forEach(row -> {
            String val = row.getOrDefault(intakeColumn, "EMPTY");
            System.out.println("  - '" + val + "'");
        });

        // Filter main data for the selected intake and UA status
        List<Map<String, String>> uaStudents = mainData.stream()
                .filter(row -> {
                    String rowIntake = row.getOrDefault(intakeColumn, "").trim();
                    String decision = row.getOrDefault(decisionColumn, "").trim();

                    // Convert the database intake to month/year format
                    String convertedIntake = convertIntakeToMonthYear(rowIntake);

                    boolean intakeMatches = convertedIntake.equalsIgnoreCase(intake);
                    boolean decisionMatches = decision.equalsIgnoreCase("UA") ||
                            decision.equalsIgnoreCase("CA") ||
                            decision.equalsIgnoreCase("UO");

                    if (intakeMatches && decisionMatches) {
                        System.out.println("✅ MATCH: DB Intake: '" + rowIntake + "' -> '" + convertedIntake +
                                "', Decision: '" + decision + "'");
                    }

                    return intakeMatches && decisionMatches;
                })
                .collect(Collectors.toList());

        System.out.println("\nFound " + uaStudents.size() + " students with Decision=UA/CA/UO for intake: " + intake);

        // Build a set of existing UA student identifiers (using Student ID)
        Set<String> existingIdentifiers = new HashSet<>();
        for (Map<String, String> existing : existingUAData) {
            String studentId = existing.getOrDefault("Student ID", "").trim();
            if (!studentId.isEmpty()) {
                existingIdentifiers.add(studentId);
            }
            // Also add Surname + Given Name as fallback
            String surname = existing.getOrDefault("Surname", "").trim();
            String givenName = existing.getOrDefault("Given Name", "").trim();
            if (!surname.isEmpty() && !givenName.isEmpty()) {
                existingIdentifiers.add((surname + "_" + givenName).toLowerCase());
            }
        }
        System.out.println("Existing UA List has " + existingIdentifiers.size() + " student identifiers");

        // Separate new and existing UA students
        List<Map<String, String>> newUAStudents = new ArrayList<>();
        List<Map<String, String>> existingUAStudents = new ArrayList<>();
        List<String> newAdditions = new ArrayList<>();

        for (Map<String, String> student : uaStudents) {
            String studentId = student.getOrDefault(studentIdColumn, "").trim();
            String surname = student.getOrDefault(surnameColumn, "").trim();
            String givenName = student.getOrDefault(givenNameColumn, "").trim();
            String identifier = (surname + "_" + givenName).toLowerCase();

            // Check if student exists in existing UA list
            boolean exists = false;
            if (!studentId.isEmpty() && existingIdentifiers.contains(studentId)) {
                exists = true;
            } else if (existingIdentifiers.contains(identifier)) {
                exists = true;
            }

            if (exists) {
                existingUAStudents.add(student);
            } else {
                newUAStudents.add(student);
                // Build name for new addition notification
                String name = surname + " " + givenName;
                String id = studentId.isEmpty() ? "No ID" : studentId;
                newAdditions.add(name.trim() + " (ID: " + id + ")");
            }
        }

        System.out.println("New UA students found: " + newUAStudents.size());
        System.out.println("Already existing UA students: " + existingUAStudents.size());
        System.out.println("==========================================\n");

        // Combine all UA students (existing + new) - keep existing order, append new at end
        List<Map<String, String>> allUAStudents = new ArrayList<>();
        allUAStudents.addAll(existingUAData);  // Start with existing UA List data
        allUAStudents.addAll(newUAStudents);   // Append new students

        return new UAUpdateResult(newUAStudents, existingUAStudents, allUAStudents,
                existingHeaders, newAdditions, intake);
    }

    /**
     * Convert database intake format to Month Year format
     * Supports multiple formats:
     * - "Apr-26" -> "April 2026"
     * - "2026-04-01 00:00:00" -> "April 2026"
     * - "2026-04-01" -> "April 2026"
     * - "April 2026" -> "April 2026" (no change)
     * - "Jul-26" -> "July 2026"
     */
    private String convertIntakeToMonthYear(String dbIntake) {
        if (dbIntake == null || dbIntake.isEmpty()) return "";

        // If it's already a full month name format, return as-is
        if (dbIntake.matches("(?i)(january|february|march|april|may|june|july|august|september|october|november|december)\\s+\\d{4}")) {
            return dbIntake;
        }

        // Handle "Apr-26", "Jul-26", "Sep-26" format
        if (dbIntake.matches("(?i)[a-z]{3}-\\d{2}")) {
            String[] parts = dbIntake.split("-");
            if (parts.length == 2) {
                String monthAbbr = parts[0].toLowerCase();
                String yearSuffix = parts[1];

                // Map 3-letter month abbreviations to full month names
                Map<String, String> monthMap = new HashMap<>();
                monthMap.put("jan", "January");
                monthMap.put("feb", "February");
                monthMap.put("mar", "March");
                monthMap.put("apr", "April");
                monthMap.put("may", "May");
                monthMap.put("jun", "June");
                monthMap.put("jul", "July");
                monthMap.put("aug", "August");
                monthMap.put("sep", "September");
                monthMap.put("oct", "October");
                monthMap.put("nov", "November");
                monthMap.put("dec", "December");

                String fullMonth = monthMap.get(monthAbbr);
                if (fullMonth != null) {
                    // Convert "26" to "2026"
                    int year = 2000 + Integer.parseInt(yearSuffix);
                    return fullMonth + " " + year;
                }
            }
            return dbIntake;
        }

        // Handle "2026-04-01 00:00:00" or "2026-04-01" format
        try {
            String datePart = dbIntake.split(" ")[0];

            if (datePart.contains("-")) {
                String[] parts = datePart.split("-");
                if (parts.length >= 3) {
                    int year = Integer.parseInt(parts[0]);
                    int month = Integer.parseInt(parts[1]);

                    String[] monthNames = {"January", "February", "March", "April", "May", "June",
                            "July", "August", "September", "October", "November", "December"};
                    String monthName = monthNames[month - 1];

                    return monthName + " " + year;
                }
            }

            if (datePart.contains("/")) {
                String[] parts = datePart.split("/");
                if (parts.length >= 3) {
                    int year = Integer.parseInt(parts[0]);
                    int month = Integer.parseInt(parts[1]);

                    String[] monthNames = {"January", "February", "March", "April", "May", "June",
                            "July", "August", "September", "October", "November", "December"};
                    String monthName = monthNames[month - 1];

                    return monthName + " " + year;
                }
            }
        } catch (Exception e) {
            System.err.println("Error parsing intake: " + dbIntake + " - " + e.getMessage());
        }

        return dbIntake;
    }

    private String findColumn(List<String> headers, String searchTerm) {
        for (String header : headers) {
            if (header.equalsIgnoreCase(searchTerm)) {
                return header;
            }
        }
        return null;
    }

    public List<String> getHeaders(File file) throws IOException {
        try (InputStream fis = new FileInputStream(file);
             Workbook workbook = createWorkbook(fis, file.getName())) {

            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);

            if (headerRow == null) {
                return Collections.emptyList();
            }

            List<String> headers = new ArrayList<>();
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                Cell cell = headerRow.getCell(i);
                if (cell != null) {
                    String value = getCellValueAsString(cell).trim();
                    if (!value.isEmpty()) {
                        headers.add(value);
                    }
                }
            }
            return headers;
        }
    }

    public List<Map<String, String>> loadExcelFile(File file) throws IOException {
        List<Map<String, String>> data = new ArrayList<>();

        try (InputStream fis = new FileInputStream(file);
             Workbook workbook = createWorkbook(fis, file.getName())) {

            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);

            if (headerRow == null) {
                return data;
            }

            // Get headers
            List<String> headers = new ArrayList<>();
            Map<Integer, String> headerMap = new LinkedHashMap<>();
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                Cell cell = headerRow.getCell(i);
                if (cell != null) {
                    String value = getCellValueAsString(cell).trim();
                    if (!value.isEmpty()) {
                        headers.add(value);
                        headerMap.put(i, value);
                    }
                }
            }

            // Read data rows
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;

                Map<String, String> rowData = new LinkedHashMap<>();
                for (int colIndex = 0; colIndex < headerRow.getLastCellNum(); colIndex++) {
                    String header = headerMap.getOrDefault(colIndex, "");
                    if (!header.isEmpty()) {
                        Cell cell = row.getCell(colIndex);
                        String value = (cell != null) ? getCellValueAsString(cell) : "";
                        rowData.put(header, value);
                    }
                }
                data.add(rowData);
            }
        }

        return data;
    }

    public void exportUAList(UAUpdateResult result, File outputFile) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("UA List - " + result.getIntake());

            // Create header style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            List<String> headers = result.getHeaders();

            // Write headers
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
            }

            // Write data - all existing + new
            int rowIndex = 1;
            for (Map<String, String> rowData : result.getAllUAStudents()) {
                Row row = sheet.createRow(rowIndex++);
                for (int i = 0; i < headers.size(); i++) {
                    String header = headers.get(i);
                    String value = rowData.getOrDefault(header, "");
                    Cell cell = row.createCell(i);
                    cell.setCellValue(value);
                }
            }

            // Auto-size columns
            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
                if (sheet.getColumnWidth(i) > 50 * 256) {
                    sheet.setColumnWidth(i, 50 * 256);
                }
            }

            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                workbook.write(fos);
            }
        }
    }

    private Workbook createWorkbook(InputStream inputStream, String fileName) throws IOException {
        if (fileName.endsWith(".xlsx")) {
            return new XSSFWorkbook(inputStream);
        } else if (fileName.endsWith(".xls")) {
            return new HSSFWorkbook(inputStream);
        }
        throw new IOException("Unsupported file format: " + fileName);
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        DataFormatter formatter = new DataFormatter();
        return formatter.formatCellValue(cell);
    }
}
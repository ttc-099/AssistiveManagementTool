package com.example.app.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.IndexedColors;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class RegionMassAdderService {

    // Country to Region mapping
    private final Map<String, String> countryToRegionMap = new HashMap<>();

    public RegionMassAdderService() {
        loadCountryMappings();
    }

    private void loadCountryMappings() {
        // Load from properties file
        try (InputStream input = getClass().getResourceAsStream("/countries.properties")) {
            if (input != null) {
                Properties props = new Properties();
                props.load(input);
                for (String key : props.stringPropertyNames()) {
                    countryToRegionMap.put(key.toLowerCase().trim(), props.getProperty(key));
                }
                System.out.println("✅ Loaded " + countryToRegionMap.size() + " country mappings from properties file.");
            } else {
                System.err.println("⚠️ countries.properties not found. Using default mappings.");
                loadDefaultMappings();
            }
        } catch (IOException e) {
            System.err.println("❌ Error loading country mappings: " + e.getMessage());
            loadDefaultMappings();
        }
    }

    private void loadDefaultMappings() {
        // Default mappings for common countries
        Map<String, String> defaultMap = new HashMap<>();
        defaultMap.put("malaysia", "SEA");
        defaultMap.put("singapore", "SEA");
        defaultMap.put("indonesia", "SEA");
        defaultMap.put("thailand", "SEA");
        defaultMap.put("vietnam", "SEA");
        defaultMap.put("philippines", "SEA");
        defaultMap.put("myanmar", "SEA");
        defaultMap.put("cambodia", "SEA");
        defaultMap.put("laos", "SEA");
        defaultMap.put("brunei", "SEA");
        defaultMap.put("timor-leste", "SEA");
        defaultMap.put("india", "SA");
        defaultMap.put("pakistan", "SA");
        defaultMap.put("bangladesh", "SA");
        defaultMap.put("sri lanka", "SA");
        defaultMap.put("nepal", "SA");
        defaultMap.put("bhutan", "SA");
        defaultMap.put("maldives", "SA");
        defaultMap.put("afghanistan", "SA");
        defaultMap.put("china", "East Asia");
        defaultMap.put("japan", "East Asia");
        defaultMap.put("south korea", "East Asia");
        defaultMap.put("united states", "Other");
        defaultMap.put("canada", "Other");
        defaultMap.put("united kingdom", "Other");

        countryToRegionMap.putAll(defaultMap);
        System.out.println("📦 Loaded " + countryToRegionMap.size() + " default country mappings.");
    }

    // Method to reload mappings from a file
    public void reloadMappings(File mappingFile) throws IOException {
        if (mappingFile.getName().endsWith(".properties")) {
            loadFromProperties(mappingFile);
        } else if (mappingFile.getName().endsWith(".csv")) {
            loadFromCsv(mappingFile);
        } else {
            throw new IOException("Unsupported file format. Use .properties or .csv");
        }
    }

    private void loadFromProperties(File file) throws IOException {
        try (InputStream input = new FileInputStream(file)) {
            Properties props = new Properties();
            props.load(input);
            countryToRegionMap.clear();
            for (String key : props.stringPropertyNames()) {
                countryToRegionMap.put(key.toLowerCase().trim(), props.getProperty(key));
            }
            System.out.println("✅ Loaded " + countryToRegionMap.size() + " country mappings from properties file.");
        }
    }

    private void loadFromCsv(File file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            boolean isHeader = true;
            countryToRegionMap.clear();

            while ((line = reader.readLine()) != null) {
                if (isHeader) {
                    isHeader = false;
                    continue;
                }
                String[] parts = line.split(",");
                if (parts.length >= 2) {
                    String country = parts[0].trim().toLowerCase();
                    String region = parts[1].trim();
                    if (!country.isEmpty() && !region.isEmpty()) {
                        countryToRegionMap.put(country, region);
                    }
                }
            }
            System.out.println("✅ Loaded " + countryToRegionMap.size() + " country mappings from CSV file.");
        }
    }

    // Method to get region for a country
    public String getRegionForCountry(String country) {
        if (country == null || country.trim().isEmpty()) {
            return "";
        }
        // Clean the country string - remove extra spaces and convert to lowercase
        String cleanCountry = country.trim().toLowerCase();

        // Try exact match first
        String region = countryToRegionMap.get(cleanCountry);
        if (region != null) {
            return region;
        }

        // Try partial match for countries with multiple words
        for (Map.Entry<String, String> entry : countryToRegionMap.entrySet()) {
            if (cleanCountry.contains(entry.getKey()) || entry.getKey().contains(cleanCountry)) {
                return entry.getValue();
            }
        }

        return "Other"; // Default if not found
    }

    public static class FileProcessResult {
        private final String fileName;
        private final String filePath;
        private final String countryColumn;
        private List<Map<String, String>> previewData;
        private List<Map<String, String>> fullData;
        private List<String> headers;
        private final boolean success;
        private final String errorMessage;
        private final int rowCount;

        private FileProcessResult(Builder builder) {
            this.fileName = builder.fileName;
            this.filePath = builder.filePath;
            this.countryColumn = builder.countryColumn;
            this.previewData = builder.previewData;
            this.fullData = builder.fullData;
            this.headers = builder.headers;
            this.success = builder.success;
            this.errorMessage = builder.errorMessage;
            this.rowCount = builder.rowCount;
        }

        public static class Builder {
            private String fileName;
            private String filePath;
            private String countryColumn;
            private List<Map<String, String>> previewData = new ArrayList<>();
            private List<Map<String, String>> fullData = new ArrayList<>();
            private List<String> headers = new ArrayList<>();
            private boolean success = true;
            private String errorMessage = "";
            private int rowCount = 0;

            public Builder fileName(String fileName) { this.fileName = fileName; return this; }
            public Builder filePath(String filePath) { this.filePath = filePath; return this; }
            public Builder countryColumn(String countryColumn) { this.countryColumn = countryColumn; return this; }
            public Builder previewData(List<Map<String, String>> previewData) { this.previewData = previewData; return this; }
            public Builder fullData(List<Map<String, String>> fullData) { this.fullData = fullData; return this; }
            public Builder headers(List<String> headers) { this.headers = headers; return this; }
            public Builder success(boolean success) { this.success = success; return this; }
            public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }
            public Builder rowCount(int rowCount) { this.rowCount = rowCount; return this; }

            public FileProcessResult build() {
                return new FileProcessResult(this);
            }
        }

        public String getFileName() { return fileName; }
        public String getFilePath() { return filePath; }
        public String getCountryColumn() { return countryColumn; }
        public List<Map<String, String>> getPreviewData() { return previewData; }
        public List<Map<String, String>> getFullData() { return fullData; }
        public List<String> getHeaders() { return headers; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public int getRowCount() { return rowCount; }
    }

    public List<String> getColumnHeaders(File file) throws IOException {
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
                    String value = getCellValueAsString(cell);
                    if (!value.trim().isEmpty()) {
                        headers.add(value.trim());
                    }
                }
            }
            return headers;
        }
    }

    public FileProcessResult processSingleFile(File file, String countryColumn) {
        FileProcessResult.Builder builder = new FileProcessResult.Builder()
                .fileName(file.getName())
                .filePath(file.getAbsolutePath())
                .countryColumn(countryColumn);

        try (InputStream fis = new FileInputStream(file);
             Workbook workbook = createWorkbook(fis, file.getName())) {

            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);

            if (headerRow == null) {
                return builder.success(false).errorMessage("File is empty").build();
            }

            // Get all headers
            List<String> headers = new ArrayList<>();
            Map<Integer, String> headerMap = new LinkedHashMap<>();
            int countryColumnIndex = -1;

            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                Cell cell = headerRow.getCell(i);
                if (cell != null) {
                    String value = getCellValueAsString(cell).trim();
                    if (!value.isEmpty()) {
                        headers.add(value);
                        headerMap.put(i, value);
                        if (value.equalsIgnoreCase(countryColumn)) {
                            countryColumnIndex = i;
                        }
                    }
                }
            }

            if (countryColumnIndex == -1) {
                return builder.success(false)
                        .errorMessage("Country column '" + countryColumn + "' not found")
                        .headers(headers)
                        .build();
            }

            // Read ALL data rows (store all in fullData, previewData will reference fullData)
            List<Map<String, String>> fullData = new ArrayList<>();
            int rowCount = 0;

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

                fullData.add(rowData);
                rowCount++;
            }

            // Store ALL data in previewData as well - displayPreview will limit if needed
            builder.fullData(fullData)
                    .previewData(new ArrayList<>(fullData))  // Store all data
                    .headers(headers)
                    .rowCount(rowCount)
                    .success(true);

        } catch (Exception e) {
            return builder.success(false)
                    .errorMessage("Error processing file: " + e.getMessage())
                    .build();
        }

        return builder.build();
    }

    // Add Region column with auto-population from country mapping
    public void addRegionColumn(FileProcessResult result) {
        if (!result.isSuccess() || result.getFullData().isEmpty()) {
            return;
        }

        List<Map<String, String>> updatedFullData = new ArrayList<>();
        String countryColumn = result.getCountryColumn();

        for (Map<String, String> row : result.getFullData()) {
            Map<String, String> updatedRow = new LinkedHashMap<>();

            String countryValue = row.getOrDefault(countryColumn, "");
            String regionValue = getRegionForCountry(countryValue);

            for (Map.Entry<String, String> entry : row.entrySet()) {
                updatedRow.put(entry.getKey(), entry.getValue());
                if (entry.getKey().equals(countryColumn)) {
                    updatedRow.put("Region", regionValue);
                }
            }

            if (!updatedRow.containsKey("Region")) {
                updatedRow.put("Region", regionValue);
            }

            updatedFullData.add(updatedRow);
        }

        // Store ALL data in both fullData and previewData
        result.previewData.clear();
        result.previewData.addAll(updatedFullData);  // All data
        result.fullData.clear();
        result.fullData.addAll(updatedFullData);

        // Update headers - insert Region after Country
        List<String> newHeaders = new ArrayList<>(result.headers);
        int countryIndex = newHeaders.indexOf(countryColumn);
        if (countryIndex != -1) {
            newHeaders.add(countryIndex + 1, "Region");
        } else {
            newHeaders.add("Region");
        }
        result.headers.clear();
        result.headers.addAll(newHeaders);
    }

    // Populate Region column based on existing Country column
    public void populateRegionColumn(FileProcessResult result) {
        if (!result.isSuccess() || result.getFullData().isEmpty()) {
            return;
        }

        String countryColumn = result.getCountryColumn();

        boolean hasRegionColumn = false;
        if (!result.headers.isEmpty()) {
            hasRegionColumn = result.headers.stream().anyMatch(h -> h.equals("Region"));
        }

        if (!hasRegionColumn) {
            addRegionColumn(result);
            return;
        }

        List<Map<String, String>> updatedFullData = new ArrayList<>();

        for (Map<String, String> row : result.getFullData()) {
            Map<String, String> updatedRow = new LinkedHashMap<>();

            String countryValue = row.getOrDefault(countryColumn, "");
            String regionValue = getRegionForCountry(countryValue);

            for (Map.Entry<String, String> entry : row.entrySet()) {
                if (entry.getKey().equals("Region")) {
                    updatedRow.put(entry.getKey(), regionValue);
                } else {
                    updatedRow.put(entry.getKey(), entry.getValue());
                }
            }

            updatedFullData.add(updatedRow);
        }

        // Store ALL data in both fullData and previewData
        result.previewData.clear();
        result.previewData.addAll(updatedFullData);  // All data
        result.fullData.clear();
        result.fullData.addAll(updatedFullData);
    }

    public int exportFiles(List<FileProcessResult> results, File outputDirectory) throws IOException {
        int exported = 0;
        List<String> errors = new ArrayList<>();

        for (FileProcessResult result : results) {
            if (!result.isSuccess()) {
                errors.add("Skipped failed file: " + result.getFileName());
                continue;
            }

            try {
                String outputFileName = result.getFileName();
                int extensionIndex = outputFileName.lastIndexOf(".");
                if (extensionIndex > 0) {
                    outputFileName = outputFileName.substring(0, extensionIndex) + "_modified" + outputFileName.substring(extensionIndex);
                } else {
                    outputFileName = outputFileName + "_modified";
                }

                File outputFile = new File(outputDirectory, outputFileName);

                if (outputFile.exists()) {
                    int counter = 1;
                    String baseName = outputFileName.substring(0, outputFileName.lastIndexOf("."));
                    String extension = outputFileName.substring(outputFileName.lastIndexOf("."));
                    while (outputFile.exists()) {
                        outputFileName = baseName + "_" + counter + extension;
                        outputFile = new File(outputDirectory, outputFileName);
                        counter++;
                    }
                }

                createExcelFile(result, outputFile);
                exported++;
                System.out.println("✅ Exported: " + outputFile.getName());

            } catch (Exception e) {
                String errorMsg = "Error exporting file " + result.getFileName() + ": " + e.getMessage();
                errors.add(errorMsg);
                System.err.println("❌ " + errorMsg);
                e.printStackTrace();
            }
        }

        if (!errors.isEmpty()) {
            System.err.println("⚠️ Export completed with " + errors.size() + " errors:");
            for (String error : errors) {
                System.err.println("   - " + error);
            }
        }

        return exported;
    }

    private void createExcelFile(FileProcessResult result, File outputFile) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Data");

            Row headerRow = sheet.createRow(0);
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            List<String> headers = result.getHeaders();

            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (Map<String, String> rowData : result.getFullData()) {
                Row row = sheet.createRow(rowIndex++);
                for (int i = 0; i < headers.size(); i++) {
                    String header = headers.get(i);
                    String value = rowData.getOrDefault(header, "");
                    Cell cell = row.createCell(i);
                    cell.setCellValue(value);
                }
            }

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
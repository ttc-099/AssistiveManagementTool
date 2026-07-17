package com.example.app.service.addTools.regions;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class RegionMassAdderService {

    private final Map<String, String> countryToRegionMap = new HashMap<>();
    private static final int PREVIEW_LIMIT = 100;
    private static final String[] TARGET_SHEETS = {"FY data", "UGPG data"};
    private static final boolean DEBUG = true;

    public RegionMassAdderService() {
        System.out.println("🔍 [DEBUG] RegionMassAdderService initialized");
        loadCountryMappings();
    }

    private void logDebug(String message) {
        if (DEBUG) {
            System.out.println("🔍 [DEBUG] " + message);
        }
    }

    private void logError(String message, Throwable t) {
        System.err.println("❌ [ERROR] " + message);
        if (DEBUG && t != null) {
            t.printStackTrace();
        }
    }

    private void logInfo(String message) {
        System.out.println("ℹ️ [INFO] " + message);
    }

    private void logSuccess(String message) {
        System.out.println("✅ [SUCCESS] " + message);
    }

    private void logWarning(String message) {
        System.out.println("⚠️ [WARNING] " + message);
    }

    private void loadCountryMappings() {
        logDebug("Loading country mappings...");
        try (InputStream input = getClass().getResourceAsStream("/countries.properties")) {
            if (input != null) {
                logDebug("Found countries.properties in classpath");
                Properties props = new Properties();
                props.load(input);
                for (String key : props.stringPropertyNames()) {
                    countryToRegionMap.put(key.toLowerCase().trim(), props.getProperty(key));
                }
                logSuccess("Loaded " + countryToRegionMap.size() + " country mappings from properties file.");

                // Log sample mappings for debugging
                if (DEBUG) {
                    logDebug("Sample country mappings (first 15):");
                    int count = 0;
                    for (Map.Entry<String, String> entry : countryToRegionMap.entrySet()) {
                        if (count++ < 15) {
                            logDebug("  " + entry.getKey() + " -> " + entry.getValue());
                        }
                    }
                    if (countryToRegionMap.size() > 15) {
                        logDebug("  ... and " + (countryToRegionMap.size() - 15) + " more");
                    }

                    // Log specific important mappings
                    logDebug("Important mapping checks:");
                    String[] testCountries = {"malaysia", "sri lanka", "saudi arabia", "united arab emirates",
                            "south korea", "united states", "united kingdom", "taiwan"};
                    for (String test : testCountries) {
                        String result = countryToRegionMap.get(test);
                        logDebug("  " + test + " -> " + (result != null ? result : "NOT FOUND"));
                    }
                }
            } else {
                logWarning("countries.properties not found. Using default mappings.");
                loadDefaultMappings();
            }
        } catch (IOException e) {
            logError("Error loading country mappings: " + e.getMessage(), e);
            loadDefaultMappings();
        }
    }

    private void loadDefaultMappings() {
        logDebug("Loading default mappings...");
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
        logInfo("Loaded " + countryToRegionMap.size() + " default country mappings.");
    }

    public void reloadMappings(File mappingFile) throws IOException {
        logDebug("Reloading mappings from: " + mappingFile.getAbsolutePath());
        if (mappingFile.getName().endsWith(".properties")) {
            loadFromProperties(mappingFile);
        } else if (mappingFile.getName().endsWith(".csv")) {
            loadFromCsv(mappingFile);
        } else {
            throw new IOException("Unsupported file format. Use .properties or .csv");
        }
    }

    private void loadFromProperties(File file) throws IOException {
        logDebug("Loading from properties file: " + file.getAbsolutePath());
        try (InputStream input = new FileInputStream(file)) {
            Properties props = new Properties();
            props.load(input);
            countryToRegionMap.clear();
            for (String key : props.stringPropertyNames()) {
                countryToRegionMap.put(key.toLowerCase().trim(), props.getProperty(key));
            }
            logSuccess("Loaded " + countryToRegionMap.size() + " country mappings from properties file.");
        } catch (IOException e) {
            logError("Failed to load properties file: " + e.getMessage(), e);
            throw e;
        }
    }

    private void loadFromCsv(File file) throws IOException {
        logDebug("Loading from CSV file: " + file.getAbsolutePath());
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            boolean isHeader = true;
            countryToRegionMap.clear();
            int lineCount = 0;

            while ((line = reader.readLine()) != null) {
                lineCount++;
                if (isHeader) {
                    logDebug("CSV Header: " + line);
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
            logSuccess("Loaded " + countryToRegionMap.size() + " country mappings from CSV file (" + lineCount + " lines processed).");
        } catch (IOException e) {
            logError("Failed to load CSV file: " + e.getMessage(), e);
            throw e;
        }
    }

    public String getRegionForCountry(String country) {
        if (country == null || country.trim().isEmpty()) {
            logDebug("getRegionForCountry: Empty country value");
            return "";
        }

        // Clean the country string: trim and convert to lowercase
        String cleanCountry = country.trim().toLowerCase();
        logDebug("getRegionForCountry: Looking up '" + cleanCountry + "'");

        // ========== STRATEGY 1: Direct match ==========
        String region = countryToRegionMap.get(cleanCountry);
        if (region != null) {
            logDebug("getRegionForCountry: Direct match found: " + cleanCountry + " -> " + region);
            return region;
        }

        // ========== STRATEGY 2: Handle escaped spaces (properties file format) ==========
        // Keys in properties file with spaces are stored with backslash: "sri\ lanka"
        // When loaded, they become "sri lanka" in the map
        // But sometimes the value might still have the backslash
        String escapedCountry = cleanCountry.replace(" ", "\\ ");
        region = countryToRegionMap.get(escapedCountry);
        if (region != null) {
            logDebug("getRegionForCountry: Escaped match found: " + escapedCountry + " -> " + region);
            return region;
        }

        // ========== STRATEGY 3: Handle hyphen to space conversion ==========
        // e.g., "timor-leste" -> "timor leste"
        String hyphenFixed = cleanCountry.replace("-", " ");
        if (!hyphenFixed.equals(cleanCountry)) {
            region = countryToRegionMap.get(hyphenFixed);
            if (region != null) {
                logDebug("getRegionForCountry: Hyphen fixed match: " + hyphenFixed + " -> " + region);
                return region;
            }
            // Try escaped version of hyphen fixed
            region = countryToRegionMap.get(hyphenFixed.replace(" ", "\\ "));
            if (region != null) {
                logDebug("getRegionForCountry: Escaped hyphen fixed match found -> " + region);
                return region;
            }
        }

        // ========== STRATEGY 4: Handle space to hyphen conversion ==========
        // e.g., "sri lanka" -> "sri-lanka"
        String spaceFixed = cleanCountry.replace(" ", "-");
        if (!spaceFixed.equals(cleanCountry)) {
            region = countryToRegionMap.get(spaceFixed);
            if (region != null) {
                logDebug("getRegionForCountry: Space to hyphen match: " + spaceFixed + " -> " + region);
                return region;
            }
        }

        // ========== STRATEGY 5: Remove all spaces ==========
        // e.g., "sri lanka" -> "srilanka"
        String noSpaces = cleanCountry.replace(" ", "");
        if (!noSpaces.equals(cleanCountry)) {
            region = countryToRegionMap.get(noSpaces);
            if (region != null) {
                logDebug("getRegionForCountry: No spaces match: " + noSpaces + " -> " + region);
                return region;
            }
        }

        // ========== STRATEGY 6: Handle special cases for common variations ==========
        // UAE variations
        if (cleanCountry.equals("uae") || cleanCountry.equals("united arab emirates")) {
            region = countryToRegionMap.get("united arab emirates");
            if (region != null) {
                logDebug("getRegionForCountry: UAE special match -> " + region);
                return region;
            }
        }

        // USA variations
        if (cleanCountry.equals("usa") || cleanCountry.equals("us") || cleanCountry.equals("united states")) {
            region = countryToRegionMap.get("united states");
            if (region != null) {
                logDebug("getRegionForCountry: USA special match -> " + region);
                return region;
            }
        }

        // UK variations
        if (cleanCountry.equals("uk") || cleanCountry.equals("united kingdom")) {
            region = countryToRegionMap.get("united kingdom");
            if (region != null) {
                logDebug("getRegionForCountry: UK special match -> " + region);
                return region;
            }
        }

        // South Korea / North Korea variations
        if (cleanCountry.equals("south korea") || cleanCountry.equals("korea south") || cleanCountry.equals("korea")) {
            region = countryToRegionMap.get("south korea");
            if (region != null) {
                logDebug("getRegionForCountry: South Korea special match -> " + region);
                return region;
            }
        }

        if (cleanCountry.equals("north korea") || cleanCountry.equals("korea north")) {
            region = countryToRegionMap.get("north korea");
            if (region != null) {
                logDebug("getRegionForCountry: North Korea special match -> " + region);
                return region;
            }
        }

        // ========== STRATEGY 7: Partial matching (country contains known name) ==========
        for (Map.Entry<String, String> entry : countryToRegionMap.entrySet()) {
            String key = entry.getKey();
            // Remove backslash escapes for comparison
            String cleanKey = key.replace("\\ ", " ");

            if (cleanCountry.contains(cleanKey) || cleanKey.contains(cleanCountry)) {
                logDebug("getRegionForCountry: Partial match found: " + cleanCountry + " -> " + entry.getValue() + " (matched: " + cleanKey + ")");
                return entry.getValue();
            }
        }

        // ========== STRATEGY 8: Word-by-word matching (for multi-word country names) ==========
        String[] words = cleanCountry.split("[\\s-]+");
        if (words.length > 1) {
            for (String word : words) {
                if (word.length() > 2) { // Only match meaningful words
                    for (Map.Entry<String, String> entry : countryToRegionMap.entrySet()) {
                        String cleanKey = entry.getKey().replace("\\ ", " ");
                        if (cleanKey.contains(word) || word.contains(cleanKey)) {
                            logDebug("getRegionForCountry: Word match found: '" + word + "' in '" + cleanCountry + "' -> " + entry.getValue() + " (matched: " + cleanKey + ")");
                            return entry.getValue();
                        }
                    }
                }
            }
        }

        logDebug("getRegionForCountry: No match found for '" + cleanCountry + "', defaulting to 'Other'");
        return "Other";
    }

    // Helper inner class representing a single sheet's state inside the process result
    public static class SheetData {
        private final String sheetName;
        private final List<String> headers = new ArrayList<>();
        private final List<Map<String, String>> fullData = new ArrayList<>();
        private final List<Map<String, String>> previewData = new ArrayList<>();

        public SheetData(String sheetName) {
            this.sheetName = sheetName;
        }

        public String getSheetName() { return sheetName; }
        public List<String> getHeaders() { return headers; }
        public List<Map<String, String>> getFullData() { return fullData; }
        public List<Map<String, String>> getPreviewData() { return previewData; }
    }

    public static class FileProcessResult {
        private final String fileName;
        private final String filePath;
        private final String countryColumn;
        private final Map<String, SheetData> sheetsData;
        private final boolean success;
        private final String errorMessage;
        private final int totalRowCount;

        private FileProcessResult(Builder builder) {
            this.fileName = builder.fileName;
            this.filePath = builder.filePath;
            this.countryColumn = builder.countryColumn;
            this.sheetsData = builder.sheetsData;
            this.success = builder.success;
            this.errorMessage = builder.errorMessage;
            this.totalRowCount = builder.totalRowCount;
        }

        public static class Builder {
            private String fileName;
            private String filePath;
            private String countryColumn;
            private Map<String, SheetData> sheetsData = new LinkedHashMap<>();
            private boolean success = true;
            private String errorMessage = "";
            private int totalRowCount = 0;

            public Builder fileName(String fileName) { this.fileName = fileName; return this; }
            public Builder filePath(String filePath) { this.filePath = filePath; return this; }
            public Builder countryColumn(String countryColumn) { this.countryColumn = countryColumn; return this; }
            public Builder sheetsData(Map<String, SheetData> sheetsData) { this.sheetsData = sheetsData; return this; }
            public Builder success(boolean success) { this.success = success; return this; }
            public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }
            public Builder totalRowCount(int totalRowCount) { this.totalRowCount = totalRowCount; return this; }

            public FileProcessResult build() {
                return new FileProcessResult(this);
            }
        }

        public String getFileName() { return fileName; }
        public String getFilePath() { return filePath; }
        public String getCountryColumn() { return countryColumn; }
        public Map<String, SheetData> getSheetsData() { return sheetsData; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public int getRowCount() { return totalRowCount; }

        public List<String> getHeaders() {
            if (sheetsData.containsKey("FY data")) return sheetsData.get("FY data").getHeaders();
            return sheetsData.values().isEmpty() ? Collections.emptyList() : sheetsData.values().iterator().next().getHeaders();
        }

        public List<Map<String, String>> getPreviewData() {
            if (sheetsData.containsKey("FY data")) return sheetsData.get("FY data").getPreviewData();
            return sheetsData.values().isEmpty() ? Collections.emptyList() : sheetsData.values().iterator().next().getPreviewData();
        }

        public List<Map<String, String>> getFullData() {
            if (sheetsData.containsKey("FY data")) return sheetsData.get("FY data").getFullData();
            return sheetsData.values().isEmpty() ? Collections.emptyList() : sheetsData.values().iterator().next().getFullData();
        }
    }

    public List<String> getColumnHeaders(File file) throws IOException, InvalidFormatException {
        System.out.println("[DEBUG] getColumnHeaders: Starting for file: " + file.getAbsolutePath());
        System.out.println("[DEBUG] getColumnHeaders: File exists: " + file.exists());
        System.out.println("[DEBUG] getColumnHeaders: File size: " + file.length() + " bytes");
        System.out.println("[DEBUG] getColumnHeaders: File can read: " + file.canRead());
        System.out.println("[DEBUG] getColumnHeaders: Attempting to create workbook...");

        Workbook workbook = null;
        try {
            workbook = createWorkbook(file);
            System.out.println("[DEBUG] getColumnHeaders: Workbook created successfully: " + workbook.getClass().getSimpleName());

            System.out.println("[DEBUG] getColumnHeaders: Total sheets in workbook: " + workbook.getNumberOfSheets());
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                System.out.println("[DEBUG] getColumnHeaders: Sheet " + i + ": '" + workbook.getSheetName(i) + "'");
            }

            // Find the target sheet - look for "FY data" or "UGPG data"
            Sheet sheet = null;
            String targetSheetName = null;

            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                String sheetName = workbook.getSheetName(i);
                // Trim and compare case-insensitive
                if (sheetName.trim().equalsIgnoreCase("FY data")) {
                    sheet = workbook.getSheetAt(i);
                    targetSheetName = sheetName;
                    System.out.println("[DEBUG] getColumnHeaders: Found sheet via trim/ignore case: '" + sheetName + "' matches 'FY data'");
                    break;
                } else if (sheetName.trim().equalsIgnoreCase("UGPG data")) {
                    sheet = workbook.getSheetAt(i);
                    targetSheetName = sheetName;
                    System.out.println("[DEBUG] getColumnHeaders: Found sheet via trim/ignore case: '" + sheetName + "' matches 'UGPG data'");
                    break;
                }
            }

            // If no specific sheet found, use the first sheet
            if (sheet == null && workbook.getNumberOfSheets() > 0) {
                sheet = workbook.getSheetAt(0);
                targetSheetName = workbook.getSheetName(0);
                System.out.println("[DEBUG] getColumnHeaders: No 'FY data' or 'UGPG data' sheet found, using first sheet: '" + targetSheetName + "'");
            }

            if (sheet == null) {
                System.out.println("[DEBUG] getColumnHeaders: No sheets found in workbook");
                return Collections.emptyList();
            }

            System.out.println("[DEBUG] getColumnHeaders: Found target sheet: '" + targetSheetName + "'");
            System.out.println("[DEBUG] getColumnHeaders: Using sheet: '" + targetSheetName + "'");

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                System.out.println("[DEBUG] getColumnHeaders: Header row is null");
                return Collections.emptyList();
            }

            System.out.println("[DEBUG] getColumnHeaders: Header row has " + headerRow.getLastCellNum() + " cells");

            List<String> headers = new ArrayList<>();
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                Cell cell = headerRow.getCell(i);
                if (cell != null) {
                    String value = getCellValueAsString(cell);
                    System.out.println("[DEBUG] getColumnHeaders: Cell " + i + " value: '" + value + "' (cell type: " + cell.getCellType() + ")");
                    if (!value.trim().isEmpty()) {
                        headers.add(value.trim());
                    }
                }
            }

            System.out.println("[DEBUG] getColumnHeaders: Found " + headers.size() + " headers: " + headers);

            // Check if Country header was found
            boolean countryFound = headers.stream().anyMatch(h -> h.equalsIgnoreCase("Country"));
            System.out.println("[DEBUG] getColumnHeaders: Country header found: " + countryFound);

            return headers;

        } catch (Exception e) {
            System.err.println("[ERROR] getColumnHeaders: Error processing headers: " + e.getMessage());
            e.printStackTrace();
            throw new IOException("Error reading file headers: " + e.getMessage(), e);
        } finally {
            // Always close the workbook in finally block
            if (workbook != null) {
                try {
                    workbook.close();
                    System.out.println("[DEBUG] getColumnHeaders: Workbook closed successfully");
                } catch (IOException e) {
                    System.err.println("[DEBUG] getColumnHeaders: Error closing workbook: " + e.getMessage());
                    // Don't rethrow - we already have the headers
                }
            }
        }
    }

    public FileProcessResult processSingleFile(File file, String countryColumn) {
        logDebug("========================================");
        logDebug("processSingleFile: Starting processing for: " + file.getName());
        logDebug("processSingleFile: File path: " + file.getAbsolutePath());
        logDebug("processSingleFile: File exists: " + file.exists());
        logDebug("processSingleFile: File size: " + file.length() + " bytes");
        logDebug("processSingleFile: Country column: '" + countryColumn + "'");
        logDebug("========================================");

        // If countryColumn is null or empty, try to auto-detect it
        if (countryColumn == null || countryColumn.trim().isEmpty()) {
            logDebug("processSingleFile: No country column specified, attempting to auto-detect...");
            try {
                List<String> headers = getColumnHeaders(file);
                for (String header : headers) {
                    if (header.equalsIgnoreCase("Country")) {
                        countryColumn = header;
                        logDebug("processSingleFile: Auto-detected country column: '" + countryColumn + "'");
                        break;
                    }
                }
                if (countryColumn == null || countryColumn.trim().isEmpty()) {
                    // Fallback to first column if no "Country" found
                    countryColumn = headers.isEmpty() ? "Country" : headers.get(0);
                    logDebug("processSingleFile: No 'Country' column found, using first column: '" + countryColumn + "'");
                }
            } catch (Exception e) {
                logError("processSingleFile: Failed to auto-detect country column: " + e.getMessage(), e);
                countryColumn = "Country"; // Default fallback
            }
        }

        FileProcessResult.Builder builder = new FileProcessResult.Builder()
                .fileName(file.getName())
                .filePath(file.getAbsolutePath())
                .countryColumn(countryColumn);

        Workbook workbook = null;
        try {
            logDebug("processSingleFile: Attempting to create workbook...");
            workbook = createWorkbook(file);
            logDebug("processSingleFile: Workbook created successfully: " + workbook.getClass().getSimpleName());
            logDebug("processSingleFile: Total sheets in workbook: " + workbook.getNumberOfSheets());

            // List all sheet names for debugging
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                logDebug("processSingleFile: Sheet " + i + ": '" + workbook.getSheetName(i) + "'");
            }
        } catch (Exception e) {
            logError("processSingleFile: Failed to create workbook: " + e.getMessage(), e);
            return builder.success(false)
                    .errorMessage("Failed to open file: " + e.getMessage())
                    .build();
        }

        try (Workbook safeWorkbook = workbook) {
            Map<String, SheetData> sheetsDataMap = new LinkedHashMap<>();
            int totalRows = 0;
            int sheetsProcessed = 0;

            for (String targetSheetName : TARGET_SHEETS) {
                logDebug("processSingleFile: Looking for sheet: '" + targetSheetName + "'");

                // Try exact match first
                Sheet sheet = safeWorkbook.getSheet(targetSheetName);

                // If not found, try trimming and case-insensitive match
                if (sheet == null) {
                    for (int i = 0; i < safeWorkbook.getNumberOfSheets(); i++) {
                        String sheetName = safeWorkbook.getSheetName(i);
                        if (sheetName.trim().equalsIgnoreCase(targetSheetName.trim())) {
                            sheet = safeWorkbook.getSheetAt(i);
                            logDebug("processSingleFile: Found sheet via trim/ignore case: '" + sheetName + "' matches '" + targetSheetName + "'");
                            break;
                        }
                    }
                }

                if (sheet == null) {
                    logDebug("processSingleFile: Sheet '" + targetSheetName + "' not found");
                    continue;
                }

                sheetsProcessed++;
                logDebug("processSingleFile: Found sheet '" + targetSheetName + "'");
                logDebug("processSingleFile: Sheet physical rows: " + sheet.getPhysicalNumberOfRows());
                logDebug("processSingleFile: Sheet last row num: " + sheet.getLastRowNum());

                SheetData sheetData = new SheetData(targetSheetName);
                Row headerRow = sheet.getRow(0);
                if (headerRow == null) {
                    logWarning("processSingleFile: Header row (row 0) is null for sheet: " + targetSheetName);
                    continue;
                }

                logDebug("processSingleFile: Header row has " + headerRow.getLastCellNum() + " cells");

                // First pass: collect headers and find Country column
                Map<Integer, String> headerMap = new LinkedHashMap<>();
                int countryColumnIndex = -1;

                for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                    Cell cell = headerRow.getCell(i);
                    if (cell != null) {
                        String headerName = getCellValueAsString(cell).trim();
                        logDebug("processSingleFile: Header cell " + i + ": '" + headerName + "'");
                        if (!headerName.isEmpty()) {
                            headerMap.put(i, headerName);
                            sheetData.getHeaders().add(headerName);
                            if (headerName.equalsIgnoreCase(countryColumn)) {
                                countryColumnIndex = i;
                                logDebug("processSingleFile: Found country column at index " + i + ": '" + headerName + "'");
                            }
                        }
                    } else {
                        logDebug("processSingleFile: Header cell " + i + " is null");
                    }
                }

                if (countryColumnIndex == -1) {
                    logError("processSingleFile: Country column '" + countryColumn + "' not found in sheet: " + targetSheetName, null);
                    return builder.success(false)
                            .errorMessage("Country column '" + countryColumn + "' not found in sheet: " + targetSheetName)
                            .build();
                }

                // Process data rows
                int rowCount = 0;
                int emptyRows = 0;
                for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    if (row == null) {
                        emptyRows++;
                        continue;
                    }

                    Map<String, String> rowData = new LinkedHashMap<>();
                    boolean hasData = false;

                    for (Map.Entry<Integer, String> entry : headerMap.entrySet()) {
                        int colIndex = entry.getKey();
                        String header = entry.getValue();
                        Cell cell = row.getCell(colIndex);
                        String value = (cell != null) ? getCellValueAsString(cell) : "";
                        if (!value.trim().isEmpty()) {
                            hasData = true;
                        }
                        rowData.put(header, value);
                    }

                    if (hasData || rowCount < 10) {
                        sheetData.getFullData().add(rowData);
                        totalRows++;
                        rowCount++;
                    }
                }

                logDebug("processSingleFile: Sheet '" + targetSheetName + "' - " + rowCount + " rows with data, " + emptyRows + " empty rows");

                // CRITICAL FIX: Process Region column for THIS sheet
                logDebug("processSingleFile: Processing Region column for sheet: " + targetSheetName);
                processRegionColumn(sheetData, countryColumn);

                sheetsDataMap.put(targetSheetName, sheetData);
            }

            logDebug("processSingleFile: Sheets processed: " + sheetsProcessed + " out of " + TARGET_SHEETS.length + " targets");
            logDebug("processSingleFile: Total rows loaded: " + totalRows);

            if (sheetsDataMap.isEmpty()) {
                logWarning("processSingleFile: No valid target sheets found in workbook");
                logDebug("processSingleFile: Available sheets: " + getAvailableSheetNames(safeWorkbook));
                return builder.success(false)
                        .errorMessage("No valid sheets ('FY data' or 'UGPG data') found inside workbook. Available: " + getAvailableSheetNames(safeWorkbook))
                        .build();
            }

            builder.sheetsData(sheetsDataMap)
                    .totalRowCount(totalRows)
                    .success(true);

            logSuccess("processSingleFile: File processed successfully: " + file.getName());

        } catch (Exception e) {
            logError("processSingleFile: Error processing file: " + e.getMessage(), e);
            return builder.success(false)
                    .errorMessage("Error processing file: " + e.getMessage() + "\n" + getStackTraceString(e))
                    .build();
        }

        return builder.build();
    }

    private String getAvailableSheetNames(Workbook workbook) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("'").append(workbook.getSheetName(i)).append("'");
        }
        return sb.toString();
    }

    private String getStackTraceString(Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        return sw.toString();
    }

    private void processRegionColumn(SheetData sheetData, String countryColumn) {
        logDebug("processRegionColumn: Processing sheet: " + sheetData.getSheetName());
        if (sheetData.getFullData().isEmpty()) {
            logDebug("processRegionColumn: Sheet has no data, skipping");
            return;
        }

        // Check if Region column already exists
        boolean hasRegion = sheetData.getHeaders().contains("Region");
        logDebug("processRegionColumn: Region column exists: " + hasRegion);

        List<Map<String, String>> updatedFullData = new ArrayList<>();
        int rowsProcessed = 0;

        for (Map<String, String> row : sheetData.getFullData()) {
            Map<String, String> updatedRow = new LinkedHashMap<>();
            String countryValue = row.getOrDefault(countryColumn, "");
            String regionValue = getRegionForCountry(countryValue);
            rowsProcessed++;

            if (!hasRegion) {
                // Add Region column after Country column
                for (Map.Entry<String, String> entry : row.entrySet()) {
                    updatedRow.put(entry.getKey(), entry.getValue());
                    if (entry.getKey().equals(countryColumn)) {
                        updatedRow.put("Region", regionValue);
                        if (rowsProcessed <= 5) {
                            logDebug("processRegionColumn: Sample row " + rowsProcessed + " - Country: '" + countryValue + "' -> Region: '" + regionValue + "'");
                        }
                    }
                }
                if (!updatedRow.containsKey("Region")) {
                    updatedRow.put("Region", regionValue);
                }
            } else {
                // Update existing Region column
                for (Map.Entry<String, String> entry : row.entrySet()) {
                    if (entry.getKey().equals("Region")) {
                        updatedRow.put("Region", regionValue);
                    } else {
                        updatedRow.put(entry.getKey(), entry.getValue());
                    }
                }
            }
            updatedFullData.add(updatedRow);
        }

        sheetData.getFullData().clear();
        sheetData.getFullData().addAll(updatedFullData);

        // Update headers
        if (!hasRegion) {
            int countryIndex = sheetData.getHeaders().indexOf(countryColumn);
            if (countryIndex != -1 && countryIndex < sheetData.getHeaders().size() - 1) {
                sheetData.getHeaders().add(countryIndex + 1, "Region");
                logDebug("processRegionColumn: Added Region column after Country column (index " + countryIndex + ")");
            } else {
                sheetData.getHeaders().add("Region");
                logDebug("processRegionColumn: Added Region column at end");
            }
        }

        // Update preview
        int previewSize = Math.min(PREVIEW_LIMIT, sheetData.getFullData().size());
        sheetData.getPreviewData().clear();
        sheetData.getPreviewData().addAll(
                sheetData.getFullData().subList(0, previewSize)
        );

        logDebug("processRegionColumn: Completed processing " + rowsProcessed + " rows for sheet: " + sheetData.getSheetName());
    }

    public void addRegionColumn(FileProcessResult result) {
        logDebug("addRegionColumn: Starting for file: " + result.getFileName());
        if (!result.isSuccess() || result.getSheetsData().isEmpty()) {
            logWarning("addRegionColumn: Result not successful or no sheets data");
            return;
        }

        String countryColumn = result.getCountryColumn();
        for (SheetData sheetData : result.getSheetsData().values()) {
            processRegionColumn(sheetData, countryColumn);
        }
        logSuccess("addRegionColumn: Completed for file: " + result.getFileName());
    }

    public void populateRegionColumn(FileProcessResult result) {
        logDebug("populateRegionColumn: Starting for file: " + result.getFileName());
        if (!result.isSuccess() || result.getSheetsData().isEmpty()) {
            logWarning("populateRegionColumn: Result not successful or no sheets data");
            return;
        }

        String countryColumn = result.getCountryColumn();
        for (SheetData sheetData : result.getSheetsData().values()) {
            if (!sheetData.getHeaders().contains("Region")) {
                logDebug("populateRegionColumn: Region column not found, adding...");
                processRegionColumn(sheetData, countryColumn);
                continue;
            }

            List<Map<String, String>> updatedFullData = new ArrayList<>();
            int rowsProcessed = 0;

            for (Map<String, String> row : sheetData.getFullData()) {
                Map<String, String> updatedRow = new LinkedHashMap<>();
                String countryValue = row.getOrDefault(countryColumn, "");
                String regionValue = getRegionForCountry(countryValue);
                rowsProcessed++;

                for (Map.Entry<String, String> entry : row.entrySet()) {
                    if (entry.getKey().equals("Region")) {
                        updatedRow.put("Region", regionValue);
                    } else {
                        updatedRow.put(entry.getKey(), entry.getValue());
                    }
                }
                updatedFullData.add(updatedRow);
            }

            sheetData.getFullData().clear();
            sheetData.getFullData().addAll(updatedFullData);

            // Update preview
            int previewSize = Math.min(PREVIEW_LIMIT, sheetData.getFullData().size());
            sheetData.getPreviewData().clear();
            sheetData.getPreviewData().addAll(
                    sheetData.getFullData().subList(0, previewSize)
            );

            logDebug("populateRegionColumn: Updated " + rowsProcessed + " rows for sheet: " + sheetData.getSheetName());
        }
        logSuccess("populateRegionColumn: Completed for file: " + result.getFileName());
    }

    public int exportFiles(List<FileProcessResult> results, File outputDirectory) {
        logDebug("========================================");
        logDebug("exportFiles: Starting export of " + results.size() + " files");
        logDebug("exportFiles: Output directory: " + outputDirectory.getAbsolutePath());
        logDebug("========================================");

        int exported = 0;
        List<String> errors = new ArrayList<>();

        if (!outputDirectory.exists()) {
            logDebug("exportFiles: Output directory doesn't exist, creating...");
            if (outputDirectory.mkdirs()) {
                logDebug("exportFiles: Output directory created successfully");
            } else {
                logError("exportFiles: Failed to create output directory", null);
            }
        }

        for (FileProcessResult result : results) {
            logDebug("exportFiles: Processing file: " + result.getFileName());
            if (!result.isSuccess()) {
                logWarning("exportFiles: Skipping failed file: " + result.getFileName() + " - " + result.getErrorMessage());
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
                    logDebug("exportFiles: Using alternate filename: " + outputFileName);
                }

                logDebug("exportFiles: Creating Excel file with preserved formatting: " + outputFile.getAbsolutePath());

                // Use the new method that preserves formatting
                createExcelFileWithFormatting(result, outputFile);

                exported++;
                logSuccess("exportFiles: Exported: " + outputFile.getName() + " (" + outputFile.length() + " bytes)");
            } catch (Exception e) {
                String errorMsg = "Error exporting file " + result.getFileName() + ": " + e.getMessage();
                errors.add(errorMsg);
                logError("exportFiles: " + errorMsg, e);
            }
        }

        if (!errors.isEmpty()) {
            logWarning("exportFiles: Export completed with " + errors.size() + " errors:");
            for (String error : errors) {
                logWarning("exportFiles:  - " + error);
            }
        }

        logSuccess("exportFiles: Exported " + exported + " files successfully");
        return exported;
    }

    private void createExcelFileWithFormatting(FileProcessResult result, File outputFile) throws IOException {
        logDebug("createExcelFileWithFormatting: Creating file with preserved formatting");

        // First, read the original file
        File originalFile = new File(result.getFilePath());
        logDebug("createExcelFileWithFormatting: Original file: " + originalFile.getAbsolutePath());

        if (!originalFile.exists()) {
            logWarning("createExcelFileWithFormatting: Original file not found, creating from scratch");
            createExcelFileWithFormatting(result, outputFile); // Fallback to the old method
            return;
        }

        Workbook workbook = null;
        try {
            // Open the original workbook with read/write access
            if (originalFile.getName().endsWith(".xlsx")) {
                // Use OPCPackage for better control
                OPCPackage pkg = OPCPackage.open(originalFile, PackageAccess.READ_WRITE);
                workbook = new XSSFWorkbook(pkg);
            } else if (originalFile.getName().endsWith(".xls")) {
                try (InputStream fis = new FileInputStream(originalFile)) {
                    workbook = new HSSFWorkbook(fis);
                }
            } else {
                throw new IOException("Unsupported file format: " + originalFile.getName());
            }

            logDebug("createExcelFileWithFormatting: Original workbook opened successfully");

            // Get the sheets we need to modify
            Map<String, SheetData> sheetsData = result.getSheetsData();

            for (Map.Entry<String, SheetData> entry : sheetsData.entrySet()) {
                String sheetName = entry.getKey();
                SheetData sheetData = entry.getValue();

                logDebug("createExcelFileWithFormatting: Processing sheet: " + sheetName);

                // Find the sheet in the workbook (with case-insensitive and trimmed matching)
                Sheet sheet = null;
                String actualSheetName = null;

                for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                    String name = workbook.getSheetName(i);
                    if (name.trim().equalsIgnoreCase(sheetName.trim())) {
                        sheet = workbook.getSheetAt(i);
                        actualSheetName = name;
                        break;
                    }
                }

                if (sheet == null) {
                    logWarning("createExcelFileWithFormatting: Sheet '" + sheetName + "' not found in original workbook, skipping");
                    continue;
                }

                logDebug("createExcelFileWithFormatting: Found sheet: '" + actualSheetName + "'");

                // Get the header row from the original sheet
                Row headerRow = sheet.getRow(0);
                if (headerRow == null) {
                    logWarning("createExcelFileWithFormatting: Header row not found in sheet: " + sheetName);
                    continue;
                }

                // Determine the country column index and region column index
                int countryColIndex = -1;
                int regionColIndex = -1;

                for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                    Cell cell = headerRow.getCell(i);
                    if (cell != null) {
                        String cellValue = getCellValueAsString(cell).trim();
                        if (cellValue.equalsIgnoreCase(result.getCountryColumn())) {
                            countryColIndex = i;
                            logDebug("createExcelFileWithFormatting: Found country column at index: " + i);
                        }
                        if (cellValue.equalsIgnoreCase("Region")) {
                            regionColIndex = i;
                            logDebug("createExcelFileWithFormatting: Found region column at index: " + i);
                        }
                    }
                }

                // If Region column doesn't exist, add it
                if (regionColIndex == -1 && countryColIndex != -1) {
                    logDebug("createExcelFileWithFormatting: Adding Region column after country column");

                    // Add the column by shifting cells to the right
                    int insertIndex = countryColIndex + 1;

                    // Shift all columns to the right from insertIndex
                    for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                        Row row = sheet.getRow(r);
                        if (row == null) continue;

                        // Shift cells to the right
                        Cell lastCell = row.getCell(row.getLastCellNum() - 1);
                        if (lastCell != null) {
                            row.shiftCellsRight(insertIndex, row.getLastCellNum() - 1, 1);
                        }
                    }

                    // Set the header cell for Region
                    Row headerRow2 = sheet.getRow(0);
                    if (headerRow2 != null) {
                        Cell regionCell = headerRow2.createCell(insertIndex);
                        regionCell.setCellValue("Region");

                        // Copy style from country column if available
                        Cell countryCell = headerRow2.getCell(countryColIndex);
                        if (countryCell != null) {
                            regionCell.setCellStyle(countryCell.getCellStyle());
                        }
                    }

                    regionColIndex = insertIndex;
                    logDebug("createExcelFileWithFormatting: Region column added at index: " + regionColIndex);
                }

                // Now populate the Region column with data
                if (regionColIndex != -1 && countryColIndex != -1) {
                    logDebug("createExcelFileWithFormatting: Populating Region column at index: " + regionColIndex);

                    // Get the data rows (skip header row)
                    List<Map<String, String>> fullData = sheetData.getFullData();
                    int rowIndex = 1; // Start from row 1 (after header)

                    for (Map<String, String> rowData : fullData) {
                        Row row = sheet.getRow(rowIndex);
                        if (row == null) {
                            row = sheet.createRow(rowIndex);
                        }

                        // Get the country value from the row data
                        String countryValue = rowData.getOrDefault(result.getCountryColumn(), "");
                        String regionValue = getRegionForCountry(countryValue);

                        // Set the Region cell
                        Cell regionCell = row.getCell(regionColIndex);
                        if (regionCell == null) {
                            regionCell = row.createCell(regionColIndex);
                        }
                        regionCell.setCellValue(regionValue);

                        // Copy style from the country cell if available
                        Cell countryCell = row.getCell(countryColIndex);
                        if (countryCell != null && countryCell.getCellStyle() != null) {
                            regionCell.setCellStyle(countryCell.getCellStyle());
                        }

                        rowIndex++;
                    }

                    logDebug("createExcelFileWithFormatting: Populated " + fullData.size() + " rows with Region data");
                }
            }

            // Write the modified workbook to the output file
            logDebug("createExcelFileWithFormatting: Writing to output file: " + outputFile.getAbsolutePath());
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                workbook.write(fos);
            }

            logDebug("createExcelFileWithFormatting: File written successfully");

        } catch (Exception e) {
            logError("createExcelFileWithFormatting: Error: " + e.getMessage(), e);
            // Fallback to creating from scratch
            logWarning("createExcelFileWithFormatting: Falling back to creating from scratch");
            createExcelFileWithFormatting(result, outputFile);
        } finally {
            if (workbook != null) {
                try {
                    workbook.close();
                    logDebug("createExcelFileWithFormatting: Workbook closed");
                } catch (IOException e) {
                    logDebug("createExcelFileWithFormatting: Error closing workbook: " + e.getMessage());
                }
            }
        }
    }

    // =========================================================================
    // ENHANCED: Aggressively repair corrupted relationship files
    // =========================================================================

    private Workbook createWorkbook(File file) throws IOException, InvalidFormatException {
        String fileName = file.getName();
        System.out.println("[DEBUG] createWorkbook: Creating workbook for: " + fileName);
        System.out.println("[DEBUG] createWorkbook: File size: " + file.length() + " bytes");

        if (fileName.endsWith(".xlsx")) {
            try {
                System.out.println("[DEBUG] createWorkbook: Detected .xlsx format");
                System.out.println("[DEBUG] createWorkbook: Attempting normal XSSFWorkbook read...");

                // Use a FileInputStream instead of File directly to avoid locking issues
                try (InputStream fis = new FileInputStream(file)) {
                    Workbook workbook = new XSSFWorkbook(fis);
                    System.out.println("[DEBUG] createWorkbook: Normal read successful");
                    return workbook;
                }
            } catch (Exception e) {
                System.err.println("[DEBUG] createWorkbook: Normal read failed: " + e.getMessage());

                // Check if it's a relationship-related error
                if (e.getMessage() != null &&
                        (e.getMessage().contains("mailto") ||
                                e.getMessage().contains("URI") ||
                                e.getMessage().contains("Illegal character") ||
                                e.getMessage().contains("RelationshipId") ||
                                e.getMessage().contains("Element type") ||
                                e.getMessage().contains("parse"))) {

                    System.out.println("🔧 Repairing corrupted relationships in: " + file.getName());

                    try {
                        // Aggressively repair the relationship files
                        File repairedFile = repairCorruptedRelationships(file);

                        // Read the repaired file using InputStream
                        try (InputStream fis = new FileInputStream(repairedFile)) {
                            Workbook workbook = new XSSFWorkbook(fis);
                            System.out.println("[DEBUG] createWorkbook: Repaired read successful");
                            return workbook;
                        }
                    } catch (Exception ex) {
                        System.err.println("⚠️ Repair failed: " + ex.getMessage());

                        // Ultimate fallback - try OPC Package
                        try {
                            System.out.println("📖 Trying OPC Package fallback...");
                            OPCPackage pkg = OPCPackage.open(file, PackageAccess.READ);
                            Workbook workbook = new XSSFWorkbook(pkg);
                            System.out.println("[DEBUG] createWorkbook: OPC Package fallback successful");
                            return workbook;
                        } catch (Exception ex2) {
                            throw new InvalidFormatException("Cannot read file: " + ex2.getMessage(), ex2);
                        }
                    }
                } else {
                    throw new InvalidFormatException("Error reading file: " + e.getMessage(), e);
                }
            }
        } else if (fileName.endsWith(".xls")) {
            System.out.println("[DEBUG] createWorkbook: Detected .xls format");
            try (InputStream fis = new FileInputStream(file)) {
                return new HSSFWorkbook(fis);
            }
        } else {
            throw new InvalidFormatException("Unsupported file format: " + fileName);
        }
    }

    /**
     * Repair corrupted relationship (.rels) files by rebuilding them properly
     * This handles the "RelationshipId" tag error and malformed mailto links
     */
    private File repairCorruptedRelationships(File inputFile) throws IOException {
        logDebug("repairCorruptedRelationships: Starting repair for: " + inputFile.getName());
        File outputFile = File.createTempFile("repaired_workbook_", ".xlsx");
        outputFile.deleteOnExit();
        logDebug("repairCorruptedRelationships: Temp output file: " + outputFile.getAbsolutePath());

        try (ZipFile zipFile = new ZipFile(inputFile);
             FileOutputStream fos = new FileOutputStream(outputFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            int totalEntries = 0;
            int relsFilesFound = 0;
            int relsFilesRepaired = 0;

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String entryName = entry.getName();
                totalEntries++;

                // Process all .rels files (relationship files)
                if (entryName.endsWith(".rels")) {
                    relsFilesFound++;
                    logDebug("repairCorruptedRelationships: Found .rels file: " + entryName + " (size: " + entry.getSize() + " bytes)");

                    byte[] content = readZipEntryContent(zipFile, entry);
                    String contentStr = new String(content, "UTF-8");
                    logDebug("repairCorruptedRelationships: Original content length: " + contentStr.length() + " chars");
                    logDebug("repairCorruptedRelationships: Original content preview: " + contentStr.substring(0, Math.min(200, contentStr.length())) + "...");

                    // Rebuild the relationship XML properly
                    String rebuilt = rebuildRelationshipXML(contentStr, entryName);
                    logDebug("repairCorruptedRelationships: Rebuilt content length: " + rebuilt.length() + " chars");

                    if (!rebuilt.equals(contentStr)) {
                        relsFilesRepaired++;
                        logSuccess("repairCorruptedRelationships: Rebuilt corrupted XML in: " + entryName);
                        logDebug("repairCorruptedRelationships: Rebuilt content preview: " + rebuilt.substring(0, Math.min(200, rebuilt.length())) + "...");
                    } else {
                        logDebug("repairCorruptedRelationships: No changes needed for: " + entryName);
                    }

                    ZipEntry newEntry = new ZipEntry(entry.getName());
                    zos.putNextEntry(newEntry);
                    zos.write(rebuilt.getBytes("UTF-8"));
                    zos.closeEntry();
                } else {
                    // Copy all other files unchanged
                    logDebug("repairCorruptedRelationships: Copying file: " + entryName + " (size: " + entry.getSize() + " bytes)");
                    ZipEntry newEntry = new ZipEntry(entry.getName());
                    zos.putNextEntry(newEntry);

                    try (InputStream entryStream = zipFile.getInputStream(entry)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = entryStream.read(buffer)) != -1) {
                            zos.write(buffer, 0, bytesRead);
                        }
                    }
                    zos.closeEntry();
                }
            }

            logDebug("repairCorruptedRelationships: Processed " + totalEntries + " total entries");
            logDebug("repairCorruptedRelationships: Found " + relsFilesFound + " .rels files");
            logDebug("repairCorruptedRelationships: Repaired " + relsFilesRepaired + " .rels files");

            if (relsFilesFound == 0) {
                logWarning("repairCorruptedRelationships: No .rels files found in archive");
            }

        } catch (IOException e) {
            logError("repairCorruptedRelationships: Failed to repair file: " + e.getMessage(), e);
            throw e;
        }

        logDebug("repairCorruptedRelationships: Repair completed. Output file size: " + outputFile.length() + " bytes");
        return outputFile;
    }

    /**
     * Rebuild the relationship XML from corrupted content
     * This extracts valid Relationship elements and discards corrupted ones
     */
    private String rebuildRelationshipXML(String corruptedXml, String fileName) {
        logDebug("rebuildRelationshipXML: Processing: " + fileName);
        logDebug("rebuildRelationshipXML: Input length: " + corruptedXml.length() + " chars");

        // First, try to fix malformed mailto links
        String fixed = fixMailtoLinks(corruptedXml);
        logDebug("rebuildRelationshipXML: After mailto fix, length: " + fixed.length() + " chars");

        // Pattern to match valid Relationship elements
        // Valid: <Relationship Id="..." Target="..." Type="..."/>
        Pattern validPattern = Pattern.compile(
                "<Relationship\\s+[^>]*Id\\s*=\\s*\"[^\"]*\"[^>]*Target\\s*=\\s*\"[^\"]*\"[^>]*Type\\s*=\\s*\"[^\"]*\"[^>]*/>"
        );

        java.util.regex.Matcher matcher = validPattern.matcher(fixed);
        StringBuilder rebuilt = new StringBuilder();

        // Start with the standard XML header and Relationships root
        rebuilt.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        rebuilt.append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n");

        boolean foundValid = false;
        int validCount = 0;
        while (matcher.find()) {
            String rel = matcher.group();
            // Double-check and fix any remaining mailto spaces
            rel = rel.replaceAll("mailto:[\\s\\u00A0]+", "mailto:");
            rel = rel.replaceAll("mailto:\\s+", "mailto:");
            rebuilt.append("  ").append(rel).append("\n");
            foundValid = true;
            validCount++;
            logDebug("rebuildRelationshipXML: Found valid relationship: " + rel);
        }

        // If no valid relationships found, try a more lenient pattern
        if (!foundValid) {
            logWarning("rebuildRelationshipXML: No valid relationships found in: " + fileName + ", trying lenient pattern...");

            // Lenient pattern: just look for Relationship elements with Id and Target
            Pattern lenientPattern = Pattern.compile(
                    "<Relationship\\s+[^>]*Id\\s*=\\s*\"[^\"]*\"[^>]*Target\\s*=\\s*\"[^\"]*\"[^>]*/>"
            );

            matcher = lenientPattern.matcher(fixed);
            while (matcher.find()) {
                String rel = matcher.group();
                // Fix mailto spaces
                rel = rel.replaceAll("mailto:[\\s\\u00A0]+", "mailto:");
                rel = rel.replaceAll("mailto:\\s+", "mailto:");

                // Add a default Type if missing
                if (!rel.contains("Type=")) {
                    rel = rel.replace("/>", " Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\"/>");
                    logDebug("rebuildRelationshipXML: Added default Type to relationship");
                }
                rebuilt.append("  ").append(rel).append("\n");
                foundValid = true;
                validCount++;
                logDebug("rebuildRelationshipXML: Found lenient relationship: " + rel);
            }
        }

        // If still no valid relationships, create minimal valid XML
        if (!foundValid) {
            logWarning("rebuildRelationshipXML: No valid relationships found in: " + fileName + " - creating minimal XML");
            return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
                    "</Relationships>";
        }

        rebuilt.append("</Relationships>");
        logDebug("rebuildRelationshipXML: Found " + validCount + " valid relationships in: " + fileName);
        logDebug("rebuildRelationshipXML: Output length: " + rebuilt.length() + " chars");
        return rebuilt.toString();
    }

    /**
     * Fix mailto: links with spaces or non-breaking spaces
     */
    private String fixMailtoLinks(String content) {
        logDebug("fixMailtoLinks: Fixing mailto links...");
        // Fix mailto: followed by any whitespace (including non-breaking space \u00A0)
        String fixed = content.replaceAll("mailto:[\\s\\u00A0]+", "mailto:");
        // Also fix mailto: followed by a literal space
        fixed = fixed.replaceAll("mailto:\\s+", "mailto:");
        // Fix case where mailto: has no colon but has space
        fixed = fixed.replaceAll("mailto\\s+", "mailto:");

        if (!fixed.equals(content)) {
            logDebug("fixMailtoLinks: Fixed mailto issues in content");
        }
        return fixed;
    }

    private byte[] readZipEntryContent(ZipFile zipFile, ZipEntry entry) throws IOException {
        logDebug("readZipEntryContent: Reading entry: " + entry.getName());
        try (InputStream is = zipFile.getInputStream(entry);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            int totalBytes = 0;
            while ((bytesRead = is.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }
            logDebug("readZipEntryContent: Read " + totalBytes + " bytes from: " + entry.getName());
            return baos.toByteArray();
        }
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        try {
            DataFormatter formatter = new DataFormatter();
            String value = formatter.formatCellValue(cell);
            return value;
        } catch (Exception e) {
            logDebug("getCellValueAsString: Error formatting cell: " + e.getMessage());
            return "";
        }
    }
}
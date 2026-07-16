package com.example.app.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.util.CellRangeAddress;

import java.io.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class RegionAnalyserService {

    private static final String DEBUG_PREFIX = "🔍 [RegionAnalyserService]";
    private static final List<String> TARGET_SHEETS = Arrays.asList("FY data", "UGPG data");

    public static class RegionData {
        private String regionName;
        private final Map<String, Integer> foundationApps = new HashMap<>();
        private final Map<String, Integer> foundationOffers = new HashMap<>();
        private final Map<String, Integer> foundationAccept = new HashMap<>();
        private final Map<String, Integer> ugpgApps = new HashMap<>();
        private final Map<String, Integer> ugpgOffers = new HashMap<>();
        private final Map<String, Integer> ugpgAccept = new HashMap<>();

        public String getRegionName() { return regionName; }
        public void setRegionName(String regionName) { this.regionName = regionName; }

        public int getFoundationApps(String year) { return foundationApps.getOrDefault(year, 0); }
        public void setFoundationApps(String year, int value) { foundationApps.put(year, value); }
        public void addFoundationApp(String year) { foundationApps.put(year, foundationApps.getOrDefault(year, 0) + 1); }

        public int getFoundationOffers(String year) { return foundationOffers.getOrDefault(year, 0); }
        public void setFoundationOffers(String year, int value) { foundationOffers.put(year, value); }
        public void addFoundationOffer(String year) { foundationOffers.put(year, foundationOffers.getOrDefault(year, 0) + 1); }

        public int getFoundationAccept(String year) { return foundationAccept.getOrDefault(year, 0); }
        public void setFoundationAccept(String year, int value) { foundationAccept.put(year, value); }
        public void addFoundationAccept(String year) { foundationAccept.put(year, foundationAccept.getOrDefault(year, 0) + 1); }

        public int getUgpgApps(String year) { return ugpgApps.getOrDefault(year, 0); }
        public void setUgpgApps(String year, int value) { ugpgApps.put(year, value); }
        public void addUgpgApp(String year) { ugpgApps.put(year, ugpgApps.getOrDefault(year, 0) + 1); }

        public int getUgpgOffers(String year) { return ugpgOffers.getOrDefault(year, 0); }
        public void setUgpgOffers(String year, int value) { ugpgOffers.put(year, value); }
        public void addUgpgOffer(String year) { ugpgOffers.put(year, ugpgOffers.getOrDefault(year, 0) + 1); }

        public int getUgpgAccept(String year) { return ugpgAccept.getOrDefault(year, 0); }
        public void setUgpgAccept(String year, int value) { ugpgAccept.put(year, value); }
        public void addUgpgAccept(String year) { ugpgAccept.put(year, ugpgAccept.getOrDefault(year, 0) + 1); }
    }

    public static class AnalysisResult {
        private Map<String, RegionData> regionData = new LinkedHashMap<>();
        private final Map<String, Integer> totalFoundationApps = new HashMap<>();
        private final Map<String, Integer> totalFoundationOffers = new HashMap<>();
        private final Map<String, Integer> totalFoundationAccept = new HashMap<>();
        private final Map<String, Integer> totalUGPGApps = new HashMap<>();
        private final Map<String, Integer> totalUGPGOffers = new HashMap<>();
        private final Map<String, Integer> totalUGPGAccept = new HashMap<>();
        private List<String> years = new ArrayList<>();

        public Map<String, RegionData> getRegionData() { return regionData; }
        public void setRegionData(Map<String, RegionData> regionData) { this.regionData = regionData; }
        public List<String> getYears() { return years; }
        public void setYears(List<String> years) { this.years = years; }

        public int getTotalFoundationApps(String year) { return totalFoundationApps.getOrDefault(year, 0); }
        public void setTotalFoundationApps(String year, int value) { totalFoundationApps.put(year, value); }
        public int getTotalFoundationOffers(String year) { return totalFoundationOffers.getOrDefault(year, 0); }
        public void setTotalFoundationOffers(String year, int value) { totalFoundationOffers.put(year, value); }
        public int getTotalFoundationAccept(String year) { return totalFoundationAccept.getOrDefault(year, 0); }
        public void setTotalFoundationAccept(String year, int value) { totalFoundationAccept.put(year, value); }
        public int getTotalUGPGApps(String year) { return totalUGPGApps.getOrDefault(year, 0); }
        public void setTotalUGPGApps(String year, int value) { totalUGPGApps.put(year, value); }
        public int getTotalUGPGOffers(String year) { return totalUGPGOffers.getOrDefault(year, 0); }
        public void setTotalUGPGOffers(String year, int value) { totalUGPGOffers.put(year, value); }
        public int getTotalUGPGAccept(String year) { return totalUGPGAccept.getOrDefault(year, 0); }
        public void setTotalUGPGAccept(String year, int value) { totalUGPGAccept.put(year, value); }
        public int getTotalRegions() { return regionData.size(); }
    }

    // ===== SHEET NAMES =====
    public List<String> getSheetNames(File file) throws IOException {
        System.out.println(DEBUG_PREFIX + " getSheetNames: Reading file: " + file.getName());
        List<String> sheetNames = new ArrayList<>();
        try (Workbook workbook = createWorkbook(file)) {
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                String sheetName = workbook.getSheetName(i);
                sheetNames.add(sheetName);
                System.out.println(DEBUG_PREFIX + " getSheetNames: Found sheet: '" + sheetName + "'");
            }
        }
        System.out.println(DEBUG_PREFIX + " getSheetNames: Total sheets found: " + sheetNames.size());
        return sheetNames;
    }

    // ===== ANALYSE FILES =====
    public AnalysisResult analyseFiles(List<File> files, String year1, String year2, Consumer<Double> progressCallback) throws IOException {
        System.out.println(DEBUG_PREFIX + " ========================================");
        System.out.println(DEBUG_PREFIX + " analyseFiles: STARTING ANALYSIS");
        System.out.println(DEBUG_PREFIX + " analyseFiles: Files to process: " + files.size());
        System.out.println(DEBUG_PREFIX + " analyseFiles: Year1 = '" + year1 + "'");
        System.out.println(DEBUG_PREFIX + " analyseFiles: Year2 = '" + year2 + "'");
        System.out.println(DEBUG_PREFIX + " analyseFiles: Target sheets: " + TARGET_SHEETS);
        System.out.println(DEBUG_PREFIX + " ========================================");

        AnalysisResult result = new AnalysisResult();
        Map<String, RegionData> regionDataMap = new LinkedHashMap<>();
        List<String> years = Arrays.asList(year1, year2);
        result.setYears(years);

        if (files == null || files.isEmpty()) {
            System.err.println(DEBUG_PREFIX + " analyseFiles: ERROR - No files provided!");
            throw new IOException("No files provided for analysis");
        }

        int totalFiles = files.size();
        int processedFiles = 0;
        int totalFYSheetsFound = 0;
        int totalUGPGSheetsFound = 0;
        int totalRowsProcessed = 0;

        for (File file : files) {
            System.out.println(DEBUG_PREFIX + " analyseFiles: ----------------------------------------");
            System.out.println(DEBUG_PREFIX + " analyseFiles: Processing file: " + file.getName());
            System.out.println(DEBUG_PREFIX + " analyseFiles: File path: " + file.getAbsolutePath());
            System.out.println(DEBUG_PREFIX + " analyseFiles: File exists: " + file.exists());
            System.out.println(DEBUG_PREFIX + " analyseFiles: File size: " + file.length() + " bytes");

            if (!file.exists()) {
                System.err.println(DEBUG_PREFIX + " analyseFiles: WARNING - File does not exist, skipping: " + file.getName());
                continue;
            }

            try (Workbook workbook = createWorkbook(file)) {
                System.out.println(DEBUG_PREFIX + " analyseFiles: Workbook opened successfully");
                System.out.println(DEBUG_PREFIX + " analyseFiles: Total sheets in workbook: " + workbook.getNumberOfSheets());

                List<String> allSheets = new ArrayList<>();
                for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                    allSheets.add(workbook.getSheetName(i));
                }
                System.out.println(DEBUG_PREFIX + " analyseFiles: All sheets: " + allSheets);

                Sheet fySheet = getSheetIgnoreCase(workbook, "FY data");
                if (fySheet != null) {
                    System.out.println(DEBUG_PREFIX + " analyseFiles: ✓ Found 'FY data' sheet!");
                    totalFYSheetsFound++;
                    System.out.println(DEBUG_PREFIX + " analyseFiles: Processing FY data sheet for both years...");
                    int rowsProcessed = processSheet(fySheet, regionDataMap, year1, true);
                    System.out.println(DEBUG_PREFIX + " analyseFiles:   - Processed " + rowsProcessed + " rows for " + year1);
                    rowsProcessed = processSheet(fySheet, regionDataMap, year2, true);
                    System.out.println(DEBUG_PREFIX + " analyseFiles:   - Processed " + rowsProcessed + " rows for " + year2);
                    totalRowsProcessed += rowsProcessed * 2;
                } else {
                    System.out.println(DEBUG_PREFIX + " analyseFiles: ✗ 'FY data' sheet NOT found in this file");
                }

                Sheet ugpgSheet = getSheetIgnoreCase(workbook, "UGPG data");
                if (ugpgSheet != null) {
                    System.out.println(DEBUG_PREFIX + " analyseFiles: ✓ Found 'UGPG data' sheet!");
                    totalUGPGSheetsFound++;
                    System.out.println(DEBUG_PREFIX + " analyseFiles: Processing UGPG data sheet for both years...");
                    int rowsProcessed = processSheet(ugpgSheet, regionDataMap, year1, false);
                    System.out.println(DEBUG_PREFIX + " analyseFiles:   - Processed " + rowsProcessed + " rows for " + year1);
                    rowsProcessed = processSheet(ugpgSheet, regionDataMap, year2, false);
                    System.out.println(DEBUG_PREFIX + " analyseFiles:   - Processed " + rowsProcessed + " rows for " + year2);
                    totalRowsProcessed += rowsProcessed * 2;
                } else {
                    System.out.println(DEBUG_PREFIX + " analyseFiles: ✗ 'UGPG data' sheet NOT found in this file");
                }

            } catch (Exception e) {
                System.err.println(DEBUG_PREFIX + " analyseFiles: ERROR processing file: " + file.getName());
                System.err.println(DEBUG_PREFIX + " analyseFiles: Error: " + e.getMessage());
                e.printStackTrace();
                throw new IOException("Error processing file " + file.getName() + ": " + e.getMessage(), e);
            }

            processedFiles++;
            if (progressCallback != null) {
                double progress = (double) processedFiles / totalFiles;
                System.out.println(DEBUG_PREFIX + " analyseFiles: Progress: " + String.format("%.0f%%", progress * 100));
                progressCallback.accept(progress);
            }
        }

        System.out.println(DEBUG_PREFIX + " analyseFiles: ========================================");
        System.out.println(DEBUG_PREFIX + " analyseFiles: ANALYSIS SUMMARY");
        System.out.println(DEBUG_PREFIX + " analyseFiles:   - Total files processed: " + processedFiles);
        System.out.println(DEBUG_PREFIX + " analyseFiles:   - FY data sheets found: " + totalFYSheetsFound);
        System.out.println(DEBUG_PREFIX + " analyseFiles:   - UGPG data sheets found: " + totalUGPGSheetsFound);
        System.out.println(DEBUG_PREFIX + " analyseFiles:   - Total rows processed: " + totalRowsProcessed);
        System.out.println(DEBUG_PREFIX + " analyseFiles:   - Unique regions found: " + regionDataMap.size());

        result.setRegionData(regionDataMap);

        for (String year : years) {
            int tFApps = 0, tFOffers = 0, tFAccept = 0;
            int tUApps = 0, tUOffers = 0, tUAccept = 0;

            for (Map.Entry<String, RegionData> entry : regionDataMap.entrySet()) {
                RegionData d = entry.getValue();
                tFApps += d.getFoundationApps(year);
                tFOffers += d.getFoundationOffers(year);
                tFAccept += d.getFoundationAccept(year);
                tUApps += d.getUgpgApps(year);
                tUOffers += d.getUgpgOffers(year);
                tUAccept += d.getUgpgAccept(year);
            }

            result.setTotalFoundationApps(year, tFApps);
            result.setTotalFoundationOffers(year, tFOffers);
            result.setTotalFoundationAccept(year, tFAccept);
            result.setTotalUGPGApps(year, tUApps);
            result.setTotalUGPGOffers(year, tUOffers);
            result.setTotalUGPGAccept(year, tUAccept);

            System.out.println(DEBUG_PREFIX + " analyseFiles:   Totals for " + year + ":");
            System.out.println(DEBUG_PREFIX + " analyseFiles:     Foundation Apps: " + tFApps);
            System.out.println(DEBUG_PREFIX + " analyseFiles:     Foundation Offers: " + tFOffers);
            System.out.println(DEBUG_PREFIX + " analyseFiles:     Foundation Accept: " + tFAccept);
            System.out.println(DEBUG_PREFIX + " analyseFiles:     UGPG Apps: " + tUApps);
            System.out.println(DEBUG_PREFIX + " analyseFiles:     UGPG Offers: " + tUOffers);
            System.out.println(DEBUG_PREFIX + " analyseFiles:     UGPG Accept: " + tUAccept);
        }

        System.out.println(DEBUG_PREFIX + " analyseFiles: ANALYSIS COMPLETE");
        System.out.println(DEBUG_PREFIX + " ========================================");
        return result;
    }

    // ===== PROCESS SHEET =====
    private int processSheet(Sheet sheet, Map<String, RegionData> regionDataMap, String year, boolean isFoundation) throws IOException {
        System.out.println(DEBUG_PREFIX + " processSheet: STARTING");
        System.out.println(DEBUG_PREFIX + " processSheet: Sheet name = '" + sheet.getSheetName() + "'");
        System.out.println(DEBUG_PREFIX + " processSheet: Year = '" + year + "'");
        System.out.println(DEBUG_PREFIX + " processSheet: Is Foundation = " + isFoundation);
        System.out.println(DEBUG_PREFIX + " processSheet: Sheet last row = " + sheet.getLastRowNum());

        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            System.err.println(DEBUG_PREFIX + " processSheet: WARNING - Header row is null, skipping sheet");
            return 0;
        }

        System.out.println(DEBUG_PREFIX + " processSheet: Header row found, scanning for columns...");

        int regionCol = -1;
        int decisionCol = -1;
        int applicationYearCol = -1;

        for (int col = 0; col < headerRow.getLastCellNum(); col++) {
            Cell cell = headerRow.getCell(col);
            if (cell != null) {
                String headerVal = getCellValueAsString(cell).trim();
                System.out.println(DEBUG_PREFIX + " processSheet:   Column " + col + ": '" + headerVal + "'");

                if (regionCol == -1 && headerVal.equalsIgnoreCase("region")) {
                    regionCol = col;
                    System.out.println(DEBUG_PREFIX + " processSheet:   ✓ Found EXACT Region column at index " + col);
                } else if (decisionCol == -1 && headerVal.equalsIgnoreCase("decision")) {
                    decisionCol = col;
                    System.out.println(DEBUG_PREFIX + " processSheet:   ✓ Found EXACT Decision column at index " + col);
                } else if (applicationYearCol == -1 &&
                        (headerVal.equalsIgnoreCase("application year") ||
                                headerVal.equalsIgnoreCase("app year") ||
                                headerVal.toLowerCase().contains("application year") ||
                                headerVal.toLowerCase().contains("app year"))) {
                    applicationYearCol = col;
                    System.out.println(DEBUG_PREFIX + " processSheet:   ✓ Found Application Year column at index " + col);
                }
            }
        }

        System.out.println(DEBUG_PREFIX + " processSheet: Region column index: " + regionCol);
        System.out.println(DEBUG_PREFIX + " processSheet: Decision column index: " + decisionCol);
        System.out.println(DEBUG_PREFIX + " processSheet: Application Year column index: " + applicationYearCol);

        if (regionCol == -1) {
            System.err.println(DEBUG_PREFIX + " processSheet: ERROR - Region column not found!");
            throw new IOException("Required 'Region' column not found in sheet: " + sheet.getSheetName());
        }
        if (decisionCol == -1) {
            System.err.println(DEBUG_PREFIX + " processSheet: ERROR - Decision column not found!");
            throw new IOException("Required 'Decision' column not found in sheet: " + sheet.getSheetName());
        }
        if (applicationYearCol == -1) {
            System.err.println(DEBUG_PREFIX + " processSheet: ERROR - Application Year column not found!");
            throw new IOException("Required 'Application Year' column not found in sheet: " + sheet.getSheetName());
        }

        int rowsProcessed = 0;
        int rowsWithEmptyRegion = 0;
        int rowsWithEmptyDecision = 0;
        int rowsWithEmptyYear = 0;
        int rowsSkippedWrongYear = 0;
        int rowsSkippedInvalidRegion = 0;
        Map<String, Integer> regionCounts = new HashMap<>();
        Map<String, Integer> decisionCounts = new HashMap<>();

        System.out.println(DEBUG_PREFIX + " processSheet: Processing data rows for year: " + year);

        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;

            String region = getCellValueAsString(row.getCell(regionCol)).trim();
            String decision = getCellValueAsString(row.getCell(decisionCol)).trim().toUpperCase();
            String appYearStr = getCellValueAsString(row.getCell(applicationYearCol)).trim();

            if (region.isEmpty()) { rowsWithEmptyRegion++; continue; }
            if (decision.isEmpty()) { rowsWithEmptyDecision++; continue; }
            if (appYearStr.isEmpty()) { rowsWithEmptyYear++; continue; }

            // Skip invalid region values
            if (region.equalsIgnoreCase("Verify Entry") ||
                    region.equalsIgnoreCase("Verify Country Name") ||
                    region.toLowerCase().contains("verify entry") ||
                    region.toLowerCase().contains("verify country") ||
                    region.startsWith("_xlfn.") ||
                    region.contains("XLOOKUP") ||
                    region.contains("#REF") ||
                    region.contains("#N/A") ||
                    region.contains("#VALUE") ||
                    region.contains("_xlfn")) {
                rowsSkippedInvalidRegion++;
                continue;
            }

            // Parse application year
            String appYear = appYearStr;
            if (appYearStr.matches(".*\\d{4}.*")) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\d{4}").matcher(appYearStr);
                if (matcher.find()) {
                    appYear = matcher.group();
                }
            }

            if (!appYear.equals(year)) {
                rowsSkippedWrongYear++;
                continue;
            }

            rowsProcessed++;
            regionCounts.put(region, regionCounts.getOrDefault(region, 0) + 1);
            decisionCounts.put(decision, decisionCounts.getOrDefault(decision, 0) + 1);

            RegionData data = regionDataMap.computeIfAbsent(region, k -> {
                RegionData rd = new RegionData();
                rd.setRegionName(k);
                return rd;
            });

            boolean isOffer = isOffer(decision);
            boolean isAccept = isAccept(decision);

            if (isFoundation) {
                data.addFoundationApp(year);
                if (isOffer) data.addFoundationOffer(year);
                if (isAccept) data.addFoundationAccept(year);
            } else {
                data.addUgpgApp(year);
                if (isOffer) data.addUgpgOffer(year);
                if (isAccept) data.addUgpgAccept(year);
            }

            if (rowsProcessed <= 10) {
                System.out.println(DEBUG_PREFIX + " processSheet:   Row " + rowIndex + ": Region='" + region +
                        "', Decision='" + decision + "', AppYear='" + appYear +
                        "', isOffer=" + isOffer + ", isAccept=" + isAccept);
            }
        }

        System.out.println(DEBUG_PREFIX + " processSheet: ========================================");
        System.out.println(DEBUG_PREFIX + " processSheet: SUMMARY for year " + year);
        System.out.println(DEBUG_PREFIX + " processSheet:   Rows processed (valid data): " + rowsProcessed);
        System.out.println(DEBUG_PREFIX + " processSheet:   Rows skipped (wrong year): " + rowsSkippedWrongYear);
        System.out.println(DEBUG_PREFIX + " processSheet:   Rows skipped (invalid region): " + rowsSkippedInvalidRegion);
        System.out.println(DEBUG_PREFIX + " processSheet:   Rows with empty region: " + rowsWithEmptyRegion);
        System.out.println(DEBUG_PREFIX + " processSheet:   Rows with empty decision: " + rowsWithEmptyDecision);
        System.out.println(DEBUG_PREFIX + " processSheet:   Rows with empty year: " + rowsWithEmptyYear);
        System.out.println(DEBUG_PREFIX + " processSheet:   Unique valid regions found: " + regionCounts.size());
        System.out.println(DEBUG_PREFIX + " processSheet:   Decision codes found: " + decisionCounts.keySet());

        if (!regionCounts.isEmpty()) {
            System.out.println(DEBUG_PREFIX + " processSheet: Valid region counts:");
            regionCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(e -> System.out.println(DEBUG_PREFIX + " processSheet:   " + e.getKey() + ": " + e.getValue() + " rows"));
        }

        if (!decisionCounts.isEmpty()) {
            System.out.println(DEBUG_PREFIX + " processSheet: Decision counts:");
            decisionCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(e -> System.out.println(DEBUG_PREFIX + " processSheet:   " + e.getKey() + ": " + e.getValue() + " rows"));
        }

        System.out.println(DEBUG_PREFIX + " processSheet: COMPLETE - Processed " + rowsProcessed + " valid rows for year " + year);
        System.out.println(DEBUG_PREFIX + " processSheet: ========================================");
        return rowsProcessed;
    }

    private boolean isOffer(String decision) {
        if (decision == null) return false;
        String d = decision.trim().toUpperCase();
        if (d.equals("UO") || d.equals("CO") || d.equals("UA") || d.equals("CA")) return true;
        String lower = d.toLowerCase();
        return lower.contains("offer") || lower.contains("unconditional") || lower.contains("conditional");
    }

    private boolean isAccept(String decision) {
        if (decision == null) return false;
        String d = decision.trim().toUpperCase();
        if (d.equals("UA") || d.equals("CA")) return true;
        String lower = d.toLowerCase();
        return lower.contains("accept") || lower.contains("firm") || lower.contains("confirmed");
    }

    // ===== EXPORT REPORT =====
    public void exportReport(AnalysisResult result, File outputFile, String year1, String year2) throws IOException {
        System.out.println(DEBUG_PREFIX + " ========================================");
        System.out.println(DEBUG_PREFIX + " exportReport: STARTING VISUAL MATCH EXPORT");
        System.out.println(DEBUG_PREFIX + " exportReport: Output file: " + outputFile.getAbsolutePath());
        System.out.println(DEBUG_PREFIX + " exportReport: Year1 = '" + year1 + "', Year2 = '" + year2 + "'");
        System.out.println(DEBUG_PREFIX + " exportReport: Regions to export: " + result.getRegionData().size());

        // Debug: Print all region data before export
        System.out.println(DEBUG_PREFIX + " exportReport: --- REGION DATA DUMP ---");
        for (Map.Entry<String, RegionData> entry : result.getRegionData().entrySet()) {
            RegionData d = entry.getValue();
            System.out.println(DEBUG_PREFIX + " exportReport: Region: " + entry.getKey());
            System.out.println(DEBUG_PREFIX + " exportReport:   Foundation Apps " + year1 + ": " + d.getFoundationApps(year1));
            System.out.println(DEBUG_PREFIX + " exportReport:   Foundation Apps " + year2 + ": " + d.getFoundationApps(year2));
            System.out.println(DEBUG_PREFIX + " exportReport:   Foundation Offers " + year1 + ": " + d.getFoundationOffers(year1));
            System.out.println(DEBUG_PREFIX + " exportReport:   Foundation Offers " + year2 + ": " + d.getFoundationOffers(year2));
            System.out.println(DEBUG_PREFIX + " exportReport:   Foundation Accept " + year1 + ": " + d.getFoundationAccept(year1));
            System.out.println(DEBUG_PREFIX + " exportReport:   Foundation Accept " + year2 + ": " + d.getFoundationAccept(year2));
            System.out.println(DEBUG_PREFIX + " exportReport:   UGPG Apps " + year1 + ": " + d.getUgpgApps(year1));
            System.out.println(DEBUG_PREFIX + " exportReport:   UGPG Apps " + year2 + ": " + d.getUgpgApps(year2));
            System.out.println(DEBUG_PREFIX + " exportReport:   UGPG Offers " + year1 + ": " + d.getUgpgOffers(year1));
            System.out.println(DEBUG_PREFIX + " exportReport:   UGPG Offers " + year2 + ": " + d.getUgpgOffers(year2));
            System.out.println(DEBUG_PREFIX + " exportReport:   UGPG Accept " + year1 + ": " + d.getUgpgAccept(year1));
            System.out.println(DEBUG_PREFIX + " exportReport:   UGPG Accept " + year2 + ": " + d.getUgpgAccept(year2));
        }
        System.out.println(DEBUG_PREFIX + " exportReport: --- END REGION DATA DUMP ---");

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Region Analysis");
            sheet.setDisplayGridlines(true);

            // 1. Color Palette Customization
            java.awt.Color headerDarkBlue = new java.awt.Color(0, 0, 102);
            java.awt.Color foundationYellow = new java.awt.Color(211, 114, 13);
            java.awt.Color ugpgGreen = new java.awt.Color(39, 78, 39);
            java.awt.Color metricBlue = new java.awt.Color(51, 102, 255);
            java.awt.Color yearGrey = new java.awt.Color(104, 101, 101);

            // 2. Font Setup
            Font whiteBoldFont = workbook.createFont();
            whiteBoldFont.setBold(true);
            whiteBoldFont.setColor(IndexedColors.WHITE.getIndex());
            whiteBoldFont.setFontName("Arial");

            Font blackBoldFont = workbook.createFont();
            blackBoldFont.setBold(true);
            blackBoldFont.setColor(IndexedColors.BLACK.getIndex());
            blackBoldFont.setFontName("Arial");

            Font regularFont = workbook.createFont();
            regularFont.setFontName("Arial");

            // 3. Style Declarations
            CellStyle regionStyle = workbook.createCellStyle();
            regionStyle.setFillForegroundColor(new XSSFColor(headerDarkBlue, null));
            regionStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            regionStyle.setAlignment(HorizontalAlignment.CENTER);
            regionStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            regionStyle.setFont(whiteBoldFont);
            setThinBorders(regionStyle);

            CellStyle foundationTopStyle = workbook.createCellStyle();
            foundationTopStyle.setFillForegroundColor(new XSSFColor(foundationYellow, null));
            foundationTopStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            foundationTopStyle.setAlignment(HorizontalAlignment.CENTER);
            foundationTopStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            foundationTopStyle.setFont(whiteBoldFont);
            setThinBorders(foundationTopStyle);

            CellStyle ugpgTopStyle = workbook.createCellStyle();
            ugpgTopStyle.setFillForegroundColor(new XSSFColor(ugpgGreen, null));
            ugpgTopStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            ugpgTopStyle.setAlignment(HorizontalAlignment.CENTER);
            ugpgTopStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            ugpgTopStyle.setFont(whiteBoldFont);
            setThinBorders(ugpgTopStyle);

            CellStyle metricHeaderStyle = workbook.createCellStyle();
            metricHeaderStyle.setFillForegroundColor(new XSSFColor(metricBlue, null));
            metricHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            metricHeaderStyle.setAlignment(HorizontalAlignment.CENTER);
            metricHeaderStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            metricHeaderStyle.setFont(whiteBoldFont);
            setThinBorders(metricHeaderStyle);

            CellStyle yearHeaderStyle = workbook.createCellStyle();
            yearHeaderStyle.setFillForegroundColor(new XSSFColor(yearGrey, null));
            yearHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            yearHeaderStyle.setAlignment(HorizontalAlignment.CENTER);
            yearHeaderStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            yearHeaderStyle.setFont(whiteBoldFont);
            setThinBorders(yearHeaderStyle);

            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setAlignment(HorizontalAlignment.RIGHT);
            dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            dataStyle.setFont(regularFont);
            setThinBorders(dataStyle);

            CellStyle regionDataStyle = workbook.createCellStyle();
            regionDataStyle.setAlignment(HorizontalAlignment.LEFT);
            regionDataStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            regionDataStyle.setFont(regularFont);
            setThinBorders(regionDataStyle);

            CellStyle totalStyle = workbook.createCellStyle();
            totalStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            totalStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            totalStyle.setAlignment(HorizontalAlignment.RIGHT);
            totalStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            totalStyle.setFont(blackBoldFont);
            setThinBorders(totalStyle);

            CellStyle totalRegionStyle = workbook.createCellStyle();
            totalRegionStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            totalRegionStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            totalRegionStyle.setAlignment(HorizontalAlignment.CENTER);
            totalRegionStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            totalRegionStyle.setFont(blackBoldFont);
            setThinBorders(totalRegionStyle);

            // Get sorted list of regions
            List<String> regions = new ArrayList<>(result.getRegionData().keySet());
            Collections.sort(regions);
            System.out.println(DEBUG_PREFIX + " exportReport: Sorted regions: " + regions);

            int currentRow = 0;

            // =========================================================================
            // BLOCK 1: FOUNDATION PROGRAMMES
            // =========================================================================
            System.out.println(DEBUG_PREFIX + " exportReport: Building Foundation Programmes block...");
            currentRow = buildProgramBlock(sheet, currentRow, "Foundation Programmes", foundationTopStyle,
                    regionStyle, metricHeaderStyle, yearHeaderStyle, dataStyle, regionDataStyle, totalStyle, totalRegionStyle,
                    regions, result, year1, year2, true);

            // Two blank rows separating blocks
            currentRow += 2;
            System.out.println(DEBUG_PREFIX + " exportReport: Foundation block complete. Next row: " + currentRow);

            // =========================================================================
            // BLOCK 2: UG & PG PROGRAMMES
            // =========================================================================
            System.out.println(DEBUG_PREFIX + " exportReport: Building UG & PG Programmes block...");
            buildProgramBlock(sheet, currentRow, "UG & PG Programmes", ugpgTopStyle,
                    regionStyle, metricHeaderStyle, yearHeaderStyle, dataStyle, regionDataStyle, totalStyle, totalRegionStyle,
                    regions, result, year1, year2, false);

            System.out.println(DEBUG_PREFIX + " exportReport: UGPG block complete.");

            // Auto-size all 7 structural columns
            for (int i = 0; i <= 6; i++) {
                sheet.autoSizeColumn(i);
                if (sheet.getColumnWidth(i) < 3500) sheet.setColumnWidth(i, 3500);
            }

            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                workbook.write(fos);
                System.out.println(DEBUG_PREFIX + " exportReport: Successfully saved visual rendering format.");
                System.out.println(DEBUG_PREFIX + " exportReport: File size: " + outputFile.length() + " bytes");
            }

            System.out.println(DEBUG_PREFIX + " exportReport: EXPORT COMPLETE");
            System.out.println(DEBUG_PREFIX + " ========================================");
        } catch (Exception e) {
            System.err.println(DEBUG_PREFIX + " exportReport: ERROR - " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    private int buildProgramBlock(Sheet sheet, int startRow, String title, CellStyle topStyle,
                                  CellStyle regionStyle, CellStyle metricStyle, CellStyle yearStyle,
                                  CellStyle dataStyle, CellStyle regionDataStyle, CellStyle totalStyle, CellStyle totalRegionStyle,
                                  List<String> regions, AnalysisResult result, String year1, String year2, boolean isFoundation) {

        System.out.println(DEBUG_PREFIX + " buildProgramBlock: Building '" + title + "' at row " + startRow);
        System.out.println(DEBUG_PREFIX + " buildProgramBlock: isFoundation = " + isFoundation);
        System.out.println(DEBUG_PREFIX + " buildProgramBlock: year1 = " + year1 + ", year2 = " + year2);
        System.out.println(DEBUG_PREFIX + " buildProgramBlock: regions count = " + regions.size());

        // Row 0: Top Banner Header Group
        Row r0 = sheet.createRow(startRow);
        r0.setHeightInPoints(28);

        Cell cRegion = r0.createCell(0);
        cRegion.setCellValue("Region");
        cRegion.setCellStyle(regionStyle);

        Cell cTitle = r0.createCell(1);
        cTitle.setCellValue(title);
        cTitle.setCellStyle(topStyle);

        // Apply styling across full width
        for (int i = 2; i <= 6; i++) {
            Cell cell = r0.createCell(i);
            cell.setCellStyle(topStyle);
        }
        sheet.addMergedRegion(new CellRangeAddress(startRow, startRow, 1, 6));

        // Row 1: Metrics Header Row
        Row r1 = sheet.createRow(startRow + 1);
        r1.setHeightInPoints(22);
        Cell regionMergeCell = r1.createCell(0);
        regionMergeCell.setCellStyle(regionStyle);

        String[] categories = {"Applications", "Offers", "Acceptances"};
        for (int i = 0; i < categories.length; i++) {
            int targetCol = 1 + (i * 2);
            Cell mCell = r1.createCell(targetCol);
            mCell.setCellValue(categories[i]);
            mCell.setCellStyle(metricStyle);
            Cell emptyCell = r1.createCell(targetCol + 1);
            emptyCell.setCellStyle(metricStyle);
            sheet.addMergedRegion(new CellRangeAddress(startRow + 1, startRow + 1, targetCol, targetCol + 1));
        }
        sheet.addMergedRegion(new CellRangeAddress(startRow, startRow + 1, 0, 0));

        // Row 2: Subheading Years
        Row r2 = sheet.createRow(startRow + 2);
        r2.setHeightInPoints(20);
        Cell yearRegionCell = r2.createCell(0);
        yearRegionCell.setCellStyle(regionStyle);

        for (int col = 1; col <= 6; col++) {
            Cell cell = r2.createCell(col);
            cell.setCellValue((col % 2 != 0) ? year1 : year2);
            cell.setCellStyle(yearStyle);
        }

        // Data Rows Populate
        int dataStartRowIdx = startRow + 4;
        int currentIdx = startRow + 3;

        System.out.println(DEBUG_PREFIX + " buildProgramBlock: Populating data rows starting at index " + currentIdx);

        for (String region : regions) {
            Row row = sheet.createRow(currentIdx++);
            row.setHeightInPoints(20);

            Cell regCell = row.createCell(0);
            regCell.setCellValue(region);
            regCell.setCellStyle(regionDataStyle);

            RegionData d = result.getRegionData().get(region);
            if (d != null) {
                int apps1, apps2, offers1, offers2, accept1, accept2;

                if (isFoundation) {
                    apps1 = d.getFoundationApps(year1);
                    apps2 = d.getFoundationApps(year2);
                    offers1 = d.getFoundationOffers(year1);
                    offers2 = d.getFoundationOffers(year2);
                    accept1 = d.getFoundationAccept(year1);
                    accept2 = d.getFoundationAccept(year2);
                } else {
                    apps1 = d.getUgpgApps(year1);
                    apps2 = d.getUgpgApps(year2);
                    offers1 = d.getUgpgOffers(year1);
                    offers2 = d.getUgpgOffers(year2);
                    accept1 = d.getUgpgAccept(year1);
                    accept2 = d.getUgpgAccept(year2);
                }

                row.createCell(1).setCellValue(apps1);
                row.createCell(2).setCellValue(apps2);
                row.createCell(3).setCellValue(offers1);
                row.createCell(4).setCellValue(offers2);
                row.createCell(5).setCellValue(accept1);
                row.createCell(6).setCellValue(accept2);

                System.out.println(DEBUG_PREFIX + " buildProgramBlock: Region '" + region +
                        "' - Apps: " + apps1 + "/" + apps2 +
                        ", Offers: " + offers1 + "/" + offers2 +
                        ", Accept: " + accept1 + "/" + accept2);
            } else {
                System.err.println(DEBUG_PREFIX + " buildProgramBlock: WARNING - Region data missing for: " + region);
                for (int col = 1; col <= 6; col++) {
                    row.createCell(col).setCellValue(0);
                }
            }

            for (int col = 1; col <= 6; col++) {
                if (row.getCell(col) == null) row.createCell(col).setCellValue(0);
                row.getCell(col).setCellStyle(dataStyle);
            }
        }

        // Total Bottom Summary Row
        Row totalRow = sheet.createRow(currentIdx);
        totalRow.setHeightInPoints(22);

        Cell totalLabelCell = totalRow.createCell(0);
        totalLabelCell.setCellValue("Total");
        totalLabelCell.setCellStyle(totalRegionStyle);

        int lastDataRow = currentIdx - 1;

        System.out.println(DEBUG_PREFIX + " buildProgramBlock: Adding totals row at index " + currentIdx);
        System.out.println(DEBUG_PREFIX + " buildProgramBlock: dataStartRowIdx = " + dataStartRowIdx + ", lastDataRow = " + lastDataRow);

        // Use actual totals from result rather than formulas for consistency with UI
        int totalApps1, totalApps2, totalOffers1, totalOffers2, totalAccept1, totalAccept2;

        if (isFoundation) {
            totalApps1 = result.getTotalFoundationApps(year1);
            totalApps2 = result.getTotalFoundationApps(year2);
            totalOffers1 = result.getTotalFoundationOffers(year1);
            totalOffers2 = result.getTotalFoundationOffers(year2);
            totalAccept1 = result.getTotalFoundationAccept(year1);
            totalAccept2 = result.getTotalFoundationAccept(year2);
        } else {
            totalApps1 = result.getTotalUGPGApps(year1);
            totalApps2 = result.getTotalUGPGApps(year2);
            totalOffers1 = result.getTotalUGPGOffers(year1);
            totalOffers2 = result.getTotalUGPGOffers(year2);
            totalAccept1 = result.getTotalUGPGAccept(year1);
            totalAccept2 = result.getTotalUGPGAccept(year2);
        }

        // Set the total values directly (matching what the UI shows)
        totalRow.createCell(1).setCellValue(totalApps1);
        totalRow.createCell(2).setCellValue(totalApps2);
        totalRow.createCell(3).setCellValue(totalOffers1);
        totalRow.createCell(4).setCellValue(totalOffers2);
        totalRow.createCell(5).setCellValue(totalAccept1);
        totalRow.createCell(6).setCellValue(totalAccept2);

        for (int col = 1; col <= 6; col++) {
            totalRow.getCell(col).setCellStyle(totalStyle);
        }

        System.out.println(DEBUG_PREFIX + " buildProgramBlock: Totals set directly:");
        System.out.println(DEBUG_PREFIX + " buildProgramBlock:   Apps: " + totalApps1 + "/" + totalApps2);
        System.out.println(DEBUG_PREFIX + " buildProgramBlock:   Offers: " + totalOffers1 + "/" + totalOffers2);
        System.out.println(DEBUG_PREFIX + " buildProgramBlock:   Accept: " + totalAccept1 + "/" + totalAccept2);

        System.out.println(DEBUG_PREFIX + " buildProgramBlock: Completed '" + title + "' - " +
                regions.size() + " regions, " + (currentIdx - startRow - 3) + " data rows");
        return currentIdx + 1;
    }

    private void setThinBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setTopBorderColor(IndexedColors.BLACK.getIndex());
        style.setBottomBorderColor(IndexedColors.BLACK.getIndex());
        style.setLeftBorderColor(IndexedColors.BLACK.getIndex());
        style.setRightBorderColor(IndexedColors.BLACK.getIndex());
    }

    /**
     * Get Excel column name (1-indexed: A=1, B=2, ... Z=26, AA=27, ...)
     */
    private String getExcelColumnName(int col) {
        if (col <= 0) return "";
        StringBuilder sb = new StringBuilder();
        while (col > 0) {
            col--;
            sb.insert(0, (char) ('A' + (col % 26)));
            col = col / 26;
        }
        return sb.toString();
    }

    // ===== LEGACY METHOD - REMOVED, use the dynamic version instead =====
    // analyseFiles(File file2025, File file2026) has been removed.
    // Use analyseFiles(List<File> files, String year1, String year2, Consumer<Double> progressCallback)

    // ===== HELPER METHODS =====
    public Workbook createWorkbook(File file) throws IOException {
        System.out.println(DEBUG_PREFIX + " createWorkbook: Opening file: " + file.getName());
        try {
            byte[] cleanedBytes = repairExcelFile(file);
            System.out.println(DEBUG_PREFIX + " createWorkbook: File repaired successfully, size: " + cleanedBytes.length + " bytes");
            try (InputStream is = new ByteArrayInputStream(cleanedBytes)) {
                Workbook workbook = WorkbookFactory.create(is);
                System.out.println(DEBUG_PREFIX + " createWorkbook: Workbook created successfully");
                return workbook;
            }
        } catch (Exception e) {
            System.err.println(DEBUG_PREFIX + " createWorkbook: ERROR - " + e.getMessage());
            System.err.println(DEBUG_PREFIX + " createWorkbook: Falling back to direct file read");
            return WorkbookFactory.create(file);
        }
    }

    private byte[] repairExcelFile(File file) throws IOException {
        System.out.println(DEBUG_PREFIX + " repairExcelFile: Starting repair for: " + file.getName());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(file);
             java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos)) {

            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zipFile.entries();
            int entryCount = 0;
            int relsFilesFound = 0;

            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                entryCount++;
                java.util.zip.ZipEntry newEntry = new java.util.zip.ZipEntry(entry.getName());
                zos.putNextEntry(newEntry);

                try (InputStream is = zipFile.getInputStream(entry)) {
                    if (entry.getName().endsWith(".rels")) {
                        relsFilesFound++;
                        String content = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                        String repairedContent = fixMailtoLinks(content);
                        zos.write(repairedContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    } else {
                        is.transferTo(zos);
                    }
                }
                zos.closeEntry();
            }

            System.out.println(DEBUG_PREFIX + " repairExcelFile: Processed " + entryCount + " entries, " + relsFilesFound + " .rels files");

        } catch (Exception e) {
            System.err.println(DEBUG_PREFIX + " repairExcelFile: ERROR - " + e.getMessage());
            throw e;
        }
        return baos.toByteArray();
    }

    private String fixMailtoLinks(String content) {
        String fixed = content.replaceAll("mailto:[\\s\\u00A0]+", "mailto:");
        fixed = fixed.replaceAll("mailto:\\s+", "mailto:");
        fixed = fixed.replaceAll("mailto\\s+", "mailto:");
        return fixed;
    }

    private Sheet getSheetIgnoreCase(Workbook workbook, String sheetName) {
        System.out.println(DEBUG_PREFIX + " getSheetIgnoreCase: Looking for sheet: '" + sheetName + "'");
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            String name = workbook.getSheetName(i);
            if (name.trim().equalsIgnoreCase(sheetName.trim())) {
                System.out.println(DEBUG_PREFIX + " getSheetIgnoreCase: ✓ Found sheet: '" + name + "' at index " + i);
                return workbook.getSheetAt(i);
            }
        }
        System.out.println(DEBUG_PREFIX + " getSheetIgnoreCase: ✗ Sheet '" + sheetName + "' NOT found");
        return null;
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        try {
            if (cell.getCellType() == CellType.FORMULA) {
                FormulaEvaluator evaluator = cell.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator();
                CellValue cellValue = evaluator.evaluate(cell);
                if (cellValue != null) {
                    return switch (cellValue.getCellType()) {
                        case STRING -> cellValue.getStringValue();
                        case NUMERIC -> String.valueOf(cellValue.getNumberValue());
                        case BOOLEAN -> String.valueOf(cellValue.getBooleanValue());
                        default -> "";
                    };
                }
                try { return cell.getStringCellValue(); } catch (Exception e) { return ""; }
            }
            DataFormatter formatter = new DataFormatter();
            return formatter.formatCellValue(cell);
        } catch (Exception e) {
            System.err.println(DEBUG_PREFIX + " getCellValueAsString: Error: " + e.getMessage());
            return "";
        }
    }

    private String getColName(int index) {
        if (index < 26) return String.valueOf((char) ('A' + index));
        return "A" + (char) ('A' + (index - 26));
    }

    // ===== STYLE METHODS =====
    private CellStyle createTitleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.MEDIUM);
        style.setBorderBottom(BorderStyle.MEDIUM);
        style.setBorderLeft(BorderStyle.MEDIUM);
        style.setBorderRight(BorderStyle.MEDIUM);
        return style;
    }

    private CellStyle createSubHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createTotalStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createFoundationHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        font.setFontHeightInPoints((short) 12);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.CORNFLOWER_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.MEDIUM);
        style.setBorderBottom(BorderStyle.MEDIUM);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createUGPGHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        font.setFontHeightInPoints((short) 12);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREEN.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.MEDIUM);
        style.setBorderBottom(BorderStyle.MEDIUM);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle getYearHeaderStyle(Workbook workbook, String year) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        if (year != null && !year.equals("Total")) {
            style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        return style;
    }
}
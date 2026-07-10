package com.example.app.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.usermodel.IndexedColors;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

public class RegionAnalyserService {

    public static class RegionData {
        private String regionName;

        // Foundation Programmes
        private int foundationApps2025;
        private int foundationOffers2025;
        private int foundationAccept2025;
        private int foundationApps2026;
        private int foundationOffers2026;
        private int foundationAccept2026;

        // UG & PG Programmes
        private int ugpgApps2025;
        private int ugpgOffers2025;
        private int ugpgAccept2025;
        private int ugpgApps2026;
        private int ugpgOffers2026;
        private int ugpgAccept2026;

        // ===== GETTERS AND SETTERS =====

        public String getRegionName() { return regionName; }
        public void setRegionName(String regionName) { this.regionName = regionName; }

        public int getFoundationApps2025() { return foundationApps2025; }
        public void setFoundationApps2025(int foundationApps2025) { this.foundationApps2025 = foundationApps2025; }
        public int getFoundationOffers2025() { return foundationOffers2025; }
        public void setFoundationOffers2025(int foundationOffers2025) { this.foundationOffers2025 = foundationOffers2025; }
        public int getFoundationAccept2025() { return foundationAccept2025; }
        public void setFoundationAccept2025(int foundationAccept2025) { this.foundationAccept2025 = foundationAccept2025; }

        public int getFoundationApps2026() { return foundationApps2026; }
        public void setFoundationApps2026(int foundationApps2026) { this.foundationApps2026 = foundationApps2026; }
        public int getFoundationOffers2026() { return foundationOffers2026; }
        public void setFoundationOffers2026(int foundationOffers2026) { this.foundationOffers2026 = foundationOffers2026; }
        public int getFoundationAccept2026() { return foundationAccept2026; }
        public void setFoundationAccept2026(int foundationAccept2026) { this.foundationAccept2026 = foundationAccept2026; }

        public int getUgpgApps2025() { return ugpgApps2025; }
        public void setUgpgApps2025(int ugpgApps2025) { this.ugpgApps2025 = ugpgApps2025; }
        public int getUgpgOffers2025() { return ugpgOffers2025; }
        public void setUgpgOffers2025(int ugpgOffers2025) { this.ugpgOffers2025 = ugpgOffers2025; }
        public int getUgpgAccept2025() { return ugpgAccept2025; }
        public void setUgpgAccept2025(int ugpgAccept2025) { this.ugpgAccept2025 = ugpgAccept2025; }

        public int getUgpgApps2026() { return ugpgApps2026; }
        public void setUgpgApps2026(int ugpgApps2026) { this.ugpgApps2026 = ugpgApps2026; }
        public int getUgpgOffers2026() { return ugpgOffers2026; }
        public void setUgpgOffers2026(int ugpgOffers2026) { this.ugpgOffers2026 = ugpgOffers2026; }
        public int getUgpgAccept2026() { return ugpgAccept2026; }
        public void setUgpgAccept2026(int ugpgAccept2026) { this.ugpgAccept2026 = ugpgAccept2026; }
    }

    public static class AnalysisResult {
        private Map<String, RegionData> regionData = new LinkedHashMap<>();

        private int totalFoundationApps2025;
        private int totalFoundationOffers2025;
        private int totalFoundationAccept2025;
        private int totalFoundationApps2026;
        private int totalFoundationOffers2026;
        private int totalFoundationAccept2026;

        private int totalUGPGApps2025;
        private int totalUGPGOffers2025;
        private int totalUGPGAccept2025;
        private int totalUGPGApps2026;
        private int totalUGPGOffers2026;
        private int totalUGPGAccept2026;

        // ===== GETTERS AND SETTERS =====

        public Map<String, RegionData> getRegionData() { return regionData; }
        public void setRegionData(Map<String, RegionData> regionData) { this.regionData = regionData; }

        public int getTotalFoundationApps2025() { return totalFoundationApps2025; }
        public void setTotalFoundationApps2025(int totalFoundationApps2025) { this.totalFoundationApps2025 = totalFoundationApps2025; }
        public int getTotalFoundationOffers2025() { return totalFoundationOffers2025; }
        public void setTotalFoundationOffers2025(int totalFoundationOffers2025) { this.totalFoundationOffers2025 = totalFoundationOffers2025; }
        public int getTotalFoundationAccept2025() { return totalFoundationAccept2025; }
        public void setTotalFoundationAccept2025(int totalFoundationAccept2025) { this.totalFoundationAccept2025 = totalFoundationAccept2025; }
        public int getTotalFoundationApps2026() { return totalFoundationApps2026; }
        public void setTotalFoundationApps2026(int totalFoundationApps2026) { this.totalFoundationApps2026 = totalFoundationApps2026; }
        public int getTotalFoundationOffers2026() { return totalFoundationOffers2026; }
        public void setTotalFoundationOffers2026(int totalFoundationOffers2026) { this.totalFoundationOffers2026 = totalFoundationOffers2026; }
        public int getTotalFoundationAccept2026() { return totalFoundationAccept2026; }
        public void setTotalFoundationAccept2026(int totalFoundationAccept2026) { this.totalFoundationAccept2026 = totalFoundationAccept2026; }

        public int getTotalUGPGApps2025() { return totalUGPGApps2025; }
        public void setTotalUGPGApps2025(int totalUGPGApps2025) { this.totalUGPGApps2025 = totalUGPGApps2025; }
        public int getTotalUGPGOffers2025() { return totalUGPGOffers2025; }
        public void setTotalUGPGOffers2025(int totalUGPGOffers2025) { this.totalUGPGOffers2025 = totalUGPGOffers2025; }
        public int getTotalUGPGAccept2025() { return totalUGPGAccept2025; }
        public void setTotalUGPGAccept2025(int totalUGPGAccept2025) { this.totalUGPGAccept2025 = totalUGPGAccept2025; }
        public int getTotalUGPGApps2026() { return totalUGPGApps2026; }
        public void setTotalUGPGApps2026(int totalUGPGApps2026) { this.totalUGPGApps2026 = totalUGPGApps2026; }
        public int getTotalUGPGOffers2026() { return totalUGPGOffers2026; }
        public void setTotalUGPGOffers2026(int totalUGPGOffers2026) { this.totalUGPGOffers2026 = totalUGPGOffers2026; }
        public int getTotalUGPGAccept2026() { return totalUGPGAccept2026; }
        public void setTotalUGPGAccept2026(int totalUGPGAccept2026) { this.totalUGPGAccept2026 = totalUGPGAccept2026; }

        public int getTotalRegions() { return regionData.size(); }
    }

    public AnalysisResult analyseFiles(File file2025, File file2026) throws IOException {
        AnalysisResult result = new AnalysisResult();

        Map<String, RegionData> data2025 = processFile(file2025, "2025");
        Map<String, RegionData> data2026 = processFile(file2026, "2026");

        Set<String> allRegions = new HashSet<>();
        allRegions.addAll(data2025.keySet());
        allRegions.addAll(data2026.keySet());

        // Initialize totals
        result.setTotalFoundationApps2025(0);
        result.setTotalFoundationOffers2025(0);
        result.setTotalFoundationAccept2025(0);
        result.setTotalFoundationApps2026(0);
        result.setTotalFoundationOffers2026(0);
        result.setTotalFoundationAccept2026(0);
        result.setTotalUGPGApps2025(0);
        result.setTotalUGPGOffers2025(0);
        result.setTotalUGPGAccept2025(0);
        result.setTotalUGPGApps2026(0);
        result.setTotalUGPGOffers2026(0);
        result.setTotalUGPGAccept2026(0);

        for (String region : allRegions) {
            RegionData merged = new RegionData();
            merged.setRegionName(region);

            RegionData d2025 = data2025.getOrDefault(region, new RegionData());
            RegionData d2026 = data2026.getOrDefault(region, new RegionData());

            merged.setFoundationApps2025(d2025.getFoundationApps2025());
            merged.setFoundationOffers2025(d2025.getFoundationOffers2025());
            merged.setFoundationAccept2025(d2025.getFoundationAccept2025());
            merged.setFoundationApps2026(d2026.getFoundationApps2026());
            merged.setFoundationOffers2026(d2026.getFoundationOffers2026());
            merged.setFoundationAccept2026(d2026.getFoundationAccept2026());

            merged.setUgpgApps2025(d2025.getUgpgApps2025());
            merged.setUgpgOffers2025(d2025.getUgpgOffers2025());
            merged.setUgpgAccept2025(d2025.getUgpgAccept2025());
            merged.setUgpgApps2026(d2026.getUgpgApps2026());
            merged.setUgpgOffers2026(d2026.getUgpgOffers2026());
            merged.setUgpgAccept2026(d2026.getUgpgAccept2026());

            result.getRegionData().put(region, merged);

            result.setTotalFoundationApps2025(result.getTotalFoundationApps2025() + d2025.getFoundationApps2025());
            result.setTotalFoundationOffers2025(result.getTotalFoundationOffers2025() + d2025.getFoundationOffers2025());
            result.setTotalFoundationAccept2025(result.getTotalFoundationAccept2025() + d2025.getFoundationAccept2025());
            result.setTotalFoundationApps2026(result.getTotalFoundationApps2026() + d2026.getFoundationApps2026());
            result.setTotalFoundationOffers2026(result.getTotalFoundationOffers2026() + d2026.getFoundationOffers2026());
            result.setTotalFoundationAccept2026(result.getTotalFoundationAccept2026() + d2026.getFoundationAccept2026());
            result.setTotalUGPGApps2025(result.getTotalUGPGApps2025() + d2025.getUgpgApps2025());
            result.setTotalUGPGOffers2025(result.getTotalUGPGOffers2025() + d2025.getUgpgOffers2025());
            result.setTotalUGPGAccept2025(result.getTotalUGPGAccept2025() + d2025.getUgpgAccept2025());
            result.setTotalUGPGApps2026(result.getTotalUGPGApps2026() + d2026.getUgpgApps2026());
            result.setTotalUGPGOffers2026(result.getTotalUGPGOffers2026() + d2026.getUgpgOffers2026());
            result.setTotalUGPGAccept2026(result.getTotalUGPGAccept2026() + d2026.getUgpgAccept2026());
        }

        return result;
    }

    private Map<String, RegionData> processFile(File file, String year) throws IOException {
        Map<String, RegionData> regionDataMap = new LinkedHashMap<>();

        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);

            if (headerRow == null) {
                throw new IOException("File is empty: " + file.getName());
            }

            // Find column indices - ONLY look for Region and Decision
            Map<String, Integer> columnIndexMap = new HashMap<>();
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                Cell cell = headerRow.getCell(i);
                if (cell != null) {
                    String value = getCellValueAsString(cell).trim();
                    columnIndexMap.put(value, i);
                }
            }

            // Only require Region and Decision columns
            if (!columnIndexMap.containsKey("Region")) {
                throw new IOException("Required column 'Region' not found in " + file.getName());
            }
            if (!columnIndexMap.containsKey("Decision")) {
                throw new IOException("Required column 'Decision' not found in " + file.getName());
            }

            int regionCol = columnIndexMap.get("Region");
            int decisionCol = columnIndexMap.get("Decision");

            // Process data rows - count Decision column values
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;

                String region = getCellValueAsString(row.getCell(regionCol)).trim();
                String decision = getCellValueAsString(row.getCell(decisionCol)).trim();

                if (region.isEmpty()) continue;

                RegionData data = regionDataMap.computeIfAbsent(region, k -> {
                    RegionData rd = new RegionData();
                    rd.setRegionName(k);
                    return rd;
                });

                // Count: All rows = Applications
                // CO or UO = Offers
                // CA or UA = Acceptances
                boolean isOffer = isOffer(decision);
                boolean isAccept = isAccept(decision);

                // Since we don't have Programme column, we need to determine if it's Foundation or UGPG
                // We'll look for keywords in the decision or use a default split
                // For now, let's put everything in UGPG by default, or try to detect from the data
                // Actually, we need to detect from the file structure. Let's check if there's a Programme column

                // Check if Programme column exists
                if (columnIndexMap.containsKey("Programme")) {
                    int programmeCol = columnIndexMap.get("Programme");
                    String programme = getCellValueAsString(row.getCell(programmeCol)).trim();
                    boolean isFoundation = isFoundationProgramme(programme);

                    if (isFoundation) {
                        if ("2025".equals(year)) {
                            data.setFoundationApps2025(data.getFoundationApps2025() + 1);
                            if (isOffer) data.setFoundationOffers2025(data.getFoundationOffers2025() + 1);
                            if (isAccept) data.setFoundationAccept2025(data.getFoundationAccept2025() + 1);
                        } else {
                            data.setFoundationApps2026(data.getFoundationApps2026() + 1);
                            if (isOffer) data.setFoundationOffers2026(data.getFoundationOffers2026() + 1);
                            if (isAccept) data.setFoundationAccept2026(data.getFoundationAccept2026() + 1);
                        }
                    } else {
                        if ("2025".equals(year)) {
                            data.setUgpgApps2025(data.getUgpgApps2025() + 1);
                            if (isOffer) data.setUgpgOffers2025(data.getUgpgOffers2025() + 1);
                            if (isAccept) data.setUgpgAccept2025(data.getUgpgAccept2025() + 1);
                        } else {
                            data.setUgpgApps2026(data.getUgpgApps2026() + 1);
                            if (isOffer) data.setUgpgOffers2026(data.getUgpgOffers2026() + 1);
                            if (isAccept) data.setUgpgAccept2026(data.getUgpgAccept2026() + 1);
                        }
                    }
                } else {
                    // No Programme column - put everything in UGPG
                    if ("2025".equals(year)) {
                        data.setUgpgApps2025(data.getUgpgApps2025() + 1);
                        if (isOffer) data.setUgpgOffers2025(data.getUgpgOffers2025() + 1);
                        if (isAccept) data.setUgpgAccept2025(data.getUgpgAccept2025() + 1);
                    } else {
                        data.setUgpgApps2026(data.getUgpgApps2026() + 1);
                        if (isOffer) data.setUgpgOffers2026(data.getUgpgOffers2026() + 1);
                        if (isAccept) data.setUgpgAccept2026(data.getUgpgAccept2026() + 1);
                    }
                }
            }
        }

        return regionDataMap;
    }

    private boolean isFoundationProgramme(String programme) {
        if (programme == null) return false;
        String lower = programme.toLowerCase();
        return lower.contains("foundation") || lower.contains("fnd") ||
                lower.contains("pre-u") || lower.contains("found");
    }

    private boolean isOffer(String decision) {
        if (decision == null) return false;
        String upper = decision.toUpperCase().trim();
        return upper.equals("CO") || upper.equals("UO") ||
                upper.equals("CONDITIONAL OFFER") || upper.equals("UNCONDITIONAL OFFER") ||
                upper.contains("OFFER");
    }

    private boolean isAccept(String decision) {
        if (decision == null) return false;
        String upper = decision.toUpperCase().trim();
        return upper.equals("CA") || upper.equals("UA") ||
                upper.equals("CONFIRMED ACCEPTANCE") || upper.equals("UNCONFIRMED ACCEPTANCE") ||
                upper.contains("ACCEPT");
    }

    public void exportToExcel(AnalysisResult result, File outputFile) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Region Analysis");

            CellStyle headerStyle = createHeaderStyle(workbook, IndexedColors.DARK_BLUE.getIndex());
            CellStyle subHeaderStyle = createHeaderStyle(workbook, IndexedColors.LIGHT_BLUE.getIndex());
            CellStyle foundationStyle = createHeaderStyle(workbook, IndexedColors.LIGHT_YELLOW.getIndex());
            CellStyle ugpgStyle = createHeaderStyle(workbook, IndexedColors.LIGHT_GREEN.getIndex());
            CellStyle totalStyle = createTotalStyle(workbook);

            int rowNum = 0;
            int colNum = 0;

            // Main header row
            Row headerRow = sheet.createRow(rowNum++);
            Cell regionHeader = headerRow.createCell(colNum++);
            regionHeader.setCellValue("Region");
            regionHeader.setCellStyle(headerStyle);

            Cell foundationHeader = headerRow.createCell(colNum);
            foundationHeader.setCellValue("Foundation Programmes");
            foundationHeader.setCellStyle(foundationStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, colNum, colNum + 8));
            colNum += 9;

            Cell ugpgHeader = headerRow.createCell(colNum);
            ugpgHeader.setCellValue("UG & PG Programmes");
            ugpgHeader.setCellStyle(ugpgStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, colNum, colNum + 8));
            colNum += 9;

            colNum = 1;

            // Sub-header row
            Row subHeaderRow = sheet.createRow(rowNum++);
            Cell regionSubHeader = subHeaderRow.createCell(0);
            regionSubHeader.setCellValue("Region");
            regionSubHeader.setCellStyle(subHeaderStyle);

            String[] headers = {"Applications", "Offers", "Acceptances"};
            String[] years = {"2025", "2026"};

            for (String header : headers) {
                int startCol = colNum;
                Cell cell = subHeaderRow.createCell(colNum++);
                cell.setCellValue(header);
                cell.setCellStyle(subHeaderStyle);
                Cell cell2 = subHeaderRow.createCell(colNum++);
                cell2.setCellValue(years[0]);
                cell2.setCellStyle(subHeaderStyle);
                Cell cell3 = subHeaderRow.createCell(colNum++);
                cell3.setCellValue(years[1]);
                cell3.setCellStyle(subHeaderStyle);
                sheet.addMergedRegion(new CellRangeAddress(1, 1, startCol, startCol + 2));
            }

            for (String header : headers) {
                int startCol = colNum;
                Cell cell = subHeaderRow.createCell(colNum++);
                cell.setCellValue(header);
                cell.setCellStyle(subHeaderStyle);
                Cell cell2 = subHeaderRow.createCell(colNum++);
                cell2.setCellValue(years[0]);
                cell2.setCellStyle(subHeaderStyle);
                Cell cell3 = subHeaderRow.createCell(colNum++);
                cell3.setCellValue(years[1]);
                cell3.setCellStyle(subHeaderStyle);
                sheet.addMergedRegion(new CellRangeAddress(1, 1, startCol, startCol + 2));
            }

            // Data rows
            List<String> regions = new ArrayList<>(result.getRegionData().keySet());
            Collections.sort(regions);
            regions.add("Total");

            for (String region : regions) {
                Row dataRow = sheet.createRow(rowNum++);
                int dataCol = 0;

                Cell regionCell = dataRow.createCell(dataCol++);
                regionCell.setCellValue(region);
                if ("Total".equals(region)) {
                    regionCell.setCellStyle(totalStyle);
                }

                RegionData data = result.getRegionData().get(region);
                boolean isTotal = "Total".equals(region);

                int[] foundationValues = {
                        isTotal ? result.getTotalFoundationApps2025() : (data != null ? data.getFoundationApps2025() : 0),
                        isTotal ? result.getTotalFoundationApps2026() : (data != null ? data.getFoundationApps2026() : 0),
                        isTotal ? result.getTotalFoundationOffers2025() : (data != null ? data.getFoundationOffers2025() : 0),
                        isTotal ? result.getTotalFoundationOffers2026() : (data != null ? data.getFoundationOffers2026() : 0),
                        isTotal ? result.getTotalFoundationAccept2025() : (data != null ? data.getFoundationAccept2025() : 0),
                        isTotal ? result.getTotalFoundationAccept2026() : (data != null ? data.getFoundationAccept2026() : 0)
                };

                for (int val : foundationValues) {
                    Cell cell = dataRow.createCell(dataCol++);
                    cell.setCellValue(val);
                    if (isTotal) cell.setCellStyle(totalStyle);
                }

                int[] ugpgValues = {
                        isTotal ? result.getTotalUGPGApps2025() : (data != null ? data.getUgpgApps2025() : 0),
                        isTotal ? result.getTotalUGPGApps2026() : (data != null ? data.getUgpgApps2026() : 0),
                        isTotal ? result.getTotalUGPGOffers2025() : (data != null ? data.getUgpgOffers2025() : 0),
                        isTotal ? result.getTotalUGPGOffers2026() : (data != null ? data.getUgpgOffers2026() : 0),
                        isTotal ? result.getTotalUGPGAccept2025() : (data != null ? data.getUgpgAccept2025() : 0),
                        isTotal ? result.getTotalUGPGAccept2026() : (data != null ? data.getUgpgAccept2026() : 0)
                };

                for (int val : ugpgValues) {
                    Cell cell = dataRow.createCell(dataCol++);
                    cell.setCellValue(val);
                    if (isTotal) cell.setCellStyle(totalStyle);
                }
            }

            for (int i = 0; i < 19; i++) {
                sheet.autoSizeColumn(i);
                if (sheet.getColumnWidth(i) > 6000) {
                    sheet.setColumnWidth(i, 6000);
                }
            }

            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                workbook.write(fos);
            }
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook, short colorIndex) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(colorIndex);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
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

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        DataFormatter formatter = new DataFormatter();
        return formatter.formatCellValue(cell);
    }
}
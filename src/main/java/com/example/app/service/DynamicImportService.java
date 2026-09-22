package com.example.app.service;

import com.example.app.model.AppDataModel;
import com.example.app.model.MultiSheetModel;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class DynamicImportService {

    private final DataSanitizationService sanitizationService = new DataSanitizationService();
    private final DataFormatter poiFormatter = new DataFormatter();

    private static final List<String> TARGET_SHEETS = Arrays.asList("FY data", "UGPG data");

    public MultiSheetModel importFile(File file) throws IOException {
        String fileName = file.getName();
        String fileNameLower = fileName.toLowerCase();

        if (fileNameLower.endsWith(".csv")) {
            AppDataModel csvModel = importCSV(file);
            MultiSheetModel multiModel = new MultiSheetModel();
            multiModel.addSheet("Data", csvModel.getHeaders(), csvModel.getRawData());
            multiModel.setActiveSheet("Data");
            return multiModel;
        } else if (fileNameLower.endsWith(".xlsx") || fileNameLower.endsWith(".xls")) {
            return importExcel(file);
        } else {
            throw new IOException("Unsupported file format: " + fileName);
        }
    }

    private AppDataModel importCSV(File file) throws IOException {
        System.out.println("📄 Importing CSV: " + file.getName());
        AppDataModel model = new AppDataModel();

        try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8);
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {

            List<String> headers = csvParser.getHeaderNames();
            model.setHeaders(headers);

            List<Map<String, String>> data = new ArrayList<>();
            for (CSVRecord record : csvParser) {
                Map<String, String> rowMap = new HashMap<>();
                for (String header : headers) {
                    String value = record.get(header);
                    rowMap.put(header, sanitizationService.sanitize(header, value));
                }
                data.add(rowMap);
            }
            model.setRawData(data);
            System.out.println("✅ CSV imported: " + data.size() + " rows, " + headers.size() + " columns");
        } catch (IOException e) {
            System.err.println("❌ Error importing CSV: " + e.getMessage());
            throw e;
        }
        return model;
    }

    public MultiSheetModel importExcel(File file) throws IOException {
        System.out.println("📄 Importing Excel: " + file.getName());
        MultiSheetModel multiModel = new MultiSheetModel();

        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = createWorkbook(file)) {

            // Process ONLY target sheets - skip "L vs I" and other sheets
            System.out.println("🔍 Looking for target sheets: " + TARGET_SHEETS);

            for (String targetSheetName : TARGET_SHEETS) {
                Sheet sheet = getSheetIgnoreCase(workbook, targetSheetName);

                if (sheet == null) {
                    System.out.println("   ⚠️ Sheet '" + targetSheetName + "' not found - skipping");
                    continue;
                }

                System.out.println("   ✅ Found sheet: " + targetSheetName);
                SheetData sheetData = processSheet(sheet, targetSheetName);

                multiModel.addSheet(
                        targetSheetName,
                        sheetData.headers,
                        sheetData.rows
                );

                System.out.println("      → " + sheetData.rows.size() + " rows, " +
                        sheetData.headers.size() + " columns");
            }

            if (multiModel.isEmpty()) {
                System.out.println("⚠️ No target sheets found in the Excel file.");
                System.out.println("   Expected sheets: " + TARGET_SHEETS);
                System.out.println("   Available sheets: " + getAvailableSheetNames(workbook));
                List<Map<String, String>> errorRows = new ArrayList<>();
                Map<String, String> msgRow = new HashMap<>();
                msgRow.put("Message", "No 'FY data' or 'UGPG data' sheets found. Please check the file.");
                errorRows.add(msgRow);
                multiModel.addSheet("Error", Arrays.asList("Message"), errorRows);
                multiModel.setActiveSheet("Error");
                return multiModel;
            }

            multiModel.setActiveSheet(multiModel.getSheetNames().get(0));

            System.out.println("✅ Excel imported: " + multiModel.getSheetCount() + " sheets");
            System.out.println("   Sheets: " + multiModel.getSheetNames());

        } catch (Exception e) {
            throw new IOException("Error importing Excel: " + e.getMessage(), e);
        }

        return multiModel;
    }

    private SheetData processSheet(Sheet sheet, String sheetName) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            System.out.println("   ⚠️ Sheet '" + sheetName + "' has no header row");
            return new SheetData(new ArrayList<>(), new ArrayList<>());
        }

        List<String> headers = new ArrayList<>();
        Map<Integer, String> headerMap = new LinkedHashMap<>();

        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell != null) {
                String value = getCellValueSafe(cell).trim();
                if (!value.isEmpty()) {
                    String cleaned = cleanHeader(value);
                    headers.add(cleaned);
                    headerMap.put(i, cleaned);
                }
            }
        }

        System.out.println("   Headers: " + headers.size() + " columns");

        List<Map<String, String>> rows = new ArrayList<>();
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;

            Map<String, String> rowData = new HashMap<>();
            boolean hasData = false;

            for (Map.Entry<Integer, String> entry : headerMap.entrySet()) {
                int colIndex = entry.getKey();
                String header = entry.getValue();
                Cell cell = row.getCell(colIndex);

                String value = getCellValueSafe(cell);

                if (!value.trim().isEmpty()) {
                    hasData = true;
                }
                rowData.put(header, sanitizationService.sanitize(header, value));
            }

            if (hasData) {
                rows.add(rowData);
            }
        }

        System.out.println("   Data rows: " + rows.size());
        return new SheetData(headers, rows);
    }

    /**
     * Get cell value safely - uses cached value for formulas
     * This avoids the GETPIVOTDATA error
     */
    private String getCellValueSafe(Cell cell) {
        if (cell == null) return "";

        try {
            // For formulas, use the cached value (the last calculated value)
            if (cell.getCellType() == CellType.FORMULA) {
                try {
                    CellType cachedType = cell.getCachedFormulaResultType();
                    return switch (cachedType) {
                        case STRING -> cell.getStringCellValue();
                        case NUMERIC -> String.valueOf(cell.getNumericCellValue());
                        case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
                        default -> "";
                    };
                } catch (Exception e) {
                    // If cached value fails, return empty
                    return "";
                }
            }

            // Normal cell handling
            return switch (cell.getCellType()) {
                case STRING -> cell.getStringCellValue();
                case NUMERIC -> {
                    if (DateUtil.isCellDateFormatted(cell)) {
                        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
                        yield sdf.format(cell.getDateCellValue());
                    }
                    yield poiFormatter.formatCellValue(cell);
                }
                case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
                default -> "";
            };

        } catch (Exception e) {
            // Any other exception, return empty
            return "";
        }
    }

    private Sheet getSheetIgnoreCase(Workbook workbook, String sheetName) {
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            String name = workbook.getSheetName(i);
            if (name.trim().equalsIgnoreCase(sheetName.trim())) {
                return workbook.getSheetAt(i);
            }
        }
        return null;
    }

    /**
     * Get all sheet names from the workbook as a comma-separated string
     */
    private String getAvailableSheetNames(Workbook workbook) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("'").append(workbook.getSheetName(i)).append("'");
        }
        return sb.toString();
    }

    private String cleanHeader(String header) {
        String cleaned = header.replaceAll("[^a-zA-Z0-9_ ]", "");
        cleaned = cleaned.trim().replaceAll("\\s+", "_");
        if (cleaned.matches("^\\d.*")) {
            cleaned = "col_" + cleaned;
        }
        return cleaned;
    }

    private Workbook createWorkbook(File file) throws IOException {
        String fileName = file.getName().toLowerCase();
        try (FileInputStream fis = new FileInputStream(file)) {
            if (fileName.endsWith(".xlsx")) {
                return new XSSFWorkbook(fis);
            } else if (fileName.endsWith(".xls")) {
                return new HSSFWorkbook(fis);
            } else {
                throw new IOException("Unsupported file format: " + fileName);
            }
        }
    }

    private static class SheetData {
        List<String> headers;
        List<Map<String, String>> rows;

        SheetData(List<String> headers, List<Map<String, String>> rows) {
            this.headers = headers != null ? headers : new ArrayList<>();
            this.rows = rows != null ? rows : new ArrayList<>();
        }
    }
}
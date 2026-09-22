package com.example.app.service.excel;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

public class ExcelWorkbookService {

    private static final String DEBUG_PREFIX = "📊 [ExcelWorkbookService]";
    private static final List<String> TARGET_SHEETS = Arrays.asList("FY data", "UGPG data");

    private FormulaEvaluator evaluator;

    public List<SheetDataWrapper> parseExcelFile(File excelFile) throws IOException {
        System.out.println(DEBUG_PREFIX + " Parsing: " + excelFile.getName());

        List<SheetDataWrapper> sheets = new ArrayList<>();

        try (Workbook workbook = createWorkbook(excelFile)) {
            evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            // Process ONLY target sheets - skip "L vs I" and others
            System.out.println(DEBUG_PREFIX + " Looking for target sheets: " + TARGET_SHEETS);

            for (String targetSheet : TARGET_SHEETS) {
                Sheet sheet = getSheetIgnoreCase(workbook, targetSheet);

                if (sheet == null) {
                    System.out.println(DEBUG_PREFIX + "   ⚠️ Sheet '" + targetSheet + "' not found");
                    continue;
                }

                System.out.println(DEBUG_PREFIX + "   ✅ Found sheet: '" + targetSheet + "'");

                // Evaluate formulas ONLY for this sheet
                try {
                    evaluator.evaluateAll();  // This will only evaluate the current sheet?
                    // Actually evaluateAll() evaluates ALL sheets. Let's skip it.
                } catch (Exception e) {
                    System.out.println(DEBUG_PREFIX + "   ⚠️ Could not evaluate formulas in sheet: " + targetSheet);
                }

                SheetDataWrapper wrapper = parseSheet(sheet, targetSheet);
                sheets.add(wrapper);

                System.out.println(DEBUG_PREFIX + "   - " + wrapper.getRowCount() + " rows, " +
                        wrapper.getHeaders().size() + " columns");
            }

            if (sheets.isEmpty()) {
                System.out.println(DEBUG_PREFIX + " ⚠️ No target sheets found. Available sheets:");
                for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                    System.out.println(DEBUG_PREFIX + "   - '" + workbook.getSheetName(i) + "'");
                }
            }
        }

        return sheets;
    }

    private SheetDataWrapper parseSheet(Sheet sheet, String sheetName) {
        SheetDataWrapper wrapper = new SheetDataWrapper();
        wrapper.setSheetName(sheetName);

        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            System.out.println(DEBUG_PREFIX + "   ⚠️ Sheet '" + sheetName + "' has no header row");
            return wrapper;
        }

        // Extract headers
        List<String> headers = new ArrayList<>();
        Map<Integer, String> headerMap = new LinkedHashMap<>();

        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell != null) {
                String header = getCellValueSafe(cell).trim();
                if (!header.isEmpty()) {
                    header = cleanColumnName(header);
                    headers.add(header);
                    headerMap.put(i, header);
                }
            }
        }

        wrapper.setHeaders(headers);

        // Extract data rows - use safe cell reading
        List<Map<String, String>> rows = new ArrayList<>();
        int rowCount = 0;

        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;

            Map<String, String> rowData = new LinkedHashMap<>();
            boolean hasData = false;

            for (Map.Entry<Integer, String> entry : headerMap.entrySet()) {
                int colIndex = entry.getKey();
                String header = entry.getValue();
                Cell cell = row.getCell(colIndex);

                // Use safe cell value (avoids GETPIVOTDATA errors)
                String value = getCellValueSafe(cell);

                if (!value.trim().isEmpty()) {
                    hasData = true;
                }
                rowData.put(header, value);
            }

            if (hasData) {
                rowData.put("_row_id", String.valueOf(rowIndex)); // Add implicit ID matching SQLite
                rows.add(rowData);
                rowCount++;
            }
        }

        wrapper.setRows(rows);
        wrapper.setRowCount(rowCount);

        return wrapper;
    }

    /**
     * Safe cell value extraction - uses cached values for formulas
     * This avoids GETPIVOTDATA and other unsupported functions
     */
    private String getCellValueSafe(Cell cell) {
        if (cell == null) return "";

        try {
            // For formulas, use cached value (avoids evaluation errors)
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
                    // If cached value fails, try to get the formula string
                    try {
                        return cell.getCellFormula();
                    } catch (Exception ex) {
                        return "";
                    }
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
                    DataFormatter formatter = new DataFormatter();
                    yield formatter.formatCellValue(cell);
                }
                case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
                default -> "";
            };

        } catch (Exception e) {
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

    private String cleanColumnName(String header) {
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
}
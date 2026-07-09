package com.example.app.service;

import com.example.app.model.AppDataModel;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class DynamicImportService {

    private final DataSanitizationService sanitizationService = new DataSanitizationService();
    private final DataFormatter poiFormatter = new DataFormatter();
    private FormulaEvaluator evaluator;

    public AppDataModel importFile(File file) throws IOException {
        String fileName = file.getName().toLowerCase();
        if (fileName.endsWith(".csv")) {
            return importCSV(file);
        } else if (fileName.endsWith(".xlsx")) {
            return importExcel(file);
        } else {
            throw new IOException("Unsupported file format: " + fileName);
        }
    }

    private AppDataModel importCSV(File file) throws IOException {
        AppDataModel model = new AppDataModel();
        try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8);
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {

            List<String> headers = csvParser.getHeaderNames();
            model.setHeaders(headers);

            List<Map<String, String>> data = new ArrayList<>();
            for (CSVRecord record : csvParser) {
                Map<String, String> rawMap = record.toMap();
                Map<String, String> sanitizedMap = new HashMap<>();
                for (String header : headers) {
                    sanitizedMap.put(header, sanitizationService.sanitize(header, rawMap.get(header)));
                }
                data.add(sanitizedMap);
            }
            model.setRawData(data);
        }
        return model;
    }

    private AppDataModel importExcel(File file) throws IOException {
        AppDataModel model = new AppDataModel();
        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(fis)) {

            evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IOException("Excel file is empty");
            }

            // First, get all raw headers and clean them
            List<String> rawHeaders = new ArrayList<>();
            for (Cell cell : headerRow) {
                String value = getCellValueAsString(cell, "Header").trim();
                if (!value.isEmpty()) {
                    rawHeaders.add(value);
                }
            }

            // Clean the headers - remove duplicates and empty values
            List<String> headers = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (String header : rawHeaders) {
                if (!seen.contains(header)) {
                    headers.add(header);
                    seen.add(header);
                } else {
                    // For duplicates, add a number
                    int count = 1;
                    String unique = header + "_" + count;
                    while (seen.contains(unique)) {
                        count++;
                        unique = header + "_" + count;
                    }
                    headers.add(unique);
                    seen.add(unique);
                }
            }
            model.setHeaders(headers);

            // Read data rows starting from row 1
            List<Map<String, String>> data = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                Map<String, String> rowMap = new HashMap<>();
                for (int j = 0; j < headers.size(); j++) {
                    Cell cell = row.getCell(j);
                    String header = headers.get(j);
                    String value = getCellValueAsString(cell, header);
                    rowMap.put(header, sanitizationService.sanitize(header, value));
                }
                data.add(rowMap);
            }
            model.setRawData(data);
        }
        return model;
    }

    private String getCellValueAsString(Cell cell, String header) {
        if (cell == null) return "";

        // Use FormulaEvaluator to read cached/evaluated values
        CellValue cellValue = null;
        if (cell.getCellType() == CellType.FORMULA && evaluator != null) {
            try {
                cellValue = evaluator.evaluate(cell);
            } catch (Exception e) {
                // Fallback to cached value or formula string if evaluation fails
            }
        }

        if (cellValue != null) {
            return switch (cellValue.getCellType()) {
                case STRING -> cellValue.getStringValue();
                case NUMERIC -> poiFormatter.formatRawCellContents(cellValue.getNumberValue(), -1, poiFormatter.formatCellValue(cell));
                case BOOLEAN -> String.valueOf(cellValue.getBooleanValue());
                default -> poiFormatter.formatCellValue(cell);
            };
        }

        // Auto-Correct Scientific/Excel Notation using DataFormatter
        if (cell.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)) {
            return poiFormatter.formatCellValue(cell);
        }

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
            case FORMULA -> poiFormatter.formatCellValue(cell);
            default -> "";
        };
    }
}
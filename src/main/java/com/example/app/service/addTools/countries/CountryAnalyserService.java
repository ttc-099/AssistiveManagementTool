package com.example.app.service.addTools.countries;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class CountryAnalyserService {

    private static final String DEBUG_PREFIX = "🌍 [CountryAnalyserService]";
    private static final String[] TARGET_SHEETS = {"FY data", "UGPG data"};

    // ===== ANALYSIS RESULT =====
    public static class CountryAnalysisResult {
        private final String sheetName;
        private final List<CountryCount> countryCounts;
        private final int totalRows;
        private final int uniqueCountries;
        private final List<Map<String, String>> allRows;

        public CountryAnalysisResult(String sheetName, List<CountryCount> countryCounts,
                                     int totalRows, List<Map<String, String>> allRows) {
            this.sheetName = sheetName;
            this.countryCounts = countryCounts;
            this.totalRows = totalRows;
            this.uniqueCountries = countryCounts.size();
            this.allRows = allRows;
        }

        public String getSheetName() { return sheetName; }
        public List<CountryCount> getCountryCounts() { return countryCounts; }
        public int getTotalRows() { return totalRows; }
        public int getUniqueCountries() { return uniqueCountries; }
        public List<CountryCount> getTop10() {
            return countryCounts.stream().limit(10).toList();
        }
        public List<Map<String, String>> getAllRows() { return allRows; }

        public List<Map<String, String>> getRowsForCountry(String country) {
            List<Map<String, String>> result = new ArrayList<>();
            for (Map<String, String> row : allRows) {
                String rowCountry = row.getOrDefault("Country", "");
                if (country.equalsIgnoreCase(rowCountry.trim())) {
                    result.add(row);
                }
            }
            return result;
        }
    }

    public static class CountryCount {
        private final String country;
        private final int count;

        public CountryCount(String country, int count) {
            this.country = country;
            this.count = count;
        }

        public String getCountry() { return country; }
        public int getCount() { return count; }
    }

    // ===== COURSE DATA FOR DRILL-DOWN WITH INTAKE =====
    public static class CourseBreakdown {
        private final String country;
        private final String sheetName;
        private final int totalStudents;
        private final List<CourseCount> courseCounts;
        private final List<String> intakes;

        public CourseBreakdown(String country, String sheetName, int totalStudents,
                               List<CourseCount> courseCounts, List<String> intakes) {
            this.country = country;
            this.sheetName = sheetName;
            this.totalStudents = totalStudents;
            this.courseCounts = courseCounts;
            this.intakes = intakes != null ? intakes : Collections.emptyList();
        }

        public String getCountry() { return country; }
        public String getSheetName() { return sheetName; }
        public int getTotalStudents() { return totalStudents; }
        public List<CourseCount> getCourseCounts() { return courseCounts; }
        public List<String> getIntakes() { return intakes; }
        public List<CourseCount> getTop5() {
            return courseCounts.stream().limit(5).toList();
        }
        public String getIntakeDisplay() {
            if (intakes == null || intakes.isEmpty()) return "N/A";
            return String.join(", ", intakes);
        }
    }

    public static class CourseCount {
        private final String courseCode;
        private final String courseName;
        private final String intake;  // The intake for this course
        private final int count;      // Total students for this course

        public CourseCount(String courseCode, String courseName, String intake, int count) {
            this.courseCode = courseCode;
            this.courseName = courseName;
            this.intake = intake != null ? intake : "N/A";
            this.count = count;
        }

        public String getCourseCode() { return courseCode; }
        public String getCourseName() { return courseName; }
        public String getIntake() { return intake; }
        public int getCount() { return count; }

        public String getDisplayName() {
            if (courseName != null && !courseName.isEmpty()) {
                return courseCode + " - " + courseName;
            }
            return courseCode;
        }
    }

    // ===== MAIN ANALYSIS METHOD =====
    public Map<String, CountryAnalysisResult> analyseFile(File file) throws IOException {
        System.out.println(DEBUG_PREFIX + " ========================================");
        System.out.println(DEBUG_PREFIX + " analyseFile: Starting for: " + file.getName());

        Map<String, CountryAnalysisResult> results = new LinkedHashMap<>();

        try (Workbook workbook = createWorkbook(file)) {
            for (String targetSheetName : TARGET_SHEETS) {
                Sheet sheet = getSheetIgnoreCase(workbook, targetSheetName);
                if (sheet == null) {
                    System.out.println(DEBUG_PREFIX + " ⚠️ Sheet '" + targetSheetName + "' not found");
                    continue;
                }
                CountryAnalysisResult result = processSheet(sheet, targetSheetName);
                results.put(targetSheetName, result);
            }
        } catch (Exception e) {
            throw new IOException("Error analysing file: " + e.getMessage(), e);
        }

        return results;
    }

    // ===== PROCESS SHEET WITH EXPLICIT COLUMN NAMES AND INTAKE =====
    private CountryAnalysisResult processSheet(Sheet sheet, String sheetName) {
        System.out.println(DEBUG_PREFIX + " processSheet: " + sheetName);

        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            return new CountryAnalysisResult(sheetName, Collections.emptyList(), 0, Collections.emptyList());
        }

        // Find columns with explicit names
        int countryCol = -1;
        int courseCodeCol = -1;
        int courseNameCol = -1;
        int intakeCol = -1;
        Map<Integer, String> headerMap = new LinkedHashMap<>();

        System.out.println(DEBUG_PREFIX + " processSheet: Scanning headers for sheet: " + sheetName);

        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell != null) {
                String header = getCellValueAsString(cell).trim();
                headerMap.put(i, header);
                System.out.println(DEBUG_PREFIX + "   Column " + i + ": '" + header + "'");

                // Country column
                if (countryCol == -1 && header.equalsIgnoreCase("Country")) {
                    countryCol = i;
                    System.out.println(DEBUG_PREFIX + "   ✓ Found 'Country' column at index " + i);
                }

                // Course Code column - always "Course code"
                if (courseCodeCol == -1 && header.equals("Course code")) {
                    courseCodeCol = i;
                    System.out.println(DEBUG_PREFIX + "   ✓ Found 'Course code' column at index " + i);
                }

                // Course Name column - depends on sheet type
                if (courseNameCol == -1) {
                    if (sheetName.equals("FY data") && header.equals("Foundation")) {
                        courseNameCol = i;
                        System.out.println(DEBUG_PREFIX + "   ✓ Found 'Foundation' column at index " + i + " (FY data)");
                    } else if (sheetName.equals("UGPG data") && header.equals("Course")) {
                        courseNameCol = i;
                        System.out.println(DEBUG_PREFIX + "   ✓ Found 'Course' column at index " + i + " (UGPG data)");
                    }
                }

                // Intake column - only for FY data
                if (sheetName.equals("FY data") && intakeCol == -1 && header.equals("Intake")) {
                    intakeCol = i;
                    System.out.println(DEBUG_PREFIX + "   ✓ Found 'Intake' column at index " + i + " (FY data)");
                }
            }
        }

        System.out.println(DEBUG_PREFIX + " processSheet: Column detection results for " + sheetName + ":");
        System.out.println(DEBUG_PREFIX + "   Country column: " + (countryCol != -1 ? "index " + countryCol : "NOT FOUND"));
        System.out.println(DEBUG_PREFIX + "   Course code column: " + (courseCodeCol != -1 ? "index " + courseCodeCol : "NOT FOUND"));
        System.out.println(DEBUG_PREFIX + "   Course name column: " + (courseNameCol != -1 ? "index " + courseNameCol : "NOT FOUND"));
        System.out.println(DEBUG_PREFIX + "   Intake column: " + (intakeCol != -1 ? "index " + intakeCol : "NOT FOUND"));

        if (countryCol == -1) {
            System.out.println(DEBUG_PREFIX + " ⚠️ No 'Country' column found in " + sheetName);
            return new CountryAnalysisResult(sheetName, Collections.emptyList(), 0, Collections.emptyList());
        }

        // If we found course code but not course name, use course code as name too
        if (courseCodeCol != -1 && courseNameCol == -1) {
            courseNameCol = courseCodeCol;
            System.out.println(DEBUG_PREFIX + "   Using course code column as course name fallback");
        }

        // Process all rows
        Map<String, Integer> countryCounts = new HashMap<>();
        List<Map<String, String>> allRows = new ArrayList<>();
        int totalRows = 0;

        System.out.println(DEBUG_PREFIX + " processSheet: Processing data rows...");

        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;

            Cell countryCell = row.getCell(countryCol);
            String country = getCellValueAsString(countryCell).trim();

            if (!country.isEmpty()) {
                country = cleanCountryName(country);
                countryCounts.put(country, countryCounts.getOrDefault(country, 0) + 1);
                totalRows++;

                Map<String, String> rowData = new LinkedHashMap<>();
                for (Map.Entry<Integer, String> entry : headerMap.entrySet()) {
                    int col = entry.getKey();
                    String header = entry.getValue();
                    Cell cell = row.getCell(col);
                    String value = (cell != null) ? getCellValueAsString(cell) : "";
                    rowData.put(header, value);
                }
                rowData.put("Country", country);

                // Debug: Show first 3 rows with course and intake data
                if (rowIndex <= 3) {
                    System.out.println(DEBUG_PREFIX + "   Row " + rowIndex + ": Country='" + country + "'");
                    if (courseCodeCol != -1) {
                        String code = rowData.getOrDefault("Course code", "");
                        System.out.println(DEBUG_PREFIX + "      Course code: '" + code + "'");
                    }
                    if (courseNameCol != -1 && courseNameCol != courseCodeCol) {
                        String name = rowData.getOrDefault(
                                sheetName.equals("FY data") ? "Foundation" : "Course",
                                ""
                        );
                        System.out.println(DEBUG_PREFIX + "      Course name: '" + name + "'");
                    }
                    if (intakeCol != -1) {
                        String intake = rowData.getOrDefault("Intake", "");
                        System.out.println(DEBUG_PREFIX + "      Intake: '" + intake + "'");
                    }
                }

                allRows.add(rowData);
            }
        }

        // Sort by count descending
        List<CountryCount> sorted = countryCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(e -> new CountryCount(e.getKey(), e.getValue()))
                .toList();

        System.out.println(DEBUG_PREFIX + " processSheet: " + sheetName + " - " +
                sorted.size() + " unique countries, " + totalRows + " total rows");

        return new CountryAnalysisResult(sheetName, sorted, totalRows, allRows);
    }

    // ===== GET COURSE BREAKDOWN FOR A COUNTRY =====
    public CourseBreakdown getCourseBreakdown(CountryAnalysisResult result, String country) {
        System.out.println(DEBUG_PREFIX + " getCourseBreakdown: Country='" + country + "', Sheet='" + result.getSheetName() + "'");

        List<Map<String, String>> rows = result.getRowsForCountry(country);

        System.out.println(DEBUG_PREFIX + " getCourseBreakdown: Found " + rows.size() + " rows for country: " + country);

        if (rows.isEmpty()) {
            return new CourseBreakdown(country, result.getSheetName(), 0, Collections.emptyList(), Collections.emptyList());
        }

        // Determine which columns to use based on sheet name
        String sheetName = result.getSheetName();
        String courseCodeColumn = "Course code";
        String courseNameColumn;

        if (sheetName.equals("FY data")) {
            courseNameColumn = "Foundation";
            System.out.println(DEBUG_PREFIX + " getCourseBreakdown: Using FY data columns: Course code + Foundation");
        } else if (sheetName.equals("UGPG data")) {
            courseNameColumn = "Course";
            System.out.println(DEBUG_PREFIX + " getCourseBreakdown: Using UGPG data columns: Course code + Course");
        } else {
            courseNameColumn = "Course";
            System.out.println(DEBUG_PREFIX + " getCourseBreakdown: Unknown sheet type, using Course code + Course");
        }

        // Verify columns exist
        Map<String, String> sampleRow = rows.get(0);
        System.out.println(DEBUG_PREFIX + " getCourseBreakdown: Available columns: " + sampleRow.keySet());

        if (!sampleRow.containsKey(courseCodeColumn)) {
            System.err.println(DEBUG_PREFIX + " ❌ ERROR: '" + courseCodeColumn + "' column not found!");
            return new CourseBreakdown(country, sheetName, rows.size(), Collections.emptyList(), Collections.emptyList());
        }

        // Collect unique intakes for display
        Set<String> uniqueIntakes = new LinkedHashSet<>();
        for (Map<String, String> row : rows) {
            String intake = row.getOrDefault("Intake", "").trim();
            if (!intake.isEmpty()) {
                uniqueIntakes.add(intake);
            }
        }
        List<String> intakes = new ArrayList<>(uniqueIntakes);
        System.out.println(DEBUG_PREFIX + " getCourseBreakdown: Found " + intakes.size() + " unique intakes: " + intakes);

        // ===== KEY: Map course code → most common intake =====
        // For each course code, find the most common intake (or first occurrence)
        Map<String, String> courseToIntakeMap = new HashMap<>();
        Map<String, Integer> intakeCountPerCourse = new HashMap<>();

        for (Map<String, String> row : rows) {
            String code = row.getOrDefault(courseCodeColumn, "").trim();
            String intake = row.getOrDefault("Intake", "").trim();

            if (!code.isEmpty() && !intake.isEmpty()) {
                String key = code + "|||" + intake;
                intakeCountPerCourse.put(key, intakeCountPerCourse.getOrDefault(key, 0) + 1);
            }
        }

        // For each course code, find the intake with the highest count (most common)
        for (Map.Entry<String, Integer> entry : intakeCountPerCourse.entrySet()) {
            String[] parts = entry.getKey().split("\\|\\|\\|");
            if (parts.length == 2) {
                String code = parts[0];
                String intake = parts[1];
                int count = entry.getValue();

                String existingIntake = courseToIntakeMap.get(code);
                if (existingIntake == null) {
                    courseToIntakeMap.put(code, intake);
                } else {
                    // If this intake has more occurrences, use it
                    String existingKey = code + "|||" + existingIntake;
                    int existingCount = intakeCountPerCourse.getOrDefault(existingKey, 0);
                    if (count > existingCount) {
                        courseToIntakeMap.put(code, intake);
                    }
                }
            }
        }

        System.out.println(DEBUG_PREFIX + " getCourseBreakdown: Course → Intake mapping:");
        for (Map.Entry<String, String> entry : courseToIntakeMap.entrySet()) {
            System.out.println(DEBUG_PREFIX + "   " + entry.getKey() + " → " + entry.getValue());
        }

        // ===== Count courses (each unique course appears ONCE) =====
        Map<String, CourseCount> courseMap = new LinkedHashMap<>();

        for (Map<String, String> row : rows) {
            String code = row.getOrDefault(courseCodeColumn, "").trim();
            String name = row.getOrDefault(courseNameColumn, "").trim();

            // If code is empty, try fallback
            if (code.isEmpty()) {
                code = row.getOrDefault("Course", "").trim();
            }

            // If name is empty, use code as name
            if (name.isEmpty() && !code.isEmpty()) {
                name = code;
            }

            // If both are empty, use "Unknown Course"
            if (code.isEmpty() && name.isEmpty()) {
                code = "Unknown Course";
                name = "Unknown Course";
            }

            // Get the intake for this course code (most common intake)
            String courseIntake = courseToIntakeMap.getOrDefault(code, "N/A");

            // Use code + name as the key (unique course)
            String key = code + "|||" + name;

            CourseCount existing = courseMap.get(key);
            if (existing != null) {
                // Increment count
                courseMap.put(key, new CourseCount(code, name, courseIntake, existing.getCount() + 1));
            } else {
                // First time seeing this course - create with count 1
                courseMap.put(key, new CourseCount(code, name, courseIntake, 1));
            }
        }

        // Sort by count descending
        List<CourseCount> sortedCourses = courseMap.values().stream()
                .sorted(Comparator.comparing(CourseCount::getCount).reversed())
                .toList();

        System.out.println(DEBUG_PREFIX + " getCourseBreakdown: Found " + sortedCourses.size() + " unique courses");

        // Debug: Show each unique course with its intake and count
        for (int i = 0; i < sortedCourses.size(); i++) {
            CourseCount cc = sortedCourses.get(i);
            System.out.println(DEBUG_PREFIX + "   " + (i+1) + ". " + cc.getCourseCode() +
                    " - " + cc.getCourseName() + " | Intake: " + cc.getIntake() + " | Count: " + cc.getCount());
        }

        return new CourseBreakdown(country, sheetName, rows.size(), sortedCourses, intakes);
    }

    // ===== CLEAN COUNTRY NAME =====
    private String cleanCountryName(String country) {
        if (country == null) return "";
        String cleaned = country.trim().replaceAll("\\s+", " ");
        cleaned = cleaned.replaceAll("(?i)^usa$", "United States");
        cleaned = cleaned.replaceAll("(?i)^uk$", "United Kingdom");
        cleaned = cleaned.replaceAll("(?i)^uae$", "United Arab Emirates");
        cleaned = cleaned.replaceAll("(?i)^s\\s*korea$", "South Korea");
        return toTitleCase(cleaned);
    }

    private String toTitleCase(String input) {
        if (input == null || input.isEmpty()) return input;
        String[] words = input.toLowerCase().split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                if (result.length() > 0) result.append(" ");
                result.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1));
            }
        }
        return result.toString();
    }

    // ===== SHEET FINDER =====
    private Sheet getSheetIgnoreCase(Workbook workbook, String sheetName) {
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            String name = workbook.getSheetName(i);
            if (name.trim().equalsIgnoreCase(sheetName.trim())) {
                return workbook.getSheetAt(i);
            }
        }
        return null;
    }

    // ===== CELL VALUE EXTRACTOR =====
    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        try {
            DataFormatter formatter = new DataFormatter();
            return formatter.formatCellValue(cell);
        } catch (Exception e) {
            return "";
        }
    }

    // ===== WORKBOOK CREATOR WITH MAILTO: REPAIR =====
    private Workbook createWorkbook(File file) throws IOException {
        String fileName = file.getName();
        if (fileName.endsWith(".xlsx")) {
            try {
                return new XSSFWorkbook(file);
            } catch (Exception e) {
                if (e.getMessage() != null &&
                        (e.getMessage().contains("mailto") ||
                                e.getMessage().contains("URI") ||
                                e.getMessage().contains("RelationshipId"))) {
                    System.out.println(DEBUG_PREFIX + " 🔧 Repairing corrupted relationships...");
                    File repairedFile = repairCorruptedRelationships(file);
                    return new XSSFWorkbook(new FileInputStream(repairedFile));
                }
                throw new IOException("Cannot read file: " + e.getMessage(), e);
            }
        } else if (fileName.endsWith(".xls")) {
            return new HSSFWorkbook(new FileInputStream(file));
        } else {
            throw new IOException("Unsupported file format: " + fileName);
        }
    }

    // ===== MAILTO: REPAIR =====
    private File repairCorruptedRelationships(File inputFile) throws IOException {
        File outputFile = File.createTempFile("repaired_country_", ".xlsx");
        outputFile.deleteOnExit();

        try (ZipFile zipFile = new ZipFile(inputFile);
             FileOutputStream fos = new FileOutputStream(outputFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String entryName = entry.getName();

                if (entryName.endsWith(".rels")) {
                    byte[] content = readZipEntryContent(zipFile, entry);
                    String contentStr = new String(content, "UTF-8");
                    String rebuilt = rebuildRelationshipXML(contentStr, entryName);

                    ZipEntry newEntry = new ZipEntry(entry.getName());
                    zos.putNextEntry(newEntry);
                    zos.write(rebuilt.getBytes("UTF-8"));
                    zos.closeEntry();
                } else {
                    ZipEntry newEntry = new ZipEntry(entry.getName());
                    zos.putNextEntry(newEntry);
                    try (InputStream is = zipFile.getInputStream(entry)) {
                        is.transferTo(zos);
                    }
                    zos.closeEntry();
                }
            }
        }
        return outputFile;
    }

    private byte[] readZipEntryContent(ZipFile zipFile, ZipEntry entry) throws IOException {
        try (InputStream is = zipFile.getInputStream(entry);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            is.transferTo(baos);
            return baos.toByteArray();
        }
    }

    private String rebuildRelationshipXML(String corruptedXml, String fileName) {
        String fixed = corruptedXml.replaceAll("mailto:[\\s\\u00A0]+", "mailto:");
        fixed = fixed.replaceAll("mailto:\\s+", "mailto:");
        fixed = fixed.replaceAll("mailto\\s+", "mailto:");

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "<Relationship\\s+[^>]*Id\\s*=\\s*\"[^\"]*\"[^>]*Target\\s*=\\s*\"[^\"]*\"[^>]*Type\\s*=\\s*\"[^\"]*\"[^>]*/>"
        );
        java.util.regex.Matcher matcher = pattern.matcher(fixed);

        StringBuilder rebuilt = new StringBuilder();
        rebuilt.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        rebuilt.append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n");

        boolean foundValid = false;
        while (matcher.find()) {
            String rel = matcher.group();
            rel = rel.replaceAll("mailto:[\\s\\u00A0]+", "mailto:");
            rel = rel.replaceAll("mailto:\\s+", "mailto:");
            rebuilt.append("  ").append(rel).append("\n");
            foundValid = true;
        }

        if (!foundValid) {
            java.util.regex.Pattern lenientPattern = java.util.regex.Pattern.compile(
                    "<Relationship\\s+[^>]*Id\\s*=\\s*\"[^\"]*\"[^>]*Target\\s*=\\s*\"[^\"]*\"[^>]*/>"
            );
            matcher = lenientPattern.matcher(fixed);
            while (matcher.find()) {
                String rel = matcher.group();
                rel = rel.replaceAll("mailto:[\\s\\u00A0]+", "mailto:");
                rel = rel.replaceAll("mailto:\\s+", "mailto:");
                if (!rel.contains("Type=")) {
                    rel = rel.replace("/>", " Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\"/>");
                }
                rebuilt.append("  ").append(rel).append("\n");
                foundValid = true;
            }
        }

        if (!foundValid) {
            return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
                    "</Relationships>";
        }

        rebuilt.append("</Relationships>");
        return rebuilt.toString();
    }

    // ===== EXPORT REPORT =====
    public void exportReport(Map<String, CountryAnalysisResult> results, File outputFile) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            for (Map.Entry<String, CountryAnalysisResult> entry : results.entrySet()) {
                String sheetName = entry.getKey();
                CountryAnalysisResult result = entry.getValue();

                Sheet sheet = workbook.createSheet(sheetName + " - Countries");

                Row headerRow = sheet.createRow(0);
                headerRow.createCell(0).setCellValue("Rank");
                headerRow.createCell(1).setCellValue("Country");
                headerRow.createCell(2).setCellValue("Count");
                headerRow.createCell(3).setCellValue("Percentage");

                CellStyle headerStyle = workbook.createCellStyle();
                Font headerFont = workbook.createFont();
                headerFont.setBold(true);
                headerStyle.setFont(headerFont);
                headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
                headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                for (int i = 0; i < 4; i++) {
                    headerRow.getCell(i).setCellStyle(headerStyle);
                }

                int rowIndex = 1;
                int totalRows = result.getTotalRows();
                for (CountryCount cc : result.getCountryCounts()) {
                    Row row = sheet.createRow(rowIndex++);
                    row.createCell(0).setCellValue(rowIndex - 1);
                    row.createCell(1).setCellValue(cc.getCountry());
                    row.createCell(2).setCellValue(cc.getCount());
                    double percentage = totalRows > 0 ? (cc.getCount() * 100.0 / totalRows) : 0;
                    row.createCell(3).setCellValue(String.format("%.1f%%", percentage));
                }

                Row summaryRow = sheet.createRow(rowIndex + 1);
                summaryRow.createCell(0).setCellValue("TOTAL");
                summaryRow.createCell(1).setCellValue(result.getUniqueCountries() + " countries");
                summaryRow.createCell(2).setCellValue(totalRows + " rows");

                for (int i = 0; i < 4; i++) {
                    sheet.autoSizeColumn(i);
                }
            }

            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                workbook.write(fos);
            }
        }
    }
}
package com.example.app.service.addTools.countries;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
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

    // ===== COURSE DATA FOR DRILL-DOWN WITH INTAKE & STATUS =====
    public static class CourseBreakdown {
        private final String country;
        private final String sheetName;
        private final int totalStudents;
        private final int totalOffers;
        private final int totalAccepts;
        private final List<CourseCount> courseCounts;
        private final List<String> intakes;

        public CourseBreakdown(String country, String sheetName, int totalStudents,
                               List<CourseCount> courseCounts, List<String> intakes) {
            this.country = country;
            this.sheetName = sheetName;
            this.totalStudents = totalStudents;
            this.courseCounts = courseCounts;
            this.intakes = intakes != null ? intakes : Collections.emptyList();

            // Calculate totals from course counts
            int offers = 0, accepts = 0;
            for (CourseCount cc : courseCounts) {
                offers += cc.getOffers();
                accepts += cc.getAccepts();
            }
            this.totalOffers = offers;
            this.totalAccepts = accepts;
        }

        public String getCountry() { return country; }
        public String getSheetName() { return sheetName; }
        public int getTotalStudents() { return totalStudents; }
        public int getTotalOffers() { return totalOffers; }
        public int getTotalAccepts() { return totalAccepts; }
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
        private final String intake;
        private final int applications;  // Total applications (students)
        private final int offers;        // Number of offers
        private final int accepts;       // Number of acceptances

        // Full constructor with all fields
        public CourseCount(String courseCode, String courseName, String intake,
                           int applications, int offers, int accepts) {
            this.courseCode = courseCode;
            this.courseName = courseName;
            this.intake = intake != null ? intake : "N/A";
            this.applications = applications;
            this.offers = offers;
            this.accepts = accepts;
        }

        // Legacy constructor for backward compatibility
        public CourseCount(String courseCode, String courseName, String intake, int count) {
            this(courseCode, courseName, intake, count, 0, 0);
        }

        public String getCourseCode() { return courseCode; }
        public String getCourseName() { return courseName; }
        public String getIntake() { return intake; }
        public int getApplications() { return applications; }
        public int getOffers() { return offers; }
        public int getAccepts() { return accepts; }
        public int getCount() { return applications; } // Alias for backward compatibility

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
        int decisionCol = -1;
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

                // Decision column
                if (decisionCol == -1 && header.equalsIgnoreCase("Decision")) {
                    decisionCol = i;
                    System.out.println(DEBUG_PREFIX + "   ✓ Found 'Decision' column at index " + i);
                }
            }
        }

        System.out.println(DEBUG_PREFIX + " processSheet: Column detection results for " + sheetName + ":");
        System.out.println(DEBUG_PREFIX + "   Country column: " + (countryCol != -1 ? "index " + countryCol : "NOT FOUND"));
        System.out.println(DEBUG_PREFIX + "   Course code column: " + (courseCodeCol != -1 ? "index " + courseCodeCol : "NOT FOUND"));
        System.out.println(DEBUG_PREFIX + "   Course name column: " + (courseNameCol != -1 ? "index " + courseNameCol : "NOT FOUND"));
        System.out.println(DEBUG_PREFIX + "   Intake column: " + (intakeCol != -1 ? "index " + intakeCol : "NOT FOUND"));
        System.out.println(DEBUG_PREFIX + "   Decision column: " + (decisionCol != -1 ? "index " + decisionCol : "NOT FOUND"));

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
                    if (decisionCol != -1) {
                        String decision = rowData.getOrDefault("Decision", "");
                        System.out.println(DEBUG_PREFIX + "      Decision: '" + decision + "'");
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

    // ===== GET COURSE BREAKDOWN FOR A COUNTRY WITH STATUS =====
    public CourseBreakdown getCourseBreakdown(CountryAnalysisResult result, String country) {
        System.out.println(DEBUG_PREFIX + " getCourseBreakdownWithStatus: Country='" + country + "', Sheet='" + result.getSheetName() + "'");

        List<Map<String, String>> rows = result.getRowsForCountry(country);
        System.out.println(DEBUG_PREFIX + " Found " + rows.size() + " rows for country: " + country);

        if (rows.isEmpty()) {
            return new CourseBreakdown(country, result.getSheetName(), 0, Collections.emptyList(), Collections.emptyList());
        }

        String sheetName = result.getSheetName();
        String courseCodeColumn = "Course code";
        String courseNameColumn = sheetName.equals("FY data") ? "Foundation" : "Course";
        String decisionColumn = "Decision";

        // Verify columns exist
        Map<String, String> sampleRow = rows.get(0);
        if (!sampleRow.containsKey(courseCodeColumn)) {
            System.err.println(DEBUG_PREFIX + " ❌ ERROR: '" + courseCodeColumn + "' column not found!");
            return new CourseBreakdown(country, sheetName, rows.size(), Collections.emptyList(), Collections.emptyList());
        }

        // Collect unique intakes
        Set<String> uniqueIntakes = new LinkedHashSet<>();
        for (Map<String, String> row : rows) {
            String intake = row.getOrDefault("Intake", "").trim();
            if (!intake.isEmpty()) {
                uniqueIntakes.add(intake);
            }
        }
        List<String> intakes = new ArrayList<>(uniqueIntakes);

        // ===== Build course map with counts =====
        Map<String, CourseCount> courseMap = new LinkedHashMap<>();

        for (Map<String, String> row : rows) {
            String code = row.getOrDefault(courseCodeColumn, "").trim();
            String name = row.getOrDefault(courseNameColumn, "").trim();
            String decision = row.getOrDefault(decisionColumn, "").trim();

            if (code.isEmpty()) {
                code = row.getOrDefault("Course", "").trim();
            }
            if (name.isEmpty() && !code.isEmpty()) {
                name = code;
            }
            if (code.isEmpty() && name.isEmpty()) {
                code = "Unknown Course";
                name = "Unknown Course";
            }

            // Get intake for this course
            String courseIntake = row.getOrDefault("Intake", "N/A").trim();
            if (courseIntake.isEmpty()) courseIntake = "N/A";

            String key = code + "|||" + name;

            CourseCount existing = courseMap.get(key);
            if (existing != null) {
                // Update existing - increment applications
                int newApps = existing.getApplications() + 1;
                int newOffers = existing.getOffers() + (isOffer(decision) ? 1 : 0);
                int newAccepts = existing.getAccepts() + (isAccept(decision) ? 1 : 0);

                courseMap.put(key, new CourseCount(
                        code, name, courseIntake, newApps, newOffers, newAccepts
                ));
            } else {
                // Create new
                int offers = isOffer(decision) ? 1 : 0;
                int accepts = isAccept(decision) ? 1 : 0;
                courseMap.put(key, new CourseCount(
                        code, name, courseIntake, 1, offers, accepts
                ));
            }
        }

        // Sort by applications descending
        List<CourseCount> sortedCourses = courseMap.values().stream()
                .sorted(Comparator.comparing(CourseCount::getApplications).reversed())
                .toList();

        System.out.println(DEBUG_PREFIX + " Found " + sortedCourses.size() + " unique courses with status counts");
        return new CourseBreakdown(country, sheetName, rows.size(), sortedCourses, intakes);
    }

    // ===== DECISION HELPERS =====

    /**
     * Check if a decision counts as an offer
     * Uses the same logic as RegionAnalyserService
     */
    private boolean isOffer(String decision) {
        if (decision == null) return false;
        String d = decision.trim().toUpperCase();

        // All decision codes that count as offers
        if (d.equals("UO") || d.equals("CO") || d.equals("D") ||
                d.equals("DA") || d.equals("RA") || d.equals("UA") ||
                d.equals("CA") || d.equals("WA")) {
            return true;
        }

        // Full words
        if (d.equals("OFFER") || d.equals("OFFERED")) return true;
        if (d.equals("CONDITIONAL OFFER") || d.equals("UNCONDITIONAL OFFER")) return true;

        String lower = d.toLowerCase();
        return lower.contains("offer") ||
                lower.contains("unconditional") ||
                lower.contains("conditional");
    }

    /**
     * Check if a decision counts as an acceptance
     */
    private boolean isAccept(String decision) {
        if (decision == null) return false;
        String d = decision.trim().toUpperCase();
        if (d.equals("UA") || d.equals("CA")) return true;
        if (d.equals("ACCEPT") || d.equals("ACCEPTED")) return true;
        String lower = d.toLowerCase();
        return lower.contains("accept") || lower.contains("firm") || lower.contains("confirmed");
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

    /**
     * Export full analysis with country drill-downs to Excel
     * Creates a workbook with:
     * - Summary sheet: All countries with counts
     * - Individual sheets: Each country's course breakdown (with Applications, Offers, Acceptances)
     */
    public void exportFullReport(Map<String, CountryAnalysisResult> results, File outputFile) throws IOException {
        System.out.println(DEBUG_PREFIX + " ========================================");
        System.out.println(DEBUG_PREFIX + " exportFullReport: Starting full export");
        System.out.println(DEBUG_PREFIX + " exportFullReport: Output file: " + outputFile.getAbsolutePath());

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {

            // ===== SHEET 1: Summary =====
            Sheet summarySheet = workbook.createSheet("Country Summary");
            createSummarySheet(workbook, summarySheet, results);

            // ===== SHEET 2+: Country Drill-Downs =====
            for (Map.Entry<String, CountryAnalysisResult> entry : results.entrySet()) {
                String sheetName = entry.getKey();
                CountryAnalysisResult result = entry.getValue();

                // Create a sheet for this data type (FY data / UGPG data)
                String safeSheetName = sanitizeSheetName(sheetName + " - Countries");
                Sheet countrySheet = workbook.createSheet(safeSheetName);
                createCountryListSheet(workbook, countrySheet, result, sheetName);

                // For each country, create a drill-down sheet
                // Limit to top 20 countries to avoid too many sheets
                List<CountryCount> topCountries = result.getTop10();
                for (CountryCount cc : topCountries) {
                    String countryName = cc.getCountry();
                    // Sanitize sheet name (Excel max 31 chars)
                    String drillSheetName = sanitizeSheetName(countryName + " - " + sheetName);

                    // Truncate if too long
                    if (drillSheetName.length() > 31) {
                        drillSheetName = drillSheetName.substring(0, 28) + "...";
                    }

                    try {
                        Sheet drillSheet = workbook.createSheet(drillSheetName);
                        createDrillDownSheet(workbook, drillSheet, result, countryName, sheetName);
                    } catch (Exception e) {
                        System.err.println(DEBUG_PREFIX + " ⚠️ Could not create sheet for: " + countryName);
                    }
                }
            }

            // Auto-size all sheets
            autoSizeAllSheets(workbook);

            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                workbook.write(fos);
            }

            System.out.println(DEBUG_PREFIX + " ✅ Full export complete: " + outputFile.getAbsolutePath());
            System.out.println(DEBUG_PREFIX + " ========================================");
        }
    }

    /**
     * Create the summary sheet with all countries
     */
    private void createSummarySheet(Workbook workbook, Sheet sheet, Map<String, CountryAnalysisResult> results) {
        int rowNum = 0;

        // Header
        Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {"Data Type", "Country", "Applications", "Offers", "Acceptances", "Percentage"};
        CellStyle headerStyle = createHeaderStyle(workbook);

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Data
        for (Map.Entry<String, CountryAnalysisResult> entry : results.entrySet()) {
            String sheetName = entry.getKey();
            CountryAnalysisResult result = entry.getValue();
            int totalRows = result.getTotalRows();

            for (CountryCount cc : result.getCountryCounts()) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(sheetName);
                row.createCell(1).setCellValue(cc.getCountry());
                row.createCell(2).setCellValue(cc.getCount());

                // Get offer/accept counts for this country
                CourseBreakdown breakdown = getCourseBreakdown(result, cc.getCountry());
                row.createCell(3).setCellValue(breakdown.getTotalOffers());
                row.createCell(4).setCellValue(breakdown.getTotalAccepts());

                double percentage = totalRows > 0 ? (cc.getCount() * 100.0 / totalRows) : 0;
                row.createCell(5).setCellValue(String.format("%.1f%%", percentage));
            }

            // Add blank row between data types
            rowNum++;
        }
    }

    /**
     * Create a sheet listing all countries for a data type
     */
    private void createCountryListSheet(Workbook workbook, Sheet sheet, CountryAnalysisResult result, String sheetName) {
        int rowNum = 0;

        // Title
        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(sheetName + " - Country Distribution");
        CellStyle titleStyle = workbook.createCellStyle();
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 16);
        titleStyle.setFont(titleFont);
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 4));
        rowNum++;

        // Headers
        Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {"Rank", "Country", "Applications", "Offers", "Acceptances", "Percentage"};
        CellStyle headerStyle = createHeaderStyle(workbook);

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Data
        int totalRows = result.getTotalRows();
        int rank = 1;
        for (CountryCount cc : result.getCountryCounts()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(rank++);
            row.createCell(1).setCellValue(cc.getCountry());
            row.createCell(2).setCellValue(cc.getCount());

            CourseBreakdown breakdown = getCourseBreakdown(result, cc.getCountry());
            row.createCell(3).setCellValue(breakdown.getTotalOffers());
            row.createCell(4).setCellValue(breakdown.getTotalAccepts());

            double percentage = totalRows > 0 ? (cc.getCount() * 100.0 / totalRows) : 0;
            row.createCell(5).setCellValue(String.format("%.1f%%", percentage));
        }

        // Total row
        Row totalRow = sheet.createRow(rowNum++);
        CellStyle totalStyle = createTotalStyle(workbook);
        totalRow.createCell(0).setCellValue("TOTAL");
        totalRow.createCell(1).setCellValue(result.getUniqueCountries() + " countries");
        totalRow.createCell(2).setCellValue(totalRows);
        for (int i = 0; i < 6; i++) {
            totalRow.getCell(i).setCellStyle(totalStyle);
        }
    }

    /**
     * Create a drill-down sheet for a specific country
     */
    private void createDrillDownSheet(Workbook workbook, Sheet sheet, CountryAnalysisResult result,
                                      String country, String sheetName) {
        int rowNum = 0;

        // Title
        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Course Breakdown: " + country + " (" + sheetName + ")");
        CellStyle titleStyle = workbook.createCellStyle();
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        titleStyle.setFont(titleFont);
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));
        rowNum++;

        // Get breakdown
        CourseBreakdown breakdown = getCourseBreakdown(result, country);

        // Summary row
        Row summaryRow = sheet.createRow(rowNum++);
        CellStyle summaryStyle = workbook.createCellStyle();
        Font summaryFont = workbook.createFont();
        summaryFont.setBold(true);
        summaryStyle.setFont(summaryFont);
        summaryStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        summaryStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        summaryRow.createCell(0).setCellValue("Total Students: " + breakdown.getTotalStudents());
        summaryRow.createCell(2).setCellValue("Offers: " + breakdown.getTotalOffers());
        summaryRow.createCell(4).setCellValue("Acceptances: " + breakdown.getTotalAccepts());
        for (int i = 0; i < 6; i++) {
            if (summaryRow.getCell(i) == null) summaryRow.createCell(i);
            summaryRow.getCell(i).setCellStyle(summaryStyle);
        }
        rowNum++;

        // Headers
        Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {"Course Code", "Course Name", "Intake", "Applications", "Offers", "Acceptances"};
        CellStyle headerStyle = createHeaderStyle(workbook);

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Data
        for (CourseCount cc : breakdown.getCourseCounts()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(cc.getCourseCode());
            row.createCell(1).setCellValue(cc.getCourseName());
            row.createCell(2).setCellValue(cc.getIntake());
            row.createCell(3).setCellValue(cc.getApplications());
            row.createCell(4).setCellValue(cc.getOffers());
            row.createCell(5).setCellValue(cc.getAccepts());
        }

        // Total row
        if (!breakdown.getCourseCounts().isEmpty()) {
            Row totalRow = sheet.createRow(rowNum++);
            CellStyle totalStyle = createTotalStyle(workbook);
            totalRow.createCell(0).setCellValue("TOTAL");
            totalRow.createCell(3).setCellValue(breakdown.getTotalStudents());
            totalRow.createCell(4).setCellValue(breakdown.getTotalOffers());
            totalRow.createCell(5).setCellValue(breakdown.getTotalAccepts());
            for (int i = 0; i < 6; i++) {
                if (totalRow.getCell(i) == null) totalRow.createCell(i);
                totalRow.getCell(i).setCellStyle(totalStyle);
            }
        }
    }

    /**
     * Create header cell style
     */
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    /**
     * Create total row style
     */
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

    /**
     * Sanitize sheet name for Excel (max 31 chars, no invalid characters)
     */
    private String sanitizeSheetName(String name) {
        if (name == null) return "Sheet";
        // Remove invalid characters
        String sanitized = name.replaceAll("[\\\\/*\\[\\]:?]", "");
        // Truncate to 31 characters
        if (sanitized.length() > 31) {
            sanitized = sanitized.substring(0, 28) + "...";
        }
        return sanitized;
    }

    /**
     * Auto-size all sheets in the workbook
     */
    private void autoSizeAllSheets(Workbook workbook) {
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            if (sheet.getPhysicalNumberOfRows() > 0) {
                Row row = sheet.getRow(0);
                if (row != null) {
                    for (int j = 0; j < row.getLastCellNum(); j++) {
                        sheet.autoSizeColumn(j);
                        // Set minimum width
                        if (sheet.getColumnWidth(j) < 3000) {
                            sheet.setColumnWidth(j, 3000);
                        }
                        // Set maximum width
                        if (sheet.getColumnWidth(j) > 15000) {
                            sheet.setColumnWidth(j, 15000);
                        }
                    }
                }
            }
        }
    }

    /**
     * Export Top 10 countries with Applications, Offers, Acceptances
     * Simple summary format
     */
    public void exportTop10Summary(Map<String, CountryAnalysisResult> results, File outputFile) throws IOException {
        System.out.println(DEBUG_PREFIX + " exportTop10Summary: Starting");

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {

            for (Map.Entry<String, CountryAnalysisResult> entry : results.entrySet()) {
                String sheetName = entry.getKey();
                CountryAnalysisResult result = entry.getValue();

                // Create sheet for this data type
                String safeSheetName = sanitizeSheetName(sheetName + " - Top 10");
                Sheet sheet = workbook.createSheet(safeSheetName);

                int rowNum = 0;

                // Title
                Row titleRow = sheet.createRow(rowNum++);
                Cell titleCell = titleRow.createCell(0);
                titleCell.setCellValue(sheetName + " - Top 10 Countries");
                CellStyle titleStyle = workbook.createCellStyle();
                Font titleFont = workbook.createFont();
                titleFont.setBold(true);
                titleFont.setFontHeightInPoints((short) 14);
                titleStyle.setFont(titleFont);
                titleCell.setCellStyle(titleStyle);
                sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 4));
                rowNum++;

                // Headers
                Row headerRow = sheet.createRow(rowNum++);
                CellStyle headerStyle = createHeaderStyle(workbook);

                String[] headers = {"Rank", "Country", "Applications", "Offers", "Acceptances"};
                for (int i = 0; i < headers.length; i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(headers[i]);
                    cell.setCellStyle(headerStyle);
                }

                // Data - Top 10 only
                int totalRows = result.getTotalRows();
                int rank = 1;
                List<CountryCount> top10 = result.getCountryCounts().stream().limit(10).toList();

                for (CountryCount cc : top10) {
                    Row row = sheet.createRow(rowNum++);
                    row.createCell(0).setCellValue(rank++);
                    row.createCell(1).setCellValue(cc.getCountry());
                    row.createCell(2).setCellValue(cc.getCount());

                    // Get offer/accept counts for this country
                    CourseBreakdown breakdown = getCourseBreakdown(result, cc.getCountry());
                    row.createCell(3).setCellValue(breakdown.getTotalOffers());
                    row.createCell(4).setCellValue(breakdown.getTotalAccepts());
                }

                // Total row
                Row totalRow = sheet.createRow(rowNum++);
                CellStyle totalStyle = createTotalStyle(workbook);
                totalRow.createCell(0).setCellValue("TOTAL");
                totalRow.createCell(2).setCellValue(totalRows);

                // Calculate total offers and accepts across ALL countries
                int totalOffers = 0, totalAccepts = 0;
                for (CountryCount cc : result.getCountryCounts()) {
                    CourseBreakdown bd = getCourseBreakdown(result, cc.getCountry());
                    totalOffers += bd.getTotalOffers();
                    totalAccepts += bd.getTotalAccepts();
                }
                totalRow.createCell(3).setCellValue(totalOffers);
                totalRow.createCell(4).setCellValue(totalAccepts);

                for (int i = 0; i < 5; i++) {
                    if (totalRow.getCell(i) == null) totalRow.createCell(i);
                    totalRow.getCell(i).setCellStyle(totalStyle);
                }

                // Auto-size columns
                for (int i = 0; i < 5; i++) {
                    sheet.autoSizeColumn(i);
                    if (sheet.getColumnWidth(i) < 3000) sheet.setColumnWidth(i, 3000);
                    if (sheet.getColumnWidth(i) > 12000) sheet.setColumnWidth(i, 12000);
                }
            }

            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                workbook.write(fos);
            }

            System.out.println(DEBUG_PREFIX + " ✅ Top 10 summary exported to: " + outputFile.getAbsolutePath());
        }
    }
}
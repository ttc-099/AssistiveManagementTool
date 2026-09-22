package com.example.app.service.preset;

import com.example.app.db.DatabaseManager;
import com.example.app.service.preset.PresetDataModels.CountResult;
import com.example.app.service.preset.PresetDataModels.LocalVsInternationalRow;
import com.example.app.service.preset.PresetDataModels.ProgrammeInterestRow;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for preset data aggregation and table building
 */
public class PresetService {

    private final DatabaseManager dbManager;

    // Table names in SQLite (sanitized with underscores)
    private static final String FY_TABLE = "FY_data";
    private static final String UGPG_TABLE = "UGPG_data";

    // Decision codes that count as offers
    private static final Set<String> OFFER_CODES = Set.of("UO", "CO", "OFFER");

    // Debug flag
    private static final boolean DEBUG = true;

    public PresetService(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    private void logDebug(String message) {
        if (DEBUG) {
            System.out.println("🔍 [PresetService] " + message);
        }
    }

    private void logError(String message, Exception e) {
        System.err.println("❌ [PresetService] " + message);
        if (DEBUG && e != null) {
            e.printStackTrace();
        }
    }

    // ================================================================
    // 1. DISCOVERY METHODS - Get dynamic values from data
    // ================================================================

    public List<String> getFYProgrammes() {
        logDebug("getFYProgrammes() called");
        List<String> result = getDistinctValues(FY_TABLE, "Foundation");
        logDebug("getFYProgrammes() found " + result.size() + " programmes: " + result);
        return result;
    }

    public List<String> getUGPGProgrammes() {
        logDebug("getUGPGProgrammes() called");
        List<String> result = getDistinctValues(UGPG_TABLE, "Course");
        logDebug("getUGPGProgrammes() found " + result.size() + " programmes: " + result);
        return result;
    }

    public List<String> getFYIntakes() {
        logDebug("getFYIntakes() called");
        List<String> result = getDistinctValues(FY_TABLE, "Intake");
        logDebug("getFYIntakes() found " + result.size() + " intakes: " + result);
        return result;
    }

    public List<String> getDecisionCodes() {
        logDebug("getDecisionCodes() called");
        Set<String> decisions = new LinkedHashSet<>();
        decisions.addAll(getDistinctValues(FY_TABLE, "Decision"));
        decisions.addAll(getDistinctValues(UGPG_TABLE, "Decision"));
        // Filter out empty/null and sort with known order
        List<String> result = decisions.stream()
                .filter(d -> d != null && !d.isEmpty())
                .sorted((a, b) -> {
                    String order = "UO,CO,D,DA,RA,UA,CA,WA,OFFER";
                    int idxA = order.indexOf(a);
                    int idxB = order.indexOf(b);
                    if (idxA == -1 && idxB == -1) return a.compareTo(b);
                    if (idxA == -1) return 1;
                    if (idxB == -1) return -1;
                    return Integer.compare(idxA, idxB);
                })
                .collect(Collectors.toList());
        logDebug("getDecisionCodes() found " + result.size() + " decisions: " + result);
        return result;
    }

    private List<String> getDistinctValues(String tableName, String columnName) {
        List<String> values = new ArrayList<>();
        String sql = "SELECT DISTINCT \"" + columnName + "\" FROM \"" + tableName + "\"" +
                " WHERE \"" + columnName + "\" IS NOT NULL AND \"" + columnName + "\" != ''" +
                " ORDER BY \"" + columnName + "\"";

        logDebug("getDistinctValues() SQL: " + sql);

        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String value = rs.getString(1);
                if (value != null && !value.trim().isEmpty()) {
                    values.add(value.trim());
                }
            }
            logDebug("getDistinctValues() table=" + tableName + ", column=" + columnName + ", found " + values.size() + " values");
        } catch (SQLException e) {
            logError("Error getting distinct values from " + tableName + "." + columnName + ": " + e.getMessage(), e);
        }
        return values;
    }

    // ================================================================
    // 2. LOCAL VS INTERNATIONAL DATA
    // ================================================================

    /**
     * Get Local vs International data for FY
     * Returns ObservableList of rows with Intake headers and Programme data
     */
    public ObservableList<LocalVsInternationalRow> getLocalVsInternationalFY() {
        logDebug("=== getLocalVsInternationalFY() START ===");
        List<String> intakes = getFYIntakes();
        List<String> programmes = getFYProgrammes();

        logDebug("Intakes: " + intakes);
        logDebug("Programmes: " + programmes);

        List<CountResult> rawData = getLIVCounts(FY_TABLE, "Foundation", "Intake");
        logDebug("Raw data count: " + rawData.size());

        ObservableList<LocalVsInternationalRow> result = buildLIVRows(intakes, programmes, rawData);
        logDebug("Result rows: " + result.size());
        logDebug("=== getLocalVsInternationalFY() END ===");
        return result;
    }

    /**
     * Get Local vs International data for UGPG
     * Returns ObservableList of rows without Intake headers
     */
    public ObservableList<LocalVsInternationalRow> getLocalVsInternationalUGPG() {
        logDebug("=== getLocalVsInternationalUGPG() START ===");
        List<String> programmes = getUGPGProgrammes();
        logDebug("Programmes: " + programmes);

        List<CountResult> rawData = getLIVCounts(UGPG_TABLE, "Course", null);
        logDebug("Raw data count: " + rawData.size());

        ObservableList<LocalVsInternationalRow> result = buildLIVRowsNoIntake(programmes, rawData);
        logDebug("Result rows: " + result.size());
        logDebug("=== getLocalVsInternationalUGPG() END ===");
        return result;
    }

    private List<CountResult> getLIVCounts(String tableName, String programmeColumn, String intakeColumn) {
        List<CountResult> results = new ArrayList<>();

        try (Connection conn = dbManager.getConnection()) {
            String groupByClause = intakeColumn != null ?
                    "GROUP BY \"" + intakeColumn + "\", \"" + programmeColumn + "\", \"Nationality_2\"" :
                    "GROUP BY \"" + programmeColumn + "\", \"Nationality_2\"";

            String intakeSelect = intakeColumn != null ?
                    "\"" + intakeColumn + "\" AS intake, " :
                    "";

            String sql = "SELECT " + intakeSelect +
                    "\"" + programmeColumn + "\" AS programme, " +
                    "\"Nationality_2\" AS nationality, " +
                    "COUNT(*) AS total, " +
                    "SUM(CASE WHEN Decision IN ('UO','CO','OFFER') THEN 1 ELSE 0 END) AS offers, " +
                    "SUM(CASE WHEN Decision = 'CA' THEN 1 ELSE 0 END) AS ca, " +
                    "SUM(CASE WHEN Decision = 'UA' THEN 1 ELSE 0 END) AS ua " +
                    "FROM \"" + tableName + "\" " +
                    "WHERE \"Nationality_2\" IS NOT NULL AND \"Nationality_2\" != '' " +
                    groupByClause;

            logDebug("getLIVCounts() SQL: " + sql);
            logDebug("getLIVCounts() table=" + tableName + ", programmeColumn=" + programmeColumn + ", intakeColumn=" + intakeColumn);

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                int rowCount = 0;
                while (rs.next()) {
                    rowCount++;
                    String intake = intakeColumn != null ? rs.getString("intake") : "";
                    String programme = rs.getString("programme");
                    String nationality = rs.getString("nationality");
                    int total = rs.getInt("total");
                    int offers = rs.getInt("offers");
                    int ca = rs.getInt("ca");
                    int ua = rs.getInt("ua");

                    logDebug("Row " + rowCount + ": intake='" + intake + "', programme='" + programme +
                            "', nationality='" + nationality + "', total=" + total +
                            ", offers=" + offers + ", ca=" + ca + ", ua=" + ua);

                    // Only add if programme is not null/empty
                    if (programme != null && !programme.trim().isEmpty()) {
                        String cleanProgramme = programme.trim();
                        results.add(new CountResult(intake, cleanProgramme, nationality, "", total));
                        results.add(new CountResult(intake + "_OFFERS", cleanProgramme, nationality, "OFFERS", offers));
                        results.add(new CountResult(intake + "_CA", cleanProgramme, nationality, "CA", ca));
                        results.add(new CountResult(intake + "_UA", cleanProgramme, nationality, "UA", ua));
                    }
                }
                logDebug("getLIVCounts() processed " + rowCount + " rows from SQL result");
            }
        } catch (SQLException e) {
            logError("Error getting LIV counts from " + tableName + ": " + e.getMessage(), e);
        }

        logDebug("getLIVCounts() returning " + results.size() + " results");
        return results;
    }

    private ObservableList<LocalVsInternationalRow> buildLIVRows(List<String> intakes, List<String> programmes,
                                                                 List<CountResult> rawData) {
        logDebug("buildLIVRows() START - intakes=" + intakes.size() + ", programmes=" + programmes.size() + ", rawData=" + rawData.size());

        ObservableList<LocalVsInternationalRow> rows = FXCollections.observableArrayList();

        if (intakes.isEmpty() || programmes.isEmpty()) {
            logDebug("buildLIVRows() - intakes or programmes is empty, returning empty rows");
            return rows;
        }

        // Build map: intake -> programme -> nationality -> metric -> count
        Map<String, Map<String, Map<String, Map<String, Integer>>>> dataMap = new LinkedHashMap<>();

        // Initialize the map with all intakes and programmes
        for (String intake : intakes) {
            if (intake == null || intake.trim().isEmpty()) continue;
            String cleanIntake = intake.trim();
            dataMap.putIfAbsent(cleanIntake, new LinkedHashMap<>());
            for (String programme : programmes) {
                if (programme == null || programme.trim().isEmpty()) continue;
                String cleanProgramme = programme.trim();
                dataMap.get(cleanIntake).computeIfAbsent(cleanProgramme, k -> new LinkedHashMap<>());
                dataMap.get(cleanIntake).get(cleanProgramme).putIfAbsent("Malaysian", new HashMap<>());
                dataMap.get(cleanIntake).get(cleanProgramme).putIfAbsent("Non Malaysian", new HashMap<>());
            }
        }

        logDebug("buildLIVRows() - dataMap initialized with " + dataMap.size() + " intakes");

        // Populate map with data from rawData
        int populatedCount = 0;
        for (CountResult cr : rawData) {
            if (cr.group1.contains("_")) continue; // Skip aggregated metrics
            String intake = cr.group1;
            String programme = cr.group2;
            String nationality = cr.nationality;

            // Check if this intake and programme exist in our map
            if (dataMap.containsKey(intake) && dataMap.get(intake).containsKey(programme)) {
                Map<String, Map<String, Integer>> progData = dataMap.get(intake).get(programme);
                if (progData.containsKey(nationality)) {
                    // Find associated metrics
                    int total = cr.count;
                    int offers = findMetric(rawData, intake + "_OFFERS", programme, nationality);
                    int ca = findMetric(rawData, intake + "_CA", programme, nationality);
                    int ua = findMetric(rawData, intake + "_UA", programme, nationality);

                    Map<String, Integer> metricMap = progData.get(nationality);
                    metricMap.put("Applications", total);
                    metricMap.put("Offers", offers);
                    metricMap.put("CA", ca);
                    metricMap.put("UA", ua);
                    populatedCount++;

                    logDebug("buildLIVRows() - populated: intake=" + intake + ", programme=" + programme +
                            ", nationality=" + nationality + ", Apps=" + total + ", Offers=" + offers +
                            ", CA=" + ca + ", UA=" + ua);
                }
            }
        }
        logDebug("buildLIVRows() - populated " + populatedCount + " entries into dataMap");

        // Build rows
        int rowCount = 0;
        for (String intake : intakes) {
            if (intake == null || intake.trim().isEmpty()) continue;
            String cleanIntake = intake.trim();

            // Add intake header row
            rows.add(new LocalVsInternationalRow("--- " + cleanIntake + " ---"));
            rowCount++;

            for (String programme : programmes) {
                if (programme == null || programme.trim().isEmpty()) continue;
                String cleanProgramme = programme.trim();

                Map<String, Map<String, Integer>> progData = dataMap.getOrDefault(cleanIntake, new LinkedHashMap<>())
                        .getOrDefault(cleanProgramme, new LinkedHashMap<>());

                Map<String, Integer> lData = progData.getOrDefault("Malaysian", new HashMap<>());
                Map<String, Integer> iData = progData.getOrDefault("Non Malaysian", new HashMap<>());

                int lApps = lData.getOrDefault("Applications", 0);
                int iApps = iData.getOrDefault("Applications", 0);
                int lOffers = lData.getOrDefault("Offers", 0);
                int iOffers = iData.getOrDefault("Offers", 0);
                int lCA = lData.getOrDefault("CA", 0);
                int iCA = iData.getOrDefault("CA", 0);
                int lUA = lData.getOrDefault("UA", 0);
                int iUA = iData.getOrDefault("UA", 0);

                if (lApps > 0 || iApps > 0 || lOffers > 0 || iOffers > 0) {
                    logDebug("buildLIVRows() - row: intake=" + cleanIntake + ", programme=" + cleanProgramme +
                            ", L Apps=" + lApps + ", I Apps=" + iApps);
                }

                rows.add(new LocalVsInternationalRow(
                        cleanProgramme,
                        lApps, iApps,
                        lOffers, iOffers,
                        lCA, iCA,
                        lUA, iUA
                ));
                rowCount++;
            }
        }

        logDebug("buildLIVRows() - created " + rowCount + " rows");
        return rows;
    }

    private ObservableList<LocalVsInternationalRow> buildLIVRowsNoIntake(List<String> programmes,
                                                                         List<CountResult> rawData) {
        logDebug("buildLIVRowsNoIntake() START - programmes=" + programmes.size() + ", rawData=" + rawData.size());

        ObservableList<LocalVsInternationalRow> rows = FXCollections.observableArrayList();

        if (programmes.isEmpty()) {
            logDebug("buildLIVRowsNoIntake() - programmes is empty, returning empty rows");
            return rows;
        }

        // Build map: programme -> nationality -> metric -> count
        Map<String, Map<String, Map<String, Integer>>> dataMap = new LinkedHashMap<>();

        // Initialize the map with all programmes
        for (String programme : programmes) {
            if (programme == null || programme.trim().isEmpty()) continue;
            String cleanProgramme = programme.trim();
            dataMap.putIfAbsent(cleanProgramme, new LinkedHashMap<>());
            dataMap.get(cleanProgramme).putIfAbsent("Malaysian", new HashMap<>());
            dataMap.get(cleanProgramme).putIfAbsent("Non Malaysian", new HashMap<>());
        }

        logDebug("buildLIVRowsNoIntake() - dataMap initialized with " + dataMap.size() + " programmes");

        // Populate map
        int populatedCount = 0;
        for (CountResult cr : rawData) {
            if (cr.group1.contains("_")) continue;
            String programme = cr.group2;
            String nationality = cr.nationality;

            if (dataMap.containsKey(programme)) {
                Map<String, Map<String, Integer>> progData = dataMap.get(programme);
                if (progData.containsKey(nationality)) {
                    int total = cr.count;
                    int offers = findMetric(rawData, "_OFFERS", programme, nationality);
                    int ca = findMetric(rawData, "_CA", programme, nationality);
                    int ua = findMetric(rawData, "_UA", programme, nationality);

                    Map<String, Integer> metricMap = progData.get(nationality);
                    metricMap.put("Applications", total);
                    metricMap.put("Offers", offers);
                    metricMap.put("CA", ca);
                    metricMap.put("UA", ua);
                    populatedCount++;

                    logDebug("buildLIVRowsNoIntake() - populated: programme=" + programme +
                            ", nationality=" + nationality + ", Apps=" + total + ", Offers=" + offers +
                            ", CA=" + ca + ", UA=" + ua);
                }
            }
        }
        logDebug("buildLIVRowsNoIntake() - populated " + populatedCount + " entries into dataMap");

        // Build rows
        int rowCount = 0;
        for (String programme : programmes) {
            if (programme == null || programme.trim().isEmpty()) continue;
            String cleanProgramme = programme.trim();

            Map<String, Map<String, Integer>> progData = dataMap.getOrDefault(cleanProgramme, new LinkedHashMap<>());
            Map<String, Integer> lData = progData.getOrDefault("Malaysian", new HashMap<>());
            Map<String, Integer> iData = progData.getOrDefault("Non Malaysian", new HashMap<>());

            int lApps = lData.getOrDefault("Applications", 0);
            int iApps = iData.getOrDefault("Applications", 0);
            int lOffers = lData.getOrDefault("Offers", 0);
            int iOffers = iData.getOrDefault("Offers", 0);
            int lCA = lData.getOrDefault("CA", 0);
            int iCA = iData.getOrDefault("CA", 0);
            int lUA = lData.getOrDefault("UA", 0);
            int iUA = iData.getOrDefault("UA", 0);

            if (lApps > 0 || iApps > 0) {
                logDebug("buildLIVRowsNoIntake() - row: programme=" + cleanProgramme +
                        ", L Apps=" + lApps + ", I Apps=" + iApps);
            }

            rows.add(new LocalVsInternationalRow(
                    cleanProgramme,
                    lApps, iApps,
                    lOffers, iOffers,
                    lCA, iCA,
                    lUA, iUA
            ));
            rowCount++;
        }

        logDebug("buildLIVRowsNoIntake() - created " + rowCount + " rows");
        return rows;
    }

    private int findMetric(List<CountResult> rawData, String searchKey, String programme, String nationality) {
        for (CountResult cr : rawData) {
            if (cr.group1.equals(searchKey) &&
                    cr.group2.equals(programme) &&
                    cr.nationality.equals(nationality)) {
                return cr.count;
            }
        }
        return 0;
    }

    // ================================================================
    // 3. PROGRAMME INTERESTS DATA
    // ================================================================

    /**
     * Get Programme Interests for FY
     */
    public ObservableList<ProgrammeInterestRow> getProgrammeInterestsFY(String nationalityFilter) {
        logDebug("=== getProgrammeInterestsFY() START - filter=" + nationalityFilter + " ===");
        List<String> intakes = getFYIntakes();
        List<String> programmes = getFYProgrammes();
        List<String> decisions = getDecisionCodes();

        logDebug("Intakes: " + intakes);
        logDebug("Programmes: " + programmes);
        logDebug("Decisions: " + decisions);

        if (intakes.isEmpty() || programmes.isEmpty()) {
            logDebug("getProgrammeInterestsFY() - intakes or programmes is empty, returning empty rows");
            return FXCollections.observableArrayList();
        }

        Map<String, Map<String, Map<String, Integer>>> rawData = getPIData(
                FY_TABLE, "Foundation", "Intake", nationalityFilter
        );
        logDebug("Raw data keys: " + rawData.keySet());

        ObservableList<ProgrammeInterestRow> result = buildPIRows(intakes, programmes, decisions, rawData);
        logDebug("Result rows: " + result.size());
        logDebug("=== getProgrammeInterestsFY() END ===");
        return result;
    }

    /**
     * Get Programme Interests for UGPG (no intake grouping)
     */
    public ObservableList<ProgrammeInterestRow> getProgrammeInterestsUGPG(String nationalityFilter) {
        logDebug("=== getProgrammeInterestsUGPG() START - filter=" + nationalityFilter + " ===");
        List<String> programmes = getUGPGProgrammes();
        List<String> decisions = getDecisionCodes();

        logDebug("Programmes: " + programmes);
        logDebug("Decisions: " + decisions);

        if (programmes.isEmpty()) {
            logDebug("getProgrammeInterestsUGPG() - programmes is empty, returning empty rows");
            return FXCollections.observableArrayList();
        }

        Map<String, Map<String, Map<String, Integer>>> rawData = getPIData(
                UGPG_TABLE, "Course", null, nationalityFilter
        );
        logDebug("Raw data keys: " + rawData.keySet());

        ObservableList<ProgrammeInterestRow> result = buildPIRowsNoIntake(programmes, decisions, rawData);
        logDebug("Result rows: " + result.size());
        logDebug("=== getProgrammeInterestsUGPG() END ===");
        return result;
    }

    private Map<String, Map<String, Map<String, Integer>>> getPIData(
            String tableName, String programmeColumn, String intakeColumn, String nationalityFilter) {

        logDebug("getPIData() START - table=" + tableName + ", programmeColumn=" + programmeColumn +
                ", intakeColumn=" + intakeColumn + ", filter=" + nationalityFilter);

        Map<String, Map<String, Map<String, Integer>>> results = new LinkedHashMap<>();

        try (Connection conn = dbManager.getConnection()) {
            String groupByClause = intakeColumn != null ?
                    "GROUP BY \"" + intakeColumn + "\", \"" + programmeColumn + "\", Decision" :
                    "GROUP BY \"" + programmeColumn + "\", Decision";

            String intakeSelect = intakeColumn != null ?
                    "\"" + intakeColumn + "\" AS intake, " :
                    "";

            String nationalityWhere = nationalityFilter != null && !nationalityFilter.isEmpty() ?
                    "AND \"Nationality_2\" = '" + nationalityFilter + "' " :
                    "AND \"Nationality_2\" IS NOT NULL AND \"Nationality_2\" != '' ";

            String sql = "SELECT " + intakeSelect +
                    "\"" + programmeColumn + "\" AS programme, " +
                    "Decision, COUNT(*) AS count " +
                    "FROM \"" + tableName + "\" " +
                    "WHERE Decision IS NOT NULL AND Decision != '' " +
                    nationalityWhere +
                    groupByClause;

            logDebug("getPIData() SQL: " + sql);

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                int rowCount = 0;
                while (rs.next()) {
                    rowCount++;
                    String intake = intakeColumn != null ? rs.getString("intake") : "";
                    String programme = rs.getString("programme");
                    String decision = rs.getString("Decision");
                    int count = rs.getInt("count");

                    logDebug("Row " + rowCount + ": intake='" + intake + "', programme='" + programme +
                            "', decision='" + decision + "', count=" + count);

                    // Skip if programme is null/empty
                    if (programme == null || programme.trim().isEmpty()) continue;
                    String cleanProgramme = programme.trim();

                    String key = intakeColumn != null ? intake + "||" + cleanProgramme : cleanProgramme;
                    results.putIfAbsent(key, new LinkedHashMap<>());
                    results.get(key).putIfAbsent(decision, new HashMap<>());
                    results.get(key).get(decision).put("count", count);
                }
                logDebug("getPIData() processed " + rowCount + " rows from SQL result");
            }
        } catch (SQLException e) {
            logError("Error getting PI data from " + tableName + ": " + e.getMessage(), e);
        }

        logDebug("getPIData() returning " + results.size() + " keys");
        return results;
    }

    private ObservableList<ProgrammeInterestRow> buildPIRows(
            List<String> intakes, List<String> programmes,
            List<String> decisions, Map<String, Map<String, Map<String, Integer>>> rawData) {

        logDebug("buildPIRows() START - intakes=" + intakes.size() + ", programmes=" + programmes.size() +
                ", decisions=" + decisions.size() + ", rawData keys=" + rawData.size());

        ObservableList<ProgrammeInterestRow> rows = FXCollections.observableArrayList();
        int rowCount = 0;

        for (String intake : intakes) {
            if (intake == null || intake.trim().isEmpty()) continue;
            String cleanIntake = intake.trim();

            // Add intake header row
            rows.add(new ProgrammeInterestRow("--- " + cleanIntake + " ---"));
            rowCount++;

            for (String programme : programmes) {
                if (programme == null || programme.trim().isEmpty()) continue;
                String cleanProgramme = programme.trim();

                String key = cleanIntake + "||" + cleanProgramme;
                Map<String, Map<String, Integer>> decisionMap = rawData.getOrDefault(key, new LinkedHashMap<>());

                Map<String, Integer> counts = new LinkedHashMap<>();
                int total = 0;

                for (String decision : decisions) {
                    int count = decisionMap.getOrDefault(decision, new HashMap<>()).getOrDefault("count", 0);
                    counts.put(decision, count);
                    total += count;
                }

                if (total > 0) {
                    logDebug("buildPIRows() - row: intake=" + cleanIntake + ", programme=" + cleanProgramme +
                            ", total=" + total + ", counts=" + counts);
                }

                rows.add(new ProgrammeInterestRow(cleanProgramme, counts, total));
                rowCount++;
            }
        }

        logDebug("buildPIRows() - created " + rowCount + " rows");
        return rows;
    }

    private ObservableList<ProgrammeInterestRow> buildPIRowsNoIntake(
            List<String> programmes, List<String> decisions,
            Map<String, Map<String, Map<String, Integer>>> rawData) {

        logDebug("buildPIRowsNoIntake() START - programmes=" + programmes.size() +
                ", decisions=" + decisions.size() + ", rawData keys=" + rawData.size());

        ObservableList<ProgrammeInterestRow> rows = FXCollections.observableArrayList();
        int rowCount = 0;

        for (String programme : programmes) {
            if (programme == null || programme.trim().isEmpty()) continue;
            String cleanProgramme = programme.trim();

            Map<String, Map<String, Integer>> decisionMap = rawData.getOrDefault(cleanProgramme, new LinkedHashMap<>());

            Map<String, Integer> counts = new LinkedHashMap<>();
            int total = 0;

            for (String decision : decisions) {
                int count = decisionMap.getOrDefault(decision, new HashMap<>()).getOrDefault("count", 0);
                counts.put(decision, count);
                total += count;
            }

            if (total > 0) {
                logDebug("buildPIRowsNoIntake() - row: programme=" + cleanProgramme +
                        ", total=" + total + ", counts=" + counts);
            }

            rows.add(new ProgrammeInterestRow(cleanProgramme, counts, total));
            rowCount++;
        }

        logDebug("buildPIRowsNoIntake() - created " + rowCount + " rows");
        return rows;
    }
}
package com.example.app.controller;

import com.example.app.service.RegionAnalyserService;
import com.example.app.service.RegionAnalyserService.AnalysisResult;
import com.example.app.service.RegionAnalyserService.RegionData;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.*;

public class RegionAnalyserController {

    private static final String DEBUG_PREFIX = "🔍 [RegionAnalyserController]";

    @FXML private VBox mainContainer;
    @FXML private Label statusLabel;
    @FXML private Label fileCountLabel;
    @FXML private Label selectedFileLabel;
    @FXML private Label sheetsFoundLabel;
    @FXML private Label rowCountLabel;
    @FXML private Label progressLabel;
    @FXML private ProgressBar progressBar;
    @FXML private TextField year1Field;
    @FXML private TextField year2Field;
    @FXML private Button generateBtn;
    @FXML private Button exportBtn;
    @FXML private ListView<File> fileListView;
    @FXML private TableView<Map<String, String>> previewTable;

    private final ObservableList<File> loadedFiles = FXCollections.observableArrayList();
    private AnalysisResult analysisResult;
    private final RegionAnalyserService analyserService = new RegionAnalyserService();

    @FXML
    public void initialize() {
        System.out.println(DEBUG_PREFIX + " ========================================");
        System.out.println(DEBUG_PREFIX + " INITIALIZING RegionAnalyserController");
        System.out.println(DEBUG_PREFIX + " ========================================");

        exportBtn.setDisable(true);
        generateBtn.setDisable(true);

        fileListView.setItems(loadedFiles);
        fileListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                System.out.println(DEBUG_PREFIX + " File selected in ListView: " + newVal.getName());
                selectedFileLabel.setText(newVal.getName());
                updateFileInfo(newVal);
            }
        });

        // Enable generate when files are loaded and years are entered
        year1Field.textProperty().addListener((obs, oldVal, newVal) -> {
            System.out.println(DEBUG_PREFIX + " Year1 field changed: '" + oldVal + "' -> '" + newVal + "'");
            checkGenerateReady();
        });
        year2Field.textProperty().addListener((obs, oldVal, newVal) -> {
            System.out.println(DEBUG_PREFIX + " Year2 field changed: '" + oldVal + "' -> '" + newVal + "'");
            checkGenerateReady();
        });
        loadedFiles.addListener((ListChangeListener<File>) c -> {
            System.out.println(DEBUG_PREFIX + " Loaded files list changed. New size: " + loadedFiles.size());
            fileCountLabel.setText(loadedFiles.size() + " files loaded");
            checkGenerateReady();
        });

        statusLabel.setText("Load files and enter years to begin.");
        statusLabel.setStyle("-fx-text-fill: #6c757d;");
        progressBar.setVisible(false);
        progressLabel.setText("");

        System.out.println(DEBUG_PREFIX + " Initialization complete.");
        System.out.println(DEBUG_PREFIX + " ========================================");
    }

    private void checkGenerateReady() {
        boolean hasFiles = !loadedFiles.isEmpty();
        boolean hasYears = !year1Field.getText().trim().isEmpty() &&
                !year2Field.getText().trim().isEmpty();
        boolean ready = hasFiles && hasYears;

        System.out.println(DEBUG_PREFIX + " checkGenerateReady: hasFiles=" + hasFiles +
                ", hasYears=" + hasYears + ", ready=" + ready);

        generateBtn.setDisable(!ready);
    }

    private void updateFileInfo(File file) {
        System.out.println(DEBUG_PREFIX + " updateFileInfo: Reading sheet info for: " + file.getName());
        try {
            List<String> sheets = analyserService.getSheetNames(file);
            System.out.println(DEBUG_PREFIX + " updateFileInfo: Found " + sheets.size() + " sheets: " + sheets);
            sheetsFoundLabel.setText(String.join(", ", sheets));
        } catch (Exception e) {
            System.err.println(DEBUG_PREFIX + " updateFileInfo: ERROR reading file: " + e.getMessage());
            e.printStackTrace();
            sheetsFoundLabel.setText("Error reading file");
        }
    }

    @FXML
    private void handleAddFiles() {
        System.out.println(DEBUG_PREFIX + " ========================================");
        System.out.println(DEBUG_PREFIX + " handleAddFiles: User clicked Add Files");
        System.out.println(DEBUG_PREFIX + " ========================================");

        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx", "*.xls")
        );
        fileChooser.setTitle("Select Excel Files");

        List<File> files = fileChooser.showOpenMultipleDialog(null);
        if (files != null && !files.isEmpty()) {
            System.out.println(DEBUG_PREFIX + " handleAddFiles: User selected " + files.size() + " file(s):");
            for (File f : files) {
                System.out.println(DEBUG_PREFIX + "   - " + f.getName() + " (" + f.length() + " bytes)");
            }

            loadedFiles.addAll(files);
            if (!loadedFiles.isEmpty()) {
                fileListView.getSelectionModel().select(0);
                System.out.println(DEBUG_PREFIX + " handleAddFiles: Selected first file: " + loadedFiles.get(0).getName());
            }
            statusLabel.setText("Added " + files.size() + " file(s). Enter years and click Generate.");
            statusLabel.setStyle("-fx-text-fill: #17a2b8;");
        } else {
            System.out.println(DEBUG_PREFIX + " handleAddFiles: No files selected by user.");
        }
        System.out.println(DEBUG_PREFIX + " handleAddFiles: Complete. Total files: " + loadedFiles.size());
        System.out.println(DEBUG_PREFIX + " ========================================");
    }

    @FXML
    private void handleClearFiles() {
        System.out.println(DEBUG_PREFIX + " ========================================");
        System.out.println(DEBUG_PREFIX + " handleClearFiles: Clearing all loaded files");
        System.out.println(DEBUG_PREFIX + " handleClearFiles: Previously had " + loadedFiles.size() + " files");

        loadedFiles.clear();
        fileListView.getSelectionModel().clearSelection();
        selectedFileLabel.setText("None");
        sheetsFoundLabel.setText("-");
        previewTable.getItems().clear();
        previewTable.getColumns().clear();
        analysisResult = null;
        exportBtn.setDisable(true);
        rowCountLabel.setText("0 rows");
        statusLabel.setText("Cleared all files.");
        statusLabel.setStyle("-fx-text-fill: #6c757d;");

        System.out.println(DEBUG_PREFIX + " handleClearFiles: Complete. Files cleared.");
        System.out.println(DEBUG_PREFIX + " ========================================");
    }

    @FXML
    private void handleGenerate() {
        System.out.println(DEBUG_PREFIX + " ========================================");
        System.out.println(DEBUG_PREFIX + " handleGenerate: STARTING ANALYSIS");
        System.out.println(DEBUG_PREFIX + " ========================================");

        if (loadedFiles.isEmpty()) {
            System.err.println(DEBUG_PREFIX + " handleGenerate: ERROR - No files loaded!");
            statusLabel.setText("Please load at least one Excel file.");
            statusLabel.setStyle("-fx-text-fill: #dc3545;");
            return;
        }

        String year1 = year1Field.getText().trim();
        String year2 = year2Field.getText().trim();

        System.out.println(DEBUG_PREFIX + " handleGenerate: Year1 = '" + year1 + "'");
        System.out.println(DEBUG_PREFIX + " handleGenerate: Year2 = '" + year2 + "'");
        System.out.println(DEBUG_PREFIX + " handleGenerate: Files to process = " + loadedFiles.size());

        if (year1.isEmpty() || year2.isEmpty()) {
            System.err.println(DEBUG_PREFIX + " handleGenerate: ERROR - Years not entered!");
            statusLabel.setText("Please enter both Application Year 1 and Year 2.");
            statusLabel.setStyle("-fx-text-fill: #dc3545;");
            return;
        }

        // Log all files being processed
        System.out.println(DEBUG_PREFIX + " handleGenerate: Files to be processed:");
        for (int i = 0; i < loadedFiles.size(); i++) {
            File f = loadedFiles.get(i);
            System.out.println(DEBUG_PREFIX + "   [" + (i + 1) + "] " + f.getName() +
                    " (path: " + f.getAbsolutePath() + ")");
        }

        try {
            statusLabel.setText("Analysing " + loadedFiles.size() + " file(s)...");
            statusLabel.setStyle("-fx-text-fill: #17a2b8;");
            generateBtn.setDisable(true);
            progressBar.setVisible(true);
            progressBar.setProgress(0);
            progressLabel.setText("Processing...");

            System.out.println(DEBUG_PREFIX + " handleGenerate: Calling analyserService.analyseFiles()...");

            // Process all files with the two years
            analysisResult = analyserService.analyseFiles(
                    new ArrayList<>(loadedFiles),
                    year1,
                    year2,
                    progress -> {
                        // Update progress
                        javafx.application.Platform.runLater(() -> {
                            progressBar.setProgress(progress);
                            int percent = (int) (progress * 100);
                            progressLabel.setText("Processing... " + percent + "%");
                            System.out.println(DEBUG_PREFIX + " handleGenerate: Progress update: " + percent + "%");
                        });
                    }
            );

            System.out.println(DEBUG_PREFIX + " handleGenerate: analyseFiles() completed successfully!");
            System.out.println(DEBUG_PREFIX + " handleGenerate: AnalysisResult details:");
            System.out.println(DEBUG_PREFIX + "   - Total regions found: " + analysisResult.getTotalRegions());
            System.out.println(DEBUG_PREFIX + "   - Years: " + analysisResult.getYears());
            System.out.println(DEBUG_PREFIX + "   - Region data keys: " + analysisResult.getRegionData().keySet());

            // Log some sample data
            if (!analysisResult.getRegionData().isEmpty()) {
                Map.Entry<String, RegionData> firstEntry = analysisResult.getRegionData().entrySet().iterator().next();
                RegionData sampleData = firstEntry.getValue();
                System.out.println(DEBUG_PREFIX + "   - Sample region data for '" + firstEntry.getKey() + "':");
                System.out.println(DEBUG_PREFIX + "       Foundation Apps " + year1 + ": " + sampleData.getFoundationApps(year1));
                System.out.println(DEBUG_PREFIX + "       Foundation Apps " + year2 + ": " + sampleData.getFoundationApps(year2));
                System.out.println(DEBUG_PREFIX + "       Foundation Offers " + year1 + ": " + sampleData.getFoundationOffers(year1));
                System.out.println(DEBUG_PREFIX + "       Foundation Offers " + year2 + ": " + sampleData.getFoundationOffers(year2));
                System.out.println(DEBUG_PREFIX + "       UGPG Apps " + year1 + ": " + sampleData.getUgpgApps(year1));
                System.out.println(DEBUG_PREFIX + "       UGPG Apps " + year2 + ": " + sampleData.getUgpgApps(year2));
            }

            // Render the table with dynamic years
            System.out.println(DEBUG_PREFIX + " handleGenerate: Rendering analysis table...");
            renderAnalysisTable(analysisResult, year1, year2);

            statusLabel.setText("✓ Analysis complete. " + analysisResult.getTotalRegions() + " regions found.");
            statusLabel.setStyle("-fx-text-fill: #28a745;");
            exportBtn.setDisable(false);
            progressBar.setVisible(false);
            progressLabel.setText("");

            System.out.println(DEBUG_PREFIX + " handleGenerate: ANALYSIS COMPLETE - SUCCESS");
            System.out.println(DEBUG_PREFIX + " ========================================");

        } catch (Exception e) {
            System.err.println(DEBUG_PREFIX + " handleGenerate: ERROR - " + e.getMessage());
            e.printStackTrace();
            statusLabel.setText("Error analysing data: " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill: #dc3545;");
            progressBar.setVisible(false);
            progressLabel.setText("");
        } finally {
            generateBtn.setDisable(false);
        }
    }

    private void renderAnalysisTable(AnalysisResult result, String year1, String year2) {
        System.out.println(DEBUG_PREFIX + " ========================================");
        System.out.println(DEBUG_PREFIX + " renderAnalysisTable: Building table view");
        System.out.println(DEBUG_PREFIX + " renderAnalysisTable: Year1 = " + year1 + ", Year2 = " + year2);
        System.out.println(DEBUG_PREFIX + " renderAnalysisTable: Regions count = " + result.getRegionData().size());

        previewTable.getItems().clear();
        previewTable.getColumns().clear();

        // Get all regions
        List<String> regions = new ArrayList<>(result.getRegionData().keySet());
        Collections.sort(regions);
        System.out.println(DEBUG_PREFIX + " renderAnalysisTable: Sorted regions: " + regions);

        // Add "Total" as last row
        regions.add("Total");
        System.out.println(DEBUG_PREFIX + " renderAnalysisTable: Added 'Total' row");

        // Create columns with dynamic year names
        TableColumn<Map<String, String>, String> regionCol = new TableColumn<>("Region");
        regionCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().get("Region")));
        regionCol.setPrefWidth(150);
        regionCol.setStyle("-fx-font-weight: bold; -fx-background-color: #f8f9fa;");
        regionCol.setSortable(false);

        // Foundation Programmes section
        TableColumn<Map<String, String>, String> foundationHeader = new TableColumn<>("Foundation Programmes");
        foundationHeader.setStyle("-fx-background-color: #0072B2; -fx-text-fill: white; -fx-font-weight: bold; -fx-alignment: CENTER;");

        // Applications
        TableColumn<Map<String, String>, String> foundationApps = new TableColumn<>("Applications");
        foundationApps.setStyle("-fx-background-color: #E8F4FD; -fx-alignment: CENTER;");

        TableColumn<Map<String, String>, String> foundationAppsYear1 = new TableColumn<>(year1);
        foundationAppsYear1.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getOrDefault("Foundation_Apps_" + year1, "0")));
        foundationAppsYear1.setStyle("-fx-alignment: CENTER;");

        TableColumn<Map<String, String>, String> foundationAppsYear2 = new TableColumn<>(year2);
        foundationAppsYear2.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getOrDefault("Foundation_Apps_" + year2, "0")));
        foundationAppsYear2.setStyle("-fx-alignment: CENTER;");

        foundationApps.getColumns().addAll(foundationAppsYear1, foundationAppsYear2);

        // Offers
        TableColumn<Map<String, String>, String> foundationOffers = new TableColumn<>("Offers");
        foundationOffers.setStyle("-fx-background-color: #FFF3CD; -fx-alignment: CENTER;");

        TableColumn<Map<String, String>, String> foundationOffersYear1 = new TableColumn<>(year1);
        foundationOffersYear1.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getOrDefault("Foundation_Offers_" + year1, "0")));
        foundationOffersYear1.setStyle("-fx-alignment: CENTER;");

        TableColumn<Map<String, String>, String> foundationOffersYear2 = new TableColumn<>(year2);
        foundationOffersYear2.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getOrDefault("Foundation_Offers_" + year2, "0")));
        foundationOffersYear2.setStyle("-fx-alignment: CENTER;");

        foundationOffers.getColumns().addAll(foundationOffersYear1, foundationOffersYear2);

        // Acceptances
        TableColumn<Map<String, String>, String> foundationAccept = new TableColumn<>("Acceptances");
        foundationAccept.setStyle("-fx-background-color: #F8D7DA; -fx-alignment: CENTER;");

        TableColumn<Map<String, String>, String> foundationAcceptYear1 = new TableColumn<>(year1);
        foundationAcceptYear1.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getOrDefault("Foundation_Accept_" + year1, "0")));
        foundationAcceptYear1.setStyle("-fx-alignment: CENTER;");

        TableColumn<Map<String, String>, String> foundationAcceptYear2 = new TableColumn<>(year2);
        foundationAcceptYear2.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getOrDefault("Foundation_Accept_" + year2, "0")));
        foundationAcceptYear2.setStyle("-fx-alignment: CENTER;");

        foundationAccept.getColumns().addAll(foundationAcceptYear1, foundationAcceptYear2);

        foundationHeader.getColumns().addAll(foundationApps, foundationOffers, foundationAccept);

        // UG & PG Programmes section
        TableColumn<Map<String, String>, String> ugpgHeader = new TableColumn<>("UG & PG Programmes");
        ugpgHeader.setStyle("-fx-background-color: #28A745; -fx-text-fill: white; -fx-font-weight: bold; -fx-alignment: CENTER;");

        // Applications
        TableColumn<Map<String, String>, String> ugpgApps = new TableColumn<>("Applications");
        ugpgApps.setStyle("-fx-background-color: #E8F4FD; -fx-alignment: CENTER;");

        TableColumn<Map<String, String>, String> ugpgAppsYear1 = new TableColumn<>(year1);
        ugpgAppsYear1.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getOrDefault("UGPG_Apps_" + year1, "0")));
        ugpgAppsYear1.setStyle("-fx-alignment: CENTER;");

        TableColumn<Map<String, String>, String> ugpgAppsYear2 = new TableColumn<>(year2);
        ugpgAppsYear2.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getOrDefault("UGPG_Apps_" + year2, "0")));
        ugpgAppsYear2.setStyle("-fx-alignment: CENTER;");

        ugpgApps.getColumns().addAll(ugpgAppsYear1, ugpgAppsYear2);

        // Offers
        TableColumn<Map<String, String>, String> ugpgOffers = new TableColumn<>("Offers");
        ugpgOffers.setStyle("-fx-background-color: #FFF3CD; -fx-alignment: CENTER;");

        TableColumn<Map<String, String>, String> ugpgOffersYear1 = new TableColumn<>(year1);
        ugpgOffersYear1.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getOrDefault("UGPG_Offers_" + year1, "0")));
        ugpgOffersYear1.setStyle("-fx-alignment: CENTER;");

        TableColumn<Map<String, String>, String> ugpgOffersYear2 = new TableColumn<>(year2);
        ugpgOffersYear2.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getOrDefault("UGPG_Offers_" + year2, "0")));
        ugpgOffersYear2.setStyle("-fx-alignment: CENTER;");

        ugpgOffers.getColumns().addAll(ugpgOffersYear1, ugpgOffersYear2);

        // Acceptances
        TableColumn<Map<String, String>, String> ugpgAccept = new TableColumn<>("Acceptances");
        ugpgAccept.setStyle("-fx-background-color: #F8D7DA; -fx-alignment: CENTER;");

        TableColumn<Map<String, String>, String> ugpgAcceptYear1 = new TableColumn<>(year1);
        ugpgAcceptYear1.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getOrDefault("UGPG_Accept_" + year1, "0")));
        ugpgAcceptYear1.setStyle("-fx-alignment: CENTER;");

        TableColumn<Map<String, String>, String> ugpgAcceptYear2 = new TableColumn<>(year2);
        ugpgAcceptYear2.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getOrDefault("UGPG_Accept_" + year2, "0")));
        ugpgAcceptYear2.setStyle("-fx-alignment: CENTER;");

        ugpgAccept.getColumns().addAll(ugpgAcceptYear1, ugpgAcceptYear2);

        ugpgHeader.getColumns().addAll(ugpgApps, ugpgOffers, ugpgAccept);

        // Add all columns to table
        previewTable.getColumns().add(regionCol);
        previewTable.getColumns().add(foundationHeader);
        previewTable.getColumns().add(ugpgHeader);

        System.out.println(DEBUG_PREFIX + " renderAnalysisTable: Table columns created: " + previewTable.getColumns().size());

        // Build data rows
        ObservableList<Map<String, String>> tableData = FXCollections.observableArrayList();

        System.out.println(DEBUG_PREFIX + " renderAnalysisTable: Building " + regions.size() + " data rows...");

        for (String region : regions) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("Region", region);

            if ("Total".equals(region)) {
                // Add totals - dynamically using the year keys
                row.put("Foundation_Apps_" + year1, String.valueOf(result.getTotalFoundationApps(year1)));
                row.put("Foundation_Apps_" + year2, String.valueOf(result.getTotalFoundationApps(year2)));
                row.put("Foundation_Offers_" + year1, String.valueOf(result.getTotalFoundationOffers(year1)));
                row.put("Foundation_Offers_" + year2, String.valueOf(result.getTotalFoundationOffers(year2)));
                row.put("Foundation_Accept_" + year1, String.valueOf(result.getTotalFoundationAccept(year1)));
                row.put("Foundation_Accept_" + year2, String.valueOf(result.getTotalFoundationAccept(year2)));
                row.put("UGPG_Apps_" + year1, String.valueOf(result.getTotalUGPGApps(year1)));
                row.put("UGPG_Apps_" + year2, String.valueOf(result.getTotalUGPGApps(year2)));
                row.put("UGPG_Offers_" + year1, String.valueOf(result.getTotalUGPGOffers(year1)));
                row.put("UGPG_Offers_" + year2, String.valueOf(result.getTotalUGPGOffers(year2)));
                row.put("UGPG_Accept_" + year1, String.valueOf(result.getTotalUGPGAccept(year1)));
                row.put("UGPG_Accept_" + year2, String.valueOf(result.getTotalUGPGAccept(year2)));

                System.out.println(DEBUG_PREFIX + " renderAnalysisTable: Total row - Foundation Apps " + year1 + ": " +
                        result.getTotalFoundationApps(year1) + ", " + year2 + ": " + result.getTotalFoundationApps(year2));
            } else {
                RegionData regionData = result.getRegionData().get(region);
                if (regionData != null) {
                    row.put("Foundation_Apps_" + year1, String.valueOf(regionData.getFoundationApps(year1)));
                    row.put("Foundation_Apps_" + year2, String.valueOf(regionData.getFoundationApps(year2)));
                    row.put("Foundation_Offers_" + year1, String.valueOf(regionData.getFoundationOffers(year1)));
                    row.put("Foundation_Offers_" + year2, String.valueOf(regionData.getFoundationOffers(year2)));
                    row.put("Foundation_Accept_" + year1, String.valueOf(regionData.getFoundationAccept(year1)));
                    row.put("Foundation_Accept_" + year2, String.valueOf(regionData.getFoundationAccept(year2)));
                    row.put("UGPG_Apps_" + year1, String.valueOf(regionData.getUgpgApps(year1)));
                    row.put("UGPG_Apps_" + year2, String.valueOf(regionData.getUgpgApps(year2)));
                    row.put("UGPG_Offers_" + year1, String.valueOf(regionData.getUgpgOffers(year1)));
                    row.put("UGPG_Offers_" + year2, String.valueOf(regionData.getUgpgOffers(year2)));
                    row.put("UGPG_Accept_" + year1, String.valueOf(regionData.getUgpgAccept(year1)));
                    row.put("UGPG_Accept_" + year2, String.valueOf(regionData.getUgpgAccept(year2)));
                } else {
                    System.err.println(DEBUG_PREFIX + " renderAnalysisTable: WARNING - No data for region: " + region);
                }
            }

            tableData.add(row);
        }

        previewTable.setItems(tableData);
        rowCountLabel.setText(tableData.size() + " rows");

        System.out.println(DEBUG_PREFIX + " renderAnalysisTable: Table populated with " + tableData.size() + " rows");

        // Apply row styling for total row
        previewTable.setRowFactory(tv -> new TableRow<Map<String, String>>() {
            @Override
            protected void updateItem(Map<String, String> item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setStyle("");
                } else if ("Total".equals(item.get("Region"))) {
                    setStyle("-fx-font-weight: bold; -fx-background-color: #e9ecef;");
                } else {
                    setStyle("");
                }
            }
        });

        System.out.println(DEBUG_PREFIX + " renderAnalysisTable: COMPLETE");
        System.out.println(DEBUG_PREFIX + " ========================================");
    }

    @FXML
    private void handleExport() {
        System.out.println(DEBUG_PREFIX + " ========================================");
        System.out.println(DEBUG_PREFIX + " handleExport: User clicked Export");

        if (analysisResult == null) {
            System.err.println(DEBUG_PREFIX + " handleExport: ERROR - No analysis result to export!");
            statusLabel.setText("No data to export. Generate report first.");
            statusLabel.setStyle("-fx-text-fill: #dc3545;");
            return;
        }

        String year1 = year1Field.getText().trim();
        String year2 = year2Field.getText().trim();

        System.out.println(DEBUG_PREFIX + " handleExport: Year1 = " + year1 + ", Year2 = " + year2);
        System.out.println(DEBUG_PREFIX + " handleExport: Regions = " + analysisResult.getRegionData().size());

        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx")
        );
        fileChooser.setInitialFileName("Region_Analysis_" + year1 + "_" + year2 + ".xlsx");

        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            System.out.println(DEBUG_PREFIX + " handleExport: Exporting to: " + file.getAbsolutePath());
            try {
                statusLabel.setText("Exporting...");
                statusLabel.setStyle("-fx-text-fill: #17a2b8;");
                exportBtn.setDisable(true);

                analyserService.exportReport(analysisResult, file, year1, year2);

                statusLabel.setText("✓ Exported successfully to: " + file.getName());
                statusLabel.setStyle("-fx-text-fill: #28a745;");

                System.out.println(DEBUG_PREFIX + " handleExport: Export completed successfully!");

            } catch (Exception e) {
                System.err.println(DEBUG_PREFIX + " handleExport: ERROR - " + e.getMessage());
                e.printStackTrace();
                statusLabel.setText("Error exporting: " + e.getMessage());
                statusLabel.setStyle("-fx-text-fill: #dc3545;");
            } finally {
                exportBtn.setDisable(false);
            }
        } else {
            System.out.println(DEBUG_PREFIX + " handleExport: User cancelled file save dialog.");
        }
        System.out.println(DEBUG_PREFIX + " ========================================");
    }

    @FXML
    private void handleClose() {
        System.out.println(DEBUG_PREFIX + " handleClose: Closing RegionAnalyser window");
        Stage stage = (Stage) mainContainer.getScene().getWindow();
        stage.close();
        System.out.println(DEBUG_PREFIX + " handleClose: Window closed.");
    }
}
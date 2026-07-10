package com.example.app.controller;

import com.example.app.service.RegionAnalyserService;
import com.example.app.service.RegionAnalyserService.AnalysisResult;
import com.example.app.service.RegionAnalyserService.RegionData;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.*;

public class RegionAnalyserController {

    @FXML private VBox mainContainer;
    @FXML private Label statusLabel;
    @FXML private Button load2025Btn;
    @FXML private Button load2026Btn;
    @FXML private Button generateBtn;
    @FXML private Button exportBtn;
    @FXML private Label file2025Label;
    @FXML private Label file2026Label;
    @FXML private TableView<Map<String, String>> previewTable;

    private File file2025;
    private File file2026;
    private AnalysisResult analysisResult;
    private final RegionAnalyserService analyserService = new RegionAnalyserService();

    @FXML
    public void initialize() {
        exportBtn.setDisable(true);
        generateBtn.setDisable(true);
        statusLabel.setText("Load 2025 and 2026 Excel files to begin.");
        statusLabel.setStyle("-fx-text-fill: #6c757d;");
    }

    @FXML
    private void handleLoad2025() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx", "*.xls")
        );
        fileChooser.setTitle("Select 2025 Data File");

        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            file2025 = file;
            file2025Label.setText("✓ " + file.getName());
            file2025Label.setStyle("-fx-text-fill: #28a745;");
            checkFilesLoaded();
        }
    }

    @FXML
    private void handleLoad2026() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx", "*.xls")
        );
        fileChooser.setTitle("Select 2026 Data File");

        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            file2026 = file;
            file2026Label.setText("✓ " + file.getName());
            file2026Label.setStyle("-fx-text-fill: #28a745;");
            checkFilesLoaded();
        }
    }

    private void checkFilesLoaded() {
        if (file2025 != null && file2026 != null) {
            generateBtn.setDisable(false);
            statusLabel.setText("Both files loaded. Click 'Generate Report' to analyse.");
            statusLabel.setStyle("-fx-text-fill: #17a2b8;");
        }
    }

    @FXML
    private void handleGenerate() {
        if (file2025 == null || file2026 == null) {
            statusLabel.setText("Please load both 2025 and 2026 files.");
            statusLabel.setStyle("-fx-text-fill: #dc3545;");
            return;
        }

        try {
            statusLabel.setText("Analysing data...");
            statusLabel.setStyle("-fx-text-fill: #17a2b8;");
            generateBtn.setDisable(true);

            // Process both files
            analysisResult = analyserService.analyseFiles(file2025, file2026);

            // Render the table
            renderAnalysisTable(analysisResult);

            statusLabel.setText("✓ Analysis complete. " + analysisResult.getTotalRegions() + " regions found.");
            statusLabel.setStyle("-fx-text-fill: #28a745;");
            exportBtn.setDisable(false);

        } catch (Exception e) {
            statusLabel.setText("Error analysing data: " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill: #dc3545;");
            e.printStackTrace();
        } finally {
            generateBtn.setDisable(false);
        }
    }

    @FXML
    private void handleExport() {
        if (analysisResult == null) {
            statusLabel.setText("No data to export. Generate report first.");
            statusLabel.setStyle("-fx-text-fill: #dc3545;");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx")
        );
        fileChooser.setInitialFileName("Region_Analysis_2025_2026.xlsx");

        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            try {
                statusLabel.setText("Exporting...");
                statusLabel.setStyle("-fx-text-fill: #17a2b8;");
                exportBtn.setDisable(true);

                analyserService.exportToExcel(analysisResult, file);

                statusLabel.setText("✓ Exported successfully to: " + file.getName());
                statusLabel.setStyle("-fx-text-fill: #28a745;");

            } catch (Exception e) {
                statusLabel.setText("Error exporting: " + e.getMessage());
                statusLabel.setStyle("-fx-text-fill: #dc3545;");
                e.printStackTrace();
            } finally {
                exportBtn.setDisable(false);
            }
        }
    }

    private void renderAnalysisTable(AnalysisResult result) {
        previewTable.getItems().clear();
        previewTable.getColumns().clear();

        // Get all regions
        List<String> regions = new ArrayList<>(result.getRegionData().keySet());
        Collections.sort(regions);

        // Add "Total" as last row
        regions.add("Total");

        // Create columns
        // 1. Region column (fixed)
        TableColumn<Map<String, String>, String> regionCol = new TableColumn<>("Region");
        regionCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().get("Region")));
        regionCol.setPrefWidth(150);
        regionCol.setStyle("-fx-font-weight: bold; -fx-background-color: #f8f9fa;");
        regionCol.setSortable(false);

        // 2. Foundation Programmes section
        TableColumn<Map<String, String>, String> foundationHeader = new TableColumn<>("Foundation Programmes");
        foundationHeader.setStyle("-fx-background-color: #0072B2; -fx-text-fill: white; -fx-font-weight: bold; -fx-alignment: CENTER;");

        // Applications sub-columns
        TableColumn<Map<String, String>, String> foundationApps = new TableColumn<>("Applications");
        foundationApps.setStyle("-fx-background-color: #E8F4FD; -fx-alignment: CENTER;");

        TableColumn<Map<String, String>, String> foundationApps2025 = new TableColumn<>("2025");
        foundationApps2025.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getOrDefault("Foundation_Apps_2025", "0")));
        foundationApps2025.setStyle("-fx-alignment: CENTER;");

        TableColumn<Map<String, String>, String> foundationApps2026 = new TableColumn<>("2026");
        foundationApps2026.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getOrDefault("Foundation_Apps_2026", "0")));
        foundationApps2026.setStyle("-fx-alignment: CENTER;");

        foundationApps.getColumns().addAll(foundationApps2025, foundationApps2026);

        // Offers sub-columns
        TableColumn<Map<String, String>, String> foundationOffers = new TableColumn<>("Offers");
        foundationOffers.setStyle("-fx-background-color: #FFF3CD; -fx-alignment: CENTER;");

        TableColumn<Map<String, String>, String> foundationOffers2025 = new TableColumn<>("2025");
        foundationOffers2025.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getOrDefault("Foundation_Offers_2025", "0")));
        foundationOffers2025.setStyle("-fx-alignment: CENTER;");

        TableColumn<Map<String, String>, String> foundationOffers2026 = new TableColumn<>("2026");
        foundationOffers2026.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getOrDefault("Foundation_Offers_2026", "0")));
        foundationOffers2026.setStyle("-fx-alignment: CENTER;");

        foundationOffers.getColumns().addAll(foundationOffers2025, foundationOffers2026);

        // Acceptances sub-columns
        TableColumn<Map<String, String>, String> foundationAccept = new TableColumn<>("Acceptances");
        foundationAccept.setStyle("-fx-background-color: #F8D7DA; -fx-alignment: CENTER;");

        TableColumn<Map<String, String>, String> foundationAccept2025 = new TableColumn<>("2025");
        foundationAccept2025.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getOrDefault("Foundation_Accept_2025", "0")));
        foundationAccept2025.setStyle("-fx-alignment: CENTER;");

        TableColumn<Map<String, String>, String> foundationAccept2026 = new TableColumn<>("2026");
        foundationAccept2026.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getOrDefault("Foundation_Accept_2026", "0")));
        foundationAccept2026.setStyle("-fx-alignment: CENTER;");

        foundationAccept.getColumns().addAll(foundationAccept2025, foundationAccept2026);

        foundationHeader.getColumns().addAll(foundationApps, foundationOffers, foundationAccept);

        // 3. UG & PG Programmes section
        TableColumn<Map<String, String>, String> ugpgHeader = new TableColumn<>("UG & PG Programmes");
        ugpgHeader.setStyle("-fx-background-color: #28A745; -fx-text-fill: white; -fx-font-weight: bold; -fx-alignment: CENTER;");

        // Applications sub-columns
        TableColumn<Map<String, String>, String> ugpgApps = new TableColumn<>("Applications");
        ugpgApps.setStyle("-fx-background-color: #E8F4FD; -fx-alignment: CENTER;");

        TableColumn<Map<String, String>, String> ugpgApps2025 = new TableColumn<>("2025");
        ugpgApps2025.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getOrDefault("UGPG_Apps_2025", "0")));
        ugpgApps2025.setStyle("-fx-alignment: CENTER;");

        TableColumn<Map<String, String>, String> ugpgApps2026 = new TableColumn<>("2026");
        ugpgApps2026.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getOrDefault("UGPG_Apps_2026", "0")));
        ugpgApps2026.setStyle("-fx-alignment: CENTER;");

        ugpgApps.getColumns().addAll(ugpgApps2025, ugpgApps2026);

        // Offers sub-columns
        TableColumn<Map<String, String>, String> ugpgOffers = new TableColumn<>("Offers");
        ugpgOffers.setStyle("-fx-background-color: #FFF3CD; -fx-alignment: CENTER;");

        TableColumn<Map<String, String>, String> ugpgOffers2025 = new TableColumn<>("2025");
        ugpgOffers2025.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getOrDefault("UGPG_Offers_2025", "0")));
        ugpgOffers2025.setStyle("-fx-alignment: CENTER;");

        TableColumn<Map<String, String>, String> ugpgOffers2026 = new TableColumn<>("2026");
        ugpgOffers2026.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getOrDefault("UGPG_Offers_2026", "0")));
        ugpgOffers2026.setStyle("-fx-alignment: CENTER;");

        ugpgOffers.getColumns().addAll(ugpgOffers2025, ugpgOffers2026);

        // Acceptances sub-columns
        TableColumn<Map<String, String>, String> ugpgAccept = new TableColumn<>("Acceptances");
        ugpgAccept.setStyle("-fx-background-color: #F8D7DA; -fx-alignment: CENTER;");

        TableColumn<Map<String, String>, String> ugpgAccept2025 = new TableColumn<>("2025");
        ugpgAccept2025.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getOrDefault("UGPG_Accept_2025", "0")));
        ugpgAccept2025.setStyle("-fx-alignment: CENTER;");

        TableColumn<Map<String, String>, String> ugpgAccept2026 = new TableColumn<>("2026");
        ugpgAccept2026.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getOrDefault("UGPG_Accept_2026", "0")));
        ugpgAccept2026.setStyle("-fx-alignment: CENTER;");

        ugpgAccept.getColumns().addAll(ugpgAccept2025, ugpgAccept2026);

        ugpgHeader.getColumns().addAll(ugpgApps, ugpgOffers, ugpgAccept);

        // Add all columns to table
        previewTable.getColumns().add(regionCol);
        previewTable.getColumns().add(foundationHeader);
        previewTable.getColumns().add(ugpgHeader);

        // Build data rows
        ObservableList<Map<String, String>> tableData = FXCollections.observableArrayList();

        for (String region : regions) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("Region", region);

            if ("Total".equals(region)) {
                // Add totals
                row.put("Foundation_Apps_2025", String.valueOf(result.getTotalFoundationApps2025()));
                row.put("Foundation_Apps_2026", String.valueOf(result.getTotalFoundationApps2026()));
                row.put("Foundation_Offers_2025", String.valueOf(result.getTotalFoundationOffers2025()));
                row.put("Foundation_Offers_2026", String.valueOf(result.getTotalFoundationOffers2026()));
                row.put("Foundation_Accept_2025", String.valueOf(result.getTotalFoundationAccept2025()));
                row.put("Foundation_Accept_2026", String.valueOf(result.getTotalFoundationAccept2026()));
                row.put("UGPG_Apps_2025", String.valueOf(result.getTotalUGPGApps2025()));
                row.put("UGPG_Apps_2026", String.valueOf(result.getTotalUGPGApps2026()));
                row.put("UGPG_Offers_2025", String.valueOf(result.getTotalUGPGOffers2025()));
                row.put("UGPG_Offers_2026", String.valueOf(result.getTotalUGPGOffers2026()));
                row.put("UGPG_Accept_2025", String.valueOf(result.getTotalUGPGAccept2025()));
                row.put("UGPG_Accept_2026", String.valueOf(result.getTotalUGPGAccept2026()));
            } else {
                RegionData regionData = result.getRegionData().get(region);
                if (regionData != null) {
                    row.put("Foundation_Apps_2025", String.valueOf(regionData.getFoundationApps2025()));
                    row.put("Foundation_Apps_2026", String.valueOf(regionData.getFoundationApps2026()));
                    row.put("Foundation_Offers_2025", String.valueOf(regionData.getFoundationOffers2025()));
                    row.put("Foundation_Offers_2026", String.valueOf(regionData.getFoundationOffers2026()));
                    row.put("Foundation_Accept_2025", String.valueOf(regionData.getFoundationAccept2025()));
                    row.put("Foundation_Accept_2026", String.valueOf(regionData.getFoundationAccept2026()));
                    row.put("UGPG_Apps_2025", String.valueOf(regionData.getUgpgApps2025()));
                    row.put("UGPG_Apps_2026", String.valueOf(regionData.getUgpgApps2026()));
                    row.put("UGPG_Offers_2025", String.valueOf(regionData.getUgpgOffers2025()));
                    row.put("UGPG_Offers_2026", String.valueOf(regionData.getUgpgOffers2026()));
                    row.put("UGPG_Accept_2025", String.valueOf(regionData.getUgpgAccept2025()));
                    row.put("UGPG_Accept_2026", String.valueOf(regionData.getUgpgAccept2026()));
                }
            }

            tableData.add(row);
        }

        previewTable.setItems(tableData);

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
    }

    @FXML
    private void handleClose() {
        Stage stage = (Stage) mainContainer.getScene().getWindow();
        stage.close();
    }
}
package com.example.app.controller;

import com.example.app.service.RegionMassAdderService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.List;
import java.util.Map;

public class RegionMassAdderController {

    @FXML private ListView<File> fileListView;
    @FXML private TableView<Map<String, String>> previewTable;
    @FXML private Label statusLabel;
    @FXML private Label fileCountLabel;
    @FXML private Label processedCountLabel;
    @FXML private Button processFilesBtn;
    @FXML private Button addRegionBtn;
    @FXML private Button populateRegionBtn;
    @FXML private Button exportFilesBtn;
    @FXML private ComboBox<String> countryColumnSelector;

    private final RegionMassAdderService regionService = new RegionMassAdderService();
    private ObservableList<File> selectedFiles = FXCollections.observableArrayList();
    private List<RegionMassAdderService.FileProcessResult> processedResults;
    private RegionMassAdderService.FileProcessResult currentResult;
    private String selectedCountryColumn;

    @FXML
    public void initialize() {
        fileListView.setItems(selectedFiles);
        fileListView.setCellFactory(lv -> new ListCell<File>() {
            @Override
            protected void updateItem(File file, boolean empty) {
                super.updateItem(file, empty);
                if (empty || file == null) {
                    setText(null);
                } else {
                    setText(file.getName());
                    setGraphic(null);
                }
            }
        });

        fileListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && countryColumnSelector.getValue() != null) {
                processFilesBtn.setDisable(false);
            } else {
                processFilesBtn.setDisable(true);
            }
        });

        countryColumnSelector.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !fileListView.getSelectionModel().getSelectedItems().isEmpty()) {
                processFilesBtn.setDisable(false);
            } else {
                processFilesBtn.setDisable(true);
            }
        });

        processFilesBtn.setDisable(true);
        addRegionBtn.setDisable(true);
        populateRegionBtn.setDisable(true);
        exportFilesBtn.setDisable(true);

        updateCountLabels();
    }

    @FXML
    private void handleAddFiles() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx", "*.xls")
        );
        fileChooser.setTitle("Select Excel Files");

        List<File> files = fileChooser.showOpenMultipleDialog(null);
        if (files != null && !files.isEmpty()) {
            selectedFiles.addAll(files);
            updateCountLabels();
            statusLabel.setText("Added " + files.size() + " file(s). Select a Country column and click 'Process Files'.");
            statusLabel.setStyle("-fx-text-fill: #17a2b8;");

            detectCountryColumns(files.get(0));
        }
    }

    @FXML
    private void handleClearFiles() {
        selectedFiles.clear();
        processedResults = null;
        currentResult = null;
        previewTable.getItems().clear();
        previewTable.getColumns().clear();
        countryColumnSelector.getItems().clear();

        processFilesBtn.setDisable(true);
        addRegionBtn.setDisable(true);
        populateRegionBtn.setDisable(true);
        exportFilesBtn.setDisable(true);

        updateCountLabels();
        statusLabel.setText("Cleared all files.");
        statusLabel.setStyle("-fx-text-fill: #6c757d;");
    }

    @FXML
    private void handleRemoveSelectedFile() {
        File selected = fileListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            selectedFiles.remove(selected);
            updateCountLabels();
            if (selectedFiles.isEmpty()) {
                countryColumnSelector.getItems().clear();
                processFilesBtn.setDisable(true);
                addRegionBtn.setDisable(true);
                populateRegionBtn.setDisable(true);
                exportFilesBtn.setDisable(true);
                previewTable.getItems().clear();
                previewTable.getColumns().clear();
                currentResult = null;
            }
        }
    }

    @FXML
    private void handleProcessFiles() {
        File selectedFile = fileListView.getSelectionModel().getSelectedItem();
        if (selectedFile == null) {
            statusLabel.setText("Please select a file from the list.");
            statusLabel.setStyle("-fx-text-fill: #dc3545;");
            return;
        }

        String countryColumn = countryColumnSelector.getValue();
        if (countryColumn == null) {
            statusLabel.setText("Please select the Country column.");
            statusLabel.setStyle("-fx-text-fill: #dc3545;");
            return;
        }

        try {
            statusLabel.setText("Processing " + selectedFile.getName() + "...");
            statusLabel.setStyle("-fx-text-fill: #17a2b8;");
            processFilesBtn.setDisable(true);

            RegionMassAdderService.FileProcessResult result =
                    regionService.processSingleFile(selectedFile, countryColumn);

            if (processedResults == null) {
                processedResults = FXCollections.observableArrayList();
            }
            processedResults.add(result);
            currentResult = result;

            if (result.isSuccess()) {
                statusLabel.setText("✓ " + selectedFile.getName() + " processed successfully. Found " +
                        result.getRowCount() + " rows with Country column '" + countryColumn + "'.");
                statusLabel.setStyle("-fx-text-fill: #28a745;");

                displayPreview(result);

                addRegionBtn.setDisable(false);
                populateRegionBtn.setDisable(false);
                exportFilesBtn.setDisable(false);

                processedCountLabel.setText("Processed: " + processedResults.size());
            } else {
                statusLabel.setText("✗ " + selectedFile.getName() + " failed: " + result.getErrorMessage());
                statusLabel.setStyle("-fx-text-fill: #dc3545;");

                addRegionBtn.setDisable(true);
                populateRegionBtn.setDisable(true);
                exportFilesBtn.setDisable(true);
            }

        } catch (Exception e) {
            statusLabel.setText("Error processing file: " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill: #dc3545;");
            e.printStackTrace();
        } finally {
            processFilesBtn.setDisable(false);
        }
    }

    @FXML
    private void handleAddRegionColumn() {
        if (processedResults == null || processedResults.isEmpty()) {
            statusLabel.setText("No processed files to add Region column to.");
            statusLabel.setStyle("-fx-text-fill: #dc3545;");
            return;
        }

        try {
            File selectedFile = fileListView.getSelectionModel().getSelectedItem();
            if (selectedFile == null) {
                statusLabel.setText("Please select a file from the list.");
                statusLabel.setStyle("-fx-text-fill: #dc3545;");
                return;
            }

            RegionMassAdderService.FileProcessResult result = processedResults.stream()
                    .filter(r -> r.getFileName().equals(selectedFile.getName()))
                    .findFirst()
                    .orElse(null);

            if (result == null) {
                statusLabel.setText("No processed result found for selected file.");
                statusLabel.setStyle("-fx-text-fill: #dc3545;");
                return;
            }

            regionService.addRegionColumn(result);
            currentResult = result;

            // Clear and refresh preview
            previewTable.getItems().clear();
            previewTable.getColumns().clear();
            displayPreview(result);

            statusLabel.setText("✓ Region column added to " + selectedFile.getName() +
                    ". Use 'Populate Region Column' to fill with region data or 'Export Files' to save.");
            statusLabel.setStyle("-fx-text-fill: #28a745;");

            exportFilesBtn.setDisable(false);

        } catch (Exception e) {
            statusLabel.setText("Error adding Region column: " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill: #dc3545;");
            e.printStackTrace();
        }
    }

    @FXML
    private void handlePopulateRegionColumn() {
        if (processedResults == null || processedResults.isEmpty()) {
            statusLabel.setText("No processed files to populate Region column.");
            statusLabel.setStyle("-fx-text-fill: #dc3545;");
            return;
        }

        try {
            File selectedFile = fileListView.getSelectionModel().getSelectedItem();
            if (selectedFile == null) {
                statusLabel.setText("Please select a file from the list.");
                statusLabel.setStyle("-fx-text-fill: #dc3545;");
                return;
            }

            RegionMassAdderService.FileProcessResult result = processedResults.stream()
                    .filter(r -> r.getFileName().equals(selectedFile.getName()))
                    .findFirst()
                    .orElse(null);

            if (result == null) {
                statusLabel.setText("No processed result found for selected file.");
                statusLabel.setStyle("-fx-text-fill: #dc3545;");
                return;
            }

            boolean hasRegion = result.getHeaders().stream().anyMatch(h -> h.equals("Region"));
            if (!hasRegion) {
                statusLabel.setText("No Region column found. Click 'Add Region Column' first.");
                statusLabel.setStyle("-fx-text-fill: #ffc107;");
                return;
            }

            regionService.populateRegionColumn(result);
            currentResult = result;

            // Clear and refresh preview
            previewTable.getItems().clear();
            previewTable.getColumns().clear();
            displayPreview(result);

            statusLabel.setText("✓ Region column populated for " + selectedFile.getName() +
                    " using country mappings. Use 'Export Files' to save.");
            statusLabel.setStyle("-fx-text-fill: #28a745;");

            exportFilesBtn.setDisable(false);

        } catch (Exception e) {
            statusLabel.setText("Error populating Region column: " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill: #dc3545;");
            e.printStackTrace();
        }
    }

    @FXML
    private void handleExportFiles() {
        if (processedResults == null || processedResults.isEmpty()) {
            statusLabel.setText("No processed files to export.");
            statusLabel.setStyle("-fx-text-fill: #dc3545;");
            return;
        }

        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Export Directory");

        File desktop = new File(System.getProperty("user.home") + "/Desktop");
        if (desktop.exists()) {
            directoryChooser.setInitialDirectory(desktop);
        }

        File exportDir = directoryChooser.showDialog(null);

        if (exportDir == null) return;

        try {
            statusLabel.setText("Exporting files to: " + exportDir.getAbsolutePath());
            statusLabel.setStyle("-fx-text-fill: #17a2b8;");
            exportFilesBtn.setDisable(true);

            int exported = regionService.exportFiles(processedResults, exportDir);

            if (exported > 0) {
                statusLabel.setText("✓ Successfully exported " + exported + " files to " + exportDir.getAbsolutePath());
                statusLabel.setStyle("-fx-text-fill: #28a745;");
            } else {
                statusLabel.setText("⚠️ No files were exported. Check if any files were processed successfully.");
                statusLabel.setStyle("-fx-text-fill: #ffc107;");
            }

        } catch (Exception e) {
            statusLabel.setText("Error exporting files: " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill: #dc3545;");
            e.printStackTrace();
        } finally {
            exportFilesBtn.setDisable(false);
        }
    }

    private void detectCountryColumns(File file) {
        try {
            List<String> columns = regionService.getColumnHeaders(file);
            countryColumnSelector.getItems().clear();
            countryColumnSelector.getItems().addAll(columns);

            for (String col : columns) {
                if (col.equalsIgnoreCase("Country") || col.equalsIgnoreCase("country")) {
                    countryColumnSelector.getSelectionModel().select(col);
                    break;
                }
            }

            if (countryColumnSelector.getSelectionModel().getSelectedItem() == null && !columns.isEmpty()) {
                countryColumnSelector.getSelectionModel().select(0);
            }

            processFilesBtn.setDisable(false);

        } catch (Exception e) {
            statusLabel.setText("Error reading file: " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill: #dc3545;");
        }
    }

    private void displayPreview(RegionMassAdderService.FileProcessResult result) {
        if (result == null || !result.isSuccess()) {
            previewTable.getItems().clear();
            previewTable.getColumns().clear();
            return;
        }

        List<Map<String, String>> data = result.getPreviewData();
        if (data == null || data.isEmpty()) {
            previewTable.getItems().clear();
            previewTable.getColumns().clear();
            statusLabel.setText("No data to preview.");
            statusLabel.setStyle("-fx-text-fill: #ffc107;");
            return;
        }

        previewTable.getItems().clear();
        previewTable.getColumns().clear();

        List<String> headers = new java.util.ArrayList<>(data.get(0).keySet());

        for (String header : headers) {
            TableColumn<Map<String, String>, String> column = new TableColumn<>(header);
            column.setCellValueFactory(cellData ->
                    new SimpleStringProperty(cellData.getValue().getOrDefault(header, ""))
            );

            double columnWidth = Math.max(120, Math.min(200, header.length() * 10 + 40));
            column.setPrefWidth(columnWidth);
            column.setMinWidth(80);
            column.setResizable(true);

            previewTable.getColumns().add(column);
        }

        ObservableList<Map<String, String>> observableData = FXCollections.observableArrayList();
        observableData.addAll(data);
        previewTable.setItems(observableData);

        previewTable.refresh();
        previewTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    private void updateCountLabels() {
        fileCountLabel.setText("Files: " + selectedFiles.size());
        if (processedResults != null) {
            processedCountLabel.setText("Processed: " + processedResults.size());
        } else {
            processedCountLabel.setText("Processed: 0");
        }
    }

    @FXML
    private void handleClose() {
        Stage stage = (Stage) fileListView.getScene().getWindow();
        stage.close();
    }
}
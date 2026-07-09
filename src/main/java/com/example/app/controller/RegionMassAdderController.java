package com.example.app.controller;

import com.example.app.service.RegionMassAdderService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
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
    @FXML private Button exportFilesBtn;
    @FXML private ComboBox<String> countryColumnSelector;

    private final RegionMassAdderService regionService = new RegionMassAdderService();
    private ObservableList<File> selectedFiles = FXCollections.observableArrayList();
    private List<RegionMassAdderService.FileProcessResult> processedResults;
    private List<Map<String, String>> previewData;
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
                    // Show status indicator
                    setGraphic(null);
                }
            }
        });

        // Enable/disable buttons based on selection
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

        addRegionBtn.setDisable(true);
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

            // Try to detect country columns from first file
            detectCountryColumns(files.get(0));
        }
    }

    @FXML
    private void handleClearFiles() {
        selectedFiles.clear();
        processedResults = null;
        previewData = null;
        previewTable.getItems().clear();
        previewTable.getColumns().clear();
        countryColumnSelector.getItems().clear();
        addRegionBtn.setDisable(true);
        exportFilesBtn.setDisable(true);
        processFilesBtn.setDisable(true);
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
                addRegionBtn.setDisable(true);
                exportFilesBtn.setDisable(true);
                processFilesBtn.setDisable(true);
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

            // Process the selected file
            RegionMassAdderService.FileProcessResult result =
                    regionService.processSingleFile(selectedFile, countryColumn);

            // Store results
            if (processedResults == null) {
                processedResults = FXCollections.observableArrayList();
            }
            processedResults.add(result);

            // Update UI
            if (result.isSuccess()) {
                statusLabel.setText("✓ " + selectedFile.getName() + " processed successfully. Found " +
                        result.getRowCount() + " rows with Country column '" + countryColumn + "'.");
                statusLabel.setStyle("-fx-text-fill: #28a745;");

                // Show preview
                previewData = result.getPreviewData();
                displayPreview(previewData);

                addRegionBtn.setDisable(false);
                exportFilesBtn.setDisable(false);
                processedCountLabel.setText("Processed: " + processedResults.size());
            } else {
                statusLabel.setText("✗ " + selectedFile.getName() + " failed: " + result.getErrorMessage());
                statusLabel.setStyle("-fx-text-fill: #dc3545;");
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
        if (processedResults == null || processedResults.isEmpty() || previewData == null) {
            statusLabel.setText("No processed files to add Region column to.");
            statusLabel.setStyle("-fx-text-fill: #dc3545;");
            return;
        }

        try {
            // For the selected file, add the Region column
            File selectedFile = fileListView.getSelectionModel().getSelectedItem();
            if (selectedFile == null) {
                statusLabel.setText("Please select a file from the list.");
                statusLabel.setStyle("-fx-text-fill: #dc3545;");
                return;
            }

            // Find the result for this file
            RegionMassAdderService.FileProcessResult result = processedResults.stream()
                    .filter(r -> r.getFileName().equals(selectedFile.getName()))
                    .findFirst()
                    .orElse(null);

            if (result == null) {
                statusLabel.setText("No processed result found for selected file.");
                statusLabel.setStyle("-fx-text-fill: #dc3545;");
                return;
            }

            // Add the Region column
            regionService.addRegionColumn(result);

            // Refresh preview with Region column
            previewData = result.getPreviewData();
            displayPreview(previewData);

            statusLabel.setText("✓ Region column added to " + selectedFile.getName() +
                    ". Use 'Export Files' to save the updated files.");
            statusLabel.setStyle("-fx-text-fill: #28a745;");

            exportFilesBtn.setDisable(false);

        } catch (Exception e) {
            statusLabel.setText("Error adding Region column: " + e.getMessage());
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

        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialDirectory(new File(System.getProperty("user.home") + "/Desktop"));
        fileChooser.setTitle("Select Export Directory");
        File directory = fileChooser.showSaveDialog(null);

        if (directory == null) return;

        try {
            // Use the directory path (remove filename if user entered one)
            String exportPath = directory.getAbsolutePath();
            if (exportPath.endsWith(".xlsx") || exportPath.endsWith(".xls")) {
                exportPath = new File(exportPath).getParent();
            }
            File exportDir = new File(exportPath);
            if (!exportDir.exists()) {
                exportDir.mkdirs();
            }

            statusLabel.setText("Exporting files to: " + exportDir.getAbsolutePath());
            statusLabel.setStyle("-fx-text-fill: #17a2b8;");
            exportFilesBtn.setDisable(true);

            // Export all processed files
            int exported = regionService.exportFiles(processedResults, exportDir);

            statusLabel.setText("✓ Successfully exported " + exported + " files to " + exportDir.getAbsolutePath());
            statusLabel.setStyle("-fx-text-fill: #28a745;");

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

            // Auto-select if a country column is found
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

    private void displayPreview(List<Map<String, String>> data) {
        previewTable.getItems().clear();
        previewTable.getColumns().clear();

        if (data == null || data.isEmpty()) {
            return;
        }

        // Get all column headers from the first row
        List<String> headers = new java.util.ArrayList<>(data.get(0).keySet());

        for (String header : headers) {
            TableColumn<Map<String, String>, String> column = new TableColumn<>(header);
            // Fixed: Use SimpleStringProperty instead of SimpleObjectProperty
            column.setCellValueFactory(cellData ->
                    new SimpleStringProperty(cellData.getValue().getOrDefault(header, ""))
            );
            column.setPrefWidth(120);
            previewTable.getColumns().add(column);
        }

        previewTable.getItems().addAll(data);
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
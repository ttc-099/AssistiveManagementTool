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
    @FXML private Label validationLabel;
    @FXML private Label previewInfoLabel;
    @FXML private Label rowCountLabel;
    @FXML private Button processFilesBtn;
    @FXML private Button exportFilesBtn;
    @FXML private ComboBox<String> countryColumnSelector;
    @FXML private ComboBox<String> sheetSelector;

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
            // Show validation status when file is selected
            if (newVal != null) {
                validateFile(newVal);
            }
        });

        countryColumnSelector.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !fileListView.getSelectionModel().getSelectedItems().isEmpty()) {
                processFilesBtn.setDisable(false);
            } else {
                processFilesBtn.setDisable(true);
            }
        });

        sheetSelector.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && currentResult != null) {
                // Pass both the result and the selected sheet name
                displaySheetPreview(currentResult, newVal);
            }
        });

        processFilesBtn.setDisable(true);
        exportFilesBtn.setDisable(true);

        updateCountLabels();
        validationLabel.setText("No file selected");
        validationLabel.setStyle("-fx-text-fill: #6c757d;");

        // Set table to allow horizontal scrolling
        previewTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
    }

    private void validateFile(File file) {
        try {
            List<String> columns = regionService.getColumnHeaders(file);
            validationLabel.setText("✅ Valid Excel file - Found " + columns.size() + " columns");
            validationLabel.setStyle("-fx-text-fill: #28a745;");
        } catch (Exception e) {
            validationLabel.setText("❌ Invalid or corrupt file: " + e.getMessage());
            validationLabel.setStyle("-fx-text-fill: #dc3545;");
        }
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

            if (!files.isEmpty()) {
                detectCountryColumns(files.get(0));
                validateFile(files.get(0));
            }
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
        sheetSelector.getItems().clear();
        rowCountLabel.setText("");
        previewInfoLabel.setText("Select and process a file to preview data");

        processFilesBtn.setDisable(true);
        exportFilesBtn.setDisable(true);

        updateCountLabels();
        statusLabel.setText("Cleared all files.");
        statusLabel.setStyle("-fx-text-fill: #6c757d;");
        validationLabel.setText("No file selected");
        validationLabel.setStyle("-fx-text-fill: #6c757d;");
    }

    @FXML
    private void handleRemoveSelectedFile() {
        File selected = fileListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            selectedFiles.remove(selected);
            updateCountLabels();
            if (selectedFiles.isEmpty()) {
                countryColumnSelector.getItems().clear();
                sheetSelector.getItems().clear();
                processFilesBtn.setDisable(true);
                exportFilesBtn.setDisable(true);
                previewTable.getItems().clear();
                previewTable.getColumns().clear();
                currentResult = null;
                rowCountLabel.setText("");
                previewInfoLabel.setText("Select and process a file to preview data");
                validationLabel.setText("No file selected");
                validationLabel.setStyle("-fx-text-fill: #6c757d;");
            } else {
                // Validate next selected file
                File nextFile = fileListView.getSelectionModel().getSelectedItem();
                if (nextFile != null) {
                    validateFile(nextFile);
                }
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
                int totalRows = result.getRowCount();
                int previewRows = Math.min(100, totalRows);
                statusLabel.setText("✓ " + selectedFile.getName() + " processed successfully. " +
                        totalRows + " rows loaded across " + result.getSheetsData().size() + " sheets. " +
                        "Region column automatically added.");
                statusLabel.setStyle("-fx-text-fill: #28a745;");

                // Update sheet selector
                sheetSelector.getItems().clear();
                sheetSelector.getItems().addAll(result.getSheetsData().keySet());
                if (!sheetSelector.getItems().isEmpty()) {
                    sheetSelector.getSelectionModel().select(0);
                }

                previewInfoLabel.setText("Showing up to 100 rows per sheet (Region column auto-added)");

                // Display the first sheet's preview
                String firstSheet = sheetSelector.getValue();
                if (firstSheet != null) {
                    displaySheetPreview(result, firstSheet);
                }

                exportFilesBtn.setDisable(false);

                processedCountLabel.setText("Processed: " + processedResults.size());
            } else {
                statusLabel.setText("✗ " + selectedFile.getName() + " failed: " + result.getErrorMessage());
                statusLabel.setStyle("-fx-text-fill: #dc3545;");

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

    /**
     * Display preview for a specific sheet
     */
    private void displaySheetPreview(RegionMassAdderService.FileProcessResult result, String sheetName) {
        if (result == null || !result.isSuccess()) {
            previewTable.getItems().clear();
            previewTable.getColumns().clear();
            return;
        }

        // Get the specific sheet data
        RegionMassAdderService.SheetData sheetData = result.getSheetsData().get(sheetName);
        if (sheetData == null) {
            previewTable.getItems().clear();
            previewTable.getColumns().clear();
            statusLabel.setText("Sheet '" + sheetName + "' not found.");
            statusLabel.setStyle("-fx-text-fill: #ffc107;");
            return;
        }

        List<Map<String, String>> data = sheetData.getPreviewData();
        if (data == null || data.isEmpty()) {
            previewTable.getItems().clear();
            previewTable.getColumns().clear();
            rowCountLabel.setText("0 rows");
            return;
        }

        previewTable.getItems().clear();
        previewTable.getColumns().clear();

        List<String> headers = new java.util.ArrayList<>(sheetData.getHeaders());

        // Calculate total width and column sizes based on content
        for (String header : headers) {
            TableColumn<Map<String, String>, String> column = new TableColumn<>(header);
            column.setCellValueFactory(cellData ->
                    new SimpleStringProperty(cellData.getValue().getOrDefault(header, ""))
            );

            // Calculate column width based on content
            int maxLength = header.length();
            for (Map<String, String> row : data) {
                String value = row.getOrDefault(header, "");
                if (value != null && value.length() > maxLength) {
                    maxLength = value.length();
                }
            }

            // Width = 8 pixels per character + padding (min 120, max 350)
            double columnWidth = Math.min(Math.max(120, maxLength * 8 + 40), 350);
            column.setPrefWidth(columnWidth);
            column.setMinWidth(80);
            column.setResizable(true);

            previewTable.getColumns().add(column);
        }

        ObservableList<Map<String, String>> observableData = FXCollections.observableArrayList();
        observableData.addAll(data);
        previewTable.setItems(observableData);

        previewTable.refresh();

        // Ensure UNCONSTRAINED_RESIZE_POLICY is set for horizontal scrolling
        previewTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        // Update row count label
        rowCountLabel.setText(sheetData.getFullData().size() + " rows (showing " + data.size() + ")");
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
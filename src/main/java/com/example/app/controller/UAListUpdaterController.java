package com.example.app.controller;

import com.example.app.service.addTools.UALists.UAListUpdaterService;
import com.example.app.service.addTools.UALists.UAListUpdaterService.UAUpdateResult;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class UAListUpdaterController {

    @FXML private VBox initialPopupContainer;
    @FXML private Button makeNewBtn;
    @FXML private Button updateExistingBtn;
    @FXML private VBox mainContainer;
    @FXML private Label statusLabel;
    @FXML private Button generateBtn;
    @FXML private Button exportBtn;
    @FXML private Label mainDbLabel;
    @FXML private Label uaListLabel;
    @FXML private TableView<Map<String, String>> previewTable;
    @FXML private ComboBox<String> intakeSelector;
    @FXML private TextField customIntakeField;
    @FXML private ListView<String> newAdditionsList;
    @FXML private Label totalNewLabel;
    @FXML private Label totalExistingLabel;
    @FXML private Label totalOverallLabel;

    // REMOVED: enableLLMCheckbox, llmProgressBar, llmStatusLabel, LocalLLMService

    private File mainDatabaseFile;
    private File existingUAListFile;
    private UAUpdateResult updateResult;
    private final UAListUpdaterService service = new UAListUpdaterService();
    private List<Map<String, String>> loadedUAListData = null;
    private List<String> loadedUAListHeaders = null;

    @FXML
    public void initialize() {
        intakeSelector.setItems(FXCollections.observableArrayList(
                "April 2026",
                "July 2026",
                "September 2026",
                "Custom..."
        ));
        intakeSelector.getSelectionModel().selectFirst();

        intakeSelector.valueProperty().addListener((obs, oldVal, newVal) -> {
            if ("Custom...".equals(newVal)) {
                customIntakeField.setVisible(true);
                customIntakeField.setManaged(true);
            } else {
                customIntakeField.setVisible(false);
                customIntakeField.setManaged(false);
                if (newVal != null) {
                    customIntakeField.setText(newVal);
                }
            }

            if (mainDatabaseFile != null && existingUAListFile != null && newVal != null) {
                handleGenerate();
            }
        });

        customIntakeField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (mainDatabaseFile != null && existingUAListFile != null &&
                    customIntakeField.isVisible() && !newVal.trim().isEmpty()) {
                handleGenerate();
            }
        });

        customIntakeField.setVisible(false);
        customIntakeField.setManaged(false);
        customIntakeField.setPromptText("e.g., July 2026");

        mainContainer.setVisible(false);
        mainContainer.setManaged(false);
        generateBtn.setDisable(true);
        exportBtn.setDisable(true);

        statusLabel.setText("Select an option to begin.");
        statusLabel.setStyle("-fx-text-fill: #6c757d;");
    }

    @FXML
    private void handleMakeNew() {
        showInfo("Coming Soon", "Make New functionality will be available in a future update.");
    }

    @FXML
    private void handleUpdateExisting() {
        initialPopupContainer.setVisible(false);
        initialPopupContainer.setManaged(false);
        mainContainer.setVisible(true);
        mainContainer.setManaged(true);
        statusLabel.setText("Load the Main Database and existing UA List files.");
        statusLabel.setStyle("-fx-text-fill: #17a2b8;");
    }

    @FXML
    private void handleLoadMainDb() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx", "*.xls")
        );
        fileChooser.setTitle("Select Main Database");

        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            mainDatabaseFile = file;
            mainDbLabel.setText("✓ " + file.getName());
            mainDbLabel.setStyle("-fx-text-fill: #28a745;");

            if (existingUAListFile != null) {
                handleGenerate();
            } else {
                statusLabel.setText("Main Database loaded. Load UA List to generate updates.");
                statusLabel.setStyle("-fx-text-fill: #17a2b8;");
            }
            checkFilesLoaded();
        }
    }

    @FXML
    private void handleLoadUAList() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx", "*.xls")
        );
        fileChooser.setTitle("Select Existing UA List");

        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            existingUAListFile = file;
            uaListLabel.setText("✓ " + file.getName());
            uaListLabel.setStyle("-fx-text-fill: #28a745;");

            try {
                loadedUAListData = service.loadExcelFile(file);
                loadedUAListHeaders = service.getHeaders(file);
                displayUAListPreview(loadedUAListData, loadedUAListHeaders);

                if (mainDatabaseFile == null) {
                    statusLabel.setText("UA List loaded. Load Main Database to generate updates.");
                    statusLabel.setStyle("-fx-text-fill: #17a2b8;");
                } else {
                    handleGenerate();
                }
            } catch (Exception e) {
                statusLabel.setText("Error loading UA List: " + e.getMessage());
                statusLabel.setStyle("-fx-text-fill: #dc3545;");
                System.err.println("Error loading UA List: " + e.getMessage());
                e.printStackTrace(System.err);
            }

            checkFilesLoaded();
        }
    }

    private void checkFilesLoaded() {
        if (mainDatabaseFile != null && existingUAListFile != null) {
            generateBtn.setDisable(false);
        }
    }

    @FXML
    private void handleGenerate() {
        if (mainDatabaseFile == null || existingUAListFile == null) {
            if (existingUAListFile != null && loadedUAListData != null) {
                displayUAListPreview(loadedUAListData, loadedUAListHeaders);
                statusLabel.setText("Load Main Database to generate updates.");
                statusLabel.setStyle("-fx-text-fill: #17a2b8;");
            }
            return;
        }

        String intake = getSelectedIntake();
        if (intake == null || intake.isEmpty()) {
            statusLabel.setText("Please select or enter an intake.");
            statusLabel.setStyle("-fx-text-fill: #dc3545;");
            return;
        }

        try {
            statusLabel.setText("Generating UA List for " + intake + "...");
            statusLabel.setStyle("-fx-text-fill: #17a2b8;");
            generateBtn.setDisable(true);
            exportBtn.setDisable(true);

            updateResult = service.processFiles(mainDatabaseFile, existingUAListFile, intake);

            displayPreview(updateResult);
            displayNewAdditions(updateResult);

            totalNewLabel.setText("New: " + updateResult.getTotalNew());
            totalExistingLabel.setText("Existing: " + updateResult.getTotalExisting());
            totalOverallLabel.setText("Total: " + updateResult.getAllUAStudents().size());

            if (updateResult.getTotalNew() == 0) {
                statusLabel.setText("✓ No new students found for " + intake + ". UA List is up to date.");
                statusLabel.setStyle("-fx-text-fill: #28a745;");
            } else {
                statusLabel.setText("✓ Found " + updateResult.getTotalNew() + " new student(s). Ready for export.");
                statusLabel.setStyle("-fx-text-fill: #28a745;");
            }

            exportBtn.setDisable(false);
            generateBtn.setDisable(false);

        } catch (Exception e) {
            statusLabel.setText("Error: " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill: #dc3545;");
            System.err.println("Error during generation: " + e.getMessage());
            e.printStackTrace(System.err);

            if (loadedUAListData != null) {
                displayUAListPreview(loadedUAListData, loadedUAListHeaders);
            }
            generateBtn.setDisable(false);
        }
    }

    @FXML
    private void handleExport() {
        if (updateResult == null) {
            statusLabel.setText("No data to export. Generate UA List first.");
            statusLabel.setStyle("-fx-text-fill: #dc3545;");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx")
        );
        String intakeForFileName = updateResult.getIntake().replace(" ", "_").replace("/", "-");
        fileChooser.setInitialFileName("UA_List_" + intakeForFileName + ".xlsx");

        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            try {
                statusLabel.setText("Exporting...");
                statusLabel.setStyle("-fx-text-fill: #17a2b8;");
                exportBtn.setDisable(true);

                service.exportUAList(updateResult, file);

                statusLabel.setText("✓ Exported successfully to: " + file.getName());
                statusLabel.setStyle("-fx-text-fill: #28a745;");

            } catch (Exception e) {
                statusLabel.setText("Error exporting: " + e.getMessage());
                statusLabel.setStyle("-fx-text-fill: #dc3545;");
                System.err.println("Error exporting: " + e.getMessage());
                e.printStackTrace(System.err);
            } finally {
                exportBtn.setDisable(false);
            }
        }
    }

    private String getSelectedIntake() {
        String selected = intakeSelector.getValue();
        if ("Custom...".equals(selected)) {
            return customIntakeField.getText().trim();
        }
        return selected;
    }

    private void displayUAListPreview(List<Map<String, String>> data, List<String> headers) {
        previewTable.getItems().clear();
        previewTable.getColumns().clear();

        if (data == null || data.isEmpty()) {
            previewTable.setPlaceholder(new Label("No data found in UA List."));
            return;
        }

        List<String> displayHeaders = Arrays.asList(
                "No", "Student ID", "Surname", "Given Name",
                "NRIC / Passport No", "Nationality", "Country", "Intake", "Decision"
        );

        boolean hasColumns = false;
        for (String header : displayHeaders) {
            if (headers != null && headers.contains(header)) {
                TableColumn<Map<String, String>, String> column = new TableColumn<>(header);
                column.setCellValueFactory(cellData ->
                        new SimpleStringProperty(cellData.getValue().getOrDefault(header, ""))
                );
                column.setPrefWidth(120);
                previewTable.getColumns().add(column);
                hasColumns = true;
            }
        }

        if (!hasColumns && headers != null) {
            for (String header : headers) {
                TableColumn<Map<String, String>, String> column = new TableColumn<>(header);
                column.setCellValueFactory(cellData ->
                        new SimpleStringProperty(cellData.getValue().getOrDefault(header, ""))
                );
                column.setPrefWidth(120);
                previewTable.getColumns().add(column);
            }
        }

        ObservableList<Map<String, String>> observableData = FXCollections.observableArrayList();
        observableData.addAll(data);
        previewTable.setItems(observableData);
    }

    private void displayPreview(UAUpdateResult result) {
        List<Map<String, String>> data = result.getAllUAStudents();

        if (data == null || data.isEmpty()) {
            if (loadedUAListData != null && !loadedUAListData.isEmpty()) {
                displayUAListPreview(loadedUAListData, loadedUAListHeaders);
                statusLabel.setText("No UA students found for this intake. Showing existing UA List.");
                statusLabel.setStyle("-fx-text-fill: #ffc107;");
                return;
            } else {
                previewTable.getItems().clear();
                previewTable.getColumns().clear();
                previewTable.setPlaceholder(new Label("No UA students found for this intake."));
                return;
            }
        }

        previewTable.getItems().clear();
        previewTable.getColumns().clear();

        List<String> headers = result.getHeaders();

        List<String> displayHeaders = Arrays.asList(
                "No", "Student ID", "Surname", "Given Name",
                "NRIC / Passport No", "Nationality", "Country", "Intake", "Decision"
        );

        for (String header : displayHeaders) {
            if (headers.contains(header)) {
                TableColumn<Map<String, String>, String> column = new TableColumn<>(header);
                column.setCellValueFactory(cellData ->
                        new SimpleStringProperty(cellData.getValue().getOrDefault(header, ""))
                );
                column.setPrefWidth(120);
                previewTable.getColumns().add(column);
            }
        }

        ObservableList<Map<String, String>> observableData = FXCollections.observableArrayList();
        observableData.addAll(data);
        previewTable.setItems(observableData);
        previewTable.refresh();
    }

    private void displayNewAdditions(UAUpdateResult result) {
        newAdditionsList.getItems().clear();

        List<String> additions = result.getNewAdditions();
        if (additions.isEmpty()) {
            newAdditionsList.getItems().add("✅ No new additions found. UA List is up to date!");
        } else {
            newAdditionsList.getItems().addAll(additions);
        }
    }

    @FXML
    private void handleClose() {
        Stage stage = (Stage) initialPopupContainer.getScene().getWindow();
        stage.close();
    }

    private void showInfo(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
package com.example.app.controller;

import com.example.app.service.addTools.countries.CountryAnalyserService;
import com.example.app.service.addTools.countries.CountryAnalyserService.CountryAnalysisResult;
import com.example.app.service.addTools.countries.CountryAnalyserService.CountryCount;
import com.example.app.service.addTools.countries.CountryAnalyserService.CourseBreakdown;
import com.example.app.service.addTools.countries.CountryAnalyserService.CourseCount;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.util.Map;

public class CountryAnalyserController {

    private static final String DEBUG_PREFIX = "🔍 [CountryAnalyserController]";

    @FXML private Label statusLabel;
    @FXML private Label selectedFileLabel;
    @FXML private Button analyseBtn;
    @FXML private Button exportBtn;
    @FXML private Button clearBtn;
    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel;

    // FY Data UI
    @FXML private TableView<CountryCount> fyTable;
    @FXML private TableColumn<CountryCount, String> fyRankCol;
    @FXML private TableColumn<CountryCount, String> fyCountryCol;
    @FXML private TableColumn<CountryCount, String> fyCountCol;
    @FXML private Label fyTotalLabel;
    @FXML private Label fyUniqueLabel;

    // UGPG Data UI
    @FXML private TableView<CountryCount> ugpgTable;
    @FXML private TableColumn<CountryCount, String> ugpgRankCol;
    @FXML private TableColumn<CountryCount, String> ugpgCountryCol;
    @FXML private TableColumn<CountryCount, String> ugpgCountCol;
    @FXML private Label ugpgTotalLabel;
    @FXML private Label ugpgUniqueLabel;

    // Instructions label
    @FXML private Label instructionLabel;

    private final CountryAnalyserService service = new CountryAnalyserService();
    private File loadedFile;
    private Map<String, CountryAnalysisResult> analysisResults;

    @FXML
    public void initialize() {
        System.out.println(DEBUG_PREFIX + " ========================================");
        System.out.println(DEBUG_PREFIX + " INITIALIZING CountryAnalyserController");
        System.out.println(DEBUG_PREFIX + " ========================================");

        // Setup FY table
        fyRankCol.setCellValueFactory(data ->
                new SimpleStringProperty(String.valueOf(fyTable.getItems().indexOf(data.getValue()) + 1)));
        fyCountryCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getCountry()));
        fyCountCol.setCellValueFactory(data ->
                new SimpleStringProperty(String.valueOf(data.getValue().getCount())));

        // Setup UGPG table
        ugpgRankCol.setCellValueFactory(data ->
                new SimpleStringProperty(String.valueOf(ugpgTable.getItems().indexOf(data.getValue()) + 1)));
        ugpgCountryCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getCountry()));
        ugpgCountCol.setCellValueFactory(data ->
                new SimpleStringProperty(String.valueOf(data.getValue().getCount())));

        // ===== ADD CLICK HANDLERS FOR COUNTRY DRILL-DOWN =====
        fyTable.setRowFactory(tv -> {
            TableRow<CountryCount> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    CountryCount country = row.getItem();
                    System.out.println(DEBUG_PREFIX + " 🔄 Double-click on FY country: " + country.getCountry());
                    handleCountryDrillDown(country.getCountry(), "FY data");
                }
            });
            row.setOnMouseEntered(e -> {
                if (!row.isEmpty()) {
                    row.setStyle("-fx-cursor: hand; -fx-background-color: #e8f0fe;");
                }
            });
            row.setOnMouseExited(e -> {
                if (!row.isEmpty()) {
                    row.setStyle("");
                }
            });
            return row;
        });

        ugpgTable.setRowFactory(tv -> {
            TableRow<CountryCount> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    CountryCount country = row.getItem();
                    System.out.println(DEBUG_PREFIX + " 🔄 Double-click on UGPG country: " + country.getCountry());
                    handleCountryDrillDown(country.getCountry(), "UGPG data");
                }
            });
            row.setOnMouseEntered(e -> {
                if (!row.isEmpty()) {
                    row.setStyle("-fx-cursor: hand; -fx-background-color: #e8f0fe;");
                }
            });
            row.setOnMouseExited(e -> {
                if (!row.isEmpty()) {
                    row.setStyle("");
                }
            });
            return row;
        });

        analyseBtn.setDisable(true);
        exportBtn.setDisable(true);
        progressBar.setVisible(false);
        progressLabel.setVisible(false);

        instructionLabel.setText("💡 Double-click a country to see course breakdown");
        instructionLabel.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 11px;");
        instructionLabel.setVisible(false);

        statusLabel.setText("Load an Excel file to begin.");
        statusLabel.setStyle("-fx-text-fill: #6c757d;");

        System.out.println(DEBUG_PREFIX + " Initialization complete.");
        System.out.println(DEBUG_PREFIX + " ========================================");
    }

    // ===== DRILL-DOWN HANDLER WITH COMPREHENSIVE DEBUG =====
    private void handleCountryDrillDown(String country, String sheetName) {
        System.out.println(DEBUG_PREFIX + " ========================================");
        System.out.println(DEBUG_PREFIX + " handleCountryDrillDown: STARTING");
        System.out.println(DEBUG_PREFIX + " handleCountryDrillDown: Country = '" + country + "'");
        System.out.println(DEBUG_PREFIX + " handleCountryDrillDown: SheetName = '" + sheetName + "'");
        System.out.println(DEBUG_PREFIX + " ========================================");

        // Check if analysis results exist
        if (analysisResults == null) {
            System.err.println(DEBUG_PREFIX + " ❌ ERROR: analysisResults is NULL!");
            statusLabel.setText("No analysis results available. Run analysis first.");
            statusLabel.setStyle("-fx-text-fill: #dc3545;");
            return;
        }

        System.out.println(DEBUG_PREFIX + " analysisResults keys: " + analysisResults.keySet());

        if (!analysisResults.containsKey(sheetName)) {
            System.err.println(DEBUG_PREFIX + " ❌ ERROR: Sheet '" + sheetName + "' not found in results!");
            System.err.println(DEBUG_PREFIX + "    Available sheets: " + analysisResults.keySet());
            statusLabel.setText("No data available for " + sheetName);
            statusLabel.setStyle("-fx-text-fill: #dc3545;");
            return;
        }

        CountryAnalysisResult result = analysisResults.get(sheetName);
        System.out.println(DEBUG_PREFIX + " ✓ Found result for sheet: " + sheetName);
        System.out.println(DEBUG_PREFIX + "    Total rows in result: " + result.getTotalRows());
        System.out.println(DEBUG_PREFIX + "    Unique countries: " + result.getUniqueCountries());
        System.out.println(DEBUG_PREFIX + "    All rows count: " + result.getAllRows().size());

        // DEBUG: Show first 5 rows from the result
        System.out.println(DEBUG_PREFIX + " --- FIRST 5 ROWS FROM RESULT ---");
        int rowCount = 0;
        for (Map<String, String> row : result.getAllRows()) {
            if (rowCount++ >= 5) break;
            System.out.println(DEBUG_PREFIX + " Row " + rowCount + ":");
            for (Map.Entry<String, String> entry : row.entrySet()) {
                System.out.println(DEBUG_PREFIX + "   " + entry.getKey() + " = '" + entry.getValue() + "'");
            }
        }

        // DEBUG: Show all column headers available in the rows
        System.out.println(DEBUG_PREFIX + " --- ALL COLUMN HEADERS ---");
        if (!result.getAllRows().isEmpty()) {
            Map<String, String> sampleRow = result.getAllRows().get(0);
            System.out.println(DEBUG_PREFIX + " Columns found: " + sampleRow.keySet());
            for (String col : sampleRow.keySet()) {
                System.out.println(DEBUG_PREFIX + "   - '" + col + "'");
            }
        } else {
            System.err.println(DEBUG_PREFIX + " ❌ ERROR: No rows in result!");
            statusLabel.setText("No data rows found for " + sheetName);
            statusLabel.setStyle("-fx-text-fill: #dc3545;");
            return;
        }

        // Get course breakdown
        System.out.println(DEBUG_PREFIX + " --- GETTING COURSE BREAKDOWN ---");
        CourseBreakdown breakdown = service.getCourseBreakdown(result, country);
        System.out.println(DEBUG_PREFIX + " Breakdown returned:");
        System.out.println(DEBUG_PREFIX + "   Country: " + breakdown.getCountry());
        System.out.println(DEBUG_PREFIX + "   SheetName: " + breakdown.getSheetName());
        System.out.println(DEBUG_PREFIX + "   TotalStudents: " + breakdown.getTotalStudents());
        System.out.println(DEBUG_PREFIX + "   CourseCounts size: " + breakdown.getCourseCounts().size());

        if (breakdown.getCourseCounts().isEmpty()) {
            System.err.println(DEBUG_PREFIX + " ⚠️ WARNING: No course counts found!");
            System.err.println(DEBUG_PREFIX + "    This means no 'Course Code' or 'Course' columns were found.");
            System.err.println(DEBUG_PREFIX + "    Available columns: " +
                    (result.getAllRows().isEmpty() ? "NO ROWS" : result.getAllRows().get(0).keySet()));
        } else {
            System.out.println(DEBUG_PREFIX + " ✓ Found " + breakdown.getCourseCounts().size() + " courses:");
            for (int i = 0; i < Math.min(5, breakdown.getCourseCounts().size()); i++) {
                CourseCount cc = breakdown.getCourseCounts().get(i);
                System.out.println(DEBUG_PREFIX + "   " + (i+1) + ". " + cc.getCourseCode() +
                        " - " + cc.getCourseName() + " (" + cc.getCount() + " students)");
            }
            if (breakdown.getCourseCounts().size() > 5) {
                System.out.println(DEBUG_PREFIX + "   ... and " + (breakdown.getCourseCounts().size() - 5) + " more");
            }
        }

        if (breakdown.getTotalStudents() == 0) {
            System.err.println(DEBUG_PREFIX + " ⚠️ WARNING: Total students is 0 for country: " + country);
            statusLabel.setText("No course data found for " + country + " in " + sheetName);
            statusLabel.setStyle("-fx-text-fill: #ffc107;");
            return;
        }

        // Open the drill-down popup
        System.out.println(DEBUG_PREFIX + " --- OPENING DRILL-DOWN POPUP ---");
        try {
            // Try multiple path variations for the FXML
            String[] paths = {
                    "/com/example/app/view/CountryDrillDownView.fxml",
                    "/CountryDrillDownView.fxml",
                    "com/example/app/view/CountryDrillDownView.fxml"
            };

            FXMLLoader loader = null;
            java.net.URL foundResource = null;

            for (String path : paths) {
                java.net.URL resource = getClass().getResource(path);
                if (resource != null) {
                    System.out.println(DEBUG_PREFIX + " ✓ Found FXML at: " + path);
                    foundResource = resource;
                    loader = new FXMLLoader(resource);
                    break;
                }
            }

            if (loader == null) {
                // Try with classloader
                java.net.URL resource = getClass().getClassLoader().getResource("com/example/app/view/CountryDrillDownView.fxml");
                if (resource != null) {
                    System.out.println(DEBUG_PREFIX + " ✓ Found FXML via classloader: com/example/app/view/CountryDrillDownView.fxml");
                    foundResource = resource;
                    loader = new FXMLLoader(resource);
                }
            }

            if (loader == null) {
                System.err.println(DEBUG_PREFIX + " ❌ ERROR: Could not find CountryDrillDownView.fxml");
                System.err.println(DEBUG_PREFIX + "    Tried paths: " + String.join(", ", paths));
                statusLabel.setText("Error: Could not find drill-down view file.");
                statusLabel.setStyle("-fx-text-fill: #dc3545;");
                return;
            }

            System.out.println(DEBUG_PREFIX + " ✓ Loading FXML...");
            Parent root = loader.load();
            System.out.println(DEBUG_PREFIX + " ✓ FXML loaded successfully");

            CountryDrillDownController controller = loader.getController();
            if (controller == null) {
                System.err.println(DEBUG_PREFIX + " ❌ ERROR: Controller is NULL after loading FXML!");
                statusLabel.setText("Error: Could not initialize drill-down view.");
                statusLabel.setStyle("-fx-text-fill: #dc3545;");
                return;
            }

            System.out.println(DEBUG_PREFIX + " ✓ Controller obtained: " + controller.getClass().getSimpleName());
            controller.setBreakdown(breakdown);
            System.out.println(DEBUG_PREFIX + " ✓ Breakdown set in controller");

            Stage stage = new Stage();
            stage.setTitle("📚 Course Breakdown - " + country + " (" + sheetName + ")");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root, 700, 500));

            System.out.println(DEBUG_PREFIX + " ✓ Showing drill-down popup...");
            stage.showAndWait();
            System.out.println(DEBUG_PREFIX + " ✓ Drill-down popup closed");

        } catch (Exception e) {
            System.err.println(DEBUG_PREFIX + " ❌ ERROR opening drill-down: " + e.getMessage());
            e.printStackTrace();
            statusLabel.setText("Error opening drill-down: " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill: #dc3545;");
        }

        System.out.println(DEBUG_PREFIX + " handleCountryDrillDown: COMPLETE");
        System.out.println(DEBUG_PREFIX + " ========================================");
    }

    @FXML
    private void handleLoadFile() {
        System.out.println(DEBUG_PREFIX + " handleLoadFile: User clicked Load File");
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx", "*.xls")
        );
        fileChooser.setTitle("Select Excel File");

        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            loadedFile = file;
            selectedFileLabel.setText(file.getName());
            selectedFileLabel.setStyle("-fx-text-fill: #28a745;");
            analyseBtn.setDisable(false);
            statusLabel.setText("File loaded. Click 'Analyse' to process.");
            statusLabel.setStyle("-fx-text-fill: #17a2b8;");
            System.out.println(DEBUG_PREFIX + " ✓ File loaded: " + file.getAbsolutePath());
            System.out.println(DEBUG_PREFIX + "    File size: " + file.length() + " bytes");
        } else {
            System.out.println(DEBUG_PREFIX + " User cancelled file selection");
        }
    }

    @FXML
    private void handleAnalyse() {
        System.out.println(DEBUG_PREFIX + " ========================================");
        System.out.println(DEBUG_PREFIX + " handleAnalyse: STARTING ANALYSIS");
        System.out.println(DEBUG_PREFIX + " ========================================");

        if (loadedFile == null) {
            System.err.println(DEBUG_PREFIX + " ❌ ERROR: No file loaded!");
            statusLabel.setText("Please load a file first.");
            statusLabel.setStyle("-fx-text-fill: #dc3545;");
            return;
        }

        System.out.println(DEBUG_PREFIX + " Analysing file: " + loadedFile.getAbsolutePath());

        try {
            statusLabel.setText("Analysing: " + loadedFile.getName() + "...");
            statusLabel.setStyle("-fx-text-fill: #17a2b8;");
            analyseBtn.setDisable(true);
            exportBtn.setDisable(true);
            progressBar.setVisible(true);
            progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
            progressLabel.setVisible(true);
            progressLabel.setText("Processing...");

            new Thread(() -> {
                try {
                    System.out.println(DEBUG_PREFIX + " Calling service.analyseFile()...");
                    long startTime = System.currentTimeMillis();
                    analysisResults = service.analyseFile(loadedFile);
                    long endTime = System.currentTimeMillis();
                    System.out.println(DEBUG_PREFIX + " ✓ Analysis completed in " + (endTime - startTime) + "ms");

                    System.out.println(DEBUG_PREFIX + " Results summary:");
                    for (Map.Entry<String, CountryAnalysisResult> entry : analysisResults.entrySet()) {
                        CountryAnalysisResult r = entry.getValue();
                        System.out.println(DEBUG_PREFIX + "   " + entry.getKey() + ": " +
                                r.getUniqueCountries() + " countries, " + r.getTotalRows() + " rows");
                    }

                    javafx.application.Platform.runLater(() -> {
                        displayResults(analysisResults);
                        statusLabel.setText("✓ Analysis complete! Double-click a country to see course breakdown.");
                        statusLabel.setStyle("-fx-text-fill: #28a745;");
                        exportBtn.setDisable(false);
                        progressBar.setVisible(false);
                        progressLabel.setVisible(false);
                        instructionLabel.setVisible(true);
                        System.out.println(DEBUG_PREFIX + " ✓ UI updated with results");
                    });
                } catch (Exception e) {
                    System.err.println(DEBUG_PREFIX + " ❌ ERROR during analysis: " + e.getMessage());
                    e.printStackTrace();
                    javafx.application.Platform.runLater(() -> {
                        statusLabel.setText("Error: " + e.getMessage());
                        statusLabel.setStyle("-fx-text-fill: #dc3545;");
                        progressBar.setVisible(false);
                        progressLabel.setVisible(false);
                    });
                } finally {
                    javafx.application.Platform.runLater(() -> {
                        analyseBtn.setDisable(false);
                    });
                }
            }).start();

        } catch (Exception e) {
            System.err.println(DEBUG_PREFIX + " ❌ ERROR in handleAnalyse: " + e.getMessage());
            e.printStackTrace();
            statusLabel.setText("Error: " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill: #dc3545;");
            analyseBtn.setDisable(false);
            exportBtn.setDisable(true);
            progressBar.setVisible(false);
            progressLabel.setVisible(false);
        }
    }

    private void displayResults(Map<String, CountryAnalysisResult> results) {
        System.out.println(DEBUG_PREFIX + " displayResults: Updating UI tables");
        fyTable.getItems().clear();
        ugpgTable.getItems().clear();

        CountryAnalysisResult fyResult = results.get("FY data");
        if (fyResult != null) {
            ObservableList<CountryCount> fyData = FXCollections.observableArrayList();
            fyData.addAll(fyResult.getTop10());
            fyTable.setItems(fyData);
            fyTotalLabel.setText("Total rows: " + fyResult.getTotalRows());
            fyUniqueLabel.setText("Unique countries: " + fyResult.getUniqueCountries());
            System.out.println(DEBUG_PREFIX + " ✓ FY Data: " + fyData.size() + " countries displayed");
        } else {
            fyTotalLabel.setText("No FY data found");
            fyUniqueLabel.setText("");
            System.out.println(DEBUG_PREFIX + " ⚠️ No FY data found");
        }

        CountryAnalysisResult ugpgResult = results.get("UGPG data");
        if (ugpgResult != null) {
            ObservableList<CountryCount> ugpgData = FXCollections.observableArrayList();
            ugpgData.addAll(ugpgResult.getTop10());
            ugpgTable.setItems(ugpgData);
            ugpgTotalLabel.setText("Total rows: " + ugpgResult.getTotalRows());
            ugpgUniqueLabel.setText("Unique countries: " + ugpgResult.getUniqueCountries());
            System.out.println(DEBUG_PREFIX + " ✓ UGPG Data: " + ugpgData.size() + " countries displayed");
        } else {
            ugpgTotalLabel.setText("No UGPG data found");
            ugpgUniqueLabel.setText("");
            System.out.println(DEBUG_PREFIX + " ⚠️ No UGPG data found");
        }
    }

    @FXML
    private void handleExport() {
        System.out.println(DEBUG_PREFIX + " handleExport: User clicked Export");
        if (analysisResults == null || analysisResults.isEmpty()) {
            statusLabel.setText("No data to export. Run analysis first.");
            statusLabel.setStyle("-fx-text-fill: #dc3545;");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx")
        );
        fileChooser.setInitialFileName("Country_Analysis_Summary.xlsx");

        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            try {
                statusLabel.setText("Exporting summary...");
                statusLabel.setStyle("-fx-text-fill: #17a2b8;");
                exportBtn.setDisable(true);

                // ===== FIX: Call exportTop10Summary instead =====
                service.exportTop10Summary(analysisResults, file);

                statusLabel.setText("✓ Exported top 10 summary to: " + file.getName());
                statusLabel.setStyle("-fx-text-fill: #28a745;");
                System.out.println(DEBUG_PREFIX + " ✓ Export completed: " + file.getAbsolutePath());

            } catch (Exception e) {
                System.err.println(DEBUG_PREFIX + " ❌ Export error: " + e.getMessage());
                e.printStackTrace();
                statusLabel.setText("Error exporting: " + e.getMessage());
                statusLabel.setStyle("-fx-text-fill: #dc3545;");
            } finally {
                exportBtn.setDisable(false);
            }
        }
    }

    @FXML
    private void handleClear() {
        System.out.println(DEBUG_PREFIX + " handleClear: Clearing all data");
        loadedFile = null;
        analysisResults = null;
        selectedFileLabel.setText("None");
        selectedFileLabel.setStyle("-fx-text-fill: #6c757d;");
        fyTable.getItems().clear();
        ugpgTable.getItems().clear();
        fyTotalLabel.setText("");
        fyUniqueLabel.setText("");
        ugpgTotalLabel.setText("");
        ugpgUniqueLabel.setText("");
        analyseBtn.setDisable(true);
        exportBtn.setDisable(true);
        progressBar.setVisible(false);
        progressLabel.setVisible(false);
        instructionLabel.setVisible(false);
        statusLabel.setText("Cleared. Load a file to begin.");
        statusLabel.setStyle("-fx-text-fill: #6c757d;");
        System.out.println(DEBUG_PREFIX + " ✓ Clear complete");
    }

    @FXML
    private void handleClose() {
        System.out.println(DEBUG_PREFIX + " handleClose: Closing window");
        Stage stage = (Stage) statusLabel.getScene().getWindow();
        stage.close();
    }

    @FXML
    private void handleExportFull() {
        System.out.println(DEBUG_PREFIX + " handleExportFull: User clicked Export Full");

        if (analysisResults == null || analysisResults.isEmpty()) {
            statusLabel.setText("No data to export. Run analysis first.");
            statusLabel.setStyle("-fx-text-fill: #dc3545;");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx")
        );
        fileChooser.setInitialFileName("Country_Analysis_Full.xlsx");

        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            try {
                statusLabel.setText("Exporting full report...");
                statusLabel.setStyle("-fx-text-fill: #17a2b8;");
                exportBtn.setDisable(true);

                service.exportFullReport(analysisResults, file);

                statusLabel.setText("✓ Full report exported to: " + file.getName());
                statusLabel.setStyle("-fx-text-fill: #28a745;");
                System.out.println(DEBUG_PREFIX + " ✓ Full export completed: " + file.getAbsolutePath());

            } catch (Exception e) {
                System.err.println(DEBUG_PREFIX + " ❌ Export error: " + e.getMessage());
                e.printStackTrace();
                statusLabel.setText("Error exporting: " + e.getMessage());
                statusLabel.setStyle("-fx-text-fill: #dc3545;");
            } finally {
                exportBtn.setDisable(false);
            }
        }


    }
}
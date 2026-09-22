package com.example.app.controller;

import com.example.app.service.aiUsages.QwenAgentService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class AiChatController implements Initializable {

    @FXML private Label modelStatusLabel;
    @FXML private VBox chatContainer;
    @FXML private Button cleanDataBtn;
    @FXML private Button cleanColumnBtn;
    @FXML private Button terminateBtn;        // ← NEW
    @FXML private Label tokenCountLabel;
    @FXML private ProgressBar aiProgressBar;
    @FXML private Label aiStatusLabel;
    @FXML private Button clearChatBtn;
    @FXML private ComboBox<String> columnSelector;
    @FXML private ComboBox<Integer> batchSizeSelector;
    @FXML private Label columnTypeLabel;

    private final QwenAgentService qwenService = new QwenAgentService();
    private int tokenCount = 0;
    private boolean isProcessing = false;
    private DataController dataController;

    // ===== TERMINATE CONTROL =====
    private AtomicBoolean stopRequested = new AtomicBoolean(false);
    private CompletableFuture<QwenAgentService.CleanColumnResult> currentFuture = null;

    // Inner class for chat messages
    public static class ChatMessage {
        private final String sender;
        private final String content;
        private final LocalDateTime timestamp;

        public ChatMessage(String sender, String content) {
            this.sender = sender;
            this.content = content;
            this.timestamp = LocalDateTime.now();
        }

        public String getSender() { return sender; }
        public String getContent() { return content; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupUI();
        updateModelStatus();
        loadModelInBackground();

        // Set default batch size
        batchSizeSelector.getSelectionModel().select(2); // 20

        // Column selector listener
        columnSelector.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && dataController != null) {
                detectColumnType(newVal);
            }
        });
    }

    private void setupUI() {
        aiStatusLabel.setText("Ready");
        aiProgressBar.setVisible(false);
        tokenCountLabel.setText("Tokens: 0");
        chatContainer.setStyle("-fx-padding: 10; -fx-spacing: 10;");
        cleanDataBtn.setDisable(true);
        cleanColumnBtn.setDisable(true);
        if (terminateBtn != null) {
            terminateBtn.setDisable(true);
            terminateBtn.setVisible(false);
        }
        columnTypeLabel.setText("Type: Unknown");
    }

    private void loadModelInBackground() {
        new Thread(() -> {
            try {
                if (!qwenService.isAvailable()) {
                    Platform.runLater(() -> {
                        modelStatusLabel.setText("● Loading model...");
                        modelStatusLabel.setStyle("-fx-text-fill: #ffc107;");
                        aiStatusLabel.setText("Loading Qwen model...");
                    });

                    Thread.sleep(2000);
                    Platform.runLater(() -> updateModelStatus());
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    modelStatusLabel.setText("● Error loading model");
                    modelStatusLabel.setStyle("-fx-text-fill: #dc3545;");
                    aiStatusLabel.setText("Error: " + e.getMessage());
                });
            }
        }).start();
    }

    private void updateModelStatus() {
        if (qwenService.isAvailable()) {
            modelStatusLabel.setText("● Online");
            modelStatusLabel.setStyle("-fx-text-fill: #28a745;");
            cleanDataBtn.setDisable(false);
            cleanColumnBtn.setDisable(false);

            if (qwenService.hasCachedData()) {
                aiStatusLabel.setText("Ready - SQLite: " + qwenService.getTrackedFileName());
                populateColumns();
            } else {
                aiStatusLabel.setText("Ready - Load data first");
                cleanDataBtn.setDisable(true);
                cleanColumnBtn.setDisable(true);
            }
        } else {
            modelStatusLabel.setText("● Offline");
            modelStatusLabel.setStyle("-fx-text-fill: #dc3545;");
            cleanDataBtn.setDisable(true);
            cleanColumnBtn.setDisable(true);
            aiStatusLabel.setText("⚠️ Qwen model not available");
        }
    }

    private void populateColumns() {
        if (dataController == null || dataController.getMultiSheetModel() == null) return;

        String currentSheet = dataController.getCurrentSheet();
        if (currentSheet == null) return;

        var sheetData = dataController.getMultiSheetModel().getSheet(currentSheet);
        if (sheetData == null) return;

        columnSelector.getItems().clear();
        columnSelector.getItems().addAll(sheetData.getHeaders());

        if (!columnSelector.getItems().isEmpty()) {
            columnSelector.getSelectionModel().select(0);
        }
    }

    private void detectColumnType(String columnName) {
        if (dataController == null || dataController.getMultiSheetModel() == null) return;

        String currentSheet = dataController.getCurrentSheet();
        if (currentSheet == null) return;

        var sheetData = dataController.getMultiSheetModel().getSheet(currentSheet);
        if (sheetData == null) return;

        List<String> samples = new ArrayList<>();
        int count = 0;
        for (Map<String, String> row : sheetData.getRows()) {
            if (count++ >= 10) break;
            String value = row.get(columnName);
            if (value != null && !value.isEmpty()) {
                samples.add(value);
            }
        }

        QwenAgentService.ColumnType type = qwenService.detectColumnType(columnName, samples);
        columnTypeLabel.setText("Type: " + type.name());
    }

    // ===== TERMINATE METHOD =====
    @FXML
    private void handleTerminate() {
        if (!isProcessing) {
            addSystemMessage("ℹ️ No process is running.", "#ffc107");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Terminate Process");
        confirm.setHeaderText("⚠️ Are you sure you want to terminate the cleaning process?");
        confirm.setContentText("This will stop the current cleaning operation. Partial changes may have been applied.");
        confirm.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            stopRequested.set(true);
            if (currentFuture != null) {
                currentFuture.cancel(true);
            }
            addSystemMessage("⏹️ Terminating process...", "#dc3545");
            if (terminateBtn != null) {
                terminateBtn.setDisable(true);
                terminateBtn.setText("⏹️ Stopping...");
            }
            aiStatusLabel.setText("⏹️ Terminating...");
        }
    }

    // ===== UI HELPER METHODS =====
    private void addSystemMessage(String content, String bgColor) {
        HBox wrapper = new HBox();
        wrapper.setAlignment(Pos.CENTER);
        wrapper.setPadding(new Insets(5, 0, 5, 0));

        Label label = new Label(content);
        label.setStyle("-fx-background-color: " + bgColor + "; -fx-background-radius: 15; -fx-padding: 6 15 6 15; -fx-text-fill: white; -fx-font-size: 11;");
        wrapper.getChildren().add(label);
        chatContainer.getChildren().add(wrapper);
        scrollToBottom();
    }

    private void addAssistantMessage(String content) {
        HBox wrapper = new HBox();
        wrapper.setAlignment(Pos.CENTER_LEFT);
        wrapper.setPadding(new Insets(5, 10, 5, 10));
        wrapper.setMaxWidth(Double.MAX_VALUE);

        VBox messageBox = new VBox(4);
        messageBox.setMaxWidth(chatContainer.getWidth() * 0.85);
        messageBox.setPrefWidth(chatContainer.getWidth() * 0.85);
        messageBox.setMinWidth(100);
        messageBox.setStyle("-fx-background-color: white; -fx-background-radius: 15 15 15 5; -fx-padding: 12; -fx-border-color: #dee2e6; -fx-border-radius: 15;");

        Label label = new Label(content);
        label.setWrapText(true);
        label.setStyle("-fx-text-fill: #212529; -fx-font-size: 13; -fx-line-spacing: 2;");
        label.setMaxWidth(Double.MAX_VALUE);
        label.setMinWidth(1);
        label.setPrefWidth(messageBox.getPrefWidth() - 24);
        messageBox.getChildren().add(label);

        Label timeLabel = new Label("Qwen • " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        timeLabel.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 9;");
        messageBox.getChildren().add(timeLabel);

        wrapper.getChildren().add(messageBox);
        chatContainer.getChildren().add(wrapper);
        scrollToBottom();
    }

    private void addResultMessage(String summary, int changes) {
        HBox wrapper = new HBox();
        wrapper.setAlignment(Pos.CENTER_LEFT);
        wrapper.setPadding(new Insets(5, 10, 5, 10));
        wrapper.setMaxWidth(Double.MAX_VALUE);

        VBox messageBox = new VBox(8);
        messageBox.setMaxWidth(chatContainer.getWidth() * 0.85);
        messageBox.setPrefWidth(chatContainer.getWidth() * 0.85);
        messageBox.setMinWidth(200);
        messageBox.setStyle("-fx-background-color: #e8f5e9; -fx-background-radius: 15 15 15 5; -fx-padding: 15; -fx-border-color: #a5d6a7; -fx-border-radius: 15;");

        Label summaryLabel = new Label(summary);
        summaryLabel.setWrapText(true);
        summaryLabel.setStyle("-fx-text-fill: #1b5e20; -fx-font-size: 13; -fx-line-spacing: 2; -fx-font-weight: bold;");
        summaryLabel.setMaxWidth(Double.MAX_VALUE);
        messageBox.getChildren().add(summaryLabel);

        if (changes > 0) {
            Label changesLabel = new Label("✅ " + changes + " changes made");
            changesLabel.setStyle("-fx-text-fill: #2e7d32; -fx-font-size: 12;");
            messageBox.getChildren().add(changesLabel);
        } else {
            Label noChangesLabel = new Label("✅ No changes needed - data is already clean!");
            noChangesLabel.setStyle("-fx-text-fill: #2e7d32; -fx-font-size: 12;");
            messageBox.getChildren().add(noChangesLabel);
        }

        Label timeLabel = new Label("Qwen • " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        timeLabel.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 9;");
        messageBox.getChildren().add(timeLabel);

        wrapper.getChildren().add(messageBox);
        chatContainer.getChildren().add(wrapper);
        scrollToBottom();
    }

    private void addChangeDetails(List<QwenAgentService.RowChange> changes) {
        if (changes == null || changes.isEmpty()) return;

        HBox wrapper = new HBox();
        wrapper.setAlignment(Pos.CENTER_LEFT);
        wrapper.setPadding(new Insets(5, 10, 5, 10));
        wrapper.setMaxWidth(Double.MAX_VALUE);

        VBox messageBox = new VBox(4);
        messageBox.setMaxWidth(chatContainer.getWidth() * 0.85);
        messageBox.setPrefWidth(chatContainer.getWidth() * 0.85);
        messageBox.setMinWidth(200);
        messageBox.setStyle("-fx-background-color: #fff3cd; -fx-background-radius: 15 15 15 5; -fx-padding: 12; -fx-border-color: #ffc107; -fx-border-radius: 15;");

        Label titleLabel = new Label("📋 Changes made:");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #856404; -fx-font-size: 12;");
        messageBox.getChildren().add(titleLabel);

        int count = 0;
        int total = changes.size();
        int maxDisplay = 20;

        for (QwenAgentService.RowChange change : changes) {
            if (count++ >= maxDisplay) {
                Label moreLabel = new Label("... and " + (total - maxDisplay) + " more changes");
                moreLabel.setStyle("-fx-text-fill: #856404; -fx-font-size: 11; -fx-font-style: italic;");
                messageBox.getChildren().add(moreLabel);
                break;
            }
            Label changeLabel = new Label("  Row " + change.getRowId() + ": \"" +
                    truncate(change.getOriginalValue(), 30) + "\" → \"" +
                    truncate(change.getCleanedValue(), 30) + "\"");
            changeLabel.setStyle("-fx-text-fill: #856404; -fx-font-size: 11;");
            changeLabel.setWrapText(true);
            messageBox.getChildren().add(changeLabel);
        }

        wrapper.getChildren().add(messageBox);
        chatContainer.getChildren().add(wrapper);
        scrollToBottom();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return "";
        if (value.length() <= maxLength) return value;
        return value.substring(0, maxLength) + "...";
    }

    private void scrollToBottom() {
        Platform.runLater(() -> {
            if (chatContainer.getParent() instanceof ScrollPane scrollPane) {
                scrollPane.setVvalue(1.0);
            }
        });
    }

    // ===== RESET UI STATE =====
    private void resetUIState() {
        isProcessing = false;
        stopRequested.set(false);
        currentFuture = null;
        cleanColumnBtn.setDisable(false);
        cleanColumnBtn.setText("🧹 Clean Column");
        cleanDataBtn.setDisable(false);
        if (terminateBtn != null) {
            terminateBtn.setDisable(true);
            terminateBtn.setVisible(false);
            terminateBtn.setText("⏹️ Stop");
        }
        aiProgressBar.setVisible(false);
        aiStatusLabel.setText("Ready");
    }

    // ===== PUBLIC SETTERS =====
    public void setDataController(DataController dataController) {
        this.dataController = dataController;
        if (qwenService.isAvailable() && qwenService.hasCachedData()) {
            populateColumns();
        }
    }

    public void setMainController(MainController mainController) {
        if (mainController != null) {
            this.dataController = mainController.getDataController();
        }
    }

    public void notifyFileLoaded(String fileName, List<Map<String, String>> dataRows) {
        if (dataRows != null && !dataRows.isEmpty()) {
            qwenService.ingestDatasetSnapshot(fileName, dataRows);
            Platform.runLater(() -> {
                String message = "📂 Data loaded: " + fileName + " (" + dataRows.size() + " rows)\n\nReady to clean!";
                addAssistantMessage(message);
                updateModelStatus();
                cleanDataBtn.setDisable(false);
                cleanColumnBtn.setDisable(false);
                populateColumns();
            });
        }
    }

    public void updateDatabaseStatus(String fileName, Map<String, Integer> sheetRowCounts) {
        Platform.runLater(() -> {
            int totalRows = sheetRowCounts.values().stream().mapToInt(Integer::intValue).sum();
            String message = "📊 Data loaded to SQLite!\n\n" +
                    "📁 File: " + fileName + "\n" +
                    "📋 Sheets: " + sheetRowCounts.size() + "\n" +
                    "📈 Total rows: " + totalRows + "\n\n" +
                    "Select a column and click 'Clean Column'!";

            chatContainer.getChildren().clear();
            addAssistantMessage(message);
            updateModelStatus();

            aiStatusLabel.setText("Ready - SQLite: " + fileName);
            modelStatusLabel.setText("● SQLite Active");
            modelStatusLabel.setStyle("-fx-text-fill: #17a2b8;");
            cleanDataBtn.setDisable(false);
            cleanColumnBtn.setDisable(false);
            populateColumns();
        });
    }

    // ===== CLEAN COLUMN =====
    @FXML
    private void handleCleanColumn() {
        if (isProcessing) {
            addSystemMessage("⏳ Please wait for the current cleaning to finish.", "#ffc107");
            return;
        }

        String selectedColumn = columnSelector.getValue();
        if (selectedColumn == null || selectedColumn.isEmpty()) {
            addSystemMessage("❌ Please select a column to clean.", "#dc3545");
            return;
        }

        if (dataController == null || dataController.getMultiSheetModel() == null) {
            addSystemMessage("❌ No data loaded. Please load an Excel file first.", "#dc3545");
            return;
        }

        if (!qwenService.isAvailable()) {
            addSystemMessage("❌ Qwen model not available. Please check the model file.", "#dc3545");
            return;
        }

        String currentSheet = dataController.getCurrentSheet();
        if (currentSheet == null) {
            addSystemMessage("❌ No sheet selected.", "#dc3545");
            return;
        }

        int batchSize = batchSizeSelector.getValue() != null ? batchSizeSelector.getValue() : 20;
        String tableName = sanitizeTableName(currentSheet);

        addSystemMessage("🧹 Starting column cleaning: " + selectedColumn, "#28a745");
        addSystemMessage("📊 Table: " + currentSheet + ", Batch size: " + batchSize, "#17a2b8");

        // Reset stop flag
        stopRequested.set(false);
        isProcessing = true;
        cleanColumnBtn.setDisable(true);
        cleanColumnBtn.setText("⏳ Cleaning...");
        cleanDataBtn.setDisable(true);
        if (terminateBtn != null) {
            terminateBtn.setDisable(false);
            terminateBtn.setVisible(true);
            terminateBtn.setText("⏹️ Stop");
        }
        aiProgressBar.setVisible(true);
        aiProgressBar.setProgress(0);
        aiStatusLabel.setText("Starting...");

        currentFuture = qwenService.cleanColumn(
                tableName,
                selectedColumn,
                batchSize,
                (progress, message) -> {
                    Platform.runLater(() -> {
                        // Check if stop was requested
                        if (stopRequested.get()) {
                            aiStatusLabel.setText("⏹️ Stopping...");
                            return;
                        }
                        aiProgressBar.setProgress(progress);
                        aiStatusLabel.setText(message);
                    });
                }
        );

        currentFuture.thenAcceptAsync(result -> {
            Platform.runLater(() -> {
                // Check if stopped
                if (stopRequested.get()) {
                    addSystemMessage("⏹️ Process stopped by user. Partial changes applied.", "#dc3545");
                    if (!result.getChanges().isEmpty()) {
                        addChangeDetails(result.getChanges());
                        addResultMessage("⚠️ Partial cleaning: " + result.getRowsChanged() + " changes applied before stop.",
                                result.getRowsChanged());
                    }
                    resetUIState();
                    return;
                }

                isProcessing = false;
                cleanColumnBtn.setDisable(false);
                cleanColumnBtn.setText("🧹 Clean Column");
                cleanDataBtn.setDisable(false);
                if (terminateBtn != null) {
                    terminateBtn.setDisable(true);
                    terminateBtn.setVisible(false);
                }
                aiProgressBar.setVisible(false);
                aiStatusLabel.setText("Ready");

                tokenCount += result.getTotalRows() / 2;
                tokenCountLabel.setText("Tokens: ~" + tokenCount);

                if (result.hasChanges()) {
                    addResultMessage("✅ Column cleaning complete! " +
                                    result.getRowsChanged() + " rows changed.",
                            result.getRowsChanged());

                    // ===== SHOW INDIVIDUAL CHANGES =====
                    addChangeDetails(result.getChanges());

                    // Refresh the table
                    if (dataController != null) {
                        dataController.refreshData();
                    }
                } else {
                    addResultMessage("✅ No changes needed - column is already clean!", 0);
                }
                currentFuture = null;
            });
        }).exceptionally(throwable -> {
            Platform.runLater(() -> {
                if (stopRequested.get()) {
                    addSystemMessage("⏹️ Process stopped by user.", "#dc3545");
                } else {
                    addSystemMessage("❌ Error during cleaning: " + throwable.getMessage(), "#dc3545");
                }
                resetUIState();
                currentFuture = null;
            });
            return null;
        });
    }

    // ===== CLEAN ALL ROWS =====
    @FXML
    private void handleCleanData() {
        if (isProcessing) {
            addSystemMessage("⏳ Please wait for the current cleaning to finish.", "#ffc107");
            return;
        }

        if (dataController == null || dataController.getFilteredData() == null ||
                dataController.getFilteredData().isEmpty()) {
            addSystemMessage("❌ No data loaded. Please load an Excel file first.", "#dc3545");
            return;
        }

        if (!qwenService.isAvailable()) {
            addSystemMessage("❌ Qwen model not available. Please check the model file.", "#dc3545");
            return;
        }

        addSystemMessage("🧹 Starting AI data cleaning (row-by-row)...", "#28a745");

        // Reset stop flag
        stopRequested.set(false);
        isProcessing = true;
        cleanDataBtn.setDisable(true);
        cleanDataBtn.setText("⏳ Cleaning...");
        cleanColumnBtn.setDisable(true);
        if (terminateBtn != null) {
            terminateBtn.setDisable(false);
            terminateBtn.setVisible(true);
            terminateBtn.setText("⏹️ Stop");
        }
        aiProgressBar.setVisible(true);
        aiProgressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        aiStatusLabel.setText("Cleaning data...");

        var masterData = dataController.getFilteredData();
        List<Map<String, String>> dataList = new ArrayList<>(masterData);

        // Use CompletableFuture with stop checking
        CompletableFuture.supplyAsync(() -> {
            int totalRows = dataList.size();
            int changesMade = 0;

            for (int i = 0; i < totalRows; i++) {
                // Check if stop requested
                if (stopRequested.get() || Thread.currentThread().isInterrupted()) {
                    int finalI1 = i;
                    Platform.runLater(() -> {
                        addSystemMessage("⏹️ Stopped at row " + (finalI1 + 1) + "/" + totalRows, "#dc3545");
                    });
                    break;
                }

                Map<String, String> rowCopy = new HashMap<>(dataList.get(i));
                try {
                    Map<String, String> corrections = qwenService.suggestCorrections(rowCopy).get();
                    if (corrections != null && !corrections.isEmpty()) {
                        int rowChanges = corrections.size();
                        changesMade += rowChanges;
                        int finalI = i;
                        int finalChangesMade = changesMade;
                        Platform.runLater(() -> {
                            masterData.get(finalI).putAll(corrections);
                            dataController.getDataTable().refresh();
                            aiStatusLabel.setText("Cleaning row " + (finalI + 1) + "/" + totalRows + " (" + finalChangesMade + " changes)");
                        });
                    }
                } catch (Exception e) {
                    System.err.println("❌ Error cleaning row " + i + ": " + e.getMessage());
                }
            }
            return changesMade;
        }).thenAcceptAsync(changesMade -> {
            Platform.runLater(() -> {
                boolean wasStopped = stopRequested.get();
                resetUIState();
                cleanDataBtn.setText("🧹 Clean All Rows");
                cleanColumnBtn.setDisable(false);

                tokenCount += dataList.size() / 2;
                tokenCountLabel.setText("Tokens: ~" + tokenCount);

                if (wasStopped) {
                    addSystemMessage("⏹️ Process stopped by user. " + changesMade + " changes applied.", "#dc3545");
                    if (changesMade > 0) {
                        addResultMessage("⚠️ Partial cleaning: " + changesMade + " changes applied.", changesMade);
                    }
                } else if (changesMade > 0) {
                    addResultMessage("✅ Row cleaning complete! " + changesMade + " changes made.", changesMade);
                } else {
                    addResultMessage("✅ Data is already clean!", 0);
                }
            });
        }).exceptionally(throwable -> {
            Platform.runLater(() -> {
                if (stopRequested.get()) {
                    addSystemMessage("⏹️ Process stopped by user.", "#dc3545");
                } else {
                    addSystemMessage("❌ Error during cleaning: " + throwable.getMessage(), "#dc3545");
                }
                resetUIState();
                cleanDataBtn.setText("🧹 Clean All Rows");
                cleanColumnBtn.setDisable(false);
            });
            return null;
        });
    }

    @FXML
    private void handleClearChat() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Clear Chat");
        confirm.setHeaderText("Clear all chat messages?");
        confirm.setContentText("This will remove all messages from the chat history.");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            chatContainer.getChildren().clear();
            if (qwenService.hasCachedData()) {
                addAssistantMessage("Chat cleared. Data is still loaded. Select a column and click 'Clean Column'!");
            } else {
                addAssistantMessage("Chat cleared. Load an Excel file to start cleaning!");
            }
            tokenCount = 0;
            tokenCountLabel.setText("Tokens: 0");
        }
    }

    private String sanitizeTableName(String name) {
        return name.trim().replaceAll("[^a-zA-Z0-9_]", "_").replaceAll("_+", "_");
    }

    public boolean hasCachedData() {
        return qwenService.hasCachedData();
    }

    public String getCachedFileName() {
        return qwenService.getTrackedFileName();
    }

    public int getCachedRowCount() {
        return qwenService.getTotalRowsCached();
    }
}
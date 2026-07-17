package com.example.app.controller;

import com.example.app.service.aiUsages.QwenAgentService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.PrintWriter;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class AiChatController implements Initializable {

    // FXML injected fields
    @FXML private Label modelStatusLabel;
    @FXML private VBox chatContainer;
    @FXML private TextArea chatInput;
    @FXML private Button sendChatBtn;
    @FXML private Label tokenCountLabel;
    @FXML private ProgressBar aiProgressBar;
    @FXML private Label aiStatusLabel;
    @FXML private Button clearChatBtn;
    @FXML private Button exportChatBtn;

    // Service and state
    private final QwenAgentService qwenService = new QwenAgentService();
    private int tokenCount = 0;
    private final List<ChatMessage> chatHistory = new ArrayList<>();
    private boolean isProcessing = false;

    // Reference to main controller for data access
    private MainController mainController;

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
        // Set up the chat UI
        setupChatUI();

        // Check model status
        updateModelStatus();

        // Add welcome message
        addWelcomeMessage();

        // Set up input handling
        setupInputHandling();

        // Start loading the model in background
        loadModelInBackground();
    }

    private void setupChatUI() {
        // Set placeholder text
        chatInput.setPromptText("Ask Qwen about your data... (e.g., 'Fix the Country column' or 'What regions have the most students?')");

        // Initial status
        aiStatusLabel.setText("Ready");
        aiProgressBar.setVisible(false);
        tokenCountLabel.setText("Tokens: 0");

        // Style the chat container
        chatContainer.setStyle("-fx-padding: 10;");
    }

    private void setupInputHandling() {
        chatInput.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER && !event.isShiftDown()) {
                event.consume();
                handleSendChatMessage();
            }
        });
    }

    private void loadModelInBackground() {
        new Thread(() -> {
            try {
                // Force model initialization if not already loaded
                if (!qwenService.isAvailable()) {
                    Platform.runLater(() -> {
                        modelStatusLabel.setText("● Loading model...");
                        modelStatusLabel.setStyle("-fx-text-fill: #ffc107;");
                        aiStatusLabel.setText("Loading Qwen model...");
                    });

                    // The model loads lazily on first use
                    // We'll just wait a bit and check again
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
            sendChatBtn.setDisable(false);
            aiStatusLabel.setText("Ready - Qwen model loaded");
        } else {
            modelStatusLabel.setText("● Offline");
            modelStatusLabel.setStyle("-fx-text-fill: #dc3545;");
            sendChatBtn.setDisable(true);
            aiStatusLabel.setText("⚠️ Qwen model not available - check file path");
        }
    }

    private void addWelcomeMessage() {
        String welcomeText = "👋 Hello! I'm Qwen, your AI data assistant.\n\n" +
                "I can help you with:\n" +
                "• 📍 Fix geographic data errors (Country/State mismatches)\n" +
                "• ✅ Validate and clean your data\n" +
                "• 📊 Summarize your dataset\n" +
                "• 🔍 Find anomalies and inconsistencies\n\n" +
                "Try asking me something like:\n" +
                "• \"Fix the Country column\"\n" +
                "• \"What regions have the most students?\"\n" +
                "• \"Validate all data\"\n\n" +
                "Or use the Quick Action buttons below! 🚀";

        addAssistantMessage(welcomeText);
    }

    private void addUserMessage(String content) {
        HBox messageBox = createMessageBox(content, "user");
        chatContainer.getChildren().add(messageBox);
        chatHistory.add(new ChatMessage("user", content));
        scrollToBottom();
    }

    private void addAssistantMessage(String content) {
        HBox messageBox = createMessageBox(content, "assistant");
        chatContainer.getChildren().add(messageBox);
        chatHistory.add(new ChatMessage("assistant", content));
        scrollToBottom();
    }

    private void addSystemMessage(String content, String bgColor) {
        HBox messageBox = createSystemMessageBox(content, bgColor);
        chatContainer.getChildren().add(messageBox);
        scrollToBottom();
    }

    private HBox createMessageBox(String content, String type) {
        HBox wrapper = new HBox();
        wrapper.setAlignment(type.equals("user") ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        wrapper.setPadding(new Insets(5, 10, 5, 10));
        wrapper.setMaxWidth(Double.MAX_VALUE);

        VBox messageBox = new VBox(4);
        // CRITICAL: Set both max and pref width to allow wrapping
        messageBox.setMaxWidth(chatContainer.getWidth() * 0.75);
        messageBox.setPrefWidth(chatContainer.getWidth() * 0.75);
        messageBox.setMinWidth(100);

        if (type.equals("user")) {
            messageBox.setStyle("-fx-background-color: #6f42c1; -fx-background-radius: 15 15 5 15; -fx-padding: 12;");
            Label label = new Label(content);
            label.setWrapText(true);
            label.setStyle("-fx-text-fill: white; -fx-font-size: 13;");
            label.setMaxWidth(Double.MAX_VALUE);
            label.setMinWidth(1);
            // Force the label to use the full width
            label.setPrefWidth(messageBox.getPrefWidth() - 24);
            messageBox.getChildren().add(label);

            Label timeLabel = new Label("You • " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
            timeLabel.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 9;");
            messageBox.getChildren().add(timeLabel);
        } else {
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
        }

        wrapper.getChildren().add(messageBox);
        return wrapper;
    }

    private HBox createSystemMessageBox(String content, String bgColor) {
        HBox wrapper = new HBox();
        wrapper.setAlignment(Pos.CENTER);
        wrapper.setPadding(new Insets(5, 0, 5, 0));

        Label label = new Label(content);
        label.setStyle("-fx-background-color: " + bgColor + "; -fx-background-radius: 15; -fx-padding: 6 15 6 15; -fx-text-fill: white; -fx-font-size: 11;");
        wrapper.getChildren().add(label);

        return wrapper;
    }

    private void scrollToBottom() {
        Platform.runLater(() -> {
            // Scroll the parent ScrollPane to bottom
            if (chatContainer.getParent() instanceof ScrollPane scrollPane) {
                scrollPane.setVvalue(1.0);
            }

            // Update message widths when chat container size changes
            updateMessageWidths();
        });
    }

    private void updateMessageWidths() {
        double containerWidth = chatContainer.getWidth();
        if (containerWidth <= 0) return;

        double maxWidth = containerWidth * 0.75;

        for (var child : chatContainer.getChildren()) {
            if (child instanceof HBox wrapper) {
                for (var inner : wrapper.getChildren()) {
                    if (inner instanceof VBox messageBox) {
                        messageBox.setMaxWidth(maxWidth);
                        messageBox.setPrefWidth(maxWidth);
                        // Update label widths inside
                        for (var node : messageBox.getChildren()) {
                            if (node instanceof Label label) {
                                label.setPrefWidth(maxWidth - 24);
                            }
                        }
                    }
                }
            }
        }
    }

    private String buildContextForAI() {
        if (mainController == null) {
            return "No data loaded. User should load data first.";
        }

        var data = mainController.getFilteredData();
        if (data == null || data.isEmpty()) {
            return "No data loaded. User should load data first.";
        }

        StringBuilder context = new StringBuilder();
        context.append("Dataset has ").append(data.size()).append(" rows and columns: ");
        if (!data.isEmpty()) {
            context.append(String.join(", ", data.get(0).keySet()));
        }
        context.append("\n");

        // Add sample data (first 5 rows)
        context.append("Sample data (first 5 rows):\n");
        int sampleSize = Math.min(5, data.size());
        for (int i = 0; i < sampleSize; i++) {
            context.append("Row ").append(i+1).append(": ").append(data.get(i)).append("\n");
        }

        // Add summary stats if available
        context.append("\nSummary:\n");
        context.append("• Total rows: ").append(data.size()).append("\n");
        if (!data.isEmpty()) {
            context.append("• Total columns: ").append(data.get(0).keySet().size()).append("\n");
        }

        return context.toString();
    }

    @FXML
    private void handleSendChatMessage() {
        if (isProcessing) return;

        String userInput = chatInput.getText().trim();
        if (userInput.isEmpty()) return;

        // Clear input
        chatInput.clear();

        // Add user message to chat
        addUserMessage(userInput);

        // Show loading indicator
        addSystemMessage("🤔 Qwen is thinking...", "#6f42c1");

        // Disable input during processing
        isProcessing = true;
        sendChatBtn.setDisable(true);
        chatInput.setDisable(true);
        aiProgressBar.setVisible(true);
        aiProgressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        aiStatusLabel.setText("Processing...");

        // Process the request - get the data context first
        String context = buildContextForAI();

        // Use Qwen's chat method
        qwenService.chat(userInput, context)
                .thenAcceptAsync(response -> {
                    Platform.runLater(() -> {
                        // Remove loading indicator
                        chatContainer.getChildren().remove(chatContainer.getChildren().size() - 1);

                        // Add assistant response
                        addAssistantMessage(response);

                        // Update status
                        aiStatusLabel.setText("Ready");
                        aiProgressBar.setVisible(false);
                        sendChatBtn.setDisable(false);
                        chatInput.setDisable(false);
                        chatInput.requestFocus();
                        isProcessing = false;

                        // Update token count (approximate)
                        int newTokens = userInput.length() / 4 + response.length() / 4;
                        tokenCount += newTokens;
                        tokenCountLabel.setText("Tokens: ~" + tokenCount);
                    });
                })
                .exceptionally(throwable -> {
                    Platform.runLater(() -> {
                        chatContainer.getChildren().remove(chatContainer.getChildren().size() - 1);
                        addAssistantMessage("❌ Error: " + throwable.getMessage());
                        aiStatusLabel.setText("Error: " + throwable.getMessage());
                        aiProgressBar.setVisible(false);
                        sendChatBtn.setDisable(false);
                        chatInput.setDisable(false);
                        isProcessing = false;
                    });
                    return null;
                });
    }

    // ========== QUICK ACTION HANDLERS ==========

    @FXML
    private void handleQuickFixGeo() {
        chatInput.setText("Fix the Country and State columns. Find any states in the Country column and countries in the State column.");
        handleSendChatMessage();
    }

    @FXML
    private void handleQuickValidate() {
        chatInput.setText("Validate all data. Check for empty values, incorrect formats, and geographic mismatches.");
        handleSendChatMessage();
    }

    @FXML
    private void handleQuickSummarize() {
        chatInput.setText("Summarize the dataset. Give me an overview of what's in this data.");
        handleSendChatMessage();
    }

    @FXML
    private void handleQuickAnomalies() {
        chatInput.setText("Find anomalies in the data. Look for unusual patterns, outliers, and inconsistencies.");
        handleSendChatMessage();
    }

    // ========== CHAT MANAGEMENT ==========

    @FXML
    private void handleClearChat() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Clear Chat");
        confirm.setHeaderText("Clear all chat messages?");
        confirm.setContentText("This will remove all messages from the chat history.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            chatContainer.getChildren().clear();
            chatHistory.clear();
            addWelcomeMessage();
            tokenCount = 0;
            tokenCountLabel.setText("Tokens: 0");
        }
    }

    @FXML
    private void handleExportChat() {
        if (chatHistory.isEmpty()) {
            showInfo("Export Chat", "No chat history to export.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
        fileChooser.setInitialFileName("chat_export_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".txt");

        File file = fileChooser.showSaveDialog(null);
        if (file == null) return;

        try (PrintWriter writer = new PrintWriter(file)) {
            writer.println("=== Qwen AI Chat Export ===");
            writer.println("Exported: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            writer.println("Total messages: " + chatHistory.size());
            writer.println("============================\n");

            for (ChatMessage msg : chatHistory) {
                String timestamp = msg.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                String sender = msg.getSender().equals("user") ? "You" : "Qwen";
                writer.println("[" + timestamp + "] " + sender + ":");
                writer.println(msg.getContent());
                writer.println("---");
            }

            showInfo("Export Success", "Chat exported to:\n" + file.getName());
        } catch (Exception e) {
            showError("Export Error", "Failed to export chat: " + e.getMessage());
        }
    }

    // ========== UTILITY METHODS ==========

    private void showInfo(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }



    // ========== SETTER FOR MAIN CONTROLLER ==========

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }
}
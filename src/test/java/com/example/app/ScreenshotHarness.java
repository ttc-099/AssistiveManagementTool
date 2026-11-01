package com.example.app;

import com.example.app.controller.AiChatController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.io.File;
import java.util.*;

/**
 * Headless UI screenshots: launches the REAL MainView under the Monocle
 * headless glass platform (software pipeline), switches real tabs, waits for
 * the Qwen model, and snapshots the scene to docs/screenshots/.
 *
 * Run: java -Dglass.platform=Monocle -Dmonocle.platform=Headless -Dprism.order=sw \
 *          -cp <fat-jar>:<test-classes> com.example.app.ScreenshotHarness
 */
public class ScreenshotHarness extends Application {

    private Scene scene;
    private TabPane tabPane;
    private Map<String, Object> namespace;

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("view/MainView.fxml"));
        Parent root = loader.load();
        namespace = loader.getNamespace();

        scene = new Scene(root, 1200, 800);
        stage.setScene(scene);
        stage.show();

        Object pane = namespace.get("mainTabPane");
        if (pane instanceof TabPane tp) tabPane = tp;

        capture("1-table.png");
        Thread.sleep(1500);

        // wait for the async Qwen model load, then populate + capture the AI tab
        var q = com.example.app.service.aiUsages.QwenAgentService.getInstance();
        long deadline = System.currentTimeMillis() + 240_000;
        while (!q.isAvailable() && System.currentTimeMillis() < deadline) {
            Thread.sleep(2000);
        }
        System.out.println("model available: " + q.isAvailable());

        q.ingestExcelToDatabase(new File("app_data/smoke_test.xlsx")).join();

        Object aiCtrl = namespace.get("aiChatView");
        if (aiCtrl instanceof AiChatController ai) {
            ai.notifyFileLoaded("smoke_test.xlsx", sampleRows());
        }

        selectTab("AI Review");
        Thread.sleep(2500);
        capture("2-ai-chat.png");

        selectTab("Dashboard");
        Thread.sleep(1500);
        capture("3-dashboard.png");

        selectTab("Additional Tools");
        Thread.sleep(1000);
        capture("4-tools.png");

        Platform.exit();
    }

    private void selectTab(String title) {
        if (tabPane == null) return;
        Platform.runLater(() -> {
            for (Tab t : tabPane.getTabs()) {
                if (t.getText() != null && t.getText().equalsIgnoreCase(title)) {
                    tabPane.getSelectionModel().select(t);
                    return;
                }
            }
        });
    }

    private void capture(String name) throws Exception {
        Thread.sleep(500);
        WritableImage img = scene.snapshot(null);
        File out = new File("docs/screenshots/" + name);
        out.getParentFile().mkdirs();
        ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", out);
        System.out.println("captured " + out.getAbsolutePath());
    }

    private List<Map<String, String>> sampleRows() {
        List<Map<String, String>> rows = new ArrayList<>();
        String[][] data = {
                {"ONG RONG ZHE", "MY", "ONG@Example.COM"},
                {"siti aminah binti omar", "malaysia", "siti.aminah@GMAIL.com"},
                {"JOHN DOE", "UK", "john.doe@outlook.com"},
                {"nurul aisyah", "my", "nurul@Example.org"},
                {"MOHAMAD AL BIN ALI", "MY", "mohamad.ali@gmail.COM"}
        };
        for (String[] d : data) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("Name", d[0]);
            row.put("Country", d[1]);
            row.put("Email", d[2]);
            rows.add(row);
        }
        return rows;
    }

    public static void main(String[] args) {
        launch(args);
    }
}

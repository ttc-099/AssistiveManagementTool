package com.example.app.service.chartDashboard;

import com.example.app.controller.DashboardController.DashboardItem;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.xslf.usermodel.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class DashboardExportService {

    private static final int SLIDE_WIDTH = 960;
    private static final int SLIDE_HEIGHT = 540;
    private static final int TITLE_HEIGHT = 50;
    private static final int CONTENT_TOP = 70;
    private static final int CONTENT_WIDTH = 850;
    private static final int CONTENT_HEIGHT = 420;

    public void exportDashboardToPPTX(List<DashboardItem> dashboardItems, File outputFile) throws IOException {
        try (XMLSlideShow ppt = new XMLSlideShow()) {
            // Set slide size
            ppt.setPageSize(new Dimension(SLIDE_WIDTH, SLIDE_HEIGHT));

            for (DashboardItem item : dashboardItems) {
                createSlideForItem(ppt, item);
            }

            try (FileOutputStream out = new FileOutputStream(outputFile)) {
                ppt.write(out);
            }
        }
    }

    private void createSlideForItem(XMLSlideShow ppt, DashboardItem item) {
        XSLFSlide slide = ppt.createSlide();

        // Add background
        XSLFTextBox bgBox = slide.createTextBox();
        bgBox.setAnchor(new Rectangle2D.Double(0, 0, SLIDE_WIDTH, SLIDE_HEIGHT));
        bgBox.setFillColor(java.awt.Color.decode("#f5f7fa"));
        bgBox.setLineWidth(0);

        // Add title
        addTitle(slide, item.getTitle());

        // Get content and add to slide
        Node content = item.getContent();
        String type = item.getType();

        try {
            if ("pivot".equals(type) && content instanceof TableView) {
                addPivotTableToSlide(slide, (TableView<?>) content);
            } else if ("barchart".equals(type) && content instanceof BarChart) {
                addBarChartToSlide(slide, (BarChart<?, ?>) content);
            } else if ("piechart".equals(type) && content instanceof PieChart) {
                addPieChartToSlide(slide, (PieChart) content);
            } else {
                addGenericContentToSlide(slide, content);
            }
        } catch (Exception e) {
            addErrorMessage(slide, "Error rendering content: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void addTitle(XSLFSlide slide, String title) {
        XSLFTextShape titleShape = slide.createTextBox();
        titleShape.setAnchor(new Rectangle2D.Double(50, 10, SLIDE_WIDTH - 100, TITLE_HEIGHT));
        titleShape.setFillColor(java.awt.Color.decode("#003366"));

        XSLFTextParagraph p = titleShape.getTextParagraphs().get(0);
        p.setTextAlign(org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER);

        XSLFTextRun r = p.addNewTextRun();
        r.setText(title);
        r.setFontColor(java.awt.Color.WHITE);
        r.setFontSize(20.0);
        r.setBold(true);
        r.setFontFamily("Arial");
    }

    private void addPivotTableToSlide(XSLFSlide slide, TableView<?> tableView) {
        if (tableView.getItems().isEmpty()) {
            addErrorMessage(slide, "No data available in pivot table");
            return;
        }

        // Try to capture as image first
        byte[] imageBytes = captureNodeAsImage(tableView);
        if (imageBytes != null) {
            addImageToSlide(slide, imageBytes);
        } else {
            // Fallback: Create a simple text table
            addTextTableToSlide(slide, tableView);
        }
    }

    @SuppressWarnings("unchecked")
    private void addTextTableToSlide(XSLFSlide slide, TableView<?> tableView) {
        try {
            XSLFTable table = slide.createTable();
            table.setAnchor(new Rectangle2D.Double(50, CONTENT_TOP, CONTENT_WIDTH, CONTENT_HEIGHT));

            // Add headers
            XSLFTableRow headerRow = table.addRow();
            headerRow.setHeight(30);

            for (TableColumn<?, ?> column : tableView.getColumns()) {
                XSLFTableCell cell = headerRow.addCell();
                cell.setText(column.getText());
                cell.setFillColor(java.awt.Color.decode("#003366"));

                XSLFTextParagraph p = cell.getTextParagraphs().get(0);
                p.setTextAlign(org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER);

                XSLFTextRun r = p.addNewTextRun();
                r.setText(column.getText());
                r.setFontColor(java.awt.Color.WHITE);
                r.setBold(true);
                r.setFontSize(12.0);
            }

            // Add data rows (limit to 10 rows for readability)
            int rowCount = 0;
            int totalItems = tableView.getItems().size();

            for (Object item : tableView.getItems()) {
                if (rowCount++ >= 10) {
                    XSLFTableRow moreRow = table.addRow();
                    moreRow.setHeight(25);
                    XSLFTableCell cell = moreRow.addCell();
                    cell.setText("... and " + (totalItems - 10) + " more rows");
                    cell.setFillColor(java.awt.Color.decode("#f0f0f0"));
                    int colCount = tableView.getColumns().size();
                    for (int i = 1; i < colCount; i++) {
                        moreRow.addCell().setText("");
                    }
                    break;
                }

                XSLFTableRow dataRow = table.addRow();
                dataRow.setHeight(25);

                for (TableColumn<?, ?> column : tableView.getColumns()) {
                    XSLFTableCell cell = dataRow.addCell();
                    Object value = getCellValue(column, item);
                    cell.setText(value != null ? value.toString() : "");

                    XSLFTextParagraph p = cell.getTextParagraphs().get(0);
                    p.setTextAlign(org.apache.poi.sl.usermodel.TextParagraph.TextAlign.LEFT);

                    if (!p.getTextRuns().isEmpty()) {
                        XSLFTextRun r = p.getTextRuns().get(0);
                        r.setFontSize(11.0);
                        r.setFontColor(java.awt.Color.BLACK);
                    }
                }
            }

            int colCount = tableView.getColumns().size();
            if (colCount > 0) {
                double colWidth = CONTENT_WIDTH / colCount;
                for (int i = 0; i < colCount; i++) {
                    table.setColumnWidth(i, colWidth);
                }
            }

        } catch (Exception e) {
            addErrorMessage(slide, "Error creating table: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Object getCellValue(TableColumn<?, ?> column, Object item) {
        try {
            Method cellDataMethod = TableColumn.class.getMethod("getCellData", Object.class);
            return cellDataMethod.invoke(column, item);
        } catch (Exception e1) {
            try {
                Method cellValueFactoryMethod = TableColumn.class.getMethod("getCellValueFactory");
                Object factory = cellValueFactoryMethod.invoke(column);
                if (factory != null) {
                    Method callMethod = factory.getClass().getMethod("call", Object.class);
                    Object observableValue = callMethod.invoke(factory, item);
                    if (observableValue != null) {
                        Method getValueMethod = observableValue.getClass().getMethod("getValue");
                        return getValueMethod.invoke(observableValue);
                    }
                }
                return "";
            } catch (Exception e2) {
                return "";
            }
        }
    }

    private void addBarChartToSlide(XSLFSlide slide, BarChart<?, ?> chart) {
        byte[] imageBytes = captureNodeAsImage(chart);
        if (imageBytes != null) {
            addImageToSlide(slide, imageBytes);
        } else {
            addErrorMessage(slide, "Unable to capture bar chart image");
        }
    }

    private void addPieChartToSlide(XSLFSlide slide, PieChart chart) {
        byte[] imageBytes = captureNodeAsImage(chart);
        if (imageBytes != null) {
            addImageToSlide(slide, imageBytes);
        } else {
            addErrorMessage(slide, "Unable to capture pie chart image");
        }
    }

    private void addGenericContentToSlide(XSLFSlide slide, Node content) {
        byte[] imageBytes = captureNodeAsImage(content);
        if (imageBytes != null) {
            addImageToSlide(slide, imageBytes);
        } else {
            addErrorMessage(slide, "Unable to capture content as image");
        }
    }

    private void addImageToSlide(XSLFSlide slide, byte[] imageBytes) {
        try {
            XSLFPictureData pictureData = slide.getSlideShow().addPicture(imageBytes, PictureData.PictureType.PNG);
            XSLFPictureShape picture = slide.createPicture(pictureData);

            double imageWidth = CONTENT_WIDTH;
            double imageHeight = CONTENT_HEIGHT;

            try {
                BufferedImage bufferedImage = ImageIO.read(new java.io.ByteArrayInputStream(imageBytes));
                if (bufferedImage != null) {
                    double aspectRatio = (double) bufferedImage.getWidth() / bufferedImage.getHeight();
                    if (aspectRatio > 1) {
                        imageHeight = imageWidth / aspectRatio;
                        if (imageHeight > CONTENT_HEIGHT) {
                            imageHeight = CONTENT_HEIGHT;
                            imageWidth = imageHeight * aspectRatio;
                        }
                    } else {
                        imageWidth = imageHeight * aspectRatio;
                        if (imageWidth > CONTENT_WIDTH) {
                            imageWidth = CONTENT_WIDTH;
                            imageHeight = imageWidth / aspectRatio;
                        }
                    }
                }
            } catch (Exception e) {
                // Use default sizing if unable to read image
            }

            double x = (SLIDE_WIDTH - imageWidth) / 2;
            double y = CONTENT_TOP + (CONTENT_HEIGHT - imageHeight) / 2;

            picture.setAnchor(new Rectangle2D.Double(x, y, imageWidth, imageHeight));

        } catch (Exception e) {
            addErrorMessage(slide, "Unable to add image: " + e.getMessage());
        }
    }

    private void addErrorMessage(XSLFSlide slide, String message) {
        XSLFTextShape shape = slide.createTextBox();
        shape.setAnchor(new Rectangle2D.Double(100, 200, SLIDE_WIDTH - 200, 100));

        XSLFTextParagraph p = shape.getTextParagraphs().get(0);
        p.setTextAlign(org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER);

        XSLFTextRun r = p.addNewTextRun();
        r.setText(message);
        r.setFontColor(java.awt.Color.RED);
        r.setFontSize(16.0);
        r.setFontFamily("Arial");
        r.setBold(true);
    }

    private byte[] captureNodeAsImage(Node node) {
        try {
            CountDownLatch latch = new CountDownLatch(1);
            final WritableImage[] imageHolder = new WritableImage[1];
            final Exception[] errorHolder = new Exception[1];

            Platform.runLater(() -> {
                try {
                    double width = 800;
                    double height = 400;

                    if (node instanceof BarChart) {
                        BarChart<?, ?> chart = (BarChart<?, ?>) node;
                        chart.setPrefSize(800, 400);
                        chart.setMinSize(800, 400);
                        width = 800;
                        height = 400;
                    } else if (node instanceof PieChart) {
                        PieChart chart = (PieChart) node;
                        chart.setPrefSize(600, 400);
                        chart.setMinSize(600, 400);
                        width = 600;
                        height = 400;
                    } else if (node instanceof TableView) {
                        TableView<?> tableView = (TableView<?>) node;
                        tableView.setPrefSize(800, 400);
                        tableView.setMinSize(800, 400);
                        width = 800;
                        height = 400;
                    }

                    node.applyCss();
                    node.resize(width, height);

                    SnapshotParameters params = new SnapshotParameters();
                    params.setFill(Color.WHITE);

                    imageHolder[0] = node.snapshot(params, null);
                } catch (Exception e) {
                    errorHolder[0] = e;
                } finally {
                    latch.countDown();
                }
            });

            boolean completed = latch.await(3, TimeUnit.SECONDS);

            if (!completed) {
                System.err.println("Snapshot timed out");
                return null;
            }

            if (errorHolder[0] != null) {
                System.err.println("Error during snapshot: " + errorHolder[0].getMessage());
                errorHolder[0].printStackTrace();
                return null;
            }

            if (imageHolder[0] == null) {
                System.err.println("No image captured");
                return null;
            }

            BufferedImage bufferedImage = SwingFXUtils.fromFXImage(imageHolder[0], null);
            if (bufferedImage != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(bufferedImage, "png", baos);
                return baos.toByteArray();
            }
            return null;

        } catch (Exception e) {
            System.err.println("Error capturing node as image: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}
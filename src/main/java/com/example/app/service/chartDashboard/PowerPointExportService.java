package com.example.app.service.chartDashboard;

import com.example.app.controller.MainController;
import com.example.app.controller.DashboardController.DashboardItem;
import org.apache.poi.xslf.usermodel.*;
import org.apache.poi.sl.usermodel.TableCell.BorderEdge;
import org.apache.poi.sl.usermodel.VerticalAlignment;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.poi.sl.usermodel.PictureData;

import java.awt.geom.Rectangle2D;

public class PowerPointExportService {

    public void exportToPPTX(List<Map<String, String>> data, List<String> columns, File outputFile) throws IOException {
        try (XMLSlideShow ppt = new XMLSlideShow()) {
            addTableSlide(ppt, data, columns, "Data Export");
            try (FileOutputStream out = new FileOutputStream(outputFile)) {
                ppt.write(out);
            }
        }
    }

    public void exportPivotToPPTX(List<PivotDataService.PivotResult> results, String title, byte[] chartImage, File outputFile) throws IOException {
        try (XMLSlideShow ppt = new XMLSlideShow()) {
            if (chartImage != null) {
                addChartSlide(ppt, chartImage, title);
            }
            addPivotTableSlide(ppt, results, title + " - Summary");
            try (FileOutputStream out = new FileOutputStream(outputFile)) {
                ppt.write(out);
            }
        }
    }

    public void exportDashboardToPPTX(List<DashboardItem> dashboardItems, File outputFile) throws IOException {
        try (XMLSlideShow ppt = new XMLSlideShow()) {
            // Set slide size
            ppt.setPageSize(new java.awt.Dimension(960, 540));

            for (DashboardItem item : dashboardItems) {
                XSLFSlide slide = ppt.createSlide();

                // Add title
                XSLFTextShape titleShape = slide.createTextBox();
                titleShape.setAnchor(new Rectangle2D.Double(50, 10, 860, 50));
                titleShape.setFillColor(new Color(0, 51, 102));

                XSLFTextParagraph titleP = titleShape.getTextParagraphs().get(0);
                titleP.setTextAlign(org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER);
                XSLFTextRun titleR = titleP.addNewTextRun();
                titleR.setText(item.getTitle());
                titleR.setFontColor(Color.WHITE);
                titleR.setFontSize(20.0);
                titleR.setBold(true);
                titleR.setFontFamily("Arial");

                // Add content as text
                XSLFTextShape contentShape = slide.createTextBox();
                contentShape.setAnchor(new Rectangle2D.Double(50, 70, 860, 420));
                contentShape.setLineWidth(0);

                XSLFTextParagraph contentP = contentShape.getTextParagraphs().get(0);
                contentP.setTextAlign(org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER);
                XSLFTextRun contentR = contentP.addNewTextRun();
                contentR.setText("Item: " + item.getTitle() + "\nType: " + item.getType());
                contentR.setFontSize(14.0);
                contentR.setFontColor(Color.BLACK);
                contentR.setFontFamily("Arial");
            }

            try (FileOutputStream out = new FileOutputStream(outputFile)) {
                ppt.write(out);
            }
        }
    }

    private void addChartSlide(XMLSlideShow ppt, byte[] imageBytes, String title) {
        XSLFSlide slide = ppt.createSlide();
        XSLFTextShape titleShape = slide.createTextBox();
        titleShape.setAnchor(new Rectangle2D.Double(50, 10, 620, 50));
        titleShape.setText(title);

        XSLFPictureData pd = ppt.addPicture(imageBytes, PictureData.PictureType.PNG);
        XSLFPictureShape pic = slide.createPicture(pd);
        pic.setAnchor(new Rectangle2D.Double(50, 70, 620, 400));
    }

    private void addPivotTableSlide(XMLSlideShow ppt, List<PivotDataService.PivotResult> results, String title) {
        XSLFSlide slide = ppt.createSlide();
        XSLFTextShape titleShape = slide.createTextBox();
        titleShape.setAnchor(new Rectangle2D.Double(50, 10, 620, 50));
        titleShape.setText(title);

        XSLFTable table = slide.createTable();
        table.setAnchor(new Rectangle2D.Double(50, 70, 600, 300));

        XSLFTableRow headerRow = table.addRow();
        String[] headers = {"Row Labels", "Values"};
        for (String h : headers) {
            XSLFTableCell cell = headerRow.addCell();
            cell.setText(h);
            cell.setLeftInset(5.0);
            cell.setRightInset(5.0);
            formatHeaderCell(cell);
        }

        for (PivotDataService.PivotResult res : results) {
            XSLFTableRow row = table.addRow();
            XSLFTableCell c1 = row.addCell();
            c1.setText(res.getGroup());
            c1.setLeftInset(5.0);
            c1.setRightInset(5.0);
            formatDataCell(c1);
            XSLFTableCell c2 = row.addCell();
            c2.setText(String.format("%.2f", res.getValue()));
            c2.setLeftInset(5.0);
            c2.setRightInset(5.0);
            formatDataCell(c2);
        }
        table.setColumnWidth(0, 400);
        table.setColumnWidth(1, 200);
    }

    private void addTableSlide(XMLSlideShow ppt, List<Map<String, String>> data, List<String> columns, String title) {
        XSLFSlide slide = ppt.createSlide();
        XSLFTextShape titleShape = slide.createTextBox();
        titleShape.setAnchor(new Rectangle2D.Double(50, 10, 620, 30));
        titleShape.setText(title);

        XSLFTable table = slide.createTable();
        table.setAnchor(new Rectangle2D.Double(25, 50, 670, 450));

        // Create Header Row
        XSLFTableRow headerRow = table.addRow();
        headerRow.setHeight(30);
        for (String column : columns) {
            XSLFTableCell cell = headerRow.addCell();
            cell.setText(column);
            cell.setLeftInset(5.0);
            cell.setRightInset(5.0);
            formatHeaderCell(cell);
        }

        // Create Data Rows
        int count = 0;
        for (Map<String, String> rowData : data) {
            if (count++ > 20) break;
            XSLFTableRow row = table.addRow();
            row.setHeight(20);
            for (String column : columns) {
                XSLFTableCell cell = row.addCell();
                cell.setText(rowData.getOrDefault(column, ""));
                cell.setLeftInset(5.0);
                cell.setRightInset(5.0);
                formatDataCell(cell);
            }
        }

        for (int i = 0; i < columns.size(); i++) {
            table.setColumnWidth(i, 670.0 / columns.size());
        }
    }

    private void formatHeaderCell(XSLFTableCell cell) {
        cell.setFillColor(new Color(0, 51, 102));
        XSLFTextParagraph p = cell.getTextParagraphs().get(0);
        p.setTextAlign(org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER);
        if (!p.getTextRuns().isEmpty()) {
            XSLFTextRun r = p.getTextRuns().get(0);
            r.setFontColor(Color.WHITE);
            r.setBold(true);
            r.setFontSize(12.0);
        }

        setBorder(cell);
        cell.setVerticalAlignment(VerticalAlignment.MIDDLE);
    }

    private void formatDataCell(XSLFTableCell cell) {
        XSLFTextParagraph p = cell.getTextParagraphs().get(0);
        p.setTextAlign(org.apache.poi.sl.usermodel.TextParagraph.TextAlign.LEFT);
        if (!p.getTextRuns().isEmpty()) {
            XSLFTextRun r = p.getTextRuns().get(0);
            r.setFontSize(10.0);
            r.setFontColor(Color.BLACK);
        }
        setBorder(cell);
        cell.setVerticalAlignment(VerticalAlignment.TOP);
        cell.setWordWrap(true);
    }

    private void setBorder(XSLFTableCell cell) {
        cell.setBorderColor(BorderEdge.bottom, Color.LIGHT_GRAY);
        cell.setBorderColor(BorderEdge.top, Color.LIGHT_GRAY);
        cell.setBorderColor(BorderEdge.left, Color.LIGHT_GRAY);
        cell.setBorderColor(BorderEdge.right, Color.LIGHT_GRAY);
        cell.setBorderWidth(BorderEdge.bottom, 0.5);
        cell.setBorderWidth(BorderEdge.top, 0.5);
        cell.setBorderWidth(BorderEdge.left, 0.5);
        cell.setBorderWidth(BorderEdge.right, 0.5);
    }
}
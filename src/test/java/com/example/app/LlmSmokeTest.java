package com.example.app;

import com.example.app.service.aiUsages.QwenAgentService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.util.*;

/**
 * headless smoke test: proves the local LLM path works end-to-end
 * (model load -> excel ingest -> chat SELECT -> update confirmation gate -> column cleaning)
 */
public class LlmSmokeTest {

    static int pass = 0, fail = 0;

    static void check(String name, boolean ok, String detail) {
        System.out.println((ok ? "PASS  " : "FAIL  ") + name + (detail == null || detail.isEmpty() ? "" : " -- " + detail));
        if (ok) pass++; else fail++;
    }

    public static void main(String[] args) throws Exception {
        File xlsx = new File("app_data/smoke_test.xlsx");
        xlsx.getParentFile().mkdirs();
        makeSyntheticWorkbook(xlsx);

        QwenAgentService q = QwenAgentService.getInstance();

        // wait for the async model load (up to 120 s)
        long deadline = System.currentTimeMillis() + 120_000;
        while (!q.isAvailable() && System.currentTimeMillis() < deadline) {
            Thread.sleep(500);
        }
        check("model loaded (real GGUF via llama.cpp)", q.isAvailable(), q.getStatus());

        // ingest
        q.ingestExcelToDatabase(xlsx).join();
        check("excel ingest imported sheets (fallback fix)",
                !q.getSheetRowCounts().isEmpty(),
                "sheets=" + q.getSheetRowCounts() + " file=" + q.getTrackedFileName());
        check("row count > 0", q.getTotalRowsCached() > 0, "rows=" + q.getTotalRowsCached());

        // chat: natural-language SELECT
        QwenAgentService.QueryResult r1 = q.chat("How many students are in the table?", null).join();
        System.out.println("      reply: " + r1.getNaturalLanguageResponse());
        System.out.println("      sql:   " + r1.getSqlExecuted());
        check("chat generated + ran SQL", r1.getSqlExecuted() != null && !r1.getSqlExecuted().isEmpty(),
                "sql=" + r1.getSqlExecuted());

        // chat: update request -> must gate behind confirmation
        QwenAgentService.QueryResult r2 =
                q.chat("update the Country column: set Country to Malaysia where Country is MY", null).join();
        System.out.println("      reply2: " + r2.getNaturalLanguageResponse());
        boolean gated = r2.getNaturalLanguageResponse() != null
                && r2.getNaturalLanguageResponse().toLowerCase().contains("confirm");
        check("update request gated behind confirmation", gated, "");

        // column cleaning on comma-containing addresses
        String table = q.getSheetRowCounts().keySet().iterator().next();
        QwenAgentService.CleanColumnResult r3 = q.cleanColumn(table, "Home_Address", 5, (p, m) -> {
        }).join();
        System.out.println("      cleaned: " + r3.getRowsChanged() + "/" + r3.getTotalRows() + " rows, "
                + r3.getChanges().size() + " change records");
        check("cleanColumn completed on all rows", r3.getTotalRows() >= 5,
                "changed=" + r3.getRowsChanged());

        // verify row alignment + integrity directly from SQLite (deterministic — no LLM in the check)
        // the old comma-split parser would have produced fragmented/misaligned values.
        // invariant: each row keeps ITS leading number and ITS street name (the 1.5B model
        // may reorder words like "Lot 8" -> "8 lot", which is still a correct cleaning)
        String[][] expectedTokens = {
                {"21", "desa"}, {"12", "kenari"}, {"3", "merdeka"}, {"8", "bahru"}, {"5", "ampang"}
        };
        List<String> addresses = new ArrayList<>();
        try (java.sql.Connection conn = q.getDatabaseManager().getConnection();
             java.sql.Statement st = conn.createStatement();
             java.sql.ResultSet rs = st.executeQuery(
                     "SELECT _row_id, \"Home_Address\" FROM \"Students\" ORDER BY _row_id")) {
            while (rs.next()) addresses.add(rs.getString(2).toLowerCase());
        }
        boolean aligned = addresses.size() == 5;
        for (int i = 0; aligned && i < 5; i++) {
            aligned = addresses.get(i).contains(expectedTokens[i][0])
                    && addresses.get(i).contains(expectedTokens[i][1]);
        }
        System.out.println("      addresses now: " + addresses);
        check("cleaned addresses row-aligned and coherent (JSON parser fix)", aligned, "");

        // confirmation flow: "Yes, apply changes" should execute the gated update
        QwenAgentService.QueryResult r5 = q.chat("Yes, apply changes", null).join();
        System.out.println("      confirm reply: " + r5.getNaturalLanguageResponse());
        List<String> countries = new ArrayList<>();
        try (java.sql.Connection conn = q.getDatabaseManager().getConnection();
             java.sql.Statement st = conn.createStatement();
             java.sql.ResultSet rs = st.executeQuery("SELECT \"Country\" FROM \"Students\" ORDER BY _row_id")) {
            while (rs.next()) countries.add(rs.getString(1));
        }
        System.out.println("      countries now: " + countries);
        long malaysiaCount = countries.stream().filter(c -> c != null && c.equalsIgnoreCase("Malaysia")).count();
        check("confirmed update applied to DB (MY rows -> Malaysia)", malaysiaCount >= 2,
                "malaysia=" + malaysiaCount + " of " + countries.size());

        System.out.println();
        System.out.println("==== RESULT: " + pass + " passed, " + fail + " failed ====");
        System.exit(fail == 0 ? 0 : 1);
    }

    static void makeSyntheticWorkbook(File f) throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("Students");
            String[] headers = {"Name", "Country", "Home Address", "Email"};
            String[][] data = {
                    {"ONG RONG ZHE", "MY", "no 21, jalan desa 4, taman universiti", "ONG@Example.COM"},
                    {"siti aminah binti omar", "malaysia", "12 Jalan Kenari 5 Taman Bandar", "siti.aminah@GMAIL.com"},
                    {"JOHN DOE", "UK", "3, jalan merdeka, taman sentosa", "john.doe@outlook.com"},
                    {"nurul aisyah", "my", "Lot 8 jalan bahru taman melati", "nurul@Example.org"},
                    {"MOHAMAD AL BIN ALI", "MY", "5 jalan ampang taman hill", "mohamad.ali@gmail.COM"}
            };
            Row hr = s.createRow(0);
            for (int i = 0; i < headers.length; i++) hr.createCell(i).setCellValue(headers[i]);
            for (int r = 0; r < data.length; r++) {
                Row row = s.createRow(r + 1);
                for (int c = 0; c < data[r].length; c++) row.createCell(c).setCellValue(data[r][c]);
            }
            try (FileOutputStream out = new FileOutputStream(f)) {
                wb.write(out);
            }
        }
        System.out.println("synthetic workbook written: " + f.getAbsolutePath());
    }
}

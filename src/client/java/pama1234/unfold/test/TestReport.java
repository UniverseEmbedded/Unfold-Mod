package pama1234.unfold.test;

import pama1234.unfold.UnfoldMod;

import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

public class TestReport {
    private final Map<String, TestResult> results = new LinkedHashMap<>();

    public void clear() {
        results.clear();
    }

    public void record(String testName, boolean success, String message) {
        results.put(testName, new TestResult(success, message));
    }

    public void generate() {
        StringBuilder sb = new StringBuilder();
        String title = "====== Unfold Mod Test Report ======";
        sb.append(title).append("\n");
        sb.append("Time: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).append("\n");
        sb.append("==================================\n\n");

        int successCount = 0;
        for (Map.Entry<String, TestResult> entry : results.entrySet()) {
            TestResult result = entry.getValue();
            sb.append(String.format("[%s] %s: %s\n", result.success ? "PASS" : "FAIL", entry.getKey(), result.message));
            if (result.success) {
                successCount++;
            }
        }

        sb.append("\n==================================\n");
        sb.append(String.format("Summary: %d / %d tests passed.\n", successCount, results.size()));
        sb.append("==================================\n");

        String reportContent = sb.toString();
        UnfoldMod.LOGGER.info("\n" + reportContent);

        try (FileWriter writer = new FileWriter("unfold_test_report.txt")) {
            writer.write(reportContent);
            UnfoldMod.LOGGER.info("Test report saved to unfold_test_report.txt");
        } catch (IOException e) {
            UnfoldMod.LOGGER.error("Failed to save test report file.", e);
        }
    }

    private record TestResult(boolean success, String message) {}
}
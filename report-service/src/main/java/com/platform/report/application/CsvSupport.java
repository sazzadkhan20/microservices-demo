package com.platform.report.application;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Production CSV helpers — quote/escape + neutralize spreadsheet formula injection.
 */
public final class CsvSupport {

    private CsvSupport() {}

    public static String cell(Object v) {
        if (v == null) {
            return "";
        }
        String s = String.valueOf(v);
        if (s.isEmpty()) {
            return "";
        }
        // Excel/Sheets formula injection guard (same idea as payroll export).
        char c0 = s.charAt(0);
        if (c0 == '=' || c0 == '+' || c0 == '-' || c0 == '@') {
            s = "'" + s;
        }
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    public static ResponseEntity<byte[]> download(String csv, String filename) {
        // UTF-8 BOM so Excel opens accented names correctly.
        byte[] body = csv.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] bom = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] bytes = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, bytes, 0, bom.length);
        System.arraycopy(body, 0, bytes, bom.length, body.length);

        String safeName = filename == null || filename.isBlank() ? "export.csv" : filename.replaceAll("[\\r\\n\"]", "");
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safeName + "\"");
        headers.set(HttpHeaders.CACHE_CONTROL, "no-store");
        headers.setContentLength(bytes.length);
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(bytes);
    }
}

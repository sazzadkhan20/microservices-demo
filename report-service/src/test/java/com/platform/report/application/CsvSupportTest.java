package com.platform.report.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvSupportTest {

    @Test
    void neutralizesFormulaInjection() {
        assertEquals("'=1+1", CsvSupport.cell("=1+1"));
        assertEquals("'+cmd", CsvSupport.cell("+cmd"));
    }

    @Test
    void quotesCommaFields() {
        assertEquals("\"a,b\"", CsvSupport.cell("a,b"));
        assertTrue(CsvSupport.cell("say \"hi\"").startsWith("\""));
    }

    @Test
    void downloadAddsUtf8BomAndSafeDisposition() {
        var resp = CsvSupport.download("a,b\n", "team \"x\".csv");
        byte[] body = resp.getBody();
        assertTrue(body != null && body.length >= 3);
        assertEquals((byte) 0xEF, body[0]);
        assertEquals((byte) 0xBB, body[1]);
        assertEquals((byte) 0xBF, body[2]);
        String cd = resp.getHeaders().getFirst("Content-Disposition");
        assertTrue(cd != null && cd.contains("team x.csv"));
    }
}

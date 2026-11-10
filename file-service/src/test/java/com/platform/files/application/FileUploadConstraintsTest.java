package com.platform.files.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("File upload constraints — documents 10 MB / photos 2 MB")
class FileUploadConstraintsTest {

    @Test
    void documentsAllowPdfJpegPngDocxUpTo10Mb() {
        assertDoesNotThrow(() -> FileService.assertUploadConstraints(
                "GENERAL", "application/pdf", 10L * 1024 * 1024, "application/pdf"));
        assertDoesNotThrow(() -> FileService.assertUploadConstraints(
                "GENERAL", "image/jpeg", 1024, "image/jpeg"));
        assertDoesNotThrow(() -> FileService.assertUploadConstraints(
                "GENERAL", "image/png", 1024, "image/png"));
        assertDoesNotThrow(() -> FileService.assertUploadConstraints(
                "GENERAL",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                1024,
                "application/docx"));
    }

    @Test
    void documentsRejectOversizedOrWrongType() {
        IllegalArgumentException size = assertThrows(IllegalArgumentException.class,
                () -> FileService.assertUploadConstraints(
                        "GENERAL", "application/pdf", 10L * 1024 * 1024 + 1, "application/pdf"));
        assertTrue(size.getMessage().contains("10 MB"));

        IllegalArgumentException type = assertThrows(IllegalArgumentException.class,
                () -> FileService.assertUploadConstraints(
                        "GENERAL", "image/webp", 100, "image/webp"));
        assertTrue(type.getMessage().toLowerCase().contains("pdf"));
    }

    @Test
    void photosAllowOnlyJpegPngUpTo2Mb() {
        assertDoesNotThrow(() -> FileService.assertUploadConstraints(
                "PHOTO", "image/jpeg", 2L * 1024 * 1024, "image/jpeg"));
        assertDoesNotThrow(() -> FileService.assertUploadConstraints(
                "PHOTO", "image/png", 512, "image/png"));

        IllegalArgumentException size = assertThrows(IllegalArgumentException.class,
                () -> FileService.assertUploadConstraints(
                        "PHOTO", "image/jpeg", 2L * 1024 * 1024 + 1, "image/jpeg"));
        assertTrue(size.getMessage().contains("2 MB"));

        IllegalArgumentException pdf = assertThrows(IllegalArgumentException.class,
                () -> FileService.assertUploadConstraints(
                        "PHOTO", "application/pdf", 100, "application/pdf"));
        assertTrue(pdf.getMessage().toLowerCase().contains("jpg"));
    }
}

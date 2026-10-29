package com.platform.report.infrastructure.persistence;

import com.platform.report.domain.ReportExport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportExportRepository extends JpaRepository<ReportExport, Long> {
    List<ReportExport> findTop50ByOrderByCreatedAtDesc();
}

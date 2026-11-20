package com.fixops.workorder.report.repository;

import com.fixops.workorder.report.domain.Report;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {
}

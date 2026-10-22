package com.mkwang.backend.modules.spreadsheet.repository;

import com.mkwang.backend.modules.spreadsheet.entity.ExcelExportLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExcelExportLogRepository extends JpaRepository<ExcelExportLog,Long> {
    List<ExcelExportLog> findTop50ByActorIdOrderByCreatedAtDesc(Long actorId);
}

package com.mkwang.backend.modules.audit.service;
import com.mkwang.backend.modules.spreadsheet.service.*;
import com.mkwang.backend.modules.spreadsheet.repository.ExcelExportSpecification;
import com.mkwang.backend.modules.audit.entity.AuditLog;
import com.mkwang.backend.modules.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.*;
import java.util.*;
import static com.mkwang.backend.modules.spreadsheet.service.ExcelValues.*;
@Configuration @RequiredArgsConstructor
public class AuditExcelConfiguration {
    private final AuditLogRepository logs;
    @Bean ExcelDataset auditExcel(){
        var columns=List.of(column("time","Thời gian","TEXT",false),column("actor","Người thao tác","TEXT",false),column("action","Thao tác","TEXT",false),
                column("entity","Đối tượng","TEXT",false),column("entityId","Mã đối tượng","TEXT",false),column("trace","Mã truy vết","TEXT",false));
        return new ExportExcelDataset("audit-history","Nhật ký hệ thống · lịch sử theo bộ lọc ngày",Set.of("ADMIN","CFO"),columns,(actor,filters,max)->
                logs.findAll(com.mkwang.backend.modules.audit.repository.AuditLogSpecification.exportFilter(filters),PageRequest.of(0,max+1,Sort.by("id"))).stream()
                        .map(log->values("time",log.getCreatedAt(),"actor",log.getActor()==null?"Hệ thống":log.getActor().getFullName(),"action",log.getAction(),"entity",log.getEntityName(),"entityId",log.getEntityId(),"trace",log.getTraceId())).toList());
    }
}

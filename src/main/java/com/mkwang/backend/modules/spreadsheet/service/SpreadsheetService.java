package com.mkwang.backend.modules.spreadsheet.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mkwang.backend.common.exception.*;
import com.mkwang.backend.modules.auth.security.UserDetailsAdapter;
import com.mkwang.backend.modules.spreadsheet.dto.response.*;
import com.mkwang.backend.modules.spreadsheet.entity.ExcelImportBatch;
import com.mkwang.backend.modules.spreadsheet.repository.ExcelImportBatchRepository;
import com.mkwang.backend.modules.spreadsheet.repository.ExcelExportLogRepository;
import com.mkwang.backend.modules.spreadsheet.entity.ExcelExportLog;
import com.mkwang.backend.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.time.*;
import java.util.*;
import java.security.*;
import java.io.IOException;

@Service @RequiredArgsConstructor
public class SpreadsheetService {
    private final List<ExcelDataset> datasets;
    private final ExcelWorkbookService workbooks;
    private final ExcelImportBatchRepository batches;
    private final ExcelExportLogRepository exportLogs;
    private final ObjectMapper json;
    @PersistenceContext private EntityManager em;
    @Value("${app.excel.preview-ttl-minutes:30}") private long ttlMinutes;
    @Value("${app.excel.max-export-rows:20000}") private int maxExportRows;
    private record Payload(List<ExcelRowResponse> rows,List<ExcelRowErrorResponse> parseErrors) {}

    @Transactional(readOnly=true)
    @PreAuthorize("isAuthenticated()")
    public List<ExcelDatasetResponse> list() {
        User actor=actor();
        return datasets.stream().filter(d->allowed(d,actor)).map(d->new ExcelDatasetResponse(d.code(),d.label(),d.importEnabled(),d.supportsDateFilter(),
                d.scopeLabel(),ExcelWorkbookService.SCHEMA_VERSION,d.columns())).toList();
    }
    @Transactional(readOnly=true)
    @PreAuthorize("isAuthenticated()")
    public List<Map<String,Object>> scopes(String code) {
        var dataset=datasets.stream().filter(d->d.code().equals(code)).findFirst().orElseThrow(()->new ResourceNotFoundException("Dataset not found"));
        if(!allowed(dataset,actor())) throw new UnauthorizedException("Không có quyền với bộ dữ liệu này");
        return dataset.scopes(actor());
    }
    @Transactional(readOnly=true)
    @PreAuthorize("isAuthenticated()")
    public byte[] template(String code,Long scope) {
        var dataset=dataset(code,scope);
        if(!dataset.importEnabled()) throw new BadRequestException("Bộ dữ liệu chỉ hỗ trợ xuất");
        return workbooks.template(dataset,dataset.references(actor(),scope));
    }
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ExcelPreviewResponse preview(String code,Long scope,MultipartFile file) {
        var dataset=dataset(code,scope);
        if(!dataset.importEnabled()) throw new BadRequestException("Bộ dữ liệu chỉ hỗ trợ xuất");
        var parsed=workbooks.parse(file,dataset);
        String hash;
        try { hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file.getBytes())); }
        catch(IOException|NoSuchAlgorithmException error) { throw new BadRequestException("Không đọc được file"); }
        User actor=actor(); String scopeKey=scope==null?"GLOBAL":scope.toString();
        em.createNativeQuery("SELECT pg_advisory_xact_lock(hashtextextended(:key,0))")
                .setParameter("key","IFMS_EXCEL:"+actor.getId()+":"+code+":"+scopeKey+":"+hash).getSingleResult();
        var existing=batches.findByActorIdAndDatasetAndScopeKeyAndFileHashAndSchemaVersion(actor.getId(),code,scopeKey,hash,ExcelWorkbookService.SCHEMA_VERSION);
        ExcelImportBatch batch=existing.orElseGet(()->ExcelImportBatch.builder().id(UUID.randomUUID().toString())
                .actorId(actor.getId()).dataset(code).scopeKey(scopeKey).fileHash(hash).schemaVersion(ExcelWorkbookService.SCHEMA_VERSION)
                .state("PREVIEW").createdAt(Instant.now()).build());
        if("COMMITTED".equals(batch.getState())) return read(batch.getResult(),ExcelPreviewResponse.class);
        batch.setPayload(write(new Payload(parsed.rows(),parsed.errors())));
        batch.setExpiresAt(Instant.now().plusSeconds(ttlMinutes*60));
        batches.save(batch);
        return validate(batch,dataset,scope);
    }
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ExcelPreviewResponse commit(String id) {
        var batch=owned(id);
        Long scope=scope(batch);
        var dataset=dataset(batch.getDataset(),scope);
        if("COMMITTED".equals(batch.getState())) return read(batch.getResult(),ExcelPreviewResponse.class);
        if(batch.getExpiresAt().isBefore(Instant.now())) throw new BadRequestException("Bản xem trước đã hết hạn; hãy tải file lại");
        var preview=validate(batch,dataset,scope);
        if(!preview.errors().isEmpty()) return preview;
        dataset.beforeCommit(preview.rows(),actor(),scope);
        List<String> results=new ArrayList<>();
        for(var row:dataset.orderForCommit(preview.rows(),scope)) {
            try { results.add(dataset.commit(row,actor(),scope)); }
            catch(BaseException error) { throw new BadRequestException("Dòng "+row.row()+": "+error.getMessage()+". Toàn bộ lô chưa được ghi."); }
        }
        batch.setState("COMMITTED");batch.setCommittedAt(Instant.now());
        var result=new ExcelPreviewResponse(batch.getId(),batch.getDataset(),"COMMITTED",preview.totalRows(),preview.totalRows(),0,preview.rows(),List.of(),results);
        batch.setResult(write(result));return result;
    }
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public byte[] errors(String id) {
        var batch=owned(id);var dataset=dataset(batch.getDataset(),scope(batch));
        var preview=validate(batch,dataset,scope(batch));
        var columns=List.of(ExcelValues.column("row","Dòng","TEXT",true),ExcelValues.column("column","Cột","TEXT",true),ExcelValues.column("message","Lỗi","TEXT",true));
        return workbooks.export("Lỗi nhập "+dataset.label(),columns,preview.errors().stream()
                .map(e->ExcelValues.values("row",e.row(),"column",e.column(),"message",e.message())).toList(),actor().getFullName());
    }
    @Transactional(isolation=Isolation.REPEATABLE_READ)
    @PreAuthorize("isAuthenticated()")
    public byte[] export(String code,Long scope,Map<String,String> filters) {
        var dataset=dataset(code,scope);
        if(!dataset.supportsDateFilter() && (filters.containsKey("from")||filters.containsKey("to")))
            throw new BadRequestException("Bộ dữ liệu này xuất trạng thái hiện tại, không hỗ trợ lọc theo ngày");
        var rows=dataset.export(actor(),scope,filters,maxExportRows);
        if(rows.size()>maxExportRows) throw new BadRequestException("Báo cáo vượt "+maxExportRows+" dòng; hãy thu hẹp bộ lọc");
        User user=actor();
        byte[] file=workbooks.export(dataset.label()+" · bộ lọc "+filters,dataset.exportColumns(),rows,user.getFullName());
        try {
            String checksum=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file));
            exportLogs.save(ExcelExportLog.builder().actorId(user.getId()).datasetCode(code)
                    .scopeKey(effectiveExportScope(user,scope)).filterSnapshot(write(filters))
                    .rowCount(rows.size()).fileSha256(checksum).createdAt(Instant.now()).build());
        } catch(NoSuchAlgorithmException impossible) {
            throw new InternalSystemException("Không tạo được mã kiểm tra file xuất Excel");
        }
        return file;
    }

    @Transactional(readOnly=true)
    @PreAuthorize("isAuthenticated()")
    public List<ExcelExportLogResponse> exportHistory() {
        return exportLogs.findTop50ByActorIdOrderByCreatedAtDesc(actor().getId()).stream()
                .map(log->new ExcelExportLogResponse(log.getId(),log.getDatasetCode(),log.getScopeKey(),
                        log.getFilterSnapshot(),log.getRowCount(),log.getFileSha256(),log.getCreatedAt())).toList();
    }
    private ExcelPreviewResponse validate(ExcelImportBatch batch,ExcelDataset dataset,Long scope) {
        var payload=read(batch.getPayload(),Payload.class);
        var errors=new ArrayList<>(payload.parseErrors());
        errors.addAll(dataset.validate(payload.rows(),actor(),scope));
        int invalid=(int)errors.stream().map(ExcelRowErrorResponse::row).distinct().count();
        return new ExcelPreviewResponse(batch.getId(),batch.getDataset(),batch.getState(),payload.rows().size(),
                payload.rows().size()-invalid,invalid,payload.rows(),errors,List.of());
    }
    private ExcelImportBatch owned(String id) {
        return batches.findLocked(id,actor().getId()).orElseThrow(()->new ResourceNotFoundException("Excel batch not found"));
    }
    private String effectiveExportScope(User user,Long scope){
        if(scope!=null)return "SELECTED:"+scope;
        return switch(user.getRole().getName()){
            case "MANAGER" -> "DEPARTMENT:"+(user.getDepartment()==null?"NONE":user.getDepartment().getId());
            case "TEAM_LEADER" -> "MANAGED_PROJECTS:"+user.getId();
            case "EMPLOYEE" -> "SELF:"+user.getId();
            default -> "ROLE:"+user.getRole().getName();
        };
    }
    private Long scope(ExcelImportBatch batch) { return "GLOBAL".equals(batch.getScopeKey())?null:Long.valueOf(batch.getScopeKey()); }
    private ExcelDataset dataset(String code,Long scope) {
        var dataset=datasets.stream().filter(d->d.code().equals(code)).findFirst().orElseThrow(()->new ResourceNotFoundException("Excel dataset not found"));
        if(!allowed(dataset,actor())) throw new UnauthorizedException("Không có quyền nhập/xuất bộ dữ liệu này");
        dataset.authorize(actor(),scope);return dataset;
    }
    private boolean allowed(ExcelDataset dataset,User actor) { return dataset.roles().contains(actor.getRole().getName()); }
    private User actor() {
        Object principal=SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if(principal instanceof UserDetailsAdapter adapter) return adapter.getUser();
        throw new UnauthorizedException("Cần đăng nhập");
    }
    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch(com.fasterxml.jackson.core.JsonProcessingException error) { throw new InternalSystemException("Không lưu được bản xem trước Excel"); }
    }
    private <T>T read(String value,Class<T> type) {
        try { return json.readValue(value,type); }
        catch(com.fasterxml.jackson.core.JsonProcessingException error) { throw new InternalSystemException("Không đọc được bản xem trước Excel"); }
    }
}

package com.mkwang.backend.modules.request.mapper;
import com.mkwang.backend.common.exception.BadRequestException;
import com.mkwang.backend.modules.request.dto.request.*;
import com.mkwang.backend.modules.request.entity.RequestType;
import com.mkwang.backend.modules.file.entity.FileStorage;
import com.mkwang.backend.modules.spreadsheet.dto.response.ExcelRowResponse;
import org.springframework.stereotype.Component;
import java.util.List;
import static com.mkwang.backend.modules.spreadsheet.service.ExcelValues.*;
@Component
public class RequestExcelMapper {
    public CreateRequestRequest toRequest(ExcelRowResponse row,List<FileStorage> files) {
        var v=row.values();var request=new CreateRequestRequest();
        try { request.setType(RequestType.valueOf(v.get("type"))); }
        catch(IllegalArgumentException error) { throw new BadRequestException("Loại yêu cầu phải là ADVANCE, EXPENSE hoặc REIMBURSE"); }
        request.setProjectId(id(v,"projectId"));request.setPhaseId(id(v,"phaseId"));request.setCategoryId(id(v,"categoryId"));
        request.setAdvanceBalanceId(id(v,"advanceBalanceId"));request.setAmount(money(v,"amount"));request.setDescription(v.get("description"));
        request.setAttachments(files.stream().map(file->{
            var attachment=new AttachmentRequest();attachment.setFileName(file.getFileName());
            attachment.setCloudinaryPublicId(file.getCloudinaryPublicId());attachment.setUrl(file.getUrl());
            attachment.setFileType(file.getFileType());attachment.setSize(file.getSize());return attachment;
        }).toList());
        return request;
    }
}

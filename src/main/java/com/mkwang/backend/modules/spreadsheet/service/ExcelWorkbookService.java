package com.mkwang.backend.modules.spreadsheet.service;

import com.mkwang.backend.common.exception.*;
import com.mkwang.backend.modules.spreadsheet.dto.response.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.util.WorkbookUtil;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.math.BigDecimal;
import java.util.*;

@Service
public class ExcelWorkbookService {
    public static final int SCHEMA_VERSION=1;
    @Value("${app.excel.max-file-bytes:10485760}") private long maxFileBytes;
    @Value("${app.excel.max-import-rows:2000}") private int maxRows;
    public record ParseResult(List<ExcelRowResponse> rows,List<ExcelRowErrorResponse> errors) {}
    public ParseResult parse(MultipartFile file,ExcelDataset dataset) {
        String name=file==null?null:file.getOriginalFilename();
        if(file==null||file.isEmpty()||name==null||!name.toLowerCase(Locale.ROOT).endsWith(".xlsx")) throw new BadRequestException("Chỉ nhận file .xlsx theo mẫu IFMS");
        if(file.getSize()>maxFileBytes) throw new BadRequestException("File vượt dung lượng cho phép");
        List<ExcelRowResponse> rows=new ArrayList<>();
        List<ExcelRowErrorResponse> errors=new ArrayList<>();
        try(var workbook=new XSSFWorkbook(file.getInputStream())) {
            Sheet schema=workbook.getSheet("_IFMS");
            if(schema==null||!dataset.code().equals(text(schema.getRow(0),0))||!String.valueOf(SCHEMA_VERSION).equals(text(schema.getRow(0),1))) {
                throw new BadRequestException("Sai loại hoặc phiên bản mẫu Excel; hãy tải mẫu mới");
            }
            Sheet sheet=workbook.getSheet("Data");
            if(sheet==null||sheet.getRow(0)==null) throw new BadRequestException("Thiếu sheet Data hoặc tiêu đề");
            var columns=dataset.columns();
            for(int i=0;i<columns.size();i++) if(!columns.get(i).key().equals(text(sheet.getRow(0),i))) throw new BadRequestException("Sai tiêu đề cột "+(i+1)+": "+columns.get(i).key());
            if(sheet.getRow(0).getLastCellNum()>columns.size()) throw new BadRequestException("File có cột ngoài schema được phép");
            if(sheet.getLastRowNum()>maxRows) throw new BadRequestException("Vượt "+maxRows+" dòng; hãy chia nhỏ file");
            for(int n=1;n<=sheet.getLastRowNum();n++) {
                Row row=sheet.getRow(n);
                if(row==null) continue;
                Map<String,String> values=new LinkedHashMap<>();
                boolean nonempty=false;
                for(int col=0;col<columns.size();col++) {
                    var definition=columns.get(col); String value="";
                    Cell cell=row.getCell(col,Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    if(cell!=null) {
                        nonempty=true;
                        if(cell.getCellType()==CellType.FORMULA||cell.getCellType()==CellType.ERROR||cell.getCellType()==CellType.BOOLEAN) {
                            errors.add(new ExcelRowErrorResponse(n+1,definition.key(),"Không nhận ô công thức, lỗi hoặc boolean"));
                        } else if(cell.getCellType()==CellType.NUMERIC) {
                            if(!"MONEY".equals(definition.type())) errors.add(new ExcelRowErrorResponse(n+1,definition.key(),"Nhập ô dạng Text để giữ nguyên mã hoặc ngày"));
                            value=org.apache.poi.ss.util.NumberToTextConverter.toText(cell.getNumericCellValue());
                        } else value=cell.getStringCellValue().trim();
                    }
                    if(value.length()>4000) errors.add(new ExcelRowErrorResponse(n+1,definition.key(),"Nội dung quá dài"));
                    values.put(definition.key(),value);
                }
                if(row.getLastCellNum()>columns.size()) { nonempty=true; errors.add(new ExcelRowErrorResponse(n+1,"","Dòng có cột ngoài schema")); }
                if(!nonempty) continue;
                if(values.values().stream().allMatch(String::isBlank)) {
                    final int rowNumber=n+1;
                    if(errors.stream().noneMatch(error->error.row()==rowNumber)) continue;
                }
                for(var column:columns) if(column.required()&&values.get(column.key()).isBlank()) errors.add(new ExcelRowErrorResponse(n+1,column.key(),"Bắt buộc nhập"));
                rows.add(new ExcelRowResponse(n+1,values));
            }
            if(rows.isEmpty()) throw new BadRequestException("File không có dòng dữ liệu");
            return new ParseResult(rows,errors);
        } catch(BaseException error) { throw error;
        } catch(IOException|RuntimeException error) {
            throw new BadRequestException("Không đọc được file Excel hợp lệ");
        }
    }
    private String text(Row row,int col) {
        if(row==null) return "";
        Cell cell=row.getCell(col);
        return cell!=null&&cell.getCellType()==CellType.STRING?cell.getStringCellValue():"";
    }
    public byte[] template(ExcelDataset dataset,List<Map<String,Object>> references) {
        try(var workbook=new XSSFWorkbook()) {
            Sheet schema=workbook.createSheet("_IFMS");
            schema.createRow(0).createCell(0).setCellValue(dataset.code());
            schema.getRow(0).createCell(1).setCellValue(String.valueOf(SCHEMA_VERSION));
            write(workbook,"Data",dataset.columns(),List.of());
            Sheet guide=workbook.createSheet("HuongDan");
            guide.createRow(0).createCell(0).setCellValue("Chỉ nhập Data. Mã/ngày dùng Text, ngày YYYY-MM-DD; tiền dùng số, không có công thức. Xem trước rồi xác nhận toàn lô.");
            int n=1;
            for(var column:dataset.columns()) {
                Row row=guide.createRow(n++);
                row.createCell(0).setCellValue(column.key());
                row.createCell(1).setCellValue(column.label());
                row.createCell(2).setCellValue(column.type()+(column.required()?" · bắt buộc":" · tùy chọn"));
            }
            if(!references.isEmpty()) {
                Set<String> keys=new LinkedHashSet<>(); references.forEach(row->keys.addAll(row.keySet()));
                write(workbook,"ThamChieu",keys.stream().map(key->new ExcelColumnResponse(key,key,"TEXT",false)).toList(),references);
            }
            workbook.setSheetOrder("Data",0); workbook.setActiveSheet(0);
            workbook.setSheetHidden(workbook.getSheetIndex(schema),true);
            return bytes(workbook);
        } catch(IOException error) { throw new InternalSystemException("Không tạo được mẫu Excel"); }
    }
    public byte[] export(String title,List<ExcelColumnResponse> columns,List<Map<String,Object>> rows,String actor) {
        try(var workbook=new XSSFWorkbook()) {
            write(workbook,"Data",columns,rows);
            Sheet info=workbook.createSheet("ThongTin");
            String[] metadata={title,"Người xuất: "+actor,"Thời điểm chốt: "+java.time.Instant.now(),"Số dòng: "+rows.size(),"Dữ liệu theo phạm vi được cấp quyền; ô văn bản không thực thi công thức."};
            for(int n=0;n<metadata.length;n++) info.createRow(n).createCell(0).setCellValue(metadata[n]);
            return bytes(workbook);
        } catch(IOException error) { throw new InternalSystemException("Không xuất được Excel"); }
    }
    private void write(XSSFWorkbook workbook,String name,List<ExcelColumnResponse> columns,List<Map<String,Object>> rows) {
        Sheet sheet=workbook.createSheet(WorkbookUtil.createSafeSheetName(name));
        var header=sheet.createRow(0); var style=workbook.createCellStyle();
        var font=workbook.createFont();font.setBold(true);style.setFont(font);
        for(int i=0;i<columns.size();i++) { var cell=header.createCell(i);cell.setCellValue(columns.get(i).key());cell.setCellStyle(style);sheet.setColumnWidth(i,Math.min(60,Math.max(20,columns.get(i).label().length()+3))*256); }
        int index=1; Map<String,BigDecimal> totals=new LinkedHashMap<>();
        for(var values:rows) {
            Row row=sheet.createRow(index++);
            for(int i=0;i<columns.size();i++) {
                var column=columns.get(i);Object value=values.get(column.key());var cell=row.createCell(i);
                if(value==null) continue;
                if(("MONEY".equals(column.type())||"MONEY_INFO".equals(column.type()))&&value instanceof Number number) {
                    writeMoney(cell,new BigDecimal(number.toString()));if("MONEY".equals(column.type())) totals.merge(column.key(),new BigDecimal(number.toString()),BigDecimal::add);
                } else cell.setCellValue(value.toString()); // STRING cell: never formula, even if starts with '='
            }
        }
        sheet.createFreezePane(0,1);
        if(!rows.isEmpty()) sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0,rows.size(),0,columns.size()-1));
        if(!totals.isEmpty()) {
            Row total=sheet.createRow(index+1); total.createCell(0).setCellValue("TỔNG");
            for(int i=1;i<columns.size();i++) if(totals.containsKey(columns.get(i).key())) writeMoney(total.createCell(i),totals.get(columns.get(i).key()));
        }
    }
    private void writeMoney(Cell cell,BigDecimal amount) {
        if(amount.precision()>15) cell.setCellValue(amount.toPlainString()); else cell.setCellValue(amount.doubleValue());
    }
    private byte[] bytes(XSSFWorkbook workbook) throws IOException {
        var out=new ByteArrayOutputStream();workbook.write(out);return out.toByteArray();
    }
}

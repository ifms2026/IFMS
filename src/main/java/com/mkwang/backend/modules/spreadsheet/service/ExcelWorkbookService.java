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
            if(hasUnexpectedColumns(sheet.getRow(0),columns.size())) throw new BadRequestException("File có cột ngoài schema được phép");
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
                if(hasUnexpectedColumns(row,columns.size())) { nonempty=true; errors.add(new ExcelRowErrorResponse(n+1,"","Dòng có cột ngoài schema")); }
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
    private boolean hasUnexpectedColumns(Row row,int allowedColumns) {
        if(row==null) return false;
        for(int col=allowedColumns;col<row.getLastCellNum();col++) {
            Cell cell=row.getCell(col,Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if(cell==null) continue;
            if(cell.getCellType()==CellType.STRING&&cell.getStringCellValue().isBlank()) continue;
            return true;
        }
        return false;
    }
    public byte[] template(ExcelDataset dataset,List<Map<String,Object>> references,List<Map<String,Object>> sampleRows) {
        try(var workbook=new XSSFWorkbook()) {
            Sheet schema=workbook.createSheet("_IFMS");
            schema.createRow(0).createCell(0).setCellValue(dataset.code());
            schema.getRow(0).createCell(1).setCellValue(String.valueOf(SCHEMA_VERSION));
            write(workbook,"Data",dataset.columns(),sampleRows);
            formatTemplateInputRows(workbook,workbook.getSheet("Data"),dataset.columns(),sampleRows.size());
            Sheet guide=workbook.createSheet("HuongDan");
            writeGuide(workbook,guide,dataset,sampleRows.size());
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
            write(workbook,"Data",columns,rows,true);
            Sheet info=workbook.createSheet("ThongTin");
            String[] metadata={title,"Người xuất: "+actor,"Thời điểm chốt: "+java.time.Instant.now(),"Số dòng: "+rows.size(),"Dữ liệu theo phạm vi được cấp quyền; ô văn bản không thực thi công thức."};
            CellStyle titleStyle=titleStyle(workbook);
            CellStyle bodyStyle=bodyStyle(workbook);
            for(int n=0;n<metadata.length;n++) {
                Row row=info.createRow(n);
                Cell cell=row.createCell(0); cell.setCellValue(metadata[n]); cell.setCellStyle(n==0?titleStyle:bodyStyle);
                row.setHeightInPoints(n==0?30:22);
            }
            info.setColumnWidth(0,100*256);
            return bytes(workbook);
        } catch(IOException error) { throw new InternalSystemException("Không xuất được Excel"); }
    }
    private void write(XSSFWorkbook workbook,String name,List<ExcelColumnResponse> columns,List<Map<String,Object>> rows) {
        write(workbook,name,columns,rows,false);
    }
    private void write(XSSFWorkbook workbook,String name,List<ExcelColumnResponse> columns,List<Map<String,Object>> rows,boolean friendlyHeaders) {
        Sheet sheet=workbook.createSheet(WorkbookUtil.createSafeSheetName(name));
        var header=sheet.createRow(0); CellStyle headerStyle=headerStyle(workbook);
        Drawing<?> drawing=sheet.createDrawingPatriarch();
        header.setHeightInPoints(32);
        for(int i=0;i<columns.size();i++) {
            var column=columns.get(i); var cell=header.createCell(i);cell.setCellValue(friendlyHeaders?column.label():column.key());cell.setCellStyle(headerStyle);
            CreationHelper helper=workbook.getCreationHelper(); ClientAnchor anchor=helper.createClientAnchor();anchor.setCol1(i);anchor.setCol2(i+1);anchor.setRow1(0);anchor.setRow2(3);
            Comment note=drawing.createCellComment(anchor);
            note.setString(helper.createRichTextString(friendlyHeaders?column.key()+(column.required()?" · Bắt buộc":" · Tùy chọn"):column.label()+(column.required()?" · Bắt buộc":" · Tùy chọn"))); cell.setCellComment(note);
            sheet.setColumnWidth(i,columnWidth(column,rows));
        }
        CellStyle normal=bodyStyle(workbook),alternate=alternateBodyStyle(workbook),moneyNormal=moneyStyle(workbook,false),moneyAlternate=moneyStyle(workbook,true);
        int index=1; Map<String,BigDecimal> totals=new LinkedHashMap<>();
        for(var values:rows) {
            Row row=sheet.createRow(index++);
            row.setHeightInPoints(22);
            for(int i=0;i<columns.size();i++) {
                var column=columns.get(i);Object value=values.get(column.key());var cell=row.createCell(i);
                boolean isAlternate=index%2==0;cell.setCellStyle(isAlternate?alternate:normal);
                if(value==null) continue;
                if(("MONEY".equals(column.type())||"MONEY_INFO".equals(column.type()))&&value instanceof Number number) {
                    writeMoney(cell,new BigDecimal(number.toString()));cell.setCellStyle(isAlternate?moneyAlternate:moneyNormal);if("MONEY".equals(column.type())) totals.merge(column.key(),new BigDecimal(number.toString()),BigDecimal::add);
                } else cell.setCellValue(value.toString()); // STRING cell: never formula, even if starts with '='
            }
        }
        sheet.createFreezePane(0,1);
        sheet.setDefaultRowHeightInPoints(22);
        if(!rows.isEmpty()) sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0,rows.size(),0,columns.size()-1));
        if(!totals.isEmpty()) {
            Row total=sheet.createRow(index+1); CellStyle totalStyle=totalStyle(workbook);total.setHeightInPoints(25);
            Cell label=total.createCell(0);label.setCellValue("TỔNG");label.setCellStyle(totalStyle);
            for(int i=1;i<columns.size();i++) { Cell cell=total.createCell(i);cell.setCellStyle(totalStyle);if(totals.containsKey(columns.get(i).key())) writeMoney(cell,totals.get(columns.get(i).key())); }
        }
    }
    private void writeGuide(XSSFWorkbook workbook,Sheet guide,ExcelDataset dataset,int sampleRowCount) {
        CellStyle title=titleStyle(workbook),subTitle=sectionStyle(workbook),head=headerStyle(workbook),body=bodyStyle(workbook),alt=alternateBodyStyle(workbook),note=noteStyle(workbook);
        Row titleRow=guide.createRow(0);titleRow.setHeightInPoints(34);Cell titleCell=titleRow.createCell(0);titleCell.setCellValue("HƯỚNG DẪN NHẬP DỮ LIỆU · "+dataset.label());titleCell.setCellStyle(title);
        Row info=guide.createRow(1);info.setHeightInPoints(42);Cell infoCell=info.createCell(0);infoCell.setCellValue(sampleRowCount>0?"Sheet Data có "+sampleRowCount+" dòng màu vàng là dữ liệu minh họa. Hãy thay bằng dữ liệu thật hoặc xóa các dòng này trước khi xem trước và xác nhận nhập.":"Chỉ nhập dữ liệu vào sheet Data. Kiểm tra bản xem trước trước khi xác nhận.");infoCell.setCellStyle(note);
        Row section=guide.createRow(3);Cell sectionCell=section.createCell(0);sectionCell.setCellValue("DANH SÁCH CỘT VÀ DỮ LIỆU MINH HỌA");sectionCell.setCellStyle(subTitle);
        guide.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0,0,0,5));
        guide.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(1,1,0,5));
        guide.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(3,3,0,5));
        String[] headers={"Tên cột trong Excel","Tên dễ hiểu","Kiểu dữ liệu","Bắt buộc","Giá trị minh họa","Ghi chú"};
        Row header=guide.createRow(4);header.setHeightInPoints(30);for(int i=0;i<headers.length;i++){Cell cell=header.createCell(i);cell.setCellValue(headers[i]);cell.setCellStyle(head);}
        int index=5;
        for(ExcelColumnResponse column:dataset.columns()) {
            Row row=guide.createRow(index);row.setHeightInPoints(24);
            String example=sampleValue(column);
            String[] values={column.key(),column.label(),friendlyType(column.type()),column.required()?"Có":"Không",example,column.required()?"Cần nhập trước khi xem trước":"Có thể để trống nếu không áp dụng"};
            for(int i=0;i<values.length;i++){Cell cell=row.createCell(i);cell.setCellValue(values[i]);cell.setCellStyle(index%2==1?body:alt);}
            index++;
        }
        int[] widths={24,34,18,14,30,46};for(int i=0;i<widths.length;i++)guide.setColumnWidth(i,widths[i]*256);
        guide.createFreezePane(0,5);guide.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(4,index-1,0,headers.length-1));
    }
    private void formatTemplateInputRows(XSSFWorkbook workbook,Sheet sheet,List<ExcelColumnResponse> columns,int sampleRowCount) {
        CellStyle normal=bodyStyle(workbook),alternate=alternateBodyStyle(workbook),moneyNormal=moneyStyle(workbook,false),moneyAlternate=moneyStyle(workbook,true),example=exampleStyle(workbook),moneyExample=moneyExampleStyle(workbook);
        for(int rowIndex=1;rowIndex<=20;rowIndex++) {
            Row row=sheet.getRow(rowIndex);
            if(row==null) row=sheet.createRow(rowIndex);
            row.setHeightInPoints(26);
            for(int columnIndex=0;columnIndex<columns.size();columnIndex++) {
                boolean isAlternate=rowIndex%2==0;Cell cell=row.getCell(columnIndex);
                if(cell==null) cell=row.createCell(columnIndex);
                boolean isExample=rowIndex<=sampleRowCount;
                cell.setCellStyle(isExample?("MONEY".equals(columns.get(columnIndex).type())?moneyExample:example):("MONEY".equals(columns.get(columnIndex).type())?(isAlternate?moneyAlternate:moneyNormal):(isAlternate?alternate:normal)));
            }
        }
        if(sampleRowCount>0) {
            CellStyle label=workbook.createCellStyle();label.cloneStyleFrom(example);label.setFont(font(workbook,true,IndexedColors.DARK_RED.getIndex(),10));
            Cell marker=sheet.getRow(1).getCell(0);marker.setCellStyle(label);
            Drawing<?> drawing=sheet.createDrawingPatriarch();CreationHelper helper=workbook.getCreationHelper();ClientAnchor anchor=helper.createClientAnchor();anchor.setCol1(0);anchor.setCol2(3);anchor.setRow1(1);anchor.setRow2(4);
            Comment comment=drawing.createCellComment(anchor);comment.setString(helper.createRichTextString("DÒNG DỮ LIỆU MINH HỌA: hãy thay hoặc xóa dòng này trước khi nhập dữ liệu thật."));marker.setCellComment(comment);
        }
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0,20,0,columns.size()-1));
    }
    private String sampleValue(ExcelColumnResponse column) {
        String key=column.key().toLowerCase(Locale.ROOT),label=column.label().toLowerCase(Locale.ROOT);
        if(key.contains("email")||label.contains("email"))return "nhanvien.mau@congty.vn";
        if(key.contains("code")||key.endsWith("no"))return "SAMPLE-001";
        if(key.contains("name")||label.contains("tên"))return "Phòng ban minh họa";
        if(key.contains("managerid")||key.endsWith("id"))return "1001";
        if(key.contains("date")||key.endsWith("at"))return "2026-01-15";
        if("MONEY".equals(column.type())||"MONEY_INFO".equals(column.type()))return "25000000";
        if(key.contains("status"))return "DRAFT";
        if(key.contains("description")||key.contains("note"))return "Chi phí hoạt động theo kế hoạch";
        return column.required()?"Giá trị minh họa":"";
    }
    private String friendlyType(String type) { return switch(type){case "MONEY","MONEY_INFO"->"Số tiền (VND)";case "DATE"->"Ngày YYYY-MM-DD";case "NUMBER"->"Số";default->"Văn bản";}; }
    private int columnWidth(ExcelColumnResponse column,List<Map<String,Object>> rows) {
        String key=column.key().toLowerCase(Locale.ROOT);int width=Math.max(column.label().length()+3,column.key().length()+3);
        if(key.contains("description")||key.contains("note")||key.contains("reason"))width=Math.max(width,38);
        else if(key.contains("name")||key.contains("email")||key.contains("title"))width=Math.max(width,28);
        else if("MONEY".equals(column.type())||"MONEY_INFO".equals(column.type()))width=Math.max(width,20);
        else if(key.endsWith("id")||key.contains("status")||key.contains("date"))width=Math.max(width,16);
        for(Map<String,Object> row:rows){Object value=row.get(column.key());if(value!=null)width=Math.max(width,Math.min(42,value.toString().length()+2));}
        return Math.min(48,Math.max(14,width))*256;
    }
    private CellStyle headerStyle(XSSFWorkbook workbook) { CellStyle style=workbook.createCellStyle();style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());style.setFillPattern(FillPatternType.SOLID_FOREGROUND);style.setFont(font(workbook,true,IndexedColors.WHITE.getIndex(),11));style.setVerticalAlignment(VerticalAlignment.CENTER);style.setWrapText(true);setThinBorders(style);return style; }
    private CellStyle titleStyle(XSSFWorkbook workbook) { CellStyle style=workbook.createCellStyle();style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());style.setFillPattern(FillPatternType.SOLID_FOREGROUND);style.setFont(font(workbook,true,IndexedColors.WHITE.getIndex(),16));style.setVerticalAlignment(VerticalAlignment.CENTER);return style; }
    private CellStyle sectionStyle(XSSFWorkbook workbook) { CellStyle style=workbook.createCellStyle();style.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());style.setFillPattern(FillPatternType.SOLID_FOREGROUND);style.setFont(font(workbook,true,IndexedColors.DARK_BLUE.getIndex(),11));style.setVerticalAlignment(VerticalAlignment.CENTER);return style; }
    private CellStyle noteStyle(XSSFWorkbook workbook) { CellStyle style=workbook.createCellStyle();style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());style.setFillPattern(FillPatternType.SOLID_FOREGROUND);style.setFont(font(workbook,false,IndexedColors.DARK_BLUE.getIndex(),10));style.setWrapText(true);style.setVerticalAlignment(VerticalAlignment.CENTER);return style; }
    private CellStyle bodyStyle(XSSFWorkbook workbook) { CellStyle style=workbook.createCellStyle();style.setVerticalAlignment(VerticalAlignment.CENTER);setThinBorders(style);return style; }
    private CellStyle alternateBodyStyle(XSSFWorkbook workbook) { CellStyle style=bodyStyle(workbook);style.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());style.setFillPattern(FillPatternType.SOLID_FOREGROUND);return style; }
    private CellStyle exampleStyle(XSSFWorkbook workbook) { CellStyle style=bodyStyle(workbook);style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());style.setFillPattern(FillPatternType.SOLID_FOREGROUND);return style; }
    private CellStyle moneyExampleStyle(XSSFWorkbook workbook) { CellStyle style=exampleStyle(workbook);style.setAlignment(HorizontalAlignment.RIGHT);style.setDataFormat(workbook.createDataFormat().getFormat("#,##0\" ₫\";[Red]-#,##0\" ₫\""));return style; }
    private CellStyle moneyStyle(XSSFWorkbook workbook,boolean alternate) { CellStyle style=alternate?alternateBodyStyle(workbook):bodyStyle(workbook);style.setAlignment(HorizontalAlignment.RIGHT);style.setDataFormat(workbook.createDataFormat().getFormat("#,##0\" ₫\";[Red]-#,##0\" ₫\""));return style; }
    private CellStyle totalStyle(XSSFWorkbook workbook) { CellStyle style=workbook.createCellStyle();style.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());style.setFillPattern(FillPatternType.SOLID_FOREGROUND);style.setFont(font(workbook,true,IndexedColors.DARK_GREEN.getIndex(),10));setThinBorders(style);return style; }
    private Font font(XSSFWorkbook workbook,boolean bold,short color,int size) { Font font=workbook.createFont();font.setBold(bold);font.setColor(color);font.setFontHeightInPoints((short)size);return font; }
    private void setThinBorders(CellStyle style) { style.setBorderBottom(BorderStyle.THIN);style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());style.setBorderLeft(BorderStyle.THIN);style.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());style.setBorderRight(BorderStyle.THIN);style.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex()); }
    private void writeMoney(Cell cell,BigDecimal amount) {
        if(amount.precision()>15) cell.setCellValue(amount.toPlainString()); else cell.setCellValue(amount.doubleValue());
    }
    private byte[] bytes(XSSFWorkbook workbook) throws IOException {
        var out=new ByteArrayOutputStream();workbook.write(out);return out.toByteArray();
    }
}

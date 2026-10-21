package com.mkwang.backend.modules.spreadsheet.repository;
import com.mkwang.backend.common.exception.BadRequestException;
import org.springframework.data.jpa.domain.Specification;
import java.time.*;
import java.util.Map;
public final class ExcelExportSpecification {
    private ExcelExportSpecification() {}
    public static <T> Specification<T> dimension(String field,Object value) {
        return (root,query,cb)->{
            jakarta.persistence.criteria.Path<?> path=root;
            for(String name:field.split("\\.")) path=path.get(name);
            return value instanceof java.util.Collection<?> values?path.in(values):cb.equal(path,value);
        };
    }
    public static <T> Specification<T> filter(Map<String,String> filters,Map<String,Object> dimensions) {
        Specification<T> spec=filter(filters);
        for(var entry:dimensions.entrySet()) spec=spec.and(dimension(entry.getKey(),entry.getValue()));
        return spec;
    }
    public static <T> Specification<T> from(LocalDate date) {
        return (root,query,cb)->cb.greaterThanOrEqualTo(root.get("createdAt"),date.atStartOfDay());
    }
    public static <T> Specification<T> to(LocalDate date) {
        return (root,query,cb)->cb.lessThan(root.get("createdAt"),date.plusDays(1).atStartOfDay());
    }
    public static <T> Specification<T> filter(Map<String,String> filters) {
        LocalDate from=null,to=null;
        try {
            if(filters.containsKey("from")&&!filters.get("from").isBlank()) from=LocalDate.parse(filters.get("from"));
            if(filters.containsKey("to")&&!filters.get("to").isBlank()) to=LocalDate.parse(filters.get("to"));
        } catch(java.time.format.DateTimeParseException error) { throw new BadRequestException("Bộ lọc ngày không hợp lệ"); }
        if(from!=null&&to!=null&&from.isAfter(to)) throw new BadRequestException("Từ ngày phải trước đến ngày");
        Specification<T> spec=Specification.where(null);
        if(from!=null) spec=spec.and(from(from)); if(to!=null) spec=spec.and(to(to));return spec;
    }
}

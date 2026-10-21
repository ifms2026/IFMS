package com.mkwang.backend.modules.spreadsheet.service;

import com.mkwang.backend.modules.spreadsheet.dto.response.*;
import com.mkwang.backend.modules.user.entity.User;
import java.util.*;

/** Domain adapters own authorization, validation and business-service mutations. */
public interface ExcelDataset {
    String code();
    String label();
    Set<String> roles();
    List<ExcelColumnResponse> columns();
    default boolean importEnabled() { return true; }
    default boolean supportsDateFilter() { return true; }
    default String scopeLabel() { return null; }
    void authorize(User actor, Long scope);
    default List<Map<String,Object>> scopes(User actor) { return List.of(); }
    default List<Map<String,Object>> references(User actor, Long scope) { return List.of(); }
    default List<ExcelRowErrorResponse> validate(List<ExcelRowResponse> rows, User actor, Long scope) { return List.of(); }
    default String commit(ExcelRowResponse row, User actor, Long scope) {
        throw new com.mkwang.backend.common.exception.BadRequestException("Bộ dữ liệu chỉ hỗ trợ xuất");
    }
    default void beforeCommit(List<ExcelRowResponse> rows, User actor, Long scope) {}
    default List<ExcelRowResponse> orderForCommit(List<ExcelRowResponse> rows, Long scope) { return rows; }
    default List<ExcelColumnResponse> exportColumns() { return columns(); }
    List<Map<String,Object>> export(User actor, Long scope, Map<String,String> filters, int maxRows);
}

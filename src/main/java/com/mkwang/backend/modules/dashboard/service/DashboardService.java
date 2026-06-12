package com.mkwang.backend.modules.dashboard.service;

import com.mkwang.backend.modules.dashboard.dto.response.AccountantDashboardResponse;
import com.mkwang.backend.modules.dashboard.dto.response.AdminAnalyticsResponse;
import com.mkwang.backend.modules.dashboard.dto.response.AdminDashboardResponse;
import com.mkwang.backend.modules.dashboard.dto.response.CashFlowAnalyticsResponse;
import com.mkwang.backend.modules.dashboard.dto.response.CfoDashboardResponse;
import com.mkwang.backend.modules.dashboard.dto.response.EmployeeSpendingAnalyticsResponse;
import com.mkwang.backend.modules.dashboard.dto.response.ManagerDashboardResponse;

public interface DashboardService {

    ManagerDashboardResponse getManagerDashboard(Long managerId);

    AccountantDashboardResponse getAccountantDashboard();

    CfoDashboardResponse getCfoDashboard();

    AdminDashboardResponse getAdminDashboard();

    CashFlowAnalyticsResponse getCashFlowAnalytics(String period, String unit);

    AdminAnalyticsResponse getAdminAnalytics();

    EmployeeSpendingAnalyticsResponse getEmployeeSpendingAnalytics(Long userId);
}

package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.security.ApiAuthenticationInterceptor;
import com.assistantrh.assistant_rh_api.service.AssistantInsightsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/assistant/insights")
public class AssistantInsightsController {

    private final AssistantInsightsService insightsService;

    public AssistantInsightsController(AssistantInsightsService insightsService) {
        this.insightsService = insightsService;
    }

    @GetMapping("/organization")
    public Map<String, Object> organizationOverview(
            @RequestAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE)
            Integer userId
    ) {
        return insightsService.organizationOverview(userId);
    }

    @GetMapping("/employees-by-position")
    public List<Map<String, Object>> employeesByPosition(
            @RequestAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE)
            Integer userId,
            @RequestParam(defaultValue = "") String query
    ) {
        return insightsService.employeesByPosition(userId, query);
    }

    @GetMapping("/highest-degree")
    public List<Map<String, Object>> highestDegreeEmployees(
            @RequestAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE)
            Integer userId
    ) {
        return insightsService.highestDegreeEmployees(userId);
    }

    @GetMapping("/recent-assignments")
    public List<Map<String, Object>> recentAssignments(
            @RequestAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE)
            Integer userId,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return insightsService.recentAssignments(userId, limit);
    }
}

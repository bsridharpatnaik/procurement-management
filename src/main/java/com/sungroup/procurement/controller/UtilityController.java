package com.sungroup.procurement.controller;

import com.sungroup.procurement.constants.ProjectConstants;
import com.sungroup.procurement.dto.response.ApiResponse;
import com.sungroup.procurement.entity.enums.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping(ProjectConstants.API_BASE_PATH + "/utils")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Utility APIs", description = "APIs for dropdown values and enums")
public class UtilityController {

    @Operation(summary = "Get all priority options")
    @GetMapping("/priorities")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getPriorities() {
        List<Map<String, Object>> priorities = Arrays.stream(Priority.values())
                .map(priority -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("value", priority.name());
                    map.put("label", priority.name());
                    return map;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Priorities fetched", priorities));
    }


    @Operation(summary = "Get all procurement statuses")
    @GetMapping("/procurement-statuses")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getProcurementStatuses() {
        List<Map<String, Object>> statuses = Arrays.stream(ProcurementStatus.values())
                .map(status -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("value", status.name());
                    map.put("label", status.name());
                    return map;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Statuses fetched", statuses));
    }

    @Operation(summary = "Get all user roles")
    @GetMapping("/user-roles")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getUserRoles() {
        List<Map<String, Object>> roles = Arrays.stream(UserRole.values())
                .map(role -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("value", role.name());
                    map.put("label", role.name());
                    return map;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("User roles fetched", roles));
    }

    @Operation(summary = "Get all line item statuses")
    @GetMapping("/line-item-statuses")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getLineItemStatuses() {
        List<Map<String, Object>> statuses = Arrays.stream(LineItemStatus.values())
                .map(status -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("value", status.name());
                    map.put("label", status.name());
                    return map;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Line item statuses fetched", statuses));
    }

    @Operation(summary = "Get return statuses")
    @GetMapping("/return-statuses")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getReturnStatuses() {
        List<Map<String, Object>> statuses = Arrays.stream(ReturnStatus.values())
                .map(status -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("value", status.name());
                    map.put("label", status.name());
                    return map;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Return statuses fetched", statuses));
    }

}
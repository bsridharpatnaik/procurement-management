package com.sungroup.procurement.controller;

import com.sungroup.procurement.constants.ProjectConstants;
import com.sungroup.procurement.dto.request.FilterDataList;
import com.sungroup.procurement.dto.response.ApiResponse;
import com.sungroup.procurement.entity.ProcurementRequest;
import com.sungroup.procurement.entity.enums.ProcurementStatus;
import com.sungroup.procurement.service.ProcurementRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping(ProjectConstants.API_BASE_PATH + "/procurement-requests")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Procurement Request Management", description = "APIs for managing procurement requests in the system")
public class ProcurementRequestController {

    private final ProcurementRequestService procurementRequestService;

    @Operation(
            summary = "Get all procurement requests with filtering and pagination",
            description = "Retrieve procurement requests with optional filtering. Supports pagination and sorting.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Filter criteria (optional)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = FilterDataList.class)
                    )
            )
    )
    @PostMapping
    public ResponseEntity<ApiResponse<List<ProcurementRequest>>> getAllProcurementRequests(
            @RequestBody(required = false) FilterDataList filterDataList,
            @Parameter(description = "Pagination and sorting parameters")
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        log.info("Fetching procurement requests with pagination: {}", pageable);
        ApiResponse<List<ProcurementRequest>> response = procurementRequestService.findRequestsWithFilters(filterDataList, pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get procurement request by ID",
            description = "Retrieve a specific procurement request by its unique identifier"
    )
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProcurementRequest>> getProcurementRequestById(
            @Parameter(description = "Procurement Request ID", required = true, example = "1")
            @PathVariable Long id) {

        log.info("Fetching procurement request by id: {}", id);
        ApiResponse<ProcurementRequest> response = procurementRequestService.findById(id);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get procurement request by request number",
            description = "Retrieve a specific procurement request by its request number"
    )
    @GetMapping("/number/{requestNumber}")
    public ResponseEntity<ApiResponse<ProcurementRequest>> getProcurementRequestByNumber(
            @Parameter(description = "Request Number", required = true, example = "REQ-TC-2025-001")
            @PathVariable String requestNumber) {

        log.info("Fetching procurement request by number: {}", requestNumber);
        ApiResponse<ProcurementRequest> response = procurementRequestService.findByRequestNumber(requestNumber);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get unassigned procurement requests",
            description = "Retrieve all procurement requests that are not assigned to any purchase team member"
    )
    @GetMapping("/unassigned")
    public ResponseEntity<ApiResponse<List<ProcurementRequest>>> getUnassignedRequests() {
        log.info("Fetching unassigned procurement requests");
        ApiResponse<List<ProcurementRequest>> response = procurementRequestService.findUnassignedRequests();
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get requests requiring approval",
            description = "Retrieve all procurement requests that require management approval"
    )
    @GetMapping("/requiring-approval")
    public ResponseEntity<ApiResponse<List<ProcurementRequest>>> getRequestsRequiringApproval() {
        log.info("Fetching requests requiring approval");
        ApiResponse<List<ProcurementRequest>> response = procurementRequestService.findRequestsRequiringApproval();
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Create new procurement request",
            description = "Create a new procurement request with line items",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Procurement request details with line items",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProcurementRequest.class)
                    )
            )
    )
    @PostMapping("/create")
    public ResponseEntity<ApiResponse<ProcurementRequest>> createProcurementRequest(
            @Valid @RequestBody ProcurementRequest procurementRequest) {

        log.info("Creating new procurement request for factory: {}",
                procurementRequest.getFactory() != null ? procurementRequest.getFactory().getId() : "unknown");

        ApiResponse<ProcurementRequest> response = procurementRequestService.createProcurementRequest(procurementRequest);

        if (response.isSuccess()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    @Operation(
            summary = "Update procurement request",
            description = "Update an existing procurement request's details"
    )
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProcurementRequest>> updateProcurementRequest(
            @Parameter(description = "Procurement Request ID", required = true)
            @PathVariable Long id,
            @Valid @RequestBody ProcurementRequest requestDetails) {

        log.info("Updating procurement request with id: {}", id);
        ApiResponse<ProcurementRequest> response = procurementRequestService.updateProcurementRequest(id, requestDetails);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    @Operation(
            summary = "Update procurement request status",
            description = "Update the status of a procurement request"
    )
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<ProcurementRequest>> updateStatus(
            @Parameter(description = "Procurement Request ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "New status", required = true)
            @RequestParam ProcurementStatus status) {

        log.info("Updating status for procurement request id: {} to: {}", id, status);
        ApiResponse<ProcurementRequest> response = procurementRequestService.updateStatus(id, status);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    @Operation(
            summary = "Assign procurement request to user",
            description = "Assign a procurement request to a purchase team member"
    )
    @PatchMapping("/{id}/assign")
    public ResponseEntity<ApiResponse<ProcurementRequest>> assignToUser(
            @Parameter(description = "Procurement Request ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "User ID to assign to", required = true)
            @RequestParam Long userId) {

        log.info("Assigning procurement request id: {} to user id: {}", id, userId);
        ApiResponse<ProcurementRequest> response = procurementRequestService.assignToUser(id, userId);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    @Operation(
            summary = "Approve procurement request",
            description = "Approve a procurement request that requires management approval"
    )
    @PatchMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<ProcurementRequest>> approveRequest(
            @Parameter(description = "Procurement Request ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "Approver User ID", required = true)
            @RequestParam Long approverId) {

        log.info("Approving procurement request id: {} by user id: {}", id, approverId);
        ApiResponse<ProcurementRequest> response = procurementRequestService.approveRequest(id, approverId);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    @Operation(
            summary = "Delete procurement request",
            description = "Soft delete a procurement request (only allowed for draft requests)"
    )
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deleteProcurementRequest(
            @Parameter(description = "Procurement Request ID", required = true)
            @PathVariable Long id) {

        log.info("Deleting procurement request with id: {}", id);
        ApiResponse<String> response = procurementRequestService.deleteProcurementRequestRestricted(id);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }
}
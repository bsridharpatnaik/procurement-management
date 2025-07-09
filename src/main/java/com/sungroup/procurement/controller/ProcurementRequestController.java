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
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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
                    description = "Filter criteria (optional). Available filter keywords: " +
                            "• requestNumber - Filter by request number (partial match) " +
                            "• status - Filter by status (DRAFT, SUBMITTED, IN_PROGRESS, ORDERED, DISPATCHED, RECEIVED, CLOSED) " +
                            "• priority - Filter by priority (HIGH, MEDIUM, LOW) " +
                            "• factoryId - Filter by factory ID (exact match) " +
                            "• factoryName - Filter by factory name (partial match) " +
                            "• assignedTo - Filter by assigned user ID " +
                            "• requiresApproval - Filter by approval requirement (true/false) " +
                            "• approvedBy - Filter by approver user ID " +
                            "• isShortClosed - Filter by short closed status (true/false) " +
                            "• createdBy - Filter by creator username " +
                            "• materialId - Filter by material ID in line items " +
                            "• materialName - Filter by material name in line items " +
                            "• vendorId - Filter by vendor ID in line items " +
                            "• pendingDays - Filter requests older than X days " +
                            "• startDate - Filter by creation start date (yyyy-MM-dd HH:mm:ss) " +
                            "• endDate - Filter by creation end date (yyyy-MM-dd HH:mm:ss) " +
                            "• expectedStartDate - Filter by expected delivery start date (yyyy-MM-dd HH:mm:ss) " +
                            "• expectedEndDate - Filter by expected delivery end date (yyyy-MM-dd HH:mm:ss)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = FilterDataList.class),
                            examples = @ExampleObject(
                                    name = "Filter by status and factory",
                                    value = "{\n" +
                                            "  \"filterData\": [\n" +
                                            "    {\"attrName\": \"status\", \"attrValue\": [\"IN_PROGRESS\", \"ORDERED\"]},\n" +
                                            "    {\"attrName\": \"factoryId\", \"attrValue\": [\"1\"]}\n" +
                                            "  ]\n" +
                                            "}"
                            )
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

    @Operation(
            summary = "Cancel procurement request",
            description = "Cancel an entire procurement request. Only allowed for non-dispatched requests."
    )
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<ProcurementRequest>> cancelProcurementRequest(
            @Parameter(description = "Procurement Request ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "Cancellation reason", required = true)
            @RequestParam String cancellationReason) {

        log.info("Cancelling procurement request: {} with reason: {}", id, cancellationReason);
        ApiResponse<ProcurementRequest> response = procurementRequestService.cancelProcurementRequest(id, cancellationReason);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    @Operation(
            summary = "Remove line item from request",
            description = "Remove a specific line item from procurement request. Only allowed for DRAFT/SUBMITTED requests without vendor assignments."
    )
    @DeleteMapping("/{id}/line-items/{lineItemId}")
    public ResponseEntity<ApiResponse<ProcurementRequest>> removeLineItem(
            @Parameter(description = "Procurement Request ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "Line Item ID", required = true)
            @PathVariable Long lineItemId,
            @Parameter(description = "Removal reason", required = true)
            @RequestParam String removalReason) {

        log.info("Removing line item: {} from request: {} with reason: {}", lineItemId, id, removalReason);
        ApiResponse<ProcurementRequest> response = procurementRequestService.removeLineItem(id, lineItemId, removalReason);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    @Operation(summary = "Submit procurement request")
    @PostMapping("/{id}/submit")
    public ResponseEntity<ApiResponse<ProcurementRequest>> submitRequest(@PathVariable Long id) {
        log.info("Submitting procurement request: {}", id);
        ApiResponse<ProcurementRequest> response = procurementRequestService.submitRequest(id);
        return response.isSuccess() ? ResponseEntity.ok(response) : ResponseEntity.badRequest().body(response);
    }

    @Operation(summary = "Mark request for approval")
    @PatchMapping("/{id}/mark-for-approval")
    public ResponseEntity<ApiResponse<ProcurementRequest>> markForApproval(@PathVariable Long id) {
        log.info("Marking request for approval: {}", id);
        ApiResponse<ProcurementRequest> response = procurementRequestService.markForApproval(id);
        return response.isSuccess() ? ResponseEntity.ok(response) : ResponseEntity.badRequest().body(response);
    }

    @Operation(summary = "Mark request as received")
    @PatchMapping("/{id}/receive")
    public ResponseEntity<ApiResponse<ProcurementRequest>> receiveRequest(
            @PathVariable Long id,
            @RequestBody Map<Long, BigDecimal> actualQuantities) {
        log.info("Marking request as received: {}", id);
        ApiResponse<ProcurementRequest> response = procurementRequestService.receiveRequest(id, actualQuantities);
        return response.isSuccess() ? ResponseEntity.ok(response) : ResponseEntity.badRequest().body(response);
    }

    @Operation(summary = "Close procurement request")
    @PatchMapping("/{id}/close")
    public ResponseEntity<ApiResponse<ProcurementRequest>> closeRequest(@PathVariable Long id) {
        log.info("Closing procurement request: {}", id);
        ApiResponse<ProcurementRequest> response = procurementRequestService.closeRequest(id);
        return response.isSuccess() ? ResponseEntity.ok(response) : ResponseEntity.badRequest().body(response);
    }

    @Operation(summary = "Get dashboard summary")
    @GetMapping("/dashboard-summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDashboardSummary() {
        log.info("Fetching dashboard summary");
        ApiResponse<Map<String, Object>> response = procurementRequestService.getDashboardSummary();
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Check if user can edit request")
    @GetMapping("/{id}/can-edit")
    public ResponseEntity<ApiResponse<Boolean>> canEditRequest(@PathVariable Long id) {
        log.info("Checking edit permissions for request: {}", id);
        // Implementation needed in service
        return ResponseEntity.ok(ApiResponse.success("Edit permission checked", true));
    }

    @Operation(summary = "Create and submit procurement request directly (Purchase Team)",
            description = "Purchase team can create and submit requests directly, bypassing draft status")
    @PostMapping("/create-and-submit")
    public ResponseEntity<ApiResponse<ProcurementRequest>> createAndSubmitRequest(
            @Valid @RequestBody ProcurementRequest request) {

        log.info("Purchase team creating and submitting request directly for factory: {}",
                request.getFactory() != null ? request.getFactory().getId() : "null");

        ApiResponse<ProcurementRequest> response = procurementRequestService.createAndSubmitRequest(request);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    @Operation(summary = "Assign vendor to line item",
            description = "Assign vendor and price to a specific line item")
    @PutMapping("/{requestId}/line-items/{lineItemId}/assign-vendor")
    public ResponseEntity<ApiResponse<ProcurementRequest>> assignVendorToLineItem(
            @PathVariable Long requestId,
            @PathVariable Long lineItemId,
            @RequestParam Long vendorId,
            @RequestParam BigDecimal price) {

        log.info("Assigning vendor {} to line item {} in request {}", vendorId, lineItemId, requestId);

        ApiResponse<ProcurementRequest> response = procurementRequestService.assignVendorToLineItem(
                requestId, lineItemId, vendorId, price);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }
}
package com.sungroup.procurement.controller;

import com.sungroup.procurement.constants.ProjectConstants;
import com.sungroup.procurement.dto.response.ApiResponse;
import com.sungroup.procurement.entity.ReturnRequest;
import com.sungroup.procurement.service.ReturnRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping(ProjectConstants.API_BASE_PATH + "/return-requests")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Return Request Management", description = "APIs for managing return requests in the procurement system")
public class ReturnRequestController {

    private final ReturnRequestService returnRequestService;

    @Operation(
            summary = "Get all return requests",
            description = "Retrieve all return requests with factory-based access control"
    )
    @GetMapping
    public ResponseEntity<ApiResponse<List<ReturnRequest>>> getAllReturnRequests() {
        log.info("Fetching all return requests");
        ApiResponse<List<ReturnRequest>> response = returnRequestService.getAllReturnRequests();
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get pending return requests",
            description = "Retrieve all return requests that are pending approval"
    )
    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<ReturnRequest>>> getPendingReturnRequests() {
        log.info("Fetching pending return requests");
        ApiResponse<List<ReturnRequest>> response = returnRequestService.getPendingReturnRequests();
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get return request by ID",
            description = "Retrieve a specific return request by its unique identifier"
    )
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ReturnRequest>> getReturnRequestById(
            @Parameter(description = "Return Request ID", required = true, example = "1")
            @PathVariable Long id) {

        log.info("Fetching return request by id: {}", id);
        ApiResponse<ReturnRequest> response = returnRequestService.findById(id);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get return requests by line item ID",
            description = "Retrieve all return requests for a specific procurement line item"
    )
    @GetMapping("/line-item/{lineItemId}")
    public ResponseEntity<ApiResponse<List<ReturnRequest>>> getReturnRequestsByLineItem(
            @Parameter(description = "Line Item ID", required = true, example = "1")
            @PathVariable Long lineItemId) {

        log.info("Fetching return requests for line item: {}", lineItemId);
        ApiResponse<List<ReturnRequest>> response = returnRequestService.findByLineItemId(lineItemId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Create return request",
            description = "Create a new return request for a received line item. Only factory users can create return requests."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Return request created successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid request data or business rule violation"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied - only factory users can create return requests"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Line item not found"
            )
    })
    @PostMapping("/create")
    public ResponseEntity<ApiResponse<ReturnRequest>> createReturnRequest(
            @Parameter(description = "Line Item ID", required = true, example = "1")
            @RequestParam Long lineItemId,
            @Parameter(description = "Return Quantity", required = true, example = "5.5")
            @RequestParam BigDecimal returnQuantity,
            @Parameter(description = "Return Reason", required = true, example = "Defective material")
            @RequestParam String returnReason) {

        log.info("Creating return request for line item: {} with quantity: {}", lineItemId, returnQuantity);
        ApiResponse<ReturnRequest> response = returnRequestService.createReturnRequest(lineItemId, returnQuantity, returnReason);

        if (response.isSuccess()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    @Operation(
            summary = "Approve return request",
            description = "Approve a pending return request. Only purchase team and management can approve return requests."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Return request approved successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid request or business rule violation"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied - only purchase team and management can approve"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Return request not found"
            )
    })
    @PatchMapping("/{returnRequestId}/approve")
    public ResponseEntity<ApiResponse<ReturnRequest>> approveReturnRequest(
            @Parameter(description = "Return Request ID", required = true, example = "1")
            @PathVariable Long returnRequestId,
            @Parameter(description = "Approver User ID", required = true, example = "2")
            @RequestParam Long approverId) {

        log.info("Approving return request: {} by user: {}", returnRequestId, approverId);
        ApiResponse<ReturnRequest> response = returnRequestService.approveReturnRequest(returnRequestId, approverId);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    @Operation(
            summary = "Get my factory return requests",
            description = "Get all return requests for factories assigned to current factory user"
    )
    @GetMapping("/my-factory")
    public ResponseEntity<ApiResponse<List<ReturnRequest>>> getMyFactoryReturnRequests() {
        log.info("Fetching return requests for current user's factories");
        ApiResponse<List<ReturnRequest>> response = returnRequestService.getMyFactoryReturnRequests();
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Check if procurement request can be closed",
            description = "Check if a procurement request can be closed considering pending return requests"
    )
    @GetMapping("/can-close-request/{procurementRequestId}")
    public ResponseEntity<ApiResponse<Boolean>> canCloseProcurementRequest(
            @Parameter(description = "Procurement Request ID", required = true, example = "1")
            @PathVariable Long procurementRequestId) {

        log.info("Checking if procurement request {} can be closed", procurementRequestId);
        boolean canClose = returnRequestService.canCloseProcurementRequest(procurementRequestId);

        String message = canClose ?
                "Procurement request can be closed" :
                "Procurement request cannot be closed due to pending return requests";

        ApiResponse<Boolean> response = ApiResponse.success(message, canClose);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get pending return requests count for line item",
            description = "Get the count of pending return requests for a specific line item"
    )
    @GetMapping("/pending-count/line-item/{lineItemId}")
    public ResponseEntity<ApiResponse<Long>> getPendingReturnRequestsCount(
            @Parameter(description = "Line Item ID", required = true, example = "1")
            @PathVariable Long lineItemId) {

        log.info("Getting pending return requests count for line item: {}", lineItemId);
        long count = returnRequestService.getPendingReturnRequestsCount(lineItemId);

        ApiResponse<Long> response = ApiResponse.success("Pending return requests count retrieved", count);
        return ResponseEntity.ok(response);
    }
}
package com.sungroup.procurement.controller;

import com.sungroup.procurement.constants.ProjectConstants;
import com.sungroup.procurement.dto.response.ApiResponse;
import com.sungroup.procurement.entity.ProcurementLineItem;
import com.sungroup.procurement.entity.enums.LineItemStatus;
import com.sungroup.procurement.service.ProcurementLineItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping(ProjectConstants.API_BASE_PATH + "/line-items")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Procurement Line Item Management", description = "APIs for managing procurement line items")
public class ProcurementLineItemController {

    private final ProcurementLineItemService lineItemService;

    @Operation(
            summary = "Get line items by procurement request ID",
            description = "Retrieve all line items for a specific procurement request with factory-based access control"
    )
    @GetMapping("/request/{requestId}")
    public ResponseEntity<ApiResponse<List<ProcurementLineItem>>> getLineItemsByRequestId(
            @Parameter(description = "Procurement Request ID", required = true, example = "1")
            @PathVariable Long requestId) {

        log.info("Fetching line items for procurement request: {}", requestId);
        ApiResponse<List<ProcurementLineItem>> response = lineItemService.findByProcurementRequestId(requestId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get line item by ID",
            description = "Retrieve a specific line item by its unique identifier"
    )
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProcurementLineItem>> getLineItemById(
            @Parameter(description = "Line Item ID", required = true, example = "1")
            @PathVariable Long id) {

        log.info("Fetching line item by id: {}", id);
        ApiResponse<ProcurementLineItem> response = lineItemService.findById(id);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Assign vendor and price to line item",
            description = "Assign a vendor and set price for a line item. Only purchase team and management can perform this operation."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Vendor and price assigned successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid request data or business rule violation"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied - only purchase team can assign vendors"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Line item or vendor not found"
            )
    })
    @PatchMapping("/{lineItemId}/assign-vendor")
    public ResponseEntity<ApiResponse<ProcurementLineItem>> assignVendorAndPrice(
            @Parameter(description = "Line Item ID", required = true, example = "1")
            @PathVariable Long lineItemId,
            @Parameter(description = "Vendor ID", required = true, example = "2")
            @RequestParam Long vendorId,
            @Parameter(description = "Price", required = true, example = "150.75")
            @RequestParam BigDecimal price) {

        log.info("Assigning vendor {} and price {} to line item: {}", vendorId, price, lineItemId);
        ApiResponse<ProcurementLineItem> response = lineItemService.assignVendorAndPrice(lineItemId, vendorId, price);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    @Operation(
            summary = "Update line item status",
            description = "Update the status of a line item. Different roles can update to different statuses."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Line item status updated successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid status transition"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied - insufficient permissions for this status change"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Line item not found"
            )
    })
    @PatchMapping("/{lineItemId}/status")
    public ResponseEntity<ApiResponse<ProcurementLineItem>> updateLineItemStatus(
            @Parameter(description = "Line Item ID", required = true, example = "1")
            @PathVariable Long lineItemId,
            @Parameter(description = "New Status", required = true, example = "ORDERED")
            @RequestParam LineItemStatus status) {

        log.info("Updating line item {} status to: {}", lineItemId, status);
        ApiResponse<ProcurementLineItem> response = lineItemService.updateLineItemStatus(lineItemId, status);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    @Operation(
            summary = "Short close line item",
            description = "Short close a line item when vendor cannot supply. Only purchase team and management can perform this operation."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Line item short closed successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid request or business rule violation"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied - only purchase team can short close"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Line item not found"
            )
    })
    @PatchMapping("/{lineItemId}/short-close")
    public ResponseEntity<ApiResponse<ProcurementLineItem>> shortCloseLineItem(
            @Parameter(description = "Line Item ID", required = true, example = "1")
            @PathVariable Long lineItemId,
            @Parameter(description = "Short Close Reason", required = true, example = "Vendor discontinued product")
            @RequestParam String shortCloseReason) {

        log.info("Short closing line item: {} with reason: {}", lineItemId, shortCloseReason);
        ApiResponse<ProcurementLineItem> response = lineItemService.shortCloseLineItem(lineItemId, shortCloseReason);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    @Operation(
            summary = "Receive line item",
            description = "Mark line item as received with actual quantity. Only factory users can perform this operation."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Line item received successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid quantity or business rule violation"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied - only factory users can receive line items"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Line item not found"
            )
    })
    @PatchMapping("/{lineItemId}/receive")
    public ResponseEntity<ApiResponse<ProcurementLineItem>> receiveLineItem(
            @Parameter(description = "Line Item ID", required = true, example = "1")
            @PathVariable Long lineItemId,
            @Parameter(description = "Actual Quantity Received", required = true, example = "95.5")
            @RequestParam BigDecimal actualQuantity) {

        log.info("Receiving line item: {} with actual quantity: {}", lineItemId, actualQuantity);
        ApiResponse<ProcurementLineItem> response = lineItemService.receiveLineItem(lineItemId, actualQuantity);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }
}
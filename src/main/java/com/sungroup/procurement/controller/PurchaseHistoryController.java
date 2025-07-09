package com.sungroup.procurement.controller;

import com.sungroup.procurement.dto.PurchaseHistoryDto;
import com.sungroup.procurement.dto.VendorPurchaseSummaryDto;
import com.sungroup.procurement.service.PurchaseHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RestController;
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
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping(ProjectConstants.API_BASE_PATH + "/purchase-history")
@Slf4j
public class PurchaseHistoryController {

    @Autowired
    private PurchaseHistoryService purchaseHistoryService;

    @Operation(summary = "Get purchase history with filters")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<PurchaseHistoryDto>>> getPurchaseHistory(
            @RequestParam(required = false) Long materialId,
            @RequestParam(required = false) Long vendorId,
            @RequestParam(required = false) Long factoryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "purchaseDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        log.info("Fetching purchase history with filters - Material: {}, Vendor: {}, Factory: {}",
                materialId, vendorId, factoryId);

        ApiResponse<Page<PurchaseHistoryDto>> response = purchaseHistoryService.getPurchaseHistory(
                materialId, vendorId, factoryId, startDate, endDate, page, size, sortBy, sortDir);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get vendor purchase summary (grouped view)")
    @GetMapping("/vendor-summary")
    public ResponseEntity<ApiResponse<Page<VendorPurchaseSummaryDto>>> getVendorPurchaseSummary(
            @RequestParam(required = false) Long factoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("Fetching vendor purchase summary for factory: {}", factoryId);

        ApiResponse<Page<VendorPurchaseSummaryDto>> response = purchaseHistoryService.getVendorPurchaseSummary(
                factoryId, page, size);

        return ResponseEntity.ok(response);
    }
}

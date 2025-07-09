package com.sungroup.procurement.service;

import com.sungroup.procurement.constants.ProjectConstants;
import com.sungroup.procurement.dto.PurchaseHistoryDto;
import com.sungroup.procurement.dto.VendorPurchaseSummaryDto;
import com.sungroup.procurement.dto.response.ApiResponse;
import com.sungroup.procurement.entity.ProcurementLineItem;
import com.sungroup.procurement.entity.PurchaseHistory;
import com.sungroup.procurement.repository.PurchaseHistoryRepository;
import com.sungroup.procurement.util.SecurityUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
@Slf4j
public class PurchaseHistoryService {

    @Autowired
    private PurchaseHistoryRepository purchaseHistoryRepository;

    public void createPurchaseHistory(ProcurementLineItem lineItem) {
        try {
            if (lineItem.getAssignedVendor() == null || lineItem.getAssignedPrice() == null) {
                return;
            }

            PurchaseHistory history = new PurchaseHistory();
            history.setMaterial(lineItem.getMaterial());
            history.setVendor(lineItem.getAssignedVendor());
            history.setFactory(lineItem.getProcurementRequest().getFactory());
            history.setProcurementLineItem(lineItem);
            history.setPurchaseDate(LocalDateTime.now());
            history.setQuantity(lineItem.getRequestedQuantity());
            history.setUnitPrice(lineItem.getAssignedPrice());
            history.setTotalAmount(lineItem.getAssignedPrice().multiply(lineItem.getRequestedQuantity()));
            history.setOrderedBy(SecurityUtil.getCurrentUsername());
            history.setRequestNumber(lineItem.getProcurementRequest().getRequestNumber());

            purchaseHistoryRepository.save(history);
            log.info("Created purchase history for request: {}", lineItem.getProcurementRequest().getRequestNumber());
        } catch (Exception e) {
            log.error("Error creating purchase history", e);
        }
    }

    public ApiResponse<Page<PurchaseHistoryDto>> getPurchaseHistory(
            Long materialId, Long vendorId, Long factoryId,
            LocalDateTime startDate, LocalDateTime endDate,
            int page, int size, String sortBy, String sortDir) {
        try {
            // Validate factory access for factory users
            if (SecurityUtil.isCurrentUserFactoryUser()) {
                List<Long> accessibleFactories = SecurityUtil.getCurrentUserAccessibleFactoryIds();
                if (factoryId != null && !accessibleFactories.contains(factoryId)) {
                    return ApiResponse.error("Access denied to factory data");
                }
            }

            Sort sort = Sort.by(sortDir.equals("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, sortBy);
            Pageable pageable = PageRequest.of(page, size, sort);

            Page<PurchaseHistory> historyPage = purchaseHistoryRepository.findPurchaseHistoryWithFilters(
                    materialId, vendorId, factoryId, startDate, endDate, pageable);

            Page<PurchaseHistoryDto> dtoPage = historyPage.map(this::convertToDto);
            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, dtoPage);
        } catch (Exception e) {
            log.error("Error fetching purchase history", e);
            return ApiResponse.error("Failed to fetch purchase history");
        }
    }

    public ApiResponse<Page<VendorPurchaseSummaryDto>> getVendorPurchaseSummary(
            Long factoryId, int page, int size) {
        try {
            // Validate factory access for factory users
            if (SecurityUtil.isCurrentUserFactoryUser()) {
                List<Long> accessibleFactories = SecurityUtil.getCurrentUserAccessibleFactoryIds();
                if (factoryId != null && !accessibleFactories.contains(factoryId)) {
                    return ApiResponse.error("Access denied to factory data");
                }
            }

            Pageable pageable = PageRequest.of(page, size);
            Page<VendorPurchaseSummaryDto> summaryPage = purchaseHistoryRepository.findVendorPurchaseSummary(factoryId, pageable);

            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, summaryPage);
        } catch (Exception e) {
            log.error("Error fetching vendor purchase summary", e);
            return ApiResponse.error("Failed to fetch vendor purchase summary");
        }
    }

    private PurchaseHistoryDto convertToDto(PurchaseHistory history) {
        PurchaseHistoryDto dto = new PurchaseHistoryDto();
        dto.setId(history.getId());
        dto.setMaterialName(history.getMaterial().getName());
        dto.setVendorName(history.getVendor().getName());
        dto.setFactoryName(history.getFactory().getName());
        dto.setContactPersonName(history.getVendor().getContactPersonName());
        dto.setPurchaseDate(history.getPurchaseDate());
        dto.setQuantity(history.getQuantity());
        dto.setUnit(history.getMaterial().getUnit());
        dto.setUnitPrice(history.getUnitPrice());
        dto.setTotalAmount(history.getTotalAmount());
        dto.setOrderedBy(history.getOrderedBy());
        dto.setRequestNumber(history.getRequestNumber());
        return dto;
    }
}
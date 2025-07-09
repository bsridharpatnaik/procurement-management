package com.sungroup.procurement.service;

import com.sungroup.procurement.entity.MaterialVendorHistory;
import com.sungroup.procurement.entity.ProcurementLineItem;
import com.sungroup.procurement.repository.MaterialVendorHistoryRepository;
import com.sungroup.procurement.util.SecurityUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Transactional
@Slf4j
public class MaterialVendorHistoryService {

    @Autowired
    private MaterialVendorHistoryRepository historyRepository;

    public void updateMaterialVendorHistory(ProcurementLineItem lineItem) {
        try {
            if (lineItem.getAssignedVendor() == null || lineItem.getAssignedPrice() == null) {
                return;
            }

            Long materialId = lineItem.getMaterial().getId();
            Long vendorId = lineItem.getAssignedVendor().getId();
            Long factoryId = lineItem.getProcurementRequest().getFactory().getId();

            Optional<MaterialVendorHistory> existingHistory = historyRepository
                    .findByMaterialIdAndVendorIdAndFactoryId(materialId, vendorId, factoryId);

            if (existingHistory.isPresent()) {
                // Update existing history
                MaterialVendorHistory history = existingHistory.get();
                history.setLastOrderedDate(LocalDateTime.now());
                history.setLastPrice(lineItem.getAssignedPrice());
                history.setOrderCount(history.getOrderCount() + 1);
                historyRepository.save(history);
            } else {
                // Create new history record
                MaterialVendorHistory history = new MaterialVendorHistory();
                history.setMaterial(lineItem.getMaterial());
                history.setVendor(lineItem.getAssignedVendor());
                history.setFactoryId(factoryId);
                history.setLastOrderedDate(LocalDateTime.now());
                history.setLastPrice(lineItem.getAssignedPrice());
                history.setOrderCount(1);
                history.setCreatedBy(SecurityUtil.getCurrentUsername());
                historyRepository.save(history);
            }

            log.info("Updated material-vendor history for material: {}, vendor: {}", materialId, vendorId);
        } catch (Exception e) {
            log.error("Error updating material-vendor history", e);
        }
    }
}
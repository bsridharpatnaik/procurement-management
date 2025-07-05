package com.sungroup.procurement.repository;

import com.sungroup.procurement.entity.ProcurementLineItem;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProcurementLineItemRepository extends BaseRepository<ProcurementLineItem, Long> {

    List<ProcurementLineItem> findByProcurementRequestIdAndIsDeletedFalse(Long procurementRequestId);

    Optional<ProcurementLineItem> findByIdAndIsDeletedFalse(Long id);

    List<ProcurementLineItem> findByMaterialIdAndIsDeletedFalse(Long materialId);

    List<ProcurementLineItem> findByAssignedVendorIdAndIsDeletedFalse(Long vendorId);

    @Query("SELECT pli FROM ProcurementLineItem pli WHERE pli.isDeleted = false AND " +
            "pli.procurementRequest.id = :requestId")
    List<ProcurementLineItem> findByRequestId(@Param("requestId") Long requestId);
}


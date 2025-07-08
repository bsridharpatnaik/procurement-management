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

    // For material dependency checks
    @Query("SELECT COUNT(pli) FROM ProcurementLineItem pli " +
            "WHERE pli.material.id = :materialId " +
            "AND pli.procurementRequest.isDeleted = false")
    long countByMaterialIdAndProcurementRequestIsDeletedFalse(@Param("materialId") Long materialId);

    // For vendor dependency checks
    @Query("SELECT COUNT(pli) FROM ProcurementLineItem pli " +
            "WHERE pli.assignedVendor.id = :vendorId " +
            "AND pli.procurementRequest.isDeleted = false")
    long countByAssignedVendorIdAndProcurementRequestIsDeletedFalse(@Param("vendorId") Long vendorId);
}


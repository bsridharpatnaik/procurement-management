package com.sungroup.procurement.repository;

import com.sungroup.procurement.entity.MaterialPriceHistory;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MaterialPriceHistoryRepository extends BaseRepository<MaterialPriceHistory, Long> {

    Optional<MaterialPriceHistory> findByIdAndIsDeletedFalse(Long id);

    // For material dependency checks
    long countByMaterialId(Long materialId);

    // For vendor dependency checks
    long countByVendorId(Long vendorId);
}
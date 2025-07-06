package com.sungroup.procurement.repository;

import com.sungroup.procurement.entity.MaterialPriceHistory;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MaterialPriceHistoryRepository extends BaseRepository<MaterialPriceHistory, Long> {

    List<MaterialPriceHistory> findByMaterialIdAndIsDeletedFalse(Long materialId);

    Optional<MaterialPriceHistory> findByIdAndIsDeletedFalse(Long id);

    List<MaterialPriceHistory> findByVendorIdAndIsDeletedFalse(Long vendorId);

    @Query("SELECT mph FROM MaterialPriceHistory mph WHERE mph.isDeleted = false AND " +
            "mph.material.id = :materialId AND mph.vendor.id = :vendorId " +
            "ORDER BY mph.createdAt DESC")
    List<MaterialPriceHistory> findByMaterialAndVendor(@Param("materialId") Long materialId,
                                                       @Param("vendorId") Long vendorId);

    @Query("SELECT mph FROM MaterialPriceHistory mph WHERE mph.isDeleted = false AND " +
            "mph.material.id = :materialId " +
            "ORDER BY mph.createdAt DESC")
    List<MaterialPriceHistory> findLatestPricesForMaterial(@Param("materialId") Long materialId);

    // For material dependency checks
    long countByMaterialId(Long materialId);
    boolean existsByMaterialId(Long materialId);

    // For vendor dependency checks
    long countByVendorId(Long vendorId);
    boolean existsByVendorId(Long vendorId);
}
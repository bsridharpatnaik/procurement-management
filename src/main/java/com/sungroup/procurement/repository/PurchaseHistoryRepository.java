package com.sungroup.procurement.repository;

import com.sungroup.procurement.dto.VendorPurchaseSummaryDto;
import com.sungroup.procurement.entity.PurchaseHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface PurchaseHistoryRepository extends BaseRepository<PurchaseHistory, Long> {

    @Query("SELECT ph FROM PurchaseHistory ph WHERE " +
            "(:materialId IS NULL OR ph.material.id = :materialId) AND " +
            "(:vendorId IS NULL OR ph.vendor.id = :vendorId) AND " +
            "(:factoryId IS NULL OR ph.factory.id = :factoryId) AND " +
            "(:startDate IS NULL OR ph.purchaseDate >= :startDate) AND " +
            "(:endDate IS NULL OR ph.purchaseDate <= :endDate) " +
            "ORDER BY ph.purchaseDate DESC")
    Page<PurchaseHistory> findPurchaseHistoryWithFilters(
            @Param("materialId") Long materialId,
            @Param("vendorId") Long vendorId,
            @Param("factoryId") Long factoryId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    @Query("SELECT ph FROM PurchaseHistory ph WHERE ph.vendor.id = :vendorId " +
            "ORDER BY ph.purchaseDate DESC")
    Page<PurchaseHistory> findByVendorIdOrderByPurchaseDateDesc(@Param("vendorId") Long vendorId, Pageable pageable);

    @Query("SELECT ph FROM PurchaseHistory ph WHERE ph.material.id = :materialId " +
            "ORDER BY ph.purchaseDate DESC")
    Page<PurchaseHistory> findByMaterialIdOrderByPurchaseDateDesc(@Param("materialId") Long materialId, Pageable pageable);

    // For grouped view by vendor
    @Query("SELECT new com.sungroup.procurement.dto.VendorPurchaseSummaryDto(" +
            "ph.vendor.id, ph.vendor.name, ph.vendor.contactPersonName, " +
            "COUNT(ph.id), SUM(ph.totalAmount), MAX(ph.purchaseDate)) " +
            "FROM PurchaseHistory ph " +
            "WHERE (:factoryId IS NULL OR ph.factory.id = :factoryId) " +
            "GROUP BY ph.vendor.id, ph.vendor.name, ph.vendor.contactPersonName " +
            "ORDER BY SUM(ph.totalAmount) DESC")
    Page<VendorPurchaseSummaryDto> findVendorPurchaseSummary(@Param("factoryId") Long factoryId, Pageable pageable);
}
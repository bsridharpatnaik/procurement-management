package com.sungroup.procurement.repository;

import com.sungroup.procurement.entity.MaterialVendorHistory;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MaterialVendorHistoryRepository extends BaseRepository<MaterialVendorHistory, Long> {

    Optional<MaterialVendorHistory> findByMaterialIdAndVendorIdAndFactoryId(Long materialId, Long vendorId, Long factoryId);

    @Query("SELECT mvh FROM MaterialVendorHistory mvh WHERE mvh.material.id = :materialId " +
            "ORDER BY mvh.lastOrderedDate DESC, mvh.orderCount DESC")
    List<MaterialVendorHistory> findByMaterialIdOrderByHistoryDesc(@Param("materialId") Long materialId);

    @Query("SELECT mvh FROM MaterialVendorHistory mvh WHERE mvh.material.id = :materialId AND mvh.factoryId = :factoryId " +
            "ORDER BY mvh.lastOrderedDate DESC, mvh.orderCount DESC")
    List<MaterialVendorHistory> findByMaterialIdAndFactoryIdOrderByHistoryDesc(@Param("materialId") Long materialId, @Param("factoryId") Long factoryId);
}
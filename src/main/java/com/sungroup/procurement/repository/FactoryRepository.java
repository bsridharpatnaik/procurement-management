package com.sungroup.procurement.repository;

import com.sungroup.procurement.entity.Factory;
import com.sungroup.procurement.entity.Material;
import com.sungroup.procurement.entity.Vendor;
import com.sungroup.procurement.entity.ProcurementRequest;
import com.sungroup.procurement.entity.ProcurementLineItem;
import com.sungroup.procurement.entity.MaterialPriceHistory;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// Factory Repository
@Repository
public interface FactoryRepository extends BaseRepository<Factory, Long> {

    Optional<Factory> findByNameAndIsDeletedFalse(String name);

    Optional<Factory> findByFactoryCodeAndIsDeletedFalse(String factoryCode);

    Optional<Factory> findByIdAndIsDeletedFalse(Long id);

    List<Factory> findByIsActiveAndIsDeletedFalse(Boolean isActive);

    boolean existsByNameAndIsDeletedFalse(String name);

    boolean existsByFactoryCodeAndIsDeletedFalse(String factoryCode);

    @Query("SELECT f FROM Factory f WHERE f.isDeleted = false AND f.isActive = true AND " +
            "(:name IS NULL OR LOWER(f.name) LIKE LOWER(CONCAT('%', :name, '%')))")
    List<Factory> findActiveFactoriesByName(@Param("name") String name);

    long countByIsDeletedFalse();
}
package com.sungroup.procurement.repository;

import com.sungroup.procurement.entity.Factory;
import com.sungroup.procurement.entity.Material;
import com.sungroup.procurement.entity.Vendor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// Factory Repository
@Repository
public interface FactoryRepository extends BaseRepository<Factory, Long> {

    Optional<Factory> findByFactoryCodeAndIsDeletedFalse(String factoryCode);

    Optional<Factory> findByIdAndIsDeletedFalse(Long id);

    boolean existsByNameAndIsDeletedFalse(String name);

    boolean existsByFactoryCodeAndIsDeletedFalse(String factoryCode);

    long countByIsDeletedFalse();
}
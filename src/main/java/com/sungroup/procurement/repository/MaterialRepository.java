package com.sungroup.procurement.repository;

import com.sungroup.procurement.entity.Material;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MaterialRepository extends BaseRepository<Material, Long> {

    Optional<Material> findByNameAndIsDeletedFalse(String name);

    Optional<Material> findByIdAndIsDeletedFalse(Long id);

    List<Material> findByImportFromChinaAndIsDeletedFalse(Boolean importFromChina);

    List<Material> findByUnitAndIsDeletedFalse(String unit);

    boolean existsByNameAndIsDeletedFalse(String name);

    @Query("SELECT m FROM Material m WHERE m.isDeleted = false AND " +
            "(:name IS NULL OR LOWER(m.name) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
            "(:unit IS NULL OR LOWER(m.unit) LIKE LOWER(CONCAT('%', :unit, '%'))) AND " +
            "(:importFromChina IS NULL OR m.importFromChina = :importFromChina)")
    List<Material> findMaterialsWithFilters(@Param("name") String name,
                                            @Param("unit") String unit,
                                            @Param("importFromChina") Boolean importFromChina);

    @Query("SELECT DISTINCT m.unit FROM Material m WHERE m.isDeleted = false AND m.unit IS NOT NULL ORDER BY m.unit")
    List<String> findAllDistinctUnits();
}
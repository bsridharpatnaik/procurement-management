package com.sungroup.procurement.repository;

import com.sungroup.procurement.dto.response.MaterialNameDto;
import com.sungroup.procurement.entity.Material;
import org.springframework.data.domain.Pageable;
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

    // Get all active material names only
    @Query("SELECT m.name FROM Material m WHERE m.isDeleted = false ORDER BY m.name")
    List<String> findAllActiveMaterialNames();

    // Search material names by partial match
    @Query("SELECT m.name FROM Material m WHERE m.isDeleted = false " +
            "AND LOWER(m.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "ORDER BY m.name")
    List<String> findMaterialNamesByNameContainingIgnoreCase(@Param("search") String search);

    // Get material names with IDs for selection
    @Query("SELECT new com.sungroup.procurement.dto.response.MaterialNameDto(m.id, m.name, m.unit) " +
            "FROM Material m WHERE m.isDeleted = false ORDER BY m.name")
    List<MaterialNameDto> findAllActiveMaterialNamesWithIds(Pageable pageable);

    // Search material names with IDs by partial match
    @Query("SELECT new com.sungroup.procurement.dto.response.MaterialNameDto(m.id, m.name, m.unit) " +
            "FROM Material m WHERE m.isDeleted = false " +
            "AND LOWER(m.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "ORDER BY m.name")
    List<MaterialNameDto> findMaterialNamesWithIdsByNameContaining(@Param("search") String search, Pageable pageable);

    // Alternative: Simple method using Spring Data naming convention
    List<Material> findByNameContainingIgnoreCaseAndIsDeletedFalseOrderByName(String name);

    boolean existsByNameIgnoreCaseAndIsDeletedFalse(String name);
    Optional<Material> findByNameIgnoreCaseAndIsDeletedFalse(String name);
}
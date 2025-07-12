package com.sungroup.procurement.repository;

import com.sungroup.procurement.dto.response.VendorNameDto;
import com.sungroup.procurement.entity.Vendor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VendorRepository extends BaseRepository<Vendor, Long> {
    Optional<Vendor> findByIdAndIsDeletedFalse(Long id);

    boolean existsByNameIgnoreCaseAndIsDeletedFalse(String name);

    // UPDATED: Include contact person name in response
    @Query("SELECT new com.sungroup.procurement.dto.response.VendorNameDto(v.id, v.name, v.email, v.contactNumber, v.contactPersonName) " +
            "FROM Vendor v WHERE v.isDeleted = false ORDER BY v.name")
    List<VendorNameDto> findAllActiveVendorNamesWithIds(Pageable pageable);

    @Query("SELECT new com.sungroup.procurement.dto.response.VendorNameDto(v.id, v.name, v.email, v.contactNumber, v.contactPersonName) " +
            "FROM Vendor v WHERE v.isDeleted = false " +
            "AND LOWER(v.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "ORDER BY v.name")
    List<VendorNameDto> findVendorNamesWithIdsByNameContaining(@Param("search") String search, Pageable pageable);

    long countByCreatedByAndIsDeletedFalse(String createdBy);

    long countByIsDeletedFalse();

    @Query("SELECT v.name FROM Vendor v WHERE v.isDeleted = false " +
            "AND LOWER(v.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "ORDER BY v.name")
    List<String> findVendorNamesByNameContainingIgnoreCase(@Param("search") String search);

    @Query("SELECT v.name FROM Vendor v WHERE v.isDeleted = false ORDER BY v.name")
    List<String> findAllActiveVendorNames();
}

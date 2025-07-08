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

    List<Vendor> findByNameContainingIgnoreCaseAndIsDeletedFalse(String name);

    Optional<Vendor> findByEmailAndIsDeletedFalse(String email);

    Optional<Vendor> findByIdAndIsDeletedFalse(Long id);

    List<Vendor> findByContactNumberAndIsDeletedFalse(String contactNumber);

    @Query("SELECT v FROM Vendor v WHERE v.isDeleted = false AND " +
            "(:keyword IS NULL OR " +
            "LOWER(v.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(v.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "v.contactNumber LIKE CONCAT('%', :keyword, '%'))")
    List<Vendor> searchVendors(@Param("keyword") String keyword);

    boolean existsByNameIgnoreCaseAndIsDeletedFalse(String name);

    Optional<Vendor> findByNameIgnoreCaseAndIsDeletedFalse(String name);

    // ADD typeahead methods
    @Query("SELECT v.name FROM Vendor v WHERE v.isDeleted = false ORDER BY v.name")
    List<String> findAllActiveVendorNames();

    @Query("SELECT v.name FROM Vendor v WHERE v.isDeleted = false " +
            "AND LOWER(v.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "ORDER BY v.name")
    List<String> findVendorNamesByNameContainingIgnoreCase(@Param("search") String search);

    @Query("SELECT new com.sungroup.procurement.dto.response.VendorNameDto(v.id, v.name, v.email, v.contactNumber) " +
            "FROM Vendor v WHERE v.isDeleted = false ORDER BY v.name")
    List<VendorNameDto> findAllActiveVendorNamesWithIds(Pageable pageable);

    @Query("SELECT new com.sungroup.procurement.dto.response.VendorNameDto(v.id, v.name, v.email, v.contactNumber) " +
            "FROM Vendor v WHERE v.isDeleted = false " +
            "AND LOWER(v.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "ORDER BY v.name")
    List<VendorNameDto> findVendorNamesWithIdsByNameContaining(@Param("search") String search, Pageable pageable);

    long countByCreatedByAndIsDeletedFalse(String createdBy);

    boolean existsByCreatedByAndIsDeletedFalse(String createdBy);

    long countByUpdatedByAndUpdatedAtAfter(String updatedBy, LocalDateTime afterDate);

    long countByIsDeletedFalse();
}

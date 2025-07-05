package com.sungroup.procurement.repository;

import com.sungroup.procurement.entity.Vendor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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

    @Query("SELECT v FROM Vendor v WHERE v.isDeleted = false AND " +
            "v.email IS NOT NULL AND v.email != '' AND v.email LIKE '%@%'")
    List<Vendor> findVendorsWithValidEmail();

    @Query("SELECT v FROM Vendor v WHERE v.isDeleted = false AND " +
            "v.contactNumber IS NOT NULL AND v.contactNumber != ''")
    List<Vendor> findVendorsWithValidContactNumber();
}

package com.sungroup.procurement.repository;

import com.sungroup.procurement.entity.ProcurementRequest;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


@Repository
public interface ProcurementRequestRepository extends BaseRepository<ProcurementRequest, Long> {

    long countByFactoryIdAndCreatedAtAfter(Long factoryId, LocalDateTime afterDate);

    Optional<ProcurementRequest> findByRequestNumberAndIsDeletedFalse(String requestNumber);

    Optional<ProcurementRequest> findByIdAndIsDeletedFalse(Long id);

    List<ProcurementRequest> findByFactoryIdAndIsDeletedFalse(Long factoryId);

    // For factory dependency checks
    long countByFactoryIdAndIsDeletedFalse(Long factoryId);

    // For user dependency checks - assuming createdBy is String (username)
    long countByCreatedByAndIsDeletedFalse(String createdBy);

    // For user dependency checks - assuming assignedTo is User entity
    long countByAssignedToIdAndIsDeletedFalse(Long userId);

    // For user dependency checks - assuming approvedBy is User entity
    long countByApprovedByIdAndIsDeletedFalse(Long userId);
}
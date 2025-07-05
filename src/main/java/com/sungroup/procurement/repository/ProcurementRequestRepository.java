package com.sungroup.procurement.repository;

import com.sungroup.procurement.entity.ProcurementRequest;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface ProcurementRequestRepository extends BaseRepository<ProcurementRequest, Long> {

    Optional<ProcurementRequest> findByRequestNumberAndIsDeletedFalse(String requestNumber);

    Optional<ProcurementRequest> findByIdAndIsDeletedFalse(Long id);

    boolean existsByRequestNumberAndIsDeletedFalse(String requestNumber);

    List<ProcurementRequest> findByFactoryIdAndIsDeletedFalse(Long factoryId);

    List<ProcurementRequest> findByAssignedToIdAndIsDeletedFalse(Long userId);

    List<ProcurementRequest> findByRequiresApprovalAndIsDeletedFalse(Boolean requiresApproval);

    @Query("SELECT pr FROM ProcurementRequest pr WHERE pr.isDeleted = false AND pr.assignedTo IS NULL")
    List<ProcurementRequest> findUnassignedRequests();

    @Query("SELECT pr FROM ProcurementRequest pr WHERE pr.isDeleted = false AND " +
            "pr.factory.id IN :factoryIds")
    List<ProcurementRequest> findByFactoryIds(@Param("factoryIds") List<Long> factoryIds);
}
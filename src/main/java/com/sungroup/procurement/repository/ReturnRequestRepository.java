package com.sungroup.procurement.repository;

import com.sungroup.procurement.entity.ReturnRequest;
import com.sungroup.procurement.entity.enums.ReturnStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReturnRequestRepository extends BaseRepository<ReturnRequest, Long> {

    List<ReturnRequest> findByProcurementLineItemIdAndIsDeletedFalse(Long lineItemId);

    Optional<ReturnRequest> findByIdAndIsDeletedFalse(Long id);

    List<ReturnRequest> findByReturnStatusAndIsDeletedFalse(ReturnStatus returnStatus);

    @Query("SELECT rr FROM ReturnRequest rr WHERE rr.isDeleted = false AND " +
            "rr.procurementLineItem.id = :lineItemId AND rr.returnStatus = :status")
    List<ReturnRequest> findByLineItemIdAndStatus(@Param("lineItemId") Long lineItemId,
                                                  @Param("status") ReturnStatus status);

    @Query("SELECT rr FROM ReturnRequest rr WHERE rr.isDeleted = false AND " +
            "rr.procurementLineItem.procurementRequest.factory.id = :factoryId")
    List<ReturnRequest> findByFactoryId(@Param("factoryId") Long factoryId);

    @Query("SELECT rr FROM ReturnRequest rr WHERE rr.isDeleted = false AND " +
            "rr.procurementLineItem.procurementRequest.factory.id IN :factoryIds")
    List<ReturnRequest> findByFactoryIds(@Param("factoryIds") List<Long> factoryIds);

    @Query("SELECT rr FROM ReturnRequest rr WHERE rr.isDeleted = false AND " +
            "rr.requestedBy.id = :userId")
    List<ReturnRequest> findByRequestedById(@Param("userId") Long userId);

    @Query("SELECT rr FROM ReturnRequest rr WHERE rr.isDeleted = false AND " +
            "rr.approvedBy.id = :userId")
    List<ReturnRequest> findByApprovedById(@Param("userId") Long userId);

    @Query("SELECT COUNT(rr) FROM ReturnRequest rr WHERE rr.isDeleted = false AND " +
            "rr.procurementLineItem.id = :lineItemId")
    long countByLineItemId(@Param("lineItemId") Long lineItemId);

    @Query("SELECT COUNT(rr) FROM ReturnRequest rr WHERE rr.isDeleted = false AND " +
            "rr.procurementLineItem.id = :lineItemId AND rr.returnStatus = :status")
    long countByLineItemIdAndStatus(@Param("lineItemId") Long lineItemId,
                                    @Param("status") ReturnStatus status);

    @Query("SELECT COALESCE(SUM(rr.returnQuantity), 0) FROM ReturnRequest rr WHERE rr.isDeleted = false AND " +
            "rr.procurementLineItem.id = :lineItemId AND rr.returnStatus = 'RETURN_APPROVED'")
    java.math.BigDecimal getTotalApprovedReturnQuantityByLineItem(@Param("lineItemId") Long lineItemId);

    @Query("SELECT COALESCE(SUM(rr.returnQuantity), 0) FROM ReturnRequest rr WHERE rr.isDeleted = false AND " +
            "rr.procurementLineItem.id = :lineItemId AND rr.returnStatus = 'RETURN_REQUESTED'")
    java.math.BigDecimal getTotalPendingReturnQuantityByLineItem(@Param("lineItemId") Long lineItemId);

    // For factory access control
    @Query("SELECT rr FROM ReturnRequest rr WHERE rr.isDeleted = false AND " +
            "rr.procurementLineItem.procurementRequest.factory.id IN :factoryIds AND " +
            "rr.returnStatus = :status")
    List<ReturnRequest> findByFactoryIdsAndStatus(@Param("factoryIds") List<Long> factoryIds,
                                                  @Param("status") ReturnStatus status);
}
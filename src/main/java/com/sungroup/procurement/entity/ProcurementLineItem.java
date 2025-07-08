package com.sungroup.procurement.entity;

import com.sungroup.procurement.entity.enums.LineItemStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.Audited;

import javax.persistence.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "procurement_line_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Audited
public class ProcurementLineItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "procurement_request_id", nullable = false)
    private ProcurementRequest procurementRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @Column(name = "requested_quantity", nullable = false, precision = 15, scale = 3)
    private BigDecimal requestedQuantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_vendor_id")
    private Vendor assignedVendor;

    @Column(name = "assigned_price", precision = 15, scale = 2)
    private BigDecimal assignedPrice;

    @Column(name = "actual_quantity", precision = 15, scale = 3)
    private BigDecimal actualQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private LineItemStatus status = LineItemStatus.PENDING;

    @Column(name = "is_short_closed", nullable = false)
    private Boolean isShortClosed = false;

    @Column(name = "short_close_reason", columnDefinition = "TEXT")
    private String shortCloseReason;

    // NEW RETURN-RELATED FIELDS
    @Column(name = "has_returns", nullable = false)
    private Boolean hasReturns = false;

    @Column(name = "total_returned_quantity", precision = 15, scale = 3)
    private BigDecimal totalReturnedQuantity = BigDecimal.ZERO;

    @OneToMany(mappedBy = "procurementLineItem", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ReturnRequest> returnRequests = new ArrayList<>();

    /**
     * Calculate effective quantity (actual received minus returned)
     */
    public BigDecimal getEffectiveQuantity() {
        if (actualQuantity == null) {
            return BigDecimal.ZERO;
        }
        if (totalReturnedQuantity == null) {
            return actualQuantity;
        }
        return actualQuantity.subtract(totalReturnedQuantity);
    }

    /**
     * Check if this line item can be returned
     */
    public boolean canBeReturned() {
        return status == LineItemStatus.RECEIVED &&
                actualQuantity != null &&
                actualQuantity.compareTo(BigDecimal.ZERO) > 0 &&
                getEffectiveQuantity().compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Get maximum returnable quantity
     */
    public BigDecimal getMaxReturnableQuantity() {
        return getEffectiveQuantity();
    }

    /**
     * Check if there are pending return requests
     */
    public boolean hasPendingReturns() {
        if (returnRequests == null || returnRequests.isEmpty()) {
            return false;
        }
        return returnRequests.stream()
                .anyMatch(returnRequest -> returnRequest.getReturnStatus() ==
                        com.sungroup.procurement.entity.enums.ReturnStatus.RETURN_REQUESTED);
    }

    /**
     * Update return totals when a return is approved
     */
    public void updateReturnTotals() {
        if (returnRequests == null || returnRequests.isEmpty()) {
            hasReturns = false;
            totalReturnedQuantity = BigDecimal.ZERO;
            return;
        }

        BigDecimal approvedReturns = returnRequests.stream()
                .filter(returnRequest -> returnRequest.getReturnStatus() ==
                        com.sungroup.procurement.entity.enums.ReturnStatus.RETURN_APPROVED)
                .map(ReturnRequest::getReturnQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        totalReturnedQuantity = approvedReturns;
        hasReturns = approvedReturns.compareTo(BigDecimal.ZERO) > 0;
    }
}
package com.sungroup.procurement.entity;

import com.sungroup.procurement.entity.enums.ReturnStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.Audited;

import javax.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "return_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Audited
public class ReturnRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "procurement_line_item_id", nullable = false)
    private ProcurementLineItem procurementLineItem;

    @Column(name = "return_quantity", nullable = false, precision = 15, scale = 3)
    private BigDecimal returnQuantity;

    @Column(name = "return_reason", nullable = false, columnDefinition = "TEXT")
    private String returnReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "return_status", nullable = false)
    private ReturnStatus returnStatus = ReturnStatus.RETURN_REQUESTED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;
}
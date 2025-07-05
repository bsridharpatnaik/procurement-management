package com.sungroup.procurement.entity;

import com.sungroup.procurement.entity.enums.LineItemStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.Audited;

import javax.persistence.*;
import java.math.BigDecimal;

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
}
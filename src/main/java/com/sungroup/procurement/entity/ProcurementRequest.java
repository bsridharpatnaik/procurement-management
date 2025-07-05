package com.sungroup.procurement.entity;

import com.sungroup.procurement.entity.enums.Priority;
import com.sungroup.procurement.entity.enums.ProcurementStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.Audited;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "procurement_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Audited
public class ProcurementRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_number", nullable = false, unique = true)
    private String requestNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "factory_id", nullable = false)
    private Factory factory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private User assignedTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProcurementStatus status = ProcurementStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    private Priority priority = Priority.MEDIUM;

    @Column(name = "expected_delivery_date")
    private LocalDate expectedDeliveryDate;

    @Column(name = "justification", columnDefinition = "TEXT")
    private String justification;

    @Column(name = "requires_approval", nullable = false)
    private Boolean requiresApproval = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column(name = "approved_date")
    private LocalDateTime approvedDate;

    @Column(name = "is_short_closed", nullable = false)
    private Boolean isShortClosed = false;

    @Column(name = "short_close_reason", columnDefinition = "TEXT")
    private String shortCloseReason;

    @OneToMany(mappedBy = "procurementRequest", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ProcurementLineItem> lineItems = new ArrayList<>();
}
package com.sungroup.procurement.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.Audited;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "material_vendor_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Audited
public class MaterialVendorHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @Column(name = "last_ordered_date")
    private LocalDateTime lastOrderedDate;

    @Column(name = "last_price", precision = 15, scale = 2)
    private BigDecimal lastPrice;

    @Column(name = "order_count", nullable = false)
    private Integer orderCount = 1;

    @Column(name = "factory_id", nullable = false)
    private Long factoryId;

    // For tracking which factory-material-vendor combination
    @Column(name = "created_by")
    private String createdBy;
}
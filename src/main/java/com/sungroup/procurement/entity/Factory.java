package com.sungroup.procurement.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.Audited;

import javax.persistence.*;

@Entity
@Table(name = "factories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Audited
public class Factory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "factory_code", nullable = false, unique = true, length = 2)
    private String factoryCode;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
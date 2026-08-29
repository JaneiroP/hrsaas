package com.ohmdevs.hrsaas.people.domain;

import com.ohmdevs.hrsaas.platform.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A quién llamar en caso de emergencia. Sin cifrar: no está en la lista explícita de AD-08. */
@Entity
@Table(name = "emergency_contacts")
@Getter
@Setter
public class EmergencyContact extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false)
    private Employee employee;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(length = 60)
    private String relationship;

    @Column(nullable = false, length = 30)
    private String phone;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}

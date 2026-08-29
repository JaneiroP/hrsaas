package com.ohmdevs.hrsaas.assets.application;

import com.ohmdevs.hrsaas.assets.domain.Asset;
import com.ohmdevs.hrsaas.assets.domain.AssetAssignment;
import com.ohmdevs.hrsaas.assets.domain.AssetAssignmentStatus;
import com.ohmdevs.hrsaas.assets.domain.AssetReturn;
import com.ohmdevs.hrsaas.assets.domain.AssetReturnCondition;
import com.ohmdevs.hrsaas.assets.domain.AssetStatus;
import com.ohmdevs.hrsaas.assets.infrastructure.AssetAssignmentRepository;
import com.ohmdevs.hrsaas.assets.infrastructure.AssetRepository;
import com.ohmdevs.hrsaas.assets.infrastructure.AssetReturnRepository;
import com.ohmdevs.hrsaas.platform.tenancy.TenantContext;
import com.ohmdevs.hrsaas.shared.statemachine.IllegalStateTransitionException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Inventario de equipos, su asignación con acta, y su devolución. */
@Service
public class AssetManagementService {

    private final AssetRepository assetRepository;
    private final AssetAssignmentRepository assetAssignmentRepository;
    private final AssetReturnRepository assetReturnRepository;

    public AssetManagementService(AssetRepository assetRepository,
                                   AssetAssignmentRepository assetAssignmentRepository,
                                   AssetReturnRepository assetReturnRepository) {
        this.assetRepository = assetRepository;
        this.assetAssignmentRepository = assetAssignmentRepository;
        this.assetReturnRepository = assetReturnRepository;
    }

    @Transactional
    public Asset registerAsset(Long companyId, String assetTag, String name, String category) {
        TenantContext.require();
        assetRepository.findByAssetTag(assetTag).ifPresent(existing -> {
            throw new IllegalArgumentException("Ya existe un activo con la etiqueta '" + assetTag + "' en este tenant");
        });
        Asset asset = new Asset();
        asset.setCompanyId(companyId);
        asset.setAssetTag(assetTag);
        asset.setName(name);
        asset.setCategory(category);
        return assetRepository.save(asset);
    }

    /** Exige que el equipo esté {@code AVAILABLE} — la máquina de estados lo hace explícito, no un chequeo suelto. */
    @Transactional
    public AssetAssignment assign(Long assetId, Long employeeId, String acknowledgementDocumentKey) {
        TenantContext.require();
        Asset asset = transitionAsset(assetId, AssetStatus.ASSIGNED);

        AssetAssignment assignment = new AssetAssignment();
        assignment.setAsset(asset);
        assignment.setEmployeeId(employeeId);
        assignment.setAcknowledgementDocumentKey(acknowledgementDocumentKey);
        return assetAssignmentRepository.save(assignment);
    }

    /** Cierra la asignación y regresa el equipo a {@code AVAILABLE} — un equipo dañado o perdido se retira aparte, explícito. */
    @Transactional
    public AssetReturn returnAsset(Long assetAssignmentId, AssetReturnCondition condition, String notes) {
        TenantContext.require();
        AssetAssignment assignment = assetAssignmentRepository.findById(assetAssignmentId).orElseThrow(
                () -> new IllegalArgumentException("Asignación " + assetAssignmentId + " no existe en este tenant"));
        AssetAssignmentStatus current = assignment.getStatus();
        if (!current.canTransitionTo(AssetAssignmentStatus.RETURNED)) {
            throw new IllegalStateTransitionException(
                    "AssetAssignment", assetAssignmentId, current, AssetAssignmentStatus.RETURNED);
        }
        assignment.setStatus(AssetAssignmentStatus.RETURNED);
        assignment.setReturnedAt(Instant.now());
        assetAssignmentRepository.save(assignment);

        transitionAsset(assignment.getAsset().getId(), AssetStatus.AVAILABLE);

        AssetReturn assetReturn = new AssetReturn();
        assetReturn.setAssetAssignment(assignment);
        assetReturn.setCondition(condition);
        assetReturn.setNotes(notes);
        return assetReturnRepository.save(assetReturn);
    }

    @Transactional
    public Asset retireAsset(Long assetId) {
        return transitionAsset(assetId, AssetStatus.RETIRED);
    }

    private Asset transitionAsset(Long assetId, AssetStatus target) {
        Asset asset = assetRepository.findById(assetId).orElseThrow(
                () -> new IllegalArgumentException("Activo " + assetId + " no existe en este tenant"));
        AssetStatus current = asset.getStatus();
        if (!current.canTransitionTo(target)) {
            throw new IllegalStateTransitionException("Asset", assetId, current, target);
        }
        asset.setStatus(target);
        return assetRepository.save(asset);
    }
}

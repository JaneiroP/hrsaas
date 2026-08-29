package com.ohmdevs.hrsaas.assets.infrastructure;

import com.ohmdevs.hrsaas.assets.domain.AssetReturn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssetReturnRepository extends JpaRepository<AssetReturn, Long> {

    Optional<AssetReturn> findByAssetAssignmentId(Long assetAssignmentId);
}

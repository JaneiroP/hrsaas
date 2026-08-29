package com.ohmdevs.hrsaas.assets.infrastructure;

import com.ohmdevs.hrsaas.assets.domain.Asset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssetRepository extends JpaRepository<Asset, Long> {

    Optional<Asset> findByAssetTag(String assetTag);
}
